package com.signaldesk.ui.views;

import com.signaldesk.calc.Fmt;
import com.signaldesk.signals.*;
import com.signaldesk.store.PriceStore;
import com.signaldesk.store.Universe;
import com.signaldesk.store.UiPrefs;
import com.signaldesk.ui.*;
import com.signaldesk.ui.PriceService.Analysis;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/** Screener: rank a list of stocks, ETFs, mutual funds or coins by the Signal Desk score, with the reasons for each. */
public final class PicksView extends View {
    private final Slot list = new Slot(), bar = new Slot(), picker = new Slot();
    private String key;
    private int done = -1, total;
    public PicksView(MainFrame app) { super(app); }

    private List<String> symbols() {
        if (key.equals("watchlist")) return new ArrayList<>(s().watchlist);
        for (Universe.List_ u : Universe.ALL) if (u.key().equals(key)) return u.symbols().stream().map(PriceStore::clean).toList();
        return List.of();
    }
    private String about() {
        if (key.equals("watchlist")) return "Everything on your watchlist";
        for (Universe.List_ u : Universe.ALL) if (u.key().equals(key)) return u.about();
        return "";
    }

    @Override public JComponent build() {
        key = UiPrefs.get("picks.list", "stocks");
        Ui.VBox root = new Ui.VBox(20);
        root.add(Kit.head("Screener", "Picks", "Rank a list by the Signal Desk score: trend, momentum, risk and timing from each symbol's price history. Use it to find things worth researching, not as a buy list.", Help.q(app, "score")));
        Ui.HBox row = new Ui.HBox(12);
        row.add(picker); row.glue(); row.add(bar);
        root.add(row);
        root.add(list);
        root.add(Kit.disclaimer());
        drawPicker();
        refresh();
        boolean missing = symbols().stream().anyMatch(s -> app.prices().get(s) == null);
        if (missing && !Boolean.getBoolean("signaldesk.offline")) SwingUtilities.invokeLater(() -> rank(false));
        return root;
    }
    private void drawPicker() {
        String[] keys = new String[Universe.ALL.size() + 1], labels = new String[keys.length];
        for (int i = 0; i < Universe.ALL.size(); i++) { keys[i] = Universe.ALL.get(i).key(); labels[i] = Universe.ALL.get(i).label(); }
        keys[keys.length - 1] = "watchlist"; labels[labels.length - 1] = "My watchlist";
        int sel = Arrays.asList(keys).indexOf(key);
        picker.set(Ui.seg(labels, Math.max(0, sel), i -> {
            if (app.priceService().busy()) { app.toast("Wait for the current download to finish.", null); return; }
            key = keys[i]; UiPrefs.put("picks.list", key); drawPicker(); refresh();
            if (symbols().stream().anyMatch(s -> app.prices().get(s) == null) && !Boolean.getBoolean("signaldesk.offline")) rank(false);
        }));
    }

    private void rank(boolean force) {
        List<String> syms = symbols(); if (syms.isEmpty()) return;
        done = 0; total = syms.size(); drawBar();
        app.priceService().refresh(syms, force, (d, n) -> { done = d; total = n; drawBar(); }, (got, failed) -> {
            done = -1; drawBar();
            if (!app.section().equals("picks")) return;
            refresh();
            if (!failed.isEmpty() && got.isEmpty()) app.toast("Couldn't reach the price service. Showing saved prices.", null);
            else if (!failed.isEmpty()) app.toast("No prices for " + String.join(", ", failed), null);
        });
    }
    private void drawBar() {
        Theme t = Theme.T;
        if (done >= 0) {
            Ui.HBox h = new Ui.HBox(10);
            JComponent m = Ui.meter(total == 0 ? 0 : (double) done / total, false, null); m.setPreferredSize(new Dimension(160, 12));
            h.add(Ui.text("Downloading " + done + " of " + total + "…", Theme.body(12.5f), t.accent)); h.add(m);
            bar.set(h);
        } else bar.set(Ui.btn("Update prices", () -> rank(true)));
    }

    @Override public void refresh() {
        Theme t = Theme.T;
        drawBar();
        List<String> syms = symbols();
        List<Analysis> rs = new ArrayList<>(); List<String> none = new ArrayList<>();
        for (String s : syms) { Analysis a = app.priceService().analysis(s); if (a != null && a.score() != null) rs.add(a); else none.add(s); }
        rs.sort(Comparator.comparingDouble((Analysis a) -> -a.score().score()));
        Ui.Card c = Kit.card(rs.size() + " ranked", Ui.text(about(), Theme.body(12.5f), t.muted));
        if (rs.isEmpty()) {
            c.add(Kit.empty(syms.isEmpty() ? "Your watchlist is empty. Add symbols on the Markets page." : "No prices saved for this list yet. Press Update prices to download them; it takes about a second per symbol."));
            list.set(c); return;
        }
        Kit.Grid g = new Kit.Grid(new String[]{"#", "Symbol", "Score", "3 months", "1 year", "Volatility", "Call", "Why"},
            new int[]{22, 170, 110, 66, 66, 80, 44, -220}, "r__rrr__");
        int n = 1;
        for (Analysis a : rs) {
            Scoring.Result r = a.score(); Stats st = a.stats();
            StringBuilder why = new StringBuilder(); StringBuilder tip = new StringBuilder("<html>");
            for (Scoring.Reason x : r.reasons()) {
                if (why.length() < 1 || why.length() < 60 && r.reasons().indexOf(x) < 2) why.append(why.length() > 0 ? " " : "").append(x.text());
                tip.append(x.good() ? "＋ " : "− ").append(x.text()).append("<br>");
            }
            JLabel w = Ui.text(why.toString(), Theme.body(12.5f), t.muted); w.setToolTipText(tip.toString());
            g.row(() -> app.analyze(a.symbol()), Ui.mono(String.valueOf(n++), 12.5f, t.muted), MarketsView.nameCell(app, a.symbol(), a), Kit.score(r, false),
                Kit.pct(st.r3m, 0, 13f), Kit.pct(st.r1y, 0, 13f), Ui.mono(Fmt.pctu(st.vol1y), 13f, t.ink), Kit.callPill(r.call()), w);
        }
        c.add(g);
        if (!none.isEmpty()) c.add(Ui.hint("Not ranked yet (no saved prices): " + String.join(", ", none) + "."));
        c.add(Ui.hint("Click a row to see the full breakdown in Research. Hover the reason to see all of them."));
        list.set(c);
    }
}

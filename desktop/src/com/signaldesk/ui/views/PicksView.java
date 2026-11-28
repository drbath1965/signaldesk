package com.signaldesk.ui.views;

import com.signaldesk.calc.Fmt;
import com.signaldesk.model.Screen;
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

/**
 * Screener: rank a built-in list (stocks, ETFs, funds, dividend payers, crypto, the watchlist) or one of your own
 * saved screens. Each list can be sorted by score, dividend yield, return or calmness.
 */
public final class PicksView extends View {
    private final Slot list = new Slot(), bar = new Slot(), picker = new Slot(), screensRow = new Slot(), sortSlot = new Slot();
    /** A built-in list key, "watchlist", or "screen:<id>". */
    private String key;
    private int done = -1, total;
    public PicksView(MainFrame app) { super(app); }

    private Screen screen() {
        if (!key.startsWith("screen:")) return null;
        String id = key.substring(7);
        for (Screen s : s().screens) if (s.id.equals(id)) return s;
        return null;
    }
    private List<String> symbols() {
        Screen sc = screen(); if (sc != null) return Screens.symbols(app, sc);
        if (key.equals("watchlist")) return new ArrayList<>(s().watchlist);
        Universe.List_ u = Universe.find(key);
        return u == null ? List.of() : u.symbols().stream().map(PriceStore::clean).toList();
    }
    private String about() {
        Screen sc = screen(); if (sc != null) return Screens.describe(sc);
        if (key.equals("watchlist")) return "Everything on your watchlist";
        Universe.List_ u = Universe.find(key); return u == null ? "" : u.about();
    }
    private String sort() {
        Screen sc = screen(); if (sc != null) return sc.sort;
        return UiPrefs.get("picks.sort." + key, key.equals("dividends") ? "yield" : "score");
    }

    @Override public JComponent build() {
        key = UiPrefs.get("picks.list", "stocks");
        if (key.startsWith("screen:") && screen() == null) key = "stocks";
        Ui.VBox root = new Ui.VBox(16);
        root.add(Kit.head("Screener", "Picks", "Rank a list by the Signal Desk score, dividend yield or other measures, or build your own screen with your own rules. Use it to find things worth researching, not as a buy list.", Help.q(app, "score")));
        root.add(picker, false);
        Ui.HBox row = new Ui.HBox(12);
        row.add(screensRow); row.glue(); row.add(sortSlot); row.add(bar);
        root.add(row);
        root.add(list);
        root.add(Kit.disclaimer());
        drawControls();
        refresh();
        autoDownload();
        return root;
    }
    private void autoDownload() {
        boolean missing = symbols().stream().anyMatch(s -> app.prices().get(s) == null);
        if (missing && !Boolean.getBoolean("signaldesk.offline")) SwingUtilities.invokeLater(() -> rank(false));
    }
    private void select(String k) {
        if (app.priceService().busy()) { app.toast("Wait for the current download to finish.", null); return; }
        key = k; UiPrefs.put("picks.list", key); drawControls(); refresh(); autoDownload();
    }

    private void drawControls() {
        Theme t = Theme.T;
        List<String> keys = new ArrayList<>(), labels = new ArrayList<>();
        for (Universe.List_ u : Universe.ALL) { keys.add(u.key()); labels.add(u.label()); }
        keys.add("watchlist"); labels.add("My watchlist");
        picker.set(Ui.seg(labels.toArray(new String[0]), keys.indexOf(key), i -> select(keys.get(i))));

        Ui.HBox sr = new Ui.HBox(8);
        sr.add(Help.label(app, Ui.text("My screens:", Theme.medium(13f), t.muted), "screen"));
        for (Screen s : s().screens) {
            boolean on = key.equals("screen:" + s.id);
            Ui.Btn b = new Ui.Btn(s.name, on ? Ui.Btn.Kind.PRIMARY : Ui.Btn.Kind.PLAIN, true);
            b.addActionListener(e -> select("screen:" + s.id)); sr.add(b);
        }
        sr.add(Ui.link("+ New screen", () -> ScreenEditor.open(app, Screen.blank(), true, sc -> {
            s().screens.add(sc); app.changed(); select("screen:" + sc.id); app.toast("Saved screen \"" + sc.name + "\"", null);
        })));
        screensRow.set(sr);

        if (screen() != null) { sortSlot.set(new JLabel()); return; }
        JComboBox<Opt> so = Ui.combo(Screens.SORTS, Opt.find(Screens.SORTS, sort()));
        so.addActionListener(e -> { UiPrefs.put("picks.sort." + key, ((Opt) so.getSelectedItem()).key()); refresh(); });
        Ui.HBox h = new Ui.HBox(8); h.add(Ui.text("Sort by", Theme.body(12.5f), t.muted)); h.add(so);
        sortSlot.set(h);
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
            JComponent m = Ui.meter(total == 0 ? 0 : (double) done / total, false, null); m.setPreferredSize(new Dimension(140, 12));
            h.add(Ui.text("Downloading " + done + " of " + total + "…", Theme.body(12.5f), t.accent)); h.add(m);
            bar.set(h);
        } else bar.set(Ui.btn("Update prices", () -> rank(true)));
    }

    @Override public void refresh() {
        Theme t = Theme.T;
        drawBar();
        Screen sc = screen();
        List<String> syms = symbols();
        List<Analysis> rs = new ArrayList<>(); List<String> none = new ArrayList<>(); int failed = 0;
        Map<String, Integer> why = new LinkedHashMap<>();
        for (String s : syms) {
            Analysis a = app.priceService().analysis(s);
            if (a == null || a.type() == AssetType.INDEX) { none.add(s); continue; }
            if (sc != null) {
                List<String> f = Screens.fails(a, sc);
                if (!f.isEmpty()) { failed++; for (String x : f) why.merge(x, 1, Integer::sum); continue; }
            }
            rs.add(a);
        }
        rs.sort(Screens.order(sort()));
        boolean yieldFirst = sort().equals("yield");

        Ui.Card c;
        if (sc != null) {
            Ui.HBox acts = new Ui.HBox(10);
            acts.add(Ui.small("Edit rules", () -> ScreenEditor.open(app, sc, false, ed -> {
                int i = s().screens.indexOf(sc); s().screens.set(i, ed); ed.id = sc.id; app.changed(); select("screen:" + ed.id);
            })));
            acts.add(Ui.small("Duplicate", () -> { Screen d = sc.copy(); d.id = "sc-" + com.signaldesk.model.Ids.next(); d.name = sc.name + " (copy)"; s().screens.add(d); app.changed(); select("screen:" + d.id); }));
            acts.add(Ui.small("Delete", () -> {
                int i = s().screens.indexOf(sc); s().screens.remove(sc); app.changed(); select("stocks");
                app.toast("Deleted screen \"" + sc.name + "\"", () -> { s().screens.add(Math.min(i, s().screens.size()), sc); app.changed(); select("screen:" + sc.id); });
            }));
            c = Kit.card(sc.name + ": " + rs.size() + (rs.size() == 1 ? " match" : " matches"), acts);
            JTextArea d = Ui.para(about(), Theme.body(13f), t.muted); d.putClientProperty("maxw", 900); c.add(d);
        } else {
            c = Kit.card(rs.size() + " ranked", Ui.text(about(), Theme.body(12.5f), t.muted));
            if (key.equals("dividends")) c.add(Ui.hint("Yield is the dividends paid over the past 12 months divided by today's price, worked out from the price history. A very high yield can mean the price has fallen because investors expect a cut."));
        }
        if (rs.isEmpty()) {
            String msg = syms.isEmpty() ? (key.equals("watchlist") ? "Your watchlist is empty. Add symbols on the Markets page." : "This screen has nothing to look in. Edit it to pick lists or tickers.")
                : sc != null && failed > 0 ? "Nothing passes every rule right now. Try loosening one: " + topReasons(why) + "."
                : "No prices saved for this list yet. Press Update prices to download them; it takes about a second per symbol.";
            c.add(Kit.empty(msg)); list.set(c); return;
        }
        Kit.Grid g = new Kit.Grid(new String[]{"#", "Symbol", "Score", "Yield", "3 months", "1 year", "Volatility", "Call", "Why"},
            new int[]{22, 170, 110, 58, 66, 66, 80, 44, -200}, "r__rrrr__");
        int n = 1;
        for (Analysis a : rs) {
            Scoring.Result r = a.score(); Stats st = a.stats();
            JLabel w = Ui.text(reason(a, yieldFirst), Theme.body(12.5f), t.muted); w.setToolTipText(tip(a));
            JLabel y = Ui.mono(st.divYield > 0 ? Fmt.pctu(st.divYield, 1) : "—", 13f, st.divYield > 0 ? (yieldFirst ? t.accent : t.ink) : t.muted);
            if (st.div.pays()) y.setToolTipText(st.div.count() + " payments totaling " + Fmt.money2(st.div.ttm()) + " a share in the past year");
            g.row(() -> app.analyze(a.symbol()), Ui.mono(String.valueOf(n++), 12.5f, t.muted), MarketsView.nameCell(app, a.symbol(), a), Kit.score(r, false),
                y, Kit.pct(st.r3m, 0, 13f), Kit.pct(st.r1y, 0, 13f), Ui.mono(Fmt.pctu(st.vol1y), 13f, t.ink), r == null ? null : Kit.callPill(r.call()), w);
        }
        c.add(g);
        List<String> notes = new ArrayList<>();
        if (sc != null && failed > 0) notes.add(failed + " didn't pass (most often: " + topReasons(why) + ").");
        if (!none.isEmpty()) notes.add("Not ranked yet (no saved prices): " + String.join(", ", none) + ".");
        notes.add("Click a row to see the full breakdown in Research. Hover the reason to see all of them.");
        for (String x : notes) c.add(Ui.hint(x));
        list.set(c);
    }
    private static String topReasons(Map<String, Integer> why) {
        List<Map.Entry<String, Integer>> e = new ArrayList<>(why.entrySet()); e.sort((a, b) -> b.getValue() - a.getValue());
        List<String> out = new ArrayList<>(); for (int i = 0; i < Math.min(2, e.size()); i++) out.add(e.get(i).getKey() + " (" + e.get(i).getValue() + ")");
        return String.join(", ", out);
    }
    private static String reason(Analysis a, boolean yieldFirst) {
        Stats st = a.stats();
        if (yieldFirst && st.div.pays()) {
            double gr = st.div.growth();
            String g = Double.isNaN(gr) ? "" : Math.abs(gr) < 0.02 ? " · steady vs prior year" : (gr > 0 ? " · up " : " · down ") + Fmt.pctu(Math.abs(gr)) + " vs prior year";
            return Fmt.money2(st.div.ttm()) + "/share in " + st.div.count() + (st.div.count() == 1 ? " payment" : " payments") + g;
        }
        if (a.score() == null || a.score().reasons().isEmpty()) return "";
        List<Scoring.Reason> rs = a.score().reasons(); StringBuilder b = new StringBuilder(rs.get(0).text());
        if (rs.size() > 1 && b.length() < 60) b.append(" ").append(rs.get(1).text());
        return b.toString();
    }
    private static String tip(Analysis a) {
        StringBuilder t = new StringBuilder("<html>");
        if (a.stats().div.pays()) t.append("Dividends: ").append(Fmt.money2(a.stats().div.ttm())).append(" a share over 12 months (").append(Fmt.pctu(a.stats().divYield, 1)).append(" yield)<br>");
        if (a.score() != null) for (Scoring.Reason x : a.score().reasons()) t.append(x.good() ? "＋ " : "− ").append(x.text()).append("<br>");
        return t.toString();
    }
}

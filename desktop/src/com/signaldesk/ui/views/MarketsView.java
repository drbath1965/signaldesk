package com.signaldesk.ui.views;

import com.signaldesk.calc.Dates;
import com.signaldesk.calc.Fmt;
import com.signaldesk.learn.Learn;
import com.signaldesk.model.Practice;
import com.signaldesk.signals.*;
import com.signaldesk.store.PriceStore;
import com.signaldesk.store.Universe;
import com.signaldesk.ui.*;
import com.signaldesk.ui.PriceService.Analysis;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Home: market indexes, the watchlist with scores, top-scoring symbols, practice and learning at a glance. */
public final class MarketsView extends View {
    private final Slot body = new Slot(), status = new Slot();
    public MarketsView(MainFrame app) { super(app); }

    @Override public JComponent build() {
        Ui.VBox root = new Ui.VBox(22);
        Ui.HBox right = new Ui.HBox(12);
        right.add(status);
        right.add(Ui.btn("Update prices", () -> { app.priceService().refreshEveryday(true, null); refreshStatus(); }));
        root.add(Kit.head(Dates.weekday(Dates.today()), "Markets", "Indexes, your watchlist and how each one scores today. Click any row to research it.", right));
        root.add(body);
        refresh();
        return root;
    }

    @Override public void refresh() { refreshStatus(); body.set(content()); }

    private void refreshStatus() {
        PriceService ps = app.priceService(); Theme t = Theme.T;
        String s; Color c = t.muted;
        if (ps.busy()) { s = "Updating prices…"; c = t.accent; }
        else if (ps.problem() != null) { s = ps.problem(); c = t.warn; }
        else if (ps.updated() != null) s = "Prices updated " + ResearchView.ago(ps.updated());
        else s = Boolean.getBoolean("signaldesk.offline") ? "Offline: showing saved prices" : "";
        JLabel l = Ui.text(s, Theme.body(12.5f), c); status.set(l);
    }

    private JComponent content() {
        Ui.VBox v = new Ui.VBox(22);
        v.add(indexes());
        v.add(Ui.rail(Ui.top(watchlist()), side(), 340));
        v.add(Kit.disclaimer());
        return v;
    }

    // ------------------------------------------------------------------ index tiles
    private JComponent indexes() {
        List<JComponent> tiles = new ArrayList<>();
        for (String[] ix : Universe.INDEXES) tiles.add(tile(ix[0], ix[1]));
        return Ui.cols(12, tiles.toArray(new JComponent[0]));
    }
    private JComponent tile(String sym, String label) {
        Theme t = Theme.T; PriceSeries p = app.prices().get(sym);
        Ui.Card c = new Ui.Card(6) {
            boolean hov;
            { setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
              addMouseListener(new java.awt.event.MouseAdapter() {
                  @Override public void mouseClicked(java.awt.event.MouseEvent e) { app.analyze(sym); }
                  @Override public void mouseEntered(java.awt.event.MouseEvent e) { hov = true; repaint(); }
                  @Override public void mouseExited(java.awt.event.MouseEvent e) { hov = false; repaint(); } }); }
            @Override protected void paintComponent(Graphics g) { super.paintComponent(g); if (hov) { Graphics2D g2 = Ui.aa(g); g2.setColor(t.accent); g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14); g2.dispose(); } }
        };
        c.setBorder(new EmptyBorder(14, 16, 12, 16));
        c.add(Ui.caps(label), false);
        if (p == null || p.size() < 2) { c.add(Ui.mono("—", 18f, t.muted), false); c.add(Ui.hint("No prices yet"), false); return c; }
        double[] x = p.close(); int L = x.length - 1; double d = x[L] / x[L - 1] - 1;
        c.add(Ui.mono(sym.startsWith("^") ? Fmt.num(x[L], 0).replaceAll("(\\d)(?=(\\d{3})+$)", "$1,") : Kit.price(x[L]), 18f, t.ink), false);
        Ui.HBox h = new Ui.HBox(8); h.add(Kit.pct(d, 2, 12.5f)); h.glue(); h.add(Kit.spark(p, 63, 84, 26));
        c.add(h);
        return c;
    }

    // ------------------------------------------------------------------ watchlist
    private JComponent watchlist() {
        Theme t = Theme.T;
        Ui.Card c = Kit.card("Watchlist", Help.q(app, "score"));
        JTextField f = Ui.field("", 10); f.setFont(Theme.mono(14f)); f.setToolTipText("Ticker such as MSFT, VOO, VFIAX or BTC-USD");
        Runnable add = () -> {
            String sym = PriceStore.clean(f.getText()); if (sym.isEmpty()) return;
            if (s().watchlist.contains(sym)) { app.toast(sym + " is already on your watchlist", null); return; }
            s().watchlist.add(sym); f.setText(""); app.changed();
            app.priceService().fetch(sym, false, p -> app.refreshCurrent(), ex -> {
                s().watchlist.remove(sym); app.changed();
                app.toast("Couldn't add " + sym + ": " + PriceService.explain(ex), null);
            });
            app.toast("Getting prices for " + sym + "…", null);
        };
        f.addActionListener(e -> add.run());
        Ui.HBox ar = new Ui.HBox(8); f.setPreferredSize(new Dimension(150, 34)); ar.add(f); ar.add(Ui.small("Add to watchlist", add)); ar.glue();
        ar.add(Ui.text("Stocks, ETFs, mutual funds and crypto (BTC-USD)", Theme.body(12f), t.muted));
        c.add(ar);
        if (s().watchlist.isEmpty()) { c.add(Kit.empty("Your watchlist is empty. Add a ticker above, or find ideas on the Picks page.")); return c; }
        Kit.Grid g = new Kit.Grid(new String[]{"Symbol", "Price", "Day", "1 month", "1 year", "Score", "Call", ""}, new int[]{-150, 84, 64, 70, 70, 46, 46, 18}, "_rrrr___");
        for (String sym : new ArrayList<>(s().watchlist)) {
            Analysis a = app.priceService().analysis(sym);
            JComponent name = nameCell(sym, a);
            JComponent x = removeX(sym);
            if (a == null) { g.row(() -> app.analyze(sym), name, Ui.mono("—", 13f, t.muted), null, null, null, null, null, x); continue; }
            Stats st = a.stats();
            g.row(() -> app.analyze(sym), name, Ui.mono(Kit.price(st.last), 13f, t.ink), Kit.pct(st.dayChange, 1, 13f), Kit.pct(st.r1m, 1, 13f), Kit.pct(st.r1y, 0, 13f),
                Kit.score(a.score(), true), a.score() == null ? null : Kit.callPill(a.score().call()), x);
        }
        c.add(g);
        return c;
    }
    static JComponent nameCell(MainFrame app, String sym, Analysis a) {
        Theme t = Theme.T; Ui.VBox v = new Ui.VBox(1);
        Ui.HBox top = new Ui.HBox(8); top.add(Ui.text(sym, Theme.monoBold(13.5f), t.ink));
        AssetType ty = a != null ? a.type() : app.prices().type(sym);
        top.add(Ui.text(ty.label, Theme.body(11.5f), t.muted));
        v.add(top, false);
        String nm = a != null ? a.name() : app.prices().name(sym);
        JLabel n = Ui.text(nm == null || nm.isBlank() ? (a == null ? "Prices not downloaded yet" : " ") : nm, Theme.body(12f), t.muted);
        n.setPreferredSize(new Dimension(10, n.getPreferredSize().height)); v.add(n, true);
        return v;
    }
    private JComponent nameCell(String sym, Analysis a) { return nameCell(app, sym, a); }
    private JComponent removeX(String sym) {
        Ui.Btn b = new Ui.Btn("×", Ui.Btn.Kind.LINK, true);
        b.setFont(Theme.body(16f)); b.setToolTipText("Remove " + sym + " from the watchlist");
        b.addActionListener(e -> {
            int i = s().watchlist.indexOf(sym); s().watchlist.remove(sym); app.changed(); refresh();
            app.toast("Removed " + sym, () -> s().watchlist.add(Math.min(i, s().watchlist.size()), sym));
        });
        return b;
    }

    // ------------------------------------------------------------------ side column
    private JComponent side() {
        Ui.VBox v = new Ui.VBox(18);
        v.add(topScores()); v.add(practice()); v.add(learning());
        return v;
    }
    private JComponent topScores() {
        Ui.Card c = Kit.card("Highest scores", Ui.link("Picks", () -> app.navigate("picks")));
        List<Analysis> all = new ArrayList<>();
        for (String sym : app.prices().symbols()) { Analysis a = app.priceService().analysis(sym); if (a != null && a.score() != null) all.add(a); }
        all.sort(Comparator.comparingDouble((Analysis a) -> -a.score().score()));
        if (all.isEmpty()) { c.add(Kit.empty("Scores appear here once prices download. Run a screen on the Picks page to rank dozens of stocks, funds and coins.")); return c; }
        c.add(Ui.hint("From the " + all.size() + " symbols with saved prices."));
        Kit.Grid g = new Kit.Grid(new String[]{"Symbol", "1 year", "Score"}, new int[]{-120, 60, 46}, "_rr");
        for (Analysis a : all.subList(0, Math.min(6, all.size()))) g.row(() -> app.analyze(a.symbol()), nameCell(a.symbol(), a), Kit.pct(a.stats().r1y, 0, 13f), Kit.score(a.score(), true));
        c.add(g);
        return c;
    }
    private JComponent practice() {
        Theme t = Theme.T; Practice p = s().practice;
        Ui.Card c = Kit.card("Practice portfolio", Ui.link("Open", () -> app.navigate("practice")));
        double val = PracticeView.value(app);
        double ch = val / p.startCash - 1;
        Ui.HBox h = new Ui.HBox(12); h.add(Ui.mono(Fmt.money(val), 22f, t.ink)); h.add(Kit.pct(ch, 1, 14f));
        c.add(h);
        c.add(Ui.hint(p.trades.isEmpty() ? "Start with " + Fmt.money(p.startCash) + " of pretend money at real prices. No risk, real lessons."
            : p.positions().size() + " positions · " + Fmt.money(p.cash()) + " cash · " + p.trades.size() + " trades"));
        return c;
    }
    private JComponent learning() {
        Theme t = Theme.T; Learn L = Learn.get();
        int done = 0; Learn.Lesson next = null;
        for (Learn.Lesson l : L.lessons) { if (s().lessonDone(l.id())) done++; else if (next == null) next = l; }
        Ui.Card c = Kit.card("Learning", Ui.link("All lessons", () -> app.navigate("learn")));
        c.add(Ui.text(done + " of " + L.lessons.size() + " lessons complete", Theme.semibold(13.5f), t.ink));
        c.add(Ui.meter(L.lessons.isEmpty() ? 0 : (double) done / L.lessons.size(), false, null));
        if (next != null) { Learn.Lesson n = next; c.add(Ui.link("Next: " + n.title() + " (" + n.minutes() + " min)", () -> app.learn("lesson:" + n.id())), false); }
        else c.add(Ui.hint("Every lesson complete. Retake any quiz to keep it fresh."));
        return c;
    }
}

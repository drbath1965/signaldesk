package com.signaldesk.ui.views;

import com.signaldesk.calc.Dates;
import com.signaldesk.calc.Fmt;
import com.signaldesk.model.Practice;
import com.signaldesk.signals.PriceSeries;
import com.signaldesk.store.PriceStore;
import com.signaldesk.store.Universe;
import com.signaldesk.ui.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/** Paper trading: pretend money, real prices. Buy and sell at the latest close and compare with the S&P 500. */
public final class PracticeView extends View {
    private final String preset;
    private final Slot kpis = new Slot(), ticketPreview = new Slot(), positions = new Slot(), history = new Slot(), chart = new Slot();
    private JTextField symF, qtyF;
    private boolean buy = true, dollars = true;
    private final Slot sideSeg = new Slot(), unitSeg = new Slot();
    private boolean busy;

    public PracticeView(MainFrame app, String preset) { super(app); this.preset = preset; }
    private Practice p() { return s().practice; }

    /** Cash plus every position at its latest saved price. */
    static double value(MainFrame app) {
        Practice p = app.studio().practice; double v = p.cash();
        for (Map.Entry<String, Practice.Position> e : p.positions().entrySet()) v += e.getValue().shares * last(app, e.getKey(), e.getValue().avgCost());
        return v;
    }
    private static double last(MainFrame app, String sym, double fallback) { PriceSeries s = app.prices().get(sym); return s == null ? fallback : s.last(); }

    @Override public JComponent build() {
        Ui.VBox root = new Ui.VBox(20);
        Ui.HBox right = new Ui.HBox(10);
        right.add(Help.q(app, "practice"));
        right.add(Ui.small("Reset portfolio…", () -> {
            if (JOptionPane.showConfirmDialog(app, "Erase every practice trade and start again with " + Fmt.money(Practice.START_CASH) + " of pretend cash?", "Reset practice portfolio", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
            Practice old = s().practice; s().practice = new Practice(); app.changed(); app.rebuild();
            app.toast("Practice portfolio reset", () -> s().practice = old);
        }));
        root.add(Kit.head("Paper trading", "Practice portfolio", "Try your ideas with " + Fmt.money(p().startCash) + " of pretend money at real prices. Trades fill at the latest close with no fees. Nothing here touches a real account.", right));
        root.add(kpis);
        root.add(Ui.rail(Ui.top(chart), ticket(), 340));
        root.add(positions);
        root.add(history);
        refresh();
        if (!p().positions().isEmpty() && !Boolean.getBoolean("signaldesk.offline")) {
            List<String> syms = new ArrayList<>(p().positions().keySet()); syms.add(Universe.BENCHMARK);
            SwingUtilities.invokeLater(() -> app.priceService().refresh(syms, false, null, (g, f) -> { if (app.section().equals("practice")) refresh(); }));
        }
        return root;
    }

    @Override public void refresh() {
        drawKpis(); drawChart(); drawPositions(); drawHistory(); preview();
    }

    // ------------------------------------------------------------------ summary
    private void drawKpis() {
        Theme t = Theme.T; Practice P = p();
        double val = value(app), ret = val / P.startCash - 1, unreal = 0;
        for (Map.Entry<String, Practice.Position> e : P.positions().entrySet()) unreal += e.getValue().shares * last(app, e.getKey(), e.getValue().avgCost()) - e.getValue().cost;
        double sp = spReturn();
        kpis.set(Ui.kpis(
            new Ui.Kpi("Total value", Fmt.money(val), P.trades.isEmpty() ? "Starting cash" : "Started " + Dates.dayLong(P.started)),
            new Ui.Kpi("Your return", Kit.signedPct(ret, 1), Kit.signColor(ret), Kit.signedMoney(val - P.startCash)),
            new Ui.Kpi("S&P 500, same period", Double.isNaN(sp) ? "—" : Kit.signedPct(sp, 1), Double.isNaN(sp) ? null : Kit.signColor(sp),
                Double.isNaN(sp) ? "Shown after your first trade" : (ret >= sp ? "You're ahead by " : "You're behind by ") + Fmt.pctu(Math.abs(ret - sp), 1)),
            new Ui.Kpi("Cash", Fmt.money(P.cash()), Fmt.pctu(val > 0 ? P.cash() / val : 0) + " of the portfolio"),
            new Ui.Kpi("Gains", Kit.signedMoney(unreal + P.realized()), "Unrealized " + Kit.signedMoney(unreal) + " · sold " + Kit.signedMoney(P.realized()))));
    }
    /** S&P 500 change from the practice start date to its latest close. */
    private double spReturn() {
        PriceSeries b = app.prices().get(Universe.BENCHMARK);
        if (b == null || p().started.isBlank()) return Double.NaN;
        int i = idx(b, p().started); return b.last() / b.close()[i] - 1;
    }
    private static int idx(PriceSeries s, String date) {
        int lo = 0, hi = s.size() - 1;
        while (lo < hi) { int m = (lo + hi + 1) / 2; if (s.dates().get(m).compareTo(date) <= 0) lo = m; else hi = m - 1; }
        return lo;
    }

    private void drawChart() {
        Theme t = Theme.T; Practice P = p();
        Ui.Card c = Kit.card("Value over time", Help.q(app, "vs-sp"));
        PriceSeries b = app.prices().get(Universe.BENCHMARK);
        if (P.trades.isEmpty() || b == null) {
            c.add(Kit.empty(P.trades.isEmpty() ? "Place your first practice trade with the ticket on the right. Your portfolio's value will be charted here against the S&P 500."
                : "The S&P 500 comparison appears once its prices download."));
            chart.set(c); return;
        }
        int a = idx(b, P.started);
        if (b.dates().get(a).compareTo(P.started) < 0 && a < b.size() - 1) a++;
        int n = b.size() - a;
        if (n < 2) { c.add(Kit.empty("The chart fills in after the next market day.")); chart.set(c); return; }
        double[] mine = new double[n], sp = new double[n]; String[] lab = new String[n];
        Map<String, PriceSeries> ser = new HashMap<>();
        for (Practice.Trade x : P.trades) ser.computeIfAbsent(x.symbol(), k -> app.prices().get(k));
        for (int k = 0; k < n; k++) {
            String d = b.dates().get(a + k); lab[k] = Dates.dayShort(d);
            double cash = P.startCash; Map<String, Double> sh = new HashMap<>();
            for (Practice.Trade x : P.trades) {
                if (x.date().compareTo(d) > 0) continue;
                cash += x.buy() ? -x.amount() : x.amount(); sh.merge(x.symbol(), x.buy() ? x.shares() : -x.shares(), Double::sum);
            }
            double v = cash;
            for (Map.Entry<String, Double> e : sh.entrySet()) {
                PriceSeries s = ser.get(e.getKey());
                double px = s == null ? 0 : s.close()[idx(s, d)];
                if (s == null) for (Practice.Trade x : P.trades) if (x.symbol().equals(e.getKey())) px = x.price();
                v += e.getValue() * px;
            }
            mine[k] = v; sp[k] = P.startCash * b.close()[a + k] / b.close()[a];
        }
        Charts.Line ln = new Charts.Line(lab, v -> Fmt.compact(v), 220).xTicks(5);
        ln.add(new Charts.Series(sp, t.slow, 1.6f, true, false, false));
        ln.add(new Charts.Series(mine, t.accent, 2.2f, false, true, true));
        c.add(Kit.legend("Practice portfolio", t.accent, "Same money in the S&P 500", t.slow));
        c.add(ln);
        chart.set(c);
    }

    // ------------------------------------------------------------------ order ticket
    private JComponent ticket() {
        Theme t = Theme.T;
        Ui.Card c = Kit.card("Order ticket", null);
        symF = Ui.field(preset == null ? "" : PriceStore.clean(preset), 8); symF.setFont(Theme.mono(15f));
        qtyF = Ui.field("1000", 8); qtyF.setFont(Theme.mono(15f));
        javax.swing.event.DocumentListener dl = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { preview(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { preview(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { preview(); }
        };
        symF.getDocument().addDocumentListener(dl); qtyF.getDocument().addDocumentListener(dl);
        drawSegs();
        c.add(sideSeg);
        c.add(Ui.labeled("Symbol", symF));
        Ui.HBox q = new Ui.HBox(8); q.grow(qtyF); q.add(unitSeg);
        c.add(Ui.labeled("Amount", q));
        c.add(ticketPreview);
        Ui.HBox go = new Ui.HBox(8);
        go.add(Ui.primary("Place practice order", this::place));
        symF.addActionListener(e -> place()); qtyF.addActionListener(e -> place());
        go.glue();
        go.add(Ui.link("Research", () -> { String s = PriceStore.clean(symF.getText()); if (!s.isEmpty()) app.analyze(s); }));
        c.add(go);
        c.add(Ui.hint("Fractional shares are allowed. Prices are the latest saved close; the app refreshes them before filling."));
        return c;
    }
    private void drawSegs() {
        sideSeg.set(Ui.seg(new String[]{"Buy", "Sell"}, buy ? 0 : 1, i -> { buy = i == 0; drawSegs(); preview(); }));
        unitSeg.set(Ui.seg(new String[]{"Dollars", "Shares"}, dollars ? 0 : 1, i -> { dollars = i == 0; drawSegs(); preview(); }));
    }
    private record Order(String sym, double price, double shares, String error) {}
    private Order order() {
        String sym = PriceStore.clean(symF == null ? "" : symF.getText());
        if (sym.isEmpty()) return new Order(sym, 0, 0, "Type a symbol such as AAPL, VOO, VFIAX or BTC-USD.");
        Double q = Ui.parse(qtyF.getText());
        PriceSeries s = app.prices().get(sym);
        if (s == null) return new Order(sym, 0, 0, "No saved price for " + sym + " yet. Placing the order downloads it first.");
        double px = s.last();
        if (q == null || q <= 0) return new Order(sym, px, 0, "Enter an amount above zero.");
        double sh = dollars ? q / px : q;
        sh = Math.floor(sh * 1e6) / 1e6;
        Practice.Position pos = p().positions().get(sym);
        if (buy && sh * px > p().cash() + 0.005) return new Order(sym, px, sh, "That costs " + Fmt.money2(sh * px) + " but you have " + Fmt.money2(p().cash()) + " in cash.");
        if (!buy && (pos == null || pos.shares < sh - 1e-6)) return new Order(sym, px, sh, pos == null ? "You don't own any " + sym + "." : "You own " + fmtSh(pos.shares) + " shares of " + sym + ".");
        if (!buy && pos != null && Math.abs(pos.shares - sh) < 1e-4) sh = pos.shares;
        return new Order(sym, px, sh, null);
    }
    private void preview() {
        if (symF == null) return;
        Theme t = Theme.T; Order o = order();
        Ui.VBox v = new Ui.VBox(3);
        if (o.price() > 0) {
            String nm = app.prices().name(o.sym());
            v.add(Ui.text((nm.isBlank() ? o.sym() : nm) + " · last " + Kit.price(o.price()) + " on " + Dates.dayShort(app.prices().get(o.sym()).lastDate()), Theme.body(12.5f), t.muted), false);
        }
        if (o.error() != null) v.add(Ui.para(o.error(), Theme.body(13f), o.price() > 0 && !o.error().startsWith("No saved") ? t.sell : t.muted));
        else v.add(Ui.text((buy ? "Buy " : "Sell ") + fmtSh(o.shares()) + " shares for " + Fmt.money2(o.shares() * o.price()), Theme.semibold(13.5f), t.ink), false);
        ticketPreview.set(v);
    }
    private void place() {
        if (busy) return;
        Order o = order();
        if (o.sym().isEmpty()) { symF.requestFocusInWindow(); return; }
        busy = true;
        app.priceService().fetch(o.sym(), false, s -> {
            busy = false;
            Order f = order();
            if (f.error() != null) { preview(); app.toast(f.error(), null); return; }
            // dated by the close it filled at, so the chart and the S&P comparison line up with real market days
            String day = app.prices().get(f.sym()).lastDate();
            if (p().started.isBlank() || day.compareTo(p().started) < 0) p().started = day;
            p().trades.add(new Practice.Trade(day, f.sym(), buy, f.shares(), f.price()));
            app.changed(); refresh();
            app.toast((buy ? "Bought " : "Sold ") + fmtSh(f.shares()) + " " + f.sym() + " at " + Kit.price(f.price()) + " (practice)", () -> p().trades.remove(p().trades.size() - 1));
            if (!app.prices().has(Universe.BENCHMARK)) app.priceService().fetch(Universe.BENCHMARK, false, b -> refresh(), ex -> {});
        }, ex -> { busy = false; app.toast("Couldn't get a price for " + o.sym() + ": " + PriceService.explain(ex), null); preview(); });
    }
    static String fmtSh(double s) { return Math.abs(s - Math.rint(s)) < 1e-9 ? String.format(Locale.US, "%,.0f", s) : String.format(Locale.US, "%,.4f", s).replaceAll("0+$", ""); }

    // ------------------------------------------------------------------ positions and history
    private void drawPositions() {
        Theme t = Theme.T; Map<String, Practice.Position> pos = p().positions();
        Ui.Card c = Kit.card("Positions", Ui.muted(pos.size() + (pos.size() == 1 ? " holding" : " holdings")));
        if (pos.isEmpty()) { c.add(Kit.empty("No positions yet. Buy something with the order ticket to start.")); positions.set(c); return; }
        double total = value(app);
        Kit.Grid g = new Kit.Grid(new String[]{"Symbol", "Shares", "Avg cost", "Price", "Value", "Gain", "Gain %", "Weight", ""},
            new int[]{-150, 84, 84, 84, 96, 90, 64, 56, 70}, "_rrrrrrr_");
        for (Map.Entry<String, Practice.Position> e : pos.entrySet()) {
            String sym = e.getKey(); Practice.Position x = e.getValue();
            double px = last(app, sym, x.avgCost()), val = x.shares * px, gain = val - x.cost, gp = x.cost > 0 ? gain / x.cost : 0;
            Ui.Btn tr = Ui.small("Trade", () -> { symF.setText(sym); buy = false; dollars = false; qtyF.setText(fmtSh(x.shares).replace(",", "")); drawSegs(); preview(); qtyF.requestFocusInWindow(); });
            g.row(() -> app.analyze(sym), MarketsView.nameCell(app, sym, app.priceService().analysis(sym)), Ui.mono(fmtSh(x.shares), 13f, t.ink), Ui.mono(Kit.price(x.avgCost()), 13f, t.muted),
                Ui.mono(Kit.price(px), 13f, t.ink), Ui.mono(Fmt.money(val), 13f, t.ink), Ui.mono(Kit.signedMoney(gain), 13f, Kit.signColor(gain)), Kit.pct(gp, 1, 13f),
                Ui.mono(Fmt.pctu(total > 0 ? val / total : 0), 13f, t.muted), tr);
        }
        c.add(g);
        positions.set(c);
    }
    private void drawHistory() {
        Theme t = Theme.T; List<Practice.Trade> tr = new ArrayList<>(p().trades); Collections.reverse(tr);
        Ui.Card c = Kit.card("Trade history", Ui.muted(tr.size() + " trades, newest first"));
        if (tr.isEmpty()) { c.add(Kit.empty("Your practice trades will be listed here.")); history.set(c); return; }
        Kit.Grid g = new Kit.Grid(new String[]{"Date", "Side", "Symbol", "Shares", "Price", "Amount"}, new int[]{110, 50, -120, 100, 90, 110}, "___rrr");
        for (Practice.Trade x : tr.subList(0, Math.min(200, tr.size())))
            g.row(() -> app.analyze(x.symbol()), Ui.text(Dates.dayLong(x.date()), Theme.body(13f), t.ink), Ui.pill(x.buy() ? "Buy" : "Sell", x.buy() ? Ui.Tone.POS : Ui.Tone.NEG),
                Ui.text(x.symbol(), Theme.monoBold(13f), t.ink), Ui.mono(fmtSh(x.shares()), 13f, t.ink), Ui.mono(Kit.price(x.price()), 13f, t.muted), Ui.mono(Fmt.money2(x.amount()), 13f, t.ink));
        c.add(g);
        history.set(c);
    }
}

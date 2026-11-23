package com.signaldesk.ui.views;

import com.signaldesk.calc.*;
import com.signaldesk.model.*;
import com.signaldesk.signals.*;
import com.signaldesk.store.PriceDownloader;
import com.signaldesk.ui.*;
import com.signaldesk.ui.EditTable.Col;
import com.signaldesk.ui.PriceService;
import java.awt.Color;
import java.util.*;
import javax.swing.*;

public final class InvestmentsView extends View {
    private final Slot top = new Slot();
    private EditTable<Holding> holdings;
    private EditTable<String> alloc;
    private final Slot donut = new Slot();
    private JLabel targetSum;
    private final JLabel priceStatus = Ui.text(" ", Theme.body(12.5f), Theme.T.muted);

    public InvestmentsView(MainFrame app) { super(app); }

    Color clsColor(String cls) { int i = Arrays.asList(Holding.CLASSES).indexOf(cls); return Theme.T.series[Math.max(0, i) % 6]; }

    private SignalEngine.Verdict signalFor(String sym) {
        if (sym == null || sym.isBlank()) return null;
        PriceSeries p = app.prices().get(sym); if (p == null || p.size() < 80) return null;
        SignalEngine e = SignalEngine.run(p, s().sig); return e.error != null ? null : e.verdict();
    }

    @Override public JComponent build() {
        Theme t = Theme.T;
        Ui.VBox root = new Ui.VBox(20);
        Ui.HBox actions = new Ui.HBox(8);
        actions.add(priceStatus);
        actions.add(Ui.btn("Refresh prices", () -> { app.priceService().refreshHoldings(true, null); refresh(); }));
        root.add(Kit.head("Investments", "Portfolio", "Enter what you hold. Prices update automatically from Yahoo Finance when the app opens and every 15 minutes you're here, and they power each holding's buy/sell signal.", actions));
        root.add(top);

        targetSum = Ui.text("", Theme.body(12.5f), Theme.T.muted);
        Ui.Card ac = Kit.card("Allocation vs target", targetSum);
        List<String> classes = new ArrayList<>();
        alloc = new EditTable<>(classes, app::changed, null, null,
            Col.<String>label("Class", 140, c -> c, null),
            Col.<String>calc("Value", 110, c -> Fmt.money(Portfolio.of(s()).byClass.getOrDefault(c, 0.0)), null),
            Col.<String>calc("Now", 80, c -> Fmt.num(Portfolio.of(s()).weightPct(c), 1) + "%", null),
            Col.<String>number("Target %", 90, c -> s().targets.getOrDefault(c, 0.0), (c, v) -> s().targets.put(c, Math.max(0, Math.min(100, v)))),
            Col.<String>calc("To rebalance", 130, this::rebalanceText, this::rebalanceColor));
        Ui.HBox row = new Ui.HBox(24); row.add(donut); row.grow(alloc.wrap());
        ac.add(row); root.add(ac);

        Ui.Card hc = Kit.card("Holdings", null);
        Object[] clsOpts = Holding.CLASSES;
        holdings = new EditTable<>(s().holdings, () -> { app.changed(); holdings.refreshRows(); }, h -> {
            int i = s().holdings.indexOf(h); s().holdings.remove(h); app.changed(); app.rebuild();
            app.toast("Deleted " + (h.sym.isBlank() ? "holding" : h.sym), () -> s().holdings.add(i, h));
        }, "No holdings yet. Add your funds and stocks.",
            Col.<Holding>text("Symbol", 80, h -> h.sym, (h, v) -> h.sym = v.trim().toUpperCase()).placeholder("VTI"),
            Col.<Holding>choice("Asset class", 120, () -> clsOpts, h -> h.cls, (h, v) -> h.cls = (String) v),
            Col.<Holding>number("Shares", 90, h -> h.shares, (h, v) -> h.shares = v),
            Col.<Holding>money("Cost / share", 100, h -> h.cost, (h, v) -> h.cost = v),
            Col.<Holding>money("Price now", 100, h -> h.price, (h, v) -> h.price = v),
            Col.<Holding>calc("Value", 110, h -> Fmt.money(h.value()), null),
            Col.<Holding>calc("Gain", 150, h -> h.costBasis() == 0 ? "—" : Fmt.money(h.value() - h.costBasis()) + "  " + Fmt.pct(h.value() / h.costBasis() - 1), h -> h.costBasis() == 0 ? t.muted : h.value() >= h.costBasis() ? t.buy : t.sell),
            Col.<Holding>calc("Signal", 90, h -> { if ("Cash".equals(h.cls) || h.sym.isBlank()) return ""; var v = signalFor(h.sym); return v == null ? "Analyze ›" : word(v.call()); },
                h -> { var v = "Cash".equals(h.cls) ? null : signalFor(h.sym); return v == null ? t.accent : switch (v.call()) { case BUY -> t.buy; case SELL -> t.sell; case HOLD -> t.accent; default -> t.muted; }; }),
            Col.delete());
        holdings.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                int r = holdings.rowAtPoint(e.getPoint()), c = holdings.columnAtPoint(e.getPoint());
                if (r >= 0 && c == 7 && r < s().holdings.size()) { Holding h = s().holdings.get(r); if (!h.sym.isBlank() && !"Cash".equals(h.cls)) app.analyze(h.sym); }
            }
        });
        hc.add(holdings.wrap());
        hc.add(Ui.small("+ Add holding", () -> { s().holdings.add(new Holding("", "US stocks", 0, 0, 0)); app.changed(); app.rebuild(); }), false);
        hc.add(Ui.hint("Prices fill in on their own; you can still type one over. Click a signal to chart that stock. For cash or money-market funds, use price 1."));
        root.add(hc);
        refresh();
        boolean stale = s().holdings.stream().anyMatch(h -> !h.sym.isBlank() && !"Cash".equals(h.cls) && !app.prices().isFresh(h.sym));
        if (stale) SwingUtilities.invokeLater(() -> { app.priceService().refreshHoldings(false, null); refresh(); });
        return root;
    }
    static String word(SignalEngine.Call c) { return switch (c) { case BUY -> "Buy"; case SELL -> "Sell"; case HOLD -> "Hold"; default -> "Wait"; }; }

    private double diff(String c) { Portfolio pf = Portfolio.of(s()); return (s().targets.getOrDefault(c, 0.0) - pf.weightPct(c)) / 100 * pf.value; }
    private String rebalanceText(String c) { double d = diff(c); return Math.abs(d) < 1 ? "—" : (d > 0 ? "Buy " : "Sell ") + Fmt.money(Math.abs(d)); }
    private Color rebalanceColor(String c) {
        Portfolio pf = Portfolio.of(s()); double gap = Math.abs(s().targets.getOrDefault(c, 0.0) - pf.weightPct(c));
        return gap < 5 ? Theme.T.muted : diff(c) > 0 ? Theme.T.buy : Theme.T.sell;
    }

    @Override public void refresh() {
        Theme t = Theme.T; Portfolio pf = Portfolio.of(s());
        top.set(Ui.kpis(
            new Ui.Kpi("Portfolio value", Fmt.money(pf.value), s().holdings.size() + " holdings"),
            new Ui.Kpi("Total gain", Fmt.money(pf.gain()), pf.gain() >= 0 ? t.buy : t.sell, Fmt.pct(pf.gainPct()) + " on " + Fmt.money(pf.cost) + " invested"),
            new Ui.Kpi("Largest holding", pf.largest == null || pf.largest.sym.isBlank() ? "—" : pf.largest.sym, pf.largest != null && pf.value > 0 ? Fmt.pctu(pf.largest.value() / pf.value) + " of portfolio" : "")));
        List<String> present = new ArrayList<>();
        for (String c : Holding.CLASSES) if (pf.byClass.getOrDefault(c, 0.0) > 0 || s().targets.getOrDefault(c, 0.0) > 0) present.add(c);
        double[] vals = present.stream().mapToDouble(c -> pf.byClass.getOrDefault(c, 0.0)).toArray();
        donut.set(Charts.donut(vals, present.stream().map(this::clsColor).toArray(Color[]::new), 150));
        if (alloc != null) {
            List<String> rows = alloc.rows();
            if (!rows.equals(present)) { rows.clear(); rows.addAll(present); alloc.rowsChanged(); } else alloc.refreshRows();
        }
        double tsum = s().targets.values().stream().mapToDouble(Double::doubleValue).sum();
        if (targetSum != null) { targetSum.setText("Targets add up to " + Ui.fmtNum(tsum, 1) + "%"); targetSum.setForeground(Math.abs(tsum - 100) > .01 ? t.sell : t.muted); }
        if (holdings != null) holdings.refreshRows();
        PriceService ps = app.priceService();
        if (ps.holdingsBusy()) { priceStatus.setText("Updating prices…"); priceStatus.setForeground(t.accent); }
        else if (ps.holdingsProblem() != null) { priceStatus.setText(ps.holdingsProblem()); priceStatus.setForeground(t.warn); }
        else if (ps.holdingsUpdated() != null) { priceStatus.setText("Prices updated " + SignalsView.ago(ps.holdingsUpdated())); priceStatus.setForeground(t.muted); }
        else priceStatus.setText(" ");
    }

}

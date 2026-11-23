package com.signaldesk.ui.views;

import com.signaldesk.calc.*;
import com.signaldesk.model.*;
import com.signaldesk.ui.*;
import com.signaldesk.ui.EditTable.Col;
import java.util.List;
import javax.swing.*;

public final class DebtView extends View {
    private final Slot summary = new Slot();
    public DebtView(MainFrame app) { super(app); }

    @Override public JComponent build() {
        Ui.VBox root = new Ui.VBox(20);
        root.add(Kit.head("Debt payoff", "Your path to debt-free", "Pay every minimum, then send the extra to one debt at a time. When a debt is gone, its minimum rolls into the next one.", null));
        root.add(summary);
        Ui.Card dc = Kit.card("Debts", null);
        EditTable<Debt> et = new EditTable<>(s().debts, app::changed, d -> {
            int i = s().debts.indexOf(d); s().debts.remove(d); app.changed(); app.rebuild();
            app.toast("Deleted " + (d.name.isBlank() ? "debt" : d.name), () -> s().debts.add(i, d));
        }, "No debts yet. Add credit cards, loans and your mortgage.",
            Col.<Debt>text("Debt", 170, d -> d.name, (d, v) -> d.name = v).placeholder("e.g. Credit card"),
            Col.<Debt>money("Balance", 110, d -> d.balance, (d, v) -> d.balance = v),
            Col.<Debt>number("APR %", 80, d -> d.apr, (d, v) -> d.apr = v),
            Col.<Debt>money("Minimum / mo", 110, d -> d.min, (d, v) -> d.min = v),
            Col.<Debt>check("In plan", 70, d -> d.plan, (d, v) -> d.plan = v),
            Col.delete());
        dc.add(et.wrap());
        dc.add(Ui.small("+ Add debt", () -> { s().debts.add(new Debt("", 0, 0, 0, true)); app.changed(); app.rebuild(); }), false);
        dc.add(Ui.hint("Untick “In plan” for debts you aren't paying down early, such as a low-rate mortgage. They still count in net worth."));

        Ui.Card sc = Kit.card("Strategy", null);
        boolean av = "avalanche".equals(s().debtMethod);
        sc.add(Ui.seg(new String[]{"Avalanche", "Snowball"}, av ? 0 : 1, i -> { s().debtMethod = i == 0 ? "avalanche" : "snowball"; app.changed(); app.rebuild(); }), false);
        sc.add(Ui.hint(av ? "Avalanche pays the highest interest rate first. It costs the least overall." : "Snowball pays the smallest balance first. Quick wins can keep you motivated."));
        sc.add(Ui.formRow("Extra each month", null, Ui.numberField(s().debtExtra, 2, v -> { s().debtExtra = Math.max(0, v); app.changed(); }), 112));
        Category extra = s().cats.stream().filter(c -> c.name.toLowerCase().contains("extra debt")).findFirst().orElse(null);
        if (extra != null) sc.add(Ui.hint("Your budget sets aside " + Fmt.money(extra.planned) + " a month for “" + extra.name + "”."));
        root.add(Ui.rail(Ui.top(dc), sc, 330));
        refresh();
        return root;
    }

    @Override public void refresh() {
        Theme t = Theme.T; DebtCalc dp = DebtCalc.of(s());
        if (dp.inPlan.isEmpty() || dp.total <= 0) {
            Ui.Card c = new Ui.Card(); c.add(Kit.empty("Add a debt below to see your payoff date, total interest and the order to pay things off.")); summary.set(c); return;
        }
        DebtCalc.Sim a = dp.plan, b = dp.alt, mn = dp.minsOnly;
        Double saved = mn.finite() ? mn.interest() - a.interest() : null;
        String otherName = "snowball".equals(dp.other) ? "Snowball" : "Avalanche";
        Ui.VBox v = new Ui.VBox(20);
        v.add(Ui.kpis(
            new Ui.Kpi("Debt-free", a.finite() ? Dates.afterMonths(a.months()) : "Not on this plan", a.finite() ? t.buy : t.sell, a.finite() ? Dates.monthsLabel(a.months()) + " from now" : "Payments don't outpace interest"),
            new Ui.Kpi("Interest you'll pay", Fmt.money(a.interest()), saved != null && saved > 0 ? Fmt.money(saved) + " less than minimums only" : "with the plan below"),
            new Ui.Kpi("Monthly payment", Fmt.money(dp.minTotal + dp.extra), Fmt.money(dp.minTotal) + " minimums + " + Fmt.money(dp.extra) + " extra"),
            new Ui.Kpi(otherName + " instead", b.finite() ? Dates.afterMonths(b.months()) : "—",
                !b.finite() ? "" : b.interest() > a.interest() + .5 ? Fmt.money(b.interest() - a.interest()) + " more interest" : b.interest() < a.interest() - .5 ? Fmt.money(a.interest() - b.interest()) + " less interest" : "Same interest")));
        int n = Math.min(Math.max(mn.finite() ? mn.months() : a.series().size() - 1, a.series().size() - 1) + 1, 601);
        String[] labels = new String[n]; for (int i = 0; i < n; i++) labels[i] = Dates.tick(Dates.add(Dates.cur(), i));
        Ui.Card ch = Kit.card("Balance over time", Kit.legend("Your plan", t.accent, "Minimums only", t.muted));
        ch.add(new Charts.Line(labels, Fmt::compact, 210).zero().xTicks(5)
            .add(new Charts.Series(pad(mn.series(), n), t.muted, 1.5f, true, false, false))
            .add(new Charts.Series(pad(a.series(), n), t.accent, 2f, false, true, false)));
        if (!mn.finite()) ch.add(Ui.hint("With minimums only, at least one balance never reaches zero."));
        Ui.Card oc = Kit.card("Payoff order", null);
        Ui.VBox ol = new Ui.VBox(0); int k = 0;
        for (DebtCalc.Payoff p : a.order()) {
            ol.add(Ui.listRow(Ui.mono(String.valueOf(k + 1), 13f, t.muted), p.name().isBlank() ? "Unnamed debt" : p.name(),
                Ui.muted(Fmt.money(p.startBalance()) + " at " + Ui.fmtNum(p.apr(), 2) + "%"),
                Ui.mono(p.paidOffMonth() != null ? Dates.afterMonths(p.paidOffMonth()) : "—", 13f, t.ink), k++ > 0));
        }
        oc.add(ol);
        if (!dp.underwater.isEmpty()) oc.add(Ui.para(String.join(", ", dp.underwater.stream().map(d -> d.name).toList()) + ": the minimum is less than the monthly interest.", Theme.body(13.5f), t.sell));
        v.add(Ui.cols(18, Ui.top(ch), Ui.top(oc)));
        summary.set(v);
    }
    private static double[] pad(List<Double> s, int n) {
        double[] o = new double[n];
        for (int i = 0; i < n; i++) o[i] = i < s.size() ? s.get(i) : (s.get(s.size() - 1) > 0 ? Double.NaN : 0);
        return o;
    }
}

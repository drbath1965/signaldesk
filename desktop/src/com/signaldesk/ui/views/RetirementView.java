package com.signaldesk.ui.views;

import com.signaldesk.calc.*;
import com.signaldesk.model.RetirementPlan;
import com.signaldesk.ui.*;
import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import javax.swing.*;

public final class RetirementView extends View {
    private final Slot kpis = new Slot(), chart = new Slot(), contrib = new Slot();
    public RetirementView(MainFrame app) { super(app); }

    private JComponent f(String label, String sub, ToDoubleFunction<RetirementPlan> get, java.util.function.ObjDoubleConsumer<RetirementPlan> set) {
        RetirementPlan r = s().retire;
        return Ui.formRow(label, sub, Ui.numberField(get.applyAsDouble(r), 2, v -> { set.accept(s().retire, v); app.changed(); }), 112);
    }
    private static JComponent group(String caps, JComponent... rows) {
        Ui.VBox v = new Ui.VBox(10); v.add(Ui.caps(caps), false); for (JComponent r : rows) v.add(r); return v;
    }

    @Override public JComponent build() {
        Theme t = Theme.T; RetirementPlan r = s().retire;
        Ui.VBox root = new Ui.VBox(20);
        root.add(Kit.head("Retirement", "Will your savings carry you?", "Shown in today's dollars, so the numbers feel like real spending power. Starting balances come from your retirement accounts on the Net worth page.", null));
        root.add(kpis);
        Ui.Card cc = Kit.card("Projected savings by age", Kit.legend("Traditional", t.series[0], "Roth", t.series[2], "Taxable", t.series[4]));
        cc.add(chart); cc.add(contrib);

        JCheckBox inc = new JCheckBox(); inc.setOpaque(false); inc.setSelected(r.includeTaxable);
        inc.addActionListener(e -> { s().retire.includeTaxable = inc.isSelected(); app.changed(); app.rebuild(); });
        Ui.Card form = new Ui.Card(18);
        form.add(group("You",
            f("Current age", null, x -> x.age, (x, v) -> x.age = v), f("Retire at age", null, x -> x.retireAge, (x, v) -> x.retireAge = v),
            f("Plan to age", "How long the money should last", x -> x.lifeExp, (x, v) -> x.lifeExp = v)));
        JComponent[] saving = {
            f("Salary", "Gross, per year", x -> x.salary, (x, v) -> x.salary = v), f("401(k) contribution %", "Of salary", x -> x.contribPct, (x, v) -> x.contribPct = v),
            f("Share going to Roth %", "The rest goes to traditional", x -> x.rothShare, (x, v) -> x.rothShare = Math.max(0, Math.min(100, v))),
            f("Employer match %", "Of what you put in", x -> x.matchPct, (x, v) -> x.matchPct = v), f("Match up to % of salary", null, x -> x.matchCap, (x, v) -> x.matchCap = v),
            f("Raises per year %", null, x -> x.raise, (x, v) -> x.raise = v),
            Ui.formRow("Include taxable investments", "Portfolio from Investments", inc, 24)};
        Ui.VBox sv = (Ui.VBox) group("Saving", saving);
        if (r.includeTaxable) sv.add(f("Added to taxable / month", null, x -> x.taxableMonthly, (x, v) -> x.taxableMonthly = v));
        form.add(sv);
        form.add(group("Retirement income",
            f("Income goal per year", "Today's dollars, before tax", x -> x.goal, (x, v) -> x.goal = v),
            f("Social Security / month", "Today's dollars. See ssa.gov for your estimate", x -> x.ssMonthly, (x, v) -> x.ssMonthly = v),
            f("Social Security starts at", null, x -> x.ssAge, (x, v) -> x.ssAge = v),
            f("Withdrawal rate %", "4% is a common rule of thumb", x -> x.wr, (x, v) -> x.wr = v),
            f("Tax on traditional withdrawals %", null, x -> x.taxRate, (x, v) -> x.taxRate = Math.max(0, Math.min(90, v)))));
        form.add(group("Assumptions",
            f("Return before retiring %", "Per year, before inflation", x -> x.retPre, (x, v) -> x.retPre = v),
            f("Return in retirement %", null, x -> x.retPost, (x, v) -> x.retPost = v),
            f("Inflation %", null, x -> x.inflation, (x, v) -> x.inflation = v)));
        root.add(Ui.rail(Ui.top(cc), form, 360));
        root.add(Ui.hint("A planning estimate, not financial advice. Returns are steady averages; real markets swing, so test a lower return too. Withdrawals come from taxable first, then traditional (grossed up for tax), then Roth. "
            + RetirementCalc.LIMIT_YEAR + " 401(k) limits: " + Fmt.money(RetirementCalc.K401) + ", plus " + Fmt.money(RetirementCalc.CATCH_50) + " catch-up from age 50 or " + Fmt.money(RetirementCalc.CATCH_60_63) + " at ages 60–63."));
        refresh();
        return root;
    }

    @Override public void refresh() {
        Theme t = Theme.T; RetirementCalc rt = RetirementCalc.of(s());
        kpis.set(Ui.kpis(
            new Ui.Kpi("Saved at " + rt.retireAge, Fmt.compact(rt.atRetirement.total()), Fmt.compact(rt.atRetirement.nominal()) + " in future dollars"),
            new Ui.Kpi("Income in retirement", Fmt.money(rt.incomeWithSS) + "/yr", Fmt.money(rt.portIncome) + " savings + " + Fmt.money(rt.ssAnnual) + " Social Security"),
            new Ui.Kpi("Of your goal", Fmt.pctu(rt.onTrack), rt.onTrack >= 1 ? t.buy : rt.onTrack < .8 ? t.sell : t.warn, "Goal " + Fmt.money(s().retire.goal) + "/yr"),
            new Ui.Kpi("Money lasts", rt.runsOutAt != null ? "To age " + rt.runsOutAt : "Past age " + rt.planTo, rt.runsOutAt != null ? t.sell : t.buy, rt.runsOutAt != null ? "Short of your plan-to age" : "Spending your full goal each year")));
        int n = rt.points.size(); String[] labels = new String[n];
        double[] tr = new double[n], ro = new double[n], tx = new double[n];
        for (int i = 0; i < n; i++) { var p = rt.points.get(i); labels[i] = String.valueOf(p.age()); tr[i] = p.trad(); ro[i] = p.roth(); tx[i] = p.tax(); }
        chart.set(new Charts.Line(labels, Fmt::compact, 250).xTicks(8).mark(rt.yearsToRetire).stacked(new double[][]{tr, ro, tx}, new Color[]{t.series[0], t.series[2], t.series[4]}));
        RetirementCalc.FirstYear f = rt.first;
        if (f == null) { contrib.set(Ui.hint("You've reached your retirement age. The chart shows the drawdown.")); return; }
        Ui.VBox v = new Ui.VBox(10);
        Ui.VBox l = new Ui.VBox(0);
        l.add(Ui.listRow(Ui.dot(t.series[0]), "Traditional 401(k)", Ui.muted("Pre-tax: lowers taxes now"), Ui.mono(Fmt.money(f.trad()) + "/yr", 13.5f, t.ink), false));
        l.add(Ui.listRow(Ui.dot(t.series[2]), "Roth 401(k)", Ui.muted("After-tax: tax-free later"), Ui.mono(Fmt.money(f.roth()) + "/yr", 13.5f, t.ink), true));
        l.add(Ui.listRow(Ui.dot(Theme.alpha(t.series[0], .55)), "Employer match", Ui.muted("Goes to traditional"), Ui.mono(Fmt.money(f.match()) + "/yr", 13.5f, t.ink), true));
        if (f.taxable() > 0) l.add(Ui.listRow(Ui.dot(t.series[4]), "Taxable investing", null, Ui.mono(Fmt.money(f.taxable()) + "/yr", 13.5f, t.ink), true));
        v.add(l);
        Ui.HBox lim = new Ui.HBox(8);
        lim.grow(Ui.hint(RetirementCalc.LIMIT_YEAR + " limit used at age " + rt.age)); lim.add(Ui.mono(Fmt.money(f.contrib()) + " of " + Fmt.money(f.limit()), 12.5f, t.muted));
        v.add(lim); v.add(Ui.meter(f.contrib() / f.limit(), rt.capped, null));
        contrib.set(v);
    }
}

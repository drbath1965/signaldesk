package com.signaldesk.ui.views;

import com.signaldesk.calc.*;
import com.signaldesk.model.Group;
import com.signaldesk.ui.*;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

public final class OverviewView extends View {
    public OverviewView(MainFrame app) { super(app); }

    @Override public JComponent build() {
        Theme t = Theme.T; var s = s(); String cur = Dates.cur();
        BudgetCalc bt = BudgetCalc.of(s, cur); NetWorth nw = NetWorth.of(s); DebtCalc dp = DebtCalc.of(s);
        RetirementCalc rt = RetirementCalc.of(s); Portfolio pf = Portfolio.of(s);
        Ui.VBox root = new Ui.VBox(20);
        root.add(Kit.head(Dates.weekday(Dates.today()), "Overview", null, null));

        // Net worth
        Ui.Card nwc = Kit.capsCard("Net worth", Ui.link("Details", () -> app.navigate("networth")));
        Ui.HBox big = new Ui.HBox(14);
        big.add(Ui.big(Fmt.money(nw.net())));
        Double prev = s.nwHist.get(Dates.add(cur, -1));
        if (prev != null) big.add(Ui.mono(Kit.signedMoney(nw.net() - prev) + " vs last month", 13.5f, Kit.signColor(nw.net() - prev)));
        nwc.add(big);
        List<String> months = new ArrayList<>(s.nwHist.keySet());
        if (months.size() > 13) months = months.subList(months.size() - 13, months.size());
        if (months.size() > 1) {
            double[] v = months.stream().mapToDouble(m -> s.nwHist.get(m)).toArray();
            nwc.add(new Charts.Line(Kit.monthTicks(months), Fmt::compact, 150).add(new Charts.Series(v, t.accent, 2f, false, true, true)).xTicks(4));
        } else nwc.add(Ui.hint("Your net worth is recorded each month you use the studio, building a history here."));
        nwc.add(Ui.text("Assets " + Fmt.compact(nw.assets) + "     Debts " + Fmt.compact(nw.liabilities), Theme.mono(12.5f), t.muted));

        // Cash flow
        Ui.Card cf = Kit.capsCard(Dates.label(cur) + " cash flow", Ui.link("Budget", () -> app.navigate("budget")));
        Ui.HBox b2 = new Ui.HBox(12); b2.add(Ui.big(Fmt.money(bt.left()))); b2.add(Ui.muted("left of " + Fmt.money(bt.planned) + " planned")); cf.add(b2);
        double pct = bt.planned > 0 ? bt.spent / bt.planned : 0, dayFrac = Dates.todayDay() / (double) Dates.dim(cur);
        cf.add(Ui.meter(pct, bt.spent > bt.planned, dayFrac));
        Ui.HBox pace = new Ui.HBox(10);
        pace.grow(Ui.text(Fmt.money(bt.spent) + " spent · day " + Dates.todayDay() + " of " + Dates.dim(cur), Theme.body(12.5f), t.muted));
        pace.add(pct > dayFrac + .1 ? Ui.pill("Spending ahead of pace", Ui.Tone.WARN) : Ui.pill("On pace", Ui.Tone.POS));
        cf.add(pace);
        Ui.VBox groups = new Ui.VBox(0); int gi = 0;
        for (Group g : Group.values()) {
            BudgetCalc.GroupTotal x = bt.groups.get(g);
            groups.add(Ui.listRow(Ui.dot(t.series[g.ordinal() == 2 ? 2 : g.ordinal()]), g.label, Ui.muted(Fmt.money(x.spent) + " of " + Fmt.money(x.planned)),
                Ui.mono(x.planned > 0 ? Fmt.pctu(x.spent / x.planned) : "—", 13f, t.ink), gi++ > 0));
        }
        cf.add(groups);
        root.add(Ui.cols(18, nwc, cf));

        // KPIs
        double cash = nw.type("cash"), needs = bt.groups.get(Group.NEEDS).planned;
        String debtFree = dp.inPlan.isEmpty() || dp.total <= 0 ? "No debts" : dp.plan.finite() ? Dates.afterMonths(dp.plan.months()) : "Not on track";
        root.add(Ui.kpis(
            new Ui.Kpi("Debt-free", debtFree, null, dp.total > 0 ? Fmt.money(dp.total) + " in the payoff plan" : "Add debts to plan a payoff", () -> app.navigate("debt")),
            new Ui.Kpi("Retirement goal", Fmt.pctu(rt.onTrack), rt.onTrack >= 1 ? t.buy : rt.onTrack < .8 ? t.sell : null, Fmt.money(rt.incomeWithSS) + "/yr of " + Fmt.money(s.retire.goal) + " at " + rt.retireAge, () -> app.navigate("retirement")),
            new Ui.Kpi("Investments", Fmt.compact(pf.value), null, Fmt.pct(pf.gainPct()) + " total gain", () -> app.navigate("investments")),
            new Ui.Kpi("Emergency fund", needs > 0 ? Fmt.num(cash / needs, 1) + " mo" : "—", null, Fmt.money(cash) + " in cash", () -> app.navigate("networth"))));

        // Attention + bills
        List<Attention.Item> att = Attention.of(s);
        Ui.Card ac = Kit.card("Needs attention", Ui.muted(att.isEmpty() ? "" : att.size() + " item" + (att.size() > 1 ? "s" : "")));
        if (att.isEmpty()) ac.add(Kit.empty("Nothing needs attention right now."));
        Ui.VBox al = new Ui.VBox(0); int k = 0;
        for (Attention.Item a : att.subList(0, Math.min(7, att.size()))) {
            JTextArea sub = Ui.hint(a.detail()); sub.putClientProperty("maxw", 420);
            al.add(Ui.listRow(Ui.dot(a.level() == Attention.Level.CRITICAL ? t.sell : t.warn), a.title(), sub, Ui.small("Open", () -> app.navigate(a.section())), k++ > 0));
        }
        ac.add(al);
        List<BillCalc.Upcoming> up = BillCalc.upcoming(s, 14);
        Ui.Card bc = Kit.card("Bills in the next 14 days", Ui.link("All bills", () -> app.navigate("bills")));
        if (up.isEmpty()) bc.add(Kit.empty("No bills due in the next two weeks."));
        Ui.VBox bl = new Ui.VBox(0); k = 0; double byHand = 0, auto = 0;
        for (BillCalc.Upcoming u : up) {
            BillCalc.Status st = BillCalc.status(u.bill(), u.ym());
            bl.add(Ui.listRow(Ui.mono(Dates.dayShort(u.date().toString()), 12.5f, t.muted), u.bill().name, Ui.pill(st.label(), BillsView.tone(st.kind())), Ui.mono(Fmt.money(u.bill().amount), 13.5f, t.ink), k++ > 0));
            if (u.bill().autopay) auto += u.bill().amount; else byHand += u.bill().amount;
        }
        bc.add(bl);
        if (!up.isEmpty()) bc.add(Ui.hint(Fmt.money(byHand) + " to pay by hand · " + Fmt.money(auto) + " on autopay"));
        root.add(Ui.cols(18, Ui.top(ac), Ui.top(bc)));
        return root;
    }
}

package com.signaldesk.calc;

import com.signaldesk.model.*;
import java.util.ArrayList;
import java.util.List;

/** The "Needs attention" list on the Overview. */
public final class Attention {
    public enum Level { CRITICAL, WARNING }
    public record Item(Level level, String title, String detail, String section) {}

    public static List<Item> of(Studio s) {
        List<Item> out = new ArrayList<>();
        String cur = Dates.cur();
        BudgetCalc bt = BudgetCalc.of(s, cur); DebtCalc dp = DebtCalc.of(s); RetirementCalc rt = RetirementCalc.of(s);
        Portfolio pf = Portfolio.of(s); NetWorth nw = NetWorth.of(s);
        for (Bill b : s.bills) if (BillCalc.status(b, cur).kind() == BillCalc.Kind.OVERDUE)
            out.add(new Item(Level.CRITICAL, b.name + " is overdue", Fmt.money(b.amount) + " was due " + Dates.dayShort(Dates.iso(cur, BillCalc.dueDay(b, cur))) + ". Mark it paid on the Bills page once it's handled.", "bills"));
        for (Category c : s.cats) { double sp = bt.spentIn(c.id); if (c.planned > 0 && sp > c.planned * 1.0001)
            out.add(new Item(Level.CRITICAL, c.name + " is over budget", Fmt.money(sp) + " spent of " + Fmt.money(c.planned) + " planned this month.", "budget")); }
        if (bt.income > 0 && Math.abs(bt.unassigned()) >= 25)
            out.add(bt.unassigned() < 0
                ? new Item(Level.CRITICAL, "Budget plans " + Fmt.money(-bt.unassigned()) + " more than you earn", "Trim planned amounts until income minus planned is zero.", "budget")
                : new Item(Level.WARNING, Fmt.money(bt.unassigned()) + " of income has no job yet", "Give it a category, such as the emergency fund or extra debt payoff.", "budget"));
        double cash = nw.type("cash"), needs = bt.groups.get(Group.NEEDS).planned;
        if (needs > 0 && cash / needs < 3)
            out.add(new Item(Level.WARNING, "Emergency fund covers " + Fmt.num(cash / needs, 1) + " months", "Cash of " + Fmt.money(cash) + " against " + Fmt.money(needs) + " a month of needs. Three to six months is the usual target.", "networth"));
        for (Debt d : dp.underwater) out.add(new Item(Level.CRITICAL, d.name + "'s minimum doesn't cover interest", "The balance will grow. Raise the payment or add it to extra payoff.", "debt"));
        if (rt.onTrack > 0 && rt.onTrack < .8) out.add(new Item(Level.WARNING, "Retirement is " + Fmt.pctu(rt.onTrack) + " of goal", "Try a higher contribution or a later retirement age on the Retirement page.", "retirement"));
        if (rt.capped) out.add(new Item(Level.WARNING, "401(k) contribution exceeds the " + RetirementCalc.LIMIT_YEAR + " limit", "Your percentage works out above " + Fmt.money(RetirementCalc.k401Limit((int) Math.round(s.retire.age))) + ". The projection caps it at the limit.", "retirement"));
        if (pf.value > 0) for (String k : Holding.CLASSES) {
            double w = pf.weightPct(k), t = s.targets.getOrDefault(k, 0.0);
            if (Math.abs(w - t) >= 5) out.add(new Item(Level.WARNING, k + " is " + Math.round(w) + "% of investments, target " + Math.round(t) + "%", "About " + Fmt.money(Math.abs(w - t) / 100 * pf.value) + " to " + (w > t ? "trim" : "add") + " to rebalance.", "investments"));
        }
        return out;
    }
}

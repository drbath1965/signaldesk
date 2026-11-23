package com.signaldesk.calc;

import com.signaldesk.model.Debt;
import com.signaldesk.model.Studio;
import java.util.*;

/** Month-by-month payoff simulation with avalanche or snowball ordering and minimum roll-over. */
public final class DebtCalc {
    public record Payoff(String id, String name, double startBalance, double apr, Integer paidOffMonth) {}
    public record Sim(int months, double interest, List<Double> series, List<Payoff> order) {
        public boolean finite() { return months < Integer.MAX_VALUE; }
    }

    public final List<Debt> inPlan;
    public final String method, other;
    public final Sim plan, alt, minsOnly;
    public final List<Debt> underwater = new ArrayList<>();
    public final double total, minTotal, extra;

    private DebtCalc(Studio s) {
        inPlan = s.debts.stream().filter(d -> d.plan).toList();
        method = s.debtMethod; other = "avalanche".equals(method) ? "snowball" : "avalanche"; extra = s.debtExtra;
        plan = simulate(inPlan, extra, method, true);
        alt = simulate(inPlan, extra, other, true);
        minsOnly = simulate(inPlan, 0, method, false);
        for (Debt d : inPlan) if (d.balance > 0 && d.min <= d.balance * d.apr / 1200) underwater.add(d);
        total = inPlan.stream().mapToDouble(d -> d.balance).sum();
        minTotal = inPlan.stream().mapToDouble(d -> d.min).sum();
    }
    public static DebtCalc of(Studio s) { return new DebtCalc(s); }

    private static final class W { String id, name; double b, apr, min, start; Integer paid; }

    public static Sim simulate(List<Debt> debts, double extra, String method, boolean rollover) {
        List<W> ds = new ArrayList<>();
        for (Debt d : debts) if (d.balance > 0) { W w = new W(); w.id = d.id; w.name = d.name; w.b = d.balance; w.start = d.balance; w.apr = d.apr; w.min = d.min; ds.add(w); }
        List<W> order = new ArrayList<>(ds);
        if ("snowball".equals(method)) order.sort(Comparator.<W>comparingDouble(w -> w.b).thenComparing(w -> -w.apr));
        else order.sort(Comparator.<W>comparingDouble(w -> -w.apr).thenComparingDouble(w -> w.b));
        int m = 0; double interest = 0;
        List<Double> series = new ArrayList<>(); series.add(ds.stream().mapToDouble(w -> w.b).sum());
        while (ds.stream().anyMatch(w -> w.b > .005) && m < 600) {
            m++; double pool = extra;
            for (W w : ds) if (w.b > .005) { double i = w.b * w.apr / 1200; interest += i; w.b += i; }
            for (W w : ds) {
                if (w.b <= .005) { if (rollover) pool += w.min; continue; }
                double p = Math.min(w.min, w.b); w.b -= p; if (rollover) pool += w.min - p;
            }
            for (W w : order) { if (pool <= 0) break; if (w.b > .005) { double p = Math.min(pool, w.b); w.b -= p; pool -= p; } }
            for (W w : ds) if (w.b <= .005 && w.paid == null) { w.b = 0; w.paid = m; }
            series.add(ds.stream().mapToDouble(w -> w.b).sum());
        }
        boolean stuck = m >= 600;
        List<Payoff> po = new ArrayList<>();
        for (W w : order) po.add(new Payoff(w.id, w.name, w.start, w.apr, w.paid));
        po.sort(Comparator.comparing(p -> p.paidOffMonth() == null ? Integer.MAX_VALUE : p.paidOffMonth()));
        return new Sim(stuck ? Integer.MAX_VALUE : m, interest, series, po);
    }
}

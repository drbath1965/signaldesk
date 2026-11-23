package com.signaldesk.calc;

import com.signaldesk.model.*;
import java.util.*;

/** Monthly budget totals: income, planned, spending by category and by group. */
public final class BudgetCalc {
    public record Row(String id, String date, String desc, String cat, double amount, boolean fromBill) {}
    public static final class GroupTotal { public double planned, spent; }

    public final double income, planned, spent;
    public final Map<String, Double> byCat;
    public final EnumMap<Group, GroupTotal> groups = new EnumMap<>(Group.class);
    public final List<Row> rows;

    private BudgetCalc(double income, double planned, double spent, Map<String, Double> byCat, List<Row> rows) {
        this.income = income; this.planned = planned; this.spent = spent; this.byCat = byCat; this.rows = rows;
    }
    public double unassigned() { return income - planned; }
    public double left() { return planned - spent; }
    public double spentIn(String catId) { return byCat.getOrDefault(catId, 0.0); }

    public static BudgetCalc of(Studio s, String ym) {
        Map<String, Double> byCat = new HashMap<>();
        List<Row> rows = new ArrayList<>();
        for (Transaction t : s.tx) if (t.date != null && t.date.startsWith(ym)) {
            byCat.merge(t.cat, t.amount, Double::sum);
            rows.add(new Row(t.id, t.date, t.desc, t.cat, t.amount, false));
        }
        for (Bill b : s.bills) if (BillCalc.isPaid(b, ym)) {
            byCat.merge(b.cat, b.amount, Double::sum);
            rows.add(new Row("bill-" + b.id, Dates.iso(ym, BillCalc.dueDay(b, ym)), b.name, b.cat, b.amount, true));
        }
        rows.sort((a, b) -> b.date().compareTo(a.date()));
        double income = s.income.stream().mapToDouble(x -> x.amount).sum();
        double planned = s.cats.stream().mapToDouble(c -> c.planned).sum();
        double spent = byCat.values().stream().mapToDouble(Double::doubleValue).sum();
        BudgetCalc r = new BudgetCalc(income, planned, spent, byCat, rows);
        for (Group g : Group.values()) r.groups.put(g, new GroupTotal());
        for (Category c : s.cats) { GroupTotal gt = r.groups.get(Group.of(c.group)); gt.planned += c.planned; gt.spent += byCat.getOrDefault(c.id, 0.0); }
        return r;
    }
}

package com.signaldesk.calc;

import com.signaldesk.model.Bill;
import com.signaldesk.model.Studio;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Due dates and paid status for recurring bills. */
public final class BillCalc {
    private BillCalc() {}
    public enum Kind { NONE, PAID, AUTO, OVERDUE, DUE_SOON, UPCOMING }
    public record Status(Kind kind, String label) {}
    public record Upcoming(Bill bill, String ym, LocalDate date, boolean overdue) {}

    public static boolean dueIn(Bill b, String ym) {
        int m = Dates.month(ym), a = Math.max(1, Math.min(12, b.mon));
        return switch (b.freq) {
            case "quarterly" -> ((m - a) % 3 + 3) % 3 == 0;
            case "annual" -> m == a;
            default -> true;
        };
    }
    public static int dueDay(Bill b, String ym) { return Math.min(Math.max(1, b.day), Dates.dim(ym)); }
    public static double monthlyEquiv(Bill b) { return b.amount / ("quarterly".equals(b.freq) ? 3 : "annual".equals(b.freq) ? 12 : 1); }

    public static boolean isPaid(Bill b, String ym) {
        if (!dueIn(b, ym)) return false;
        if (Boolean.TRUE.equals(b.paid.get(ym))) return true;
        String cur = Dates.cur();
        if (b.autopay) {
            if (ym.compareTo(cur) < 0) return true;
            if (ym.equals(cur)) return dueDay(b, ym) <= Dates.todayDay();
        }
        return false;
    }

    public static Status status(Bill b, String ym) {
        String cur = Dates.cur(); int today = Dates.todayDay();
        if (!dueIn(b, ym)) return new Status(Kind.NONE, "Not due this month");
        int d = dueDay(b, ym);
        if (Boolean.TRUE.equals(b.paid.get(ym))) return new Status(Kind.PAID, "Paid");
        if (b.autopay) {
            if (ym.compareTo(cur) < 0 || (ym.equals(cur) && d <= today)) return new Status(Kind.PAID, "Autopaid");
            return new Status(Kind.AUTO, "Autopay " + Dates.dayShort(Dates.iso(ym, d)));
        }
        if (ym.compareTo(cur) < 0 || (ym.equals(cur) && d < today)) return new Status(Kind.OVERDUE, "Overdue");
        if (ym.equals(cur) && d - today <= 5) return new Status(Kind.DUE_SOON, d == today ? "Due today" : "Due in " + (d - today) + " day" + (d - today > 1 ? "s" : ""));
        return new Status(Kind.UPCOMING, "Due " + Dates.dayShort(Dates.iso(ym, d)));
    }

    public static List<Upcoming> upcoming(Studio s, int days) {
        List<Upcoming> out = new ArrayList<>();
        LocalDate today = Dates.today(), end = today.plusDays(days);
        for (int k = 0; k <= 1; k++) {
            String ym = Dates.add(Dates.cur(), k);
            for (Bill b : s.bills) {
                if (!dueIn(b, ym)) continue;
                LocalDate dt = Dates.parse(ym).atDay(dueDay(b, ym));
                if (dt.isBefore(today)) { if (k == 0 && status(b, ym).kind() == Kind.OVERDUE) out.add(new Upcoming(b, ym, dt, true)); continue; }
                if (!dt.isAfter(end) && !Boolean.TRUE.equals(b.paid.get(ym))) out.add(new Upcoming(b, ym, dt, false));
            }
        }
        out.sort(Comparator.comparing(Upcoming::date));
        return out;
    }
}

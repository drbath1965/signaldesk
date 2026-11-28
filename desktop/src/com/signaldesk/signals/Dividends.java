package com.signaldesk.signals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Dividends recovered from the price history. Yahoo's adjusted close folds each payout into every earlier price,
 * so on an ex-dividend day the ratio of adjusted to unadjusted close steps up. The size of the step gives the amount:
 * payout = previous close × (1 − previous ratio ÷ today's ratio). Mutual fund capital-gain distributions show up the same way.
 */
public final class Dividends {
    public record Payment(String date, double amount) {}
    private Dividends() {}

    /** Every payout in the saved history, oldest first. Empty without unadjusted prices or for assets that pay none. */
    public static List<Payment> payments(PriceSeries s) {
        List<Payment> out = new ArrayList<>();
        if (!s.hasOhlc()) return out;
        double[] raw = s.rawClose();
        for (int i = 1; i < s.size(); i++) {
            double f0 = s.factor(i - 1), f1 = s.factor(i);
            if (raw[i - 1] <= 0 || f1 <= 0) continue;
            double r = f0 / f1;
            // ignore rounding noise; real payouts are at least a few hundredths of a percent
            if (r < 0.9995 && r > 0.5) out.add(new Payment(s.dates().get(i), raw[i - 1] * (1 - r)));
        }
        return out;
    }

    /** Totals over the year ending on the last price, and the year before it. */
    public record Summary(double ttm, double prevTtm, int count, double yield, String lastDate) {
        public boolean pays() { return ttm > 0; }
        /** Change in the yearly total against the year before, or NaN when there's no earlier year to compare. */
        public double growth() { return prevTtm > 0 ? ttm / prevTtm - 1 : Double.NaN; }
    }
    public static Summary summary(PriceSeries s) {
        List<Payment> ps = payments(s);
        LocalDate end = LocalDate.parse(s.lastDate()), y1 = end.minusYears(1), y2 = end.minusYears(2);
        double a = 0, b = 0; int n = 0; String last = null;
        for (Payment p : ps) {
            LocalDate d = LocalDate.parse(p.date());
            if (d.isAfter(y1)) { a += p.amount(); n++; last = p.date(); }
            else if (d.isAfter(y2)) b += p.amount();
        }
        // a series that starts inside the earlier year can't give a fair comparison
        if (LocalDate.parse(s.firstDate()).isAfter(y2)) b = 0;
        double price = s.hasOhlc() ? s.rawClose()[s.size() - 1] : s.last();
        return new Summary(a, b, n, price > 0 ? a / price : 0, last);
    }
}

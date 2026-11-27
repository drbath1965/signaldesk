package com.signaldesk.signals;

import java.time.LocalDate;

/** Key statistics computed from a price history. Fractions throughout: 0.12 means 12%. NaN when history is too short. */
public final class Stats {
    public final double last, dayChange, high52, low52, rangePos52;
    public final double r1m, r3m, r6m, ytd, r1y, r3yAnn, r5yAnn;
    /** Annualized standard deviation of daily returns over the last year. */
    public final double vol1y;
    /** Worst fall from a peak over the last year, and over the whole history. */
    public final double dd1y, ddAll;
    /** Average shares traded per day over the last three months; NaN without volume. */
    public final double avgVol;
    /** Sensitivity to the S&P 500 over the last year (1.0 moves with it); NaN when the benchmark isn't available. */
    public final double beta;

    private Stats(PriceSeries s, PriceSeries bench, AssetType type) {
        double[] c = s.close(); int n = c.length; int L = n - 1;
        last = c[L]; dayChange = n > 1 ? c[L] / c[L - 1] - 1 : Double.NaN;
        LocalDate end = LocalDate.parse(s.lastDate());
        int y1 = indexOnOrAfter(s, end.minusYears(1));
        double hi = -Double.MAX_VALUE, lo = Double.MAX_VALUE;
        for (int i = y1; i <= L; i++) { hi = Math.max(hi, s.adjHigh(i)); lo = Math.min(lo, s.adjLow(i)); }
        high52 = hi; low52 = lo; rangePos52 = hi > lo ? (last - lo) / (hi - lo) : Double.NaN;
        r1m = back(s, end.minusMonths(1)); r3m = back(s, end.minusMonths(3)); r6m = back(s, end.minusMonths(6)); r1y = back(s, end.minusYears(1));
        ytd = back(s, LocalDate.of(end.getYear(), 1, 1).minusDays(1));
        r3yAnn = ann(back(s, end.minusYears(3)), 3); r5yAnn = ann(back(s, end.minusYears(5)), 5);
        // volatility from daily log returns over the last year
        double sum = 0, sq = 0; int k = 0;
        for (int i = Math.max(1, y1 + 1); i <= L; i++) { double r = Math.log(c[i] / c[i - 1]); sum += r; sq += r * r; k++; }
        vol1y = k > 20 ? Math.sqrt(Math.max(0, (sq - sum * sum / k) / (k - 1))) * Math.sqrt(type.periodsPerYear()) : Double.NaN;
        dd1y = drawdown(c, y1, L); ddAll = drawdown(c, 0, L);
        int m3 = indexOnOrAfter(s, end.minusMonths(3)); double vs = 0; int vk = 0;
        if (s.hasVolume()) for (int i = m3; i <= L; i++) { vs += s.vol(i); vk++; }
        avgVol = vk > 0 && vs > 0 ? vs / vk : Double.NaN;
        beta = bench == null ? Double.NaN : beta(s, bench, end.minusYears(1));
    }
    public static Stats of(PriceSeries s, PriceSeries benchmark, AssetType type) { return new Stats(s, benchmark, type); }

    /** Return since the last close on or before the given date, or NaN if history doesn't reach that far. */
    private static double back(PriceSeries s, LocalDate d) {
        if (LocalDate.parse(s.firstDate()).isAfter(d)) return Double.NaN;
        int i = indexOnOrBefore(s, d); return s.last() / s.close()[i] - 1;
    }
    private static double ann(double total, double years) { return Double.isNaN(total) ? Double.NaN : Math.pow(1 + total, 1 / years) - 1; }
    static int indexOnOrAfter(PriceSeries s, LocalDate d) {
        String k = d.toString(); int lo = 0, hi = s.size() - 1;
        while (lo < hi) { int m = (lo + hi) / 2; if (s.dates().get(m).compareTo(k) < 0) lo = m + 1; else hi = m; }
        return lo;
    }
    static int indexOnOrBefore(PriceSeries s, LocalDate d) {
        int i = indexOnOrAfter(s, d);
        if (i < s.size() && s.dates().get(i).equals(d.toString())) return i;
        return Math.max(0, i - 1);
    }
    private static double drawdown(double[] c, int a, int b) {
        double pk = -Double.MAX_VALUE, mx = 0;
        for (int i = a; i <= b; i++) { pk = Math.max(pk, c[i]); mx = Math.min(mx, c[i] / pk - 1); }
        return mx;
    }
    /** Covariance of daily returns with the benchmark divided by the benchmark's variance, on days both traded. */
    private static double beta(PriceSeries s, PriceSeries b, LocalDate from) {
        java.util.Map<String, Integer> bi = new java.util.HashMap<>();
        for (int i = 0; i < b.size(); i++) bi.put(b.dates().get(i), i);
        double sx = 0, sy = 0, sxy = 0, sxx = 0; int n = 0; Integer prevB = null; int prevS = -1;
        for (int i = indexOnOrAfter(s, from); i < s.size(); i++) {
            Integer j = bi.get(s.dates().get(i));
            if (j == null) continue;
            if (prevB != null) {
                double x = b.close()[j] / b.close()[prevB] - 1, y = s.close()[i] / s.close()[prevS] - 1;
                sx += x; sy += y; sxy += x * y; sxx += x * x; n++;
            }
            prevB = j; prevS = i;
        }
        if (n < 60) return Double.NaN;
        double cov = sxy / n - (sx / n) * (sy / n), var = sxx / n - (sx / n) * (sx / n);
        return var > 0 ? cov / var : Double.NaN;
    }
}

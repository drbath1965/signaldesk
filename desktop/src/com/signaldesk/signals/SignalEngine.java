package com.signaldesk.signals;

import com.signaldesk.calc.Dates;
import com.signaldesk.calc.Fmt;
import com.signaldesk.model.SignalParams;
import java.util.ArrayList;
import java.util.List;

/**
 * The Signal Desk algorithm: SMA trend crosses confirmed by MACD, RSI dip-buys in an uptrend,
 * and exits on a trailing stop, a trend break, or fading momentum. Orders fill at the next close.
 */
public final class SignalEngine {
    public record Signal(int i, boolean buy, String why) {}
    public record Trade(int entryI, double entryPx, String why, Integer exitI, double exitPx, String exitWhy, double ret, boolean open) {}
    public record Stats(double ret, double bh, double cagr, double bhCagr, double dd, double bhDd, int trades, Double win, Double avg, double exposure, double years) {}
    public enum Call { BUY, SELL, HOLD, WAIT }
    public record Verdict(Call call, String why) {}

    public final PriceSeries data; public final SignalParams p;
    public double[] fast, slow, rsi, macd, macdSig, eq, bh;
    public boolean[] held;
    public final List<Signal> signals = new ArrayList<>();
    public final List<Trade> trades = new ArrayList<>();
    public int start; public boolean inPos; public double peak; public Trade openEntry;
    public Stats stats; public String error;

    private SignalEngine(PriceSeries d, SignalParams p) { this.data = d; this.p = p; }

    public static SignalEngine run(PriceSeries d, SignalParams p) { SignalEngine e = new SignalEngine(d, p); e.compute(); return e; }

    public static double[] sma(double[] a, int n) {
        double[] o = nanArray(a.length); double s = 0;
        for (int i = 0; i < a.length; i++) { s += a[i]; if (i >= n) s -= a[i - n]; if (i >= n - 1) o[i] = s / n; }
        return o;
    }
    public static double[] ema(double[] a, int n) {
        double[] o = nanArray(a.length); int k = 0;
        while (k < a.length && Double.isNaN(a[k])) k++;
        if (k + n > a.length) return o;
        double s = 0; for (int i = k; i < k + n; i++) s += a[i];
        double e = s / n; o[k + n - 1] = e; double al = 2.0 / (n + 1);
        for (int i = k + n; i < a.length; i++) { e = a[i] * al + e * (1 - al); o[i] = e; }
        return o;
    }
    public static double[] rsi(double[] c, int n) {
        double[] o = nanArray(c.length); if (c.length <= n) return o;
        double g = 0, l = 0;
        for (int i = 1; i <= n; i++) { double d = c[i] - c[i - 1]; if (d > 0) g += d; else l -= d; }
        g /= n; l /= n; o[n] = l == 0 ? 100 : 100 - 100 / (1 + g / l);
        for (int i = n + 1; i < c.length; i++) {
            double d = c[i] - c[i - 1];
            g = (g * (n - 1) + Math.max(d, 0)) / n; l = (l * (n - 1) + Math.max(-d, 0)) / n;
            o[i] = l == 0 ? 100 : 100 - 100 / (1 + g / l);
        }
        return o;
    }
    private static double[] nanArray(int n) { double[] a = new double[n]; java.util.Arrays.fill(a, Double.NaN); return a; }

    private void compute() {
        double[] c = data.close(); int n = c.length;
        fast = sma(c, p.fast); slow = sma(c, p.slow); rsi = rsi(c, p.rsiN);
        double[] e12 = ema(c, 12), e26 = ema(c, 26); macd = nanArray(n);
        for (int i = 0; i < n; i++) if (!Double.isNaN(e12[i]) && !Double.isNaN(e26[i])) macd[i] = e12[i] - e26[i];
        macdSig = ema(macd, 9);
        int k = 0;
        while (k < n && (Double.isNaN(slow[k]) || Double.isNaN(rsi[k]) || Double.isNaN(macdSig[k]) || Double.isNaN(fast[k]))) k++;
        start = k + 1;
        if (start >= n - 5) { error = "Not enough history for a " + p.slow + "-day slow average. Load more data or shorten the slow SMA."; return; }
        double fee = p.fee / 1e4, cash = 1, sh = 0; Signal pending = null; Trade entry = null; int daysIn = 0;
        eq = nanArray(n); bh = nanArray(n); held = new boolean[n];
        for (int i = start; i < n; i++) {
            if (pending != null) {
                double px = c[i];
                if (pending.buy() && !inPos) { sh = cash * (1 - fee) / px; cash = 0; inPos = true; peak = px; entry = new Trade(i, px, pending.why(), null, 0, "", 0, true); }
                else if (!pending.buy() && inPos) {
                    cash = sh * px * (1 - fee); sh = 0; inPos = false;
                    trades.add(new Trade(entry.entryI(), entry.entryPx(), entry.why(), i, px, pending.why(), px / entry.entryPx() * (1 - fee) * (1 - fee) - 1, false));
                    entry = null;
                }
                pending = null;
            }
            if (inPos) { peak = Math.max(peak, c[i]); daysIn++; held[i] = true; }
            eq[i] = cash + sh * c[i]; bh[i] = c[i] / c[start];
            boolean crossUp = fast[i - 1] <= slow[i - 1] && fast[i] > slow[i], crossDn = fast[i - 1] >= slow[i - 1] && fast[i] < slow[i];
            boolean rsiUp = rsi[i - 1] < p.os && rsi[i] >= p.os, rsiFade = rsi[i - 1] > p.ob && rsi[i] <= p.ob, macdPos = macd[i] > macdSig[i];
            Signal s = null;
            if (!inPos) {
                if (crossUp && rsi[i] < p.ob && macdPos) s = new Signal(i, true, "Trend turned up: fast SMA crossed above slow, MACD confirms");
                else if (rsiUp && fast[i] > slow[i]) s = new Signal(i, true, "Dip in an uptrend: RSI recovered from oversold");
            } else {
                if (c[i] <= peak * (1 - p.stop / 100)) s = new Signal(i, false, "Trailing stop: down " + Fmt.num(p.stop, p.stop % 1 == 0 ? 0 : 1) + "% from the high since entry");
                else if (crossDn) s = new Signal(i, false, "Trend broke: fast SMA crossed below slow");
                else if (rsiFade) s = new Signal(i, false, "Momentum faded: RSI fell back from overbought");
            }
            if (s != null) { signals.add(s); pending = s; }
        }
        if (inPos) { openEntry = entry; trades.add(new Trade(entry.entryI(), entry.entryPx(), entry.why(), null, c[n - 1], "Still open", c[n - 1] / entry.entryPx() * (1 - fee) - 1, true)); }
        // Calendar time, not a day count: crypto trades every day, stocks about 252 days a year.
        double years = Math.max(1 / 365.25, java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(data.dates().get(start)), java.time.LocalDate.parse(data.dates().get(n - 1))) / 365.25);
        List<Trade> closed = trades.stream().filter(t -> !t.open()).toList();
        stats = new Stats(eq[n - 1] - 1, bh[n - 1] - 1, Math.pow(eq[n - 1], 1 / years) - 1, Math.pow(bh[n - 1], 1 / years) - 1,
            drawdown(eq), drawdown(bh), closed.size(),
            closed.isEmpty() ? null : closed.stream().filter(t -> t.ret() > 0).count() / (double) closed.size(),
            closed.isEmpty() ? null : closed.stream().mapToDouble(Trade::ret).average().orElse(0),
            daysIn / (double) (n - start), years);
    }
    private double drawdown(double[] a) {
        double pk = Double.NEGATIVE_INFINITY, mx = 0;
        for (int i = start; i < a.length; i++) { pk = Math.max(pk, a[i]); mx = Math.min(mx, a[i] / pk - 1); }
        return mx;
    }
    public Signal lastSignal() { return signals.isEmpty() ? null : signals.get(signals.size() - 1); }
    public Signal signalAt(int i) { for (Signal s : signals) if (s.i() == i) return s; return null; }

    public Verdict verdict() {
        double[] c = data.close(); int L = c.length - 1; Signal ls = lastSignal();
        if (ls != null && ls.i() == L) return new Verdict(ls.buy() ? Call.BUY : Call.SELL, ls.why() + ". The model would act at the next session's close.");
        if (inPos && openEntry != null) {
            double stop = peak * (1 - p.stop / 100);
            return new Verdict(Call.HOLD, "In a position since " + Dates.dayLong(data.dates().get(openEntry.entryI())) + " at " + Fmt.money2(openEntry.entryPx())
                + " (" + Fmt.pct(c[L] / openEntry.entryPx() - 1) + "). Trailing stop sits at " + Fmt.money2(stop) + ", " + Fmt.pct(stop / c[L] - 1) + " from here.");
        }
        String t = fast[L] > slow[L]
            ? "Uptrend in place but no fresh entry. Next buy trigger: RSI dipping under " + p.os + " and recovering (now " + Math.round(rsi[L]) + ")."
            : "Out of the market. Fast SMA is " + Fmt.pctu(Math.abs(fast[L] / slow[L] - 1), 1) + " below slow; a cross above it with MACD support would trigger a buy.";
        if (ls != null && !ls.buy()) t += " Last exit: " + Dates.dayLong(data.dates().get(ls.i())) + ".";
        return new Verdict(Call.WAIT, t);
    }

    /** The synthetic DEMO series used for practice, matching the web studio's sample. */
    public static PriceSeries sample() {
        long[] seed = {20261022};
        java.util.function.DoubleSupplier rnd = () -> {
            seed[0] = (seed[0] + 0x6D2B79F5L) & 0xffffffffL; int a = (int) seed[0];
            int t = imul(a ^ (a >>> 15), 1 | a);
            t = (t + imul(t ^ (t >>> 7), 61 | t)) ^ t;
            return ((t ^ (t >>> 14)) & 0xffffffffL) / 4294967296.0;
        };
        java.util.function.DoubleSupplier norm = () -> {
            double u = 0, v = 0; while (u == 0) u = rnd.getAsDouble(); while (v == 0) v = rnd.getAsDouble();
            return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
        };
        double[][] regimes = {{150, .0012, .014}, {120, -.0002, .019}, {110, -.0019, .022}, {170, .0017, .015}, {110, .0002, .018}, {90, .0011, .016}};
        int total = 0; for (double[] r : regimes) total += (int) r[0];
        List<String> dates = new ArrayList<>(); java.time.LocalDate d = Dates.today().plusDays(1);
        while (dates.size() < total) { d = d.minusDays(1); if (d.getDayOfWeek().getValue() <= 5) dates.add(0, d.toString()); }
        double[] close = new double[total]; double px = 100; int k = 0;
        for (double[] r : regimes) for (int j = 0; j < (int) r[0]; j++) { px *= Math.exp(r[1] - r[2] * r[2] / 2 + r[2] * norm.getAsDouble()); close[k++] = Math.round(px * 100) / 100.0; }
        // Made-up intraday ranges and volume so the practice sample can show candles; closes match the web studio's sample.
        java.util.Random R = new java.util.Random(7);
        double[] o = new double[total], h = new double[total], l = new double[total], v = new double[total];
        for (int i = 0; i < total; i++) {
            double prev = i == 0 ? close[0] : close[i - 1];
            o[i] = Math.round(prev * (1 + R.nextGaussian() * .004) * 100) / 100.0;
            h[i] = Math.round(Math.max(o[i], close[i]) * (1 + Math.abs(R.nextGaussian()) * .006) * 100) / 100.0;
            l[i] = Math.round(Math.min(o[i], close[i]) * (1 - Math.abs(R.nextGaussian()) * .006) * 100) / 100.0;
            v[i] = Math.round(2_000_000 * (1 + Math.abs(R.nextGaussian()) + Math.abs(close[i] / prev - 1) * 40));
        }
        return new PriceSeries("DEMO", dates, close, o, h, l, close.clone(), v);
    }
    private static int imul(int a, int b) { return a * b; }
}

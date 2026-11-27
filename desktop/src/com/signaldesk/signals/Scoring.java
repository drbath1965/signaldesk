package com.signaldesk.signals;

import com.signaldesk.model.SignalParams;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A transparent 0–100 screening score built only from price history, so it works the same for stocks,
 * ETFs, mutual funds and crypto. Four parts, each 0–100:
 *   Trend 30%    price against its 50- and 200-day averages, and whether the 200-day is rising
 *   Momentum 30% returns over 3 and 12 months
 *   Risk 25%     volatility and the worst drop over the last year (calmer scores higher)
 *   Timing 15%   the buy/sell model's current call
 * It ranks what the price record shows. It knows nothing about earnings, valuation or news.
 */
public final class Scoring {
    public enum Rating {
        STRONG("Strong", 70), POSITIVE("Positive", 55), NEUTRAL("Neutral", 40), WEAK("Weak", 25), AVOID("Weakest", 0);
        public final String label; public final int min;
        Rating(String l, int m) { label = l; min = m; }
        public static Rating of(double score) { for (Rating r : values()) if (score >= r.min) return r; return AVOID; }
    }
    public record Part(String name, double score, double weight, String why) {}
    public record Reason(boolean good, String text) {}
    public record Result(double score, Rating rating, List<Part> parts, List<Reason> reasons, SignalEngine.Call call) {}

    public static Result score(PriceSeries s, Stats st, SignalParams p) {
        double[] c = s.close(); int L = c.length - 1;
        double[] s50 = Indicators.sma(c, 50), s200 = Indicators.sma(c, 200);
        List<Reason> rs = new ArrayList<>();

        // Trend
        double trend = 50;
        boolean has200 = !Double.isNaN(s200[L]);
        if (has200) {
            trend = 0;
            boolean above200 = c[L] > s200[L], above50 = c[L] > s50[L], golden = s50[L] > s200[L];
            double slope = L >= 20 && !Double.isNaN(s200[L - 20]) ? s200[L] / s200[L - 20] - 1 : 0;
            trend += above200 ? 35 : 0; trend += above50 ? 20 : 0; trend += golden ? 25 : 0; trend += slope > 0 ? 20 : 0;
            if (above200 && golden) rs.add(new Reason(true, "Long-term uptrend: above its 200-day average, and the 50-day is above the 200-day."));
            else if (!above200 && !golden) rs.add(new Reason(false, "Long-term downtrend: below its 200-day average, and the 50-day is below the 200-day."));
            else if (above200) rs.add(new Reason(true, "Price is above its 200-day average."));
            else rs.add(new Reason(false, "Price is below its 200-day average."));
        } else rs.add(new Reason(false, "Less than 200 days of history, so the long-term trend can't be judged yet."));

        // Momentum
        double m12 = st.r1y, m3 = st.r3m;
        double mom = 0.6 * lin(Double.isNaN(m12) ? 0 : m12, -0.30, 0.40) + 0.4 * lin(Double.isNaN(m3) ? 0 : m3, -0.15, 0.15);
        if (!Double.isNaN(m12)) {
            if (m12 >= 0.15) rs.add(new Reason(true, "Up " + pct(m12) + " over the past year."));
            else if (m12 <= -0.10) rs.add(new Reason(false, "Down " + pct(-m12) + " over the past year."));
        }
        if (!Double.isNaN(m3) && Math.abs(m3) >= 0.08) rs.add(new Reason(m3 > 0, (m3 > 0 ? "Up " : "Down ") + pct(Math.abs(m3)) + " in the last 3 months."));

        // Risk (lower volatility and shallower drops score higher)
        double vol = Double.isNaN(st.vol1y) ? 0.3 : st.vol1y, dd = Double.isNaN(st.dd1y) ? -0.2 : st.dd1y;
        double risk = 0.6 * lin(-vol, -0.80, -0.12) + 0.4 * lin(dd, -0.60, -0.08);
        if (vol >= 0.45) rs.add(new Reason(false, "Very volatile: typical yearly swings of about " + pct(vol) + "."));
        else if (vol <= 0.18) rs.add(new Reason(true, "Steady: yearly volatility of about " + pct(vol) + "."));
        if (dd <= -0.30) rs.add(new Reason(false, "Fell as much as " + pct(-dd) + " from a high in the past year."));

        // Timing from the buy/sell model, run on recent years
        SignalEngine e = SignalEngine.run(s.tail(5 * 252 + p.slow + 40), p);
        SignalEngine.Call call = e.error == null ? e.verdict().call() : SignalEngine.Call.WAIT;
        double rsiNow = e.error == null ? e.rsi[e.rsi.length - 1] : 50;
        double timing = switch (call) { case BUY -> 100; case HOLD -> 75; case WAIT -> (has200 && c[L] > s200[L]) ? 45 : 30; case SELL -> 5; };
        if (rsiNow >= p.ob) { timing = Math.max(0, timing - 25); rs.add(new Reason(false, "Overbought right now (RSI " + Math.round(rsiNow) + "), so it may be due for a pause.")); }
        if (call == SignalEngine.Call.BUY) rs.add(new Reason(true, "The buy/sell model just signaled a buy."));
        if (call == SignalEngine.Call.SELL) rs.add(new Reason(false, "The buy/sell model just signaled a sell."));

        List<Part> parts = List.of(
            new Part("Trend", trend, .30, "Price against its 50- and 200-day averages"),
            new Part("Momentum", mom, .30, "Returns over 3 and 12 months"),
            new Part("Risk", risk, .25, "Volatility and worst drop in the past year; calmer is higher"),
            new Part("Timing", timing, .15, "The buy/sell model's current call"));
        double total = 0; for (Part x : parts) total += x.score() * x.weight();
        rs.sort((a, b) -> Boolean.compare(b.good(), a.good()));
        return new Result(total, Rating.of(total), parts, rs, call);
    }
    private static double lin(double x, double lo, double hi) { return Math.max(0, Math.min(100, (x - lo) / (hi - lo) * 100)); }
    private static String pct(double f) { return String.format(Locale.US, "%.0f%%", f * 100); }
}

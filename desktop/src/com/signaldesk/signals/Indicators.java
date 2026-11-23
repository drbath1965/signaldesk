package com.signaldesk.signals;

/** Technical indicators over a daily close series. NaN marks days without enough history. */
public final class Indicators {
    private Indicators() {}
    public static double[] sma(double[] a, int n) { return SignalEngine.sma(a, n); }
    public static double[] ema(double[] a, int n) { return SignalEngine.ema(a, n); }
    public static double[] rsi(double[] a, int n) { return SignalEngine.rsi(a, n); }

    /** Bollinger Bands: n-day average with bands k standard deviations above and below. Returns {mid, upper, lower}. */
    public static double[][] bollinger(double[] a, int n, double k) {
        double[] mid = sma(a, n), up = nan(a.length), lo = nan(a.length);
        for (int i = n - 1; i < a.length; i++) {
            double m = mid[i], s = 0;
            for (int j = i - n + 1; j <= i; j++) s += (a[j] - m) * (a[j] - m);
            double sd = Math.sqrt(s / n);
            up[i] = m + k * sd; lo[i] = m - k * sd;
        }
        return new double[][]{mid, up, lo};
    }
    /** MACD 12/26/9. Returns {macd, signal, histogram}. */
    public static double[][] macd(double[] a) {
        double[] e12 = ema(a, 12), e26 = ema(a, 26), m = nan(a.length);
        for (int i = 0; i < a.length; i++) if (!Double.isNaN(e12[i]) && !Double.isNaN(e26[i])) m[i] = e12[i] - e26[i];
        double[] sig = ema(m, 9), h = nan(a.length);
        for (int i = 0; i < a.length; i++) if (!Double.isNaN(m[i]) && !Double.isNaN(sig[i])) h[i] = m[i] - sig[i];
        return new double[][]{m, sig, h};
    }
    static double[] nan(int n) { double[] x = new double[n]; java.util.Arrays.fill(x, Double.NaN); return x; }
}

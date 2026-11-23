package com.signaldesk.calc;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Money and percent formatting, matching the web studio. */
public final class Fmt {
    private Fmt() {}
    private static final DecimalFormatSymbols US = DecimalFormatSymbols.getInstance(Locale.US);
    private static final DecimalFormat D0 = new DecimalFormat("#,##0", US), D2 = new DecimalFormat("#,##0.00", US);
    private static String neg(double v) { return v < -0.0049 ? "−" : ""; }

    public static synchronized String money(double v) { return neg(Math.round(v)) + "$" + D0.format(Math.abs(Math.round(v))); }
    public static synchronized String money2(double v) { return neg(v) + "$" + D2.format(Math.abs(v)); }
    public static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1e6) return neg(v) + "$" + String.format(Locale.US, a >= 1e7 ? "%.1fM" : "%.2fM", a / 1e6);
        if (a >= 1e4) return neg(v) + "$" + Math.round(a / 1e3) + "k";
        return money(v);
    }
    /** Signed percent from a fraction: 0.054 → "+5.4%". */
    public static String pct(double f, int d) {
        if (!Double.isFinite(f)) return "—";
        String s = String.format(Locale.US, "%." + d + "f%%", Math.abs(f * 100));
        return (f > 0 ? "+" : f < 0 ? "−" : "") + s;
    }
    public static String pct(double f) { return pct(f, 1); }
    /** Unsigned percent from a fraction: 0.54 → "54%". */
    public static String pctu(double f, int d) { return Double.isFinite(f) ? String.format(Locale.US, "%." + d + "f%%", f * 100) : "—"; }
    public static String pctu(double f) { return pctu(f, 0); }
    public static String num(double v, int d) { return String.format(Locale.US, "%." + d + "f", v); }
}

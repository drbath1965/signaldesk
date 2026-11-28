package com.signaldesk.ui.views;

import com.signaldesk.calc.Fmt;
import com.signaldesk.model.Screen;
import com.signaldesk.signals.AssetType;
import com.signaldesk.signals.Stats;
import com.signaldesk.store.PriceStore;
import com.signaldesk.store.Universe;
import com.signaldesk.ui.MainFrame;
import com.signaldesk.ui.Opt;
import com.signaldesk.ui.PriceService.Analysis;
import java.util.*;

/** Matching, sorting and describing custom screens and built-in lists. */
final class Screens {
    private Screens() {}

    static final Opt[] SORTS = {
        new Opt("score", "Score, highest first"), new Opt("yield", "Dividend yield, highest first"),
        new Opt("r1y", "1-year return, highest first"), new Opt("r3m", "3-month return, highest first"),
        new Opt("vol", "Volatility, calmest first"), new Opt("dd", "Smallest 1-year drop first")};

    static String listLabel(String key) {
        if (key.equals("watchlist")) return "My watchlist";
        Universe.List_ u = Universe.find(key); return u == null ? key : u.label();
    }

    /** Symbols a screen looks at: its lists, its extra tickers, without duplicates. */
    static List<String> symbols(MainFrame app, Screen s) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String k : s.lists) {
            if (k.equals("watchlist")) out.addAll(app.studio().watchlist);
            else { Universe.List_ u = Universe.find(k); if (u != null) for (String x : u.symbols()) out.add(PriceStore.clean(x)); }
        }
        for (String x : s.extra) { String c = PriceStore.clean(x); if (!c.isEmpty()) out.add(c); }
        return new ArrayList<>(out);
    }

    /** The rules a symbol fails, in plain words. Empty means it passes. */
    static List<String> fails(Analysis a, Screen s) {
        List<String> f = new ArrayList<>(); Stats st = a.stats();
        if (!s.types.isEmpty() && !s.types.contains(a.type().name())) f.add("is a " + a.type().label.toLowerCase());
        if (s.minScore != null && (a.score() == null || a.score().score() < s.minScore)) f.add("score below " + Math.round(s.minScore));
        if (s.minYield != null && !(st.divYield >= s.minYield)) f.add("yield below " + Fmt.pctu(s.minYield, 1));
        if (s.min1y != null && !(st.r1y >= s.min1y)) f.add("1-year return below " + Kit.signedPct(s.min1y, 0));
        if (s.min3m != null && !(st.r3m >= s.min3m)) f.add("3-month return below " + Kit.signedPct(s.min3m, 0));
        if (s.maxVol != null && !(st.vol1y <= s.maxVol)) f.add("volatility above " + Fmt.pctu(s.maxVol));
        if (s.maxDrop != null && !(st.dd1y >= -s.maxDrop)) f.add("fell more than " + Fmt.pctu(s.maxDrop) + " in the past year");
        if (s.above200 && !above200(a)) f.add("below its 200-day average");
        if (!s.calls.isEmpty() && (a.score() == null || !s.calls.contains(a.score().call().name()))) f.add("call isn't " + callsText(s.calls));
        return f;
    }
    static boolean above200(Analysis a) {
        double[] c = a.series().close(); int n = c.length; if (n < 200) return false;
        double sum = 0; for (int i = n - 200; i < n; i++) sum += c[i];
        return c[n - 1] > sum / 200;
    }
    private static String callsText(Set<String> calls) {
        List<String> l = new ArrayList<>(); for (String c : calls) l.add(c.charAt(0) + c.substring(1).toLowerCase());
        return String.join(" or ", l);
    }

    static Comparator<Analysis> order(String sort) {
        Comparator<Analysis> byScore = Comparator.comparingDouble(a -> a.score() == null ? -1 : -a.score().score());
        Comparator<Analysis> c = switch (sort) {
            case "yield" -> Comparator.comparingDouble(a -> -nz(a.stats().divYield));
            case "r1y" -> Comparator.comparingDouble(a -> -nz(a.stats().r1y));
            case "r3m" -> Comparator.comparingDouble(a -> -nz(a.stats().r3m));
            case "vol" -> Comparator.comparingDouble(a -> Double.isNaN(a.stats().vol1y) ? 9 : a.stats().vol1y);
            case "dd" -> Comparator.comparingDouble(a -> -(Double.isNaN(a.stats().dd1y) ? -9 : a.stats().dd1y));
            default -> byScore;
        };
        return c.thenComparing(byScore);
    }
    private static double nz(double v) { return Double.isNaN(v) ? -9 : v; }

    /** One-line summary of a screen's rules. */
    static String describe(Screen s) {
        List<String> r = new ArrayList<>();
        List<String> where = new ArrayList<>(); for (String k : s.lists) where.add(listLabel(k));
        if (!s.extra.isEmpty()) where.add(String.join(", ", s.extra));
        r.add("Looks in " + (where.isEmpty() ? "nothing yet" : String.join(" + ", where)));
        if (!s.types.isEmpty()) { List<String> t = new ArrayList<>(); for (String x : s.types) t.add(AssetType.valueOf(x).label); r.add(String.join(" or ", t) + " only"); }
        if (s.minScore != null) r.add("score ≥ " + Math.round(s.minScore));
        if (s.minYield != null) r.add("yield ≥ " + Fmt.pctu(s.minYield, 1));
        if (s.min1y != null) r.add("1 year ≥ " + Kit.signedPct(s.min1y, 0));
        if (s.min3m != null) r.add("3 months ≥ " + Kit.signedPct(s.min3m, 0));
        if (s.maxVol != null) r.add("volatility ≤ " + Fmt.pctu(s.maxVol));
        if (s.maxDrop != null) r.add("1-year drop no worse than " + Fmt.pctu(s.maxDrop));
        if (s.above200) r.add("above 200-day average");
        if (!s.calls.isEmpty()) r.add("call: " + callsText(s.calls));
        return String.join(" · ", r);
    }
}

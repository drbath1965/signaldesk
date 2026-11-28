package com.signaldesk.model;

import com.signaldesk.json.Json;
import java.util.*;

/**
 * A saved custom screen: which symbols to look at, the rules each must pass, and how to sort the ones that do.
 * Numbers are fractions (0.03 = 3%) except the score (0–100). A null limit means "any".
 */
public final class Screen {
    public String id = "sc-" + Ids.next(), name = "My screen";
    /** Built-in list keys to look in (stocks, etfs, funds, crypto, dividends, watchlist). */
    public final Set<String> lists = new LinkedHashSet<>();
    /** Extra tickers to include. */
    public final List<String> extra = new ArrayList<>();
    /** Asset types to keep, by AssetType name; empty keeps all. */
    public final Set<String> types = new LinkedHashSet<>();
    public Double minScore, minYield, min1y, min3m, maxVol, maxDrop;
    public boolean above200;
    /** Today's calls to keep (BUY, HOLD, WAIT, SELL); empty keeps all. */
    public final Set<String> calls = new LinkedHashSet<>();
    public String sort = "score";

    public static Screen from(Map<String, Object> m) {
        Screen s = new Screen();
        s.id = Json.str(m, "id", s.id); s.name = Json.str(m, "name", s.name);
        for (Object o : Json.arr(m, "lists")) s.lists.add(String.valueOf(o));
        for (Object o : Json.arr(m, "extra")) s.extra.add(String.valueOf(o));
        for (Object o : Json.arr(m, "types")) s.types.add(String.valueOf(o));
        for (Object o : Json.arr(m, "calls")) s.calls.add(String.valueOf(o));
        s.minScore = num(m, "minScore"); s.minYield = num(m, "minYield"); s.min1y = num(m, "min1y"); s.min3m = num(m, "min3m");
        s.maxVol = num(m, "maxVol"); s.maxDrop = num(m, "maxDrop");
        s.above200 = Json.bool(m, "above200", false); s.sort = Json.str(m, "sort", "score");
        return s;
    }
    private static Double num(Map<String, Object> m, String k) { return m.get(k) instanceof Number n ? n.doubleValue() : null; }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("name", name);
        m.put("lists", new ArrayList<Object>(lists)); m.put("extra", new ArrayList<Object>(extra));
        m.put("types", new ArrayList<Object>(types)); m.put("calls", new ArrayList<Object>(calls));
        if (minScore != null) m.put("minScore", minScore); if (minYield != null) m.put("minYield", minYield);
        if (min1y != null) m.put("min1y", min1y); if (min3m != null) m.put("min3m", min3m);
        if (maxVol != null) m.put("maxVol", maxVol); if (maxDrop != null) m.put("maxDrop", maxDrop);
        m.put("above200", above200); m.put("sort", sort);
        return m;
    }
    public Screen copy() { Screen c = from(toJson()); return c; }

    /** An empty screen that looks at the large US stocks list. */
    public static Screen blank() { Screen s = new Screen(); s.lists.add("stocks"); return s; }

    /** Ready-made starting points for a new screen. */
    public static List<Screen> templates() {
        List<Screen> l = new ArrayList<>();
        Screen a = new Screen(); a.name = "Dividend income"; a.lists.add("dividends"); a.lists.add("stocks");
        a.minYield = 0.025; a.maxVol = 0.30; a.maxDrop = 0.30; a.above200 = true; a.sort = "yield"; l.add(a);
        Screen b = new Screen(); b.name = "Strong uptrends"; b.lists.add("stocks"); b.lists.add("etfs");
        b.minScore = 70.0; b.min1y = 0.15; b.above200 = true; b.sort = "score"; l.add(b);
        Screen c = new Screen(); c.name = "Calm and steady funds"; c.lists.add("etfs"); c.lists.add("funds");
        c.types.add("ETF"); c.types.add("FUND"); c.maxVol = 0.18; c.maxDrop = 0.15; c.sort = "vol"; l.add(c);
        return l;
    }
}

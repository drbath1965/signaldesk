package com.signaldesk.model;

import com.signaldesk.json.Json;
import java.util.*;

/**
 * Everything Signal Desk keeps about you: your watchlist, practice portfolio, learning progress,
 * signal settings and the project backlog. Prices live separately in the prices folder.
 */
public final class Studio {
    public static final int VERSION = 2;

    public final List<String> watchlist = new ArrayList<>();
    public Practice practice = new Practice();
    /** Lesson id → best quiz score as a fraction (0–1). A lesson counts as complete at 2/3 or better. */
    public final Map<String, Double> lessons = new LinkedHashMap<>();
    public SignalParams sig = new SignalParams();
    public final List<BacklogItem> backlog = new ArrayList<>();
    /** Custom screens built on the Picks page. */
    public final List<Screen> screens = new ArrayList<>();
    public String theme = "light";
    /** True when loaded from a version 1 file that still held budget, bills and other removed sections. */
    public transient boolean fromV1;

    public boolean lessonDone(String id) { return lessons.getOrDefault(id, 0.0) >= 2.0 / 3 - 1e-9; }

    public static Studio from(Map<String, Object> root) {
        Map<String, Object> m = root.get("state") instanceof Map ? Json.obj(root, "state") : root;
        Studio s = new Studio();
        for (Object o : Json.arr(m, "watchlist")) { String x = String.valueOf(o).trim().toUpperCase(); if (!x.isEmpty() && !s.watchlist.contains(x)) s.watchlist.add(x); }
        s.practice = Practice.from(Json.obj(m, "practice"));
        for (Map.Entry<String, Object> e : Json.obj(m, "lessons").entrySet()) if (e.getValue() instanceof Number n) s.lessons.put(e.getKey(), n.doubleValue());
        s.sig = SignalParams.from(Json.obj(Json.obj(m, "sig"), "P"));
        for (Object o : Json.arr(m, "backlog")) s.backlog.add(BacklogItem.from(Json.obj(o)));
        for (Object o : Json.arr(m, "screens")) s.screens.add(Screen.from(Json.obj(o)));
        s.theme = "dark".equals(Json.str(Json.obj(m, "ui"), "theme", "light")) ? "dark" : "light";
        // Version 1 kept a household budget. Carry its investment symbols into the watchlist.
        if (m.containsKey("budget") || m.containsKey("bills")) {
            s.fromV1 = true;
            for (Object o : Json.arr(m, "holdings")) {
                Map<String, Object> h = Json.obj(o); String sym = Json.str(h, "sym", "").trim().toUpperCase();
                if (!sym.isEmpty() && !"Cash".equals(Json.str(h, "cls", "")) && !sym.equals("CASH") && !s.watchlist.contains(sym)) s.watchlist.add(sym);
            }
            if (s.watchlist.isEmpty()) s.watchlist.addAll(defaultWatchlist());
        }
        return s;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", VERSION);
        m.put("watchlist", new ArrayList<Object>(watchlist));
        m.put("practice", practice.toJson());
        m.put("lessons", new LinkedHashMap<>(lessons));
        Map<String, Object> sg = new LinkedHashMap<>(); sg.put("P", sig.toJson()); m.put("sig", sg);
        List<Object> b = new ArrayList<>(); for (BacklogItem x : backlog) b.add(x.toJson()); m.put("backlog", b);
        List<Object> sc = new ArrayList<>(); for (Screen x : screens) sc.add(x.toJson()); m.put("screens", sc);
        Map<String, Object> ui = new LinkedHashMap<>(); ui.put("theme", theme); m.put("ui", ui);
        return m;
    }

    /** A starter mix: two large US stocks, a broad ETF, an index mutual fund and two cryptocurrencies. */
    public static List<String> defaultWatchlist() { return List.of("AAPL", "MSFT", "VOO", "VFIAX", "BTC-USD", "ETH-USD"); }

    public static Studio fresh() {
        Studio s = new Studio();
        s.watchlist.addAll(defaultWatchlist());
        s.backlog.addAll(starterBacklog());
        return s;
    }

    /** The project backlog as of version 2.0. */
    public static List<BacklogItem> starterBacklog() {
        List<BacklogItem> l = new ArrayList<>();
        BacklogItem.Priority H = BacklogItem.Priority.HIGH, M = BacklogItem.Priority.MED, L = BacklogItem.Priority.LOW;
        String d = "2026-11-27";
        l.add(done("bl-desktop-jar", "Desktop app (.jar)", "Runnable Java desktop app with data saved on this computer.", H, "Studio", "2026-11-23"));
        l.add(done("bl-live-prices", "Automatic prices", "Picking a symbol downloads its prices from Yahoo Finance and keeps them current.", H, "Research", "2026-11-23"));
        l.add(done("bl-history-charts", "Full history, candlestick and OHLC charts, indicators", "Version 1.1.0.", H, "Research", "2026-11-23"));
        l.add(done("bl-investing-focus", "Refocus on investing and learning", "Version 2.0: Markets, Research, Picks, Practice portfolio and Learn replace the household budget sections.", H, "Studio", d));
        l.add(new BacklogItem("bl-confirm-yahoo", "Confirm live price downloads on my computer",
            "Downloads were tested against a stand-in server that mimics Yahoo Finance. Open the app, pick a symbol, and confirm prices arrive.", H, "Research", d));
        l.get(l.size() - 1).status = BacklogItem.Status.NEXT;
        l.add(new BacklogItem("bl-fundamentals", "Company fundamentals", "Add valuation and quality measures (P/E, earnings growth, margins, dividend yield) to research and scoring. Needs a data source that provides them.", M, "Research", d));
        l.add(new BacklogItem("bl-news", "Headlines for each symbol", "Show recent news on the research page.", M, "Research", d));
        l.add(new BacklogItem("bl-alerts", "Price and signal alerts", "Notify when a watchlist symbol crosses a price or its signal changes.", M, "Markets", d));
        l.add(new BacklogItem("bl-log-scale", "Log scale for long chart ranges", "On Max, decades of growth squash early prices flat.", L, "Research", d));
        l.add(new BacklogItem("bl-more-lessons", "More lessons", "Options strategies, taxes on investments, retirement accounts, reading a company's financial statements.", L, "Learn", d));
        l.add(new BacklogItem("bl-release", "GitHub release with the .jar, and a license", "Attach the jar to a GitHub Release and choose a license, or make the repository private.", L, "Studio", d));
        l.add(new BacklogItem("bl-web", "Bring the web version in line", "The web page is still the version 1 household-finance studio.", L, "Studio", d));
        return l;
    }
    private static BacklogItem done(String id, String t, String det, BacklogItem.Priority p, String area, String when) {
        BacklogItem b = new BacklogItem(id, t, det, p, area, when); b.status = BacklogItem.Status.DONE; b.doneAt = when; return b;
    }
}

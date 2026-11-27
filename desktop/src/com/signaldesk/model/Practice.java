package com.signaldesk.model;

import com.signaldesk.json.Json;
import java.util.*;

/** The practice portfolio: pretend cash, positions and every trade, so ideas can be tried without risk. */
public final class Practice {
    public static final double START_CASH = 100_000;

    public record Trade(String date, String symbol, boolean buy, double shares, double price) {
        public double amount() { return shares * price; }
        static Trade from(Map<String, Object> m) {
            return new Trade(Json.str(m, "date", ""), Json.str(m, "symbol", ""), "buy".equals(Json.str(m, "side", "buy")), Json.num(m, "shares", 0), Json.num(m, "price", 0));
        }
        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", date); m.put("symbol", symbol); m.put("side", buy ? "buy" : "sell"); m.put("shares", shares); m.put("price", price);
            return m;
        }
    }
    /** Shares held and their average cost. */
    public static final class Position { public double shares, cost; Position(double s, double c) { shares = s; cost = c; } public double avgCost() { return shares > 0 ? cost / shares : 0; } }

    public double startCash = START_CASH;
    public String started = "";
    public final List<Trade> trades = new ArrayList<>();

    /** Cash left after every trade. */
    public double cash() { double c = startCash; for (Trade t : trades) c += t.buy() ? -t.amount() : t.amount(); return c; }
    /** Positions rebuilt from the trade list; average cost resets when a position is fully sold. */
    public Map<String, Position> positions() {
        Map<String, Position> p = new TreeMap<>();
        for (Trade t : trades) {
            Position x = p.computeIfAbsent(t.symbol(), k -> new Position(0, 0));
            if (t.buy()) { x.shares += t.shares(); x.cost += t.amount(); }
            else { double avg = x.avgCost(); x.shares -= t.shares(); x.cost -= avg * t.shares(); if (x.shares < 1e-9) { x.shares = 0; x.cost = 0; } }
        }
        p.values().removeIf(x -> x.shares < 1e-9);
        return p;
    }
    /** Profit or loss already locked in by sales. */
    public double realized() {
        Map<String, Position> p = new HashMap<>(); double r = 0;
        for (Trade t : trades) {
            Position x = p.computeIfAbsent(t.symbol(), k -> new Position(0, 0));
            if (t.buy()) { x.shares += t.shares(); x.cost += t.amount(); }
            else { double avg = x.avgCost(); r += (t.price() - avg) * t.shares(); x.shares -= t.shares(); x.cost -= avg * t.shares(); if (x.shares < 1e-9) { x.shares = 0; x.cost = 0; } }
        }
        return r;
    }

    public static Practice from(Map<String, Object> m) {
        Practice p = new Practice();
        p.startCash = Json.num(m, "startCash", START_CASH); p.started = Json.str(m, "started", "");
        for (Object o : Json.arr(m, "trades")) p.trades.add(Trade.from(Json.obj(o)));
        return p;
    }
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("startCash", startCash); m.put("started", started);
        List<Object> t = new ArrayList<>(); for (Trade x : trades) t.add(x.toJson()); m.put("trades", t);
        return m;
    }
}

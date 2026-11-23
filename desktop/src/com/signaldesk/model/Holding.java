package com.signaldesk.model;

import com.signaldesk.json.Json;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Holding {
    public static final String[] CLASSES = {"US stocks", "Intl stocks", "Bonds", "Cash", "Real estate", "Other"};

    public String id = Ids.next();
    public String sym = "";
    public String cls = "US stocks";
    public double shares, cost, price;

    public Holding() {}
    public Holding(String sym, String cls, double shares, double cost, double price) { this.sym = sym; this.cls = cls; this.shares = shares; this.cost = cost; this.price = price; }

    public double value() { return shares * price; }
    public double costBasis() { return shares * cost; }

    public static Holding from(Map<String, Object> m) {
        Holding h = new Holding();
        h.id = Json.str(m, "id", Ids.next()); h.sym = Json.str(m, "sym", "").toUpperCase().trim();
        h.cls = Json.str(m, "cls", "US stocks"); h.shares = Json.num(m, "shares", 0);
        h.cost = Json.num(m, "cost", 0); h.price = Json.num(m, "price", 0);
        boolean ok = false; for (String c : CLASSES) if (c.equals(h.cls)) ok = true;
        if (!ok) h.cls = "Other";
        return h;
    }
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("sym", sym); m.put("cls", cls); m.put("shares", shares); m.put("cost", cost); m.put("price", price);
        return m;
    }
}

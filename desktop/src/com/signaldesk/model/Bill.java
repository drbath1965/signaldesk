package com.signaldesk.model;

import com.signaldesk.json.Json;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** A recurring bill. Freq is monthly, quarterly or annual; mon is the first month (1-12) it comes due. */
public final class Bill {
    public String id = Ids.next();
    public String name = "";
    public double amount;
    public int day = 1;
    public String freq = "monthly";
    public int mon = 1;
    public String cat = "";
    public boolean autopay;
    /** Months (yyyy-MM) marked paid by hand. */
    public final Map<String, Boolean> paid = new TreeMap<>();

    public Bill() {}
    public Bill(String name, double amount, int day, String freq, int mon, String cat, boolean autopay) {
        this.name = name; this.amount = amount; this.day = day; this.freq = freq; this.mon = mon; this.cat = cat; this.autopay = autopay;
    }

    public static Bill from(Map<String, Object> m) {
        Bill b = new Bill();
        b.id = Json.str(m, "id", Ids.next()); b.name = Json.str(m, "name", ""); b.amount = Json.num(m, "amount", 0);
        b.day = (int) Math.round(Json.num(m, "day", 1)); b.freq = Json.str(m, "freq", "monthly");
        b.mon = (int) Math.round(Json.num(m, "mon", 1)); b.cat = Json.str(m, "cat", ""); b.autopay = Json.bool(m, "autopay", false);
        if (!b.freq.equals("quarterly") && !b.freq.equals("annual")) b.freq = "monthly";
        if (b.mon < 1 || b.mon > 12) b.mon = 1;
        for (Map.Entry<String, Object> e : Json.obj(m, "paid").entrySet()) if (Boolean.TRUE.equals(e.getValue())) b.paid.put(e.getKey(), true);
        return b;
    }
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("name", name); m.put("amount", amount); m.put("day", day); m.put("freq", freq);
        m.put("mon", mon); m.put("cat", cat); m.put("autopay", autopay); m.put("paid", new LinkedHashMap<>(paid));
        return m;
    }
}

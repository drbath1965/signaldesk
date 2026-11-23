package com.signaldesk.model;

import com.signaldesk.json.Json;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Income {
    public String id = Ids.next();
    public String name = "";
    public double amount;

    public Income() {}
    public Income(String name, double amount) { this.name = name; this.amount = amount; }

    public static Income from(Map<String, Object> m) {
        Income x = new Income();
        x.id = Json.str(m, "id", Ids.next()); x.name = Json.str(m, "name", ""); x.amount = Json.num(m, "amount", 0);
        return x;
    }
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("name", name); m.put("amount", amount);
        return m;
    }
}

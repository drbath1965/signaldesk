package com.signaldesk.model;

import com.signaldesk.json.Json;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Debt {
    public String id = Ids.next();
    public String name = "";
    public double balance, apr, min;
    /** Whether this debt is part of the payoff plan. */
    public boolean plan = true;

    public Debt() {}
    public Debt(String name, double balance, double apr, double min, boolean plan) { this.name = name; this.balance = balance; this.apr = apr; this.min = min; this.plan = plan; }

    public static Debt from(Map<String, Object> m) {
        Debt d = new Debt();
        d.id = Json.str(m, "id", Ids.next()); d.name = Json.str(m, "name", "");
        d.balance = Json.num(m, "balance", 0); d.apr = Json.num(m, "apr", 0); d.min = Json.num(m, "min", 0);
        d.plan = Json.bool(m, "plan", true);
        return d;
    }
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("name", name); m.put("balance", balance); m.put("apr", apr); m.put("min", min); m.put("plan", plan);
        return m;
    }
}

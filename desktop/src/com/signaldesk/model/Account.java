package com.signaldesk.model;

import com.signaldesk.json.Json;
import java.util.LinkedHashMap;
import java.util.Map;

/** An account or asset. Type is one of {@link AccountType}'s keys. */
public final class Account {
    public String id = Ids.next();
    public String name = "";
    public String type = "cash";
    public double balance;

    public Account() {}
    public Account(String name, String type, double balance) { this.name = name; this.type = type; this.balance = balance; }

    public static Account from(Map<String, Object> m) {
        Account a = new Account();
        a.id = Json.str(m, "id", Ids.next()); a.name = Json.str(m, "name", "");
        a.type = Json.str(m, "type", "cash"); a.balance = Json.num(m, "balance", 0);
        if (AccountType.of(a.type) == null) a.type = "other";
        return a;
    }
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("name", name); m.put("type", type); m.put("balance", balance);
        return m;
    }
}

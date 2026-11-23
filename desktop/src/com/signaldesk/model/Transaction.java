package com.signaldesk.model;

import com.signaldesk.json.Json;
import java.util.LinkedHashMap;
import java.util.Map;

/** One expense. Date is ISO yyyy-MM-dd. */
public final class Transaction {
    public String id = Ids.next();
    public String date = "";
    public String desc = "";
    public String cat = "";
    public double amount;

    public Transaction() {}
    public Transaction(String date, String desc, String cat, double amount) { this.date = date; this.desc = desc; this.cat = cat; this.amount = amount; }

    public static Transaction from(Map<String, Object> m) {
        Transaction t = new Transaction();
        t.id = Json.str(m, "id", Ids.next()); t.date = Json.str(m, "date", ""); t.desc = Json.str(m, "desc", "");
        t.cat = Json.str(m, "cat", ""); t.amount = Json.num(m, "amount", 0);
        return t;
    }
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("date", date); m.put("desc", desc); m.put("cat", cat); m.put("amount", amount);
        return m;
    }
}

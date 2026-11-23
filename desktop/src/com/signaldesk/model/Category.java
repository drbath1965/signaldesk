package com.signaldesk.model;

import com.signaldesk.json.Json;
import java.util.LinkedHashMap;
import java.util.Map;

/** A budget category. Group is "needs", "wants" or "savings". */
public final class Category {
    public String id = Ids.next();
    public String name = "";
    public String group = "wants";
    public double planned;

    public Category() {}
    public Category(String name, String group, double planned) { this.name = name; this.group = group; this.planned = planned; }

    public static Category from(Map<String, Object> m) {
        Category c = new Category();
        c.id = Json.str(m, "id", Ids.next()); c.name = Json.str(m, "name", "");
        c.group = Json.str(m, "group", "wants"); c.planned = Json.num(m, "planned", 0);
        if (!Group.isValid(c.group)) c.group = "wants";
        return c;
    }
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("name", name); m.put("group", group); m.put("planned", planned);
        return m;
    }
    @Override public String toString() { return name.isBlank() ? "(unnamed)" : name; }
}

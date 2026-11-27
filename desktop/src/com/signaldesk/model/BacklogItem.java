package com.signaldesk.model;

import com.signaldesk.json.Json;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BacklogItem {
    public enum Status {
        BACKLOG("backlog", "Backlog", "Not yet scheduled"), NEXT("next", "Up next", "Chosen for the next round"),
        DOING("doing", "In progress", "Being built now"), DONE("done", "Done", "Shipped");
        public final String key, label, sub;
        Status(String key, String label, String sub) { this.key = key; this.label = label; this.sub = sub; }
        public static Status of(String k) { for (Status s : values()) if (s.key.equals(k)) return s; return BACKLOG; }
        @Override public String toString() { return label; }
    }
    public enum Priority {
        HIGH("high", "High"), MED("med", "Medium"), LOW("low", "Low");
        public final String key, label;
        Priority(String key, String label) { this.key = key; this.label = label; }
        public static Priority of(String k) { for (Priority p : values()) if (p.key.equals(k)) return p; return MED; }
        @Override public String toString() { return label; }
    }
    public static final String[] AREAS = {"Markets", "Research", "Picks", "Practice", "Learn", "Studio"};

    public String id = Ids.next();
    public String title = "", detail = "", area = "Studio", created = "", doneAt = "";
    public Status status = Status.BACKLOG;
    public Priority priority = Priority.MED;

    public BacklogItem() {}
    public BacklogItem(String id, String title, String detail, Priority p, String area, String created) {
        this.id = id; this.title = title; this.detail = detail; this.priority = p; this.area = area; this.created = created;
    }

    public static BacklogItem from(Map<String, Object> m) {
        BacklogItem b = new BacklogItem();
        b.id = Json.str(m, "id", Ids.next()); b.title = Json.str(m, "title", "Untitled"); b.detail = Json.str(m, "detail", "");
        b.status = Status.of(Json.str(m, "status", "backlog")); b.priority = Priority.of(Json.str(m, "priority", "med"));
        b.area = Json.str(m, "area", "Studio"); b.created = Json.str(m, "created", ""); b.doneAt = Json.str(m, "doneAt", "");
        return b;
    }
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("title", title); m.put("detail", detail); m.put("status", status.key); m.put("priority", priority.key);
        m.put("area", area); m.put("created", created); m.put("doneAt", doneAt);
        return m;
    }
}

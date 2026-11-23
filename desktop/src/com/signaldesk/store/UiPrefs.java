package com.signaldesk.store;

import com.signaldesk.json.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Display preferences (chart type, indicators, range) in SignalDesk/settings.json, kept apart from your financial data. */
public final class UiPrefs {
    private static Map<String, Object> m;
    private UiPrefs() {}
    private static Path file() { return AppPaths.root().resolve("settings.json"); }
    private static synchronized Map<String, Object> map() {
        if (m == null) { try { m = Json.obj(Json.parse(Files.readString(file(), StandardCharsets.UTF_8))); } catch (Exception e) { m = new LinkedHashMap<>(); } }
        return m;
    }
    public static synchronized String get(String k, String def) { return Json.str(map(), k, def); }
    public static synchronized int getInt(String k, int def) { return (int) Json.num(map(), k, def); }
    public static synchronized Set<String> getSet(String k, Set<String> def) {
        if (!map().containsKey(k)) return new LinkedHashSet<>(def);
        Set<String> out = new LinkedHashSet<>(); for (Object o : Json.arr(map(), k)) out.add(String.valueOf(o)); return out;
    }
    public static synchronized void put(String k, Object v) {
        map().put(k, v instanceof Set<?> s ? new ArrayList<>(s) : v);
        try { Files.createDirectories(file().getParent()); Files.writeString(file(), Json.write(map()), StandardCharsets.UTF_8); } catch (Exception ignored) {}
    }
}

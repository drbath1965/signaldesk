package com.signaldesk.store;

import com.signaldesk.json.Json;
import com.signaldesk.model.Studio;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Loads and saves studio.json. Writes go to a temp file first so a crash never leaves half a file. */
public final class StudioStore {
    private final Path file;
    public StudioStore(Path file) { this.file = file; }
    public Path file() { return file; }
    public boolean exists() { return Files.exists(file); }

    public Studio load() throws IOException {
        return Studio.from(Json.obj(Json.parse(Files.readString(file, StandardCharsets.UTF_8))));
    }

    public synchronized void save(Studio s) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, Json.write(s.toJson()), StandardCharsets.UTF_8);
        try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
    }

    /** Keeps one automatic copy per day in the backups folder, and the last 14 of them. */
    public void dailyBackup() {
        try {
            if (!exists()) return;
            Path dir = AppPaths.backupsDir(); Files.createDirectories(dir);
            Path today = dir.resolve("studio-" + LocalDate.now() + ".json");
            if (!Files.exists(today)) Files.copy(file, today);
            try (Stream<Path> st = Files.list(dir)) {
                List<Path> old = st.filter(p -> p.getFileName().toString().matches("studio-\\d{4}-\\d{2}-\\d{2}\\.json"))
                    .sorted(Comparator.reverseOrder()).skip(14).toList();
                for (Path p : old) Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {}
    }

    public static void exportTo(Studio s, Path out) throws IOException {
        java.util.Map<String, Object> wrap = new java.util.LinkedHashMap<>();
        wrap.put("app", "signal-desk-finance-studio"); wrap.put("exported", java.time.Instant.now().toString()); wrap.put("state", s.toJson());
        Files.writeString(out, Json.write(wrap), StandardCharsets.UTF_8);
    }
    /** Reads a backup from this app or from the web studio's "Export backup". */
    public static Studio importFrom(Path in) throws IOException {
        Object root = Json.parse(Files.readString(in, StandardCharsets.UTF_8));
        java.util.Map<String, Object> m = Json.obj(root);
        java.util.Map<String, Object> st = m.get("state") instanceof java.util.Map ? Json.obj(m, "state") : m;
        if (!st.containsKey("budget") && !st.containsKey("bills")) throw new IOException("That file is not a Signal Desk backup.");
        return Studio.from(m);
    }
}

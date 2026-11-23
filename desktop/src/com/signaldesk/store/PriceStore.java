package com.signaldesk.store;

import com.signaldesk.json.Json;
import com.signaldesk.signals.PriceCsv;
import com.signaldesk.signals.PriceSeries;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/** Saved daily prices, one CSV per symbol in the prices folder, plus names and download times in index.json. */
public final class PriceStore {
    /** Prices younger than this are reused; older ones are downloaded again. */
    public static final Duration FRESH = Duration.ofMinutes(15);

    private final Path dir;
    private final Map<String, PriceSeries> cache = new HashMap<>();
    private Map<String, Object> index;
    public PriceStore(Path dir) { this.dir = dir; }

    public static String clean(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replaceFirst("^\\$", "").replaceAll("[^A-Z0-9.\\-^=]", "");
    }
    private Path fileFor(String sym) { return dir.resolve(sym.replace('^', '_') + ".csv"); }
    private Path indexFile() { return dir.resolve("index.json"); }

    private Map<String, Object> index() {
        if (index == null) {
            try { index = Json.obj(Json.parse(Files.readString(indexFile(), StandardCharsets.UTF_8))); }
            catch (Exception e) { index = new LinkedHashMap<>(); }
        }
        return index;
    }

    public synchronized PriceSeries get(String sym) {
        sym = clean(sym); if (sym.isEmpty()) return null;
        if (cache.containsKey(sym)) return cache.get(sym);
        Path f = fileFor(sym);
        if (!Files.exists(f)) return null;
        try { PriceSeries s = PriceCsv.parse(Files.readString(f, StandardCharsets.UTF_8), sym); cache.put(sym, s); return s; }
        catch (Exception e) { return null; }
    }
    public synchronized String name(String sym) { return Json.str(Json.obj(index().get(clean(sym))), "name", ""); }
    /** When this symbol's prices were last downloaded, or null if never. */
    public synchronized Instant fetchedAt(String sym) {
        String t = Json.str(Json.obj(index().get(clean(sym))), "fetched", null);
        if (t != null) try { return Instant.parse(t); } catch (Exception ignored) {}
        try { return Files.exists(fileFor(clean(sym))) ? Files.getLastModifiedTime(fileFor(clean(sym))).toInstant() : null; } catch (IOException e) { return null; }
    }
    public synchronized boolean isFresh(String sym) {
        Instant f = fetchedAt(sym);
        return f != null && Duration.between(f, Instant.now()).compareTo(FRESH) < 0 && get(sym) != null;
    }

    public synchronized void put(PriceSeries s) throws IOException { put(s, null, Instant.now()); }
    public synchronized void put(PriceSeries s, String name, Instant fetched) throws IOException {
        Files.createDirectories(dir);
        StringBuilder b = new StringBuilder("Date,Close\n");
        for (int i = 0; i < s.size(); i++) b.append(s.dates().get(i)).append(',').append(s.close()[i]).append('\n');
        Files.writeString(fileFor(s.symbol()), b.toString(), StandardCharsets.UTF_8);
        cache.put(s.symbol(), s);
        Map<String, Object> e = new LinkedHashMap<>(Json.obj(index().get(s.symbol())));
        if (name != null && !name.isBlank()) e.put("name", name);
        e.put("fetched", fetched.toString());
        index().put(s.symbol(), e);
        Files.writeString(indexFile(), Json.write(index()), StandardCharsets.UTF_8);
    }
    /** Symbols with saved prices, most recently updated first. */
    public synchronized List<String> symbols() {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> st = Files.list(dir)) {
            return st.filter(p -> p.toString().endsWith(".csv"))
                .sorted((a, b) -> { try { return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a)); } catch (IOException e) { return 0; } })
                .map(p -> p.getFileName().toString().replaceAll("\\.csv$", "").replace('_', '^')).toList();
        } catch (IOException e) { return List.of(); }
    }
}

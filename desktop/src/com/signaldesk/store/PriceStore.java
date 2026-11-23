package com.signaldesk.store;

import com.signaldesk.signals.PriceCsv;
import com.signaldesk.signals.PriceSeries;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Saved daily price files, one CSV per symbol in the prices folder. */
public final class PriceStore {
    private final Path dir;
    private final Map<String, PriceSeries> cache = new HashMap<>();
    public PriceStore(Path dir) { this.dir = dir; }

    public static String clean(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replaceFirst("^\\$", "").replaceAll("[^A-Z0-9.\\-^]", "");
    }
    private Path fileFor(String sym) { return dir.resolve(sym.replace('^', '_') + ".csv"); }

    public synchronized PriceSeries get(String sym) {
        sym = clean(sym); if (sym.isEmpty()) return null;
        if (cache.containsKey(sym)) return cache.get(sym);
        Path f = fileFor(sym);
        if (!Files.exists(f)) return null;
        try { PriceSeries s = PriceCsv.parse(Files.readString(f, StandardCharsets.UTF_8), sym); cache.put(sym, s); return s; }
        catch (Exception e) { return null; }
    }
    public synchronized void put(PriceSeries s) throws IOException {
        Files.createDirectories(dir);
        StringBuilder b = new StringBuilder("Date,Close\n");
        for (int i = 0; i < s.size(); i++) b.append(s.dates().get(i)).append(',').append(s.close()[i]).append('\n');
        Files.writeString(fileFor(s.symbol()), b.toString(), StandardCharsets.UTF_8);
        cache.put(s.symbol(), s);
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

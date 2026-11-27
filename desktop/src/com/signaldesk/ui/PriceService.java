package com.signaldesk.ui;

import com.signaldesk.signals.*;
import com.signaldesk.store.PriceDownloader;
import com.signaldesk.store.PriceStore;
import com.signaldesk.store.Universe;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/** Downloads prices in the background, hands results back on the Swing thread, and caches each symbol's analysis. */
public final class PriceService {
    private final MainFrame app;
    private final Set<String> inFlight = new HashSet<>();
    private boolean batchBusy;
    private Instant updated;
    private String problem;

    PriceService(MainFrame app) { this.app = app; }
    private PriceStore store() { return app.prices(); }
    public Instant updated() { return updated; }
    public String problem() { return problem; }
    public boolean busy() { return batchBusy; }

    /** CACHED reuses prices under 15 minutes old; LATEST fetches recent days now; FULL downloads the entire history again. */
    public enum Mode { CACHED, LATEST, FULL }
    public void fetch(String sym, boolean force, Consumer<PriceSeries> onData, Consumer<Exception> onError) { fetch(sym, force ? Mode.LATEST : Mode.CACHED, onData, onError); }
    public void fetch(String sym, Mode mode, Consumer<PriceSeries> onData, Consumer<Exception> onError) {
        String s = PriceStore.clean(sym);
        if (mode == Mode.CACHED && store().isFresh(s)) { onData.accept(store().get(s)); return; }
        if (!inFlight.add(s)) return;
        new SwingWorker<PriceDownloader.Result, Void>() {
            @Override protected PriceDownloader.Result doInBackground() throws Exception {
                PriceDownloader.Result r = PriceDownloader.download(s, mode == Mode.FULL ? null : store().get(s));
                store().put(r.series(), r.name(), r.fetched(), r.type());
                return r;
            }
            @Override protected void done() {
                inFlight.remove(s);
                try { onData.accept(get().series()); }
                catch (Exception ex) { onError.accept(ex.getCause() instanceof Exception c ? c : ex); }
            }
        }.execute();
    }

    /** What the home page keeps current: market indexes, the S&P 500 benchmark, the watchlist and practice positions. */
    public List<String> everyday() {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        for (String[] ix : Universe.INDEXES) s.add(ix[0]);
        s.add(Universe.BENCHMARK);
        for (String w : app.studio().watchlist) s.add(PriceStore.clean(w));
        s.addAll(app.studio().practice.positions().keySet());
        return new ArrayList<>(s);
    }
    public void refreshEveryday(boolean force, Runnable after) {
        refresh(everyday(), force, null, (got, failed) -> {
            if (!got.isEmpty()) updated = Instant.now();
            problem = failed.isEmpty() ? null
                : got.isEmpty() ? "Couldn't reach the price service. Showing the last prices saved."
                : "No prices found for " + String.join(", ", failed) + ".";
            app.refreshCurrent();
            if (after != null) after.run();
        });
    }

    /**
     * Brings a list of symbols up to date one at a time, skipping any still fresh unless forced.
     * progress gets (done, total) on the Swing thread; finish gets the symbols that updated and those that failed.
     */
    public void refresh(List<String> syms, boolean force, BiConsumer<Integer, Integer> progress, BiConsumer<List<String>, List<String>> finish) {
        if (batchBusy) { if (finish != null) finish.accept(List.of(), List.of()); return; }
        batchBusy = true;
        new SwingWorker<List<String>, Integer>() {
            final List<String> failed = new ArrayList<>();
            @Override protected List<String> doInBackground() {
                List<String> got = new ArrayList<>(); int k = 0;
                for (String s : syms) {
                    try {
                        if (!force && store().isFresh(s)) { got.add(s); continue; }
                        PriceDownloader.Result r = PriceDownloader.download(s, store().get(s));
                        store().put(r.series(), r.name(), r.fetched(), r.type());
                        got.add(s);
                        Thread.sleep(250);
                    } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    catch (Exception ex) { failed.add(s); }
                    finally { publish(++k); }
                }
                return got;
            }
            @Override protected void process(List<Integer> ks) { if (progress != null) progress.accept(ks.get(ks.size() - 1), syms.size()); }
            @Override protected void done() {
                batchBusy = false;
                List<String> got; try { got = get(); } catch (Exception e) { got = List.of(); }
                if (finish != null) finish.accept(got, failed);
            }
        }.execute();
    }

    // ------------------------------------------------------------------ analysis cache
    /** Everything Signal Desk works out about one symbol from its saved prices. */
    public record Analysis(String symbol, String name, AssetType type, PriceSeries series, Stats stats, Scoring.Result score) {}
    private final Map<String, Analysis> cache = new HashMap<>();
    private final Map<String, String> cacheKey = new HashMap<>();

    /** Analysis from saved prices, or null when there are none (or too few to analyze). Cached until new prices or settings. */
    public synchronized Analysis analysis(String sym) {
        String s = PriceStore.clean(sym);
        PriceSeries p = store().get(s);
        if (p == null || p.size() < 30) return null;
        PriceSeries bench = s.equals(Universe.BENCHMARK) ? null : store().get(Universe.BENCHMARK);
        String key = p.size() + "|" + p.lastDate() + "|" + p.last() + "|" + (bench == null ? "" : bench.lastDate()) + "|" + app.studio().sig.toJson();
        if (key.equals(cacheKey.get(s))) return cache.get(s);
        AssetType t = store().type(s);
        Stats st = Stats.of(p, bench, t);
        Scoring.Result sc = t == AssetType.INDEX ? null : Scoring.score(p, st, app.studio().sig);
        Analysis a = new Analysis(s, store().name(s), t, p, st, sc);
        cache.put(s, a); cacheKey.put(s, key);
        return a;
    }

    /** Plain-language reason for a failed download. */
    public static String explain(Exception ex) {
        if (ex instanceof PriceDownloader.NotFound) return ex.getMessage();
        Throwable c = ex;
        while (c.getCause() != null) c = c.getCause();
        if (c instanceof java.net.ConnectException || c instanceof java.net.UnknownHostException || c instanceof java.net.http.HttpTimeoutException
            || c instanceof java.io.IOException) return "Couldn't reach the price service. Check your internet connection.";
        return ex.getMessage() == null ? "The download didn't work." : ex.getMessage();
    }
    static void later(Runnable r) { SwingUtilities.invokeLater(r); }
}

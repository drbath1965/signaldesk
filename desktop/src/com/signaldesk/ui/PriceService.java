package com.signaldesk.ui;

import com.signaldesk.model.Holding;
import com.signaldesk.signals.PriceSeries;
import com.signaldesk.store.PriceDownloader;
import com.signaldesk.store.PriceStore;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/** Downloads prices in the background and hands results back on the Swing thread. */
public final class PriceService {
    private final MainFrame app;
    private final Set<String> inFlight = new HashSet<>();
    private boolean holdingsBusy;
    private Instant holdingsUpdated;
    private String holdingsProblem;

    PriceService(MainFrame app) { this.app = app; }
    private PriceStore store() { return app.prices(); }
    public Instant holdingsUpdated() { return holdingsUpdated; }
    public String holdingsProblem() { return holdingsProblem; }
    public boolean holdingsBusy() { return holdingsBusy; }

    /**
     * Gets current prices for one symbol. Saved prices younger than 15 minutes are used as is;
     * otherwise a fresh download replaces them. Errors arrive only through onError.
     */
    public void fetch(String sym, boolean force, Consumer<PriceSeries> onData, Consumer<Exception> onError) {
        String s = PriceStore.clean(sym);
        if (!force && store().isFresh(s)) { onData.accept(store().get(s)); return; }
        if (!inFlight.add(s)) return;
        new SwingWorker<PriceDownloader.Result, Void>() {
            @Override protected PriceDownloader.Result doInBackground() throws Exception {
                PriceDownloader.Result r = PriceDownloader.download(s);
                store().put(r.series(), r.name(), r.fetched());
                return r;
            }
            @Override protected void done() {
                inFlight.remove(s);
                try { onData.accept(get().series()); }
                catch (Exception ex) { onError.accept(ex.getCause() instanceof Exception c ? c : ex); }
            }
        }.execute();
    }

    /** Brings every holding's price up to the latest close. Quietly skips symbols it can't find. */
    public void refreshHoldings(boolean force, Runnable after) {
        if (holdingsBusy) return;
        List<String> syms = new ArrayList<>(new LinkedHashSet<>(app.studio().holdings.stream()
            .filter(h -> !h.sym.isBlank() && !"Cash".equals(h.cls)).map(h -> PriceStore.clean(h.sym)).toList()));
        if (syms.isEmpty()) { if (after != null) after.run(); return; }
        holdingsBusy = true;
        new SwingWorker<Map<String, Double>, Void>() {
            final List<String> failed = new ArrayList<>(); boolean offline;
            @Override protected Map<String, Double> doInBackground() {
                Map<String, Double> got = new HashMap<>();
                for (String s : syms) {
                    try {
                        if (!force && store().isFresh(s)) { got.put(s, store().get(s).last()); continue; }
                        PriceDownloader.Result r = PriceDownloader.download(s);
                        store().put(r.series(), r.name(), r.fetched());
                        got.put(s, r.series().last());
                        Thread.sleep(350);
                    } catch (PriceDownloader.NotFound nf) { failed.add(s); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    catch (Exception ex) { failed.add(s); offline = true; }
                }
                return got;
            }
            @Override protected void done() {
                holdingsBusy = false;
                Map<String, Double> got; try { got = get(); } catch (Exception e) { got = Map.of(); }
                boolean any = false;
                for (Holding h : app.studio().holdings) {
                    Double p = got.get(PriceStore.clean(h.sym));
                    if (p != null) { double r = Math.round(p * 100) / 100.0; if (r != h.price) { h.price = r; any = true; } }
                }
                if (!got.isEmpty()) holdingsUpdated = Instant.now();
                holdingsProblem = failed.isEmpty() ? null
                    : offline && got.isEmpty() ? "Couldn't reach the price service. Prices shown are the last ones saved."
                    : "No prices found for " + String.join(", ", failed) + ".";
                if (any) app.pricesUpdated(); else app.refreshCurrent();
                if (after != null) after.run();
            }
        }.execute();
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

package com.signaldesk.store;

import com.signaldesk.json.Json;
import com.signaldesk.signals.PriceSeries;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Fetches daily price history and symbol matches from Yahoo Finance's public chart and search
 * services. No account or API key is needed. (Stooq, used before, began requiring a key in 2026.)
 */
public final class PriceDownloader {
    private PriceDownloader() {}
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(12)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36";
    private static final String[] HOSTS = {"https://query1.finance.yahoo.com", "https://query2.finance.yahoo.com"};
    /** Test hook: -Dsignaldesk.priceBase=http://127.0.0.1:PORT sends requests to a local stand-in server. */
    private static String[] hosts() { String b = System.getProperty("signaldesk.priceBase"); return b != null ? new String[]{b} : HOSTS; }

    /** A symbol the service doesn't know. */
    public static final class NotFound extends Exception { public NotFound(String m) { super(m); } }

    /** full is true when the whole history was downloaded; added counts days that were new. */
    public record Result(PriceSeries series, String name, String currency, String exchange, Instant fetched, boolean full, int added, String type) {}
    public record Match(String symbol, String name, String exchange, String type) {
        @Override public String toString() { return symbol + "  " + name; }
    }

    /** Class shares like BRK.B are BRK-B on Yahoo; exchange suffixes like RY.TO stay as they are. */
    public static String yahooSymbol(String sym) {
        return sym.matches("[A-Z]{1,5}\\.[A-Z]") ? sym.replace('.', '-') : sym;
    }
    public static String quotePage(String sym) { return "https://finance.yahoo.com/quote/" + enc(yahooSymbol(sym)) + "/history/"; }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    /** Full daily history for a symbol. */
    public static Result download(String sym) throws Exception { return download(sym, null); }

    /**
     * Brings saved prices up to date. With a recent saved history, only the last month is fetched and merged in.
     * If those overlapping days no longer match (a split or dividend changed past adjusted prices), or nothing
     * usable is saved, the entire history is downloaded instead.
     */
    public static Result download(String sym, PriceSeries saved) throws Exception {
        if (saved != null && saved.hasOhlc() && saved.size() >= 60) {
            long gap = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(saved.lastDate()), LocalDate.now());
            if (gap >= 0 && gap <= 25) {
                Result recent = fetch(sym, "range=1mo&interval=1d", 1);
                if (consistent(saved, recent.series())) {
                    PriceSeries merged = merge(saved, recent.series());
                    return new Result(merged, recent.name(), recent.currency(), recent.exchange(), recent.fetched(), false, merged.size() - saved.size(), recent.type());
                }
            }
        }
        long now = Instant.now().getEpochSecond() + 86400;
        Result full = fetch(sym, "period1=-2208988800&period2=" + now + "&interval=1d", 60);
        return new Result(full.series(), full.name(), full.currency(), full.exchange(), full.fetched(), true, full.series().size() - (saved == null ? 0 : saved.size()), full.type());
    }

    private static Result fetch(String sym, String query, int minRows) throws Exception {
        Exception last = null;
        for (String host : hosts()) {
            try { return parseChart(get(host + "/v8/finance/chart/" + enc(yahooSymbol(sym)) + "?" + query + "&includeAdjustedClose=true&events=div%2Csplits"), sym, minRows); }
            catch (NotFound e) { throw e; }
            catch (Exception e) { last = e; }
        }
        throw last;
    }

    /** True when overlapping days agree, apart from the newest two (today's price moves; yesterday's can be revised). */
    public static boolean consistent(PriceSeries saved, PriceSeries recent) {
        java.util.Map<String, Integer> at = new java.util.HashMap<>();
        for (int i = 0; i < saved.size(); i++) at.put(saved.dates().get(i), i);
        int overlap = 0;
        for (int j = 0; j < recent.size() - 2; j++) {
            Integer i = at.get(recent.dates().get(j)); if (i == null) continue;
            overlap++;
            if (Math.abs(saved.close()[i] / recent.close()[j] - 1) > 5e-4) return false;
            if (saved.hasOhlc() && recent.hasOhlc() && Math.abs(saved.rawClose()[i] / recent.rawClose()[j] - 1) > 5e-4) return false;
        }
        return overlap >= 3;
    }
    /** Saved days, with the recent download replacing and extending the end. */
    public static PriceSeries merge(PriceSeries saved, PriceSeries recent) {
        java.util.TreeMap<String, double[]> m = new java.util.TreeMap<>();
        for (PriceSeries p : new PriceSeries[]{saved, recent})
            for (int i = 0; i < p.size(); i++) m.put(p.dates().get(i), new double[]{p.close()[i], p.open()[i], p.high()[i], p.low()[i], p.rawClose()[i], p.vol(i)});
        int n = m.size(); java.util.List<String> d = new java.util.ArrayList<>(n);
        double[] c = new double[n], o = new double[n], h = new double[n], l = new double[n], r = new double[n], v = new double[n]; int k = 0;
        for (java.util.Map.Entry<String, double[]> e : m.entrySet()) { double[] x = e.getValue(); d.add(e.getKey()); c[k] = x[0]; o[k] = x[1]; h[k] = x[2]; l[k] = x[3]; r[k] = x[4]; v[k] = x[5]; k++; }
        return new PriceSeries(saved.symbol(), d, c, o, h, l, r, v);
    }

    /** Stocks, ETFs, funds and indexes matching a name or ticker. */
    public static List<Match> search(String query) throws Exception {
        String[] h = hosts();
        String body = get(h[h.length - 1] + "/v1/finance/search?q=" + enc(query) + "&quotesCount=8&newsCount=0&listsCount=0&enableFuzzyQuery=false");
        return parseSearch(body);
    }

    private static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
            .header("User-Agent", UA).header("Accept", "application/json,text/plain,*/*").GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 429) { Thread.sleep(4000); res = HTTP.send(req, HttpResponse.BodyHandlers.ofString()); }
        int code = res.statusCode();
        if (code == 404) {
            String d = describeError(res.body());
            throw new NotFound(d != null ? d : "No stock found for that symbol.");
        }
        if (code == 429) throw new Exception("The price service is busy right now. Try again in a minute.");
        if (code != 200) throw new Exception("The price service answered with status " + code + ".");
        return res.body();
    }
    private static String describeError(String body) {
        try { Map<String, Object> e = Json.obj(Json.obj(Json.obj(Json.parse(body)), "chart"), "error"); String d = Json.str(e, "description", null); return d; }
        catch (Exception ex) { return null; }
    }

    // ------------------------------------------------------------------ parsing (public for tests)
    public static Result parseChart(String body, String sym) throws Exception { return parseChart(body, sym, 60); }
    public static Result parseChart(String body, String sym, int minRows) throws Exception {
        Map<String, Object> chart = Json.obj(Json.obj(Json.parse(body)), "chart");
        Object err = chart.get("error");
        if (err instanceof Map<?, ?> em && !em.isEmpty()) throw new NotFound(Json.str(Json.obj(err), "description", "No data for " + sym + "."));
        List<Object> results = Json.arr(chart, "result");
        if (results.isEmpty()) throw new NotFound("No price history found for " + sym + ".");
        Map<String, Object> r = Json.obj(results.get(0)), meta = Json.obj(r, "meta"), ind = Json.obj(r, "indicators");
        String gran = Json.str(meta, "dataGranularity", "1d");
        if (!gran.equals("1d")) throw new Exception("The price service sent " + gran + " prices instead of daily ones.");
        long offset = (long) Json.num(meta, "gmtoffset", 0);
        List<Object> ts = Json.arr(r, "timestamp");
        List<Object> adj = Json.arr(Json.obj(Json.arr(ind, "adjclose").isEmpty() ? null : Json.arr(ind, "adjclose").get(0)), "adjclose");
        Map<String, Object> q = Json.obj(Json.arr(ind, "quote").isEmpty() ? null : Json.arr(ind, "quote").get(0));
        List<Object> close = Json.arr(q, "close"), open = Json.arr(q, "open"), high = Json.arr(q, "high"), low = Json.arr(q, "low"), vol = Json.arr(q, "volume");
        TreeMap<LocalDate, double[]> byDate = new TreeMap<>();
        for (int i = 0; i < ts.size(); i++) {
            if (!(ts.get(i) instanceof Number t)) continue;
            Double c = num(close, i), a = num(adj, i);
            if (c == null && a == null) continue;
            if (a == null) a = c; if (c == null) c = a;
            if (a <= 0 || c <= 0) continue;
            double o = orElse(num(open, i), c), h = orElse(num(high, i), Math.max(o, c)), l = orElse(num(low, i), Math.min(o, c));
            h = Math.max(h, Math.max(o, c)); l = Math.min(l, Math.min(o, c));
            LocalDate d = Instant.ofEpochSecond(t.longValue() + offset).atZone(ZoneOffset.UTC).toLocalDate();
            byDate.put(d, new double[]{round(a), round(o), round(h), round(l), round(c), orElse(num(vol, i), 0)});
        }
        if (byDate.size() < minRows) throw new NotFound("Only " + byDate.size() + " days of history are available for " + sym + ", too few to analyze.");
        int n = byDate.size(); List<String> dates = new ArrayList<>(n);
        double[] ac = new double[n], o = new double[n], h = new double[n], l = new double[n], rc = new double[n], v = new double[n]; int k = 0;
        for (Map.Entry<LocalDate, double[]> e : byDate.entrySet()) { double[] x = e.getValue(); dates.add(e.getKey().toString()); ac[k] = x[0]; o[k] = x[1]; h[k] = x[2]; l[k] = x[3]; rc[k] = x[4]; v[k] = x[5]; k++; }
        String name = Json.str(meta, "longName", Json.str(meta, "shortName", ""));
        return new Result(new PriceSeries(sym, dates, ac, o, h, l, rc, v), name, Json.str(meta, "currency", "USD"),
            Json.str(meta, "fullExchangeName", Json.str(meta, "exchangeName", "")), Instant.now(), true, n,
            com.signaldesk.signals.AssetType.of(Json.str(meta, "instrumentType", null), sym).name());
    }
    private static Double num(List<Object> a, int i) { return i < a.size() && a.get(i) instanceof Number n ? n.doubleValue() : null; }
    private static double orElse(Double v, double d) { return v == null ? d : v; }
    private static double round(double v) { return Math.round(v * 10000) / 10000.0; }

    public static List<Match> parseSearch(String body) {
        List<Match> out = new ArrayList<>();
        Set<String> ok = Set.of("EQUITY", "ETF", "INDEX", "MUTUALFUND");
        for (Object o : Json.arr(Json.obj(Json.parse(body)), "quotes")) {
            Map<String, Object> q = Json.obj(o);
            String type = Json.str(q, "quoteType", "");
            String s = Json.str(q, "symbol", "");
            if (s.isEmpty() || !ok.contains(type)) continue;
            String name = Json.str(q, "longname", Json.str(q, "shortname", ""));
            out.add(new Match(s.matches("[A-Z]{1,5}-[A-Z]") ? s.replace('-', '.') : s, name, Json.str(q, "exchDisp", ""), Json.str(q, "typeDisp", type)));
        }
        return out;
    }
}

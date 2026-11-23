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

    public record Result(PriceSeries series, String name, String currency, String exchange, Instant fetched) {}
    public record Match(String symbol, String name, String exchange, String type) {
        @Override public String toString() { return symbol + "  " + name; }
    }

    /** Class shares like BRK.B are BRK-B on Yahoo; exchange suffixes like RY.TO stay as they are. */
    public static String yahooSymbol(String sym) {
        return sym.matches("[A-Z]{1,5}\\.[A-Z]") ? sym.replace('.', '-') : sym;
    }
    public static String quotePage(String sym) { return "https://finance.yahoo.com/quote/" + enc(yahooSymbol(sym)) + "/history/"; }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    /** Five years of daily closes (split- and dividend-adjusted), including today's latest price while the market is open. */
    public static Result download(String sym) throws Exception {
        Exception last = null;
        for (String host : hosts()) {
            try { return parseChart(get(host + "/v8/finance/chart/" + enc(yahooSymbol(sym)) + "?range=5y&interval=1d&includeAdjustedClose=true&events=div%2Csplits"), sym); }
            catch (NotFound e) { throw e; }
            catch (Exception e) { last = e; }
        }
        throw last;
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
    public static Result parseChart(String body, String sym) throws Exception {
        Map<String, Object> chart = Json.obj(Json.obj(Json.parse(body)), "chart");
        Object err = chart.get("error");
        if (err instanceof Map<?, ?> em && !em.isEmpty()) throw new NotFound(Json.str(Json.obj(err), "description", "No data for " + sym + "."));
        List<Object> results = Json.arr(chart, "result");
        if (results.isEmpty()) throw new NotFound("No price history found for " + sym + ".");
        Map<String, Object> r = Json.obj(results.get(0)), meta = Json.obj(r, "meta"), ind = Json.obj(r, "indicators");
        long offset = (long) Json.num(meta, "gmtoffset", 0);
        List<Object> ts = Json.arr(r, "timestamp");
        List<Object> adj = Json.arr(Json.obj(Json.arr(ind, "adjclose").isEmpty() ? null : Json.arr(ind, "adjclose").get(0)), "adjclose");
        List<Object> close = Json.arr(Json.obj(Json.arr(ind, "quote").isEmpty() ? null : Json.arr(ind, "quote").get(0)), "close");
        TreeMap<LocalDate, Double> byDate = new TreeMap<>();
        for (int i = 0; i < ts.size(); i++) {
            if (!(ts.get(i) instanceof Number t)) continue;
            Object v = i < adj.size() && adj.get(i) instanceof Number ? adj.get(i) : i < close.size() ? close.get(i) : null;
            if (!(v instanceof Number n) || n.doubleValue() <= 0) continue;
            LocalDate d = Instant.ofEpochSecond(t.longValue() + offset).atZone(ZoneOffset.UTC).toLocalDate();
            byDate.put(d, Math.round(n.doubleValue() * 10000) / 10000.0);
        }
        if (byDate.size() < 60) throw new NotFound("Only " + byDate.size() + " days of history are available for " + sym + ", too few to analyze.");
        List<String> dates = new ArrayList<>(); double[] c = new double[byDate.size()]; int k = 0;
        for (Map.Entry<LocalDate, Double> e : byDate.entrySet()) { dates.add(e.getKey().toString()); c[k++] = e.getValue(); }
        String name = Json.str(meta, "longName", Json.str(meta, "shortName", ""));
        return new Result(new PriceSeries(sym, dates, c), name, Json.str(meta, "currency", "USD"),
            Json.str(meta, "fullExchangeName", Json.str(meta, "exchangeName", "")), Instant.now());
    }

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

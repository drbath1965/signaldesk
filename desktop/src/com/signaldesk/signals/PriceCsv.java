package com.signaldesk.signals;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Reads daily price files from Yahoo Finance, Nasdaq, Stooq and most brokers, and the files Signal Desk saves:
 * a Date column plus Close (or Close/Last, Price), and optionally Adj Close, Open, High, Low and Volume.
 */
public final class PriceCsv {
    private PriceCsv() {}
    private static final DateTimeFormatter[] FORMATS = {
        DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("M/d/yyyy"), DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("yyyyMMdd"), DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.US), DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)};

    public static final String HEADER = "Date,Open,High,Low,Close,Adj Close,Volume";

    public static PriceSeries parse(String text, String symbol) {
        String[] lines = text.replace("\r", "").split("\n");
        List<String> rows = new ArrayList<>();
        for (String l : lines) if (!l.isBlank()) rows.add(l);
        if (rows.size() < 3) throw new IllegalArgumentException("That file has fewer than three rows. Load a daily price history CSV.");
        String delim = rows.get(0).contains(";") && !rows.get(0).contains(",") ? ";" : ",";
        String[] head = split(rows.get(0), delim);
        int di = -1, adj = -1, cl = -1, op = -1, hi = -1, lo = -1, vo = -1;
        for (int i = 0; i < head.length; i++) {
            String h = head[i].trim().toLowerCase(Locale.ROOT).replace("\"", "").replace("<", "").replace(">", "");
            switch (h) {
                case "date", "time", "timestamp" -> { if (di < 0) di = i; }
                case "adj close", "adj_close", "adjclose", "adjusted close" -> adj = i;
                case "close", "close/last", "price" -> { if (cl < 0) cl = i; }
                case "open" -> op = i;
                case "high" -> hi = i;
                case "low" -> lo = i;
                case "volume", "vol" -> vo = i;
                default -> {}
            }
        }
        if (di < 0 || (cl < 0 && adj < 0)) throw new IllegalArgumentException("Couldn't find a Date and a Close column. The first row should name the columns, for example: Date,Open,High,Low,Close,Volume");
        boolean ohlc = op >= 0 && hi >= 0 && lo >= 0 && cl >= 0;
        TreeMap<LocalDate, double[]> byDate = new TreeMap<>();
        for (int r = 1; r < rows.size(); r++) {
            String[] c = split(rows.get(r), delim);
            LocalDate d = di < c.length ? date(c[di].replace("\"", "").trim()) : null;
            Double raw = cl >= 0 && cl < c.length ? number(c[cl]) : null;
            Double ad = adj >= 0 && adj < c.length ? number(c[adj]) : null;
            if (ad == null) ad = raw; if (raw == null) raw = ad;
            if (d == null || ad == null || ad <= 0) continue;
            double o = ohlc ? orElse(number(at(c, op)), raw) : raw, h = ohlc ? orElse(number(at(c, hi)), raw) : raw, l = ohlc ? orElse(number(at(c, lo)), raw) : raw;
            double v = vo >= 0 ? orElse(number(at(c, vo)), 0.0) : 0;
            byDate.put(d, new double[]{ad, o, h, l, raw, v});
        }
        if (byDate.size() < 60) throw new IllegalArgumentException("Only " + byDate.size() + " usable price rows were found. Load at least a few months of daily data.");
        int n = byDate.size(); List<String> dates = new ArrayList<>(n);
        double[] c = new double[n], o = new double[n], h = new double[n], l = new double[n], rc = new double[n], v = new double[n]; int k = 0;
        for (Map.Entry<LocalDate, double[]> e : byDate.entrySet()) { double[] x = e.getValue(); dates.add(e.getKey().toString()); c[k] = x[0]; o[k] = x[1]; h[k] = x[2]; l[k] = x[3]; rc[k] = x[4]; v[k] = x[5]; k++; }
        return ohlc ? new PriceSeries(symbol, dates, c, o, h, l, rc, vo >= 0 ? v : null) : new PriceSeries(symbol, dates, c, null, null, null, null, vo >= 0 ? v : null);
    }

    /** Writes the standard layout: Date,Open,High,Low,Close,Adj Close,Volume (or Date,Close for close-only data). */
    public static String write(PriceSeries s) {
        StringBuilder b = new StringBuilder(s.size() * 64);
        if (s.hasOhlc()) {
            b.append(HEADER).append('\n');
            for (int i = 0; i < s.size(); i++)
                b.append(s.dates().get(i)).append(',').append(n(s.open()[i])).append(',').append(n(s.high()[i])).append(',').append(n(s.low()[i])).append(',')
                 .append(n(s.rawClose()[i])).append(',').append(n(s.close()[i])).append(',').append((long) s.vol(i)).append('\n');
        } else {
            b.append("Date,Close").append(s.hasVolume() ? ",Volume" : "").append('\n');
            for (int i = 0; i < s.size(); i++) { b.append(s.dates().get(i)).append(',').append(n(s.close()[i])); if (s.hasVolume()) b.append(',').append((long) s.vol(i)); b.append('\n'); }
        }
        return b.toString();
    }
    private static String n(double v) { String s = String.format(Locale.US, "%.4f", v); s = s.replaceAll("0+$", ""); return s.endsWith(".") ? s.substring(0, s.length() - 1) : s; }

    /** Guess a ticker from a downloaded file's name, e.g. "aapl_us_d.csv" → "AAPL". */
    public static String symbolFromFileName(String name) {
        String base = name.replaceAll("(?i)\\.csv$", "");
        if (base.isBlank() || base.toLowerCase(Locale.ROOT).startsWith("historicaldata")) return "TICKER";
        String s = base.split("[_\\s]|(?i)\\.us$")[0].toUpperCase(Locale.ROOT);
        return s.length() > 12 ? s.substring(0, 12) : s;
    }

    private static String at(String[] c, int i) { return i >= 0 && i < c.length ? c[i] : null; }
    private static double orElse(Double v, double d) { return v == null ? d : v; }
    private static String[] split(String line, String delim) {
        if (!line.contains("\"")) return line.split(delim, -1);
        List<String> out = new ArrayList<>(); StringBuilder b = new StringBuilder(); boolean q = false;
        for (char ch : line.toCharArray()) {
            if (ch == '"') q = !q;
            else if (!q && String.valueOf(ch).equals(delim)) { out.add(b.toString()); b.setLength(0); }
            else b.append(ch);
        }
        out.add(b.toString());
        return out.toArray(new String[0]);
    }
    private static LocalDate date(String s) {
        for (DateTimeFormatter f : FORMATS) try { return LocalDate.parse(s, f); } catch (Exception ignored) {}
        if (s.length() > 10) return date(s.substring(0, 10));
        return null;
    }
    private static Double number(String s) {
        if (s == null) return null;
        String c = s.replace("$", "").replace("\"", "").replace(",", "").trim();
        if (c.isEmpty() || c.equalsIgnoreCase("null") || c.equalsIgnoreCase("n/a")) return null;
        try { return Double.parseDouble(c); } catch (Exception e) { return null; }
    }
}

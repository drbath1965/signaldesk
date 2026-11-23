package com.signaldesk.signals;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Reads daily price files from Stooq, Nasdaq, Yahoo Finance and most brokers:
 * any CSV with a Date column and a Close, Adj Close, Close/Last or Price column.
 */
public final class PriceCsv {
    private PriceCsv() {}
    private static final DateTimeFormatter[] FORMATS = {
        DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("M/d/yyyy"), DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("yyyyMMdd"), DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.US), DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)};

    public static PriceSeries parse(String text, String symbol) {
        String[] lines = text.replace("\r", "").split("\n");
        List<String> rows = new ArrayList<>();
        for (String l : lines) if (!l.isBlank()) rows.add(l);
        if (rows.size() < 3) throw new IllegalArgumentException("That file has fewer than three rows. Load a daily price history CSV.");
        String delim = rows.get(0).contains(";") && !rows.get(0).contains(",") ? ";" : ",";
        String[] head = split(rows.get(0), delim);
        int di = -1, ci = -1;
        for (int i = 0; i < head.length; i++) {
            String h = head[i].trim().toLowerCase(Locale.ROOT).replace("\"", "");
            if (di < 0 && (h.equals("date") || h.equals("time") || h.equals("timestamp") || h.equals("<date>"))) di = i;
            if (h.equals("adj close") || h.equals("adj_close") || h.equals("adjclose")) ci = i;
        }
        if (ci < 0) for (int i = 0; i < head.length; i++) {
            String h = head[i].trim().toLowerCase(Locale.ROOT).replace("\"", "");
            if (h.equals("close") || h.equals("<close>") || h.equals("price") || h.equals("close/last")) { ci = i; break; }
        }
        if (di < 0 || ci < 0) throw new IllegalArgumentException("Couldn't find a Date and a Close column. The first row should name the columns, for example: Date,Open,High,Low,Close,Volume");
        TreeMap<LocalDate, Double> byDate = new TreeMap<>();
        for (int r = 1; r < rows.size(); r++) {
            String[] c = split(rows.get(r), delim);
            if (c.length <= Math.max(di, ci)) continue;
            LocalDate d = date(c[di].replace("\"", "").trim());
            Double v = number(c[ci]);
            if (d != null && v != null && v > 0) byDate.put(d, v);
        }
        if (byDate.size() < 60) throw new IllegalArgumentException("Only " + byDate.size() + " usable price rows were found. Load at least a few months of daily data.");
        List<String> dates = new ArrayList<>(); double[] close = new double[byDate.size()]; int k = 0;
        for (Map.Entry<LocalDate, Double> e : byDate.entrySet()) { dates.add(e.getKey().toString()); close[k++] = e.getValue(); }
        return new PriceSeries(symbol, dates, close);
    }

    /** Guess a ticker from a downloaded file's name, e.g. "aapl_us_d.csv" → "AAPL". */
    public static String symbolFromFileName(String name) {
        String base = name.replaceAll("(?i)\\.csv$", "");
        if (base.isBlank() || base.toLowerCase(Locale.ROOT).startsWith("historicaldata")) return "TICKER";
        String s = base.split("[_\\s]|(?i)\\.us$")[0].toUpperCase(Locale.ROOT);
        return s.length() > 12 ? s.substring(0, 12) : s;
    }

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
        try { return Double.parseDouble(s.replace("$", "").replace("\"", "").replace(",", "").trim()); } catch (Exception e) { return null; }
    }
}

package com.signaldesk.signals;

import java.util.List;

/** Daily closing prices for one symbol, oldest first. */
public record PriceSeries(String symbol, List<String> dates, double[] close) {
    public int size() { return close.length; }
    public double last() { return close[close.length - 1]; }
    public String lastDate() { return dates.get(dates.size() - 1); }
}

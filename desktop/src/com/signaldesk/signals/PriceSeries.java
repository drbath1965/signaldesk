package com.signaldesk.signals;

import java.util.Arrays;
import java.util.List;

/**
 * Daily prices for one symbol, oldest first.
 * close is the split- and dividend-adjusted close used for indicators and backtests.
 * open/high/low/rawClose are as traded (split-adjusted); volume is shares traded. These four may be null
 * when a file had closing prices only.
 */
public record PriceSeries(String symbol, List<String> dates, double[] close, double[] open, double[] high, double[] low, double[] rawClose, double[] volume) {
    public PriceSeries(String symbol, List<String> dates, double[] close) { this(symbol, dates, close, null, null, null, null, null); }

    public int size() { return close.length; }
    public double last() { return close[close.length - 1]; }
    public String lastDate() { return dates.get(dates.size() - 1); }
    public String firstDate() { return dates.get(0); }
    public boolean hasOhlc() { return open != null && high != null && low != null && rawClose != null; }
    public boolean hasVolume() { return volume != null; }

    /** Converts as-traded prices onto the adjusted scale so candles line up with the adjusted close. */
    public double factor(int i) { return hasOhlc() && rawClose[i] > 0 ? close[i] / rawClose[i] : 1; }
    public double adjOpen(int i) { return hasOhlc() ? open[i] * factor(i) : close[i]; }
    public double adjHigh(int i) { return hasOhlc() ? high[i] * factor(i) : close[i]; }
    public double adjLow(int i) { return hasOhlc() ? low[i] * factor(i) : close[i]; }
    public double vol(int i) { return volume == null ? 0 : volume[i]; }

    /** The most recent n days. */
    public PriceSeries tail(int n) {
        if (n >= size()) return this;
        int a = size() - n;
        return new PriceSeries(symbol, dates.subList(a, size()), Arrays.copyOfRange(close, a, size()),
            cut(open, a), cut(high, a), cut(low, a), cut(rawClose, a), cut(volume, a));
    }
    private double[] cut(double[] x, int a) { return x == null ? null : Arrays.copyOfRange(x, a, x.length); }
    public PriceSeries withSymbol(String s) { return new PriceSeries(s, dates, close, open, high, low, rawClose, volume); }
}

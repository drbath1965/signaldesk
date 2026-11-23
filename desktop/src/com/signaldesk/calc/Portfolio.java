package com.signaldesk.calc;

import com.signaldesk.model.Holding;
import com.signaldesk.model.Studio;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Portfolio {
    public final double value, cost;
    public final Map<String, Double> byClass = new LinkedHashMap<>();
    public final Holding largest;

    private Portfolio(Studio s) {
        double v = 0, c = 0; Holding big = null;
        for (Holding h : s.holdings) {
            v += h.value(); c += h.costBasis(); byClass.merge(h.cls, h.value(), Double::sum);
            if (big == null || h.value() > big.value()) big = h;
        }
        value = v; cost = c; largest = big;
    }
    public static Portfolio of(Studio s) { return new Portfolio(s); }
    public double gain() { return value - cost; }
    public double gainPct() { return cost != 0 ? value / cost - 1 : 0; }
    public double weightPct(String cls) { return value > 0 ? byClass.getOrDefault(cls, 0.0) / value * 100 : 0; }
}

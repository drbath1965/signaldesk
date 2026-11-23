package com.signaldesk.calc;

import com.signaldesk.model.*;
import java.util.HashMap;
import java.util.Map;

public final class NetWorth {
    public final double assets, liabilities, portfolio;
    public final Map<String, Double> byType = new HashMap<>();

    private NetWorth(Studio s) {
        portfolio = Portfolio.of(s).value;
        double a = 0;
        for (Account x : s.accounts) { byType.merge(x.type, x.balance, Double::sum); a += x.balance; }
        assets = a + portfolio;
        liabilities = s.debts.stream().mapToDouble(d -> d.balance).sum();
    }
    public static NetWorth of(Studio s) { return new NetWorth(s); }
    public double net() { return assets - liabilities; }
    public double type(String t) { return byType.getOrDefault(t, 0.0); }
}

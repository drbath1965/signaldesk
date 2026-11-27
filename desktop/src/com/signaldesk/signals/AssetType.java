package com.signaldesk.signals;

import java.util.Locale;

/** What kind of thing a symbol is. Decides labels, trading days per year, and which lessons apply. */
public enum AssetType {
    STOCK("Stock"), ETF("ETF"), FUND("Mutual fund"), CRYPTO("Crypto"), INDEX("Index"), OTHER("Other");

    public final String label;
    AssetType(String label) { this.label = label; }
    @Override public String toString() { return label; }

    /** From Yahoo's instrument type when known, otherwise from the symbol's shape. */
    public static AssetType of(String yahooType, String symbol) {
        if (yahooType != null) switch (yahooType.toUpperCase(Locale.ROOT)) {
            case "EQUITY": return STOCK;
            case "ETF": return ETF;
            case "MUTUALFUND": return FUND;
            case "CRYPTOCURRENCY": return CRYPTO;
            case "INDEX": return INDEX;
            default: break;
        }
        return guess(symbol);
    }
    public static AssetType guess(String s) {
        if (s == null) return OTHER;
        if (s.startsWith("^")) return INDEX;
        if (s.matches("[A-Z0-9]{2,10}-(USD|USDT|EUR|GBP)")) return CRYPTO;
        if (s.matches("[A-Z]{4}X")) return FUND;
        return STOCK;
    }
    /** Days a year this asset trades: every day for crypto, about 252 for exchange-traded assets. */
    public int periodsPerYear() { return this == CRYPTO ? 365 : 252; }
}

package com.signaldesk.store;

import java.util.List;

/** Symbol lists the Picks page can rank. Well-known names chosen for broad coverage, not as recommendations. */
public final class Universe {
    public record List_(String key, String label, String about, List<String> symbols) {}
    private Universe() {}
    public static final List<List_> ALL = List.of(
        new List_("stocks", "Large US stocks", "30 of the largest US companies across sectors",
            List.of("AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "BRK.B", "JPM", "V", "MA", "UNH", "JNJ", "XOM", "PG", "HD",
                    "COST", "LLY", "AVGO", "KO", "PEP", "WMT", "BAC", "DIS", "NFLX", "ADBE", "CRM", "ORCL", "CSCO", "INTC", "MCD")),
        new List_("etfs", "ETFs", "Broad stock, international, bond, gold, real estate and sector funds that trade like stocks",
            List.of("SPY", "VOO", "VTI", "QQQ", "IWM", "VXUS", "VEA", "VWO", "BND", "AGG", "TLT", "GLD", "VNQ", "SCHD", "XLK")),
        new List_("funds", "Mutual funds", "Index and actively managed mutual funds, priced once a day",
            List.of("VFIAX", "VTSAX", "VTIAX", "VBTLX", "FXAIX", "FZROX", "FSKAX", "SWPPX", "VWELX", "VDIGX", "AGTHX", "DODGX")),
        new List_("dividends", "Dividend payers", "30 well-known dividend-paying US companies and 8 dividend-focused ETFs",
            List.of("KO", "PEP", "JNJ", "PG", "XOM", "CVX", "VZ", "T", "MO", "PM", "ABBV", "PFE", "MRK", "IBM", "MMM",
                    "O", "MCD", "HD", "JPM", "CSCO", "TXN", "KMB", "CL", "DUK", "SO", "NEE", "LMT", "UPS", "CAT", "WMT",
                    "SCHD", "VYM", "DVY", "HDV", "NOBL", "SPYD", "DGRO", "VIG")),
        new List_("crypto", "Crypto", "Large cryptocurrencies priced in US dollars, trading every day",
            List.of("BTC-USD", "ETH-USD", "SOL-USD", "XRP-USD", "ADA-USD", "DOGE-USD", "AVAX-USD", "LINK-USD", "LTC-USD", "DOT-USD")));
    public static List_ find(String key) { for (List_ u : ALL) if (u.key().equals(key)) return u; return null; }
    public static final String BENCHMARK = "^GSPC";
    public static final List<String[]> INDEXES = List.of(
        new String[]{"^GSPC", "S&P 500"}, new String[]{"^IXIC", "Nasdaq"}, new String[]{"^DJI", "Dow Jones"}, new String[]{"^RUT", "Russell 2000"}, new String[]{"BTC-USD", "Bitcoin"});
}

package com.signaldesk.store;

import com.signaldesk.signals.PriceCsv;
import com.signaldesk.signals.PriceSeries;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/** Downloads free daily price history from Stooq. */
public final class PriceDownloader {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).followRedirects(HttpClient.Redirect.NORMAL).build();

    public static String stooqUrl(String sym) {
        String s = sym.toLowerCase(Locale.ROOT).replace('.', '-');
        if (!s.startsWith("^") && !s.contains(".")) s = s + ".us";
        return "https://stooq.com/q/d/l/?s=" + s + "&i=d";
    }
    public static String nasdaqUrl(String sym) {
        return "https://www.nasdaq.com/market-activity/stocks/" + sym.toLowerCase(Locale.ROOT) + "/historical?timeline=y5";
    }

    public static PriceSeries download(String sym) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(stooqUrl(sym))).timeout(Duration.ofSeconds(20))
            .header("User-Agent", "SignalDesk/1.0 (desktop)").GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new Exception("Stooq answered with status " + res.statusCode() + ".");
        String body = res.body();
        if (body == null || body.isBlank() || body.trim().equalsIgnoreCase("No data") || !body.toLowerCase(Locale.ROOT).contains("date"))
            throw new Exception("Stooq has no daily prices for " + sym + ". Check the symbol, or download a file from Nasdaq and use Load price file.");
        return PriceCsv.parse(body, sym);
    }
}

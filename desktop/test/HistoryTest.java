import com.signaldesk.signals.*;
import com.signaldesk.store.*;
import java.nio.file.*;
/** Full download, saving, incremental update, merge, and full re-download after a dividend, against the stand-in server. */
public class HistoryTest {
  static void check(boolean ok, String what) { System.out.println((ok ? "PASS  " : "FAIL  ") + what); if (!ok) failed++; }
  static int failed;
  public static void main(String[] a) throws Exception {
    Path dir = Paths.get(a[0]); PriceStore store = new PriceStore(dir);
    PriceDownloader.Result full = PriceDownloader.download("MSFT", null);
    PriceSeries s = full.series();
    check(full.full() && s.size() > 9000, "first download is the full history: " + s.size() + " days from " + s.firstDate() + " to " + s.lastDate());
    check(s.hasOhlc() && s.hasVolume(), "open, high, low, close and volume present");
    check(s.firstDate().startsWith("1990"), "history starts in 1990");
    boolean sane = true; for (int i = 0; i < s.size(); i++) if (s.adjHigh(i) + 1e-9 < Math.max(s.adjOpen(i), s.close()[i]) || s.adjLow(i) - 1e-9 > Math.min(s.adjOpen(i), s.close()[i])) sane = false;
    check(sane, "every day's high and low bracket its open and close");
    store.put(s, full.name(), full.fetched());
    String head = Files.readAllLines(store.file("MSFT")).get(0);
    check(head.equals(PriceCsv.HEADER), "saved file header: " + head);
    PriceSeries back = new PriceStore(dir).get("MSFT");
    check(back != null && back.size() == s.size() && back.hasOhlc() && Math.abs(back.close()[100] - s.close()[100]) < 1e-6, "saved file reads back identically");

    PriceDownloader.Result inc = PriceDownloader.download("MSFT", back);
    check(!inc.full() && inc.added() == 0 && inc.series().size() == s.size(), "next update fetches only the last month and adds nothing new (full=" + inc.full() + ", added=" + inc.added() + ")");
    PriceSeries older = back.tail(back.size()).withSymbol("MSFT");
    PriceSeries cut = new PriceSeries("MSFT", older.dates().subList(0, older.size() - 4), java.util.Arrays.copyOf(older.close(), older.size() - 4),
        java.util.Arrays.copyOf(older.open(), older.size() - 4), java.util.Arrays.copyOf(older.high(), older.size() - 4), java.util.Arrays.copyOf(older.low(), older.size() - 4),
        java.util.Arrays.copyOf(older.rawClose(), older.size() - 4), java.util.Arrays.copyOf(older.volume(), older.size() - 4));
    PriceDownloader.Result inc2 = PriceDownloader.download("MSFT", cut);
    check(!inc2.full() && inc2.added() == 4 && inc2.series().lastDate().equals(s.lastDate()), "missing last 4 days are fetched and merged in (added=" + inc2.added() + ")");

    Files.writeString(Paths.get("/tmp/yahoo_mock_dividend"), "1");
    try {
      PriceDownloader.Result after = PriceDownloader.download("MSFT", back);
      check(after.full(), "after a dividend changes past adjusted prices, the full history is downloaded again");
    } finally { Files.deleteIfExists(Paths.get("/tmp/yahoo_mock_dividend")); }

    PriceSeries old = PriceCsv.parse("Date,Close\n" + String.join("\n", java.util.stream.IntStream.range(0, 80).mapToObj(i -> java.time.LocalDate.of(2026, 1, 1).plusDays(i) + "," + (100 + i)).toList()), "MSFT");
    PriceDownloader.Result upg = PriceDownloader.download("MSFT", old);
    check(upg.full() && upg.series().hasOhlc(), "a closes-only file from version 1.0 is upgraded to full history");
    System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
    System.exit(failed);
  }
}

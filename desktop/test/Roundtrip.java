import com.signaldesk.ui.*;
import com.signaldesk.model.*;
import com.signaldesk.store.*;
import java.nio.file.*;
import javax.swing.*;
/** Every section opens; edits save and reload; a version 1 file migrates and is kept as a backup. */
public class Roundtrip {
  public static void main(String[] a) throws Exception {
    StudioStore store = new StudioStore(AppPaths.studioFile()); PriceStore prices = new PriceStore(AppPaths.pricesDir());
    // a version 1 file with budget data and holdings
    Files.createDirectories(store.file().getParent());
    Files.writeString(store.file(), "{\"v\":1,\"budget\":{\"income\":[]},\"bills\":[],\"holdings\":[{\"sym\":\"vti\",\"cls\":\"US stocks\"},{\"sym\":\"\",\"cls\":\"Cash\"},{\"sym\":\"MSFT\",\"cls\":\"US stocks\"}],\"backlog\":[{\"id\":\"x\",\"title\":\"Old item\",\"status\":\"next\"}],\"ui\":{\"theme\":\"dark\"}}");
    Studio v1 = store.load(); Path keep = store.keepV1Copy();
    System.out.println("v1: fromV1=" + v1.fromV1 + " watchlist=" + v1.watchlist + " backlog=" + v1.backlog.size() + " theme=" + v1.theme + " kept=" + Files.exists(keep));
    Studio s = Studio.fresh();
    SwingUtilities.invokeAndWait(() -> Theme.install(false));
    MainFrame[] f = new MainFrame[1];
    SwingUtilities.invokeAndWait(() -> { f[0] = new MainFrame(s, store, prices, null); f[0].setVisible(true); });
    SwingUtilities.invokeAndWait(() -> {
      for (String sec : new String[]{"markets","research","picks","practice","learn","backlog","data"}) f[0].navigate(sec);
      f[0].learn("lesson:risk"); f[0].learn("term:beta"); f[0].trade("AAPL");
      s.practice.started = "2026-11-20"; s.practice.trades.add(new Practice.Trade("2026-11-20", "AAPL", true, 10, 230));
      s.practice.trades.add(new Practice.Trade("2026-11-24", "AAPL", false, 4, 240));
      s.lessons.put("stocks", 1.0); s.watchlist.add("NVDA"); s.backlog.get(0).status = BacklogItem.Status.DOING;
      f[0].changed(); f[0].saveNow();
    });
    Studio back = store.load();
    var pos = back.practice.positions().get("AAPL");
    System.out.println("saved=" + store.exists() + " v=" + com.signaldesk.json.Json.obj(com.signaldesk.json.Json.parse(Files.readString(store.file()))).get("v")
      + " watchlist=" + back.watchlist + " trades=" + back.practice.trades.size() + " AAPL shares=" + pos.shares + " avg=" + pos.avgCost()
      + " cash=" + back.practice.cash() + " realized=" + back.practice.realized() + " lessonDone=" + back.lessonDone("stocks") + " backlog0=" + back.backlog.get(0).status);
    System.exit(0);
  }
}

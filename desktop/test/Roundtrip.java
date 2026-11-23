import com.signaldesk.ui.*;
import com.signaldesk.model.*;
import com.signaldesk.store.*;
import javax.swing.*;
public class Roundtrip {
  public static void main(String[] a) throws Exception {
    StudioStore store = new StudioStore(AppPaths.studioFile()); PriceStore prices = new PriceStore(AppPaths.pricesDir());
    Studio s = ExampleData.build();
    SwingUtilities.invokeAndWait(() -> Theme.install(false));
    MainFrame[] f = new MainFrame[1];
    SwingUtilities.invokeAndWait(() -> { f[0] = new MainFrame(s, store, prices, null); f[0].setVisible(true); });
    SwingUtilities.invokeAndWait(() -> {
      for (String sec : new String[]{"overview","budget","bills","debt","networth","retirement","investments","signals","backlog","data"}) f[0].navigate(sec);
      s.debts.get(0).balance = 5000; s.backlog.get(0).status = BacklogItem.Status.DOING;
      s.tx.add(new Transaction("2026-11-23","Test coffee", s.cats.get(2).id, 4.5));
      f[0].changed(); f[0].saveNow();
    });
    Studio back = store.load();
    System.out.println("saved=" + store.exists() + " example=" + back.example + " debt0=" + back.debts.get(0).balance + " backlog0=" + back.backlog.get(0).status + " tx=" + back.tx.size() + " backups=" + java.nio.file.Files.list(AppPaths.backupsDir()).count());
    System.exit(0);
  }
}

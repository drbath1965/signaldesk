import com.signaldesk.ui.*;
import com.signaldesk.model.*;
import com.signaldesk.store.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
/** Screenshots of every section. Downloads from the mock first, then runs offline so nothing moves while shooting. */
public class Shots {
  static Component find(Container c, Class<?> k) { for (Component x : c.getComponents()) { if (k.isInstance(x)) return x; if (x instanceof Container cc) { Component r = find(cc, k); if (r != null) return r; } } return null; }
  public static void main(String[] a) throws Exception {
    String out = a[0]; boolean dark = a.length > 1 && a[1].equals("dark");
    StudioStore store = new StudioStore(AppPaths.studioFile()); PriceStore prices = new PriceStore(AppPaths.pricesDir());
    Studio s = Studio.fresh(); s.theme = dark ? "dark" : "light";
    Set<String> need = new LinkedHashSet<>(s.watchlist);
    for (String[] ix : Universe.INDEXES) need.add(ix[0]);
    need.add("^GSPC"); need.addAll(Universe.ALL.get(0).symbols());
    for (String sym : need) {
      String c = PriceStore.clean(sym);
      if (prices.get(c) == null) { PriceDownloader.Result r = PriceDownloader.download(c, null); prices.put(r.series(), r.name(), r.fetched(), r.type()); }
    }
    // a practice portfolio a few weeks old
    var spx = prices.get("^GSPC"); int n = spx.size();
    String d0 = spx.dates().get(n - 30), d1 = spx.dates().get(n - 18), d2 = spx.dates().get(n - 6);
    s.practice.started = d0;
    s.practice.trades.add(new Practice.Trade(d0, "VOO", true, 60, prices.get("VOO").close()[prices.get("VOO").dates().indexOf(d0)]));
    s.practice.trades.add(new Practice.Trade(d0, "AAPL", true, 80, prices.get("AAPL").close()[prices.get("AAPL").dates().indexOf(d0)]));
    s.practice.trades.add(new Practice.Trade(d1, "BTC-USD", true, 0.15, prices.get("BTC-USD").close()[prices.get("BTC-USD").dates().indexOf(d1)]));
    s.practice.trades.add(new Practice.Trade(d2, "AAPL", false, 30, prices.get("AAPL").close()[prices.get("AAPL").dates().indexOf(d2)]));
    s.lessons.put("stocks", 1.0); s.lessons.put("funds", 2 / 3.0); s.lessons.put("crypto", 1 / 3.0);
    System.setProperty("signaldesk.offline", "true");
    SwingUtilities.invokeAndWait(() -> { Theme.install(dark); });
    MainFrame[] f = new MainFrame[1];
    SwingUtilities.invokeAndWait(() -> { f[0] = new MainFrame(s, store, prices, null); f[0].setSize(1440, 960); f[0].setVisible(true); });
    String[] secs = a.length > 2 ? a[2].split(",") : new String[]{"markets", "research", "picks", "practice", "learn", "lesson", "glossary", "backlog", "data"};
    for (String sec : secs) {
      SwingUtilities.invokeAndWait(() -> {
        switch (sec) {
          case "research" -> f[0].analyze("AAPL");
          case "lesson" -> f[0].learn("lesson:risk");
          case "glossary" -> f[0].learn("term:beta");
          case "learn" -> f[0].learn("lesson:none");
          default -> f[0].navigate(sec);
        }
      });
      Thread.sleep(sec.equals("research") ? 1500 : 600);
      SwingUtilities.invokeAndWait(() -> {
        try {
          JScrollPane sp = (JScrollPane) find(f[0].getContentPane(), JScrollPane.class);
          JComponent view = (JComponent) sp.getViewport().getView();
          int w = sp.getViewport().getWidth();
          for (int i = 0; i < 3; i++) { view.setSize(w, view.getPreferredSize().height); view.validate(); }
          int h = view.getHeight();
          Component side = f[0].getContentPane().getComponent(0);
          BufferedImage img = new BufferedImage(side.getWidth() + w, Math.max(h, side.getHeight()), BufferedImage.TYPE_INT_RGB);
          Graphics2D g = img.createGraphics(); g.setColor(Theme.T.paper); g.fillRect(0, 0, img.getWidth(), img.getHeight());
          side.printAll(g);
          g.translate(side.getWidth(), 0); view.printAll(g); g.dispose();
          ImageIO.write(img, "png", new File(out + "/" + sec + (dark ? "-dark" : "") + ".png"));
        } catch (Exception e) { e.printStackTrace(); }
      });
    }
    System.exit(0);
  }
}

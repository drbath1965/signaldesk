import com.signaldesk.ui.*;
import com.signaldesk.model.*;
import com.signaldesk.store.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
/** Renders Stock signals for MSFT in each chart type with different indicators. */
public class ChartShots {
  static Component find(Container c, Class<?> k) { for (Component x : c.getComponents()) { if (k.isInstance(x)) return x; if (x instanceof Container cc) { Component r = find(cc, k); if (r != null) return r; } } return null; }
  public static void main(String[] a) throws Exception {
    String out = a[0];
    StudioStore store = new StudioStore(AppPaths.studioFile()); PriceStore prices = new PriceStore(AppPaths.pricesDir());
    Studio s = ExampleData.build();
    SwingUtilities.invokeAndWait(() -> Theme.install(a.length > 1 && a[1].equals("dark")));
    MainFrame[] f = new MainFrame[1];
    SwingUtilities.invokeAndWait(() -> { f[0] = new MainFrame(s, store, prices, null); f[0].setSize(1440, 960); f[0].setVisible(true); });
    Thread.sleep(3000);
    Object[][] cases = {
      {"candles-6m", "CANDLE", 126, new String[]{"sma", "bb", "signals", "volume", "macd"}},
      {"ohlc-1m", "OHLC", 21, new String[]{"ema21", "volume", "rsi"}},
      {"line-max", "LINE", 0, new String[]{"sma", "sma200", "signals", "volume", "rsi", "equity"}},
    };
    for (Object[] c : cases) {
      UiPrefs.put("chart.type", c[1]); UiPrefs.put("chart.range", c[2]); UiPrefs.put("chart.indicators", new LinkedHashSet<>(Arrays.asList((String[]) c[3])));
      SwingUtilities.invokeAndWait(() -> f[0].analyze("MSFT"));
      Thread.sleep(3500);
      String name = (String) c[0] + (a.length > 1 ? "-" + a[1] : "");
      SwingUtilities.invokeAndWait(() -> {
        try {
          JScrollPane sp = (JScrollPane) find(f[0].getContentPane(), JScrollPane.class);
          JComponent v = (JComponent) sp.getViewport().getView(); int w = sp.getViewport().getWidth();
          for (int i = 0; i < 3; i++) { v.setSize(w, v.getPreferredSize().height); v.validate(); }
          BufferedImage img = new BufferedImage(w, Math.min(v.getHeight(), 1500), BufferedImage.TYPE_INT_RGB);
          Graphics2D g = img.createGraphics(); g.setColor(Theme.T.paper); g.fillRect(0, 0, img.getWidth(), img.getHeight()); v.printAll(g); g.dispose();
          ImageIO.write(img, "png", new File(out + "/" + name + ".png"));
        } catch (Exception e) { e.printStackTrace(); }
      });
    }
    System.exit(0);
  }
}

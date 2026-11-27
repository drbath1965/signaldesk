import com.signaldesk.ui.*;
import com.signaldesk.model.*;
import com.signaldesk.store.*;
import com.signaldesk.signals.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;
/** End-to-end: watchlist and indexes refresh on launch, then type a company name, pick a suggestion, and the chart appears. */
public class AutoPrices {
  static Component find(Container c, java.util.function.Predicate<Component> p) { for (Component x : c.getComponents()) { if (p.test(x)) return x; if (x instanceof Container cc) { Component r = find(cc, p); if (r != null) return r; } } return null; }
  public static void main(String[] a) throws Exception {
    // parser checks
    System.out.println("BRK.B -> " + PriceDownloader.yahooSymbol("BRK.B") + ", RY.TO -> " + PriceDownloader.yahooSymbol("RY.TO"));
    try { PriceDownloader.download("ZZZZ"); System.out.println("FAIL: unknown symbol accepted"); } catch (PriceDownloader.NotFound nf) { System.out.println("unknown symbol -> " + nf.getMessage()); }
    PriceDownloader.Result r = PriceDownloader.download("BRK.B");
    System.out.println("BRK.B rows=" + r.series().size() + " last=" + r.series().lastDate() + " " + r.series().last() + " name=" + r.name());
    System.out.println("search 'micro' -> " + PriceDownloader.search("micro"));
    // UI flow
    StudioStore store = new StudioStore(AppPaths.studioFile()); PriceStore prices = new PriceStore(AppPaths.pricesDir());
    Studio s = Studio.fresh();
    SwingUtilities.invokeAndWait(() -> Theme.install(false));
    MainFrame[] f = new MainFrame[1];
    SwingUtilities.invokeAndWait(() -> { f[0] = new MainFrame(s, store, prices, null); f[0].setSize(1440, 960); f[0].setVisible(true); });
    Thread.sleep(4000);
    System.out.println("after launch: fresh AAPL=" + prices.isFresh("AAPL") + " VFIAX=" + prices.isFresh("VFIAX") + " BTC-USD=" + prices.isFresh("BTC-USD") + " ^GSPC=" + prices.isFresh("^GSPC")
      + " types: " + prices.type("VOO") + "/" + prices.type("VFIAX") + "/" + prices.type("BTC-USD") + "/" + prices.type("^GSPC") + "/" + prices.type("AAPL"));
    SwingUtilities.invokeAndWait(() -> f[0].navigate("research"));
    Thread.sleep(500);
    JTextField[] fld = new JTextField[1];
    SwingUtilities.invokeAndWait(() -> { fld[0] = (JTextField) find(f[0].getContentPane(), c -> c instanceof JTextField t && t.getFont().getSize() == 18); fld[0].requestFocusInWindow(); });
    Thread.sleep(300);
    SwingUtilities.invokeAndWait(() -> fld[0].setText("apple"));
    Thread.sleep(1500);
    JPopupMenu[] pop = new JPopupMenu[1];
    SwingUtilities.invokeAndWait(() -> { for (Window w : Window.getWindows()) { Component p = w instanceof RootPaneContainer rp ? find(rp.getContentPane(), c -> c instanceof JPopupMenu jp && jp.isVisible()) : null; if (p != null) pop[0] = (JPopupMenu) p; }
      if (pop[0] == null) { Component p = find(f[0].getLayeredPane(), c -> c instanceof JPopupMenu jp && jp.isVisible()); if (p != null) pop[0] = (JPopupMenu) p; } });
    System.out.println("suggestions shown: " + (pop[0] != null ? pop[0].getComponentCount() : 0));
    shot(f[0], "/tmp/claude-0/-home-claude/0a01bbaf-0bc6-5107-b6f9-f8e7e0b79656/scratchpad/auto-suggest.png", true);
    if (pop[0] != null) SwingUtilities.invokeAndWait(() -> ((JMenuItem) pop[0].getComponent(0)).doClick());
    Thread.sleep(2500);
    System.out.println("AAPL saved=" + (prices.get("AAPL") != null) + " name=" + prices.name("AAPL"));
    shot(f[0], "/tmp/claude-0/-home-claude/0a01bbaf-0bc6-5107-b6f9-f8e7e0b79656/scratchpad/auto-aapl.png", false);
    System.exit(0);
  }
  static void shot(JFrame f, String path, boolean window) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        Rectangle b = f.getBounds(); BufferedImage img;
        if (window) { img = new Robot().createScreenCapture(new Rectangle(f.getLocationOnScreen(), f.getSize())); }
        else {
          JScrollPane sp = (JScrollPane) find(f.getContentPane(), c -> c instanceof JScrollPane);
          JComponent v = (JComponent) sp.getViewport().getView(); int w = sp.getViewport().getWidth();
          for (int i = 0; i < 3; i++) { v.setSize(w, v.getPreferredSize().height); v.validate(); }
          img = new BufferedImage(w, v.getHeight(), BufferedImage.TYPE_INT_RGB); Graphics2D g = img.createGraphics(); v.printAll(g); g.dispose();
        }
        ImageIO.write(img, "png", new File(path));
      } catch (Exception e) { e.printStackTrace(); }
    });
  }
}

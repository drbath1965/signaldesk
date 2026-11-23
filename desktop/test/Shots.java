import com.signaldesk.ui.*;
import com.signaldesk.model.*;
import com.signaldesk.store.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;
public class Shots {
  static Component find(Container c, Class<?> k) { for (Component x : c.getComponents()) { if (k.isInstance(x)) return x; if (x instanceof Container cc) { Component r = find(cc, k); if (r != null) return r; } } return null; }
  public static void main(String[] a) throws Exception {
    String out = a[0]; boolean dark = a.length > 1 && a[1].equals("dark");
    Studio s = ExampleData.build(); s.theme = dark ? "dark" : "light";
    StudioStore store = new StudioStore(AppPaths.studioFile()); PriceStore prices = new PriceStore(AppPaths.pricesDir());
    SwingUtilities.invokeAndWait(() -> { Theme.install(dark); });
    MainFrame[] f = new MainFrame[1];
    SwingUtilities.invokeAndWait(() -> { f[0] = new MainFrame(s, store, prices, null); f[0].setSize(1440, 960); f[0].setVisible(true); });
    String[] secs = a.length > 2 ? a[2].split(",") : new String[]{"overview","budget","bills","debt","networth","retirement","investments","signals","backlog","data"};
    for (String sec : secs) {
      SwingUtilities.invokeAndWait(() -> f[0].navigate(sec));
      Thread.sleep(500);
      SwingUtilities.invokeAndWait(() -> {
        try {
          JScrollPane sp = (JScrollPane) find(f[0].getContentPane(), JScrollPane.class);
          JComponent view = (JComponent) sp.getViewport().getView();
          int w = sp.getViewport().getWidth(); int h = view.getPreferredSize().height;
          view.setSize(w, h); view.validate();
          for (int i=0;i<3;i++){ view.setSize(w, view.getPreferredSize().height); view.validate(); }
          h = view.getHeight();
          Component side = f[0].getContentPane().getComponent(0);
          BufferedImage img = new BufferedImage(side.getWidth() + w, Math.max(h, side.getHeight()), BufferedImage.TYPE_INT_RGB);
          Graphics2D g = img.createGraphics(); g.setColor(Theme.T.paper); g.fillRect(0,0,img.getWidth(),img.getHeight());
          side.printAll(g);
          g.translate(side.getWidth(), 0); view.printAll(g); g.dispose();
          ImageIO.write(img, "png", new File(out + "/" + sec + (dark?"-dark":"") + ".png"));
        } catch (Exception e) { e.printStackTrace(); }
      });
    }
    System.exit(0);
  }
}

package com.signaldesk.ui;

import java.awt.*;
import java.awt.geom.*;

/** Line icons for the sidebar, drawn on a 24-unit grid. */
public final class Icons {
    private Icons() {}
    public static void paint(Graphics2D g0, String key, int x, int y, int size, Color c) {
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(x, y); g.scale(size / 24.0, size / 24.0);
        g.setColor(c); g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (key) {
            case "markets" -> { path(g, 3, 17, 8, 12, 12, 15, 20, 7); path(g, 15, 7, 20, 7, 20, 12); path(g, 3, 21, 21, 21); }
            case "research" -> { g.draw(new Ellipse2D.Double(4, 4, 12, 12)); path(g, 14.5, 14.5, 20.5, 20.5); path(g, 7, 12, 9, 9.5, 11, 11, 13, 8); }
            case "picks" -> { path(g, 4, 6, 20, 6); path(g, 4, 12, 16, 12); path(g, 4, 18, 11, 18); }
            case "practice" -> { g.draw(new RoundRectangle2D.Double(3, 7, 18, 13, 3, 3)); path(g, 8, 7, 8, 4.5, 16, 4.5, 16, 7); path(g, 3, 13, 21, 13); }
            case "learn" -> { path(g, 2, 9, 12, 4, 22, 9, 12, 14, 2, 9); path(g, 6, 11, 6, 17, 12, 20, 18, 17, 18, 11); }
            case "backlog" -> { g.draw(new RoundRectangle2D.Double(3, 4, 5, 16, 2.5, 2.5)); g.draw(new RoundRectangle2D.Double(10, 4, 5, 10, 2.5, 2.5)); g.draw(new RoundRectangle2D.Double(17, 4, 4, 13, 2.5, 2.5)); }
            default -> g.draw(new Ellipse2D.Double(6, 6, 12, 12));
        }
        g.dispose();
    }
    private static void path(Graphics2D g, double... pts) {
        Path2D p = new Path2D.Double(); p.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) p.lineTo(pts[i], pts[i + 1]);
        g.draw(p);
    }
}

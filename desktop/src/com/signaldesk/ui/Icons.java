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
            case "overview" -> { path(g, 3, 12, 12, 4, 21, 12); path(g, 5, 10, 5, 20, 19, 20, 19, 10); }
            case "budget" -> { g.draw(new RoundRectangle2D.Double(3, 5, 18, 14, 4, 4)); path(g, 3, 10, 21, 10); path(g, 8, 15, 11, 15); }
            case "bills" -> { g.draw(new RoundRectangle2D.Double(4, 5, 16, 15, 4, 4)); path(g, 4, 10, 20, 10); path(g, 9, 3, 9, 7); path(g, 15, 3, 15, 7); }
            case "debt" -> { path(g, 4, 18, 10, 12, 14, 16, 20, 8); path(g, 14, 8, 20, 8, 20, 14); }
            case "networth" -> { path(g, 12, 3, 12, 21); Path2D p = new Path2D.Double(); p.moveTo(16, 7); p.lineTo(9, 7); p.curveTo(5.5, 7, 5.5, 12, 9, 12); p.lineTo(15, 12); p.curveTo(18.5, 12, 18.5, 17, 15, 17); p.lineTo(7, 17); g.draw(p); }
            case "retirement" -> { g.draw(new Ellipse2D.Double(4, 4, 16, 16)); path(g, 12, 7.5, 12, 12, 15, 14); }
            case "investments" -> { g.draw(new Arc2D.Double(3, 3, 18, 18, 0, 270, Arc2D.PIE)); g.draw(new Arc2D.Double(4.5, 2, 18, 18, 90, -90, Arc2D.PIE)); }
            case "signals" -> { path(g, 3, 16, 8, 11, 12, 14, 20, 6); path(g, 3, 21, 21, 21); }
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

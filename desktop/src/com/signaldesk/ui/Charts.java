package com.signaldesk.ui;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import javax.swing.*;

/** Line, stacked-area and donut charts drawn to scale, themed from the current palette. */
public final class Charts {
    private Charts() {}

    public static double[] niceTicks(double lo, double hi, int count) {
        if (hi <= lo) hi = lo + 1;
        double raw = (hi - lo) / count, mag = Math.pow(10, Math.floor(Math.log10(raw))), r = raw / mag;
        double step = (r < 1.5 ? 1 : r < 3 ? 2 : r < 7 ? 5 : 10) * mag;
        List<Double> out = new ArrayList<>();
        for (double v = Math.ceil(lo / step) * step; v <= hi + 1e-9; v += step) out.add(Math.round(v * 1e10) / 1e10);
        return out.stream().mapToDouble(Double::doubleValue).toArray();
    }

    public static final class Series {
        final double[] v; final Color color; final float width; final boolean dashed, area, endDot;
        public Series(double[] v, Color color, float width, boolean dashed, boolean area, boolean endDot) { this.v = v; this.color = color; this.width = width; this.dashed = dashed; this.area = area; this.endDot = endDot; }
    }

    /** Line chart with optional stacked layers. NaN breaks a line. */
    public static class Line extends JComponent {
        private final List<Series> series = new ArrayList<>();
        private double[][] layers; private Color[] layerColors;
        private final String[] labels; private final DoubleFunction<String> yfmt;
        private boolean zero; private int mark = -1, xTicks = 6;
        public Line(String[] labels, DoubleFunction<String> yfmt, int height) {
            this.labels = labels; this.yfmt = yfmt; setPreferredSize(new Dimension(400, height)); setMinimumSize(new Dimension(100, height));
        }
        public Line add(Series s) { series.add(s); return this; }
        public Line stacked(double[][] layers, Color[] colors) { this.layers = layers; this.layerColors = colors; return this; }
        public Line zero() { zero = true; return this; }
        public Line mark(int i) { mark = i; return this; }
        public Line xTicks(int n) { xTicks = n; return this; }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.aa(g); Theme t = Theme.T;
            g2.setFont(Theme.mono(11f)); FontMetrics fm = g2.getFontMetrics();
            int w = getWidth(), h = getHeight(), padL = 4, padR = 62, padT = 8, padB = 22;
            double iw = w - padL - padR, ih = h - padT - padB; int n = labels.length;
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            double[] top = null;
            if (layers != null) { top = new double[n]; for (double[] L : layers) for (int i = 0; i < n; i++) top[i] += L[i]; for (double v : top) { lo = Math.min(lo, v); hi = Math.max(hi, v); } }
            for (Series s : series) for (double v : s.v) if (Double.isFinite(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            if (!Double.isFinite(lo)) { lo = 0; hi = 1; }
            if (layers != null || zero) lo = Math.min(0, lo);
            double pad = (hi - lo) * .06; if (pad == 0) pad = 1;
            if (layers == null && !zero) lo -= pad; hi += pad;
            final double L0 = lo, H0 = hi;
            DoubleUnaryOp X = i -> padL + (n <= 1 ? 0 : i / (n - 1) * iw);
            DoubleUnaryOp Y = v -> padT + ih - (v - L0) / (H0 - L0) * ih;
            for (double tk : niceTicks(lo, hi, 4)) {
                double y = Y.f(tk); if (y < padT - 1 || y > padT + ih + 1) continue;
                g2.setColor(t.soft); g2.draw(new Line2D.Double(padL, y, padL + iw, y));
                g2.setColor(t.muted); g2.drawString(yfmt.apply(tk), (float) (padL + iw + 8), (float) (y + fm.getAscent() / 2.0 - 1));
            }
            int step = Math.max(1, (int) Math.ceil(n / (double) xTicks));
            g2.setColor(t.muted);
            for (int i = 0; i < n; i += step) {
                String s = labels[i]; double x = X.f(i); int sw = fm.stringWidth(s);
                double tx = i == 0 ? x : Math.min(x - sw / 2.0, padL + iw - sw);
                g2.drawString(s, (float) tx, h - 6);
            }
            if (layers != null) {
                double[] base = new double[n];
                for (int k = 0; k < layers.length; k++) {
                    Path2D p = new Path2D.Double(); double[] tp = new double[n];
                    for (int i = 0; i < n; i++) { tp[i] = base[i] + layers[k][i]; if (i == 0) p.moveTo(X.f(i), Y.f(tp[i])); else p.lineTo(X.f(i), Y.f(tp[i])); }
                    for (int i = n - 1; i >= 0; i--) p.lineTo(X.f(i), Y.f(base[i]));
                    p.closePath(); g2.setColor(Theme.alpha(layerColors[k], .85)); g2.fill(p); base = tp;
                }
            }
            for (Series s : series) {
                Path2D p = new Path2D.Double(); boolean on = false; double fx = 0, lx = 0, ly = 0; boolean any = false;
                for (int i = 0; i < s.v.length && i < n; i++) {
                    double v = s.v[i]; if (!Double.isFinite(v)) { on = false; continue; }
                    double x = X.f(i), y = Y.f(v);
                    if (!on) { p.moveTo(x, y); if (!any) fx = x; } else p.lineTo(x, y);
                    on = true; any = true; lx = x; ly = y;
                }
                if (!any) continue;
                if (s.area) { Path2D a = new Path2D.Double(p); a.lineTo(lx, padT + ih); a.lineTo(fx, padT + ih); a.closePath(); g2.setColor(Theme.alpha(s.color, .10)); g2.fill(a); }
                g2.setColor(s.color);
                g2.setStroke(s.dashed ? new BasicStroke(s.width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{5, 4}, 0) : new BasicStroke(s.width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(p);
                if (s.endDot) g2.fill(new Ellipse2D.Double(lx - 3.5, ly - 3.5, 7, 7));
            }
            if (mark >= 0 && mark < n) {
                double x = X.f(mark); g2.setColor(t.muted);
                g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{3, 3}, 0));
                g2.draw(new Line2D.Double(x, padT, x, padT + ih));
            }
            g2.dispose();
        }
    }
    @FunctionalInterface interface DoubleUnaryOp { double f(double v); }

    /** Donut chart. */
    public static JComponent donut(double[] values, Color[] colors, int size) {
        JComponent c = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g); double tot = 0; for (double v : values) tot += Math.max(0, v);
                double r = size / 2.0 - 2, th = 22, cx = getWidth() / 2.0, cy = getHeight() / 2.0;
                if (tot <= 0) { g2.setColor(Theme.T.soft); g2.setStroke(new BasicStroke((float) th)); g2.draw(new Ellipse2D.Double(cx - r + th / 2, cy - r + th / 2, 2 * r - th, 2 * r - th)); g2.dispose(); return; }
                double a = 90;
                for (int i = 0; i < values.length; i++) {
                    if (values[i] <= 0) continue;
                    double ext = values[i] / tot * 360;
                    Area ring = new Area(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, a, -ext, Arc2D.PIE));
                    ring.subtract(new Area(new Ellipse2D.Double(cx - r + th, cy - r + th, 2 * (r - th), 2 * (r - th))));
                    g2.setColor(colors[i]); g2.fill(ring); a -= ext;
                }
                g2.dispose();
            }
        };
        c.setPreferredSize(new Dimension(size, size)); c.setMinimumSize(new Dimension(size, size));
        return c;
    }
}

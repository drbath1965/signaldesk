package com.signaldesk.ui.views;

import com.signaldesk.calc.Dates;
import com.signaldesk.signals.SignalEngine;
import com.signaldesk.ui.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.util.function.IntConsumer;
import javax.swing.JComponent;

/** Price with fast/slow averages and buy/sell markers, RSI underneath, then strategy vs buy-and-hold. */
final class PriceChart extends JComponent {
    private SignalEngine e; private int view = Integer.MAX_VALUE; private Integer hover; private final IntConsumer onHover;
    PriceChart(IntConsumer onHover) {
        this.onHover = onHover; setPreferredSize(new Dimension(600, 520));
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent ev) { pick(ev.getX()); }
            @Override public void mouseDragged(MouseEvent ev) { pick(ev.getX()); }
            @Override public void mouseExited(MouseEvent ev) { hover = null; repaint(); onHover.accept(-1); }
        };
        addMouseListener(m); addMouseMotionListener(m);
    }
    void set(SignalEngine e) { this.e = e; hover = null; repaint(); }
    void range(int days) { view = days; hover = null; repaint(); }
    private int[] ab() { int n = e.data.size(); int a = view == Integer.MAX_VALUE ? e.start : Math.max(e.start, n - view); return new int[]{a, n - 1}; }
    private void pick(int x) {
        if (e == null || e.error != null) return;
        int[] r = ab(); int padR = 62; double plotW = getWidth() - 4 - padR;
        double f = Math.max(0, Math.min(1, (x - 4) / plotW)); hover = (int) Math.round(r[0] + f * (r[1] - r[0]));
        repaint(); onHover.accept(hover);
    }

    @Override protected void paintComponent(Graphics g0) {
        if (e == null || e.error != null) return;
        Graphics2D g = Ui.aa(g0); Theme t = Theme.T;
        double[] c = e.data.close(); int[] r = ab(); int a = r[0], b = r[1];
        int W = getWidth(), H = getHeight(), padL = 4, padR = 62; double plotW = W - padL - padR; int lab = 20;
        double p1t = 6, p1h = H * .56, p2t = H * .56 + lab + 6, p2h = H * .12, p3t = H * .56 + H * .12 + lab * 2 + 12, p3h = H - p3t - 24;
        java.util.function.IntToDoubleFunction X = i -> padL + (b == a ? 0 : (i - a) / (double) (b - a)) * plotW;
        g.setFont(Theme.mono(11f)); FontMetrics fm = g.getFontMetrics();
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int i = a; i <= b; i++) for (double v : new double[]{c[i], e.fast[i], e.slow[i]}) if (!Double.isNaN(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        double pad = (hi - lo) * .08; lo -= pad; hi += pad;
        final double L1 = lo, H1 = hi;
        java.util.function.DoubleUnaryOperator Y1 = v -> p1t + p1h - (v - L1) / (H1 - L1) * p1h;
        // shading while held
        g.setColor(Theme.alpha(t.buy, .08)); Integer run = null;
        for (int i = a; i <= b + 1; i++) {
            boolean h = i <= b && e.held[i];
            if (h && run == null) run = i;
            if (!h && run != null) { g.fill(new Rectangle2D.Double(X.applyAsDouble(run), p1t, X.applyAsDouble(i - 1) - X.applyAsDouble(run) + 1, p1h)); run = null; }
        }
        grid(g, p1t, p1h, lo, hi, Charts.niceTicks(lo, hi, 5), padL, plotW, v -> v >= 1000 ? String.format("%.0f", v) : String.format(v < 10 ? "%.2f" : "%.0f", v));
        line(g, e.slow, a, b, X, Y1, t.slow, 1.6f, false);
        line(g, e.fast, a, b, X, Y1, t.accent, 1.6f, false);
        line(g, c, a, b, X, Y1, t.ink, 1.4f, false);
        for (SignalEngine.Signal s : e.signals) {
            if (s.i() < a || s.i() > b) continue;
            double x = X.applyAsDouble(s.i()), y = Y1.applyAsDouble(c[s.i()]); double o = s.buy() ? 12 : -12, z = 6.5;
            Path2D p = new Path2D.Double();
            if (s.buy()) { p.moveTo(x, y + o - z); p.lineTo(x - z, y + o + z); p.lineTo(x + z, y + o + z); }
            else { p.moveTo(x, y + o + z); p.lineTo(x - z, y + o - z); p.lineTo(x + z, y + o - z); }
            p.closePath(); g.setColor(t.panel); g.setStroke(new BasicStroke(2.5f)); g.draw(p); g.setColor(s.buy() ? t.buy : t.sell); g.fill(p);
        }
        double ly = Y1.applyAsDouble(c[b]);
        g.setColor(t.ink); g.fill(new RoundRectangle2D.Double(padL + plotW + 3, ly - 10, padR - 4, 20, 6, 6));
        g.setColor(t.panel); g.drawString(String.format("%.2f", c[b]), (float) (padL + plotW + 8), (float) (ly + fm.getAscent() / 2.0 - 1));
        // RSI
        title(g, "RSI (" + e.p.rsiN + ")", padL, p2t - lab / 2.0 + 3);
        java.util.function.DoubleUnaryOperator Y2 = v -> p2t + p2h - v / 100 * p2h;
        g.setColor(t.soft); g.fill(new Rectangle2D.Double(padL, Y2.applyAsDouble(e.p.ob), plotW, Y2.applyAsDouble(e.p.os) - Y2.applyAsDouble(e.p.ob)));
        grid(g, p2t, p2h, 0, 100, new double[]{e.p.os, e.p.ob}, padL, plotW, v -> String.valueOf((int) v));
        line(g, e.rsi, a, b, X, Y2, t.accent, 1.3f, false);
        // equity
        title(g, "Growth of $10,000 · strategy vs buy & hold", padL, p3t - lab / 2.0 + 3);
        double elo = Double.MAX_VALUE, ehi = -Double.MAX_VALUE;
        for (int i = a; i <= b; i++) { elo = Math.min(elo, Math.min(e.eq[i], e.bh[i])); ehi = Math.max(ehi, Math.max(e.eq[i], e.bh[i])); }
        double ep = (ehi - elo) * .1; if (ep == 0) ep = .05; elo -= ep; ehi += ep;
        final double L3 = elo, H3 = ehi;
        java.util.function.DoubleUnaryOperator Y3 = v -> p3t + p3h - (v - L3) / (H3 - L3) * p3h;
        grid(g, p3t, p3h, elo, ehi, Charts.niceTicks(elo, ehi, 3), padL, plotW, v -> v * 10 < 100 ? String.format("$%.1fk", v * 10) : String.format("$%.0fk", v * 10));
        line(g, e.bh, a, b, X, Y3, t.muted, 1.3f, true);
        line(g, e.eq, a, b, X, Y3, t.accent, 1.8f, false);
        // date axis
        g.setFont(Theme.mono(11f)); g.setColor(t.muted); int nT = W < 700 ? 3 : 6;
        for (int k = 0; k <= nT; k++) {
            int i = (int) Math.round(a + (b - a) * k / (double) nT); String s = Dates.tick(e.data.dates().get(i).substring(0, 7));
            double x = X.applyAsDouble(i), sw = fm.stringWidth(s);
            g.drawString(s, (float) (k == 0 ? x : k == nT ? x - sw : x - sw / 2), H - 6);
        }
        if (hover != null && hover >= a && hover <= b) {
            double x = Math.round(X.applyAsDouble(hover)) + .5;
            g.setColor(t.muted); g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{3, 3}, 0));
            g.draw(new Line2D.Double(x, p1t, x, p3t + p3h));
            g.setColor(t.ink); double hy = Y1.applyAsDouble(c[hover]); g.fill(new Ellipse2D.Double(x - 4, hy - 4, 8, 8));
        }
        g.dispose();
    }
    private void title(Graphics2D g, String s, double x, double y) { g.setFont(Theme.semibold(11f)); g.setColor(Theme.T.muted); g.drawString(s, (float) x, (float) y); g.setFont(Theme.mono(11f)); }
    private void grid(Graphics2D g, double top, double h, double lo, double hi, double[] ticks, int padL, double plotW, java.util.function.DoubleFunction<String> fmt) {
        FontMetrics fm = g.getFontMetrics();
        for (double v : ticks) {
            double y = top + h - (v - lo) / (hi - lo) * h; if (y < top - 1 || y > top + h + 1) continue;
            g.setColor(Theme.T.soft); g.setStroke(new BasicStroke(1)); g.draw(new Line2D.Double(padL, y, padL + plotW, y));
            g.setColor(Theme.T.muted); g.drawString(fmt.apply(v), (float) (padL + plotW + 8), (float) (y + fm.getAscent() / 2.0 - 1));
        }
    }
    private void line(Graphics2D g, double[] arr, int a, int b, java.util.function.IntToDoubleFunction X, java.util.function.DoubleUnaryOperator Y, Color col, float w, boolean dashed) {
        Path2D p = new Path2D.Double(); boolean on = false;
        for (int i = a; i <= b; i++) { double v = arr[i]; if (Double.isNaN(v)) { on = false; continue; } double x = X.applyAsDouble(i), y = Y.applyAsDouble(v); if (on) p.lineTo(x, y); else p.moveTo(x, y); on = true; }
        g.setColor(col);
        g.setStroke(dashed ? new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{4, 3}, 0) : new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(p);
    }
}

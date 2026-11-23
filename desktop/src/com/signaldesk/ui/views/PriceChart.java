package com.signaldesk.ui.views;

import com.signaldesk.calc.Dates;
import com.signaldesk.calc.Fmt;
import com.signaldesk.model.SignalParams;
import com.signaldesk.signals.Indicators;
import com.signaldesk.signals.PriceSeries;
import com.signaldesk.signals.SignalEngine;
import com.signaldesk.ui.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.JComponent;

/**
 * Price as a line, candlesticks or OHLC bars, with optional overlays (strategy SMAs, EMA 21, SMA 200,
 * Bollinger Bands, buy/sell markers) and panels below (volume, RSI, MACD, strategy vs buy-and-hold).
 * Indicators are computed over the full saved history; the backtest covers the most recent years, offset by off.
 */
final class PriceChart extends JComponent {
    enum Type { LINE, CANDLE, OHLC }
    static final String SMA = "sma", EMA21 = "ema21", SMA200 = "sma200", BB = "bb", SIGNALS = "signals";
    static final String VOLUME = "volume", RSI = "rsi", MACD = "macd", EQUITY = "equity";
    static final Set<String> DEFAULTS = new LinkedHashSet<>(List.of(SMA, SIGNALS, VOLUME, RSI, EQUITY));

    private PriceSeries d; private SignalEngine e; private int off; private SignalParams p;
    private Type type = Type.CANDLE; private Set<String> on = new LinkedHashSet<>(DEFAULTS); private int view = 252;
    private Integer hover; private final IntConsumer onHover;
    private double[] smaF, smaS, ema21, sma200, bbM, bbU, bbL, rsi, macd, macdSig, macdHist;

    private static final int MAIN_H = 330, AXIS_H = 22, LABEL_H = 22, LEGEND_H = 20, PAD_L = 4, PAD_R = 66;
    private static final Map<String, Integer> PANEL_H = Map.of(VOLUME, 64, RSI, 70, MACD, 84, EQUITY, 104);

    PriceChart(IntConsumer onHover) {
        this.onHover = onHover;
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent ev) { pick(ev.getX()); }
            @Override public void mouseDragged(MouseEvent ev) { pick(ev.getX()); }
            @Override public void mouseExited(MouseEvent ev) { hover = null; repaint(); onHover.accept(-1); }
        };
        addMouseListener(m); addMouseMotionListener(m);
        resize();
    }

    // ------------------------------------------------------------------ state
    void set(PriceSeries d, SignalEngine e, int off, SignalParams p) {
        this.d = d; this.e = e != null && e.error == null ? e : null; this.off = off; this.p = p; hover = null;
        if (d != null) {
            double[] c = d.close();
            smaF = Indicators.sma(c, p.fast); smaS = Indicators.sma(c, p.slow); ema21 = Indicators.ema(c, 21); sma200 = Indicators.sma(c, 200);
            double[][] bb = Indicators.bollinger(c, 20, 2); bbM = bb[0]; bbU = bb[1]; bbL = bb[2];
            rsi = Indicators.rsi(c, p.rsiN);
            double[][] m = Indicators.macd(c); macd = m[0]; macdSig = m[1]; macdHist = m[2];
        }
        resize(); repaint();
    }
    void type(Type t) { type = t; repaint(); }
    Type type() { return type; }
    void indicators(Set<String> s) { on = new LinkedHashSet<>(s); resize(); repaint(); }
    void range(int days) { view = days; hover = null; repaint(); }
    boolean has(String k) { return on.contains(k); }
    double[] series(String k) { return switch (k) { case EMA21 -> ema21; case SMA200 -> sma200; case RSI -> rsi; default -> null; }; }
    double[][] bands() { return new double[][]{bbM, bbU, bbL}; }
    double[][] macdLines() { return new double[][]{macd, macdSig, macdHist}; }
    double[] smaFast() { return smaF; }
    double[] smaSlow() { return smaS; }

    private List<String> panels() {
        List<String> out = new ArrayList<>();
        if (on.contains(VOLUME) && d != null && d.hasVolume()) out.add(VOLUME);
        if (on.contains(RSI)) out.add(RSI);
        if (on.contains(MACD)) out.add(MACD);
        if (on.contains(EQUITY) && e != null) out.add(EQUITY);
        return out;
    }
    private void resize() {
        int h = LEGEND_H + MAIN_H + AXIS_H;
        for (String k : panels()) h += LABEL_H + PANEL_H.get(k);
        setPreferredSize(new Dimension(600, h)); setMinimumSize(new Dimension(200, h));
        revalidate();
    }
    private int[] ab() { int n = d.size(); int a = view == Integer.MAX_VALUE ? 0 : Math.max(0, n - view); return new int[]{a, n - 1}; }
    private void pick(int x) {
        if (d == null) return;
        int[] r = ab(); double plotW = getWidth() - PAD_L - PAD_R, bw = plotW / (r[1] - r[0] + 1);
        int i = (int) Math.floor((x - PAD_L) / bw) + r[0];
        hover = Math.max(r[0], Math.min(r[1], i)); repaint(); onHover.accept(hover);
    }

    // ------------------------------------------------------------------ painting
    @Override protected void paintComponent(Graphics g0) {
        if (d == null) return;
        Graphics2D g = Ui.aa(g0); Theme t = Theme.T;
        int[] r = ab(); int a = r[0], b = r[1], n = b - a + 1;
        int W = getWidth(); double plotW = W - PAD_L - PAD_R, bw = plotW / n;
        java.util.function.IntToDoubleFunction X = i -> PAD_L + (i - a + 0.5) * bw;
        boolean bars = type != Type.LINE && d.hasOhlc();
        g.setFont(Theme.mono(11f));

        // legend
        List<Object[]> leg = new ArrayList<>();
        leg.add(new Object[]{bars ? (type == Type.CANDLE ? "Candles" : "OHLC bars") : "Close", t.ink});
        if (on.contains(SMA)) { leg.add(new Object[]{"SMA " + p.fast, t.accent}); leg.add(new Object[]{"SMA " + p.slow, t.slow}); }
        if (on.contains(EMA21)) leg.add(new Object[]{"EMA 21", t.series[2]});
        if (on.contains(SMA200)) leg.add(new Object[]{"SMA 200", t.series[3]});
        if (on.contains(BB)) leg.add(new Object[]{"Bollinger 20, 2σ", t.series[5]});
        if (on.contains(SIGNALS) && e != null) { leg.add(new Object[]{"Buy", t.buy}); leg.add(new Object[]{"Sell", t.sell}); }
        int lx = PAD_L; g.setFont(Theme.body(12f)); FontMetrics lf = g.getFontMetrics();
        for (Object[] o : leg) { g.setColor((Color) o[1]); g.fillRoundRect(lx, 6, 12, 4, 2, 2); g.setColor(t.muted); g.drawString((String) o[0], lx + 17, 12); lx += 17 + lf.stringWidth((String) o[0]) + 16; }
        if (type != Type.LINE && !d.hasOhlc()) { g.setColor(t.warn); String s = "Candles need open, high and low prices; this file has closes only"; g.drawString(s, W - PAD_R - lf.stringWidth(s), 12); }
        g.setFont(Theme.mono(11f));

        // main price panel
        double top = LEGEND_H + 4, h = MAIN_H - 8;
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int i = a; i <= b; i++) {
            lo = Math.min(lo, bars ? d.adjLow(i) : d.close()[i]); hi = Math.max(hi, bars ? d.adjHigh(i) : d.close()[i]);
            for (double[] s : overlays()) if (!Double.isNaN(s[i])) { lo = Math.min(lo, s[i]); hi = Math.max(hi, s[i]); }
        }
        double pad = (hi - lo) * .06; if (pad == 0) pad = 1; lo -= pad; hi += pad;
        final double L = lo, H = hi;
        java.util.function.DoubleUnaryOperator Y = v -> top + h - (v - L) / (H - L) * h;
        grid(g, top, h, lo, hi, niceTicks(lo, hi, 5), plotW, v -> Math.abs(v) >= 1000 ? String.format("%.0f", v) : String.format(v < 10 ? "%.2f" : "%.1f", v));
        if (on.contains(SIGNALS) && e != null) {   // shade days the strategy held the stock
            g.setColor(Theme.alpha(t.buy, .07)); Integer run = null;
            for (int i = a; i <= b + 1; i++) {
                int k = i - off; boolean held = i <= b && k >= 0 && k < e.held.length && e.held[k];
                if (held && run == null) run = i;
                if (!held && run != null) { g.fill(new Rectangle2D.Double(X.applyAsDouble(run) - bw / 2, top, (i - run) * bw, h)); run = null; }
            }
        }
        if (on.contains(BB)) {
            Path2D band = new Path2D.Double(); boolean st = false;
            for (int i = a; i <= b; i++) if (!Double.isNaN(bbU[i])) { double x = X.applyAsDouble(i), y = Y.applyAsDouble(bbU[i]); if (!st) band.moveTo(x, y); else band.lineTo(x, y); st = true; }
            for (int i = b; i >= a; i--) if (!Double.isNaN(bbL[i])) band.lineTo(X.applyAsDouble(i), Y.applyAsDouble(bbL[i]));
            if (st) { band.closePath(); g.setColor(Theme.alpha(t.series[5], .10)); g.fill(band); }
            line(g, bbU, a, b, X, Y, Theme.alpha(t.series[5], .8), 1f, false);
            line(g, bbL, a, b, X, Y, Theme.alpha(t.series[5], .8), 1f, false);
            line(g, bbM, a, b, X, Y, Theme.alpha(t.series[5], .7), 1f, true);
        }
        if (on.contains(SMA200)) line(g, sma200, a, b, X, Y, t.series[3], 1.6f, false);
        if (on.contains(EMA21)) line(g, ema21, a, b, X, Y, t.series[2], 1.5f, false);
        if (on.contains(SMA)) { line(g, smaS, a, b, X, Y, t.slow, 1.6f, false); line(g, smaF, a, b, X, Y, t.accent, 1.6f, false); }
        if (bars) drawBars(g, a, b, X, Y, bw); else line(g, d.close(), a, b, X, Y, t.ink, 1.4f, false);
        if (on.contains(SIGNALS) && e != null) for (SignalEngine.Signal s : e.signals) {
            int i = s.i() + off; if (i < a || i > b) continue;
            double x = X.applyAsDouble(i), z = Math.max(4.5, Math.min(7, bw * 0.8));
            double y = s.buy() ? Y.applyAsDouble(bars ? d.adjLow(i) : d.close()[i]) + 6 + z : Y.applyAsDouble(bars ? d.adjHigh(i) : d.close()[i]) - 6 - z;
            Path2D tri = new Path2D.Double();
            if (s.buy()) { tri.moveTo(x, y - z); tri.lineTo(x - z, y + z); tri.lineTo(x + z, y + z); } else { tri.moveTo(x, y + z); tri.lineTo(x - z, y - z); tri.lineTo(x + z, y - z); }
            tri.closePath(); g.setColor(t.panel); g.setStroke(new BasicStroke(2.5f)); g.draw(tri); g.setColor(s.buy() ? t.buy : t.sell); g.fill(tri);
        }
        double last = d.close()[b], ly = Y.applyAsDouble(last); FontMetrics fm = g.getFontMetrics();
        g.setColor(t.ink); g.fill(new RoundRectangle2D.Double(PAD_L + plotW + 3, ly - 10, PAD_R - 4, 20, 6, 6));
        g.setColor(t.panel); g.drawString(String.format(last >= 1000 ? "%.0f" : "%.2f", last), (float) (PAD_L + plotW + 8), (float) (ly + fm.getAscent() / 2.0 - 1));

        // lower panels
        double y0 = LEGEND_H + MAIN_H;
        for (String k : panels()) {
            double pt = y0 + LABEL_H, ph = PANEL_H.get(k);
            switch (k) {
                case VOLUME -> {
                    title(g, "Volume", y0);
                    double mx = 1; for (int i = a; i <= b; i++) mx = Math.max(mx, d.vol(i));
                    grid(g, pt, ph, 0, mx, new double[]{mx / 2, mx}, plotW, Fmt2::compactCount);
                    for (int i = a; i <= b; i++) {
                        boolean up = i == 0 || d.close()[i] >= d.close()[i - 1];
                        double vh = d.vol(i) / mx * ph;
                        g.setColor(Theme.alpha(up ? t.buy : t.sell, .45));
                        g.fill(new Rectangle2D.Double(X.applyAsDouble(i) - Math.max(.5, bw * .35), pt + ph - vh, Math.max(1, bw * .7), vh));
                    }
                }
                case RSI -> {
                    title(g, "RSI (" + p.rsiN + ")", y0);
                    java.util.function.DoubleUnaryOperator Yr = v -> pt + ph - v / 100 * ph;
                    g.setColor(t.soft); g.fill(new Rectangle2D.Double(PAD_L, Yr.applyAsDouble(p.ob), plotW, Yr.applyAsDouble(p.os) - Yr.applyAsDouble(p.ob)));
                    grid(g, pt, ph, 0, 100, new double[]{p.os, p.ob}, plotW, v -> String.valueOf((int) v));
                    line(g, rsi, a, b, X, Yr, t.accent, 1.3f, false);
                }
                case MACD -> {
                    title(g, "MACD 12, 26, 9", y0);
                    double m = 1e-9; for (int i = a; i <= b; i++) for (double v : new double[]{macd[i], macdSig[i], macdHist[i]}) if (!Double.isNaN(v)) m = Math.max(m, Math.abs(v));
                    m *= 1.1; final double M = m;
                    java.util.function.DoubleUnaryOperator Ym = v -> pt + ph / 2 - v / M * ph / 2;
                    grid(g, pt, ph, -m, m, new double[]{0}, plotW, v -> "0");
                    for (int i = a; i <= b; i++) if (!Double.isNaN(macdHist[i])) {
                        double y1 = Ym.applyAsDouble(0), y2 = Ym.applyAsDouble(macdHist[i]);
                        g.setColor(Theme.alpha(macdHist[i] >= 0 ? t.buy : t.sell, .5));
                        g.fill(new Rectangle2D.Double(X.applyAsDouble(i) - Math.max(.5, bw * .35), Math.min(y1, y2), Math.max(1, bw * .7), Math.abs(y2 - y1)));
                    }
                    line(g, macd, a, b, X, Ym, t.accent, 1.3f, false);
                    line(g, macdSig, a, b, X, Ym, t.slow, 1.3f, false);
                }
                case EQUITY -> {
                    title(g, "Growth of $10,000 · strategy vs buy & hold", y0);
                    int s0 = Math.max(a, off + e.start);
                    if (s0 > b) { g.setColor(t.muted); g.drawString("The backtest covers later dates. Widen the range to see it.", PAD_L, (float) (pt + ph / 2)); break; }
                    double elo = Double.MAX_VALUE, ehi = -Double.MAX_VALUE;
                    for (int i = s0; i <= b; i++) { int k2 = i - off; elo = Math.min(elo, Math.min(e.eq[k2], e.bh[k2])); ehi = Math.max(ehi, Math.max(e.eq[k2], e.bh[k2])); }
                    double ep = (ehi - elo) * .1; if (ep == 0) ep = .05; elo -= ep; ehi += ep; final double EL = elo, EH = ehi;
                    java.util.function.DoubleUnaryOperator Ye = v -> pt + ph - (v - EL) / (EH - EL) * ph;
                    grid(g, pt, ph, elo, ehi, niceTicks(elo, ehi, 3), plotW, v -> v * 10 < 100 ? String.format("$%.1fk", v * 10) : String.format("$%.0fk", v * 10));
                    double[] eqF = shift(e.eq), bhF = shift(e.bh);
                    line(g, bhF, s0, b, X, Ye, t.muted, 1.3f, true);
                    line(g, eqF, s0, b, X, Ye, t.accent, 1.8f, false);
                }
                default -> {}
            }
            y0 += LABEL_H + ph;
        }

        // date axis
        g.setFont(Theme.mono(11f)); fm = g.getFontMetrics(); g.setColor(t.muted);
        int nT = W < 700 ? 3 : 6; boolean days = n <= 140;
        for (int k = 0; k <= nT; k++) {
            int i = (int) Math.round(a + (b - a) * k / (double) nT); String ds = d.dates().get(i);
            String s = days ? Dates.dayShort(ds) : Dates.tick(ds.substring(0, 7));
            double x = X.applyAsDouble(i), sw = fm.stringWidth(s);
            g.drawString(s, (float) (k == 0 ? Math.max(PAD_L, x - sw / 2) : k == nT ? Math.min(x - sw / 2, PAD_L + plotW - sw) : x - sw / 2), (float) (y0 + 15));
        }
        // crosshair
        if (hover != null && hover >= a && hover <= b) {
            double x = X.applyAsDouble(hover);
            g.setColor(Theme.alpha(t.muted, .8)); g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{3, 3}, 0));
            g.draw(new Line2D.Double(x, top, x, y0));
            double hy = Y.applyAsDouble(d.close()[hover]);
            g.setStroke(new BasicStroke(1)); g.draw(new Line2D.Double(PAD_L, hy, PAD_L + plotW, hy));
            g.setColor(t.muted); g.fill(new RoundRectangle2D.Double(PAD_L + plotW + 3, hy - 10, PAD_R - 4, 20, 6, 6));
            g.setColor(t.panel); g.setFont(Theme.mono(11f)); g.drawString(String.format(d.close()[hover] >= 1000 ? "%.0f" : "%.2f", d.close()[hover]), (float) (PAD_L + plotW + 8), (float) (hy + fm.getAscent() / 2.0 - 1));
        }
        g.dispose();
    }

    private double[] shift(double[] x) { double[] o = new double[d.size()]; Arrays.fill(o, Double.NaN); for (int k = 0; k < x.length && k + off < o.length; k++) o[k + off] = x[k]; return o; }
    private List<double[]> overlays() {
        List<double[]> l = new ArrayList<>();
        if (on.contains(SMA)) { l.add(smaF); l.add(smaS); }
        if (on.contains(EMA21)) l.add(ema21);
        if (on.contains(SMA200)) l.add(sma200);
        if (on.contains(BB)) { l.add(bbU); l.add(bbL); }
        return l;
    }
    private void drawBars(Graphics2D g, int a, int b, java.util.function.IntToDoubleFunction X, java.util.function.DoubleUnaryOperator Y, double bw) {
        Theme t = Theme.T;
        for (int i = a; i <= b; i++) {
            double o = d.adjOpen(i), c = d.close()[i], hi = d.adjHigh(i), lo = d.adjLow(i);
            boolean up = c >= o; Color col = up ? t.buy : t.sell;
            double x = X.applyAsDouble(i), yh = Y.applyAsDouble(hi), yl = Y.applyAsDouble(lo), yo = Y.applyAsDouble(o), yc = Y.applyAsDouble(c);
            g.setColor(col); g.setStroke(new BasicStroke(bw >= 4 ? 1.2f : 1f));
            if (bw < 2.2) { g.draw(new Line2D.Double(x, yh, x, yl)); continue; }
            if (type == Type.CANDLE) {
                double w = Math.max(1, Math.min(bw * .7, 14));
                g.draw(new Line2D.Double(x, yh, x, Math.min(yo, yc))); g.draw(new Line2D.Double(x, Math.max(yo, yc), x, yl));
                Rectangle2D body = new Rectangle2D.Double(x - w / 2, Math.min(yo, yc), w, Math.max(1, Math.abs(yc - yo)));
                if (up && bw >= 5) { g.setColor(Theme.alpha(col, .18)); g.fill(body); g.setColor(col); g.draw(body); } else g.fill(body);
            } else {
                double w = Math.max(1.5, Math.min(bw * .45, 7));
                g.draw(new Line2D.Double(x, yh, x, yl)); g.draw(new Line2D.Double(x - w, yo, x, yo)); g.draw(new Line2D.Double(x, yc, x + w, yc));
            }
        }
    }
    private void title(Graphics2D g, String s, double y0) { g.setFont(Theme.semibold(11f)); g.setColor(Theme.T.muted); g.drawString(s, PAD_L, (float) (y0 + 15)); g.setFont(Theme.mono(11f)); }
    private void grid(Graphics2D g, double top, double h, double lo, double hi, double[] ticks, double plotW, java.util.function.DoubleFunction<String> fmt) {
        FontMetrics fm = g.getFontMetrics();
        for (double v : ticks) {
            double y = top + h - (v - lo) / (hi - lo) * h; if (y < top - 1 || y > top + h + 1) continue;
            g.setColor(Theme.T.soft); g.setStroke(new BasicStroke(1)); g.draw(new Line2D.Double(PAD_L, y, PAD_L + plotW, y));
            g.setColor(Theme.T.muted); g.drawString(fmt.apply(v), (float) (PAD_L + plotW + 8), (float) (y + fm.getAscent() / 2.0 - 1));
        }
    }
    private void line(Graphics2D g, double[] arr, int a, int b, java.util.function.IntToDoubleFunction X, java.util.function.DoubleUnaryOperator Y, Color col, float w, boolean dashed) {
        if (arr == null) return;
        Path2D p = new Path2D.Double(); boolean on = false;
        for (int i = a; i <= b && i < arr.length; i++) { double v = arr[i]; if (Double.isNaN(v)) { on = false; continue; } double x = X.applyAsDouble(i), y = Y.applyAsDouble(v); if (on) p.lineTo(x, y); else p.moveTo(x, y); on = true; }
        g.setColor(col);
        g.setStroke(dashed ? new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{4, 3}, 0) : new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(p);
    }
    private static double[] niceTicks(double lo, double hi, int n) { return Charts.niceTicks(lo, hi, n); }

    /** Share counts as 12K, 3.4M, 1.2B. */
    static final class Fmt2 {
        static String compactCount(double v) {
            double a = Math.abs(v);
            if (a >= 1e9) return String.format("%.1fB", v / 1e9);
            if (a >= 1e6) return String.format("%.1fM", v / 1e6);
            if (a >= 1e3) return String.format("%.0fK", v / 1e3);
            return String.format("%.0f", v);
        }
    }
}

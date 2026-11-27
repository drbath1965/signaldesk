package com.signaldesk.ui.views;

import com.signaldesk.calc.Dates;
import com.signaldesk.calc.Fmt;
import com.signaldesk.ui.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/** Page-level building blocks shared by the sections. */
final class Kit {
    private Kit() {}
    static Theme t() { return Theme.T; }

    static JComponent head(String eyebrow, String title, String desc, JComponent right) {
        Ui.HBox h = new Ui.HBox(24);
        Ui.VBox l = new Ui.VBox(2);
        if (eyebrow != null) l.add(Ui.caps(eyebrow), false);
        l.add(Ui.h1(title), false);
        if (desc != null) { JTextArea p = Ui.para(desc, Theme.body(14f), t().muted); p.putClientProperty("maxw", 620); l.add(p); }
        h.grow(l);
        if (right != null) h.add(right);
        return h;
    }
    /** Card with a title row (title on the left, optional component on the right). */
    static Ui.Card card(String title, JComponent right) {
        Ui.Card c = new Ui.Card();
        if (title != null || right != null) {
            Ui.HBox h = new Ui.HBox(12);
            h.grow(title == null ? new JLabel() : Ui.h2(title));
            if (right != null) h.add(right);
            c.add(h);
        }
        return c;
    }
    static Ui.Card capsCard(String caps, JComponent right) {
        Ui.Card c = new Ui.Card();
        Ui.HBox h = new Ui.HBox(12); h.grow(Ui.caps(caps)); if (right != null) h.add(right);
        c.add(h); return c;
    }
    static JComponent legend(Object... labelColor) {
        Ui.HBox h = new Ui.HBox(14);
        for (int i = 0; i < labelColor.length; i += 2) {
            String l = (String) labelColor[i]; Color c = (Color) labelColor[i + 1];
            Ui.HBox it = new Ui.HBox(6);
            it.add(swatch(c)); it.add(Ui.text(l, Theme.body(12.5f), t().muted));
            h.add(it);
        }
        return h;
    }
    static JComponent swatch(Color c) {
        JComponent s = new JComponent() { @Override protected void paintComponent(Graphics g) { Graphics2D g2 = Ui.aa(g); g2.setColor(c); g2.fillRoundRect(0, (getHeight() - 10) / 2, 10, 10, 3, 3); g2.dispose(); } };
        s.setPreferredSize(new Dimension(10, 14)); return s;
    }
    static String[] monthTicks(java.util.List<String> months) { return months.stream().map(Dates::tick).toArray(String[]::new); }
    static Color signColor(double v) { return v >= 0 ? t().buy : t().sell; }
    static String signedMoney(double v) { return (v >= 0 ? "+" : "−") + Fmt.money(Math.abs(v)); }
    static JComponent empty(String s) { return Ui.para(s, Theme.body(13.5f), t().muted); }

    // ------------------------------------------------------------------ investing pieces
    static String signedPct(double f, int d) {
        if (Double.isNaN(f)) return "—";
        double r = Math.round(f * Math.pow(10, d + 2)) / Math.pow(10, d + 2);
        return (r > 0 ? "+" : r < 0 ? "−" : "") + Fmt.pctu(Math.abs(r), d);
    }
    static JLabel pct(double f, int d, float size) { return Ui.mono(signedPct(f, d), size, Double.isNaN(f) ? t().muted : signColor(f)); }
    static String price(double v) { return Double.isNaN(v) ? "—" : v >= 1000 ? Fmt.money(v) : v >= 1 ? Fmt.money2(v) : "$" + Fmt.num(v, 4); }

    /** Plain line of the last n closes, colored by whether it ended above where it started. */
    static JComponent spark(com.signaldesk.signals.PriceSeries s, int n, int w, int h) {
        double[] c = s == null ? new double[0] : s.close(); int a = Math.max(0, c.length - n);
        JComponent j = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                if (c.length - a < 2) return;
                Graphics2D g2 = Ui.aa(g); double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                for (int i = a; i < c.length; i++) { lo = Math.min(lo, c[i]); hi = Math.max(hi, c[i]); }
                if (hi == lo) hi = lo + 1;
                java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double(); int W = getWidth() - 2, H = getHeight() - 4;
                for (int i = a; i < c.length; i++) {
                    double x = 1 + (double) (i - a) / (c.length - 1 - a) * W, y = 2 + (hi - c[i]) / (hi - lo) * H;
                    if (i == a) p.moveTo(x, y); else p.lineTo(x, y);
                }
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(c[c.length - 1] >= c[a] ? t().buy : t().sell); g2.draw(p); g2.dispose();
            }
        };
        j.setPreferredSize(new Dimension(w, h)); j.setMinimumSize(new Dimension(w, h)); return j;
    }

    static Ui.Tone tone(com.signaldesk.signals.Scoring.Rating r) {
        return switch (r) { case STRONG, POSITIVE -> Ui.Tone.POS; case NEUTRAL -> Ui.Tone.NEU; case WEAK -> Ui.Tone.WARN; case AVOID -> Ui.Tone.NEG; };
    }
    /** Score number on a tinted rounded square, with the rating word beside it unless compact. */
    static JComponent score(com.signaldesk.signals.Scoring.Result r, boolean compact) {
        if (r == null) return Ui.text("—", Theme.mono(13f), t().muted);
        Ui.Tone k = tone(r.rating()); int v = (int) Math.round(r.score());
        JComponent n = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g); g2.setColor(Ui.toneBg(k)); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setFont(Theme.monoBold(13f)); g2.setColor(Ui.toneFg(k)); FontMetrics fm = g2.getFontMetrics(); String s = String.valueOf(v);
                g2.drawString(s, (getWidth() - fm.stringWidth(s)) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent()); g2.dispose();
            }
        };
        n.setPreferredSize(new Dimension(36, 24)); n.setMinimumSize(n.getPreferredSize());
        n.setToolTipText("Score " + v + " of 100: " + r.rating().label);
        if (compact) return n;
        Ui.HBox h = new Ui.HBox(8); h.add(n); h.add(Ui.text(r.rating().label, Theme.semibold(13f), Ui.toneFg(k))); return h;
    }
    static JComponent callPill(com.signaldesk.signals.SignalEngine.Call c) {
        if (c == null) return Ui.text("—", Theme.mono(13f), t().muted);
        return switch (c) { case BUY -> Ui.pill("Buy", Ui.Tone.POS); case SELL -> Ui.pill("Sell", Ui.Tone.NEG); case HOLD -> Ui.pill("Hold", Ui.Tone.ACC); case WAIT -> Ui.pill("Wait", Ui.Tone.NEU); };
    }
    static JComponent typePill(com.signaldesk.signals.AssetType a) { return Ui.pill(a.label, Ui.Tone.NEU); }
    static JComponent disclaimer() {
        return Ui.hint("Scores and calls are a screen built from price history only, for learning and research. They are not financial advice and know nothing about earnings, valuation or news.");
    }

    /**
     * A light table: fixed-width columns (one of them can stretch), a header row, clickable rows with hover.
     * Widths are pixels; a negative width marks the stretching column. Right-aligned columns are flagged with 'r'.
     */
    static final class Grid extends Ui.VBox {
        private final int[] w; private final boolean[] right;
        Grid(String[] heads, int[] widths, String align) {
            super(0); w = widths; right = new boolean[w.length];
            for (int i = 0; i < w.length; i++) right[i] = align != null && i < align.length() && align.charAt(i) == 'r';
            JComponent[] hs = new JComponent[heads.length];
            for (int i = 0; i < heads.length; i++) hs[i] = Ui.caps(heads[i]);
            if (!String.join("", heads).isBlank()) add(row(hs, null, true));
        }
        Grid row(Runnable onClick, JComponent... cells) { add(row(cells, onClick, false)); return this; }
        private JComponent row(JComponent[] cells, Runnable onClick, boolean head) {
            JPanel r = new JPanel(null) {
                boolean hov; final JPanel self = this;
                {
                    if (onClick != null) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        MouseAdapter m = new MouseAdapter() {
                            @Override public void mouseClicked(MouseEvent e) { onClick.run(); }
                            @Override public void mouseEntered(MouseEvent e) { hov = true; repaint(); }
                            @Override public void mouseExited(MouseEvent e) { if (!contains(SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), self))) { hov = false; repaint(); } }
                        };
                        addMouseListener(m);
                        for (JComponent c : cells) if (c != null && !(c instanceof AbstractButton)) c.addMouseListener(m);
                    }
                }
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = Ui.aa(g);
                    if (hov) { g2.setColor(t().soft); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8); }
                    else if (!head) { g2.setColor(t().soft); g2.fillRect(8, 0, getWidth() - 16, 1); }
                    g2.dispose();
                }
                @Override public Dimension getPreferredSize() {
                    int h = 0, tw = 0; for (Component c : getComponents()) h = Math.max(h, c.getPreferredSize().height);
                    for (int x : w) tw += Math.abs(x) + 12; return new Dimension(tw + 16, h + (head ? 10 : 16));
                }
                @Override public void doLayout() {
                    int fixed = 0, grow = -1; for (int i = 0; i < w.length; i++) { if (w[i] < 0) grow = i; fixed += Math.abs(w[i]) + 12; }
                    int extra = Math.max(0, getWidth() - 16 - fixed), x = 8, H = getHeight();
                    for (int i = 0; i < w.length && i < getComponentCount(); i++) {
                        int cw = Math.abs(w[i]) + (i == grow ? extra : 0); Component c = getComponent(i); Dimension d = c.getPreferredSize();
                        int ww = Math.min(cw, d.width), xx = right[i] ? x + cw - ww : x;
                        if (c instanceof JTextArea || c instanceof Ui.VBox || (c instanceof JLabel && !right[i])) ww = cw;
                        c.setBounds(xx, (H - d.height) / 2, ww, d.height);
                        x += cw + 12;
                    }
                }
            };
            r.setOpaque(false);
            for (JComponent c : cells) r.add(c == null ? new JLabel() : c);
            return r;
        }
    }
}

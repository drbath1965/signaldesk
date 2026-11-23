package com.signaldesk.ui;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;

/** Small factory methods and widgets shared by every section. */
public final class Ui {
    private Ui() {}
    static Theme t() { return Theme.T; }

    // ------------------------------------------------------------------ text
    public static JLabel text(String s, Font f, Color c) { JLabel l = new JLabel(s); l.setFont(f); l.setForeground(c); return l; }
    public static JLabel caps(String s) { return text(s.toUpperCase(), Theme.caps(11f), t().muted); }
    public static JLabel h1(String s) { return text(s, Theme.display(32f), t().ink); }
    public static JLabel h2(String s) { return text(s, Theme.bold(17f), t().ink); }
    public static JLabel h3(String s) { return text(s, Theme.semibold(14f), t().ink); }
    public static JLabel muted(String s) { return text(s, Theme.body(13f), t().muted); }
    public static JLabel mono(String s, float size, Color c) { return text(s, Theme.mono(size), c); }
    public static JLabel big(String s) { return text(s, Theme.mono(36f), t().ink); }

    /** Wrapping paragraph. */
    public static JTextArea para(String s, Font f, Color c) {
        JTextArea a = new JTextArea(s) {
            private int lastW = -1, lastH;
            @Override public Dimension getPreferredSize() {
                int w = getWidth() > 0 ? getWidth() : 420;
                w = Math.max(80, Math.min(w, getMaxW()));
                if (w != lastW) {
                    javax.swing.text.View v = getUI().getRootView(this);
                    v.setSize(w, Short.MAX_VALUE); lastH = (int) Math.ceil(v.getPreferredSpan(javax.swing.text.View.Y_AXIS)); lastW = w;
                }
                FontMetrics fm = getFontMetrics(getFont());
                int natural = 0; for (String line : getText().split("\n")) natural = Math.max(natural, fm.stringWidth(line));
                return new Dimension(Math.min(getMaxW(), natural + 2), Math.max(lastH, fm.getHeight()));
            }
            @Override public Dimension getMinimumSize() { return new Dimension(40, getPreferredSize().height); }
            int getMaxW() { Object o = getClientProperty("maxw"); return o instanceof Integer i ? i : 760; }
        };
        a.putClientProperty("maxw", 760);
        a.setLineWrap(true); a.setWrapStyleWord(true); a.setEditable(false); a.setFocusable(false); a.setOpaque(false);
        a.setFont(f); a.setForeground(c); a.setBorder(null); a.setHighlighter(null);
        return a;
    }
    public static JTextArea para(String s) { return para(s, Theme.body(13.5f), t().muted); }
    public static JTextArea hint(String s) { return para(s, Theme.body(12.5f), t().muted); }

    // ------------------------------------------------------------------ layout
    /** Vertical stack: children take the full width (or their natural width) at their preferred height. */
    public static class VBox extends JPanel {
        private final int gap;
        public VBox(int gap) {
            this.gap = gap; setOpaque(false);
            setLayout(new LayoutManager() {
                public void addLayoutComponent(String n, Component c) {}
                public void removeLayoutComponent(Component c) {}
                public Dimension preferredLayoutSize(Container p) {
                    Insets in = p.getInsets(); int w = 0, h = 0, n = 0;
                    for (Component c : p.getComponents()) { if (!c.isVisible()) continue; Dimension d = c.getPreferredSize(); w = Math.max(w, d.width); h += d.height + (n++ > 0 ? gap : 0); }
                    return new Dimension(w + in.left + in.right, h + in.top + in.bottom);
                }
                public Dimension minimumLayoutSize(Container p) { Dimension d = preferredLayoutSize(p); return new Dimension(Math.min(d.width, 60), d.height); }
                public void layoutContainer(Container p) {
                    Insets in = p.getInsets(); int W = p.getWidth() - in.left - in.right, y = in.top, n = 0;
                    for (Component c : p.getComponents()) {
                        if (!c.isVisible()) continue;
                        boolean fill = !(c instanceof JComponent jc) || !Boolean.FALSE.equals(jc.getClientProperty("vbox.fill"));
                        int pw = c.getPreferredSize().width, w = fill ? W : Math.min(pw, W);
                        if (c instanceof JComponent jc && jc.getClientProperty("maxw") instanceof Integer mx) w = Math.min(w, mx);
                        c.setSize(w, Short.MAX_VALUE);
                        int h = c.getPreferredSize().height;
                        if (n++ > 0) y += gap;
                        c.setBounds(in.left, y, w, h); y += h;
                    }
                }
            });
        }
        public VBox add(JComponent c) { return add(c, true); }
        public VBox add(JComponent c, boolean fill) { c.putClientProperty("vbox.fill", fill); super.add(c); return this; }
        public VBox end() { return this; }
    }
    /** Horizontal row: natural widths, left-aligned, vertically centered; grow() items share the spare width. */
    public static class HBox extends JPanel {
        private final int gap;
        public HBox(int gap) {
            this.gap = gap; setOpaque(false);
            setLayout(new LayoutManager() {
                public void addLayoutComponent(String n, Component c) {}
                public void removeLayoutComponent(Component c) {}
                private boolean grows(Component c) { return c instanceof JComponent jc && Boolean.TRUE.equals(jc.getClientProperty("hbox.grow")); }
                public Dimension preferredLayoutSize(Container p) {
                    Insets in = p.getInsets(); int w = 0, h = 0, n = 0;
                    for (Component c : p.getComponents()) { if (!c.isVisible()) continue; Dimension d = c.getPreferredSize(); w += d.width + (n++ > 0 ? gap : 0); h = Math.max(h, d.height); }
                    return new Dimension(w + in.left + in.right, h + in.top + in.bottom);
                }
                public Dimension minimumLayoutSize(Container p) {
                    Insets in = p.getInsets(); int w = 0, h = 0, n = 0;
                    for (Component c : p.getComponents()) { if (!c.isVisible()) continue; Dimension d = c.getPreferredSize(); w += (grows(c) ? 40 : d.width) + (n++ > 0 ? gap : 0); h = Math.max(h, d.height); }
                    return new Dimension(w + in.left + in.right, h + in.top + in.bottom);
                }
                public void layoutContainer(Container p) {
                    Insets in = p.getInsets(); int W = p.getWidth() - in.left - in.right, H = p.getHeight() - in.top - in.bottom;
                    java.util.List<Component> cs = new java.util.ArrayList<>(); for (Component c : p.getComponents()) if (c.isVisible()) cs.add(c);
                    int fixed = 0, growN = 0;
                    for (Component c : cs) { if (grows(c)) growN++; else fixed += c.getPreferredSize().width; }
                    fixed += gap * Math.max(0, cs.size() - 1);
                    int spare = Math.max(0, W - fixed), each = growN > 0 ? spare / growN : 0;
                    int x = in.left, rowH = 0;
                    int[] ws = new int[cs.size()]; int[] hs = new int[cs.size()];
                    for (int i = 0; i < cs.size(); i++) {
                        Component c = cs.get(i); int w = grows(c) ? each : Math.min(c.getPreferredSize().width, Math.max(0, W - (x - in.left)));
                        c.setSize(w, Short.MAX_VALUE); ws[i] = w; hs[i] = c.getPreferredSize().height; rowH = Math.max(rowH, hs[i]); x += w + gap;
                    }
                    x = in.left; int top = in.top + Math.max(0, (H - rowH) / 2);
                    for (int i = 0; i < cs.size(); i++) { cs.get(i).setBounds(x, top + (rowH - hs[i]) / 2, ws[i], hs[i]); x += ws[i] + gap; }
                }
            });
        }
        public HBox add(JComponent c) { super.add(c); return this; }
        public HBox grow(JComponent c) { c.putClientProperty("hbox.grow", true); super.add(c); return this; }
        public HBox glue() { return grow(new JLabel()); }
    }
    /** Equal-width columns. */
    public static JPanel cols(int gap, JComponent... cs) {
        JPanel p = new JPanel(new GridLayout(1, cs.length, gap, 0)); p.setOpaque(false);
        for (JComponent c : cs) p.add(c);
        return p;
    }
    /** Main column plus a fixed-width side rail. */
    public static JPanel rail(JComponent main, JComponent side, int sideW) {
        JPanel p = new JPanel(new BorderLayout(18, 0)); p.setOpaque(false);
        p.add(main, BorderLayout.CENTER);
        JPanel s = new JPanel(new BorderLayout()) { @Override public Dimension getPreferredSize() { return new Dimension(sideW, super.getPreferredSize().height); } };
        s.setOpaque(false); s.add(side, BorderLayout.NORTH);
        p.add(s, BorderLayout.EAST);
        return p;
    }
    public static JComponent top(JComponent c) { JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false); p.add(c, BorderLayout.NORTH); return p; }

    // ------------------------------------------------------------------ surfaces
    /** White rounded panel with a hairline border. */
    public static class Card extends VBox {
        private Color fill;
        public Card() { this(14); }
        public Card(int gap) { super(gap); setBorder(new EmptyBorder(16, 18, 16, 18)); }
        public Card fill(Color c) { fill = c; return this; }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setColor(fill != null ? fill : t().panel);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 14, 14));
            g2.setColor(t().line);
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 2, getHeight() - 2, 14, 14));
            g2.dispose();
        }
    }
    public static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    // ------------------------------------------------------------------ pill
    public enum Tone { POS, NEG, NEU, WARN, ACC }
    public static Color toneFg(Tone k) { return switch (k) { case POS -> t().buy; case NEG -> t().sell; case WARN -> t().warn; case ACC -> t().accent; default -> t().muted; }; }
    public static Color toneBg(Tone k) { return switch (k) { case POS -> t().buyBg; case NEG -> t().sellBg; case WARN -> t().warnBg; case ACC -> t().holdBg; default -> t().soft; }; }
    public static class Pill extends JComponent {
        private final String s; private final Tone k;
        public Pill(String s, Tone k) { this.s = s; this.k = k; setFont(Theme.semibold(11.5f)); }
        @Override public Dimension getPreferredSize() { FontMetrics fm = getFontMetrics(getFont()); return new Dimension(fm.stringWidth(s) + 18, fm.getHeight() + 4); }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g); int h = getHeight();
            g2.setColor(toneBg(k)); g2.fillRoundRect(0, 0, getWidth(), h, h, h);
            g2.setColor(toneFg(k)); g2.setFont(getFont()); FontMetrics fm = g2.getFontMetrics();
            g2.drawString(s, 9, (h - fm.getHeight()) / 2 + fm.getAscent()); g2.dispose();
        }
    }
    public static Pill pill(String s, Tone k) { return new Pill(s, k); }
    public static JComponent dot(Color c) {
        JComponent d = new JComponent() { @Override protected void paintComponent(Graphics g) { Graphics2D g2 = aa(g); g2.setColor(c); g2.fillOval(0, (getHeight() - 9) / 2, 9, 9); g2.dispose(); } };
        d.setPreferredSize(new Dimension(9, 16)); return d;
    }

    // ------------------------------------------------------------------ buttons
    public static class Btn extends JButton {
        public enum Kind { PLAIN, PRIMARY, DANGER, LINK }
        private Kind kind; private boolean sm, hover; private boolean armed;
        public Btn(String text, Kind kind, boolean sm) {
            super(text); this.kind = kind; this.sm = sm;
            setFont(kind == Kind.LINK ? Theme.medium(sm ? 12.5f : 13f) : Theme.semibold(sm ? 12.5f : 13.5f));
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(kind == Kind.LINK ? new EmptyBorder(0, 0, 0, 0) : sm ? new EmptyBorder(4, 10, 4, 10) : new EmptyBorder(7, 14, 7, 14));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        public void setArmed(boolean a) { armed = a; repaint(); }
        public void setKind(Kind k) { kind = k; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g); Theme th = t(); int w = getWidth(), h = getHeight();
            boolean en = isEnabled();
            Color fg;
            if (kind == Kind.LINK) {
                fg = th.accent;
                g2.setFont(getFont()); FontMetrics fm = g2.getFontMetrics();
                String s = getText(); int x = 0, y = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.setColor(en ? fg : th.muted); g2.drawString(s, x, y);
                if (hover || true) { g2.setColor(Theme.alpha(en ? fg : th.muted, hover ? .9 : .45)); g2.fillRect(x, y + 2, fm.stringWidth(s), 1); }
                g2.dispose(); return;
            }
            Color fill, border;
            if (kind == Kind.PRIMARY || armed) { fill = armed ? th.sell : th.accent; border = fill; fg = th.onAccent; if (hover && en) fill = Theme.mix(fill, th.ink, .85); }
            else { fill = hover && en ? th.soft : th.panel; border = hover && en ? th.accent : th.line; fg = kind == Kind.DANGER ? th.sell : th.ink; }
            if (!en) { fill = th.panel; border = th.line; fg = Theme.alpha(th.muted, .55); }
            g2.setColor(fill); g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
            g2.setColor(border); g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
            if (isFocusOwner()) { g2.setColor(Theme.alpha(th.accent, .6)); g2.drawRoundRect(1, 1, w - 3, h - 3, 9, 9); }
            g2.setColor(fg); g2.setFont(getFont()); FontMetrics fm = g2.getFontMetrics();
            String s = getText(); g2.drawString(s, (w - fm.stringWidth(s)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
    public static Btn btn(String s, Runnable r) { Btn b = new Btn(s, Btn.Kind.PLAIN, false); b.addActionListener(e -> r.run()); return b; }
    public static Btn primary(String s, Runnable r) { Btn b = new Btn(s, Btn.Kind.PRIMARY, false); b.addActionListener(e -> r.run()); return b; }
    public static Btn small(String s, Runnable r) { Btn b = new Btn(s, Btn.Kind.PLAIN, true); b.addActionListener(e -> r.run()); return b; }
    public static Btn link(String s, Runnable r) { Btn b = new Btn(s, Btn.Kind.LINK, false); b.addActionListener(e -> r.run()); return b; }
    public static Btn danger(String s, Runnable r) { Btn b = new Btn(s, Btn.Kind.DANGER, false); b.addActionListener(e -> r.run()); return b; }

    /** Segmented toggle. */
    public static JComponent seg(String[] labels, int selected, Consumer<Integer> onPick) {
        HBox h = new HBox(0);
        for (int i = 0; i < labels.length; i++) {
            int k = i;
            Btn b = new Btn(labels[i], i == selected ? Btn.Kind.PRIMARY : Btn.Kind.PLAIN, true) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = aa(g); Theme th = t();
                    boolean on = k == selected; int w = getWidth(), hh = getHeight();
                    g2.setColor(on ? th.ink : th.panel); g2.fillRect(0, 0, w, hh);
                    g2.setColor(th.line); g2.drawRect(0, 0, w - 1, hh - 1);
                    g2.setFont(getFont()); FontMetrics fm = g2.getFontMetrics(); g2.setColor(on ? th.panel : th.muted);
                    g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (hh - fm.getHeight()) / 2 + fm.getAscent()); g2.dispose();
                }
            };
            b.setBorder(new EmptyBorder(5, 12, 5, 12));
            b.addActionListener(e -> onPick.accept(k));
            h.add(b);
        }
        return h;
    }

    // ------------------------------------------------------------------ meter
    public static JComponent meter(double frac, boolean over, Double marker) {
        JComponent m = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = aa(g); int w = getWidth(), h = 8, y = (getHeight() - h) / 2;
                g2.setColor(t().soft); g2.fillRoundRect(0, y, w, h, h, h);
                g2.setColor(over ? t().sell : t().accent); g2.fillRoundRect(0, y, (int) Math.round(w * Math.max(0, Math.min(1, frac))), h, h, h);
                if (marker != null) { g2.setColor(Theme.alpha(t().ink, .5)); g2.fillRect((int) Math.round(w * marker), y - 2, 2, h + 4); }
                g2.dispose();
            }
        };
        m.setPreferredSize(new Dimension(100, 12)); m.setMinimumSize(new Dimension(20, 12)); return m;
    }

    // ------------------------------------------------------------------ KPI strip
    public record Kpi(String label, String value, Color valueColor, String sub, Runnable onClick) {
        public Kpi(String label, String value, String sub) { this(label, value, null, sub, null); }
        public Kpi(String label, String value, Color c, String sub) { this(label, value, c, sub, null); }
    }
    public static JComponent kpis(Kpi... ks) {
        JPanel p = new JPanel(new GridLayout(1, ks.length, 1, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = aa(g); g2.setColor(t().line); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14); g2.dispose();
            }
        };
        p.setOpaque(false); p.setBorder(new EmptyBorder(1, 1, 1, 1));
        for (int i = 0; i < ks.length; i++) {
            Kpi k = ks[i]; boolean first = i == 0, last = i == ks.length - 1;
            VBox cell = new VBox(3) {
                boolean hov;
                { if (k.onClick() != null) { setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    addMouseListener(new MouseAdapter() {
                        @Override public void mouseClicked(MouseEvent e) { k.onClick().run(); }
                        @Override public void mouseEntered(MouseEvent e) { hov = true; repaint(); }
                        @Override public void mouseExited(MouseEvent e) { hov = false; repaint(); } }); } }
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = aa(g); g2.setColor(hov ? t().soft : t().panel);
                    int w = getWidth(), h = getHeight(); int r = 13;
                    g2.fillRect(first ? r : 0, 0, w - (first ? r : 0) - (last ? r : 0), h);
                    if (first) { g2.fillRoundRect(0, 0, r * 2, h, r, r); g2.fillRect(r, 0, r, h); }
                    if (last) { g2.fillRoundRect(w - r * 2, 0, r * 2, h, r, r); g2.fillRect(w - r * 2, 0, r, h); }
                    g2.dispose();
                }
            };
            cell.setBorder(new EmptyBorder(13, 16, 14, 16));
            cell.add(caps(k.label()));
            cell.add(text(k.value(), Theme.mono(20f), k.valueColor() != null ? k.valueColor() : t().ink));
            if (k.sub() != null && !k.sub().isEmpty()) cell.add(hint(k.sub()));
            cell.end();
            p.add(cell);
        }
        return p;
    }

    // ------------------------------------------------------------------ list rows
    /** One row of a list: left marker, title + subtitle, right-hand component. */
    public static JComponent listRow(JComponent left, String title, JComponent subtitle, JComponent right, boolean divider) {
        JPanel row = new JPanel(new BorderLayout(12, 0)) {
            @Override protected void paintComponent(Graphics g) { if (divider) { g.setColor(t().soft); g.fillRect(0, 0, getWidth(), 1); } }
        };
        row.setOpaque(false); row.setBorder(new EmptyBorder(9, 0, 8, 0));
        if (left != null) { JPanel l = new JPanel(new GridBagLayout()); l.setOpaque(false); l.add(left); row.add(l, BorderLayout.WEST); }
        VBox mid = new VBox(2);
        mid.add(text(title, Theme.semibold(13.5f), t().ink));
        if (subtitle != null) mid.add(subtitle, false);
        row.add(mid, BorderLayout.CENTER);
        if (right != null) { JPanel r = new JPanel(new GridBagLayout()); r.setOpaque(false); r.add(right); row.add(r, BorderLayout.EAST); }
        return row;
    }

    // ------------------------------------------------------------------ inputs
    public static JTextField field(String value, int cols) { JTextField f = new JTextField(value, cols); styleField(f); return f; }
    public static void styleField(JTextComponent f) {
        f.setBackground(t().paper); f.setForeground(t().ink); f.setCaretColor(t().ink);
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(t().line), new EmptyBorder(5, 8, 5, 8)));
        f.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(t().accent), new EmptyBorder(5, 8, 5, 8))); }
            @Override public void focusLost(FocusEvent e) { f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(t().line), new EmptyBorder(5, 8, 5, 8))); }
        });
    }
    public static <T> JComboBox<T> combo(T[] items, T sel) {
        JComboBox<T> c = new JComboBox<>(items); if (sel != null) c.setSelectedItem(sel);
        c.setBackground(t().paper); c.setForeground(t().ink); c.setFont(Theme.body(13.5f));
        return c;
    }
    /** Number field that reports parsed values as you type. */
    public static JTextField numberField(double v, int decimals, Consumer<Double> onChange) {
        JTextField f = field(fmtNum(v, decimals), 7);
        f.setHorizontalAlignment(JTextField.RIGHT); f.setFont(Theme.mono(13.5f));
        f.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void ch() { Double d = parse(f.getText()); f.setForeground(d == null && !f.getText().isBlank() ? t().sell : t().ink); if (d != null) onChange.accept(d); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { ch(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { ch(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { ch(); }
        });
        return f;
    }
    public static String fmtNum(double v, int decimals) {
        if (decimals == 0 || v == Math.rint(v)) return String.valueOf((long) Math.rint(v));
        String s = String.format(java.util.Locale.US, "%." + decimals + "f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }
    public static Double parse(String s) {
        if (s == null) return null;
        String c = s.replace("$", "").replace(",", "").replace("%", "").replace("−", "-").trim();
        if (c.isEmpty()) return null;
        try { return Double.parseDouble(c); } catch (NumberFormatException e) { return null; }
    }
    /** Label on the left, control on the right, with an optional sub-label. */
    public static JComponent formRow(String label, String sub, JComponent control, int controlW) {
        JPanel p = new JPanel(new BorderLayout(10, 0)); p.setOpaque(false);
        VBox l = new VBox(1); l.add(text(label, Theme.body(13.5f), t().ink)); if (sub != null) l.add(hint(sub));
        p.add(l, BorderLayout.CENTER);
        JPanel c = new JPanel(new GridBagLayout()); c.setOpaque(false);
        control.setPreferredSize(new Dimension(controlW, Math.max(30, control.getPreferredSize().height)));
        c.add(control); p.add(c, BorderLayout.EAST);
        return p;
    }
    public static JComponent labeled(String label, JComponent c) {
        VBox v = new VBox(4); v.add(text(label, Theme.body(12f), t().muted)); v.add(c); return v;
    }
}

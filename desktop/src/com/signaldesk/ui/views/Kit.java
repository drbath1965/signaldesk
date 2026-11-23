package com.signaldesk.ui.views;

import com.signaldesk.calc.Dates;
import com.signaldesk.calc.Fmt;
import com.signaldesk.ui.*;
import java.awt.*;
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
}

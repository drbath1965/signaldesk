package com.signaldesk.ui.views;

import com.signaldesk.learn.Learn;
import com.signaldesk.ui.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** The small "?" buttons beside numbers and chart controls. Each opens a short plain-language explanation. */
public final class Help {
    private Help() {}

    public static JComponent q(MainFrame app, String key) {
        Learn.Explain e = Learn.get().explain.get(key);
        if (e == null) return new JLabel();
        JPanel b = new JPanel() {
            boolean hov;
            {
                setOpaque(false);
                setPreferredSize(new Dimension(18, 18)); setMinimumSize(getPreferredSize()); setMaximumSize(getPreferredSize());
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); setToolTipText("What is " + e.title().toLowerCase() + "?");
                setFocusable(true);
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent ev) { Help.show(app, e); }
                    @Override public void mouseEntered(MouseEvent ev) { hov = true; repaint(); }
                    @Override public void mouseExited(MouseEvent ev) { hov = false; repaint(); }
                });
                addKeyListener(new KeyAdapter() { @Override public void keyPressed(KeyEvent ev) { if (ev.getKeyCode() == KeyEvent.VK_ENTER || ev.getKeyCode() == KeyEvent.VK_SPACE) Help.show(app, e); } });
                getAccessibleContext().setAccessibleName("Explain " + e.title());
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g); Theme t = Theme.T; int s = Math.min(getWidth(), getHeight()) - 2, x = (getWidth() - s) / 2, y = (getHeight() - s) / 2;
                g2.setColor(hov ? t.accent : t.panel); g2.fillOval(x, y, s, s);
                g2.setColor(hov ? t.accent : Theme.alpha(t.muted, .7)); g2.drawOval(x, y, s, s);
                g2.setFont(Theme.semibold(11f)); g2.setColor(hov ? t.onAccent : t.muted); FontMetrics fm = g2.getFontMetrics();
                g2.drawString("?", x + (s - fm.stringWidth("?")) / 2 + 1, y + (s - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        };
        return b;
    }
    /** A label followed by its "?" button. */
    public static JComponent label(MainFrame app, JComponent label, String key) {
        Ui.HBox h = new Ui.HBox(6); h.add(label); h.add(q(app, key)); return h;
    }

    public static void show(MainFrame app, Learn.Explain e) {
        Theme t = Theme.T;
        JDialog d = new JDialog(app, e.title(), true);
        Ui.VBox v = new Ui.VBox(12);
        v.setBorder(new EmptyBorder(20, 22, 18, 22)); v.setBackground(t.panel); v.setOpaque(true);
        v.add(Ui.h2(e.title()), false);
        JTextArea body = Ui.para(e.body(), Theme.body(14f), t.ink); body.putClientProperty("maxw", 420); v.add(body);
        Ui.HBox foot = new Ui.HBox(10);
        Learn.Term term = e.term() == null ? null : Learn.get().term(e.term());
        if (term != null) foot.add(Ui.link("See \"" + term.term() + "\" in the glossary", () -> { d.dispose(); app.learn("term:" + term.term()); }));
        foot.glue();
        Ui.Btn ok = Ui.primary("Got it", d::dispose); foot.add(ok);
        v.add(foot);
        d.setContentPane(v);
        d.getRootPane().setDefaultButton(ok);
        d.getRootPane().registerKeyboardAction(ev -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setSize(480, 10); d.pack(); d.setSize(480, d.getPreferredSize().height);
        d.setLocationRelativeTo(app); d.setVisible(true);
    }
}

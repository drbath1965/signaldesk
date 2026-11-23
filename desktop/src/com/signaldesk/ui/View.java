package com.signaldesk.ui;

import com.signaldesk.model.Studio;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** One section of the studio. build() makes the page; refresh() updates computed parts after an edit. */
public abstract class View {
    protected final MainFrame app;
    protected View(MainFrame app) { this.app = app; }
    protected Studio s() { return app.studio(); }
    public abstract JComponent build();
    public void refresh() {}

    /** A placeholder whose contents get swapped on refresh, leaving inputs elsewhere untouched. */
    public static final class Slot extends JPanel {
        public Slot() { super(new BorderLayout()); setOpaque(false); }
        public Slot(JComponent c) { this(); set(c); }
        public void set(JComponent c) { removeAll(); add(c, BorderLayout.CENTER); revalidate(); repaint(); }
    }
}

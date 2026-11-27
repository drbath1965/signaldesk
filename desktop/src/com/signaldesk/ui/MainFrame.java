package com.signaldesk.ui;

import com.signaldesk.model.BacklogItem;
import com.signaldesk.model.Studio;
import com.signaldesk.store.*;
import com.signaldesk.ui.views.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** The studio window: sidebar navigation, the current section, autosave and messages. */
public final class MainFrame extends JFrame {
    /** From the jar's manifest, which build.sh fills in from the VERSION file. */
    public static final String VERSION = java.util.Optional.ofNullable(MainFrame.class.getPackage().getImplementationVersion()).orElse("dev");
    private static final Map<String, String> SECTIONS = new LinkedHashMap<>();
    static {
        SECTIONS.put("markets", "Markets"); SECTIONS.put("research", "Research"); SECTIONS.put("picks", "Picks");
        SECTIONS.put("practice", "Practice"); SECTIONS.put("learn", "Learn"); SECTIONS.put("backlog", "Backlog");
    }

    private Studio studio;
    private final StudioStore store;
    private final PriceStore prices;
    private final Timer saveTimer;
    private String section = "markets";
    private View current;
    private final JPanel sidebar = new JPanel(new BorderLayout());
    private final ScrollPage page = new ScrollPage();
    private final JScrollPane scroll = new JScrollPane(page);
    private final JPanel toastBar = new JPanel(new BorderLayout());
    private JLabel saveLabel;
    private Timer toastTimer;
    private String saveState = "";
    private String pendingArg;
    private final PriceService priceService = new PriceService(this);

    public MainFrame(Studio studio, StudioStore store, PriceStore prices, String loadNote) {
        super("Signal Desk " + VERSION);
        this.studio = studio; this.store = store; this.prices = prices;
        setIconImages(List.of(icon(16), icon(32), icon(64), icon(128)));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { saveNow(); dispose(); System.exit(0); } });
        saveTimer = new Timer(600, e -> saveNow()); saveTimer.setRepeats(false);
        scroll.setBorder(null); scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        buildShell();
        setMinimumSize(new Dimension(1120, 720));
        setSize(new Dimension(1400, 900));
        setLocationRelativeTo(null);
        installShortcuts();
        navigate("markets");
        saveState = store.exists() ? "Saved" : "Not saved yet";
        updateSaveLabel();
        if (loadNote != null) SwingUtilities.invokeLater(() -> toast(loadNote, null));
        if (!Boolean.getBoolean("signaldesk.offline")) SwingUtilities.invokeLater(() -> priceService.refreshEveryday(false, null));
    }

    // ------------------------------------------------------------------ public API for views
    public Studio studio() { return studio; }
    public PriceStore prices() { return prices; }
    public StudioStore store() { return store; }
    public String section() { return section; }
    public PriceService priceService() { return priceService; }

    /** Redraw the current section's computed parts without touching inputs. */
    public void refreshCurrent() { if (current != null) current.refresh(); }

    /** Call after any edit: schedules a save and refreshes computed parts. */
    public void changed() {
        saveState = "Saving…"; updateSaveLabel();
        saveTimer.restart();
        if (current != null) current.refresh();
    }
    /** Replace all data (restore, start over) and redraw. */
    public void replaceStudio(Studio s, boolean save) {
        this.studio = s;
        if (save) { saveState = "Saving…"; saveTimer.restart(); }
        navigate(section);
        updateSaveLabel();
    }
    public void navigate(String key) {
        if (!SECTIONS.containsKey(key) && !key.equals("data")) key = "markets";
        section = key;
        String arg = pendingArg; pendingArg = null;
        current = switch (key) {
            case "research" -> new ResearchView(this, arg);
            case "picks" -> new PicksView(this);
            case "practice" -> new PracticeView(this, arg);
            case "learn" -> new LearnView(this, arg);
            case "backlog" -> new BacklogView(this);
            case "data" -> new DataView(this);
            default -> new MarketsView(this);
        };
        page.removeAll();
        page.add(current.build(), BorderLayout.CENTER);
        buildSidebar();
        page.revalidate(); page.repaint();
        SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
    }
    /** Open Research on a symbol. */
    public void analyze(String sym) { pendingArg = sym; navigate("research"); }
    /** Open Practice with a trade ticket for a symbol. */
    public void trade(String sym) { pendingArg = sym; navigate("practice"); }
    /** Open Learn on a lesson ("lesson:id") or a glossary search ("term:text"). */
    public void learn(String what) { pendingArg = what; navigate("learn"); }
    /** Re-render the current section without changing data (e.g. after a structural edit). */
    public void rebuild() {
        int y = scroll.getVerticalScrollBar().getValue();
        navigate(section);
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(y)));
    }

    // ------------------------------------------------------------------ messages
    private Runnable toastUndo;
    public void toast(String msg, Runnable undo) {
        toastUndo = undo;
        toastBar.removeAll();
        Theme t = Theme.T;
        Ui.HBox h = new Ui.HBox(16);
        h.add(Ui.text(msg, Theme.medium(13.5f), t.panel));
        if (undo != null) {
            Ui.Btn u = new Ui.Btn("Undo", Ui.Btn.Kind.LINK, true) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = Ui.aa(g); g2.setFont(getFont()); g2.setColor(Theme.T.dark ? Theme.T.accent : Theme.mix(Theme.T.accent, Color.WHITE, .4));
                    FontMetrics fm = g2.getFontMetrics(); int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent(); g2.drawString(getText(), 0, y);
                    g2.fillRect(0, y + 2, fm.stringWidth(getText()), 1); g2.dispose();
                }
            };
            u.addActionListener(e -> { Runnable r = toastUndo; hideToast(); if (r != null) { r.run(); changed(); rebuild(); toast("Restored", null); } });
            h.add(u);
        }
        JPanel pill = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) { Graphics2D g2 = Ui.aa(g); g2.setColor(Theme.T.ink); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12); g2.dispose(); }
        };
        pill.setOpaque(false); pill.setBorder(new EmptyBorder(9, 16, 9, 16)); pill.add(h);
        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)); center.setOpaque(false); center.add(pill);
        toastBar.add(center); toastBar.setVisible(true); toastBar.revalidate(); toastBar.repaint();
        if (toastTimer != null) toastTimer.stop();
        toastTimer = new Timer(undo != null ? 7000 : Math.max(3600, msg.length() * 70), e -> hideToast()); toastTimer.setRepeats(false); toastTimer.start();
    }
    private void hideToast() { toastBar.setVisible(false); toastUndo = null; }

    // ------------------------------------------------------------------ saving
    public void saveNow() {
        saveTimer.stop();
        try { store.save(studio); saveState = "Saved"; store.dailyBackup(); }
        catch (Exception ex) { saveState = "Couldn't save: " + ex.getMessage(); }
        updateSaveLabel();
    }
    private void updateSaveLabel() {
        if (saveLabel == null) return;
        saveLabel.setText(saveState);
        saveLabel.setForeground(saveState.startsWith("Couldn't") ? Theme.T.sell : Theme.T.muted);
        saveLabel.setToolTipText(store.file().toString());
    }

    // ------------------------------------------------------------------ shell
    private void buildShell() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.T.paper);
        root.add(sidebar, BorderLayout.WEST);
        JPanel main = new JPanel(new BorderLayout()); main.setBackground(Theme.T.paper);
        main.add(scroll, BorderLayout.CENTER);
        toastBar.setOpaque(false); toastBar.setBorder(new EmptyBorder(0, 0, 14, 0)); toastBar.setVisible(false);
        JLayeredPane layered = new JLayeredPane() {
            @Override public void doLayout() {
                main.setBounds(0, 0, getWidth(), getHeight());
                Dimension d = toastBar.getPreferredSize(); toastBar.setBounds(0, getHeight() - d.height - 4, getWidth(), d.height + 4);
            }
        };
        layered.add(main, JLayeredPane.DEFAULT_LAYER); layered.add(toastBar, JLayeredPane.POPUP_LAYER);
        root.add(layered, BorderLayout.CENTER);
        setContentPane(root);
        page.setBackground(Theme.T.paper); scroll.getViewport().setBackground(Theme.T.paper);
    }

    private void buildSidebar() {
        Theme t = Theme.T;
        sidebar.removeAll();
        sidebar.setBackground(t.panel);
        sidebar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, t.line), new EmptyBorder(22, 14, 16, 14)));
        sidebar.setPreferredSize(new Dimension(224, 10));
        Ui.VBox top = new Ui.VBox(2);
        Ui.VBox brand = new Ui.VBox(0);
        brand.add(Ui.text("Signal Desk", Theme.display(26f), t.ink));
        brand.add(Ui.text("INVEST · RESEARCH · LEARN", Theme.caps(11f), t.muted));
        brand.setBorder(new EmptyBorder(0, 8, 18, 0));
        top.add(brand);
        int k = 1;
        for (Map.Entry<String, String> e : SECTIONS.entrySet()) top.add(navButton(e.getKey(), e.getValue(), k++));
        sidebar.add(top, BorderLayout.NORTH);

        Ui.VBox foot = new Ui.VBox(10);
        foot.setBorder(new EmptyBorder(0, 8, 0, 0));
        foot.add(Ui.seg(new String[]{"Light", "Dark"}, t.dark ? 1 : 0, i -> setDark(i == 1)), false);
        saveLabel = Ui.text("", Theme.body(12f), t.muted);
        Ui.HBox st = new Ui.HBox(7);
        st.add(Ui.dot(saveState.startsWith("Couldn't") ? t.sell : t.buy));
        st.grow(saveLabel);
        foot.add(st);
        foot.add(Ui.link("Backup & data", () -> navigate("data")), false);
        foot.add(Ui.text("Version " + VERSION, Theme.body(11.5f), Theme.alpha(t.muted, .8)), false);
        sidebar.add(foot, BorderLayout.SOUTH);
        updateSaveLabel();
        sidebar.revalidate(); sidebar.repaint();
    }

    private JComponent navButton(String key, String label, int n) {
        boolean on = key.equals(section);
        JButton b = new JButton(label) {
            boolean hov;
            { addMouseListener(new MouseAdapter() { public void mouseEntered(MouseEvent e) { hov = true; repaint(); } public void mouseExited(MouseEvent e) { hov = false; repaint(); } }); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g); Theme t = Theme.T;
                if (on) { g2.setColor(t.ink); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10); }
                else if (hov) { g2.setColor(t.soft); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10); }
                Color fg = on ? t.panel : hov ? t.ink : t.muted;
                Icons.paint(g2, key, 12, (getHeight() - 18) / 2, 18, fg);
                g2.setFont(Theme.medium(14f)); g2.setColor(fg); FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), 40, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                if (n <= 9) { g2.setFont(Theme.mono(10.5f)); g2.setColor(Theme.alpha(fg, .55)); String s = "Ctrl " + n; FontMetrics f2 = g2.getFontMetrics();
                    if (hov || on) g2.drawString(s, getWidth() - f2.stringWidth(s) - 10, (getHeight() - f2.getHeight()) / 2 + f2.getAscent()); }
                g2.dispose();
            }
        };
        b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(190, 36));
        b.addActionListener(e -> navigate(key));
        return b;
    }

    private void setDark(boolean dark) {
        if (dark == Theme.T.dark) return;
        studio.theme = dark ? "dark" : "light";
        Theme.install(dark);
        SwingUtilities.updateComponentTreeUI(this);
        buildShell();
        navigate(section);
        saveTimer.restart();
    }

    private void installShortcuts() {
        JRootPane rp = getRootPane(); int i = 1;
        for (String key : SECTIONS.keySet()) {
            KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_0 + i, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
            rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, "nav-" + key);
            rp.getActionMap().put("nav-" + key, new AbstractAction() { public void actionPerformed(ActionEvent e) { navigate(key); } });
            i++;
        }
    }

    /** Page container that tracks the viewport width so content wraps instead of scrolling sideways. */
    private static final class ScrollPage extends JPanel implements Scrollable {
        ScrollPage() { super(new BorderLayout()); setBorder(new EmptyBorder(26, 30, 60, 30)); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 40; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize(); Container p = getParent();
            return p != null ? new Dimension(p.getWidth(), d.height) : d;
        }
    }

    /** App icon: a teal tile with a rising signal line. */
    public static Image icon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double s = size / 64.0;
        g.setColor(new Color(0x0f6e78)); g.fillRoundRect(0, 0, size, size, (int) (16 * s), (int) (16 * s));
        g.setStroke(new BasicStroke((float) (5 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Color.WHITE);
        Path2D p = new Path2D.Double(); p.moveTo(12 * s, 44 * s); p.lineTo(25 * s, 31 * s); p.lineTo(34 * s, 38 * s); p.lineTo(52 * s, 18 * s);
        g.draw(p);
        g.setColor(new Color(0xe3a849)); g.fillOval((int) (47 * s), (int) (13 * s), (int) (10 * s), (int) (10 * s));
        g.dispose();
        return img;
    }
}

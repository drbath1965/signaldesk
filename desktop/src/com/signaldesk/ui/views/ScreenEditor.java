package com.signaldesk.ui.views;

import com.signaldesk.model.Screen;
import com.signaldesk.signals.AssetType;
import com.signaldesk.store.Universe;
import com.signaldesk.ui.*;
import com.signaldesk.ui.PriceService.Analysis;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Dialog for building or changing a custom screen, with a live count of what passes. */
final class ScreenEditor {
    private final MainFrame app; private final Screen s; private final JDialog d;
    private final JLabel count = new JLabel(" ");
    private ScreenEditor(MainFrame app, Screen s, String title) { this.app = app; this.s = s; d = new JDialog(app, title, true); }

    /** Opens the editor; onSave gets the edited copy only if the person presses Save. */
    static void open(MainFrame app, Screen start, boolean isNew, Consumer<Screen> onSave) {
        ScreenEditor e = new ScreenEditor(app, start.copy(), isNew ? "New screen" : "Edit screen");
        e.show(isNew, onSave);
    }

    private void show(boolean isNew, Consumer<Screen> onSave) {
        Theme t = Theme.T;
        Ui.VBox v = new Ui.VBox(14); v.setBorder(new EmptyBorder(20, 22, 18, 22)); v.setBackground(t.panel); v.setOpaque(true);
        v.add(Ui.h2(isNew ? "New screen" : "Edit screen"), false);
        if (isNew) {
            Ui.HBox tp = new Ui.HBox(8); tp.add(Ui.text("Start from:", Theme.body(12.5f), t.muted));
            for (Screen x : Screen.templates()) tp.add(Ui.small(x.name, () -> { d.dispose(); ScreenEditor.open(app, x, true, onSave); }));
            v.add(tp, false);
        }
        JTextField name = Ui.field(s.name, 24); name.setFont(Theme.body(14f));
        v.add(Ui.labeled("Name", name));

        // where to look
        Ui.HBox lists = new Ui.HBox(14);
        List<String> keys = new ArrayList<>(); for (Universe.List_ u : Universe.ALL) keys.add(u.key()); keys.add("watchlist");
        for (String k : keys) lists.add(check(Screens.listLabel(k), s.lists.contains(k), on -> { if (on) s.lists.add(k); else s.lists.remove(k); }));
        JTextField extra = Ui.field(String.join(", ", s.extra), 30); extra.setFont(Theme.mono(13f));
        extra.setToolTipText("Any tickers, separated by commas or spaces");
        onType(extra, () -> { s.extra.clear(); for (String x : extra.getText().split("[,\\s]+")) if (!x.isBlank()) s.extra.add(x.trim().toUpperCase()); });
        Ui.VBox where = new Ui.VBox(6); where.add(Ui.caps("Look in"), false); where.add(lists, false); where.add(Ui.labeled("Also include these tickers", extra));
        v.add(where);

        Ui.HBox types = new Ui.HBox(14);
        for (AssetType a : new AssetType[]{AssetType.STOCK, AssetType.ETF, AssetType.FUND, AssetType.CRYPTO})
            types.add(check(a.label, s.types.contains(a.name()), on -> { if (on) s.types.add(a.name()); else s.types.remove(a.name()); }));
        Ui.VBox tv = new Ui.VBox(6); tv.add(Ui.caps("Kinds to keep (none ticked keeps all)"), false); tv.add(types, false);
        v.add(tv);

        // rules
        Ui.VBox rules = new Ui.VBox(8); rules.add(Help.label(app, Ui.caps("Rules (leave blank for any)"), "screen"), false);
        JPanel grid = new JPanel(new GridLayout(0, 2, 18, 8)); grid.setOpaque(false);
        grid.add(rule("Score at least", "0–100", s.minScore, false, x -> s.minScore = x));
        grid.add(rule("Dividend yield at least", "%", s.minYield, true, x -> s.minYield = x));
        grid.add(rule("1-year return at least", "%", s.min1y, true, x -> s.min1y = x));
        grid.add(rule("3-month return at least", "%", s.min3m, true, x -> s.min3m = x));
        grid.add(rule("Volatility at most", "%", s.maxVol, true, x -> s.maxVol = x));
        grid.add(rule("Worst 1-year drop at most", "%", s.maxDrop, true, x -> s.maxDrop = x == null ? null : Math.abs(x)));
        rules.add(grid, false);
        rules.add(check("Price above its 200-day average (long-term uptrend)", s.above200, on -> s.above200 = on), false);
        Ui.HBox calls = new Ui.HBox(14); calls.add(Ui.text("Today's call:", Theme.body(13f), t.ink));
        for (String c : new String[]{"BUY", "HOLD", "WAIT", "SELL"})
            calls.add(check(c.charAt(0) + c.substring(1).toLowerCase(), s.calls.contains(c), on -> { if (on) s.calls.add(c); else s.calls.remove(c); }));
        calls.add(Ui.text("(none ticked keeps all)", Theme.body(12f), t.muted));
        rules.add(calls, false);
        v.add(rules);

        JComboBox<Opt> sort = Ui.combo(Screens.SORTS, Opt.find(Screens.SORTS, s.sort));
        sort.addActionListener(e -> s.sort = ((Opt) sort.getSelectedItem()).key());
        Ui.HBox so = new Ui.HBox(10); so.add(Ui.text("Sort results by", Theme.body(13f), t.ink)); so.add(sort);
        v.add(so, false);

        count.setFont(Theme.semibold(13f));
        Ui.HBox foot = new Ui.HBox(10); foot.add(count); foot.glue();
        foot.add(Ui.btn("Cancel", d::dispose));
        Ui.Btn save = Ui.primary("Save screen", () -> {
            s.name = name.getText().isBlank() ? "My screen" : name.getText().trim();
            if (s.lists.isEmpty() && s.extra.isEmpty()) { app.toast("Pick at least one list to look in, or type some tickers.", null); return; }
            d.dispose(); onSave.accept(s);
        });
        foot.add(save);
        v.add(foot);
        update();

        d.setContentPane(v);
        d.getRootPane().registerKeyboardAction(ev -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.pack(); d.setSize(Math.max(760, d.getWidth()), d.getHeight());
        d.setLocationRelativeTo(app);
        SwingUtilities.invokeLater(name::requestFocusInWindow);
        d.setVisible(true);
    }

    /** Recount what passes among symbols with saved prices. */
    private void update() {
        Theme t = Theme.T; int have = 0, pass = 0; List<String> syms = Screens.symbols(app, s);
        for (String x : syms) { Analysis a = app.priceService().analysis(x); if (a == null) continue; have++; if (Screens.fails(a, s).isEmpty()) pass++; }
        count.setText(syms.isEmpty() ? "Nothing to look in yet" : pass + " of " + have + " pass" + (have < syms.size() ? " (" + (syms.size() - have) + " still need prices)" : ""));
        count.setForeground(pass > 0 ? t.buy : t.muted);
    }

    private JComponent check(String label, boolean on, Consumer<Boolean> set) {
        JCheckBox c = new JCheckBox(label, on); c.setOpaque(false); c.setFont(Theme.body(13f)); c.setForeground(Theme.T.ink);
        c.addActionListener(e -> { set.accept(c.isSelected()); update(); });
        return c;
    }
    /** Number rule; percent rules are typed as whole percents and stored as fractions. */
    private JComponent rule(String label, String unit, Double v, boolean pct, Consumer<Double> set) {
        Theme t = Theme.T;
        JTextField f = Ui.field(v == null ? "" : Ui.fmtNum(pct ? v * 100 : v, 1), 6); f.setFont(Theme.mono(13.5f)); f.setHorizontalAlignment(JTextField.RIGHT);
        onType(f, () -> {
            Double x = Ui.parse(f.getText());
            f.setForeground(x == null && !f.getText().isBlank() ? t.sell : t.ink);
            set.accept(x == null ? null : pct ? x / 100 : x);
        });
        JPanel p = new JPanel(new BorderLayout(8, 0)); p.setOpaque(false);
        p.add(Ui.text(label, Theme.body(13f), t.ink), BorderLayout.CENTER);
        Ui.HBox r = new Ui.HBox(4); f.setPreferredSize(new Dimension(70, 30)); r.add(f); JLabel u = Ui.text(unit, Theme.body(12f), t.muted); u.setPreferredSize(new Dimension(40, 16)); r.add(u);
        p.add(r, BorderLayout.EAST);
        return p;
    }
    private void onType(JTextField f, Runnable r) {
        f.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void ch() { r.run(); update(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { ch(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { ch(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { ch(); }
        });
    }
}

package com.signaldesk.ui;

import com.signaldesk.calc.Fmt;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;

/** A spreadsheet-style table bound straight to a list of model objects. */
public final class EditTable<T> extends JTable {
    public enum Kind { TEXT, MONEY, NUMBER, INT, CHECK, CHOICE, CALC, LABEL, DELETE }

    public static final class Col<T> {
        final String name; final Kind kind; final int width;
        Function<T, Object> get; BiConsumer<T, Object> set; Supplier<Object[]> choices;
        Function<T, Color> color; String placeholder = ""; Predicate<T> canDelete;
        private Col(String name, Kind kind, int width) { this.name = name; this.kind = kind; this.width = width; }
        public static <T> Col<T> text(String n, int w, Function<T, String> g, BiConsumer<T, String> s) { Col<T> c = new Col<>(n, Kind.TEXT, w); c.get = g::apply; c.set = (t, v) -> s.accept(t, (String) v); return c; }
        public static <T> Col<T> money(String n, int w, ToDoubleFunction<T> g, ObjDoubleConsumer<T> s) { Col<T> c = new Col<>(n, Kind.MONEY, w); c.get = t -> g.applyAsDouble(t); c.set = (t, v) -> s.accept(t, (Double) v); return c; }
        public static <T> Col<T> number(String n, int w, ToDoubleFunction<T> g, ObjDoubleConsumer<T> s) { Col<T> c = new Col<>(n, Kind.NUMBER, w); c.get = t -> g.applyAsDouble(t); c.set = (t, v) -> s.accept(t, (Double) v); return c; }
        public static <T> Col<T> integer(String n, int w, ToIntFunction<T> g, ObjIntConsumer<T> s) { Col<T> c = new Col<>(n, Kind.INT, w); c.get = t -> (double) g.applyAsInt(t); c.set = (t, v) -> s.accept(t, (int) Math.round((Double) v)); return c; }
        public static <T> Col<T> check(String n, int w, Predicate<T> g, BiConsumer<T, Boolean> s) { Col<T> c = new Col<>(n, Kind.CHECK, w); c.get = g::test; c.set = (t, v) -> s.accept(t, (Boolean) v); return c; }
        public static <T> Col<T> choice(String n, int w, Supplier<Object[]> items, Function<T, Object> g, BiConsumer<T, Object> s) { Col<T> c = new Col<>(n, Kind.CHOICE, w); c.choices = items; c.get = g; c.set = s; return c; }
        public static <T> Col<T> calc(String n, int w, Function<T, String> g, Function<T, Color> color) { Col<T> c = new Col<>(n, Kind.CALC, w); c.get = g::apply; c.color = color; return c; }
        public static <T> Col<T> label(String n, int w, Function<T, String> g, Function<T, Color> color) { Col<T> c = new Col<>(n, Kind.LABEL, w); c.get = g::apply; c.color = color; return c; }
        public static <T> Col<T> delete() { return new Col<>("", Kind.DELETE, 34); }
        public Col<T> placeholder(String p) { placeholder = p; return this; }
        public Col<T> when(Predicate<T> p) { canDelete = p; return this; }
    }

    private static final Icon CHEVRON = new Icon() {
        public int getIconWidth() { return 9; }
        public int getIconHeight() { return 6; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = Ui.aa(g); g2.setColor(Theme.T.muted); g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawPolyline(new int[]{x + 1, x + 4, x + 7}, new int[]{y + 1, y + 4, y + 1}, 3); g2.dispose();
        }
    };
    private final List<T> rows; private final List<Col<T>> cols;
    private final Runnable onChange; private final Consumer<T> onDelete;
    private final String emptyText;
    private int hoverRow = -1;

    @SafeVarargs
    public EditTable(List<T> rows, Runnable onChange, Consumer<T> onDelete, String emptyText, Col<T>... cols) {
        this.rows = rows; this.cols = List.of(cols); this.onChange = onChange; this.onDelete = onDelete; this.emptyText = emptyText;
        setModel(new Model());
        Theme t = Theme.T;
        setRowHeight(32); setShowVerticalLines(false); setShowHorizontalLines(true); setGridColor(t.soft);
        setIntercellSpacing(new Dimension(0, 1)); setFillsViewportHeight(false); setBackground(t.panel); setForeground(t.ink);
        setSelectionBackground(Theme.mix(t.accent, t.panel, .12)); setSelectionForeground(t.ink);
        putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        setSurrendersFocusOnKeystroke(true);
        setAutoResizeMode(AUTO_RESIZE_ALL_COLUMNS);
        getTableHeader().setReorderingAllowed(false); getTableHeader().setResizingAllowed(false);
        getTableHeader().setDefaultRenderer(new HeaderRenderer());
        getTableHeader().setBackground(t.panel);
        getTableHeader().setPreferredSize(new Dimension(10, 30));
        for (int i = 0; i < cols.length; i++) {
            TableColumn tc = getColumnModel().getColumn(i); Col<T> c = cols[i];
            tc.setPreferredWidth(c.width);
            if (c.kind == Kind.DELETE || c.kind == Kind.CHECK) { tc.setMaxWidth(c.width + 30); tc.setMinWidth(c.width); }
            tc.setCellRenderer(new CellRenderer(c));
            TableCellEditor ed = editorFor(c); if (ed != null) tc.setCellEditor(ed);
        }
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int r = rowAtPoint(e.getPoint()), c = columnAtPoint(e.getPoint());
                if (r >= 0 && c >= 0 && EditTable.this.cols.get(c).kind == Kind.DELETE && onDelete != null && r < rows.size()
                        && (EditTable.this.cols.get(c).canDelete == null || EditTable.this.cols.get(c).canDelete.test(rows.get(r)))) {
                    if (isEditing()) getCellEditor().cancelCellEditing();
                    onDelete.accept(rows.get(r));
                }
            }
            @Override public void mouseExited(MouseEvent e) { hoverRow = -1; repaint(); }
        });
        addMouseMotionListener(new MouseAdapter() { @Override public void mouseMoved(MouseEvent e) { int r = rowAtPoint(e.getPoint()); if (r != hoverRow) { hoverRow = r; repaint(); } } });
    }

    public List<T> rows() { return rows; }
    /** Refresh computed columns after other data changed. */
    public void refreshRows() { if (!isEditing()) ((AbstractTableModel) getModel()).fireTableDataChanged(); else repaint(); }
    public void rowsChanged() { if (isEditing()) getCellEditor().cancelCellEditing(); ((AbstractTableModel) getModel()).fireTableDataChanged(); revalidate(); }

    @Override public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width, Math.max(d.height, rows.isEmpty() ? 44 : d.height));
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (rows.isEmpty() && emptyText != null) {
            Graphics2D g2 = Ui.aa(g); g2.setColor(Theme.T.muted); g2.setFont(Theme.body(13.5f));
            g2.drawString(emptyText, 8, 27); g2.dispose();
        }
    }

    /** Header + table, without a scroll pane, so the page itself scrolls. */
    public JComponent wrap() {
        JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false);
        p.add(getTableHeader(), BorderLayout.NORTH); p.add(this, BorderLayout.CENTER);
        return p;
    }

    private final class Model extends AbstractTableModel {
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return cols.size(); }
        public String getColumnName(int c) { return cols.get(c).name; }
        public Class<?> getColumnClass(int c) { return cols.get(c).kind == Kind.CHECK ? Boolean.class : Object.class; }
        public boolean isCellEditable(int r, int c) { Kind k = cols.get(c).kind; return k != Kind.CALC && k != Kind.LABEL && k != Kind.DELETE; }
        public Object getValueAt(int r, int c) { Col<T> col = cols.get(c); return col.get == null ? null : col.get.apply(rows.get(r)); }
        public void setValueAt(Object v, int r, int c) {
            if (r >= rows.size()) return;
            Col<T> col = cols.get(c); if (col.set == null || v == null) return;
            Object old = col.get.apply(rows.get(r));
            if (v.equals(old)) return;
            col.set.accept(rows.get(r), v);
            if (onChange != null) onChange.run();
            fireTableRowsUpdated(0, Math.max(0, rows.size() - 1));
        }
    }

    // ------------------------------------------------------------------ editors
    private TableCellEditor editorFor(Col<T> c) {
        switch (c.kind) {
            case TEXT: {
                JTextField f = new JTextField(); styleEditor(f);
                DefaultCellEditor d = new DefaultCellEditor(f); d.setClickCountToStart(1); return d;
            }
            case MONEY: case NUMBER: case INT: {
                JTextField f = new JTextField(); styleEditor(f); f.setHorizontalAlignment(JTextField.RIGHT); f.setFont(Theme.mono(13.5f));
                return new DefaultCellEditor(f) {
                    { setClickCountToStart(1); }
                    @Override public Component getTableCellEditorComponent(JTable t, Object v, boolean s, int r, int col) {
                        f.setText(v instanceof Double d ? Ui.fmtNum(d, 2) : ""); return f;
                    }
                    @Override public Object getCellEditorValue() { Double d = Ui.parse(f.getText()); return d == null ? 0.0 : d; }
                    @Override public boolean stopCellEditing() {
                        if (!f.getText().isBlank() && Ui.parse(f.getText()) == null) { Toolkit.getDefaultToolkit().beep(); f.setForeground(Theme.T.sell); return false; }
                        f.setForeground(Theme.T.ink); return super.stopCellEditing();
                    }
                };
            }
            case CHOICE: {
                JComboBox<Object> box = new JComboBox<>(); box.setFont(Theme.body(13.5f)); box.setBackground(Theme.T.paper);
                return new DefaultCellEditor(box) {
                    { setClickCountToStart(1); }
                    @Override public Component getTableCellEditorComponent(JTable t, Object v, boolean s, int r, int col) {
                        box.setModel(new DefaultComboBoxModel<>(c.choices.get())); box.setSelectedItem(v);
                        SwingUtilities.invokeLater(() -> { if (box.isShowing()) box.showPopup(); });
                        return box;
                    }
                };
            }
            default: return null;
        }
    }
    private static void styleEditor(JTextField f) {
        Theme t = Theme.T; f.setBackground(t.paper); f.setForeground(t.ink); f.setCaretColor(t.ink); f.setFont(Theme.body(13.5f));
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(t.accent), new EmptyBorder(0, 6, 0, 6)));
    }

    // ------------------------------------------------------------------ renderers
    private final class CellRenderer extends DefaultTableCellRenderer {
        private final Col<T> c; private final JCheckBox box = new JCheckBox();
        CellRenderer(Col<T> c) { this.c = c; box.setHorizontalAlignment(SwingConstants.CENTER); box.setOpaque(true); }
        @Override public Component getTableCellRendererComponent(JTable table, Object v, boolean sel, boolean focus, int r, int col) {
            Theme t = Theme.T;
            Color bg = sel ? getSelectionBackground() : r == hoverRow ? Theme.mix(t.soft, t.panel, .55) : t.panel;
            if (c.kind == Kind.CHECK) { box.setSelected(Boolean.TRUE.equals(v)); box.setBackground(bg); return box; }
            super.getTableCellRendererComponent(table, v, false, false, r, col);
            setBackground(bg); setForeground(t.ink); setBorder(new EmptyBorder(0, 8, 0, 8)); setFont(Theme.body(13.5f));
            setHorizontalAlignment(LEFT); setToolTipText(null); setIcon(null);
            switch (c.kind) {
                case MONEY -> { double d = v instanceof Double x ? x : 0; setText(d == Math.rint(d) ? Fmt.money(d) : Fmt.money2(d)); setFont(Theme.mono(13f)); setHorizontalAlignment(RIGHT); }
                case NUMBER -> { setText(v instanceof Double x ? Ui.fmtNum(x, 4) : ""); setFont(Theme.mono(13f)); setHorizontalAlignment(RIGHT); }
                case INT -> { setText(v instanceof Double x ? String.valueOf(Math.round(x)) : ""); setFont(Theme.mono(13f)); setHorizontalAlignment(RIGHT); }
                case CALC -> { setFont(Theme.mono(13f)); setHorizontalAlignment(RIGHT); Color cc = c.color != null ? c.color.apply(rows.get(r)) : null; setForeground(cc != null ? cc : t.ink); }
                case LABEL -> { Color cc = c.color != null ? c.color.apply(rows.get(r)) : null; setForeground(cc != null ? cc : t.ink); setToolTipText(v == null ? null : v.toString()); }
                case DELETE -> { if (c.canDelete != null && !c.canDelete.test(rows.get(r))) { setText(""); return this; } setText("×"); setFont(Theme.body(17f)); setHorizontalAlignment(CENTER); setForeground(r == hoverRow ? t.sell : t.muted); setToolTipText("Delete row"); }
                case CHOICE -> { setText(v == null ? "" : v.toString()); setIcon(CHEVRON); setHorizontalTextPosition(LEFT); setIconTextGap(7); }
                default -> { String s = v == null ? "" : v.toString(); if (s.isEmpty() && !c.placeholder.isEmpty()) { setText(c.placeholder); setForeground(Theme.alpha(t.muted, .7)); } }
            }
            return this;
        }
    }
    private final class HeaderRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object v, boolean sel, boolean focus, int r, int col) {
            super.getTableCellRendererComponent(table, v == null ? "" : v.toString().toUpperCase(), false, false, r, col);
            Theme t = Theme.T; Kind k = cols.get(col).kind;
            setFont(Theme.caps(10.5f)); setForeground(t.muted); setBackground(t.panel);
            setHorizontalAlignment(k == Kind.MONEY || k == Kind.NUMBER || k == Kind.INT || k == Kind.CALC ? RIGHT : k == Kind.CHECK ? CENTER : LEFT);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, t.line), new EmptyBorder(0, 8, 0, 8)));
            return this;
        }
    }
}

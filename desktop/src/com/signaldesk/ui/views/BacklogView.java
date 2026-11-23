package com.signaldesk.ui.views;

import com.signaldesk.calc.Dates;
import com.signaldesk.model.BacklogItem;
import com.signaldesk.model.BacklogItem.Priority;
import com.signaldesk.model.BacklogItem.Status;
import com.signaldesk.ui.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class BacklogView extends View {
    private static String filter = "All areas";
    private final Slot kpis = new Slot(), board = new Slot();
    public BacklogView(MainFrame app) { super(app); }

    private List<String> areas() {
        List<String> a = new ArrayList<>(Arrays.asList(BacklogItem.AREAS));
        for (BacklogItem b : s().backlog) if (!a.contains(b.area)) a.add(b.area);
        return a;
    }

    @Override public JComponent build() {
        Ui.VBox root = new Ui.VBox(20);
        root.add(Kit.head("Project", "Backlog", "What's planned for this studio. Add ideas, set priorities, and move each item across as work happens.", null));
        root.add(kpis);
        Ui.Card fc = new Ui.Card(12);
        JTextField title = Ui.field("", 30);
        JComboBox<String> area = Ui.combo(BacklogItem.AREAS, "Studio");
        JComboBox<Priority> pri = Ui.combo(Priority.values(), Priority.MED);
        Runnable add = () -> {
            String tt = title.getText().trim(); if (tt.isEmpty()) { title.requestFocusInWindow(); return; }
            BacklogItem b = new BacklogItem(); b.title = tt; b.area = (String) area.getSelectedItem(); b.priority = (Priority) pri.getSelectedItem(); b.created = Dates.today().toString();
            s().backlog.add(b); app.changed(); title.setText(""); title.requestFocusInWindow(); app.toast("Added to backlog", null);
        };
        title.addActionListener(e -> add.run());
        JPanel form = new JPanel(new GridBagLayout()); form.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints(); g.gridy = 0; g.anchor = GridBagConstraints.SOUTHWEST; g.insets = new Insets(0, 0, 0, 10); g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1; form.add(Ui.labeled("New item", title), g);
        g.weightx = 0; area.setPreferredSize(new Dimension(160, 32)); form.add(Ui.labeled("Area", area), g);
        pri.setPreferredSize(new Dimension(120, 32)); form.add(Ui.labeled("Priority", pri), g);
        g.insets = new Insets(0, 0, 0, 0); form.add(Ui.primary("Add item", add), g);
        fc.add(form);
        Ui.HBox fr = new Ui.HBox(8);
        fr.grow(Ui.hint("Saved with the rest of your studio in the SignalDesk folder."));
        List<String> fo = new ArrayList<>(); fo.add("All areas"); fo.addAll(areas());
        JComboBox<String> fsel = Ui.combo(fo.toArray(new String[0]), fo.contains(filter) ? filter : "All areas");
        fsel.addActionListener(e -> { filter = (String) fsel.getSelectedItem(); refresh(); });
        fr.add(Ui.muted("Show")); fr.add(fsel);
        fc.add(fr);
        root.add(fc);
        root.add(board);
        refresh();
        return root;
    }

    @Override public void refresh() {
        Theme t = Theme.T; List<BacklogItem> all = s().backlog;
        long open = all.stream().filter(b -> b.status != Status.DONE).count(), done = all.size() - open;
        long hi = all.stream().filter(b -> b.status != Status.DONE && b.priority == Priority.HIGH).count();
        kpis.set(Ui.kpis(
            new Ui.Kpi("Open items", String.valueOf(open), all.stream().filter(b -> b.status == Status.BACKLOG).count() + " in backlog · " + all.stream().filter(b -> b.status == Status.NEXT).count() + " up next"),
            new Ui.Kpi("In progress", String.valueOf(all.stream().filter(b -> b.status == Status.DOING).count()), "being built now"),
            new Ui.Kpi("High priority open", String.valueOf(hi), hi > 0 ? t.sell : null, all.stream().filter(b -> b.status != Status.DONE && b.priority == Priority.MED).count() + " medium · " + all.stream().filter(b -> b.status != Status.DONE && b.priority == Priority.LOW).count() + " low"),
            new Ui.Kpi("Done", done + " of " + all.size(), all.isEmpty() ? "" : Math.round(done * 100.0 / all.size()) + "% complete")));
        if (all.isEmpty()) {
            Ui.Card c = Kit.card("Your backlog is empty", null);
            c.add(Ui.para("Add the first item above. Each one starts in Backlog; use the arrows on a card to move it to Up next, In progress and Done."));
            board.set(c); return;
        }
        JPanel cols = new JPanel(new GridLayout(1, 4, 14, 0)); cols.setOpaque(false);
        for (Status st : Status.values()) {
            List<BacklogItem> items = new ArrayList<>();
            for (BacklogItem b : all) if (b.status == st && (filter.equals("All areas") || b.area.equals(filter))) items.add(b);
            if (st == Status.DONE) items.sort(Comparator.comparing((BacklogItem b) -> b.doneAt).reversed());
            else items.sort(Comparator.comparingInt((BacklogItem b) -> b.priority.ordinal()).thenComparing(b -> b.created));
            Ui.VBox col = new Ui.VBox(8) {
                @Override protected void paintComponent(Graphics g) { Graphics2D g2 = Ui.aa(g); g2.setColor(Theme.T.soft); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14); g2.dispose(); }
            };
            col.setBorder(new EmptyBorder(12, 10, 12, 10));
            Ui.HBox h = new Ui.HBox(8);
            Ui.VBox ht = new Ui.VBox(0); ht.add(Ui.h3(st.label), false); ht.add(Ui.text(st.sub, Theme.body(12f), t.muted), false);
            h.grow(ht); h.add(Ui.mono(String.valueOf(items.size()), 12.5f, t.muted));
            h.setBorder(new EmptyBorder(0, 4, 4, 4)); col.add(h);
            if (items.isEmpty()) col.add(Ui.text(filter.equals("All areas") ? "Nothing here yet." : "Nothing in this area.", Theme.body(13f), t.muted));
            for (BacklogItem b : items) col.add(card(b));
            col.end();
            cols.add(col);
        }
        board.set(cols);
    }

    private JComponent card(BacklogItem b) {
        Theme t = Theme.T; boolean done = b.status == Status.DONE;
        Ui.Card c = new Ui.Card(8); c.setBorder(new EmptyBorder(11, 13, 11, 13));
        Ui.HBox top = new Ui.HBox(6);
        JTextArea title = Ui.para(b.title, Theme.semibold(13.5f), done ? t.muted : t.ink);
        top.grow(title);
        c.add(top);
        if (!b.detail.isBlank()) c.add(Ui.para(b.detail, Theme.body(12.5f), t.muted));
        Ui.HBox pills = new Ui.HBox(6);
        pills.add(Ui.pill(b.priority.label, b.priority == Priority.HIGH ? Ui.Tone.NEG : b.priority == Priority.MED ? Ui.Tone.WARN : Ui.Tone.NEU));
        pills.add(Ui.pill(b.area, Ui.Tone.NEU));
        c.add(pills);
        Ui.HBox foot = new Ui.HBox(4);
        String when = done && !b.doneAt.isBlank() ? "Done " + Dates.dayShort(b.doneAt) : !b.created.isBlank() ? "Added " + Dates.dayShort(b.created) : "";
        foot.grow(Ui.mono(when, 11.5f, t.muted));
        int si = b.status.ordinal();
        Ui.Btn back = Ui.small("‹", () -> move(b, -1)); back.setEnabled(si > 0); back.setToolTipText(si > 0 ? "Move to " + Status.values()[si - 1].label : null);
        Ui.Btn fwd = Ui.small("›", () -> move(b, 1)); fwd.setEnabled(si < 3); fwd.setToolTipText(si < 3 ? "Move to " + Status.values()[si + 1].label : null);
        foot.add(back); foot.add(Ui.small("Edit", () -> edit(b))); foot.add(fwd);
        c.add(foot);
        return c;
    }

    private void move(BacklogItem b, int d) {
        int j = Math.max(0, Math.min(3, b.status.ordinal() + d)); if (j == b.status.ordinal()) return;
        b.status = Status.values()[j]; b.doneAt = b.status == Status.DONE ? Dates.today().toString() : "";
        app.changed(); app.toast("Moved to " + b.status.label, null);
    }

    private void edit(BacklogItem b) {
        JTextField title = Ui.field(b.title, 34);
        JTextArea detail = new JTextArea(b.detail, 5, 34); detail.setLineWrap(true); detail.setWrapStyleWord(true); Ui.styleField(detail);
        JComboBox<String> area = Ui.combo(areas().toArray(new String[0]), b.area);
        JComboBox<Priority> pri = Ui.combo(Priority.values(), b.priority);
        JComboBox<Status> st = Ui.combo(Status.values(), b.status);
        Ui.VBox form = new Ui.VBox(12);
        form.add(Ui.labeled("Title", title));
        JScrollPane ds = new JScrollPane(detail); ds.setBorder(null); form.add(Ui.labeled("Details", ds));
        form.add(Ui.cols(10, Ui.labeled("Area", area), Ui.labeled("Priority", pri), Ui.labeled("Status", st)));
        form.setPreferredSize(new Dimension(460, form.getPreferredSize().height));
        Object[] opts = {"Save", "Delete item", "Cancel"};
        int r = JOptionPane.showOptionDialog(app, form, "Edit backlog item", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]);
        if (r == 0) {
            if (title.getText().isBlank()) { app.toast("A backlog item needs a title.", null); return; }
            b.title = title.getText().trim(); b.detail = detail.getText().trim(); b.area = (String) area.getSelectedItem(); b.priority = (Priority) pri.getSelectedItem();
            Status ns = (Status) st.getSelectedItem();
            if (ns != b.status) { b.status = ns; b.doneAt = ns == Status.DONE ? Dates.today().toString() : ""; }
            app.changed(); app.toast("Saved", null);
        } else if (r == 1) {
            int i = s().backlog.indexOf(b); s().backlog.remove(b); app.changed();
            app.toast("Deleted “" + b.title + "”", () -> s().backlog.add(Math.min(i, s().backlog.size()), b));
        }
    }
}

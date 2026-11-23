package com.signaldesk.ui.views;

import com.signaldesk.calc.*;
import com.signaldesk.model.*;
import com.signaldesk.ui.*;
import com.signaldesk.ui.EditTable.Col;
import java.awt.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;

public final class BudgetView extends View {
    private static String month = null;
    private final Slot kpis = new Slot(), split = new Slot(), expenses = new Slot();
    private final Map<Group, EditTable<Category>> catTables = new EnumMap<>(Group.class);
    private final Map<Group, JLabel> groupTotals = new EnumMap<>(Group.class);
    private JLabel incomeTotal;

    public BudgetView(MainFrame app) { super(app); if (month == null) month = Dates.cur(); }

    private BudgetCalc calc() { return BudgetCalc.of(s(), month); }

    @Override public JComponent build() {
        Ui.VBox root = new Ui.VBox(20);
        Ui.HBox nav = new Ui.HBox(6);
        nav.add(Ui.small("‹", () -> { month = Dates.add(month, -1); app.rebuild(); }));
        if (!month.equals(Dates.cur())) nav.add(Ui.small("This month", () -> { month = Dates.cur(); app.rebuild(); }));
        nav.add(Ui.small("›", () -> { month = Dates.add(month, 1); app.rebuild(); }));
        root.add(Kit.head("Budget", Dates.label(month), null, nav));
        root.add(kpis);
        Ui.Card sc = Kit.card("Where the plan goes", Ui.hint("A common guide: 50% needs, 30% wants, 20% savings and debt payoff"));
        sc.add(split); root.add(sc);

        // Categories by group
        Ui.Card cc = Kit.card("Categories", Ui.hint("Plan each month; spending fills in from expenses and paid bills"));
        for (Group g : Group.values()) {
            Ui.HBox gh = new Ui.HBox(8);
            gh.add(Ui.dot(Theme.T.series[g == Group.SAVINGS ? 2 : g.ordinal()])); gh.grow(Ui.h3(g.label));
            JLabel tot = Ui.mono("", 12.5f, Theme.T.muted); groupTotals.put(g, tot); gh.add(tot);
            cc.add(gh);
            List<Category> rows = new ArrayList<>(); for (Category c : s().cats) if (Group.of(c.group) == g) rows.add(c);
            EditTable<Category> et = new EditTable<>(rows, this::edited, this::deleteCat, "No categories in this group.",
                Col.<Category>text("Category", 170, c -> c.name, (c, v) -> c.name = v),
                Col.<Category>choice("Group", 110, () -> Group.values(), c -> Group.of(c.group), (c, v) -> { c.group = ((Group) v).key; SwingUtilities.invokeLater(app::rebuild); }),
                Col.<Category>money("Planned", 100, c -> c.planned, (c, v) -> c.planned = v),
                Col.<Category>calc("Spent", 100, c -> Fmt.money(calc().spentIn(c.id)), null),
                Col.<Category>calc("Left", 100, c -> Fmt.money(c.planned - calc().spentIn(c.id)), c -> c.planned - calc().spentIn(c.id) < 0 ? Theme.T.sell : null),
                Col.delete());
            catTables.put(g, et);
            cc.add(et.wrap());
        }
        cc.add(Ui.small("+ Add category", () -> { s().cats.add(new Category("", "wants", 0)); app.changed(); app.rebuild(); }), false);

        Ui.Card ic = Kit.card("Monthly income", null);
        EditTable<Income> it = new EditTable<>(s().income, this::edited, x -> { int i = s().income.indexOf(x); s().income.remove(x); app.changed(); app.rebuild(); app.toast("Deleted " + label(x.name), () -> s().income.add(i, x)); }, "No income yet.",
            Col.<Income>text("Income source", 170, x -> x.name, (x, v) -> x.name = v).placeholder("e.g. Paycheck (net)"),
            Col.<Income>money("Monthly", 100, x -> x.amount, (x, v) -> x.amount = v), Col.delete());
        ic.add(it.wrap());
        incomeTotal = Ui.mono("", 14f, Theme.T.ink);
        Ui.HBox tot = new Ui.HBox(8); tot.grow(Ui.h3("Total")); tot.add(incomeTotal); ic.add(tot);
        ic.add(Ui.small("+ Add income", () -> { s().income.add(new Income("", 0)); app.changed(); app.rebuild(); }), false);
        ic.add(Ui.hint("Use take-home pay, after taxes and payroll deductions."));
        root.add(Ui.rail(Ui.top(cc), ic, 340));

        root.add(expenseCard());
        refresh();
        return root;
    }
    private static String label(String n) { return n == null || n.isBlank() ? "row" : n; }

    private void edited() { app.changed(); }
    private void deleteCat(Category c) {
        int i = s().cats.indexOf(c); s().cats.remove(c); app.changed(); app.rebuild();
        app.toast("Deleted " + label(c.name), () -> s().cats.add(i, c));
    }

    private JComponent expenseCard() {
        Theme t = Theme.T;
        Ui.Card c = new Ui.Card();
        BudgetCalc bt = calc();
        Ui.HBox h = new Ui.HBox(12); h.grow(Ui.h2("Expenses in " + Dates.labelShort(month))); h.add(Ui.muted(bt.rows.size() + " entries · " + Fmt.money(bt.spent))); c.add(h);

        JTextField date = Ui.field(month.equals(Dates.cur()) ? Dates.today().toString() : Dates.iso(month, 1), 9);
        JTextField desc = Ui.field("", 20);
        JComboBox<Category> cat = Ui.combo(s().cats.toArray(new Category[0]), null);
        JTextField amt = Ui.field("", 7); amt.setHorizontalAlignment(JTextField.RIGHT); amt.setFont(Theme.mono(13.5f));
        Runnable add = () -> {
            Double a = Ui.parse(amt.getText());
            if (a == null || a == 0) { amt.requestFocusInWindow(); app.toast("Enter an amount for the expense.", null); return; }
            String d = date.getText().trim();
            try { java.time.LocalDate.parse(d); } catch (Exception ex) { date.requestFocusInWindow(); app.toast("Use a date like " + Dates.today() + ".", null); return; }
            Category k = (Category) cat.getSelectedItem();
            s().tx.add(new Transaction(d, desc.getText().isBlank() ? "Expense" : desc.getText().trim(), k == null ? "" : k.id, Math.round(a * 100) / 100.0));
            month = d.substring(0, 7); app.changed(); app.rebuild(); app.toast("Expense added", null);
        };
        amt.addActionListener(e -> add.run()); desc.addActionListener(e -> add.run());
        JPanel form = new JPanel(new GridBagLayout()); form.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints(); g.gridy = 0; g.anchor = GridBagConstraints.SOUTHWEST; g.insets = new Insets(0, 0, 0, 10); g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 0; form.add(Ui.labeled("Date", date), g);
        g.weightx = 1; form.add(Ui.labeled("Description", desc), g);
        g.weightx = 0; cat.setPreferredSize(new Dimension(180, 32)); form.add(Ui.labeled("Category", cat), g);
        amt.setPreferredSize(new Dimension(110, 32)); form.add(Ui.labeled("Amount", amt), g);
        g.insets = new Insets(0, 0, 0, 0); form.add(Ui.primary("Add expense", add), g);
        c.add(form);

        List<BudgetCalc.Row> rows = new ArrayList<>(bt.rows);
        EditTable<BudgetCalc.Row> et = new EditTable<>(rows, null, r -> {
            if (r.fromBill()) { app.toast("Bill payments come from the Bills page. Untick it there instead.", null); return; }
            Transaction tx = s().tx.stream().filter(x -> x.id.equals(r.id())).findFirst().orElse(null);
            if (tx == null) return; int i = s().tx.indexOf(tx); s().tx.remove(tx); app.changed(); app.rebuild();
            app.toast("Deleted " + tx.desc, () -> s().tx.add(i, tx));
        }, "No expenses recorded for this month yet. Add one above.",
            Col.<BudgetCalc.Row>label("Date", 90, r -> Dates.dayShort(r.date()), r -> t.muted),
            Col.<BudgetCalc.Row>label("Description", 260, r -> r.desc() + (r.fromBill() ? "   · bill" : ""), r -> r.fromBill() ? t.accent : null),
            Col.<BudgetCalc.Row>label("Category", 160, r -> s().categoryName(r.cat()), r -> t.muted),
            Col.<BudgetCalc.Row>calc("Amount", 110, r -> Fmt.money2(r.amount()), null),
            Col.<BudgetCalc.Row>delete().when(r -> !r.fromBill()));
        c.add(et.wrap());
        c.add(Ui.hint("Bills you mark paid, and autopay bills on their due date, appear here automatically."));
        return c;
    }

    @Override public void refresh() {
        Theme t = Theme.T; BudgetCalc bt = calc();
        double un = bt.unassigned();
        kpis.set(Ui.kpis(
            new Ui.Kpi("Income", Fmt.money(bt.income), "per month, take-home"),
            new Ui.Kpi("Planned", Fmt.money(bt.planned), bt.income > 0 ? Fmt.pctu(bt.planned / bt.income) + " of income" : "—"),
            new Ui.Kpi("Left to assign", Fmt.money(un), Math.abs(un) < 1 ? t.buy : un < 0 ? t.sell : t.warn, Math.abs(un) < 1 ? "Every dollar has a job" : un < 0 ? "Planned more than income" : "Give these dollars a category"),
            new Ui.Kpi("Spent", Fmt.money(bt.spent), bt.left() < 0 ? t.sell : null, Fmt.money(bt.left()) + " left of plan")));
        // 50/30/20 split
        Ui.VBox sp = new Ui.VBox(12);
        double tot = Math.max(1, bt.planned);
        JComponent bar = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g); int w = getWidth(), h = 12; double x = 0;
                g2.setColor(t.soft); g2.fillRoundRect(0, 0, w, h, h, h);
                Shape clip = new java.awt.geom.RoundRectangle2D.Double(0, 0, w, h, h, h); g2.setClip(clip);
                for (Group gr : Group.values()) { double ww = bt.groups.get(gr).planned / tot * w; g2.setColor(t.series[gr == Group.SAVINGS ? 2 : gr.ordinal()]); g2.fill(new java.awt.geom.Rectangle2D.Double(x, 0, ww, h)); x += ww; }
                g2.dispose();
            }
        };
        bar.setPreferredSize(new Dimension(100, 12)); sp.add(bar);
        JComponent[] cells = new JComponent[3]; int i = 0;
        for (Group gr : Group.values()) {
            double p = bt.income > 0 ? bt.groups.get(gr).planned / bt.income * 100 : 0;
            boolean off = Math.abs(p - gr.guidePct) > 8;
            Ui.VBox v = new Ui.VBox(4);
            v.add(Kit.legend(gr.label, t.series[gr == Group.SAVINGS ? 2 : gr.ordinal()]), false);
            Ui.HBox n = new Ui.HBox(8); n.add(Ui.mono(Math.round(p) + "%", 18f, t.ink)); n.add(Ui.hint("of income · guide " + gr.guidePct + "%")); v.add(n, false);
            v.add(off ? Ui.pill((p > gr.guidePct ? "Above" : "Below") + " guide", Ui.Tone.WARN) : Ui.pill("Near guide", Ui.Tone.POS), false);
            cells[i++] = v;
        }
        sp.add(Ui.cols(14, cells));
        split.set(sp);
        for (Group g : Group.values()) {
            EditTable<Category> et = catTables.get(g); if (et != null) et.refreshRows();
            JLabel l = groupTotals.get(g); if (l != null) l.setText(Fmt.money(bt.groups.get(g).planned) + " planned");
        }
        if (incomeTotal != null) incomeTotal.setText(Fmt.money(bt.income));
    }
}

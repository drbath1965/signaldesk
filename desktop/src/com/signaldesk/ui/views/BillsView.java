package com.signaldesk.ui.views;

import com.signaldesk.calc.*;
import com.signaldesk.model.*;
import com.signaldesk.ui.*;
import com.signaldesk.ui.EditTable.Col;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class BillsView extends View {
    static final Opt[] FREQ = {new Opt("monthly", "Monthly"), new Opt("quarterly", "Quarterly"), new Opt("annual", "Yearly")};
    static final Opt[] MONTHS = new Opt[12];
    static { for (int i = 0; i < 12; i++) MONTHS[i] = new Opt(String.valueOf(i + 1), java.time.Month.of(i + 1).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)); }

    private final Slot kpis = new Slot(), cal = new Slot(), due = new Slot();
    private JLabel avg;
    private EditTable<Bill> table;

    public BillsView(MainFrame app) { super(app); }

    static Ui.Tone tone(BillCalc.Kind k) {
        return switch (k) { case PAID -> Ui.Tone.POS; case OVERDUE -> Ui.Tone.NEG; case DUE_SOON -> Ui.Tone.WARN; case AUTO -> Ui.Tone.ACC; default -> Ui.Tone.NEU; };
    }

    @Override public JComponent build() {
        String cur = Dates.cur();
        Ui.VBox root = new Ui.VBox(20);
        root.add(Kit.head("Bills", Dates.label(cur), "Tick a bill when you pay it. Autopay bills count as paid on their due date. Paid bills flow into your budget automatically.", null));
        root.add(kpis);
        Theme t = Theme.T;
        Ui.Card cc = Kit.card("This month's due dates", Kit.legend("Paid", t.buy, "Due soon", t.warn, "Overdue", t.sell, "Upcoming", t.muted));
        cc.add(cal); root.add(cc);
        Ui.Card dc = Kit.card("Due this month", null); dc.add(due); root.add(dc);

        avg = Ui.muted("");
        Ui.Card ac = Kit.card("All recurring bills", avg);
        List<Bill> rows = new ArrayList<>(s().bills);
        rows.sort(Comparator.<Bill, Boolean>comparing(b -> !BillCalc.dueIn(b, cur)).thenComparingInt(b -> BillCalc.dueDay(b, cur)));
        table = new EditTable<>(rows, app::changed, b -> {
            int i = s().bills.indexOf(b); s().bills.remove(b); app.changed(); app.rebuild();
            app.toast("Deleted " + (b.name.isBlank() ? "bill" : b.name), () -> s().bills.add(i, b));
        }, "No bills yet. Add rent, utilities, insurance and subscriptions.",
            Col.<Bill>text("Bill", 170, b -> b.name, (b, v) -> b.name = v).placeholder("e.g. Electric"),
            Col.<Bill>money("Amount", 96, b -> b.amount, (b, v) -> b.amount = v),
            Col.<Bill>integer("Due day", 74, b -> b.day, (b, v) -> b.day = Math.max(1, Math.min(31, v))),
            Col.<Bill>choice("Repeats", 104, () -> FREQ, b -> Opt.find(FREQ, b.freq), (b, v) -> b.freq = ((Opt) v).key()),
            Col.<Bill>choice("First month", 96, () -> MONTHS, b -> Opt.find(MONTHS, String.valueOf(b.mon)), (b, v) -> b.mon = Integer.parseInt(((Opt) v).key())),
            Col.<Bill>choice("Budget category", 160, () -> s().cats.toArray(), b -> s().category(b.cat), (b, v) -> b.cat = ((Category) v).id),
            Col.<Bill>check("Autopay", 84, b -> b.autopay, (b, v) -> b.autopay = v),
            Col.delete());
        ac.add(table.wrap());
        ac.add(Ui.small("+ Add bill", () -> {
            Bill b = new Bill("", 0, 1, "monthly", Dates.month(cur), s().cats.isEmpty() ? "" : s().cats.get(0).id, false);
            s().bills.add(b); app.changed(); app.rebuild();
        }), false);
        ac.add(Ui.hint("“First month” only matters for quarterly and yearly bills: it is the first month of the year the bill comes due."));
        root.add(ac);
        refresh();
        return root;
    }

    @Override public void refresh() {
        String cur = Dates.cur(); Theme t = Theme.T;
        List<Bill> dueBills = s().bills.stream().filter(b -> BillCalc.dueIn(b, cur)).sorted(Comparator.comparingInt(b -> BillCalc.dueDay(b, cur))).toList();
        double total = dueBills.stream().mapToDouble(b -> b.amount).sum();
        double paid = dueBills.stream().filter(b -> BillCalc.isPaid(b, cur)).mapToDouble(b -> b.amount).sum();
        long over = dueBills.stream().filter(b -> BillCalc.status(b, cur).kind() == BillCalc.Kind.OVERDUE).count();
        double avgM = s().bills.stream().mapToDouble(BillCalc::monthlyEquiv).sum();
        long autoN = s().bills.stream().filter(b -> b.autopay).count();
        kpis.set(Ui.kpis(
            new Ui.Kpi("Due this month", Fmt.money(total), Fmt.money(paid) + " paid so far"),
            new Ui.Kpi("Still to pay", Fmt.money(total - paid), over > 0 ? t.sell : null, over > 0 ? over + " overdue" : "Nothing overdue"),
            new Ui.Kpi("Average per month", Fmt.money(avgM), "quarterly and yearly bills spread out"),
            new Ui.Kpi("On autopay", autoN + " of " + s().bills.size(), Fmt.money(s().bills.stream().filter(b -> b.autopay).mapToDouble(BillCalc::monthlyEquiv).sum()) + " a month")));
        cal.set(new Calendar(s(), cur));
        Ui.VBox list = new Ui.VBox(0);
        if (dueBills.isEmpty()) list.add(Kit.empty("No bills are due this month."));
        int k = 0;
        for (Bill b : dueBills) {
            BillCalc.Status st = BillCalc.status(b, cur);
            JCheckBox box = new JCheckBox(); box.setOpaque(false); box.setSelected(BillCalc.isPaid(b, cur));
            boolean auto = b.autopay && !Boolean.TRUE.equals(b.paid.get(cur));
            box.setEnabled(!auto); if (auto) box.setToolTipText("Autopay");
            box.addActionListener(e -> { if (box.isSelected()) b.paid.put(cur, true); else b.paid.remove(cur); app.changed(); });
            Ui.HBox sub = new Ui.HBox(8); sub.add(Ui.muted(Dates.dayShort(Dates.iso(cur, BillCalc.dueDay(b, cur))))); sub.add(Ui.pill(st.label(), tone(st.kind())));
            list.add(Ui.listRow(box, b.name.isBlank() ? "(unnamed bill)" : b.name, sub, Ui.mono(Fmt.money2(b.amount), 13.5f, t.ink), k++ > 0));
        }
        due.set(list);
        if (avg != null) avg.setText(Fmt.money(avgM) + " a month on average");
        if (table != null) table.refreshRows();
    }

    /** Month grid with each bill on its due date, colored by status. */
    static final class Calendar extends JComponent {
        private final Studio s; private final String ym;
        Calendar(Studio s, String ym) { this.s = s; this.ym = ym; int weeks = weeks(); setPreferredSize(new Dimension(600, 24 + weeks * 66)); }
        private int first() { return Dates.parse(ym).atDay(1).getDayOfWeek().getValue() % 7; }
        private int weeks() { return (int) Math.ceil((first() + Dates.dim(ym)) / 7.0); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Ui.aa(g); Theme t = Theme.T;
            int w = getWidth(), gap = 5; double cw = (w - gap * 6) / 7.0; int ch = 62;
            String[] dow = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
            g2.setFont(Theme.caps(10.5f)); FontMetrics fm = g2.getFontMetrics();
            for (int i = 0; i < 7; i++) { g2.setColor(t.muted); g2.drawString(dow[i], (float) (i * (cw + gap) + (cw - fm.stringWidth(dow[i])) / 2), 14); }
            int f = first(), n = Dates.dim(ym), today = ym.equals(Dates.cur()) ? Dates.todayDay() : -1;
            for (int d = 1; d <= n; d++) {
                int idx = f + d - 1, col = idx % 7, row = idx / 7;
                double x = col * (cw + gap); int y = 24 + row * (ch + 4);
                g2.setColor(t.panel); g2.fillRoundRect((int) x, y, (int) cw, ch, 10, 10);
                g2.setColor(d == today ? t.accent : t.soft); g2.setStroke(new BasicStroke(d == today ? 2f : 1f));
                g2.drawRoundRect((int) x, y, (int) cw - 1, ch - 1, 10, 10);
                g2.setFont(Theme.mono(11f)); g2.setColor(t.muted); g2.drawString(String.valueOf(d), (float) x + 7, y + 15);
                int cy = y + 21;
                for (Bill b : s.bills) {
                    if (!BillCalc.dueIn(b, ym) || BillCalc.dueDay(b, ym) != d) continue;
                    if (cy + 16 > y + ch) break;
                    BillCalc.Kind k = BillCalc.status(b, ym).kind();
                    Color bg, fg;
                    switch (k) { case PAID -> { bg = t.buyBg; fg = t.buy; } case OVERDUE -> { bg = t.sellBg; fg = t.sell; } case DUE_SOON -> { bg = t.warnBg; fg = t.warn; } default -> { bg = t.soft; fg = t.ink; } }
                    g2.setColor(bg); g2.fillRoundRect((int) x + 4, cy, (int) cw - 8, 16, 6, 6);
                    g2.setFont(Theme.medium(11f)); g2.setColor(fg); FontMetrics f2 = g2.getFontMetrics();
                    String nm = b.name.isBlank() ? "Bill" : b.name; int max = (int) cw - 16;
                    while (f2.stringWidth(nm) > max && nm.length() > 2) nm = nm.substring(0, nm.length() - 2) + "…";
                    if (f2.stringWidth(nm) > max) nm = "";
                    g2.drawString(nm, (float) x + 8, cy + 12);
                    cy += 19;
                }
            }
            g2.dispose();
        }
    }
}

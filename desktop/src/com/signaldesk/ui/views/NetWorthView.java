package com.signaldesk.ui.views;

import com.signaldesk.calc.*;
import com.signaldesk.model.*;
import com.signaldesk.ui.*;
import com.signaldesk.ui.EditTable.Col;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

public final class NetWorthView extends View {
    private final Slot top = new Slot();
    private JLabel assetsLbl, pfLbl;
    public NetWorthView(MainFrame app) { super(app); }

    @Override public JComponent build() {
        Theme t = Theme.T;
        Ui.VBox root = new Ui.VBox(20);
        root.add(Kit.head("Net worth", "What you own, minus what you owe", null, null));
        root.add(top);
        assetsLbl = Ui.mono("", 13.5f, t.muted);
        Ui.Card ac = Kit.card("Assets", assetsLbl);
        Opt[] types = new Opt[AccountType.values().length]; int i = 0;
        for (AccountType at : AccountType.values()) types[i++] = new Opt(at.key, at.label);
        EditTable<Account> et = new EditTable<>(s().accounts, app::changed, a -> {
            int k = s().accounts.indexOf(a); s().accounts.remove(a); app.changed(); app.rebuild();
            app.toast("Deleted " + (a.name.isBlank() ? "account" : a.name), () -> s().accounts.add(k, a));
        }, "Add checking, savings, retirement accounts and property.",
            Col.<Account>text("Account or asset", 200, a -> a.name, (a, v) -> a.name = v).placeholder("e.g. Checking"),
            Col.<Account>choice("Type", 190, () -> types, a -> Opt.find(types, a.type), (a, v) -> a.type = ((Opt) v).key()),
            Col.<Account>money("Balance / value", 130, a -> a.balance, (a, v) -> a.balance = v),
            Col.delete());
        ac.add(et.wrap());
        ac.add(Ui.small("+ Add account", () -> { s().accounts.add(new Account("", "cash", 0)); app.changed(); app.rebuild(); }), false);
        pfLbl = Ui.mono("", 13.5f, t.ink);
        ac.add(Ui.listRow(Ui.dot(t.series[2]), "Investment portfolio", Ui.muted("From the Investments page"), pfLbl, true));
        ac.add(Ui.hint("Retirement balances feed the Retirement planner. Cash accounts measure your emergency fund."));

        Ui.Card dc = Kit.card("Debts", Ui.link("Edit debts", () -> app.navigate("debt")));
        Ui.VBox dl = new Ui.VBox(0); int k = 0;
        for (Debt d : s().debts) dl.add(Ui.listRow(Ui.dot(t.sell), d.name.isBlank() ? "Unnamed debt" : d.name, Ui.muted(Ui.fmtNum(d.apr, 2) + "% APR"), Ui.mono(Fmt.money(d.balance), 13.5f, t.ink), k++ > 0));
        if (s().debts.isEmpty()) dl.add(Kit.empty("No debts recorded."));
        else dl.add(Ui.listRow(null, "Total owed", null, Ui.text(Fmt.money(s().debts.stream().mapToDouble(d -> d.balance).sum()), Theme.monoBold(13.5f), t.ink), true));
        dc.add(dl);
        root.add(Ui.rail(Ui.top(ac), dc, 340));
        refresh();
        return root;
    }

    @Override public void refresh() {
        Theme t = Theme.T; var s = s(); NetWorth nw = NetWorth.of(s);
        Ui.Card nc = Kit.capsCard("Net worth", null);
        nc.add(Ui.big(Fmt.money(nw.net())));
        List<String> months = new ArrayList<>(s.nwHist.keySet());
        if (months.size() > 25) months = months.subList(months.size() - 25, months.size());
        if (months.size() > 1) nc.add(new Charts.Line(Kit.monthTicks(months), Fmt::compact, 170).xTicks(5)
            .add(new Charts.Series(months.stream().mapToDouble(m -> s.nwHist.get(m)).toArray(), t.accent, 2f, false, true, true)));
        else nc.add(Ui.hint("A point is recorded each month you update the studio."));

        String[] names = {"Cash", "Retirement", "Investments", "Home & vehicles", "Other"};
        double[] vals = {nw.type("cash"), nw.type("trad") + nw.type("roth"), nw.portfolio + nw.type("taxable"), nw.type("home") + nw.type("vehicle"), nw.type("other")};
        Color[] cols = {t.series[0], t.series[2], t.series[4], t.series[1], t.series[5]};
        Ui.Card kc = Kit.capsCard("Assets by kind", null);
        Ui.HBox row = new Ui.HBox(22);
        row.add(Charts.donut(vals, cols, 140));
        Ui.VBox lst = new Ui.VBox(0); int k = 0;
        for (int i = 0; i < names.length; i++) if (vals[i] > 0)
            lst.add(Ui.listRow(Ui.dot(cols[i]), names[i], null, Ui.mono(Fmt.compact(vals[i]), 13f, t.ink), k++ > 0));
        row.grow(lst); kc.add(row);
        kc.add(Ui.text("Assets " + Fmt.money(nw.assets) + "     Debts " + Fmt.money(nw.liabilities) + "     Debt-to-asset " + (nw.assets > 0 ? Fmt.pctu(nw.liabilities / nw.assets) : "—"), Theme.mono(12.5f), t.muted));
        top.set(Ui.cols(18, Ui.top(nc), Ui.top(kc)));
        if (assetsLbl != null) assetsLbl.setText(Fmt.money(nw.assets));
        if (pfLbl != null) pfLbl.setText(Fmt.money(nw.portfolio));
    }
}

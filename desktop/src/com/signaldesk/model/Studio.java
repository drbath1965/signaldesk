package com.signaldesk.model;

import com.signaldesk.json.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything the studio keeps. The JSON shape matches the web studio's backup format,
 * plus a "backlog" list and a "ui" block that only the desktop app uses.
 */
public final class Studio {
    public boolean example;
    public final List<Income> income = new ArrayList<>();
    public final List<Category> cats = new ArrayList<>();
    public final List<Transaction> tx = new ArrayList<>();
    public final List<Bill> bills = new ArrayList<>();
    public final List<Debt> debts = new ArrayList<>();
    public String debtMethod = "avalanche";
    public double debtExtra;
    public final List<Account> accounts = new ArrayList<>();
    public final List<Holding> holdings = new ArrayList<>();
    public final Map<String, Double> targets = new LinkedHashMap<>();
    public RetirementPlan retire = new RetirementPlan();
    public SignalParams sig = new SignalParams();
    public final TreeMap<String, Double> nwHist = new TreeMap<>();
    public final List<BacklogItem> backlog = new ArrayList<>();
    public String theme = "light";

    public Category category(String id) { for (Category c : cats) if (c.id.equals(id)) return c; return null; }
    public String categoryName(String id) { Category c = category(id); return c == null ? "Uncategorized" : c.toString(); }

    public static Map<String, Double> defaultTargets() {
        Map<String, Double> t = new LinkedHashMap<>();
        t.put("US stocks", 55.0); t.put("Intl stocks", 20.0); t.put("Bonds", 20.0); t.put("Cash", 5.0); t.put("Real estate", 0.0); t.put("Other", 0.0);
        return t;
    }

    // ------------------------------------------------------------------ JSON
    public static Studio from(Map<String, Object> root) {
        Map<String, Object> m = root.containsKey("state") && root.get("state") instanceof Map ? Json.obj(root, "state") : root;
        Studio s = new Studio();
        s.example = Json.bool(m, "example", false);
        Map<String, Object> b = Json.obj(m, "budget");
        for (Object o : Json.arr(b, "income")) s.income.add(Income.from(Json.obj(o)));
        for (Object o : Json.arr(b, "cats")) s.cats.add(Category.from(Json.obj(o)));
        for (Object o : Json.arr(b, "tx")) s.tx.add(Transaction.from(Json.obj(o)));
        for (Object o : Json.arr(m, "bills")) s.bills.add(Bill.from(Json.obj(o)));
        for (Object o : Json.arr(m, "debts")) s.debts.add(Debt.from(Json.obj(o)));
        Map<String, Object> dp = Json.obj(m, "debtPlan");
        s.debtMethod = "snowball".equals(Json.str(dp, "method", "avalanche")) ? "snowball" : "avalanche";
        s.debtExtra = Json.num(dp, "extra", 0);
        for (Object o : Json.arr(m, "accounts")) s.accounts.add(Account.from(Json.obj(o)));
        for (Object o : Json.arr(m, "holdings")) s.holdings.add(Holding.from(Json.obj(o)));
        s.targets.putAll(defaultTargets());
        for (Map.Entry<String, Object> e : Json.obj(m, "targets").entrySet()) if (e.getValue() instanceof Number n) s.targets.put(e.getKey(), n.doubleValue());
        s.retire = RetirementPlan.from(Json.obj(m, "retire"));
        s.sig = SignalParams.from(Json.obj(Json.obj(m, "sig"), "P"));
        for (Map.Entry<String, Object> e : Json.obj(m, "nwHist").entrySet()) if (e.getValue() instanceof Number n) s.nwHist.put(e.getKey(), n.doubleValue());
        for (Object o : Json.arr(m, "backlog")) s.backlog.add(BacklogItem.from(Json.obj(o)));
        s.theme = "dark".equals(Json.str(Json.obj(m, "ui"), "theme", "light")) ? "dark" : "light";
        if (s.cats.isEmpty()) for (Category c : defaultCategories(false)) s.cats.add(c);
        return s;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", 1); m.put("example", example);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("income", list(income.stream().map(Income::toJson).toList()));
        b.put("cats", list(cats.stream().map(Category::toJson).toList()));
        b.put("tx", list(tx.stream().map(Transaction::toJson).toList()));
        m.put("budget", b);
        m.put("bills", list(bills.stream().map(Bill::toJson).toList()));
        m.put("debts", list(debts.stream().map(Debt::toJson).toList()));
        Map<String, Object> dp = new LinkedHashMap<>(); dp.put("method", debtMethod); dp.put("extra", debtExtra); m.put("debtPlan", dp);
        m.put("accounts", list(accounts.stream().map(Account::toJson).toList()));
        m.put("holdings", list(holdings.stream().map(Holding::toJson).toList()));
        m.put("targets", new LinkedHashMap<>(targets));
        m.put("retire", retire.toJson());
        Map<String, Object> sg = new LinkedHashMap<>(); sg.put("P", sig.toJson()); m.put("sig", sg);
        m.put("nwHist", new LinkedHashMap<>(nwHist));
        m.put("backlog", list(backlog.stream().map(BacklogItem::toJson).toList()));
        Map<String, Object> ui = new LinkedHashMap<>(); ui.put("theme", theme); m.put("ui", ui);
        return m;
    }
    private static List<Object> list(List<Map<String, Object>> l) { return new ArrayList<>(l); }

    // ------------------------------------------------------------------ defaults
    public static List<Category> defaultCategories(boolean withAmounts) {
        Object[][] d = {
            {"Housing", "needs", 1850}, {"Utilities", "needs", 280}, {"Groceries", "needs", 750}, {"Insurance", "needs", 310},
            {"Transportation", "needs", 280}, {"Phone & internet", "needs", 160}, {"Medical", "needs", 120}, {"Debt payments", "needs", 665},
            {"Dining out", "wants", 300}, {"Entertainment", "wants", 150}, {"Shopping", "wants", 250}, {"Travel", "wants", 200}, {"Subscriptions", "wants", 70},
            {"Emergency fund", "savings", 400}, {"Extra debt payoff", "savings", 300}, {"Investing", "savings", 300}, {"Sinking funds", "savings", 150}};
        List<Category> out = new ArrayList<>();
        for (Object[] r : d) out.add(new Category((String) r[0], (String) r[1], withAmounts ? ((Integer) r[2]).doubleValue() : 0));
        return out;
    }

    /** The project backlog the desktop app starts with. */
    public static List<BacklogItem> starterBacklog() {
        List<BacklogItem> l = new ArrayList<>();
        String d = "2026-11-23";
        l.add(new BacklogItem("bl-live-prices", "Live stock prices",
            "Prices are typed in by hand and price files are loaded manually. Connect a market-data source such as Alpha Vantage so prices fill in automatically.",
            BacklogItem.Priority.HIGH, "Stock signals", d));
        l.add(new BacklogItem("bl-price-files", "Save price files to my account",
            "Price files are saved in the browser, so they don't follow me to a new window or device. Move them into account storage like the rest of the studio.",
            BacklogItem.Priority.MED, "Stock signals", d));
        l.add(new BacklogItem("bl-watchlist", "Watchlist that scores several tickers",
            "Score several tickers at once and flag which ones show a buy today.", BacklogItem.Priority.MED, "Stock signals", d));
        l.add(new BacklogItem("bl-training-video", "Training video for the full studio",
            "The current video only covers stock signals. Redo it to walk through every section.", BacklogItem.Priority.LOW, "Training", d));
        l.add(new BacklogItem("bl-browser-check", "Check the layout in a real browser",
            "The studio's layout hasn't been checked on screen yet. Review each page on desktop and phone, in light and dark mode.",
            BacklogItem.Priority.HIGH, "Studio", d));
        l.add(new BacklogItem("bl-bank-import", "Import bank transactions from CSV",
            "Bring in a CSV export from the bank so expenses don't have to be typed in one by one.", BacklogItem.Priority.MED, "Budget", d));
        return l;
    }

    public static Studio blank() {
        Studio s = new Studio();
        s.cats.addAll(defaultCategories(false));
        s.income.add(new Income("Paycheck (net)", 0));
        s.accounts.add(new Account("Checking", "cash", 0));
        s.targets.putAll(defaultTargets());
        return s;
    }
}

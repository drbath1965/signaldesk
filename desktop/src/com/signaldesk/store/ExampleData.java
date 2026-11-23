package com.signaldesk.store;

import com.signaldesk.calc.BillCalc;
import com.signaldesk.calc.Dates;
import com.signaldesk.calc.NetWorth;
import com.signaldesk.model.*;
import java.util.List;

/** The example household the studio opens with, identical to the web studio's. */
public final class ExampleData {
    private ExampleData() {}

    /** Seeded generator (mulberry32) so the example looks the same every time. */
    private static final class Rng {
        private int a;
        Rng(int seed) { a = seed; }
        double next() {
            a = a + 0x6D2B79F5;
            int t = (a ^ (a >>> 15)) * (1 | a);
            t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;
            return ((t ^ (t >>> 14)) & 0xffffffffL) / 4294967296.0;
        }
    }

    public static Studio build() {
        Studio s = new Studio();
        s.example = true;
        s.cats.addAll(Studio.defaultCategories(true));
        s.income.add(new Income("Paycheck 1 (net)", 4200));
        s.income.add(new Income("Paycheck 2 (net)", 2400));
        s.income.add(new Income("Side work", 250));
        java.util.function.Function<String, String> cid = n -> s.cats.stream().filter(c -> c.name.equals(n)).findFirst().orElseThrow().id;
        s.bills.addAll(List.of(
            new Bill("Mortgage", 1850, 1, "monthly", 1, cid.apply("Housing"), true),
            new Bill("Gym membership", 45, 3, "monthly", 1, cid.apply("Entertainment"), true),
            new Bill("Home insurance", 1560, 5, "annual", 3, cid.apply("Insurance"), false),
            new Bill("Internet", 80, 9, "monthly", 1, cid.apply("Phone & internet"), true),
            new Bill("Electric", 165, 12, "monthly", 1, cid.apply("Utilities"), false),
            new Bill("Car insurance", 540, 15, "quarterly", 1, cid.apply("Insurance"), false),
            new Bill("Water & sewer", 70, 18, "monthly", 1, cid.apply("Utilities"), false),
            new Bill("Mobile phones", 80, 22, "monthly", 1, cid.apply("Phone & internet"), true),
            new Bill("Credit card payment", 160, 24, "monthly", 1, cid.apply("Debt payments"), false),
            new Bill("Car loan", 385, 26, "monthly", 1, cid.apply("Debt payments"), true),
            new Bill("Student loan", 120, 28, "monthly", 1, cid.apply("Debt payments"), true),
            new Bill("Streaming services", 38, 27, "monthly", 1, cid.apply("Subscriptions"), true)));
        s.debts.addAll(List.of(
            new Debt("Credit card", 6400, 22.9, 160, true), new Debt("Car loan", 14800, 6.4, 385, true),
            new Debt("Student loan", 9200, 5.1, 120, true), new Debt("Mortgage", 238000, 3.25, 1850, false)));
        s.debtMethod = "avalanche"; s.debtExtra = 300;
        s.accounts.addAll(List.of(
            new Account("Checking", "cash", 4800), new Account("High-yield savings", "cash", 14500),
            new Account("401(k) · traditional", "trad", 186000), new Account("401(k) · Roth", "roth", 74000),
            new Account("Roth IRA", "roth", 38500), new Account("Home (estimated value)", "home", 410000), new Account("Car", "vehicle", 21000)));
        s.holdings.addAll(List.of(
            new Holding("VTI", "US stocks", 85, 198, 312), new Holding("VXUS", "Intl stocks", 140, 55, 68),
            new Holding("BND", "Bonds", 120, 74, 73), new Holding("AAPL", "US stocks", 30, 150, 245),
            new Holding("MSFT", "US stocks", 12, 290, 520), new Holding("CASH", "Cash", 3200, 1, 1)));
        s.targets.putAll(Studio.defaultTargets());

        String cur = Dates.cur(), prev = Dates.add(cur, -1); int today = Dates.todayDay();
        Rng rnd = new Rng(4242);
        String[][] months = {{prev, String.valueOf(Dates.dim(prev))}, {cur, String.valueOf(today)}};
        for (String[] mo : months) {
            String ym = mo[0]; int upto = Integer.parseInt(mo[1]);
            String[] groc = {"Kroger", "Publix", "Costco", "Aldi", "Publix"};
            for (int d : new int[]{2, 9, 16, 23, 30}) if (d <= upto && d <= Dates.dim(ym)) add(s, ym, d, groc[d % 5], cid.apply("Groceries"), 140 + rnd.next() * 80);
            String[] gas = {"Shell", "QuikTrip", "Costco Gas", "Chevron"};
            for (int d : new int[]{4, 11, 19, 26}) if (d <= upto) add(s, ym, d, gas[d % 4], cid.apply("Transportation"), 42 + rnd.next() * 22);
            String[] dine = {"Thai Garden", "Taco spot", "Pizza night", "Brunch"};
            for (int d : new int[]{6, 13, 20, 27}) if (d <= upto) add(s, ym, d, dine[d % 4], cid.apply("Dining out"), 30 + rnd.next() * 45);
            String[] shop = {"Target", "Amazon"};
            for (int d : new int[]{8, 21}) if (d <= upto) add(s, ym, d, shop[d % 2], cid.apply("Shopping"), 55 + rnd.next() * 90);
            if (14 <= upto) add(s, ym, 14, "Movie tickets", cid.apply("Entertainment"), 38);
            if (17 <= upto) add(s, ym, 17, "Pharmacy", cid.apply("Medical"), 24 + rnd.next() * 30);
            if (25 <= upto) add(s, ym, 25, "Concert tickets", cid.apply("Entertainment"), 64);
        }
        for (Bill b : s.bills) if (!b.autopay && BillCalc.dueIn(b, cur) && b.day < today) b.paid.put(cur, true);
        for (Bill b : s.bills) if (!b.autopay && BillCalc.dueIn(b, prev)) b.paid.put(prev, true);
        double nw = NetWorth.of(s).net(); Rng r2 = new Rng(77);
        for (int k = 12; k >= 1; k--) s.nwHist.put(Dates.add(cur, -k), (double) Math.round(nw * (1 - .0068 * k) + (r2.next() - .5) * 4000));
        s.nwHist.put(cur, (double) Math.round(nw));
        s.backlog.addAll(Studio.starterBacklog());
        return s;
    }
    private static void add(Studio s, String ym, int day, String desc, String cat, double amt) {
        s.tx.add(new Transaction(Dates.iso(ym, day), desc, cat, Math.round(amt * 100) / 100.0));
    }
}

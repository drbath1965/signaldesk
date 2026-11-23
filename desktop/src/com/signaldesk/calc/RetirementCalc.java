package com.signaldesk.calc;

import com.signaldesk.model.RetirementPlan;
import com.signaldesk.model.Studio;
import java.util.ArrayList;
import java.util.List;

/**
 * Year-by-year projection in today's dollars: contributions with employer match and the
 * IRS 401(k) limit, then a drawdown from taxable, traditional (grossed up for tax) and Roth.
 */
public final class RetirementCalc {
    /** 2026 IRS limits (irs.gov, announced Nov 2025). */
    public static final int LIMIT_YEAR = 2026;
    public static final double K401 = 24500, CATCH_50 = 8000, CATCH_60_63 = 11250;

    public static double k401Limit(int age) { return K401 + (age >= 60 && age <= 63 ? CATCH_60_63 : age >= 50 ? CATCH_50 : 0); }

    public record Point(int age, double trad, double roth, double tax, double total, double nominal) {}
    public record FirstYear(double contrib, double roth, double trad, double match, double limit, double taxable) {}

    public final List<Point> points = new ArrayList<>();
    public final int age, retireAge, planTo, yearsToRetire;
    public final double portIncome, ssAnnual, incomeWithSS, onTrack;
    public final Integer runsOutAt;
    public final boolean capped;
    public final FirstYear first;
    public final Point atRetirement;

    private RetirementCalc(Studio s) {
        RetirementPlan r = s.retire; NetWorth nw = NetWorth.of(s);
        double inf = r.inflation / 100, rp = r.retPre / 100, rq = r.retPost / 100;
        double trad = nw.type("trad"), roth = nw.type("roth"), tax = r.includeTaxable ? nw.portfolio + nw.type("taxable") : 0;
        double salary = r.salary;
        age = (int) Math.round(r.age);
        retireAge = Math.max(age, (int) Math.round(r.retireAge));
        planTo = Math.max(retireAge + 1, (int) Math.round(r.lifeExp));
        boolean cap = false; FirstYear fy = null;
        push(age, 0, trad, roth, tax, inf);
        for (int y = 0; y < retireAge - age; y++) {
            int a = age + y;
            double c = salary * r.contribPct / 100, lim = k401Limit(a);
            if (c > lim) { c = lim; cap = true; }
            double match = Math.min(r.contribPct, r.matchCap) / 100 * salary * r.matchPct / 100;
            double cr = c * r.rothShare / 100, ct = c - cr, cx = r.includeTaxable ? r.taxableMonthly * 12 : 0;
            if (y == 0) fy = new FirstYear(c, cr, ct, match, lim, cx);
            trad = trad * (1 + rp) + (ct + match) * (1 + rp / 2);
            roth = roth * (1 + rp) + cr * (1 + rp / 2);
            tax = tax * (1 + rp) + cx * (1 + rp / 2);
            salary *= 1 + r.raise / 100;
            push(a + 1, y + 1, trad, roth, tax, inf);
        }
        capped = cap; first = fy;
        atRetirement = points.get(points.size() - 1);
        yearsToRetire = retireAge - age;
        ssAnnual = r.ssMonthly * 12;
        portIncome = atRetirement.total() * r.wr / 100;
        incomeWithSS = portIncome + ssAnnual;
        Integer out = null; double t = r.taxRate / 100;
        for (int a = retireAge; a < planTo; a++) {
            int y = a - age; double f = Math.pow(1 + inf, y);
            double need = r.goal * f - (a >= r.ssAge ? ssAnnual * f : 0);
            need = Math.max(0, need);
            double fromTax = Math.min(tax, need); tax -= fromTax; need -= fromTax;
            double gross = need / (1 - t), fromTrad = Math.min(trad, gross); trad -= fromTrad; need -= fromTrad * (1 - t);
            double fromRoth = Math.min(roth, need); roth -= fromRoth; need -= fromRoth;
            if (need > 1 && out == null) out = a;
            trad *= 1 + rq; roth *= 1 + rq; tax *= 1 + rq;
            push(a + 1, y + 1, trad, roth, tax, inf);
        }
        runsOutAt = out;
        onTrack = r.goal > 0 ? incomeWithSS / r.goal : 0;
    }
    private void push(int a, int y, double trad, double roth, double tax, double inf) {
        double f = Math.pow(1 + inf, y);
        points.add(new Point(a, trad / f, roth / f, tax / f, (trad + roth + tax) / f, trad + roth + tax));
    }
    public static RetirementCalc of(Studio s) { return new RetirementCalc(s); }
}

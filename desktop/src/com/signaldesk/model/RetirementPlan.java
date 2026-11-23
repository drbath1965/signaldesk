package com.signaldesk.model;

import com.signaldesk.json.Json;
import java.util.LinkedHashMap;
import java.util.Map;

/** Retirement planner inputs, all in today's dollars and whole percents. */
public final class RetirementPlan {
    public double age = 45, retireAge = 65, lifeExp = 92, salary = 128000, contribPct = 12, rothShare = 50,
            matchPct = 50, matchCap = 6, raise = 2.5, retPre = 6.5, retPost = 5, inflation = 2.5, goal = 85000,
            ssMonthly = 2600, ssAge = 67, wr = 4, taxRate = 15, taxableMonthly = 300;
    public boolean includeTaxable = true;

    public static RetirementPlan from(Map<String, Object> m) {
        RetirementPlan r = new RetirementPlan();
        r.age = Json.num(m, "age", r.age); r.retireAge = Json.num(m, "retireAge", r.retireAge); r.lifeExp = Json.num(m, "lifeExp", r.lifeExp);
        r.salary = Json.num(m, "salary", r.salary); r.contribPct = Json.num(m, "contribPct", r.contribPct); r.rothShare = Json.num(m, "rothShare", r.rothShare);
        r.matchPct = Json.num(m, "matchPct", r.matchPct); r.matchCap = Json.num(m, "matchCap", r.matchCap); r.raise = Json.num(m, "raise", r.raise);
        r.retPre = Json.num(m, "retPre", r.retPre); r.retPost = Json.num(m, "retPost", r.retPost); r.inflation = Json.num(m, "inflation", r.inflation);
        r.goal = Json.num(m, "goal", r.goal); r.ssMonthly = Json.num(m, "ssMonthly", r.ssMonthly); r.ssAge = Json.num(m, "ssAge", r.ssAge);
        r.wr = Json.num(m, "wr", r.wr); r.taxRate = Json.num(m, "taxRate", r.taxRate); r.taxableMonthly = Json.num(m, "taxableMonthly", r.taxableMonthly);
        r.includeTaxable = Json.bool(m, "includeTaxable", r.includeTaxable);
        return r;
    }
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("age", age); m.put("retireAge", retireAge); m.put("lifeExp", lifeExp); m.put("salary", salary); m.put("contribPct", contribPct);
        m.put("rothShare", rothShare); m.put("matchPct", matchPct); m.put("matchCap", matchCap); m.put("raise", raise); m.put("retPre", retPre);
        m.put("retPost", retPost); m.put("inflation", inflation); m.put("goal", goal); m.put("ssMonthly", ssMonthly); m.put("ssAge", ssAge);
        m.put("wr", wr); m.put("taxRate", taxRate); m.put("includeTaxable", includeTaxable); m.put("taxableMonthly", taxableMonthly);
        return m;
    }
}

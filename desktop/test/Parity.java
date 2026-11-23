import com.signaldesk.calc.*;
import com.signaldesk.model.*;
import com.signaldesk.store.*;
import com.signaldesk.json.Json;
import com.signaldesk.signals.*;
public class Parity {
  public static void main(String[] a) throws Exception {
    Studio s = ExampleData.build();
    BudgetCalc b = BudgetCalc.of(s, Dates.cur());
    System.out.printf("budget income=%.0f planned=%.0f unassigned=%.0f%n", b.income, b.planned, b.unassigned());
    DebtCalc d = DebtCalc.of(s);
    System.out.printf("debt %s months=%d int=%.0f | other months=%d int=%.0f | mins months=%d int=%.0f%n", d.method, d.plan.months(), d.plan.interest(), d.alt.months(), d.alt.interest(), d.minsOnly.months(), d.minsOnly.interest());
    for (DebtCalc.Payoff p : d.plan.order()) System.out.print(p.name()+":"+p.paidOffMonth()+" ");
    System.out.println();
    RetirementCalc r = RetirementCalc.of(s);
    System.out.printf("retire atRet=%.0f nominal=%.0f inc=%.0f onTrack=%.2f runsOut=%s first=%s%n", r.atRetirement.total(), r.atRetirement.nominal(), r.incomeWithSS, r.onTrack, r.runsOutAt, r.first);
    NetWorth n = NetWorth.of(s);
    System.out.printf("nw net=%.0f assets=%.0f liab=%.0f pf=%.0f%n", n.net(), n.assets, n.liabilities, n.portfolio);
    System.out.println("attention: " + Attention.of(s).stream().map(Attention.Item::title).toList());
    // JSON round trip
    String js = Json.write(s.toJson()); Studio s2 = Studio.from(Json.obj(Json.parse(js)));
    System.out.println("roundtrip equal: " + Json.write(s2.toJson()).equals(js) + " bytes=" + js.length());
    SignalEngine e = SignalEngine.run(SignalEngine.sample(), s.sig);
    System.out.printf("signals n=%d trades=%d ret=%s bh=%s dd=%s verdict=%s%n", e.data.size(), e.trades.size(), Fmt.pct(e.stats.ret()), Fmt.pct(e.stats.bh()), Fmt.pct(e.stats.dd()), e.verdict().call());
  }
}

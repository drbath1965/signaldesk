package com.signaldesk.model;

import com.signaldesk.json.Json;
import java.util.LinkedHashMap;
import java.util.Map;

/** Settings for the buy/sell signal algorithm. */
public final class SignalParams {
    public int fast = 20, slow = 50, rsiN = 14, os = 30, ob = 70;
    public double stop = 8, fee = 5;

    public SignalParams copy() { SignalParams p = new SignalParams(); p.fast = fast; p.slow = slow; p.rsiN = rsiN; p.os = os; p.ob = ob; p.stop = stop; p.fee = fee; return p; }

    public static SignalParams from(Map<String, Object> m) {
        SignalParams p = new SignalParams();
        p.fast = (int) Json.num(m, "fast", p.fast); p.slow = (int) Json.num(m, "slow", p.slow); p.rsiN = (int) Json.num(m, "rsiN", p.rsiN);
        p.os = (int) Json.num(m, "os", p.os); p.ob = (int) Json.num(m, "ob", p.ob); p.stop = Json.num(m, "stop", p.stop); p.fee = Json.num(m, "fee", p.fee);
        if (p.fast < 2 || p.fast >= p.slow || p.os >= p.ob) return new SignalParams();
        return p;
    }
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fast", fast); m.put("slow", slow); m.put("rsiN", rsiN); m.put("os", os); m.put("ob", ob); m.put("stop", stop); m.put("fee", fee);
        return m;
    }
}

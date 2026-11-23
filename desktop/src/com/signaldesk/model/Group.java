package com.signaldesk.model;

/** Budget groups and the 50/30/20 guide. */
public enum Group {
    NEEDS("needs", "Needs", 50), WANTS("wants", "Wants", 30), SAVINGS("savings", "Savings & debt payoff", 20);

    public final String key, label; public final int guidePct;
    Group(String key, String label, int guidePct) { this.key = key; this.label = label; this.guidePct = guidePct; }

    public static Group of(String key) {
        for (Group g : values()) if (g.key.equals(key)) return g;
        return WANTS;
    }
    public static boolean isValid(String key) { for (Group g : values()) if (g.key.equals(key)) return true; return false; }
    public String shortLabel() { return this == SAVINGS ? "Savings" : label; }
    @Override public String toString() { return shortLabel(); }
}

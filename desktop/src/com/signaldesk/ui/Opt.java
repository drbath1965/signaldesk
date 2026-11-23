package com.signaldesk.ui;

/** A choice in a drop-down: a stored key with a display label. */
public record Opt(String key, String label) {
    @Override public String toString() { return label; }
    public static Opt find(Opt[] opts, String key) { for (Opt o : opts) if (o.key.equals(key)) return o; return opts.length > 0 ? opts[0] : null; }
}

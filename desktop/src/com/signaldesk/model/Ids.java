package com.signaldesk.model;

import java.security.SecureRandom;

/** Short random ids, the same shape the web studio uses. */
public final class Ids {
    private static final SecureRandom R = new SecureRandom();
    private static final String A = "abcdefghijklmnopqrstuvwxyz0123456789";
    private Ids() {}
    public static String next() {
        StringBuilder b = new StringBuilder(8);
        for (int i = 0; i < 8; i++) b.append(A.charAt(R.nextInt(A.length())));
        return b.toString();
    }
}

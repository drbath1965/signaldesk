package com.signaldesk.model;

public enum AccountType {
    CASH("cash", "Cash"), TAXABLE("taxable", "Brokerage (taxable)"), TRAD("trad", "Retirement · traditional"),
    ROTH("roth", "Retirement · Roth"), HOME("home", "Home"), VEHICLE("vehicle", "Vehicle"), OTHER("other", "Other asset");

    public final String key, label;
    AccountType(String key, String label) { this.key = key; this.label = label; }
    public static AccountType of(String key) { for (AccountType t : values()) if (t.key.equals(key)) return t; return null; }
    @Override public String toString() { return label; }
}

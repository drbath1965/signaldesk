package com.signaldesk.calc;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Calendar helpers. Months are "yyyy-MM" strings, the same keys the web studio stores. */
public final class Dates {
    private Dates() {}
    private static final DateTimeFormatter MON_LONG = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);
    private static final DateTimeFormatter MON_SHORT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US);
    private static final DateTimeFormatter MON_TICK = DateTimeFormatter.ofPattern("MMM ''yy", Locale.US);
    private static final DateTimeFormatter DAY_SHORT = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter DAY_LONG = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US);

    /** Today, overridable with -Dsignaldesk.today=yyyy-MM-dd for demos and tests. */
    public static LocalDate today() {
        String o = System.getProperty("signaldesk.today");
        if (o != null) try { return LocalDate.parse(o); } catch (Exception ignored) {}
        return LocalDate.now();
    }
    public static String cur() { return ym(YearMonth.from(today())); }
    public static int todayDay() { return today().getDayOfMonth(); }
    public static String ym(YearMonth y) { return y.toString(); }
    public static YearMonth parse(String ym) { return YearMonth.parse(ym); }
    public static String add(String ym, int k) { return ym(parse(ym).plusMonths(k)); }
    public static int dim(String ym) { return parse(ym).lengthOfMonth(); }
    public static int month(String ym) { return parse(ym).getMonthValue(); }
    public static String iso(String ym, int day) { return parse(ym).atDay(day).toString(); }
    public static String label(String ym) { return parse(ym).format(MON_LONG); }
    public static String labelShort(String ym) { return parse(ym).format(MON_SHORT); }
    public static String tick(String ym) { return parse(ym).format(MON_TICK); }
    public static String dayShort(String iso) { try { return LocalDate.parse(iso).format(DAY_SHORT); } catch (Exception e) { return iso; } }
    public static String dayLong(String iso) { try { return LocalDate.parse(iso).format(DAY_LONG); } catch (Exception e) { return iso; } }
    public static String weekday(LocalDate d) { return d.format(WEEKDAY); }
    public static String afterMonths(int m) { return labelShort(add(cur(), m)); }
    public static String monthsLabel(int m) {
        int y = m / 12, r = m % 12;
        StringBuilder b = new StringBuilder();
        if (y > 0) b.append(y).append(y > 1 ? " yrs" : " yr");
        if (y > 0 && r > 0) b.append(' ');
        if (r > 0 || y == 0) b.append(r).append(" mo");
        return b.toString();
    }
}

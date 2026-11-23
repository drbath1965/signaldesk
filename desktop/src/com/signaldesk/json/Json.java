package com.signaldesk.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer with no dependencies.
 * Objects become LinkedHashMap, arrays ArrayList, numbers Double.
 */
public final class Json {
    private Json() {}

    // ------------------------------------------------------------------ parse
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i < p.s.length()) throw p.err("Unexpected trailing content");
        return v;
    }

    private static final class Parser {
        final String s; int i;
        Parser(String s) { this.s = s; }

        IllegalArgumentException err(String m) { return new IllegalArgumentException(m + " at position " + i); }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        char peek() { if (i >= s.length()) throw err("Unexpected end of input"); return s.charAt(i); }

        Object value() {
            char c = peek();
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return number();
                    throw err("Unexpected character '" + c + "'");
            }
        }
        void expect(String w) { if (!s.startsWith(w, i)) throw err("Expected " + w); i += w.length(); }
        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws(); String k = string(); ws();
                if (peek() != ':') throw err("Expected ':'");
                i++; ws();
                m.put(k, value()); ws();
                char c = peek(); i++;
                if (c == '}') return m;
                if (c != ',') throw err("Expected ',' or '}'");
            }
        }
        List<Object> array() {
            List<Object> a = new ArrayList<>();
            i++; ws();
            if (peek() == ']') { i++; return a; }
            while (true) {
                ws(); a.add(value()); ws();
                char c = peek(); i++;
                if (c == ']') return a;
                if (c != ',') throw err("Expected ',' or ']'");
            }
        }
        String string() {
            if (peek() != '"') throw err("Expected string");
            i++;
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = peek(); i++;
                if (c == '"') return b.toString();
                if (c == '\\') {
                    char e = peek(); i++;
                    switch (e) {
                        case '"': b.append('"'); break;
                        case '\\': b.append('\\'); break;
                        case '/': b.append('/'); break;
                        case 'b': b.append('\b'); break;
                        case 'f': b.append('\f'); break;
                        case 'n': b.append('\n'); break;
                        case 'r': b.append('\r'); break;
                        case 't': b.append('\t'); break;
                        case 'u':
                            if (i + 4 > s.length()) throw err("Bad unicode escape");
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                        default: throw err("Bad escape");
                    }
                } else b.append(c);
            }
        }
        Double number() {
            int st = i;
            if (s.charAt(i) == '-') i++;
            while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) i++;
            try { return Double.parseDouble(s.substring(st, i)); }
            catch (NumberFormatException e) { throw err("Bad number"); }
        }
    }

    // ------------------------------------------------------------------ write
    public static String write(Object v) {
        StringBuilder b = new StringBuilder();
        write(b, v, 0);
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder b, Object v, int ind) {
        if (v == null) { b.append("null"); return; }
        if (v instanceof String str) { quote(b, str); return; }
        if (v instanceof Boolean) { b.append(v); return; }
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (!Double.isFinite(d)) { b.append("null"); return; }
            if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d);
            else b.append(d);
            return;
        }
        if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) { b.append("{}"); return; }
            b.append("{\n");
            int k = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                pad(b, ind + 1); quote(b, String.valueOf(e.getKey())); b.append(": ");
                write(b, e.getValue(), ind + 1);
                if (++k < m.size()) b.append(',');
                b.append('\n');
            }
            pad(b, ind); b.append('}');
            return;
        }
        if (v instanceof List<?> l) {
            if (l.isEmpty()) { b.append("[]"); return; }
            boolean simple = l.stream().noneMatch(x -> x instanceof Map || x instanceof List);
            if (simple) {
                b.append('[');
                for (int k = 0; k < l.size(); k++) { if (k > 0) b.append(", "); write(b, l.get(k), ind); }
                b.append(']');
                return;
            }
            b.append("[\n");
            for (int k = 0; k < l.size(); k++) {
                pad(b, ind + 1); write(b, l.get(k), ind + 1);
                if (k < l.size() - 1) b.append(',');
                b.append('\n');
            }
            pad(b, ind); b.append(']');
            return;
        }
        quote(b, v.toString());
    }
    private static void pad(StringBuilder b, int n) { for (int k = 0; k < n; k++) b.append("  "); }
    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
            }
        }
        b.append('"');
    }

    // ------------------------------------------------------------------ typed access
    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) { return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>(); }
    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) { return o instanceof List ? (List<Object>) o : new ArrayList<>(); }
    public static Map<String, Object> obj(Map<String, Object> m, String k) { return obj(m.get(k)); }
    public static List<Object> arr(Map<String, Object> m, String k) { return arr(m.get(k)); }
    public static String str(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        if (v == null) return def;
        if (v instanceof Double d && d == Math.rint(d)) return String.valueOf(d.longValue());
        return v.toString();
    }
    public static double num(Map<String, Object> m, String k, double def) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) { try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; } }
        return def;
    }
    public static boolean bool(Map<String, Object> m, String k, boolean def) {
        Object v = m.get(k);
        return v instanceof Boolean b ? b : def;
    }
}

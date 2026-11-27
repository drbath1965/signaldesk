package com.signaldesk.ui.views;

import com.signaldesk.learn.Learn;
import com.signaldesk.ui.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Lessons with short quizzes, and a searchable glossary. */
public final class LearnView extends View {
    private static String tab = "lessons", openLesson, query = "";
    private final Slot body = new Slot(), tabs = new Slot();
    // quiz state for the open lesson: chosen option per question (-1 = not answered)
    private int[] picked;

    public LearnView(MainFrame app, String arg) {
        super(app);
        if (arg != null && arg.startsWith("lesson:")) { tab = "lessons"; openLesson = arg.substring(7); }
        else if (arg != null && arg.startsWith("term:")) { tab = "glossary"; query = arg.substring(5); }
    }
    private Learn L() { return Learn.get(); }

    @Override public JComponent build() {
        Ui.VBox root = new Ui.VBox(20);
        int done = (int) L().lessons.stream().filter(l -> s().lessonDone(l.id())).count();
        root.add(Kit.head("Learn", "How investing works", "Short lessons on stocks, funds, crypto, risk and reading charts, each with a three-question quiz. " + done + " of " + L().lessons.size() + " complete.", tabs));
        root.add(body);
        draw();
        return root;
    }
    private void draw() {
        tabs.set(Ui.seg(new String[]{"Lessons", "Glossary"}, tab.equals("lessons") ? 0 : 1, i -> { tab = i == 0 ? "lessons" : "glossary"; if (i == 0) openLesson = null; draw(); }));
        if (tab.equals("glossary")) body.set(glossary());
        else if (openLesson != null && L().lesson(openLesson) != null) body.set(lesson(L().lesson(openLesson)));
        else body.set(lessonList());
    }
    private void open(String id) { openLesson = id; picked = null; app.navigate("learn"); }

    // ------------------------------------------------------------------ lesson list
    private JComponent lessonList() {
        Theme t = Theme.T; Ui.VBox v = new Ui.VBox(22);
        int n = 1;
        for (String level : L().levels()) {
            Ui.VBox sec = new Ui.VBox(10);
            sec.add(Ui.caps(level), false);
            List<JComponent> cards = new ArrayList<>();
            for (Learn.Lesson l : L().lessons) if (l.level().equals(level)) cards.add(lessonCard(l, n++));
            while (cards.size() % 3 != 0) { JPanel e = new JPanel(); e.setOpaque(false); cards.add(e); }
            for (int i = 0; i < cards.size(); i += 3) sec.add(Ui.cols(14, cards.get(i), cards.get(i + 1), cards.get(i + 2)));
            v.add(sec);
        }
        if (L().lessons.isEmpty()) v.add(Kit.empty("Lesson content is missing from this build."));
        return v;
    }
    private JComponent lessonCard(Learn.Lesson l, int n) {
        Theme t = Theme.T; boolean done = s().lessonDone(l.id()); Double best = s().lessons.get(l.id());
        Ui.Card c = new Ui.Card(6) {
            boolean hov;
            { setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
              addMouseListener(new MouseAdapter() {
                  @Override public void mouseClicked(MouseEvent e) { open(l.id()); }
                  @Override public void mouseEntered(MouseEvent e) { hov = true; repaint(); }
                  @Override public void mouseExited(MouseEvent e) { hov = false; repaint(); } }); }
            @Override protected void paintComponent(Graphics g) { super.paintComponent(g); if (hov) { Graphics2D g2 = Ui.aa(g); g2.setColor(t.accent); g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14); g2.dispose(); } }
        };
        Ui.HBox top = new Ui.HBox(8);
        top.add(Ui.mono(String.format("%02d", n), 12f, t.muted)); top.glue();
        if (done) top.add(Ui.pill("Done · " + Math.round(best * 3) + "/3", Ui.Tone.POS));
        else if (best != null) top.add(Ui.pill("Quiz " + Math.round(best * 3) + "/3", Ui.Tone.WARN));
        else top.add(Ui.text(l.minutes() + " min", Theme.body(12f), t.muted));
        c.add(top);
        c.add(Ui.text(l.title(), Theme.semibold(15f), t.ink), false);
        JTextArea sm = Ui.para(l.summary(), Theme.body(13f), t.muted); c.add(sm);
        return c;
    }

    // ------------------------------------------------------------------ one lesson
    private JComponent lesson(Learn.Lesson l) {
        Theme t = Theme.T;
        if (picked == null || picked.length != l.quiz().size()) { picked = new int[l.quiz().size()]; Arrays.fill(picked, -1); }
        Ui.VBox main = new Ui.VBox(18);
        Ui.HBox nav = new Ui.HBox(14);
        nav.add(Ui.link("← All lessons", () -> { openLesson = null; app.navigate("learn"); }));
        nav.add(Ui.text(l.level() + " · " + l.minutes() + " min read", Theme.body(12.5f), t.muted));
        main.add(nav, false);
        Ui.Card c = new Ui.Card(14); c.setBorder(new EmptyBorder(24, 28, 26, 28));
        c.add(Ui.text(l.title(), Theme.display(26f), t.ink), false);
        JTextArea sum = Ui.para(l.summary(), Theme.body(15.5f), t.ink); sum.putClientProperty("maxw", 680); c.add(sum);
        for (Learn.Section s : l.sections()) {
            Ui.VBox sec = new Ui.VBox(4);
            if (!s.heading().isBlank()) sec.add(Ui.text(s.heading(), Theme.semibold(15f), t.ink), false);
            JTextArea p = Ui.para(s.text(), Theme.body(14.5f), t.ink); p.putClientProperty("maxw", 680); sec.add(p);
            c.add(sec);
        }
        Ui.Card tk = new Ui.Card(6).fill(t.holdBg);
        tk.add(Ui.caps("Key points"), false);
        for (String k : l.takeaways()) { Ui.HBox h = new Ui.HBox(8); h.add(Ui.text("•", Theme.bold(14f), t.accent)); JTextArea p = Ui.para(k, Theme.body(14f), t.ink); p.putClientProperty("maxw", 640); h.grow(p); tk.add(h); }
        c.add(tk);
        main.add(c);
        main.add(quiz(l));
        return Ui.rail(main, side(l), 300);
    }
    private JComponent side(Learn.Lesson l) {
        Theme t = Theme.T; Ui.VBox v = new Ui.VBox(16);
        Ui.Card terms = Kit.card("Terms in this lesson", null);
        for (String term : l.terms()) {
            Learn.Term x = L().term(term); if (x == null) continue;
            Ui.VBox e = new Ui.VBox(2); e.add(Ui.text(x.term(), Theme.semibold(13f), t.ink), false); JTextArea d = Ui.para(x.def(), Theme.body(12.5f), t.muted); d.putClientProperty("maxw", 250); e.add(d);
            terms.add(e);
        }
        terms.add(Ui.link("Open the glossary", () -> { tab = "glossary"; query = ""; app.navigate("learn"); }), false);
        v.add(terms);
        Ui.Card list = Kit.card("All lessons", null);
        int n = 1;
        for (Learn.Lesson x : L().lessons) {
            boolean cur = x.id().equals(l.id()), done = s().lessonDone(x.id());
            Ui.HBox h = new Ui.HBox(8);
            h.add(Ui.text(done ? "✓" : String.valueOf(n), Theme.mono(12f), done ? t.buy : t.muted));
            Ui.Btn b = Ui.link(x.title(), () -> open(x.id())); if (cur) b.setForeground(t.ink);
            b.setFont(cur ? Theme.semibold(13f) : Theme.body(13f));
            h.add(b); list.add(h); n++;
        }
        v.add(list);
        return v;
    }

    private JComponent quiz(Learn.Lesson l) {
        Theme t = Theme.T;
        Ui.Card c = Kit.card("Check your understanding", null);
        int answered = 0, right = 0;
        for (int qi = 0; qi < l.quiz().size(); qi++) {
            Learn.Question q = l.quiz().get(qi); int pick = picked[qi]; int[] order = Learn.order(l, qi);
            Ui.VBox qb = new Ui.VBox(8);
            JTextArea qt = Ui.para((qi + 1) + ". " + q.q(), Theme.semibold(14.5f), t.ink); qt.putClientProperty("maxw", 680); qb.add(qt);
            for (int oi : order) {
                final int fqi = qi, foi = oi;
                qb.add(option(q.options().get(oi), pick < 0 ? 0 : oi == q.answer() ? 1 : oi == pick ? 2 : 3, pick < 0 ? () -> { picked[fqi] = foi; draw(); recordIfDone(l); } : null), false);
            }
            if (pick >= 0) {
                answered++; boolean ok = pick == q.answer(); if (ok) right++;
                JTextArea why = Ui.para((ok ? "Right. " : "Not quite. ") + q.why(), Theme.body(13.5f), ok ? t.buy : t.ink); why.putClientProperty("maxw", 660); qb.add(why);
            }
            c.add(qb);
        }
        if (answered == l.quiz().size()) {
            Ui.HBox h = new Ui.HBox(12);
            boolean pass = right * 3 >= 2 * l.quiz().size();
            h.add(Ui.text("You got " + right + " of " + l.quiz().size() + ". " + (pass ? "Lesson complete." : "Review the lesson and try again."), Theme.semibold(14f), pass ? t.buy : t.warn));
            h.glue();
            h.add(Ui.small("Retake quiz", () -> { Arrays.fill(picked, -1); draw(); }));
            int i = L().lessons.indexOf(l);
            if (i + 1 < L().lessons.size()) { Learn.Lesson nx = L().lessons.get(i + 1); h.add(Ui.primary("Next lesson", () -> open(nx.id()))); }
            c.add(h);
        }
        return c;
    }
    private void recordIfDone(Learn.Lesson l) {
        for (int p : picked) if (p < 0) return;
        int right = 0; for (int i = 0; i < picked.length; i++) if (picked[i] == l.quiz().get(i).answer()) right++;
        double sc = (double) right / picked.length;
        if (sc > s().lessons.getOrDefault(l.id(), -1.0)) { s().lessons.put(l.id(), sc); app.changed(); }
    }
    /** state: 0 open, 1 correct answer, 2 wrong pick, 3 other option after answering. */
    private JComponent option(String text, int state, Runnable onPick) {
        Theme t = Theme.T;
        JPanel p = new JPanel(new BorderLayout()) {
            boolean hov;
            { if (onPick != null) { setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); setFocusable(true);
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { onPick.run(); }
                    @Override public void mouseEntered(MouseEvent e) { hov = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hov = false; repaint(); } });
                addKeyListener(new KeyAdapter() { @Override public void keyPressed(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) onPick.run(); } }); } }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g);
                Color bg = state == 1 ? t.buyBg : state == 2 ? t.sellBg : hov ? t.soft : t.panel, bd = state == 1 ? t.buy : state == 2 ? t.sell : hov || isFocusOwner() ? t.accent : t.line;
                g2.setColor(bg); g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.setColor(bd); g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10); g2.dispose();
            }
        };
        p.setOpaque(false); p.setBorder(new EmptyBorder(9, 14, 9, 14));
        String mark = state == 1 ? "✓  " : state == 2 ? "✗  " : "";
        JTextArea a = Ui.para(mark + text, Theme.body(14f), state == 3 ? t.muted : t.ink); a.putClientProperty("maxw", 640);
        if (onPick != null) a.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { onPick.run(); } });
        p.add(a);
        p.setMaximumSize(new Dimension(700, 400));
        JPanel wrap = new JPanel(new BorderLayout()) { @Override public Dimension getPreferredSize() { Dimension d = super.getPreferredSize(); return new Dimension(Math.min(700, d.width), d.height); } };
        wrap.setOpaque(false); wrap.add(p);
        return wrap;
    }

    // ------------------------------------------------------------------ glossary
    private final Slot terms = new Slot();
    private JComponent glossary() {
        Theme t = Theme.T; Ui.VBox v = new Ui.VBox(16);
        JTextField f = Ui.field(query, 24); f.setFont(Theme.body(14f)); f.setPreferredSize(new Dimension(320, 36));
        f.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void ch() { query = f.getText(); drawTerms(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { ch(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { ch(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { ch(); }
        });
        Ui.HBox h = new Ui.HBox(10); h.add(Ui.text("Search", Theme.medium(13.5f), t.muted)); h.add(f); h.add(Ui.text(L().glossary.size() + " terms", Theme.body(12.5f), t.muted));
        v.add(h, false);
        v.add(terms);
        drawTerms();
        SwingUtilities.invokeLater(() -> { f.requestFocusInWindow(); f.setCaretPosition(f.getText().length()); });
        return v;
    }
    private void drawTerms() {
        Theme t = Theme.T; String q = query.trim().toLowerCase(Locale.ROOT);
        List<Learn.Term> exact = new ArrayList<>(), name = new ArrayList<>(), def = new ArrayList<>();
        for (Learn.Term x : L().glossary) {
            String n = x.term().toLowerCase(Locale.ROOT);
            if (q.isEmpty() || n.contains(q)) (n.equals(q) ? exact : name).add(x);
            else if (x.def().toLowerCase(Locale.ROOT).contains(q)) def.add(x);
        }
        List<Learn.Term> all = new ArrayList<>(exact); all.addAll(name); all.addAll(def);
        Ui.Card c = new Ui.Card(0);
        if (all.isEmpty()) c.add(Kit.empty("No terms match \"" + query + "\"."));
        boolean first = true;
        for (Learn.Term x : all) {
            Ui.VBox e = new Ui.VBox(3) {
                final boolean div = c.getComponentCount() > 0;
                @Override protected void paintComponent(Graphics g) { if (div) { g.setColor(t.soft); g.fillRect(0, 0, getWidth(), 1); } }
            };
            e.setOpaque(false); e.setBorder(new EmptyBorder(12, 0, 12, 0));
            boolean hit = exact.contains(x);
            e.add(Ui.text(x.term(), Theme.semibold(hit ? 16f : 14.5f), hit ? t.accent : t.ink), false);
            JTextArea d = Ui.para(x.def(), Theme.body(14f), t.ink); d.putClientProperty("maxw", 760); e.add(d);
            List<String> in = new ArrayList<>();
            for (Learn.Lesson l : L().lessons) if (l.terms().contains(x.term())) in.add(l.id());
            if (!in.isEmpty()) {
                Ui.HBox ls = new Ui.HBox(10); ls.add(Ui.text("Lesson:", Theme.body(12.5f), t.muted));
                for (String id : in.subList(0, Math.min(2, in.size()))) ls.add(Ui.link(L().lesson(id).title(), () -> { tab = "lessons"; open(id); }));
                e.add(ls, false);
            }
            c.add(e);
            first = false;
        }
        terms.set(c);
    }
}

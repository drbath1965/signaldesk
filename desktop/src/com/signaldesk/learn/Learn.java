package com.signaldesk.learn;

import com.signaldesk.json.Json;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Lessons, quizzes, glossary and the "?" explanations, read once from content/learn.json inside the jar. */
public final class Learn {
    public record Section(String heading, String text) {}
    public record Question(String q, List<String> options, int answer, String why) {}
    public record Lesson(String id, String level, String title, int minutes, String summary, List<Section> sections,
                         List<String> takeaways, List<Question> quiz, List<String> terms) {}
    public record Term(String term, String def) {}
    public record Explain(String title, String body, String term) {}

    private static Learn I;
    public final List<Lesson> lessons = new ArrayList<>();
    public final List<Term> glossary = new ArrayList<>();
    public final Map<String, Explain> explain = new HashMap<>();

    public static synchronized Learn get() {
        if (I == null) I = load();
        return I;
    }
    private static Learn load() {
        Learn l = new Learn();
        try (InputStream in = Learn.class.getResourceAsStream("/content/learn.json")) {
            if (in == null) return l;
            Map<String, Object> m = Json.obj(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            for (Object o : Json.arr(m, "lessons")) {
                Map<String, Object> x = Json.obj(o);
                List<Section> ss = new ArrayList<>(); for (Object s : Json.arr(x, "sections")) ss.add(new Section(Json.str(Json.obj(s), "h", ""), Json.str(Json.obj(s), "p", "")));
                List<Question> qs = new ArrayList<>();
                for (Object q : Json.arr(x, "quiz")) { Map<String, Object> y = Json.obj(q); qs.add(new Question(Json.str(y, "q", ""), strs(Json.arr(y, "options")), (int) Json.num(y, "answer", 0), Json.str(y, "why", ""))); }
                l.lessons.add(new Lesson(Json.str(x, "id", ""), Json.str(x, "level", ""), Json.str(x, "title", ""), (int) Json.num(x, "minutes", 5),
                    Json.str(x, "summary", ""), ss, strs(Json.arr(x, "takeaways")), qs, strs(Json.arr(x, "terms"))));
            }
            for (Object o : Json.arr(m, "glossary")) l.glossary.add(new Term(Json.str(Json.obj(o), "term", ""), Json.str(Json.obj(o), "def", "")));
            for (Map.Entry<String, Object> e : Json.obj(m, "explain").entrySet()) {
                Map<String, Object> y = Json.obj(e.getValue());
                l.explain.put(e.getKey(), new Explain(Json.str(y, "title", ""), Json.str(y, "body", ""), Json.str(y, "term", null)));
            }
        } catch (Exception ignored) {}
        return l;
    }
    private static List<String> strs(List<Object> a) { List<String> r = new ArrayList<>(); for (Object o : a) r.add(String.valueOf(o)); return r; }

    public Lesson lesson(String id) { for (Lesson x : lessons) if (x.id().equals(id)) return x; return null; }
    public Term term(String t) { for (Term x : glossary) if (x.term().equalsIgnoreCase(t)) return x; return null; }
    public List<String> levels() { List<String> r = new ArrayList<>(); for (Lesson x : lessons) if (!r.contains(x.level())) r.add(x.level()); return r; }
    /** Answer options in a fixed shuffled order per question, so the right answer isn't always in the same spot. */
    public static int[] order(Lesson l, int qi) {
        int n = l.quiz().get(qi).options().size(); Integer[] o = new Integer[n]; for (int i = 0; i < n; i++) o[i] = i;
        Collections.shuffle(Arrays.asList(o), new Random((l.id() + qi).hashCode()));
        int[] r = new int[n]; for (int i = 0; i < n; i++) r[i] = o[i]; return r;
    }
}

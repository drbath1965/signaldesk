package com.signaldesk.ui.views;

import com.signaldesk.calc.*;
import com.signaldesk.model.SignalParams;
import com.signaldesk.signals.*;
import com.signaldesk.store.PriceDownloader;
import com.signaldesk.store.PriceStore;
import com.signaldesk.ui.*;
import com.signaldesk.ui.EditTable.Col;
import com.signaldesk.ui.PriceService;
import java.awt.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class SignalsView extends View {
    private static PriceSeries lastShown;
    private static int lastRange = Integer.MAX_VALUE;
    private PriceSeries data; private boolean sample;
    private SignalEngine eng;
    private final Slot header = new Slot(), notice = new Slot(), verdict = new Slot(), stats = new Slot(), trades = new Slot(), rules = new Slot(), chips = new Slot();
    private final JLabel readout = Ui.text(" ", Theme.mono(12f), Theme.T.muted), readSig = Ui.text(" ", Theme.mono(12f), Theme.T.muted);
    private PriceChart chart;
    private JTextField symField;
    private JLabel paramHint;
    private Timer debounce, autoRefresh, searchDelay;
    private final String requested;
    private final JLabel updated = Ui.text(" ", Theme.body(12.5f), Theme.T.muted);
    private JPopupMenu suggest;
    private boolean typing = true;
    private String loading;

    public SignalsView(MainFrame app, String requested) { super(app); this.requested = requested; }

    @Override public JComponent build() {
        if (lastShown != null) { data = lastShown; sample = "DEMO".equals(data.symbol()); }
        else {
            List<String> saved = app.prices().symbols();
            PriceSeries p = saved.isEmpty() ? null : app.prices().get(saved.get(0));
            if (p != null) { data = p; sample = false; } else { data = SignalEngine.sample(); sample = true; }
        }
        Ui.VBox root = new Ui.VBox(20);
        Ui.HBox top = new Ui.HBox(24);
        top.grow(header);
        symField = Ui.field(sample ? "" : data.symbol(), 9);
        symField.setFont(Theme.mono(18f));
        symField.setToolTipText("Type a ticker such as MSFT, or a company name such as Microsoft");
        symField.addActionListener(e -> { hideSuggest(); analyze(symField.getText(), false); });
        installSuggestions();
        Ui.VBox form = new Ui.VBox(6);
        form.add(Ui.caps("Stock symbol or company"), false);
        Ui.HBox fr = new Ui.HBox(8); symField.setPreferredSize(new Dimension(170, 40)); fr.add(symField); fr.add(Ui.primary("Analyze", () -> analyze(symField.getText(), false)));
        form.add(fr, false);
        form.add(chips, false);
        top.add(form);
        root.add(top);
        Ui.HBox tools = new Ui.HBox(12);
        tools.add(Ui.btn("Refresh now", () -> { if (!sample) analyze(data.symbol(), true); else app.toast("Pick a stock first.", null); }));
        tools.add(updated);
        tools.glue();
        tools.add(Ui.link("Show practice sample", () -> show(SignalEngine.sample())));
        root.add(tools);
        root.add(notice);
        root.add(verdict);
        root.add(stats);

        Ui.Card cc = new Ui.Card(10);
        Ui.HBox ch = new Ui.HBox(12);
        ch.grow(Kit.legend("Close", Theme.T.ink, "Fast SMA", Theme.T.accent, "Slow SMA", Theme.T.slow, "Buy", Theme.T.buy, "Sell", Theme.T.sell));
        int[] ranges = {126, 252, 504, Integer.MAX_VALUE}; String[] rl = {"6M", "1Y", "2Y", "All"};
        int sel = 3; for (int i = 0; i < 4; i++) if (ranges[i] == lastRange) sel = i;
        Slot seg = new Slot();
        java.util.function.IntConsumer[] pick = new java.util.function.IntConsumer[1];
        pick[0] = i -> { lastRange = ranges[i]; chart.range(ranges[i]); seg.set(Ui.seg(rl, i, k -> pick[0].accept(k))); };
        chart = new PriceChart(this::hoverAt);
        seg.set(Ui.seg(rl, sel, k -> pick[0].accept(k)));
        ch.add(seg);
        cc.add(ch);
        Ui.VBox ro = new Ui.VBox(2); ro.add(readout); ro.add(readSig); cc.add(ro);
        chart.range(lastRange);
        cc.add(chart);

        Ui.Card settings = Kit.card("Strategy settings", null);
        SignalParams P = s().sig;
        paramHint = Ui.text("Results update as you type.", Theme.body(12.5f), Theme.T.muted);
        settings.add(group("Trend", param("Fast SMA (days)", P.fast, (p, v) -> p.fast = (int) Math.round(v)), param("Slow SMA (days)", P.slow, (p, v) -> p.slow = (int) Math.round(v))));
        settings.add(group("Momentum", param("RSI period", P.rsiN, (p, v) -> p.rsiN = (int) Math.round(v)), param("Oversold below", P.os, (p, v) -> p.os = (int) Math.round(v)), param("Overbought above", P.ob, (p, v) -> p.ob = (int) Math.round(v))));
        settings.add(group("Risk & cost", param("Trailing stop (%)", P.stop, (p, v) -> p.stop = v), param("Cost per trade (bps)", P.fee, (p, v) -> p.fee = v)));
        settings.add(paramHint);
        settings.add(Ui.small("Reset to defaults", () -> { s().sig = new SignalParams(); app.changed(); app.rebuild(); }), false);
        root.add(Ui.rail(cc, settings, 320));
        root.add(trades);
        root.add(rules);
        show(data);
        if (requested != null) SwingUtilities.invokeLater(() -> analyze(requested, false));
        else if (!sample) SwingUtilities.invokeLater(() -> analyze(data.symbol(), false));
        autoRefresh = new Timer(5 * 60 * 1000, e -> {
            if (chart == null || !chart.isShowing()) { ((Timer) e.getSource()).stop(); return; }
            if (!sample && loading == null) refreshQuietly(data.symbol());
        });
        autoRefresh.start();
        return root;
    }

    private JComponent group(String caps, JComponent... rows) { Ui.VBox v = new Ui.VBox(8); v.add(Ui.caps(caps), false); for (JComponent r : rows) v.add(r); return v; }
    private JComponent param(String label, double v, java.util.function.ObjDoubleConsumer<SignalParams> set) {
        return Ui.formRow(label, null, Ui.numberField(v, 1, x -> {
            SignalParams p = s().sig.copy(); set.accept(p, x);
            if (p.fast < 2 || p.slow < 5 || p.rsiN < 2 || p.stop <= 0 || p.fee < 0) { hint("Check " + label.toLowerCase() + ".", true); return; }
            if (p.fast >= p.slow) { hint("Fast SMA must be shorter than slow SMA.", true); return; }
            if (p.os >= p.ob) { hint("Oversold must be lower than overbought.", true); return; }
            hint("Results update as you type.", false);
            s().sig = p; app.changed();
            if (debounce != null) debounce.stop();
            debounce = new Timer(300, e -> show(data)); debounce.setRepeats(false); debounce.start();
        }), 96);
    }
    private void hint(String s, boolean err) { paramHint.setText(s); paramHint.setForeground(err ? Theme.T.sell : Theme.T.muted); }

    private void show(PriceSeries d) {
        data = d; sample = "DEMO".equals(d.symbol()); lastShown = d;
        eng = SignalEngine.run(d, s().sig);
        Theme t = Theme.T; double[] c = d.close(); int L = c.length - 1;
        double ch = c[L] / c[L - 1] - 1;
        Ui.VBox h = new Ui.VBox(2);
        h.add(Ui.caps("Stock signals"), false);
        Ui.HBox line = new Ui.HBox(14); line.add(Ui.h1(d.symbol())); line.add(Ui.mono(Fmt.money2(c[L]), 20f, t.ink)); line.add(Ui.mono(Fmt.pct(ch, 2) + " on the day", 14f, Kit.signColor(ch)));
        h.add(line, false);
        String nm = sample ? "" : app.prices().name(d.symbol());
        h.add(Ui.text((nm.isBlank() ? "" : nm + "   ·   ") + "Last price " + Dates.dayLong(d.lastDate()), Theme.body(13f), t.muted), false);
        header.set(h);
        updateFreshness();
        if (symField != null && !sample && !symField.isFocusOwner()) { typing = false; symField.setText(d.symbol()); typing = true; }
        // chips
        Ui.HBox cp = new Ui.HBox(6);
        List<String> saved = app.prices().symbols();
        if (saved.isEmpty()) cp.add(Ui.hint("Type any US ticker, then press Analyze."));
        else { cp.add(Ui.text("Saved:", Theme.body(12.5f), t.muted)); for (String sym : saved.subList(0, Math.min(8, saved.size()))) cp.add(chip(sym, !sample && sym.equals(d.symbol()))); }
        chips.set(cp);
        notice.set(sample ? noticeBand("Practice sample. DEMO is a made-up price series. Type a ticker or company name above and its real prices download and chart automatically.") : new JPanel() {{ setOpaque(false); setPreferredSize(new Dimension(0, 0)); }});
        if (eng.error != null) {
            verdict.set(Ui.para(eng.error, Theme.body(14f), t.sell)); stats.set(new JPanel()); trades.set(new JPanel()); chart.set(null); rules.set(rulesCards()); return;
        }
        verdict.set(verdictPanel());
        SignalEngine.Stats S = eng.stats; double beat = S.ret() - S.bh();
        stats.set(Ui.kpis(
            new Ui.Kpi("Strategy return", Fmt.pct(S.ret()), Kit.signColor(S.ret()), "Buy & hold " + Fmt.pct(S.bh()) + " over " + Fmt.num(S.years(), 1) + " yrs"),
            new Ui.Kpi("Vs buy & hold", Fmt.pct(beat), Kit.signColor(beat), beat >= 0 ? "Strategy came out ahead" : "Holding would have won"),
            new Ui.Kpi("Annualized", Fmt.pct(S.cagr()), "Buy & hold " + Fmt.pct(S.bhCagr())),
            new Ui.Kpi("Max drawdown", Fmt.pct(S.dd()), t.sell, "Buy & hold " + Fmt.pct(S.bhDd())),
            new Ui.Kpi("Win rate", S.win() == null ? "—" : Math.round(S.win() * 100) + "%", S.trades() + " closed trades, avg " + (S.avg() == null ? "—" : Fmt.pct(S.avg()))),
            new Ui.Kpi("Time invested", Math.round(S.exposure() * 100) + "%", "Rest of the time in cash")));
        chart.set(eng);
        hoverAt(-1);
        List<SignalEngine.Trade> rows = new ArrayList<>(eng.trades); java.util.Collections.reverse(rows);
        Ui.Card tc = Kit.card("Trade log", Ui.muted(rows.size() + " trades, newest first"));
        EditTable<SignalEngine.Trade> et = new EditTable<>(rows, null, null, "No trades with these settings. Try a shorter slow SMA or a wider RSI range.",
            Col.<SignalEngine.Trade>label("Bought", 110, x -> Dates.dayLong(d.dates().get(x.entryI())), null),
            Col.<SignalEngine.Trade>calc("Entry", 90, x -> Fmt.money2(x.entryPx()), null),
            Col.<SignalEngine.Trade>label("Sold", 110, x -> x.open() ? "Open" : Dates.dayLong(d.dates().get(x.exitI())), x -> x.open() ? t.muted : null),
            Col.<SignalEngine.Trade>calc("Exit", 90, x -> Fmt.money2(x.exitPx()), null),
            Col.<SignalEngine.Trade>calc("Return", 80, x -> Fmt.pct(x.ret()), x -> Kit.signColor(x.ret())),
            Col.<SignalEngine.Trade>calc("Days", 60, x -> String.valueOf((x.open() ? d.size() - 1 : x.exitI()) - x.entryI()), null),
            Col.<SignalEngine.Trade>label("Why it bought", 260, SignalEngine.Trade::why, null),
            Col.<SignalEngine.Trade>label("Why it sold", 260, SignalEngine.Trade::exitWhy, null));
        tc.add(et.wrap());
        trades.set(tc);
        rules.set(rulesCards());
    }

    private JComponent chip(String sym, boolean on) {
        Ui.Btn b = new Ui.Btn(sym, Ui.Btn.Kind.PLAIN, true) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g); Theme t = Theme.T; int h = getHeight();
                g2.setColor(t.panel); g2.fillRoundRect(0, 0, getWidth() - 1, h - 1, h, h);
                g2.setColor(on ? t.accent : t.line); g2.drawRoundRect(0, 0, getWidth() - 1, h - 1, h, h);
                g2.setFont(getFont()); g2.setColor(on ? t.accent : t.ink); FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent()); g2.dispose();
            }
        };
        b.setFont(Theme.mono(12f)); b.setBorder(new EmptyBorder(2, 10, 2, 10));
        b.addActionListener(e -> analyze(sym, false));
        return b;
    }
    private JComponent noticeBand(String s) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) { g.setColor(Theme.T.slow); g.fillRect(0, 0, 3, getHeight()); }
        };
        p.setOpaque(false); p.setBorder(new EmptyBorder(3, 14, 3, 0));
        JTextArea a = Ui.para(s, Theme.body(13.5f), Theme.T.muted); a.putClientProperty("maxw", 820); p.add(a);
        return p;
    }

    private JComponent verdictPanel() {
        Theme t = Theme.T; SignalEngine.Verdict v = eng.verdict();
        Color bg, fg; String word;
        switch (v.call()) {
            case BUY -> { bg = t.buyBg; fg = t.buy; word = "Buy"; }
            case SELL -> { bg = t.sellBg; fg = t.sell; word = "Sell"; }
            case HOLD -> { bg = t.holdBg; fg = t.accent; word = "Hold"; }
            default -> { bg = t.soft; fg = t.muted; word = "Wait"; }
        }
        JPanel outer = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g); g2.setColor(t.panel); g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.setColor(t.line); g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14); g2.dispose();
            }
        };
        outer.setOpaque(false);
        Ui.VBox call = new Ui.VBox(6) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Ui.aa(g); g2.setColor(bg); g2.fillRoundRect(1, 1, getWidth(), getHeight() - 2, 13, 13); g2.fillRect(getWidth() - 14, 1, 14, getHeight() - 2); g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(310, super.getPreferredSize().height); }
        };
        call.setBorder(new EmptyBorder(22, 24, 22, 24));
        call.add(Ui.caps("Today's call"), false);
        call.add(Ui.text(word, Theme.display(58f), fg), false);
        JTextArea why = Ui.para(v.why(), Theme.body(14f), t.ink); why.putClientProperty("maxw", 262); call.add(why);
        call.end();
        outer.add(call, BorderLayout.WEST);
        double[] c = eng.data.close(); int L = c.length - 1; SignalParams P = eng.p;
        boolean trend = eng.fast[L] > eng.slow[L], mac = eng.macd[L] > eng.macdSig[L]; double rv = eng.rsi[L];
        JPanel rd = new JPanel(new GridLayout(2, 2, 24, 16)); rd.setOpaque(false); rd.setBorder(new EmptyBorder(20, 26, 20, 22));
        rd.add(reading("Trend · SMA " + P.fast + " vs " + P.slow, Fmt.pct(eng.fast[L] / eng.slow[L] - 1, 2), trend ? Ui.Tone.POS : Ui.Tone.NEG, trend ? "Fast above slow" : "Fast below slow"));
        rd.add(reading("Momentum · RSI " + P.rsiN, Fmt.num(rv, 1), rv >= P.ob ? Ui.Tone.NEG : rv <= P.os ? Ui.Tone.POS : Ui.Tone.NEU, rv >= P.ob ? "Overbought" : rv <= P.os ? "Oversold" : "Neutral range"));
        rd.add(reading("MACD 12/26/9", Fmt.num(eng.macd[L] - eng.macdSig[L], 2), mac ? Ui.Tone.POS : Ui.Tone.NEG, mac ? "Above signal line" : "Below signal line"));
        rd.add(reading("Price vs slow SMA", Fmt.pct(c[L] / eng.slow[L] - 1), c[L] > eng.slow[L] ? Ui.Tone.POS : Ui.Tone.NEG, c[L] > eng.slow[L] ? "Above" : "Below"));
        outer.add(rd, BorderLayout.CENTER);
        return outer;
    }
    private JComponent reading(String label, String value, Ui.Tone tone, String pill) {
        Ui.VBox v = new Ui.VBox(4); v.add(Ui.caps(label), false); v.add(Ui.mono(value, 17f, Theme.T.ink), false); v.add(Ui.pill(pill, tone), false); v.end(); return v;
    }

    private void hoverAt(int i) {
        if (eng == null || eng.error != null) return;
        int L = eng.data.size() - 1; int k = i < 0 ? L : i; SignalParams P = eng.p;
        readout.setText(Dates.dayLong(eng.data.dates().get(k)) + "    Close " + Fmt.num(eng.data.close()[k], 2) + "    SMA " + P.fast + " " + Fmt.num(eng.fast[k], 2)
            + "    SMA " + P.slow + " " + Fmt.num(eng.slow[k], 2) + "    RSI " + Fmt.num(eng.rsi[k], 1) + "    Strategy " + Fmt.money(eng.eq[k] * 1e4));
        readout.setForeground(Theme.T.ink);
        SignalEngine.Signal s = eng.signalAt(k);
        readSig.setText(s == null ? " " : (s.buy() ? "BUY: " : "SELL: ") + s.why());
        readSig.setForeground(s == null ? Theme.T.muted : s.buy() ? Theme.T.buy : Theme.T.sell);
    }

    private JComponent rulesCards() {
        SignalParams P = s().sig; Theme t = Theme.T;
        Ui.Card a = Kit.card("How the algorithm decides", null);
        String[][] rs = {
            {"Buy on a trend turn.", "The " + P.fast + "-day average crosses above the " + P.slow + "-day average, MACD is above its signal line, and RSI is under " + P.ob + "."},
            {"Buy the dip in an uptrend.", "With the fast average above the slow, RSI drops below " + P.os + " and then climbs back above it."},
            {"Sell on a trailing stop.", "Price closes " + Ui.fmtNum(P.stop, 1) + "% or more below the highest close since entry."},
            {"Sell when the trend breaks.", "The fast average crosses back below the slow."},
            {"Sell when momentum fades.", "RSI climbs above " + P.ob + " and then falls back under it."}};
        int n = 1;
        for (String[] r : rs) {
            Ui.HBox h = new Ui.HBox(10); h.add(Ui.mono(n++ + ".", 13f, t.muted));
            Ui.VBox v = new Ui.VBox(1); v.add(Ui.text(r[0], Theme.semibold(13.5f), t.ink), false); v.add(Ui.para(r[1], Theme.body(13.5f), t.muted)); h.grow(v);
            a.add(h);
        }
        a.add(Ui.hint("Orders fill at the next session's close, so the backtest never trades on a price it couldn't have known. The strategy is fully in the stock or fully in cash; it never shorts or uses margin."));
        Ui.Card b = Kit.card("Read before trading", null);
        b.add(Ui.para("This is a rules-based screening tool, not financial advice. A good backtest on one stock often comes from tuning settings to that stock's past, and it can fail going forward. Compare against buy-and-hold, test the same settings on several tickers, and consider paper trading before using real money. Taxes and slippage beyond the per-trade cost are not modeled."));
        return Ui.cols(18, Ui.top(a), Ui.top(b));
    }

    // ------------------------------------------------------------------ getting prices
    /** Show a symbol right away from saved prices if there are any, and bring it up to date in the background. */
    private void analyze(String raw, boolean force) {
        String sym = PriceStore.clean(raw);
        if (sym.isEmpty() || sym.equals("DEMO")) { if (sym.isEmpty()) { symField.requestFocusInWindow(); app.toast("Type a ticker or company name, such as MSFT or Microsoft.", null); } return; }
        PriceSeries cached = app.prices().get(sym);
        if (cached != null && (sample || !sym.equals(data.symbol()))) show(cached);
        if (!force && cached != null && app.prices().isFresh(sym)) return;
        loading = sym; updateFreshness();
        if (cached == null) loadingState(sym);
        app.priceService().fetch(sym, true, p -> {
            loading = null;
            if (!app.section().equals("signals")) return;
            show(p);
        }, ex -> {
            loading = null; updateFreshness();
            if (!app.section().equals("signals")) return;
            if (cached != null) { app.toast("Couldn't update " + sym + ". Showing prices from " + ago(app.prices().fetchedAt(sym)) + ".", null); return; }
            if (sample || data == null) show(data == null ? SignalEngine.sample() : data);
            downloadHelp(sym, PriceService.explain(ex), ex instanceof PriceDownloader.NotFound);
        });
    }
    private void refreshQuietly(String sym) {
        loading = sym; updateFreshness();
        app.priceService().fetch(sym, true, p -> { loading = null; if (sym.equals(data.symbol()) && app.section().equals("signals")) show(p); }, ex -> { loading = null; updateFreshness(); });
    }
    private void loadingState(String sym) {
        Ui.Card c = new Ui.Card();
        c.add(Ui.h2("Downloading " + sym + "…"));
        c.add(Ui.para("Getting five years of daily prices. This usually takes a second or two."));
        verdict.set(c);
        if (symField != null) { typing = false; symField.setText(sym); typing = true; }
    }
    private void updateFreshness() {
        if (sample) { updated.setText("Showing the practice sample"); updated.setForeground(Theme.T.muted); return; }
        if (loading != null) { updated.setText("Updating " + loading + "…"); updated.setForeground(Theme.T.accent); return; }
        java.time.Instant f = app.prices().fetchedAt(data.symbol());
        updated.setText(f == null ? "Not updated yet" : "Prices updated " + ago(f) + " from Yahoo Finance · refreshes every 5 minutes while open");
        updated.setForeground(Theme.T.muted);
    }
    static String ago(java.time.Instant t) {
        if (t == null) return "earlier";
        long m = java.time.Duration.between(t, java.time.Instant.now()).toMinutes();
        if (m < 1) return "just now";
        if (m < 60) return m + " min ago";
        java.time.LocalDateTime lt = java.time.LocalDateTime.ofInstant(t, java.time.ZoneId.systemDefault());
        return (lt.toLocalDate().equals(java.time.LocalDate.now()) ? "today" : Dates.dayShort(lt.toLocalDate().toString())) + " at " + lt.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US));
    }
    private void downloadHelp(String sym, String why, boolean notFound) {
        Object[] opts = notFound ? new Object[]{"Search by company name", "Cancel"} : new Object[]{"Try again", "Load a price file instead", "Cancel"};
        int r = JOptionPane.showOptionDialog(app, why, "Prices for " + sym, JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, opts, opts[0]);
        if (notFound) { if (r == 0) { symField.requestFocusInWindow(); symField.selectAll(); } return; }
        if (r == 0) analyze(sym, true);
        else if (r == 1) loadFile(sym);
    }

    // ------------------------------------------------------------------ suggestions while typing
    private void installSuggestions() {
        suggest = new JPopupMenu(); suggest.setFocusable(false);
        suggest.setBorder(BorderFactory.createLineBorder(Theme.T.line));
        searchDelay = new Timer(300, e -> runSearch()); searchDelay.setRepeats(false);
        symField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void ch() { if (typing && symField.isFocusOwner()) searchDelay.restart(); }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { ch(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { ch(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { ch(); }
        });
        symField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) hideSuggest();
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DOWN && suggest.isVisible() && suggest.getComponentCount() > 0) {
                    MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{suggest, (MenuElement) suggest.getComponent(0)});
                }
            }
        });
        symField.addFocusListener(new java.awt.event.FocusAdapter() { @Override public void focusLost(java.awt.event.FocusEvent e) { SwingUtilities.invokeLater(() -> { if (!suggest.isVisible() || !symField.isShowing()) return; }); } });
    }
    private void hideSuggest() { if (suggest != null) suggest.setVisible(false); if (searchDelay != null) searchDelay.stop(); }
    private void runSearch() {
        String q = symField.getText().trim();
        if (q.length() < 1) { hideSuggest(); return; }
        new SwingWorker<java.util.List<PriceDownloader.Match>, Void>() {
            @Override protected java.util.List<PriceDownloader.Match> doInBackground() throws Exception { return PriceDownloader.search(q); }
            @Override protected void done() {
                java.util.List<PriceDownloader.Match> ms;
                try { ms = get(); } catch (Exception ex) { return; }
                if (!q.equals(symField.getText().trim()) || !symField.isShowing()) return;
                suggest.removeAll();
                if (ms.isEmpty()) { suggest.setVisible(false); return; }
                for (PriceDownloader.Match m : ms) {
                    JMenuItem it = new JMenuItem() {
                        @Override protected void paintComponent(Graphics g) {
                            Graphics2D g2 = Ui.aa(g); Theme t = Theme.T; boolean on = isArmed() || getModel().isRollover();
                            g2.setColor(on ? t.soft : t.panel); g2.fillRect(0, 0, getWidth(), getHeight());
                            g2.setFont(Theme.monoBold(13f)); g2.setColor(t.ink); g2.drawString(m.symbol(), 12, 20);
                            g2.setFont(Theme.body(13f)); g2.setColor(t.ink);
                            String nm = m.name(); FontMetrics fm = g2.getFontMetrics(); int max = getWidth() - 110 - 150;
                            while (fm.stringWidth(nm) > max && nm.length() > 3) nm = nm.substring(0, nm.length() - 2) + "…";
                            g2.drawString(nm, 110, 20);
                            g2.setFont(Theme.body(12f)); g2.setColor(t.muted); fm = g2.getFontMetrics();
                            String ex = (m.exchange() + " · " + m.type()).replaceAll("^ · ", "");
                            g2.drawString(ex, getWidth() - fm.stringWidth(ex) - 12, 20); g2.dispose();
                        }
                    };
                    it.setPreferredSize(new Dimension(520, 30));
                    it.addActionListener(e -> { hideSuggest(); typing = false; symField.setText(m.symbol()); typing = true; analyze(m.symbol(), false); });
                    suggest.add(it);
                }
                suggest.pack();
                Component row = symField.getParent();
                int right = row != null ? row.getWidth() - symField.getX() : symField.getWidth();
                suggest.show(symField, right - suggest.getPreferredSize().width, symField.getHeight() + 4);
                symField.requestFocusInWindow();
            }
        }.execute();
    }

    private void loadFile() { loadFile(null); }
    private void loadFile(String sym) {
        JFileChooser fc = new JFileChooser(); fc.setDialogTitle("Load a daily price file (.csv)");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV price files", "csv"));
        java.io.File dl = new java.io.File(System.getProperty("user.home"), "Downloads"); if (dl.isDirectory()) fc.setCurrentDirectory(dl);
        if (fc.showOpenDialog(app) != JFileChooser.APPROVE_OPTION) return;
        try {
            String text = Files.readString(fc.getSelectedFile().toPath(), StandardCharsets.UTF_8);
            String name = sym != null ? sym : PriceCsv.symbolFromFileName(fc.getSelectedFile().getName());
            if (name.equals("TICKER")) {
                String typed = PriceStore.clean(symField.getText());
                String ask = (String) JOptionPane.showInputDialog(app, "Which stock symbol is this file for?", "Symbol", JOptionPane.QUESTION_MESSAGE, null, null, typed);
                if (ask == null || PriceStore.clean(ask).isEmpty()) return;
                name = PriceStore.clean(ask);
            }
            PriceSeries p = PriceCsv.parse(text, name);
            app.prices().put(p); show(p); app.toast(name + " loaded", null);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(app, ex.getMessage(), "Couldn't read that file", JOptionPane.WARNING_MESSAGE);
        }
    }
}

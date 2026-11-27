package com.signaldesk.ui.views;

import com.signaldesk.calc.Dates;
import com.signaldesk.model.Practice;
import com.signaldesk.model.Studio;
import com.signaldesk.store.*;
import com.signaldesk.ui.*;
import java.awt.Desktop;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.*;

public final class DataView extends View {
    public DataView(MainFrame app) { super(app); }

    @Override public JComponent build() {
        Theme t = Theme.T;
        Ui.VBox root = new Ui.VBox(20);
        root.add(Kit.head("Backup & data", "Your data", null, null));
        Ui.Card where = Kit.card("Where your data lives", null);
        where.add(Ui.para("Everything is saved automatically to a file on this computer, a moment after each change. Nothing is sent anywhere; the only network traffic is downloading prices."));
        where.add(Ui.text(app.store().file().toString(), Theme.mono(13f), t.ink));
        where.add(Ui.hint("A dated copy goes into the backups folder once a day, and the last 14 are kept. Price histories live in the prices folder next to it, one CSV file per symbol."));
        where.add(Ui.btn("Open data folder", () -> {
            try { Files.createDirectories(AppPaths.root()); Desktop.getDesktop().open(AppPaths.root().toFile()); }
            catch (Exception ex) { JOptionPane.showMessageDialog(app, "Your data folder is:\n" + AppPaths.root()); }
        }), false);
        root.add(where);

        Ui.Card bk = Kit.card("Back up and restore", null);
        bk.add(Ui.para("A backup is one file holding your watchlist, practice portfolio, lesson progress, strategy settings and backlog. Backups from Signal Desk 1.x restore too: their investment symbols become your watchlist."));
        Ui.HBox r1 = new Ui.HBox(8);
        r1.add(Ui.primary("Export backup…", this::export)); r1.add(Ui.btn("Restore from backup…", this::restore)); r1.add(Ui.btn("Export trades (.csv)…", this::exportCsv));
        bk.add(r1);

        Ui.Card so = Kit.card("Start over", null);
        so.add(Ui.para("Reset one part at a time. Your backlog and saved prices are never touched."));
        Ui.HBox r2 = new Ui.HBox(8);
        r2.add(Ui.btn("Reset practice portfolio", () -> {
            if (!confirm("Erase every practice trade and start again with " + com.signaldesk.calc.Fmt.money(Practice.START_CASH) + " of pretend cash?")) return;
            s().practice = new Practice(); app.changed(); app.toast("Practice portfolio reset", null);
        }));
        r2.add(Ui.btn("Reset lesson progress", () -> {
            if (!confirm("Clear your quiz scores for every lesson?")) return;
            s().lessons.clear(); app.changed(); app.toast("Lesson progress cleared", null);
        }));
        r2.add(Ui.danger("Reset everything", () -> {
            if (!confirm("Reset the watchlist, practice portfolio, lesson progress and strategy settings? Your backlog stays. Yesterday's automatic backup is in the backups folder.")) return;
            Studio b = Studio.fresh(); b.backlog.clear(); b.backlog.addAll(s().backlog); b.theme = s().theme;
            app.replaceStudio(b, true); app.toast("Everything reset", null);
        }));
        so.add(r2);
        root.add(Ui.cols(18, Ui.top(bk), Ui.top(so)));
        return root;
    }
    private boolean confirm(String msg) {
        return JOptionPane.showConfirmDialog(app, msg, "Signal Desk", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }
    private File chooseSave(String name) {
        JFileChooser fc = new JFileChooser(); fc.setSelectedFile(new File(System.getProperty("user.home"), name));
        return fc.showSaveDialog(app) == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile() : null;
    }
    private void export() {
        File f = chooseSave("signal-desk-backup-" + Dates.today() + ".json"); if (f == null) return;
        try { StudioStore.exportTo(s(), f.toPath()); app.toast("Backup saved to " + f.getName(), null); }
        catch (Exception ex) { JOptionPane.showMessageDialog(app, "Couldn't save the backup: " + ex.getMessage()); }
    }
    private void restore() {
        JFileChooser fc = new JFileChooser(); fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Signal Desk backups (.json)", "json"));
        if (fc.showOpenDialog(app) != JFileChooser.APPROVE_OPTION) return;
        try {
            Studio in = StudioStore.importFrom(fc.getSelectedFile().toPath());
            if (!confirm("Replace your watchlist, practice portfolio, lesson progress and settings with this backup?")) return;
            if (in.backlog.isEmpty()) in.backlog.addAll(s().backlog);
            in.theme = s().theme;
            app.replaceStudio(in, true); app.toast("Backup restored", null);
        } catch (Exception ex) { JOptionPane.showMessageDialog(app, ex.getMessage(), "Couldn't restore", JOptionPane.WARNING_MESSAGE); }
    }
    private void exportCsv() {
        File f = chooseSave("practice-trades-" + Dates.today() + ".csv"); if (f == null) return;
        StringBuilder b = new StringBuilder("Date,Symbol,Side,Shares,Price,Amount\n");
        for (Practice.Trade x : s().practice.trades)
            b.append(x.date()).append(',').append(x.symbol()).append(',').append(x.buy() ? "Buy" : "Sell").append(',')
             .append(x.shares()).append(',').append(x.price()).append(',').append(Math.round(x.amount() * 100) / 100.0).append('\n');
        try { Files.writeString(f.toPath(), b.toString(), StandardCharsets.UTF_8); app.toast("Trades saved to " + f.getName(), null); }
        catch (Exception ex) { JOptionPane.showMessageDialog(app, "Couldn't save: " + ex.getMessage()); }
    }
}

package com.signaldesk.ui.views;

import com.signaldesk.calc.Dates;
import com.signaldesk.model.Studio;
import com.signaldesk.model.Transaction;
import com.signaldesk.store.*;
import com.signaldesk.ui.*;
import java.awt.Desktop;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import javax.swing.*;

public final class DataView extends View {
    public DataView(MainFrame app) { super(app); }

    @Override public JComponent build() {
        Theme t = Theme.T;
        Ui.VBox root = new Ui.VBox(20);
        root.add(Kit.head("Backup & data", "Your data", null, null));
        Ui.Card where = Kit.card("Where your data lives", null);
        where.add(Ui.para("Everything is saved automatically to a file on this computer, a moment after each change. Nothing is sent anywhere except price downloads."));
        where.add(Ui.text(app.store().file().toString(), Theme.mono(13f), t.ink));
        where.add(Ui.hint("A dated copy goes into the backups folder once a day, and the last 14 are kept. Price files live in the prices folder next to it."));
        where.add(Ui.btn("Open data folder", () -> {
            try { Files.createDirectories(AppPaths.root()); Desktop.getDesktop().open(AppPaths.root().toFile()); }
            catch (Exception ex) { JOptionPane.showMessageDialog(app, "Your data folder is:\n" + AppPaths.root()); }
        }), false);
        root.add(where);

        Ui.Card bk = Kit.card("Back up and restore", null);
        bk.add(Ui.para("A backup is one file holding your budget, bills, debts, accounts, holdings, retirement plan and backlog. Restore also accepts a backup exported from the web version of Signal Desk."));
        Ui.HBox r1 = new Ui.HBox(8);
        r1.add(Ui.primary("Export backup…", this::export)); r1.add(Ui.btn("Restore from backup…", this::restore)); r1.add(Ui.btn("Export expenses (.csv)…", this::exportCsv));
        bk.add(r1);

        Ui.Card so = Kit.card("Start over", null);
        so.add(Ui.para("Load the example household to explore, or erase your finances and begin blank. Both keep your backlog."));
        Ui.HBox r2 = new Ui.HBox(8);
        r2.add(Ui.btn("Load example data", () -> {
            if (!confirm("Load the example household? It replaces your current numbers. Export a backup first if you want to keep them.")) return;
            Studio ex = ExampleData.build(); ex.backlog.clear(); ex.backlog.addAll(s().backlog); ex.theme = s().theme;
            app.replaceStudio(ex, false); app.toast("Example data loaded", null);
        }));
        r2.add(Ui.danger("Erase all my data", () -> {
            if (!confirm("Erase your budget, bills, debts, accounts, holdings and retirement plan? Your backlog stays. This can't be undone, though yesterday's automatic backup is in the backups folder.")) return;
            Studio b = Studio.blank(); b.backlog.addAll(s().backlog); b.theme = s().theme;
            app.replaceStudio(b, true); app.toast("All finance data erased", null);
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
            if (!confirm("Replace everything in the studio with this backup?")) return;
            if (in.backlog.isEmpty()) in.backlog.addAll(s().backlog);
            in.example = false; in.theme = s().theme;
            app.replaceStudio(in, true); app.toast("Backup restored", null);
        } catch (Exception ex) { JOptionPane.showMessageDialog(app, ex.getMessage(), "Couldn't restore", JOptionPane.WARNING_MESSAGE); }
    }
    private void exportCsv() {
        File f = chooseSave("expenses-" + Dates.today() + ".csv"); if (f == null) return;
        StringBuilder b = new StringBuilder("Date,Description,Category,Amount\n");
        for (Transaction x : s().tx.stream().sorted(Comparator.comparing(x -> x.date)).toList())
            b.append(x.date).append(',').append(csv(x.desc)).append(',').append(csv(s().categoryName(x.cat))).append(',').append(x.amount).append('\n');
        try { Files.writeString(f.toPath(), b.toString(), StandardCharsets.UTF_8); app.toast("Expenses saved to " + f.getName(), null); }
        catch (Exception ex) { JOptionPane.showMessageDialog(app, "Couldn't save: " + ex.getMessage()); }
    }
    private static String csv(String s) { return s.matches(".*[\",\n].*") ? "\"" + s.replace("\"", "\"\"") + "\"" : s; }
}

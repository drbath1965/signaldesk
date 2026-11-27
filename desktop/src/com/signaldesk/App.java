package com.signaldesk;

import com.signaldesk.model.Studio;
import com.signaldesk.store.*;
import com.signaldesk.ui.MainFrame;
import com.signaldesk.ui.Theme;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.SwingUtilities;

/** Signal Desk: research, rank and learn about stocks, ETFs, mutual funds and crypto, with a practice portfolio. */
public final class App {
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        StudioStore store = new StudioStore(AppPaths.studioFile());
        PriceStore prices = new PriceStore(AppPaths.pricesDir());
        Studio studio; String note = null;
        if (store.exists()) {
            try {
                studio = store.load();
                if (studio.fromV1) {
                    Path keep = store.keepV1Copy();
                    note = "Signal Desk 2.0 is about investing and learning. Budget, bills and the other household sections are gone; your old data file was kept as backups/" + keep.getFileName() + ". Your investment symbols are now your watchlist.";
                }
            }
            catch (Exception ex) {
                Path bad = store.file().resolveSibling("studio-unreadable-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");
                try { Files.move(store.file(), bad); } catch (Exception ignored) {}
                studio = Studio.fresh();
                note = "Your data file couldn't be read, so it was set aside as " + bad.getFileName() + ". Restore a backup from Backup & data.";
            }
        } else studio = Studio.fresh();
        final Studio s = studio; final String n = note;
        SwingUtilities.invokeLater(() -> {
            Theme.install("dark".equals(s.theme));
            MainFrame f = new MainFrame(s, store, prices, n);
            f.setVisible(true);
        });
    }
}

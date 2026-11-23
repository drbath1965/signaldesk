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

/** Signal Desk finance studio: budget, bills, debt, net worth, retirement, investments, stock signals and backlog. */
public final class App {
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        StudioStore store = new StudioStore(AppPaths.studioFile());
        PriceStore prices = new PriceStore(AppPaths.pricesDir());
        Studio studio; String note = null;
        if (store.exists()) {
            try { studio = store.load(); }
            catch (Exception ex) {
                Path bad = store.file().resolveSibling("studio-unreadable-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");
                try { Files.move(store.file(), bad); } catch (Exception ignored) {}
                studio = ExampleData.build();
                note = "Your data file couldn't be read, so it was set aside as " + bad.getFileName() + ". Restore a backup from Backup & data.";
            }
        } else studio = ExampleData.build();
        final Studio s = studio; final String n = note;
        SwingUtilities.invokeLater(() -> {
            Theme.install("dark".equals(s.theme));
            MainFrame f = new MainFrame(s, store, prices, n);
            f.setVisible(true);
        });
    }
}

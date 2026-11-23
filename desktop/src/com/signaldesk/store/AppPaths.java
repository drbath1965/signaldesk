package com.signaldesk.store;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Where the studio keeps its files: a "SignalDesk" folder in the user's home directory. */
public final class AppPaths {
    private AppPaths() {}
    public static Path root() {
        String o = System.getProperty("signaldesk.home");
        return o != null ? Paths.get(o) : Paths.get(System.getProperty("user.home"), "SignalDesk");
    }
    public static Path studioFile() { return root().resolve("studio.json"); }
    public static Path pricesDir() { return root().resolve("prices"); }
    public static Path backupsDir() { return root().resolve("backups"); }
}

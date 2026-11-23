package com.signaldesk.ui;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.io.InputStream;
import java.util.Map;
import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;

/** Color tokens and fonts for light and dark mode, the same palette as the web studio. */
public final class Theme {
    public static Theme T = new Theme(false);

    public final boolean dark;
    public final Color paper, panel, ink, muted, line, soft, accent, slow, buy, sell, warn, buyBg, sellBg, holdBg, warnBg, onAccent;
    public final Color[] series;

    private Theme(boolean dark) {
        this.dark = dark;
        if (!dark) {
            paper = c(0xf3f5f8); panel = c(0xffffff); ink = c(0x14202e); muted = c(0x5b6878); line = c(0xdbe0e7); soft = c(0xeaeef3);
            accent = c(0x0f6e78); slow = c(0xb26f12); buy = c(0x13874a); sell = c(0xc3372c); warn = c(0xa86a00);
            buyBg = c(0xe3f4ea); sellBg = c(0xfbe6e3); holdBg = c(0xe2f0f1); warnBg = c(0xfbf0dc);
            series = new Color[]{c(0x0f6e78), c(0xb26f12), c(0x5457c9), c(0xb8407a), c(0x5f8a2b), c(0x7d8796)};
        } else {
            paper = c(0x0e131a); panel = c(0x151c25); ink = c(0xe5eaf0); muted = c(0x93a0b0); line = c(0x28323f); soft = c(0x1c2531);
            accent = c(0x4cb8c3); slow = c(0xe3a849); buy = c(0x3dcc7b); sell = c(0xff6e60); warn = c(0xe8b04a);
            buyBg = c(0x14301f); sellBg = c(0x3a1b18); holdBg = c(0x13303a); warnBg = c(0x352a12);
            series = new Color[]{c(0x4cb8c3), c(0xe3a849), c(0x8b8ef2), c(0xe57aa9), c(0x94c25a), c(0xa3adbb)};
        }
        onAccent = dark ? c(0x0e131a) : Color.WHITE;
    }
    private static Color c(int rgb) { return new Color(rgb); }
    public static Color alpha(Color c, double a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(a * 255)); }
    public static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() * t + b.getRed() * (1 - t)), (int) (a.getGreen() * t + b.getGreen() * (1 - t)), (int) (a.getBlue() * t + b.getBlue() * (1 - t)));
    }

    // ------------------------------------------------------------------ fonts
    private static Font regular, medium, semibold, bold, display, mono, monoBold;
    private static Font load(String file, String... fallbacks) {
        try (InputStream in = Theme.class.getResourceAsStream("/fonts/" + file)) {
            if (in != null) { Font f = Font.createFont(Font.TRUETYPE_FONT, in); GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f); return f; }
        } catch (Exception ignored) {}
        java.util.Set<String> have = new java.util.HashSet<>(java.util.Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String fb : fallbacks) if (have.contains(fb)) return new Font(fb, Font.PLAIN, 12);
        return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }
    public static synchronized void loadFonts() {
        if (regular != null) return;
        regular = load("Inter-Regular.otf", "Segoe UI", "Helvetica Neue", "Arial");
        medium = load("Inter-Medium.otf", "Segoe UI"); semibold = load("Inter-SemiBold.otf", "Segoe UI Semibold");
        bold = load("Inter-Bold.otf", "Segoe UI"); display = load("InterDisplay-ExtraBold.otf", "Segoe UI Black");
        mono = load("DejaVuSansMono.ttf", "Consolas", "Menlo", "Monospaced"); monoBold = load("DejaVuSansMono-Bold.ttf", "Consolas");
        if (bold.getFamily().equals(regular.getFamily()) && !bold.getFontName().toLowerCase().contains("bold")) bold = bold.deriveFont(Font.BOLD);
    }
    public static Font body(float size) { return regular.deriveFont(size); }
    public static Font medium(float size) { return medium.deriveFont(size); }
    public static Font semibold(float size) { return semibold.deriveFont(size); }
    public static Font bold(float size) { return bold.deriveFont(size); }
    public static Font display(float size) { return display.deriveFont(Map.of(TextAttribute.SIZE, size, TextAttribute.TRACKING, -0.01f)); }
    public static Font mono(float size) { return mono.deriveFont(size); }
    public static Font monoBold(float size) { return monoBold.deriveFont(size); }
    public static Font caps(float size) { return semibold.deriveFont(Map.of(TextAttribute.SIZE, size, TextAttribute.TRACKING, 0.08f)); }

    /** Installs the palette into Swing's defaults. Call before building any window. */
    public static void install(boolean dark) {
        loadFonts();
        T = new Theme(dark);
        try { MetalLookAndFeel.setCurrentTheme(new OceanTheme()); UIManager.setLookAndFeel(new MetalLookAndFeel()); } catch (Exception ignored) {}
        Theme t = T;
        FontUIResource f = new FontUIResource(body(13.5f));
        for (String k : new String[]{"Label.font", "Button.font", "TextField.font", "FormattedTextField.font", "TextArea.font", "ComboBox.font", "CheckBox.font",
                "RadioButton.font", "Table.font", "TableHeader.font", "List.font", "ToolTip.font", "OptionPane.messageFont", "OptionPane.buttonFont", "Spinner.font", "Menu.font", "MenuItem.font", "PopupMenu.font", "TitledBorder.font"})
            UIManager.put(k, f);
        ColorUIResource bg = new ColorUIResource(t.panel), fg = new ColorUIResource(t.ink), paper = new ColorUIResource(t.paper);
        for (String k : new String[]{"Panel.background", "OptionPane.background", "Viewport.background", "ScrollPane.background", "CheckBox.background", "RadioButton.background", "Table.background", "List.background", "PopupMenu.background", "Menu.background", "MenuItem.background", "ToolTip.background", "Spinner.background"})
            UIManager.put(k, bg);
        for (String k : new String[]{"Label.foreground", "Panel.foreground", "OptionPane.messageForeground", "CheckBox.foreground", "RadioButton.foreground", "Table.foreground", "List.foreground", "TextField.foreground", "TextArea.foreground", "ComboBox.foreground", "FormattedTextField.foreground", "Menu.foreground", "MenuItem.foreground", "ToolTip.foreground", "TitledBorder.titleColor", "Button.foreground"})
            UIManager.put(k, fg);
        for (String k : new String[]{"TextField.background", "TextArea.background", "FormattedTextField.background", "ComboBox.background", "Spinner.editorBackground"})
            UIManager.put(k, paper);
        UIManager.put("TextField.caretForeground", fg); UIManager.put("TextArea.caretForeground", fg); UIManager.put("FormattedTextField.caretForeground", fg);
        UIManager.put("TextField.selectionBackground", new ColorUIResource(alpha(t.accent, .3))); UIManager.put("TextField.selectionForeground", fg);
        UIManager.put("TextArea.selectionBackground", new ColorUIResource(alpha(t.accent, .3))); UIManager.put("TextArea.selectionForeground", fg);
        UIManager.put("TextField.inactiveBackground", paper); UIManager.put("TextField.disabledBackground", paper);
        UIManager.put("ComboBox.selectionBackground", new ColorUIResource(t.accent)); UIManager.put("ComboBox.selectionForeground", new ColorUIResource(t.onAccent));
        UIManager.put("ComboBox.buttonBackground", paper); UIManager.put("ComboBox.disabledBackground", paper);
        UIManager.put("ComboBox.buttonDarkShadow", new ColorUIResource(t.muted)); UIManager.put("ComboBox.buttonShadow", new ColorUIResource(t.line)); UIManager.put("ComboBox.buttonHighlight", paper);
        UIManager.put("List.selectionBackground", new ColorUIResource(t.accent)); UIManager.put("List.selectionForeground", new ColorUIResource(t.onAccent));
        UIManager.put("Table.selectionBackground", new ColorUIResource(Theme.mix(t.accent, t.panel, .14))); UIManager.put("Table.selectionForeground", fg);
        UIManager.put("Table.gridColor", new ColorUIResource(t.soft)); UIManager.put("Table.focusCellHighlightBorder", BorderFactory.createLineBorder(t.accent));
        UIManager.put("TableHeader.background", bg); UIManager.put("TableHeader.foreground", new ColorUIResource(t.muted));
        UIManager.put("ScrollBar.background", paper); UIManager.put("ScrollBar.track", paper); UIManager.put("ScrollBar.thumb", new ColorUIResource(t.line));
        UIManager.put("ScrollBar.thumbShadow", new ColorUIResource(t.line)); UIManager.put("ScrollBar.thumbHighlight", new ColorUIResource(t.line)); UIManager.put("ScrollBar.thumbDarkShadow", new ColorUIResource(t.line));
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("Button.background", bg); UIManager.put("Button.select", new ColorUIResource(t.soft)); UIManager.put("Button.focus", new ColorUIResource(t.accent));
        UIManager.put("Button.gradient", null); UIManager.put("ToggleButton.gradient", null); UIManager.put("CheckBox.gradient", null); UIManager.put("RadioButton.gradient", null);
        UIManager.put("CheckBox.focus", new ColorUIResource(t.panel)); UIManager.put("RadioButton.focus", new ColorUIResource(t.panel));
        UIManager.put("OptionPane.messageForeground", fg);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(t.line));
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(t.line));
        UIManager.put("ComboBox.border", BorderFactory.createLineBorder(t.line));
        UIManager.put("TextField.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(t.line), BorderFactory.createEmptyBorder(4, 7, 4, 7)));
        UIManager.put("FormattedTextField.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(t.line), BorderFactory.createEmptyBorder(4, 7, 4, 7)));
        UIManager.put("Spinner.border", BorderFactory.createLineBorder(t.line));
        UIManager.put("Spinner.arrowButtonBorder", BorderFactory.createEmptyBorder());
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("Control", bg); UIManager.put("control", bg); UIManager.put("OptionPane.border", BorderFactory.createEmptyBorder(14, 14, 10, 14));
        UIManager.put("Separator.foreground", new ColorUIResource(t.line));
        UIManager.put("ToolTip.font", new FontUIResource(body(12.5f)));
    }
}

package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

import com.formdev.flatlaf.intellijthemes.FlatGrayIJTheme;

import javax.swing.*;

/**
 * All FlatLaf setup lives here — theme selection, window decorations, and
 * the UIManager tweaks that trim FlatLaf's own focus/press indicators so
 * they don't double up with our custom hoverBorderColor button styling
 * (see AccentColors). Call apply() once, before any Swing component is
 * constructed.
 */
public final class AppTheme {

    private AppTheme() {}

    public static void apply() {
        FlatGrayIJTheme.setup();
        UIManager.put("Component.focusWidth", 0);
        UIManager.put("Button.focusedBorderColor", UIManager.getColor("Button.borderColor"));
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
    }
}
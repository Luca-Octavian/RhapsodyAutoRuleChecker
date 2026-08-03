package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

import com.formdev.flatlaf.intellijthemes.FlatGrayIJTheme;

import javax.swing.*;
import java.awt.*;

/**
 * All FlatLaf setup lives here — theme selection, window decorations, and
 * the UIManager tweaks that trim FlatLaf's own focus/press indicators so
 * they don't double up with our custom hoverBorderColor button styling
 * (see AccentColors). Call apply() once, before any Swing component is
 * constructed.
 */
public final class AppTheme {

    private AppTheme() {}

    /**
     * Applies FlatLaf-consistent styling to an editable JComboBox so that it
     * renders with the same border, background and button as a non-editable combo
     * instead of falling back to the system native L&F editor.
     */
    public static void styleComboBox(JComboBox<?> combo) {
        // Force the editor component to match the FlatLaf theme colours
        Component editor = combo.getEditor().getEditorComponent();
        if (editor instanceof JTextField) {
            ((JTextField) editor).setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
        }
        combo.putClientProperty("JComboBox.isTableCellEditor", Boolean.FALSE);
    }

    public static void apply() {
        FlatGrayIJTheme.setup();
        UIManager.put("Component.focusWidth", 0);
        UIManager.put("Button.focusedBorderColor", UIManager.getColor("Button.borderColor"));

        // Enable tree connector/branching lines
        UIManager.put("Tree.paintLines", Boolean.TRUE);
        UIManager.put("Tree.showsRootHandles", Boolean.TRUE);

        // Editable JComboBoxes (e.g. groupCombo) otherwise render their arrow
        // button with a separated "gray box" background + divider so users
        // can tell the text area is typable. We want editable combos to look
        // seamless like our non-editable ones, so blend the arrow button in.
        UIManager.put("ComboBox.buttonEditableBackground", UIManager.getColor("ComboBox.background"));
        UIManager.put("ComboBox.buttonSeparatorWidth", 0);

        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
    }
}
package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

import com.formdev.flatlaf.intellijthemes.FlatGrayIJTheme;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

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

        // Bump the global default font to 14pt for a more spacious / "zoomed in" feel.
        Font base = UIManager.getFont("defaultFont");
        if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        UIManager.put("defaultFont", new FontUIResource(base.deriveFont(14f)));

        UIManager.put("Component.focusWidth", 0);
        UIManager.put("Button.focusedBorderColor", UIManager.getColor("Button.borderColor"));

        // FlatLaf/JOptionPane render whichever button is the root pane's
        // default button (e.g. the OK/Yes button in showMessageDialog,
        // showConfirmDialog) with a filled accent that defaults to blue.
        // Purple is the app-wide confirmation colour; terracotta stays
        // reserved for explicit model lifecycle actions.
        Color purpleBase = Color.decode(AccentColors.PURPLE_HEX);
        UIManager.put("Button.default.background", purpleBase);
        UIManager.put("Button.default.startBackground", purpleBase);
        UIManager.put("Button.default.endBackground", purpleBase);
        UIManager.put("Button.default.foreground", Color.WHITE);
        UIManager.put("Button.default.borderColor", purpleBase);
        UIManager.put("Button.default.focusedBorderColor", Color.decode(AccentColors.PURPLE_FAINT_HEX));
        UIManager.put("Button.default.hoverBackground", Color.decode(AccentColors.darken(AccentColors.PURPLE_HEX, 0.12f)));
        UIManager.put("Button.default.pressedBackground", Color.decode(AccentColors.darken(AccentColors.PURPLE_HEX, 0.22f)));
        UIManager.put("Button.default.boldText", Boolean.FALSE);

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

    /**
     * Floors a window's size at {@code min} whenever it's resized, and
     * guards against a squashed aspect ratio (very wide + very short, or
     * vice versa) rather than just each dimension independently. Attach to
     * every top-level window, not just the main frame — Rhapsody's COM
     * automation has been observed to poke window bounds on dialogs too.
     */
    public static void guardMinimumSize(Window window, Dimension min) {
        window.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Dimension current = window.getSize();
                int w = Math.max(current.width, min.width);
                int h = Math.max(current.height, min.height);

                // If one dimension collapsed much faster than the other,
                // the window has been squashed into a sliver rather than
                // shrunk proportionally — pull the smaller side back up
                // toward the aspect ratio of the minimum size.
                double minRatio = min.width / (double) min.height;
                double currentRatio = w / (double) h;
                if (currentRatio > minRatio * 1.8) {
                    h = Math.max(h, (int) (w / minRatio / 1.8));
                } else if (currentRatio < minRatio / 1.8) {
                    w = Math.max(w, (int) (h * minRatio / 1.8));
                }

                if (w != current.width || h != current.height) {
                    window.setSize(w, h);
                }
            }
        });
    }
}
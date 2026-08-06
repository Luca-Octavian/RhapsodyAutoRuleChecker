package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

import com.formdev.flatlaf.intellijthemes.FlatGrayIJTheme;

import javax.swing.*;
import javax.swing.border.Border;
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

        applyTreeStyle();
        applyTableStyle();
        applyScrollStyle();
        applyShapeStyle();

        // Editable JComboBoxes (e.g. groupCombo) otherwise render their arrow
        // button with a separated "gray box" background + divider so users
        // can tell the text area is typable. We want editable combos to look
        // seamless like our non-editable ones, so blend the arrow button in.
        UIManager.put("ComboBox.buttonEditableBackground", UIManager.getColor("ComboBox.background"));
        UIManager.put("ComboBox.buttonSeparatorWidth", 0);

        // The title bar keeps its own background. Merging it into the window
        // body was tried and it removed the last boundary at the top of the
        // frame: with panel borders and header dividers also gone at the time,
        // the whole window read as one flat sheet with controls floating on
        // it. The strip at the top is worth keeping as a boundary.
        UIManager.put("TitlePane.unifiedBackground", Boolean.FALSE);

        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
    }

    /**
     * Tree density and selection shape.
     *
     * <p>Swing's own connector lines stay <em>off</em>: they're dotted elbows
     * drawn per parent/child pair, which is the dated part. Hierarchy is
     * instead carried by the continuous vertical indent guides painted by
     * {@link IndentGuideTree} — same information, one line per depth level, in
     * the idiom every current editor uses.
     */
    private static void applyTreeStyle() {
        UIManager.put("Tree.paintLines", Boolean.FALSE);
        UIManager.put("Tree.showsRootHandles", Boolean.TRUE);

        // Taller rows. The default (~16px) is dense to the point of being hard
        // to click accurately, and reads as cramped next to 34px buttons.
        UIManager.put("Tree.rowHeight", 26);
        UIManager.put("Tree.leftChildIndent", 10);
        UIManager.put("Tree.rightChildIndent", 12);

        // Rounded selection "pill" inset from the row edges, instead of a
        // hard-edged full-bleed rectangle. Matches the button corner radius.
        UIManager.put("Tree.selectionArc", 8);
        UIManager.put("Tree.selectionInsets", new Insets(0, 4, 0, 4));
        UIManager.put("Tree.wideSelection", Boolean.TRUE);

        // The dashed focus rectangle around the focused row is redundant once
        // the selection itself is clearly drawn.
        UIManager.put("Tree.paintSelectionFocus", Boolean.FALSE);
    }

    /** Table density; horizontal rules removed in favour of row spacing. */
    private static void applyTableStyle() {
        UIManager.put("Table.rowHeight", 26);
        UIManager.put("Table.showHorizontalLines", Boolean.FALSE);
        UIManager.put("Table.showVerticalLines", Boolean.FALSE);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 0));
        UIManager.put("Table.selectionArc", 8);
        UIManager.put("TableHeader.separatorColor",
                UIManager.getColor("Component.borderColor"));
        UIManager.put("TableHeader.bottomSeparatorColor",
                UIManager.getColor("Component.borderColor"));
    }

    /**
     * Slim, buttonless, rounded scrollbars. The stepper arrow buttons at each
     * end are a legacy affordance — every modern scrollbar is a bare thumb.
     */
    private static void applyScrollStyle() {
        UIManager.put("ScrollBar.showButtons", Boolean.FALSE);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.thumbArc", 8);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.trackArc", 8);
        UIManager.put("ScrollPane.smoothScrolling", Boolean.TRUE);

        // A split divider reads more cleanly as a plain seam than as a seam
        // with a cluster of grip dots in the middle.
        UIManager.put("SplitPaneDivider.gripDotCount", 0);
    }

    /**
     * Corner radii. These exist so that text fields, combo boxes and scroll
     * panes share the same radius the buttons already use (see
     * {@link GradientAccentButton}) — mismatched radii is what makes a UI feel
     * assembled from unrelated parts.
     */
    private static void applyShapeStyle() {
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("ProgressBar.arc", 8);
        UIManager.put("CheckBox.arc", 4);
        UIManager.put("Popup.dropShadowPainted", Boolean.TRUE);
    }

    /**
     * A grey suitable for secondary text, as a {@code #rrggbb} string for use
     * inside the HTML that cell renderers build.
     *
     * <p>Renderers need a hex literal rather than a {@link Color} because Swing
     * styles HTML labels through CSS text. Deriving it from the current theme
     * here — instead of each renderer hardcoding one — is what keeps those
     * labels legible if the look and feel is ever switched to a dark theme.
     */
    public static String mutedTextHex() {
        Color c = UIManager.getColor("Label.disabledForeground");
        if (c == null) c = UIManager.getColor("Component.disabledBorderColor");
        if (c == null) c = Color.GRAY;
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /**
     * A lighter grey than {@link #mutedTextHex()}, for separators drawn inside
     * renderer HTML (the em-dashes between fields on a result row).
     */
    public static String faintTextHex() {
        Color c = UIManager.getColor("Component.borderColor");
        if (c == null) c = Color.LIGHT_GRAY;
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /**
     * The 1px line used to delimit panels and panes. Read from the theme so a
     * single call site controls every boundary in the app.
     */
    public static Color borderColor() {
        Color c = UIManager.getColor("Component.borderColor");
        if (c == null) c = UIManager.getColor("Separator.foreground");
        return c != null ? c : Color.LIGHT_GRAY;
    }

    /** A 1px line border in {@link #borderColor()}. */
    public static Border panelBorder() {
        return BorderFactory.createLineBorder(borderColor(), 1);
    }

    /** A 1px rule on one edge only; pass 1 for the edges that should show it. */
    public static Border edgeBorder(int top, int left, int bottom, int right) {
        return BorderFactory.createMatteBorder(top, left, bottom, right, borderColor());
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
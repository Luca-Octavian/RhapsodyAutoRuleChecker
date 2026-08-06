package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

import javax.swing.UIManager;
import java.awt.Color;

public final class AccentColors {

    private AccentColors() {}

    // Brand Orange 200 is used sparingly for model/cache actions and progress.
    // Purple remains the wizard/config accent; neither colour is used for every
    // action so the mostly-neutral interface stays calm.
    public static final String ORANGE_HEX = "#ff4208";
    public static final String PURPLE_HEX = "#6a5fc4";
    // Excel-flavoured green, used only for the export action.
    public static final String GREEN_HEX = "#217346";
    // Destructive actions (Remove). Muted rather than alarm-red — these are
    // routine list edits, not irreversible data loss.
    public static final String RED_HEX = "#c0392b";
    // The neutral grey that starts every gradient ring and colours the
    // NEUTRAL button variant. Used when an action carries no semantic colour.
    public static final String NEUTRAL_HEX = "#a8acb2";

    public static final String ORANGE_FAINT_HEX = "#ffb39c";
    public static final String PURPLE_FAINT_HEX = "#c3bbe8";
    public static final String GREEN_FAINT_HEX = "#9ccbae";
    public static final String RED_FAINT_HEX = "#e6a49d";

    public static final String DEFAULT_FAINT_HEX = toHex(UIManager.getColor("Component.focusColor"));

    public static final String ORANGE_HOVER_STYLE =
            "hoverBorderColor: " + ORANGE_FAINT_HEX + "; " +
            "focusedBorderColor: " + ORANGE_FAINT_HEX + ";";

    public static final String PURPLE_HOVER_STYLE =
            "hoverBorderColor: " + PURPLE_FAINT_HEX + "; " +
            "focusedBorderColor: " + PURPLE_FAINT_HEX + ";";

    public static final String DEFAULT_HOVER_STYLE =
            "hoverBorderColor: " + DEFAULT_FAINT_HEX + "; " +
            "focusedBorderColor: " + DEFAULT_FAINT_HEX + ";";

    /**
     * Solid filled button styles — background + foreground + hover/pressed
     * shades derived from the base accent. Use for the small set of buttons
     * that should read as "primary" for their context: model/cache actions get
     * {@link #ORANGE_FILL_STYLE}, while wizard/config commit actions get
     * {@link #PURPLE_FILL_STYLE}. The Run action uses {@link #ORANGE_HOVER_STYLE}
     * rather than a filled style. Applied via the FlatLaf.style client
     * property, same as the hover styles above.
     */
    public static final String ORANGE_FILL_STYLE = fillStyle(ORANGE_HEX, ORANGE_FAINT_HEX, "#ffffff");
    public static final String PURPLE_FILL_STYLE = fillStyle(PURPLE_HEX, PURPLE_FAINT_HEX, "#ffffff");

    /**
     * Outline button styles — neutral background at rest with a light accent
     * border, full bright accent fill on hover, darkened on press.  Use for
     * primary-action buttons that should <em>not</em> be permanently filled.
     */
    public static final String ORANGE_OUTLINE_STYLE = outlineStyle(ORANGE_HEX, ORANGE_FAINT_HEX, "#ffffff");
    public static final String PURPLE_OUTLINE_STYLE = outlineStyle(PURPLE_HEX, PURPLE_FAINT_HEX, "#ffffff");

    private static String outlineStyle(String baseHex, String faintHex, String hoverForegroundHex) {
        return "borderWidth: 1.5; " +
                "borderColor: " + faintHex + "; " +
                "hoverBorderColor: " + baseHex + "; " +
                "focusedBorderColor: " + faintHex + "; " +
                "background: $Button.background; " +
                "foreground: $Button.foreground; " +
                "hoverBackground: " + baseHex + "; " +
                "hoverForeground: " + hoverForegroundHex + "; " +
                "pressedBackground: " + darken(baseHex, 0.15f) + ";";
    }

    private static String fillStyle(String baseHex, String faintHex, String foregroundHex) {
        return "background: " + baseHex + "; " +
                "foreground: " + foregroundHex + "; " +
                "borderColor: " + baseHex + "; " +
                "focusedBorderColor: " + faintHex + "; " +
                "hoverBackground: " + darken(baseHex, 0.12f) + "; " +
                "pressedBackground: " + darken(baseHex, 0.22f) + ";";
    }

    /** Darkens a hex color by the given fraction (0-1) in HSB space, for hover/pressed shades. */
    static String darken(String hex, float amount) {
        Color c = Color.decode(hex);
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hsb[2] = Math.max(0f, hsb[2] * (1f - amount));
        Color d = Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
        return toHex(d);
    }

    private static String toHex(Color c) {
        return c == null ? "#2675bf" : String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
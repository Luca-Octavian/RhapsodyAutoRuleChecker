package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

import javax.swing.UIManager;
import java.awt.Color;

public final class AccentColors {

    private AccentColors() {}

    // Brand Orange 200 is used sparingly for model/cache actions and progress.
    // Purple remains the wizard/config accent; neither colour is used for every
    // action so the mostly-neutral interface stays calm.
    public static final String ORANGE_HEX = "#ff906e";
    public static final String PURPLE_HEX = "#6a5fc4";

    public static final String ORANGE_FAINT_HEX = "#ffd2c5";
    public static final String PURPLE_FAINT_HEX = "#c3bbe8";

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
    public static final String ORANGE_FILL_STYLE = fillStyle(ORANGE_HEX, ORANGE_FAINT_HEX, "#4a2418");
    public static final String PURPLE_FILL_STYLE = fillStyle(PURPLE_HEX, PURPLE_FAINT_HEX, "#ffffff");

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
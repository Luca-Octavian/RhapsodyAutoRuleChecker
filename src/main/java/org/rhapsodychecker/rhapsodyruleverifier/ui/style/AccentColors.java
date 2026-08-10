package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

import java.awt.Color;

/**
 * Semantic accent access for custom-painted components.
 *
 * <p>The active palette is selected by {@link AppTheme}. Callers should request
 * semantic roles at paint or construction time instead of carrying appearance
 * decisions into UI components.</p>
 */
public final class AccentColors {

    private static volatile UiPalette activePalette = UiPalettes.light();

    private AccentColors() {}

    /*
     * Source-compatible light-palette aliases for established button call sites.
     * New code should use semantic accessors below so it responds to appearance
     * changes without branching on light versus dark mode.
     */
    public static final String ORANGE_HEX = "#FF6B3D";
    public static final String PURPLE_HEX = "#6A5FC4";
    public static final String GREEN_HEX = "#217346";
    public static final String RED_HEX = "#D86A68";
    public static final String NEUTRAL_HEX = "#A8ACB2";

    public static final String ORANGE_FAINT_HEX = "#FFE2D8";
    public static final String PURPLE_FAINT_HEX = "#E6E0FA";
    public static final String GREEN_FAINT_HEX = "#9CCBAE";
    public static final String RED_FAINT_HEX = "#FBE2E1";

    /** Live FlatLaf styles for the active palette; resolve them when applying a component style. */
    public static String orangeHoverStyle() {
        return hoverStyle(toHex(action()), toHex(palette().actionFaint()));
    }

    public static String primaryHoverStyle() {
        return hoverStyle(toHex(primary()), toHex(palette().primaryFaint()));
    }

    public static String defaultHoverStyle() {
        return hoverStyle(NEUTRAL_HEX, NEUTRAL_HEX);
    }

    public static String orangeFillStyle() {
        return fillStyle(toHex(action()), toHex(palette().actionFaint()), "#FFFFFF");
    }

    public static String primaryFillStyle() {
        return fillStyle(toHex(primary()), toHex(palette().primaryFaint()), "#FFFFFF");
    }

    public static String orangeOutlineStyle() {
        return outlineStyle(toHex(action()), toHex(palette().actionFaint()), "#FFFFFF");
    }

    public static String primaryOutlineStyle() {
        return outlineStyle(toHex(primary()), toHex(palette().primaryFaint()), "#FFFFFF");
    }

    static void setPalette(UiPalette palette) {
        if (palette == null) {
            throw new IllegalArgumentException("palette must not be null");
        }
        activePalette = palette;
    }

    public static UiPalette palette() {
        return activePalette;
    }

    public static Color primary() {
        return palette().primary();
    }

    public static Color action() {
        return palette().action();
    }

    public static Color failure() {
        return palette().failure();
    }

    public static Color mutedText() {
        return palette().mutedText();
    }

    private static String hoverStyle(String baseHex, String faintHex) {
        return "hoverBorderColor: " + faintHex + "; "
                + "focusedBorderColor: " + faintHex + ";";
    }

    private static String outlineStyle(String baseHex, String faintHex, String hoverForegroundHex) {
        return "borderWidth: 1.5; "
                + "borderColor: " + faintHex + "; "
                + "hoverBorderColor: " + baseHex + "; "
                + "focusedBorderColor: " + faintHex + "; "
                + "background: $Button.background; "
                + "foreground: $Button.foreground; "
                + "hoverBackground: " + baseHex + "; "
                + "hoverForeground: " + hoverForegroundHex + "; "
                + "pressedBackground: " + darken(baseHex, 0.15f) + ";";
    }

    private static String fillStyle(String baseHex, String faintHex, String foregroundHex) {
        return "background: " + baseHex + "; "
                + "foreground: " + foregroundHex + "; "
                + "borderColor: " + baseHex + "; "
                + "focusedBorderColor: " + faintHex + "; "
                + "hoverBackground: " + darken(baseHex, 0.12f) + "; "
                + "pressedBackground: " + darken(baseHex, 0.22f) + ";";
    }

    /** Darkens a hexadecimal color by the given fraction (0-1) in HSB space. */
    static String darken(String hex, float amount) {
        Color c = Color.decode(hex);
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hsb[2] = Math.max(0f, hsb[2] * (1f - amount));
        return toHex(Color.getHSBColor(hsb[0], hsb[1], hsb[2]));
    }

    private static String toHex(Color color) {
        return String.format("#%02X%02X%02X",
                color.getRed(), color.getGreen(), color.getBlue());
    }
}
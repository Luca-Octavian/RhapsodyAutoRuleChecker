package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

import javax.swing.UIManager;
import java.awt.Color;

public final class AccentColors {

    private AccentColors() {}

    public static final String ORANGE_HEX = "#ff4208";
    public static final String PURPLE_HEX = "#4827af";

    public static final String ORANGE_FAINT_HEX = "#ffa98c";
    public static final String PURPLE_FAINT_HEX = "#a99bd6";

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

    private static String toHex(Color c) {
        return c == null ? "#2675bf" : String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
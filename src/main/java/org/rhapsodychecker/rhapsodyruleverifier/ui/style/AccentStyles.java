// ui/style/AccentStyles.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

/**
 * Shared FlatLaf "FlatLaf.style" client-property strings for the two accent
 * colors used across the UI, so buttons don't each hardcode their own
 * hex values and drift out of sync.
 */
public final class AccentStyles {

    private AccentStyles() {}

    /**
     * Primary accent — the load/run pipeline (Load Model, Run).
     * Reuses Arc Orange's own theme accent (#f57900) rather than a separate
     * orange, so accented buttons read as "the app's color" instead of a
     * second hue competing with the theme.
     */
    public static final String PRIMARY_ORANGE =
            "background: #f57900;"
          + "foreground: #ffffff;"
          + "hoverBackground: #ff8c1a;"
          + "pressedBackground: #cc6100;"
          + "focusedBackground: #f57900;"
          + "borderColor: #f57900;"
          + "focusedBorderColor: #ff8c1a;";

    /**
     * Secondary accent — the config-wizard workflow (New/Edit Config).
     * Sets this workflow visually apart from the orange pipeline actions.
     */
    public static final String SECONDARY_PURPLE =
            "background: #4827af;"
          + "foreground: #ffffff;"
          + "hoverBackground: #5b3ac9;"
          + "pressedBackground: #3a1f8a;"
          + "focusedBackground: #4827af;"
          + "borderColor: #4827af;"
          + "focusedBorderColor: #5b3ac9;";
}
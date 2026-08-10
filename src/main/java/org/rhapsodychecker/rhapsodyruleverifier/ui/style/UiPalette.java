package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

import java.awt.Color;
import java.util.Objects;

/**
 * Immutable semantic color palette for the application.
 *
 * <p>Components ask for roles (primary action, failure, surface) rather than
 * knowing whether the active appearance is light or dark. This keeps visual
 * policy separate from component behavior and allows a future appearance
 * preference to switch palettes without rewriting renderers.</p>
 */
public final class UiPalette {

    private final Color applicationBackground;
    private final Color surface;
    private final Color interactiveSurface;
    private final Color border;
    private final Color text;
    private final Color mutedText;
    private final Color primary;
    private final Color primaryFaint;
    private final Color action;
    private final Color actionFaint;
    private final Color failure;
    private final Color failureFaint;
    private final Color warning;
    private final Color information;
    private final Color selection;
    private final Color selectionForeground;
    private final Color alternateRow;

    public UiPalette(Color applicationBackground, Color surface, Color interactiveSurface,
                     Color border, Color text, Color mutedText, Color primary,
                     Color primaryFaint, Color action, Color actionFaint,
                     Color failure, Color failureFaint, Color warning,
                     Color information, Color selection, Color selectionForeground,
                     Color alternateRow) {
        this.applicationBackground = requireColor(applicationBackground, "applicationBackground");
        this.surface = requireColor(surface, "surface");
        this.interactiveSurface = requireColor(interactiveSurface, "interactiveSurface");
        this.border = requireColor(border, "border");
        this.text = requireColor(text, "text");
        this.mutedText = requireColor(mutedText, "mutedText");
        this.primary = requireColor(primary, "primary");
        this.primaryFaint = requireColor(primaryFaint, "primaryFaint");
        this.action = requireColor(action, "action");
        this.actionFaint = requireColor(actionFaint, "actionFaint");
        this.failure = requireColor(failure, "failure");
        this.failureFaint = requireColor(failureFaint, "failureFaint");
        this.warning = requireColor(warning, "warning");
        this.information = requireColor(information, "information");
        this.selection = requireColor(selection, "selection");
        this.selectionForeground = requireColor(selectionForeground, "selectionForeground");
        this.alternateRow = requireColor(alternateRow, "alternateRow");
    }

    public Color applicationBackground() { return applicationBackground; }
    public Color surface() { return surface; }
    public Color interactiveSurface() { return interactiveSurface; }
    public Color border() { return border; }
    public Color text() { return text; }
    public Color mutedText() { return mutedText; }
    public Color primary() { return primary; }
    public Color primaryFaint() { return primaryFaint; }
    public Color action() { return action; }
    public Color actionFaint() { return actionFaint; }
    public Color failure() { return failure; }
    public Color failureFaint() { return failureFaint; }
    public Color warning() { return warning; }
    public Color information() { return information; }
    public Color selection() { return selection; }
    public Color selectionForeground() { return selectionForeground; }
    public Color alternateRow() { return alternateRow; }

    private static Color requireColor(Color color, String role) {
        return Objects.requireNonNull(color, role + " must not be null");
    }
}
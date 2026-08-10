package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

/**
 * Implemented by persistent custom Swing components that cache palette-derived
 * colors or borders at construction time.
 *
 * <p>{@link AppTheme} invokes this after applying a new palette so components
 * that outlive a light/dark toggle can rebuild their custom presentation
 * without rebuilding their model data.</p>
 */
public interface ThemeAware {

    /** Rebuilds colors, borders, and other palette-derived presentation state. */
    void refreshTheme();
}
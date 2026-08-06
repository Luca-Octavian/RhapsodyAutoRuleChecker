// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/ui/style/GradientAccentButton.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * The single button painter used across the whole application.
 *
 * <p>Every button in the app is a {@code GradientAccentButton} so that height,
 * corner radius, stroke width, hover timing and disabled treatment are
 * identical everywhere. Only the <em>voice</em> differs, expressed as a
 * {@link Variant}:
 *
 * <ul>
 *   <li>{@link Variant#PRIMARY} — the one main action of a screen. Neutral
 *       grey → accent gradient ring at rest over a white fill; solid accent
 *       fill with white text on hover.</li>
 *   <li>{@link Variant#SECONDARY} — supporting actions next to a primary.
 *       Same silhouette, but a flat neutral ring at rest that warms into the
 *       accent gradient on hover, and only a light accent <em>tint</em> fill
 *       rather than full saturation.</li>
 *   <li>{@link Variant#NEUTRAL} — utility actions with no semantic colour.
 *       Grey ring, grey hover fill, no accent at all.</li>
 * </ul>
 *
 * <p><b>Why this class exists instead of FlatLaf's {@code FlatLaf.style}
 * client property.</b> That mechanism (borderColor / hoverBorderColor /
 * focusedBorderColor) only ever accepts a single flat colour per state, so a
 * genuine spatial gradient stroke isn't reachable through it. Worse, FlatLaf's
 * button UI paints its own border on top of (or instead of) a component's
 * installed {@link javax.swing.border.Border}, and once a button becomes the
 * focus owner — which happens on click — it keeps using {@code
 * focusedBorderColor}, a flat colour, until focus moves elsewhere. That is why
 * a hand-rolled gradient would visibly collapse to a solid line after a press.
 *
 * <p>This class sidesteps all of that: {@link #setBorderPainted} and {@link
 * #setFocusPainted} are switched off and {@link #paintComponent(Graphics)} /
 * {@link #paintBorder(Graphics)} are overridden directly, so nothing but this
 * class's own painting ever touches the button's edge. The rest state is drawn
 * identically regardless of focus, hover history, or how recently the button
 * was pressed.
 *
 * <p>Foreground colour is derived in {@link #getForeground()} rather than
 * pushed via {@code setForeground} during painting — mutating a bound property
 * from inside {@code paintComponent} triggers property-change events and
 * repaint churn.
 */
public class GradientAccentButton extends JButton {

    /** Visual weight of a button relative to its neighbours. */
    public enum Variant { PRIMARY, SECONDARY, NEUTRAL }

    // ── Shared geometry: every button in the app agrees on these ────────────

    private static final float BORDER_WIDTH = 1.6f;
    private static final int   ARC          = 10;

    /** Standard padding. Yields roughly a 34px tall button at 100% scaling. */
    private static final Insets PAD_STANDARD = new Insets(8, 16, 8, 16);
    /** Compact padding, for buttons that sit inline beside text fields/combos. */
    private static final Insets PAD_COMPACT  = new Insets(4, 12, 4, 12);

    /** Keeps short labels ("Save", "Remove") from collapsing into stubby pills. */
    private static final int MIN_WIDTH_STANDARD = 96;
    private static final int MIN_WIDTH_COMPACT  = 0;

    /** How far the accent is lightened to produce the SECONDARY hover tint. */
    private static final float SECONDARY_TINT = 0.82f;

    // ── Per-instance state ──────────────────────────────────────────────────

    private final Variant variant;
    private final boolean compact;
    private final Color   accent;
    private final Color   accentPressed;
    private final Color   accentTint;
    private final Color   accentTintPressed;
    private final Color   neutral;
    private final Color   neutralHover;
    private final Color   neutralHoverPressed;

    // ── Factories (preferred over the constructors) ─────────────────────────

    /** Main action of a screen: gradient ring at rest, solid accent on hover. */
    public static GradientAccentButton primary(String text, String accentHex) {
        return new GradientAccentButton(text, accentHex, Variant.PRIMARY, false);
    }

    /** Supporting action: neutral ring at rest, gradient ring + tint on hover. */
    public static GradientAccentButton secondary(String text, String accentHex) {
        return new GradientAccentButton(text, accentHex, Variant.SECONDARY, false);
    }

    /** Utility action with no semantic colour. */
    public static GradientAccentButton neutral(String text) {
        return new GradientAccentButton(text, AccentColors.NEUTRAL_HEX, Variant.NEUTRAL, false);
    }

    /** Compact neutral, for inline use beside a text field or combo box. */
    public static GradientAccentButton neutralCompact(String text) {
        return new GradientAccentButton(text, AccentColors.NEUTRAL_HEX, Variant.NEUTRAL, true);
    }

    /** Compact secondary, for inline use beside a text field or combo box. */
    public static GradientAccentButton secondaryCompact(String text, String accentHex) {
        return new GradientAccentButton(text, accentHex, Variant.SECONDARY, true);
    }

    // ── Construction ────────────────────────────────────────────────────────

    /** Convenience overload; equivalent to {@link #primary(String, String)}. */
    public GradientAccentButton(String text, String accentHex) {
        this(text, accentHex, Variant.PRIMARY, false);
    }

    public GradientAccentButton(String text, String accentHex, Variant variant, boolean compact) {
        super(text);
        this.variant = variant;
        this.compact = compact;

        this.accent              = Color.decode(accentHex);
        this.accentPressed       = Color.decode(AccentColors.darken(accentHex, 0.18f));
        this.accentTint          = mix(accent, Color.WHITE, SECONDARY_TINT);
        this.accentTintPressed   = mix(accent, Color.WHITE, SECONDARY_TINT - 0.14f);
        this.neutral             = defaultNeutral();
        this.neutralHover        = mix(neutral, Color.WHITE, 0.72f);
        this.neutralHoverPressed = mix(neutral, Color.WHITE, 0.55f);

        setContentAreaFilled(false);
        setBorderPainted(false); // we paint the border ourselves, see paintBorder()
        setFocusPainted(false);  // stop Swing/FlatLaf drawing a focus ring over ours
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Insets pad = compact ? PAD_COMPACT : PAD_STANDARD;
        setBorder(BorderFactory.createEmptyBorder(pad.top, pad.left, pad.bottom, pad.right));

        // Rollover/pressed/enabled changes all fire through the ButtonModel;
        // repaint so the fill/border swap shows up immediately.
        getModel().addChangeListener(e -> repaint());
    }

    // ── Sizing ──────────────────────────────────────────────────────────────

    /**
     * Widens the preferred size to {@link #MIN_WIDTH_STANDARD} so that short
     * labels still read as full buttons beside long ones. Height is left to
     * the label + padding, which is uniform across all instances.
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        int min = compact ? MIN_WIDTH_COMPACT : MIN_WIDTH_STANDARD;
        if (d.width < min) d.width = min;
        return d;
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    // ── Colour resolution ───────────────────────────────────────────────────

    private static Color defaultNeutral() {
        Color c = UIManager.getColor("Component.borderColor");
        return c != null ? c : Color.decode(AccentColors.NEUTRAL_HEX);
    }

    /** Blends {@code base} toward {@code toward}; {@code amount} 0 = base, 1 = toward. */
    private static Color mix(Color base, Color toward, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        return new Color(
                Math.round(base.getRed()   + (toward.getRed()   - base.getRed())   * a),
                Math.round(base.getGreen() + (toward.getGreen() - base.getGreen()) * a),
                Math.round(base.getBlue()  + (toward.getBlue()  - base.getBlue())  * a));
    }

    private static Color uiColor(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    private boolean isHovered() {
        return isEnabled() && getModel().isRollover();
    }

    private boolean isPressed() {
        return isEnabled() && getModel().isPressed();
    }

    private boolean isActive() {
        return isHovered() || isPressed();
    }

    /**
     * Text colour follows the current state. Derived rather than stored so that
     * painting never mutates a bound property.
     */
    @Override
    public Color getForeground() {
        if (!isEnabled()) {
            return uiColor("Button.disabledText", Color.GRAY);
        }
        Color rest = uiColor("Button.foreground", Color.DARK_GRAY);
        if (!isActive()) return rest;

        // PRIMARY hover is a saturated accent fill, so the label must invert.
        // SECONDARY/NEUTRAL hover fills stay pale, so dark text remains readable.
        return variant == Variant.PRIMARY ? Color.WHITE : rest;
    }

    /** The fill drawn behind the label, or {@code null} to leave it white. */
    private Color fillColor() {
        if (!isEnabled()) {
            return uiColor("Button.disabledBackground", new Color(0xE8, 0xE8, 0xE8));
        }
        if (!isActive()) {
            return Color.WHITE;
        }
        switch (variant) {
            case PRIMARY:
                return isPressed() ? accentPressed : accent;
            case SECONDARY:
                return isPressed() ? accentTintPressed : accentTint;
            case NEUTRAL:
            default:
                return isPressed() ? neutralHoverPressed : neutralHover;
        }
    }

    /**
     * The ring stroke for the current state, or {@code null} when no ring
     * should be drawn (PRIMARY hover, where the solid fill reaches the edge).
     */
    private Paint borderPaint(int w, int h) {
        if (!isEnabled()) {
            // A disabled button keeps its ring, just faded. Dropping the
            // outline entirely was tried and it makes the control disappear:
            // with the disabled fill sitting close to the panel background,
            // all that remains is grey text floating in the layout, which
            // reads as a label rather than as a button you currently can't
            // press. The affordance has to survive being unavailable.
            return mix(neutral, uiColor("Panel.background", Color.WHITE), 0.55f);
        }
        switch (variant) {
            case PRIMARY:
                // Solid accent fill already defines the edge on hover/press.
                return isActive() ? null : new GradientPaint(0, 0, neutral, w, h, accent);
            case SECONDARY:
                // Flat neutral at rest; warms into the accent gradient on hover.
                return isActive() ? new GradientPaint(0, 0, neutral, w, h, accent) : neutral;
            case NEUTRAL:
            default:
                return neutral;
        }
    }

    // ── Painting ────────────────────────────────────────────────────────────

    /** Inset by half the stroke so the ring sits fully inside the bounds. */
    private RoundRectangle2D shape() {
        float s = BORDER_WIDTH;
        return new RoundRectangle2D.Float(
                s / 2f, s / 2f, getWidth() - s, getHeight() - s, ARC, ARC);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color fill = fillColor();
        if (fill != null) {
            g2.setColor(fill);
            g2.fill(shape());
        }
        g2.dispose();

        super.paintComponent(g);
    }

    @Override
    protected void paintBorder(Graphics g) {
        Paint paint = borderPaint(getWidth(), getHeight());
        if (paint == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(paint);
        g2.setStroke(new BasicStroke(BORDER_WIDTH));
        g2.draw(shape());
        g2.dispose();
    }

    @Override
    public boolean isOpaque() {
        return false;
    }
}
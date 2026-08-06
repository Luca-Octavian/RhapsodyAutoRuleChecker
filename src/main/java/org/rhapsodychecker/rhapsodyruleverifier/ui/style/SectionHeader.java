package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;

/**
 * The label strip at the top of a content pane ("Package Hierarchy",
 * "Results", "Details").
 *
 * <p>Styled as an <em>overline</em> rather than a title: small, uppercase,
 * letter-spaced and muted. This is the prevailing idiom for panel headers in
 * current tooling (VS Code side-bar sections, Figma inspector panels) and it
 * works because a section header is a <em>wayfinding</em> label, not content.
 * Rendering it as large bold text — the previous treatment — made it compete
 * with the data underneath for attention, which is backwards: the user already
 * knows which pane they're looking at, they're trying to read what's in it.
 *
 * <p>A hairline divider runs underneath. Whitespace alone was tried and it
 * doesn't hold up here: with several panes sharing one flat background and no
 * grid lines in the content, there was nothing to say where a header stopped
 * and its data began, and the whole window read as a single undifferentiated
 * field. The rule is the cheapest possible boundary — one pixel in the theme's
 * own border colour — and it costs the layout nothing.
 */
public class SectionHeader extends JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * Extra space between characters, as a fraction of the font size. Uppercase
     * text at small sizes is hard to read when tightly set, because the letters
     * lack the ascender/descender variation the eye normally uses to segment
     * words. Tracking it out restores legibility.
     */
    private static final float TRACKING = 0.08f;

    private static final float FONT_SIZE = 11f;

    public SectionHeader(String title) {
        setLayout(new BorderLayout());
        setOpaque(false);

        JLabel label = new JLabel(title.toUpperCase());
        label.setFont(trackedFont(label.getFont()));
        label.setForeground(mutedForeground());

        // Hairline underneath, then padding inside it. Compound order matters:
        // the matte is the outer border so the padding sits between the rule
        // and the text rather than outside the rule.
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, dividerColor()),
                BorderFactory.createEmptyBorder(10, 10, 6, 10)));

        add(label, BorderLayout.WEST);
    }

    /** Derives a small, bold, letter-spaced variant of the supplied font. */
    private static Font trackedFont(Font base) {
        Font sized = base.deriveFont(Font.BOLD, FONT_SIZE);
        Map<TextAttribute, Object> attributes = new HashMap<TextAttribute, Object>();
        attributes.put(TextAttribute.TRACKING, TRACKING);
        return sized.deriveFont(attributes);
    }

    /**
     * A foreground that sits between the panel background and the body text —
     * present enough to read, quiet enough to recede.
     */
    private static Color mutedForeground() {
        Color c = UIManager.getColor("Label.disabledForeground");
        if (c == null) c = UIManager.getColor("Component.borderColor");
        return c != null ? c : Color.GRAY;
    }

    /**
     * The divider colour, taken from the theme rather than hardcoded so the
     * rule stays visible against a dark background too.
     */
    private static Color dividerColor() {
        Color c = UIManager.getColor("Component.borderColor");
        if (c == null) c = UIManager.getColor("Separator.foreground");
        return c != null ? c : Color.LIGHT_GRAY;
    }
}

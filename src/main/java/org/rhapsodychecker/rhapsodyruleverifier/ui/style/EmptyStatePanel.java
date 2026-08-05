package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

import java.awt.*;
import javax.swing.*;

/**
 * A centered empty-state placeholder with a large glyph, a bold title,
 * and a smaller muted subtitle.
 *
 * <p>Used wherever a panel has no content yet (no model loaded, no results, etc.).
 */
public class EmptyStatePanel extends JPanel {

    public EmptyStatePanel(String glyph, String title, String subtitle) {
        setLayout(new GridBagLayout());
        setOpaque(false);

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);

        /* ── glyph ────────────────────────────────────────────── */
        JLabel glyphLabel = new JLabel(glyph, SwingConstants.CENTER);
        glyphLabel.setFont(glyphLabel.getFont().deriveFont(Font.PLAIN, 32f));
        glyphLabel.setForeground(new Color(0xBB, 0xBB, 0xBB));
        glyphLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(glyphLabel);

        column.add(Box.createVerticalStrut(8));

        /* ── title ────────────────────────────────────────────── */
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(titleLabel);

        column.add(Box.createVerticalStrut(4));

        /* ── subtitle ─────────────────────────────────────────── */
        JLabel subtitleLabel = new JLabel(subtitle, SwingConstants.CENTER);
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 12f));
        subtitleLabel.setForeground(Color.GRAY);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(subtitleLabel);

        add(column);
    }
}
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Reusable expand/collapse section with a clickable header row.
 *
 * <p>The header shows a toggle glyph ({@code ▸} collapsed / {@code ▾} expanded)
 * plus a title label. Clicking it toggles visibility of the wrapped content and
 * propagates {@code revalidate()}/{@code repaint()} up the ancestor chain so
 * enclosing scroll panes recalculate layout.
 */
public final class CollapsibleSection extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String COLLAPSED_GLYPH = "\u25B8 ";   // ▸
    private static final String EXPANDED_GLYPH  = "\u25BE ";   // ▾

    private final JLabel header;
    private final JComponent content;
    private final String title;
    private boolean expanded;

    /**
     * @param title         header text (e.g. "Advanced")
     * @param content       the component to show/hide
     * @param startExpanded whether to start expanded
     */
    public CollapsibleSection(String title, JComponent content, boolean startExpanded) {
        this.title = title;
        this.content = content;
        this.expanded = startExpanded;

        setLayout(new BorderLayout());
        setOpaque(false);

        // ── Thin top divider ──────────────────────────────────────────────────
        Color separatorColor = UIManager.getColor("Separator.foreground");
        if (separatorColor == null) separatorColor = Color.LIGHT_GRAY;
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, separatorColor));

        // ── Header ────────────────────────────────────────────────────────────
        header = new JLabel();
        header.setFont(header.getFont().deriveFont(Font.PLAIN, 13f));
        Color mutedText = UIManager.getColor("Label.disabledForeground");
        if (mutedText == null) mutedText = new Color(130, 130, 130);
        header.setForeground(mutedText);
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
        updateHeaderText();

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                setExpanded(!expanded);
            }
        });

        add(header, BorderLayout.NORTH);

        // ── Content ───────────────────────────────────────────────────────────
        content.setVisible(expanded);
        add(content, BorderLayout.CENTER);
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        content.setVisible(expanded);
        updateHeaderText();

        // Walk up the ancestor chain to revalidate enclosing scroll panes
        Container ancestor = getParent();
        while (ancestor != null) {
            ancestor.revalidate();
            ancestor.repaint();
            if (ancestor instanceof JScrollPane) break;
            ancestor = ancestor.getParent();
        }
        revalidate();
        repaint();
    }

    public boolean isExpanded() {
        return expanded;
    }

    private void updateHeaderText() {
        header.setText((expanded ? EXPANDED_GLYPH : COLLAPSED_GLYPH) + title);
    }
}
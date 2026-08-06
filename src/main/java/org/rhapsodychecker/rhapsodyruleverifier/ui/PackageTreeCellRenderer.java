// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/ui/PackageTreeCellRenderer.java
package org.rhapsodychecker.rhapsodyruleverifier.ui;

import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AppTheme;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

/**
 * Renders package nodes as name + muted child count.
 *
 * <p><b>No node icon.</b> An earlier version drew a filled purple square on
 * containers and a hollow outline on leaves, which turned out to duplicate
 * information the tree was already showing: FlatLaf draws a disclosure chevron
 * on every expandable row, so a container carried two separate "I have
 * children" markers a few pixels apart while a leaf carried one "I don't"
 * marker plus a blank where the chevron would be. The chevron is the one that
 * survives, because it is also the control you click and because it encodes
 * expanded-vs-collapsed state, which a static glyph cannot. Depth is carried by
 * the indent guides (see
 * {@link org.rhapsodychecker.rhapsodyruleverifier.ui.style.IndentGuideTree}).
 *
 * <p><b>Child counts are shown inline and muted.</b> Choosing an evaluation
 * scope means judging how much a package covers, so the count is the second
 * most useful fact after the name — but it must never compete with the name,
 * hence the greyed HTML span.
 *
 * <p>The renderer reuses the single component instance that
 * {@link DefaultTreeCellRenderer} provides, which is the standard Swing
 * contract — do not hold references to the returned component.
 */
final class PackageTreeCellRenderer extends DefaultTreeCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
            boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

        super.getTreeCellRendererComponent(
                tree, value, sel, expanded, leaf, row, hasFocus);

        // Breathing room inside the row. No icon gap is needed since no icon
        // is set, but leaving the value alone would inherit the stock 4px.
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 6));
        setIcon(null);
        setIconTextGap(0);

        if (!(value instanceof DefaultMutableTreeNode)) return this;
        Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
        if (!(userObj instanceof PackageNode)) return this;

        PackageNode node = (PackageNode) userObj;
        int childCount = node.children().size();

        // The count is rendered inside the same HTML string rather than as a
        // second component so that it participates in the selection highlight
        // and stays aligned with the label baseline.
        if (childCount > 0) {
            setText("<html>" + escape(node.name())
                    + " <span style='color:" + AppTheme.mutedTextHex() + ";'>&nbsp;"
                    + childCount + "</span></html>");
        } else {
            setText(escape(node.name()));
        }

        setToolTipText(node.qualifiedPath().isEmpty()
                ? node.name()
                : node.qualifiedPath().replace("::", " \u203A "));

        return this;
    }

    /**
     * Escapes HTML metacharacters. Required because the label is rendered as
     * HTML above — a package literally named {@code <default>} would otherwise
     * be swallowed as a tag.
     */
    private static String escape(String text) {
        if (text == null) return "";
        String amp = String.valueOf((char) 38);
        return text.replace(amp, amp + "amp;")
                .replace("<", amp + "lt;")
                .replace(">", amp + "gt;");
    }
}
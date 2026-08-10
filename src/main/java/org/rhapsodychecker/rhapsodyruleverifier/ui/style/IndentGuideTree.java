// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/ui/style/IndentGuideTree.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.style;

import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A {@link JTree} that draws VS Code-style vertical indent guides: one hairline
 * per ancestor level, running down the left edge of each subtree.
 *
 * <p><b>Why not {@code Tree.paintLines}.</b> Swing's built-in connector lines
 * are dotted elbows drawn per parent/child pair — the 1998 Windows Explorer
 * look. Indent guides are a different thing: continuous, straight, one per
 * <em>depth level</em>, so at a glance you can count how deep a row sits and
 * follow the column back up to the ancestor that owns it. That is exactly the
 * "which level am I on" question a package tree has to answer.
 *
 * <p><b>Guides stop at the last descendant.</b> Each guide is only as tall as
 * the rows it actually spans, so a line ends with its subtree rather than
 * running on to the bottom of the viewport. This is the detail that makes the
 * guides readable rather than a wall of stripes — the end of a line <em>is</em>
 * the end of a branch.
 *
 * <p><b>The selected row's ancestry is highlighted.</b> Guides on the path from
 * the root to the selection are drawn in the accent colour; everything else is
 * a muted hairline. Following the current row back up to its parent becomes a
 * glance instead of a count.
 *
 * <h2>Painting order</h2>
 *
 * <p>The guides must go <em>under</em> the rows, so they are drawn before
 * {@code super.paintComponent}. That forces {@link #setOpaque(boolean) opaque}
 * to be {@code false}: an opaque component has its background filled by
 * {@code ComponentUI.update()} at the start of {@code super.paintComponent},
 * which would paint over anything drawn beforehand. The background fill is
 * therefore done by hand here instead — the component still covers its whole
 * bounds, it just does so a few lines earlier than usual.
 */
public class IndentGuideTree extends JTree {

    private static final long serialVersionUID = 1L;

    /** Fallback when the UI delegate isn't a {@link BasicTreeUI}. */
    private static final int DEFAULT_RIGHT_CHILD_INDENT = 7;

    /**
     * Opacity of an ordinary guide.
     *
     * <p>This was originally much lower, on the theory that structure should
     * whisper. It whispered too quietly: against the light grey theme the
     * guides were only visible if you already knew to look for them, which
     * defeats the point of a glanceable depth cue. A guide has to be clearly
     * legible to do its job — the thing keeping it subordinate to the row text
     * is that it's one pixel wide, not that it's nearly transparent.
     */
    private static final int IDLE_ALPHA = 175;

    /** Opacity of a guide on the selected row's ancestry. */
    private static final int ACTIVE_ALPHA = 255;

    public IndentGuideTree(TreeModel model) {
        super(model);
        init();
    }

    private void init() {
        // See the class comment: guides are painted before the rows, which is
        // only possible if the UI's own background fill doesn't run first.
        setOpaque(false);

        // The active-guide highlight is derived from the selection, so a
        // selection change has to trigger a repaint of the guides too.
        addTreeSelectionListener(e -> repaint());
    }

    // ── Colours ────────────────────────────────────────────────────────────

    /**
     * Guides are theme-derived rather than hardcoded so that switching to a
     * dark IntelliJ theme keeps them visible instead of leaving black lines on
     * a black background.
     */
    private static Color idleGuideColor() {
        // Component.borderColor is preferred over Tree.hash: the latter is the
        // colour Swing reserved for its dotted connector lines and is tuned to
        // be almost invisible, which is the opposite of what's wanted here.
        Color base = UIManager.getColor("Component.borderColor");
        if (base == null) base = UIManager.getColor("Tree.hash");
        if (base == null) base = Color.GRAY;
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), IDLE_ALPHA);
    }

    private static Color activeGuideColor() {
        Color base = AccentColors.primary();
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), ACTIVE_ALPHA);
    }

    // ── Painting ───────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        Rectangle clip = g.getClipBounds();
        if (clip == null) clip = new Rectangle(0, 0, getWidth(), getHeight());

        g.setColor(getBackground());
        g.fillRect(clip.x, clip.y, clip.width, clip.height);

        paintIndentGuides((Graphics2D) g, clip);

        super.paintComponent(g);
    }

    private void paintIndentGuides(Graphics2D g, Rectangle clip) {
        Map<TreePath, int[]> spans = collectGuideSpans(clip);
        if (spans.isEmpty()) return;

        Set<TreePath> active = selectedAncestry();
        Color idleColor = idleGuideColor();
        Color activeColor = activeGuideColor();

        // Hairlines are crisper with antialiasing off; a 1px vertical line
        // gets smeared across two columns otherwise.
        Object previousHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_OFF);

        for (Map.Entry<TreePath, int[]> entry : spans.entrySet()) {
            int[] span = entry.getValue();
            g.setColor(active.contains(entry.getKey()) ? activeColor : idleColor);
            g.drawLine(span[0], span[1], span[0], span[2] - 1);
        }

        if (previousHint != null) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, previousHint);
        }
    }

    /**
     * Maps each ancestor that needs a guide to {@code {x, topY, bottomY}}.
     *
     * <p>Keying by ancestor and folding every descendant row into a min/max is
     * what makes a guide terminate at its last child: an ancestor's span simply
     * never extends past the last row that reports it.
     *
     * <p>Only rows intersecting the clip are walked. A guide that continues
     * beyond the clip is truncated to it, which is exactly what the clip would
     * have done to a full-length line anyway.
     */
    private Map<TreePath, int[]> collectGuideSpans(Rectangle clip) {
        Map<TreePath, int[]> spans = new LinkedHashMap<TreePath, int[]>();

        int rowCount = getRowCount();
        if (rowCount == 0) return spans;

        int firstRow = Math.max(0, getClosestRowForLocation(0, clip.y));
        int lastRow = getClosestRowForLocation(0, clip.y + clip.height - 1);
        if (lastRow < 0 || lastRow >= rowCount) lastRow = rowCount - 1;

        int rightChildIndent = rightChildIndent();

        for (int row = firstRow; row <= lastRow; row++) {
            TreePath path = getPathForRow(row);
            Rectangle rowBounds = getRowBounds(row);
            if (path == null || rowBounds == null) continue;

            for (TreePath ancestor = path.getParentPath();
                 ancestor != null;
                 ancestor = ancestor.getParentPath()) {

                Rectangle ancestorBounds = getPathBounds(ancestor);
                // Null means the ancestor isn't a displayed row — the hidden
                // root. Nothing above it is displayed either, so stop.
                if (ancestorBounds == null) break;

                // Align the guide with the ancestor's expand control, matching
                // BasicTreeUI.paintExpandControl's own knob placement.
                int x = ancestorBounds.x - rightChildIndent + 1;
                if (x < 0) continue;

                int[] span = spans.get(ancestor);
                if (span == null) {
                    spans.put(ancestor, new int[]{
                            x, rowBounds.y, rowBounds.y + rowBounds.height});
                } else {
                    span[1] = Math.min(span[1], rowBounds.y);
                    span[2] = Math.max(span[2], rowBounds.y + rowBounds.height);
                }
            }
        }
        return spans;
    }

    /** Every ancestor of the current selection, for the highlighted guides. */
    private Set<TreePath> selectedAncestry() {
        Set<TreePath> ancestry = new HashSet<TreePath>();
        TreePath selection = getSelectionPath();
        for (TreePath p = selection == null ? null : selection.getParentPath();
             p != null;
             p = p.getParentPath()) {
            ancestry.add(p);
        }
        return ancestry;
    }

    private int rightChildIndent() {
        TreeUI ui = getUI();
        if (ui instanceof BasicTreeUI) {
            return ((BasicTreeUI) ui).getRightChildIndent();
        }
        return DEFAULT_RIGHT_CHILD_INDENT;
    }
}
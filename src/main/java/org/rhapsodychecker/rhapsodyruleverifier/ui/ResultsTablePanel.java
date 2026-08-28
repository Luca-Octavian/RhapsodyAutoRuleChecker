// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/ui/ResultsTablePanel.java
package org.rhapsodychecker.rhapsodyruleverifier.ui;

import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleStatus;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AccentColors;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AppTheme;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.EmptyStatePanel;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.IndentGuideTree;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.SectionHeader;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.ThemeAware;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Displays rule evaluation failures in a tree layout.
 *
 * <p>Ungrouped failures identify the rule, element, and reason directly.
 * Grouped failures are nested: a parent node identifies the group and element
 * with a failure summary, and its children identify each failed rule and reason.
 * Full path and message details appear in the Details pane.
 *
 * <p>Double-click navigates to the element in Rhapsody — only on
 * individual rule nodes (leaf), not on group summary nodes.
 */
public final class ResultsTablePanel extends JPanel implements ThemeAware {

    private static final long serialVersionUID = 1L;

    // ── Node types stored as JTree user objects ────────────────────────────

    /** Parent node for a (group, element) that has at least one failure. */
    static final class GroupNode {
        final String group;
        final String elementGuid;
        final String elementName;
        final String location;
        final int failedCount;
        final int totalCount;

        GroupNode(String group, String elementGuid, String elementName,
                  String location, int failedCount, int totalCount) {
            this.group = group;
            this.elementGuid = elementGuid;
            this.elementName = elementName;
            this.location = location;
            this.failedCount = failedCount;
            this.totalCount = totalCount;
        }
    }

    /** Leaf node for a single rule failure (grouped or ungrouped). */
    static final class RuleNode {
        final String ruleId;
        final String elementGuid;
        final String elementName;
        final String location;
        final String reason;

        RuleNode(String ruleId, String elementGuid, String elementName,
                 String location, String reason) {
            this.ruleId = ruleId;
            this.elementGuid = elementGuid;
            this.elementName = elementName;
            this.location = location;
            this.reason = reason;
        }
    }

    // ── Small dot icon drawn via Graphics2D ────────────────────────────────

    /** A tiny filled-circle icon for tree rows. */
    private static final class DotIcon implements Icon {
        private static final int SIZE = 8;

        @Override public int getIconWidth()  { return SIZE; }
        @Override public int getIconHeight() { return SIZE; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(AccentColors.failure());
            g2.fillOval(x, y, SIZE, SIZE);
            g2.dispose();
        }
    }

    /**
     * Only leaf rule rows get a dot. A group row already carries a disclosure
     * chevron — adding a coloured dot beside it would be two markers for the
     * one fact that the row has children, which is the duplication that made
     * the package tree read as cluttered.
     */
    private static final Icon RULE_DOT = new DotIcon();

    // ── UI components ─────────────────────────────────────────────────────

    private final JTree tree;
    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel treeModel;
    private final JTextField filterField;
    private JLabel statusLabel;

    /* ── Detail pane: grid (top) + text area (bottom) ──────────────────── */
    private final JLabel detailLabel1Key   = new JLabel();
    private final JLabel detailLabel1Value = new JLabel();
    private final JLabel detailLabel2Key   = new JLabel("Element:");
    private final JLabel detailLabel2Value = new JLabel();
    private final JLabel detailLabel3Key   = new JLabel("Path:");
    private final JLabel detailLabel3Value = new JLabel();
    private final JPanel detailGrid;
    private final JTextArea detailArea;

    private JScrollPane treeScroll;
    private final JSplitPane splitPane;
    private JPanel detailPane;

    private final CardLayout centerLayout = new CardLayout();
    private final JPanel     centerPanel  = new JPanel(centerLayout);
    private static final String CARD_TREE        = "tree";
    private static final String CARD_PLACEHOLDER = "placeholder";

    private Consumer<String> onElementDoubleClick;

    // Kept for rebuilding after filter
    private List<RuleResult> lastResults = Collections.emptyList();
    private ElementIndex lastIndex;
    private List<RuleSpec> lastSpecs = Collections.emptyList();

    public ResultsTablePanel() {
        setLayout(new BorderLayout(5, 5));

        // Frames the pane against its neighbour across the split divider; see
        // the same call in PackageTreePanel.
        refreshTheme();

        /* ── Header + filter bar (NORTH) ──────────────────────────────── */
        JPanel filterPanel = new JPanel(new GridBagLayout());
        GridBagConstraints fgbc = new GridBagConstraints();
        fgbc.insets = new Insets(0, 5, 0, 5);
        fgbc.anchor = GridBagConstraints.WEST;

        filterField = new JTextField();
        filterField.setColumns(24);
        filterField.setToolTipText("Type to filter results (matches rule ID, element name, reason)");
        filterField.putClientProperty("JTextField.showClearButton", true);
        filterField.putClientProperty("JTextField.placeholderText", "Filter results\u2026");
        filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });

        fgbc.gridx = 0; fgbc.weightx = 0; fgbc.fill = GridBagConstraints.NONE;
        filterPanel.add(filterField, fgbc);

        fgbc.gridx = 1; fgbc.weightx = 1;
        filterPanel.add(Box.createHorizontalGlue(), fgbc);

        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.add(new SectionHeader("Results"));
        northPanel.add(filterPanel);

        // Tree
        rootNode = new DefaultMutableTreeNode("Results");
        treeModel = new DefaultTreeModel(rootNode);
        // Indent guides tie each rule row back to the group it belongs to,
        // which matters most when several groups are expanded at once.
        tree = new IndentGuideTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new ResultTreeCellRenderer());
        // Every row is a single line, so a fixed height is safe. This matters:
        // setRowHeight(0) forces the tree to call the renderer and measure each
        // row individually on every layout pass, which is O(rows) work repeated
        // constantly and is visibly slow on large result sets.
        tree.setRowHeight(28);
        tree.setToggleClickCount(1);
        ToolTipManager.sharedInstance().registerComponent(tree);

        // Double-click: navigate only on individual rule nodes (leaf), not groups
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && onElementDoubleClick != null) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path == null) return;
                    DefaultMutableTreeNode node =
                            (DefaultMutableTreeNode) path.getLastPathComponent();
                    Object userObj = node.getUserObject();
                    if (userObj instanceof RuleNode) {
                        onElementDoubleClick.accept(((RuleNode) userObj).elementGuid);
                    }
                    // GroupNode double-click: do nothing (just expand/collapse)
                }
            }
        });

        // Selection updates detail pane
        tree.addTreeSelectionListener(e -> updateDetailPane());

        treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(BorderFactory.createEmptyBorder());

        // Detail pane — structured grid + free-form text
        detailGrid = buildDetailGrid();
        detailArea = new JTextArea(5, 40);
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setText(DETAIL_HINT);

        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel detailContent = new JPanel(new BorderLayout(0, 6));
        detailContent.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
        detailContent.add(detailGrid, BorderLayout.NORTH);
        detailContent.add(detailScroll, BorderLayout.CENTER);

        detailPane = new JPanel(new BorderLayout());
        // A rule along the top edge, so the seam between results and details
        // is visible even when the divider isn't being hovered.
        detailPane.setBorder(AppTheme.edgeBorder(1, 0, 0, 0));
        detailPane.add(new SectionHeader("Details"), BorderLayout.NORTH);
        detailPane.add(detailContent, BorderLayout.CENTER);

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treeScroll, detailPane);
        splitPane.setResizeWeight(0.75);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(6);

        // Placeholder — empty state
        EmptyStatePanel placeholderPanel = new EmptyStatePanel(
                "",
                "No output",
                "Run a config file to see its results here.");

        centerPanel.add(splitPane, CARD_TREE);
        centerPanel.add(placeholderPanel, CARD_PLACEHOLDER);
        centerLayout.show(centerPanel, CARD_PLACEHOLDER);

        // Status bar
        statusLabel = new JLabel("No results");
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                AppTheme.edgeBorder(1, 0, 0, 0),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        add(northPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    @Override
    public void refreshTheme() {
        setBorder(AppTheme.panelBorder());

        Color muted = AccentColors.mutedText();
        for (JLabel key : new JLabel[]{detailLabel1Key, detailLabel2Key, detailLabel3Key}) {
            key.setForeground(muted);
        }

        if (detailPane != null) {
            detailPane.setBorder(AppTheme.edgeBorder(1, 0, 0, 0));
        }

        if (statusLabel != null) {
            statusLabel.setBorder(BorderFactory.createCompoundBorder(
                    AppTheme.edgeBorder(1, 0, 0, 0),
                    BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        }

        Color bg = UIManager.getColor("Tree.background");
        Color fg = UIManager.getColor("Tree.foreground");
        if (tree != null) {
            if (bg != null) {
                tree.setBackground(bg);
                treeScroll.getViewport().setBackground(bg);
            }
            if (fg != null) tree.setForeground(fg);
            tree.setCellRenderer(new ResultTreeCellRenderer());
        }

        Color textBg = UIManager.getColor("TextArea.background");
        Color textFg = UIManager.getColor("TextArea.foreground");
        if (detailArea != null) {
            if (textBg != null) detailArea.setBackground(textBg);
            if (textFg != null) detailArea.setForeground(textFg);
        }

        repaint();
    }

    private static final String DETAIL_HINT =
            "Select a node to see details.\n"
          + "Double-click an individual rule failure to navigate in Rhapsody.";

    public void setOnElementDoubleClick(Consumer<String> callback) {
        this.onElementDoubleClick = callback;
    }

    /**
     * Loads results and builds the tree.
     *
     * @param results all rule results from evaluation
     * @param index   element index for resolving names/paths
     * @param specs   rule specs (used to determine group membership)
     */
    public void loadResults(List<RuleResult> results, ElementIndex index, List<RuleSpec> specs) {
        this.lastResults = results;
        this.lastIndex = index;
        this.lastSpecs = specs;

        buildTree(results, index, specs, null);

        filterField.setText("");
        centerLayout.show(centerPanel, CARD_TREE);
    }

    public void clear() {
        rootNode.removeAllChildren();
        treeModel.reload();
        lastResults = Collections.emptyList();
        lastSpecs = Collections.emptyList();
        lastIndex = null;
        statusLabel.setText("No results");
        clearDetailPane();
        filterField.setText("");
        centerLayout.show(centerPanel, CARD_PLACEHOLDER);
    }

    // ── Detail pane construction ──────────────────────────────────────────

    private JPanel buildDetailGrid() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(1, 0, 1, 8);

        // Style key labels as muted
        Font keyFont = detailLabel1Key.getFont().deriveFont(Font.PLAIN, 12f);
        Color keyColor = AccentColors.palette().mutedText();
        for (JLabel key : new JLabel[]{detailLabel1Key, detailLabel2Key, detailLabel3Key}) {
            key.setFont(keyFont);
            key.setForeground(keyColor);
        }

        Font valueFont = detailLabel1Value.getFont().deriveFont(Font.PLAIN, 12f);
        for (JLabel val : new JLabel[]{detailLabel1Value, detailLabel2Value, detailLabel3Value}) {
            val.setFont(valueFont);
        }

        // Row 0
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        grid.add(detailLabel1Key, gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        grid.add(detailLabel1Value, gbc);

        // Row 1
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        grid.add(detailLabel2Key, gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        grid.add(detailLabel2Value, gbc);

        // Row 2
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        grid.add(detailLabel3Key, gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        grid.add(detailLabel3Value, gbc);

        return grid;
    }

    // ── Tree construction ─────────────────────────────────────────────────

    private void buildTree(List<RuleResult> results, ElementIndex index,
                           List<RuleSpec> specs, String filterText) {
        rootNode.removeAllChildren();

        // Build ruleId -> group lookup
        Map<String, String> ruleIdToGroup = new LinkedHashMap<>();
        for (RuleSpec spec : specs) {
            if (spec.group().isPresent()) {
                ruleIdToGroup.put(spec.id(), spec.group().get());
            }
        }

        // Separate failures into grouped and ungrouped
        List<RuleResult> ungroupedFails = new ArrayList<>();
        // For grouped: bucket by (group + "\0" + elementGuid)
        Map<String, List<RuleResult>> groupedBuckets = new LinkedHashMap<>();
        // Track total results per (group, element) — including PASS — for "N of M" display
        Map<String, Integer> groupedTotals = new LinkedHashMap<>();

        for (RuleResult r : results) {
            String group = ruleIdToGroup.get(r.ruleId());
            if (group != null) {
                String key = group + "\0" + r.elementGuid();
                Integer count = groupedTotals.get(key);
                groupedTotals.put(key, count != null ? count + 1 : 1);

                if (r.status() == RuleStatus.FAIL) {
                    List<RuleResult> bucket = groupedBuckets.get(key);
                    if (bucket == null) {
                        bucket = new ArrayList<>();
                        groupedBuckets.put(key, bucket);
                    }
                    bucket.add(r);
                }
            } else {
                if (r.status() == RuleStatus.FAIL) {
                    ungroupedFails.add(r);
                }
            }
        }

        int totalFailNodes = 0;

        // Add grouped failure nodes (parent + children)
        for (Map.Entry<String, List<RuleResult>> entry : groupedBuckets.entrySet()) {
            String[] parts = entry.getKey().split("\0", 2);
            String group = parts[0];
            String elementGuid = parts[1];
            List<RuleResult> memberFails = entry.getValue();
            int totalInGroup = groupedTotals.containsKey(entry.getKey())
                    ? groupedTotals.get(entry.getKey()) : memberFails.size();

            String elementName = resolveElementName(index, elementGuid);
            String location = resolveLocation(index, elementGuid, elementName);

            // Apply filter
            if (filterText != null && !filterText.isEmpty()) {
                boolean anyMatch = matchesFilter(group, filterText)
                        || matchesFilter(elementName, filterText)
                        || matchesFilter(location, filterText);
                if (!anyMatch) {
                    // Check if any child matches
                    boolean childMatch = false;
                    for (RuleResult mr : memberFails) {
                        if (matchesFilter(mr.ruleId(), filterText)
                                || matchesFilter(mr.message(), filterText)) {
                            childMatch = true;
                            break;
                        }
                    }
                    if (!childMatch) continue;
                }
            }

            GroupNode gn = new GroupNode(group, elementGuid, elementName,
                    location, memberFails.size(), totalInGroup);
            DefaultMutableTreeNode groupTreeNode = new DefaultMutableTreeNode(gn);

            for (RuleResult mr : memberFails) {
                RuleNode rn = new RuleNode(mr.ruleId(), mr.elementGuid(),
                        elementName, location, mr.message());
                groupTreeNode.add(new DefaultMutableTreeNode(rn));
                totalFailNodes++;
            }

            rootNode.add(groupTreeNode);
        }

        // Add ungrouped failure nodes (flat, root-level)
        for (RuleResult r : ungroupedFails) {
            String elementName = resolveElementName(index, r.elementGuid());
            String location = resolveLocation(index, r.elementGuid(), elementName);

            if (filterText != null && !filterText.isEmpty()) {
                if (!matchesFilter(r.ruleId(), filterText)
                        && !matchesFilter(elementName, filterText)
                        && !matchesFilter(location, filterText)
                        && !matchesFilter(r.message(), filterText)) {
                    continue;
                }
            }

            RuleNode rn = new RuleNode(r.ruleId(), r.elementGuid(),
                    elementName, location, r.message());
            rootNode.add(new DefaultMutableTreeNode(rn));
            totalFailNodes++;
        }

        treeModel.reload();

        // Expand all group nodes so children are visible immediately
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            if (child.getUserObject() instanceof GroupNode) {
                tree.expandPath(new TreePath(child.getPath()));
            }
        }

        // The count is the fact; the navigation hint is instruction. Emphasising
        // the number and muting the hint keeps the eye on the former, and lets
        // the reader skip the latter entirely once they know the gesture.
        int groupFailCount = groupedBuckets.size();
        StringBuilder status = new StringBuilder("<html><b>")
                .append(totalFailNodes)
                .append(totalFailNodes == 1 ? " failure" : " failures")
                .append("</b>");
        if (groupFailCount > 0) {
            status.append(" across ").append(groupFailCount)
                  .append(groupFailCount == 1 ? " grouped element" : " grouped elements");
        }
        status.append("<span style='color:").append(AppTheme.mutedTextHex()).append(";'>")
              .append("&nbsp;&nbsp;&middot;&nbsp;&nbsp;")
              .append("double-click a rule to open it in Rhapsody")
              .append("</span></html>");
        statusLabel.setText(status.toString());

        clearDetailPane();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String resolveElementName(ElementIndex index, String guid) {
        if (index == null) return "<unknown>";
        Optional<ElementRecord> rec = index.repository().get(guid);
        return rec.isPresent() ? rec.get().name() : "<unknown>";
    }

    private String resolveLocation(ElementIndex index, String guid, String elementName) {
        if (index == null) return elementName;
        Optional<ElementRecord> rec = index.repository().get(guid);
        String path = rec.isPresent() ? rec.get().ownerPath().orElse("") : "";
        return path.isEmpty()
                ? elementName + " (project root)"
                : path.replace("::", " > ") + " > " + elementName;
    }

    private static boolean matchesFilter(String text, String filter) {
        if (text == null || filter == null) return false;
        return text.toLowerCase().contains(filter.toLowerCase());
    }

    // ── Detail pane ───────────────────────────────────────────────────────

    private void clearDetailPane() {
        detailLabel1Key.setText("");
        detailLabel1Value.setText("");
        detailLabel2Value.setText("");
        detailLabel3Value.setText("");
        detailGrid.setVisible(false);
        detailArea.setText(DETAIL_HINT);
    }

    private void updateDetailPane() {
        TreePath selPath = tree.getSelectionPath();
        if (selPath == null) {
            clearDetailPane();
            return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) selPath.getLastPathComponent();
        Object userObj = node.getUserObject();

        if (userObj instanceof GroupNode) {
            GroupNode gn = (GroupNode) userObj;
            detailLabel1Key.setText("Group:");
            detailLabel1Value.setText(gn.group);
            detailLabel2Value.setText(gn.elementName);
            detailLabel3Value.setText(gn.location);
            detailGrid.setVisible(true);

            StringBuilder sb = new StringBuilder();
            sb.append(gn.failedCount).append(" of ").append(gn.totalCount)
              .append(" rules in this group failed for this element:\n\n");
            for (int i = 0; i < node.getChildCount(); i++) {
                DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) node.getChildAt(i);
                if (childNode.getUserObject() instanceof RuleNode) {
                    RuleNode rn = (RuleNode) childNode.getUserObject();
                    sb.append("  ").append(i + 1).append(". ").append(rn.ruleId)
                      .append(": ").append(rn.reason).append("\n");
                }
            }
            detailArea.setText(sb.toString());
        } else if (userObj instanceof RuleNode) {
            RuleNode rn = (RuleNode) userObj;
            detailLabel1Key.setText("Rule:");
            detailLabel1Value.setText(rn.ruleId);
            detailLabel2Value.setText(rn.elementName);
            detailLabel3Value.setText(rn.location);
            detailGrid.setVisible(true);

            detailArea.setText(rn.reason);
        }

        detailArea.setCaretPosition(0);
    }

    // ── Filter ────────────────────────────────────────────────────────────

    private void applyFilter() {
        if (lastIndex == null) return;
        String text = filterField.getText().trim();
        buildTree(lastResults, lastIndex, lastSpecs, text.isEmpty() ? null : text);
    }

    // ── Custom tree cell renderer ─────────────────────────────────────────

    /**
     * Renders one failure per row as: <em>identifier</em> — <em>subject</em> —
     * <em>muted reason</em>. Paths and full messages stay in the Details pane
     * so rows remain scannable.
     *
     * <p>Two rules govern the markup here:
     *
     * <ul>
     *   <li><b>Always exactly one line.</b> The tree uses a fixed row height
     *       for performance, so a {@code <br>} would clip. The reason is
     *       truncated instead, and the untruncated text is available both in
     *       the tooltip and the Details pane.</li>
     *   <li><b>No "Rule:" / "Why:" field labels.</b> Every row has the same
     *       shape, so repeating the field names on all of them is noise — the
     *       reader learns the column positions after the first row. Weight and
     *       colour carry the distinction instead: the identifier is emphasised,
     *       the reason is muted.</li>
     * </ul>
     */
    private static final class ResultTreeCellRenderer extends DefaultTreeCellRenderer {

        private static final long serialVersionUID = 1L;

        private static final int MAX_REASON_LENGTH = 140;

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 6));
            setIconTextGap(8);

            String separator = separatorHtml();
            String muted = AppTheme.mutedTextHex();

            if (!(value instanceof DefaultMutableTreeNode)) return this;
            Object userObj = ((DefaultMutableTreeNode) value).getUserObject();

            if (userObj instanceof GroupNode) {
                renderGroup((GroupNode) userObj, separator, muted);
            } else if (userObj instanceof RuleNode) {
                renderRule((DefaultMutableTreeNode) value, (RuleNode) userObj, separator, muted);
            } else {
                setIcon(null);
                setToolTipText(null);
            }

            return this;
        }

        private static String separatorHtml() {
            return " <span style='color:" + AppTheme.faintTextHex()
                    + ";'>&nbsp;&mdash;&nbsp;</span> ";
        }

        private void renderGroup(GroupNode gn, String separator, String muted) {
            // No icon: the chevron on this row is already the "has children"
            // marker, and it's the one the user clicks.
            setIcon(null);
            setFont(getFont().deriveFont(Font.PLAIN));

            // The failure count is the actionable number on a group row, so it
            // is the only emphasised part.
            setText("<html><b>" + html(gn.group) + "</b>"
                    + separator + html(gn.elementName)
                    + separator + "<span style='color:" + muted + ";'>"
                    + gn.failedCount + " of " + gn.totalCount + " failed</span>"
                    + "</html>");

            setToolTipText(gn.group + " \u2014 " + gn.elementName
                    + " \u2014 " + gn.failedCount + " of " + gn.totalCount
                    + " rules failed");
        }

        private void renderRule(DefaultMutableTreeNode treeNode, RuleNode rn,
                                String separator, String muted) {
            setIcon(RULE_DOT);
            setFont(getFont().deriveFont(Font.PLAIN));

            boolean nestedInGroup = treeNode.getParent() instanceof DefaultMutableTreeNode
                    && ((DefaultMutableTreeNode) treeNode.getParent())
                            .getUserObject() instanceof GroupNode;

            StringBuilder sb = new StringBuilder("<html><b>");
            sb.append(html(rn.ruleId)).append("</b>");

            // A nested row's parent already names the element; repeating it
            // would push the reason off the right edge for no information gain.
            if (!nestedInGroup) {
                sb.append(separator).append(html(rn.elementName));
            }

            sb.append(separator).append("<span style='color:").append(muted).append(";'>")
              .append(html(abbreviate(rn.reason))).append("</span></html>");

            setText(sb.toString());
            setToolTipText(rn.ruleId + " \u2014 " + rn.elementName
                    + "\n" + rn.reason);
        }

        private static String abbreviate(String text) {
            if (text == null || text.length() <= MAX_REASON_LENGTH) {
                return text == null ? "" : text;
            }
            return text.substring(0, MAX_REASON_LENGTH - 1) + "\u2026";
        }

        private static String html(String text) {
            if (text == null) return "";
            String ampersand = String.valueOf((char) 38);
            return text.replace(ampersand, ampersand + "amp;")
                    .replace("<", ampersand + "lt;")
                    .replace(">", ampersand + "gt;")
                    .replace(String.valueOf((char) 34), ampersand + "quot;");
        }
    }
}
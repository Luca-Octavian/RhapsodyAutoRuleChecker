// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/ui/ResultsTablePanel.java
package org.rhapsodychecker.rhapsodyruleverifier.ui;

import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleGrouping;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleStatus;

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
 * <p>Ungrouped rule failures appear as flat root-level nodes.
 * Grouped rule failures are nested: a parent node per (group, element)
 * shows a summary ("2 of 3 rules failed"), and expanding it reveals
 * each individual failing rule as a child node.
 *
 * <p>Double-click navigates to the element in Rhapsody — only on
 * individual rule nodes (leaf), not on group summary nodes.
 */
public final class ResultsTablePanel extends JPanel {

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

    // ── UI components ─────────────────────────────────────────────────────

    private final JTree tree;
    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel treeModel;
    private final JTextField filterField;
    private final JLabel statusLabel;

    private final JTextArea detailArea;
    private final JSplitPane splitPane;

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

        // Filter bar
        JPanel filterPanel = new JPanel(new BorderLayout(5, 0));
        filterPanel.add(new JLabel("  Filter: "), BorderLayout.WEST);
        filterField = new JTextField();
        filterField.setToolTipText("Type to filter results (matches rule ID, element name, reason)");
        filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });
        filterPanel.add(filterField, BorderLayout.CENTER);

        // Tree
        rootNode = new DefaultMutableTreeNode("Results");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new ResultTreeCellRenderer());
        tree.setRowHeight(24);
        tree.setToggleClickCount(1);

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

        JScrollPane treeScroll = new JScrollPane(tree);

        // Detail pane
        detailArea = new JTextArea(5, 40);
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setText(DETAIL_HINT);
        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(BorderFactory.createTitledBorder("Details"));

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treeScroll, detailScroll);
        splitPane.setResizeWeight(0.75);
        splitPane.setContinuousLayout(true);
        splitPane.setOneTouchExpandable(true);

        // Placeholder
        JLabel placeholderLabel = new JLabel(
                "<html><div style='text-align:center;'>No output. Run a config file to see its output here.</div></html>",
                SwingConstants.CENTER);
        placeholderLabel.setForeground(Color.GRAY);
        JPanel placeholderPanel = new JPanel(new GridBagLayout());
        placeholderPanel.add(placeholderLabel);

        centerPanel.add(splitPane, CARD_TREE);
        centerPanel.add(placeholderPanel, CARD_PLACEHOLDER);
        centerLayout.show(centerPanel, CARD_PLACEHOLDER);

        // Status bar
        statusLabel = new JLabel("  No results");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

        add(filterPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
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
        statusLabel.setText("  No results");
        detailArea.setText(DETAIL_HINT);
        filterField.setText("");
        centerLayout.show(centerPanel, CARD_PLACEHOLDER);
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

        int groupFailCount = groupedBuckets.size();
        StringBuilder status = new StringBuilder("  Failures: ").append(totalFailNodes);
        if (groupFailCount > 0) {
            status.append("  (").append(groupFailCount).append(" grouped element(s))");
        }
        status.append("  —  double-click a rule to navigate in Rhapsody");
        statusLabel.setText(status.toString());

        detailArea.setText(DETAIL_HINT);
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

    private void updateDetailPane() {
        TreePath selPath = tree.getSelectionPath();
        if (selPath == null) {
            detailArea.setText(DETAIL_HINT);
            return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) selPath.getLastPathComponent();
        Object userObj = node.getUserObject();

        StringBuilder sb = new StringBuilder();

        if (userObj instanceof GroupNode) {
            GroupNode gn = (GroupNode) userObj;
            sb.append("Group:    ").append(gn.group).append("\n");
            sb.append("Element:  ").append(gn.elementName).append("\n");
            sb.append("Location: ").append(gn.location).append("\n\n");
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
        } else if (userObj instanceof RuleNode) {
            RuleNode rn = (RuleNode) userObj;
            sb.append("Rule:     ").append(rn.ruleId).append("\n");
            sb.append("Element:  ").append(rn.elementName).append("\n");
            sb.append("Location: ").append(rn.location).append("\n\n");
            sb.append("Reason:\n  ").append(rn.reason);
        }

        detailArea.setText(sb.toString());
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
     * Renders tree nodes with a multi-column appearance:
     *   GroupNode:  "▶ GROUP_NAME  |  ElementName  |  Location  |  N of M failed"
     *   RuleNode:   "  RULE_ID  |  ElementName  |  Location  |  Reason"
     *   (for children under a group, element/location are hidden to reduce noise)
     */
    private static final class ResultTreeCellRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (!(value instanceof DefaultMutableTreeNode)) return this;
            Object userObj = ((DefaultMutableTreeNode) value).getUserObject();

            setIcon(null);

            if (userObj instanceof GroupNode) {
                GroupNode gn = (GroupNode) userObj;
                setText(gn.group + "  |  " + gn.elementName
                        + "  |  " + gn.location
                        + "  |  " + gn.failedCount + " of " + gn.totalCount + " failed");
                setFont(getFont().deriveFont(Font.BOLD));
            } else if (userObj instanceof RuleNode) {
                RuleNode rn = (RuleNode) userObj;
                DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) value;
                boolean isChild = treeNode.getParent() != null
                        && treeNode.getParent() instanceof DefaultMutableTreeNode
                        && ((DefaultMutableTreeNode) treeNode.getParent()).getUserObject() instanceof GroupNode;

                if (isChild) {
                    setText(rn.ruleId + "  |  " + rn.reason);
                } else {
                    setText(rn.ruleId + "  |  " + rn.elementName
                            + "  |  " + rn.location + "  |  " + rn.reason);
                }
                setFont(getFont().deriveFont(Font.PLAIN));
            }

            return this;
        }
    }
}
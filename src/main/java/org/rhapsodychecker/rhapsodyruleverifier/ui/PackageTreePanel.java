package org.rhapsodychecker.rhapsodyruleverifier.ui;

import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AppTheme;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.EmptyStatePanel;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.IndentGuideTree;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.SectionHeader;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.ThemeAware;

import java.awt.*;

import javax.swing.*;
import javax.swing.tree.*;

/**
 * A tree view of the model's package hierarchy.
 *
 * <p>Uses a CardLayout to flip between an empty-state placeholder
 * (when no model is loaded) and the actual tree scroll pane.
 */
public class PackageTreePanel extends JPanel implements ThemeAware {

    private static final String CARD_TREE        = "tree";
    private static final String CARD_PLACEHOLDER = "placeholder";

    private final CardLayout centerLayout = new CardLayout();
    private final JPanel     centerPanel  = new JPanel(centerLayout);

    private DefaultMutableTreeNode rootNode;
    private DefaultTreeModel       treeModel;
    private JTree                  tree;

    public PackageTreePanel() {
        setLayout(new BorderLayout());

        // The pane frames itself. Relying on the split-pane divider alone left
        // this panel and the results panel bleeding into one another, since
        // they share a background and the divider is only a few pixels wide.
        refreshTheme();

        add(new SectionHeader("Package Hierarchy"), BorderLayout.NORTH);

        rootNode  = new DefaultMutableTreeNode("Model");
        treeModel = new DefaultTreeModel(rootNode);
        // Indent guides make the nesting level readable at a glance, which is
        // the whole point of this pane; see IndentGuideTree.
        tree      = new IndentGuideTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        // Chevron + inline child counts; see PackageTreeCellRenderer.
        tree.setCellRenderer(new PackageTreeCellRenderer());
        ToolTipManager.sharedInstance().registerComponent(tree);

        JScrollPane treeScroll = new JScrollPane(tree);
        // The panel's own border above already frames this region, so the
        // scroll pane's would sit a pixel inside it and read as a double rule.
        treeScroll.setBorder(BorderFactory.createEmptyBorder());

        EmptyStatePanel placeholder = new EmptyStatePanel(
                "\uD83D\uDCC2",
                "No model loaded",
                "");
        placeholder.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Wrap in a JScrollPane so the placeholder gets the same FlatLaf
        // border that the tree scroll pane provides when data is loaded.
        JScrollPane placeholderScroll = new JScrollPane(placeholder);
        placeholderScroll.getViewport().setOpaque(false);
        placeholderScroll.setOpaque(false);
        placeholderScroll.setBorder(BorderFactory.createEmptyBorder());

        centerPanel.add(treeScroll, CARD_TREE);
        centerPanel.add(placeholderScroll, CARD_PLACEHOLDER);
        centerLayout.show(centerPanel, CARD_PLACEHOLDER);

        add(centerPanel, BorderLayout.CENTER);
    }

    @Override
    public void refreshTheme() {
        setBorder(AppTheme.panelBorder());
        Color background = UIManager.getColor("Panel.background");
        if (background != null) {
            setBackground(background);
            centerPanel.setBackground(background);
        }
        repaint();
    }

    /**
     * Populate the tree from a {@link PackageNode} hierarchy.
     * The root PackageNode itself is shown as the top-level visible node.
     */
    public void loadTree(PackageNode root) {
        rootNode.removeAllChildren();
        DefaultMutableTreeNode topNode = new DefaultMutableTreeNode(root);
        rootNode.add(topNode);
        addChildren(topNode, root);
        treeModel.reload();
        // Expand the top-level node so its children are visible
        tree.expandPath(new TreePath(topNode.getPath()));
        centerLayout.show(centerPanel, CARD_TREE);
    }

    /** Clear the tree. */
    public void clear() {
        rootNode.removeAllChildren();
        treeModel.reload();
        centerLayout.show(centerPanel, CARD_PLACEHOLDER);
    }

    /**
     * Return the selected {@link PackageNode}, or {@code null} if nothing
     * is selected.
     */
    public PackageNode getSelectedPackage() {
        Object selected = tree.getLastSelectedPathComponent();
        if (selected instanceof DefaultMutableTreeNode) {
            Object userObj = ((DefaultMutableTreeNode) selected).getUserObject();
            if (userObj instanceof PackageNode) {
                return (PackageNode) userObj;
            }
        }
        return null;
    }

    /**
     * Return the qualified path of the currently selected tree node,
     * or empty string if nothing is selected (meaning "no scope filter").
     */
    public String getSelectedPath() {
        PackageNode selected = getSelectedPackage();
        return selected != null ? selected.qualifiedPath() : "";
    }

    private void addChildren(DefaultMutableTreeNode parent, PackageNode node) {
        for (PackageNode child : node.children()) {
            DefaultMutableTreeNode treeChild = new DefaultMutableTreeNode(child);
            parent.add(treeChild);
            addChildren(treeChild, child);
        }
    }
}
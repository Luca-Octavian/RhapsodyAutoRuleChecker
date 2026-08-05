package org.rhapsodychecker.rhapsodyruleverifier.ui;

import org.rhapsodychecker.rhapsodyruleverifier.ui.style.EmptyStatePanel;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.SectionHeader;

import java.awt.*;

import javax.swing.*;
import javax.swing.tree.*;

/**
 * A tree view of the model's package hierarchy.
 *
 * <p>Uses a CardLayout to flip between an empty-state placeholder
 * (when no model is loaded) and the actual tree scroll pane.
 */
public class PackageTreePanel extends JPanel {

    private static final String CARD_TREE        = "tree";
    private static final String CARD_PLACEHOLDER = "placeholder";

    private final CardLayout centerLayout = new CardLayout();
    private final JPanel     centerPanel  = new JPanel(centerLayout);

    private DefaultMutableTreeNode rootNode;
    private DefaultTreeModel       treeModel;
    private JTree                  tree;

    public PackageTreePanel() {
        setLayout(new BorderLayout());

        add(new SectionHeader("Package Hierarchy"), BorderLayout.NORTH);

        rootNode  = new DefaultMutableTreeNode("Model");
        treeModel = new DefaultTreeModel(rootNode);
        tree      = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);

        JScrollPane treeScroll = new JScrollPane(tree);

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

        centerPanel.add(treeScroll, CARD_TREE);
        centerPanel.add(placeholderScroll, CARD_PLACEHOLDER);
        centerLayout.show(centerPanel, CARD_PLACEHOLDER);

        add(centerPanel, BorderLayout.CENTER);
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
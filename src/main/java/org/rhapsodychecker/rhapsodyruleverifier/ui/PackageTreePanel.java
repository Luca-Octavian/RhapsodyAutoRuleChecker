// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/ui/PackageTreePanel.java
package org.rhapsodychecker.rhapsodyruleverifier.ui;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;

/**
 * Panel containing a JTree that displays the package hierarchy.
 * User selects a node to scope the rule evaluation.
 */
public final class PackageTreePanel extends JPanel {

    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;

    public PackageTreePanel() {
        setLayout(new BorderLayout());

        rootNode = new DefaultMutableTreeNode("No model loaded");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);

        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setPreferredSize(new Dimension(300, 400));

        add(new JLabel("  Package Hierarchy", SwingConstants.LEFT), BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Populate the tree from a scanned PackageNode hierarchy.
     */
    public void loadTree(PackageNode root) {
        rootNode.removeAllChildren();
        rootNode.setUserObject(root);
        buildTreeNodes(rootNode, root);
        treeModel.reload();
        expandFirstLevel();
    }

    /**
     * Get the currently selected PackageNode, or null if root/nothing selected.
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
     * Get the qualified path of the selected package, or empty string for root.
     */
    public String getSelectedPath() {
        PackageNode selected = getSelectedPackage();
        return selected != null ? selected.qualifiedPath() : "";
    }

    public JTree getTree() {
        return tree;
    }

    private void buildTreeNodes(DefaultMutableTreeNode parentTreeNode, PackageNode parentPkg) {
        for (PackageNode child : parentPkg.children()) {
            DefaultMutableTreeNode childTreeNode = new DefaultMutableTreeNode(child);
            parentTreeNode.add(childTreeNode);
            buildTreeNodes(childTreeNode, child);
        }
    }

    private void expandFirstLevel() {
        for (int i = 0; i < tree.getRowCount() && i < 20; i++) {
            tree.expandRow(i);
        }
    }
}

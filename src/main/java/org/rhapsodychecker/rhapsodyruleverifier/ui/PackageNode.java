// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/ui/PackageNode.java
package org.rhapsodychecker.rhapsodyruleverifier.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lightweight node representing a package in the model hierarchy.
 * Used to populate the JTree without loading full model elements.
 */
public final class PackageNode {
    private final String guid;
    private final String name;
    private final String qualifiedPath;
    private final List<PackageNode> children;

    public PackageNode(String guid, String name, String qualifiedPath) {
        this.guid = guid;
        this.name = name;
        this.qualifiedPath = qualifiedPath;
        this.children = new ArrayList<>();
    }

    public String guid() { return guid; }
    public String name() { return name; }
    public String qualifiedPath() { return qualifiedPath; }
    public List<PackageNode> children() { return Collections.unmodifiableList(children); }

    public void addChild(PackageNode child) {
        children.add(child);
    }

    @Override
    public String toString() {
        return name;
    }
}

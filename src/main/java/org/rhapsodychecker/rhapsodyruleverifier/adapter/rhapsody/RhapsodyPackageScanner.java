
package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody;

import com.telelogic.rhapsody.core.*;
import org.rhapsodychecker.rhapsodyruleverifier.ui.PackageNode;

/**
 * Scans only the package hierarchy of a Rhapsody project.
 * Lightweight — does not load elements, stereotypes, or tags.
 * Used to populate the JTree for scope selection.
 */
public final class RhapsodyPackageScanner {

    public RhapsodyPackageScanner() {}

    /**
     * Build a package tree rooted at the project.
     */
    public PackageNode scanPackages(IRPProject project) {
        String projectName = "";
        String projectGuid = "";
        try {
            projectName = project.getName();
            projectGuid = project.getGUID();
        } catch (Throwable t) {
            projectName = "Project";
            projectGuid = "";
        }

        PackageNode root = new PackageNode(projectGuid, projectName, "");
        scanChildren(project, root, "");
        return root;
    }

    private void scanChildren(IRPModelElement parent, PackageNode parentNode, String parentPath) {
        try {
            IRPCollection nested = parent.getNestedElements();
            if (nested == null) return;
            int count = nested.getCount();
            for (int i = 1; i <= count; i++) {
                Object o = nested.getItem(i);
                if (!(o instanceof IRPModelElement)) continue;
                IRPModelElement elt = (IRPModelElement) o;

                String metaClass = "";
                try { metaClass = elt.getMetaClass(); } catch (Throwable t) { continue; }

                // Only include packages in the tree
                if (!"Package".equals(metaClass)) continue;

                String name = "";
                String guid = "";
                try {
                    name = elt.getName();
                    guid = elt.getGUID();
                } catch (Throwable t) { continue; }

                if (name == null || name.trim().isEmpty()) continue;

                String qualifiedPath = parentPath.isEmpty() ? name : parentPath + "::" + name;
                PackageNode childNode = new PackageNode(guid, name, qualifiedPath);
                parentNode.addChild(childNode);

                // Recurse into sub-packages
                scanChildren(elt, childNode, qualifiedPath);
            }
        } catch (Throwable t) {
            // ignore; partial tree is fine
        }
    }
}

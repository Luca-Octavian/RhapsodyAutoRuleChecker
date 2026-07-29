// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/service/NavigationService.java
package org.rhapsodychecker.rhapsodyruleverifier.core.service;

import com.telelogic.rhapsody.core.IRPModelElement;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCacheManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.FeaturesTab;

import java.awt.Desktop;
import java.io.File;
import java.lang.reflect.Method;

/**
 * Business logic for element navigation and cache folder access.
 */
public final class NavigationService {

    private NavigationService() {}

    /**
     * Navigate to an element in Rhapsody's browser and open its Features dialog
     * on the General tab.
     *
     * @return status message, or null if no action taken
     */
    public static String navigateToElement(String guid,
                                            RhapsodyModelSnapshot snapshot,
                                            boolean loadedFromCache) {
        return navigateToElement(guid, snapshot, loadedFromCache, FeaturesTab.GENERAL);
    }

    /**
     * Navigate to an element in Rhapsody's browser and open its Features dialog
     * on the specified tab.
     *
     * @param tab the Features dialog tab to open (e.g. GENERAL, DESCRIPTION, TAGS)
     * @return status message, or null if no action taken
     */
    public static String navigateToElement(String guid,
                                            RhapsodyModelSnapshot snapshot,
                                            boolean loadedFromCache,
                                            FeaturesTab tab) {
        if (snapshot == null || guid == null || guid.isEmpty()) return null;

        // Try live handle first (available when loaded from Rhapsody)
        IRPModelElement elt = snapshot.handleByGuid().get(guid);

        // If no handle (cache mode or incremental update), try to find via Rhapsody API
        if (elt == null) {
            RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();
            if (conn.isConnected()) {
                try {
                    elt = conn.getProject().findElementByGUID(guid);
                } catch (Throwable t) { /* ignore */ }
            }
        }

        if (elt == null) {
            if (loadedFromCache) {
                return "  Navigation requires Rhapsody connection. Use 'Update Model' first.";
            }
            return "  Element not found in model: " + guid;
        }

        try {
            elt.locateInBrowser();

            // Open Features dialog on the requested tab
            openFeaturesDialog(elt, tab != null ? tab : FeaturesTab.GENERAL);

            return "  Navigated to: " + elt.getName();
        } catch (Throwable t1) {
            try {
                RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();
                conn.getApplication().highLightElement(elt);

                // Try opening Features dialog even with highlight fallback
                openFeaturesDialog(elt, tab != null ? tab : FeaturesTab.GENERAL);

                return "  Highlighted: " + elt.getName();
            } catch (Throwable t2) {
                // COM handles are stale — Rhapsody was likely closed after model load
                return "  Navigation requires Rhapsody connection. Use 'Update Model' first.";
            }
        }
    }

    /**
     * Opens the Rhapsody Features (properties) dialog for the given element
     * on the specified tab.
     *
     * Uses reflection because {@code openFeaturesDialog(int)} may not be
     * available in all Rhapsody API versions. Falls back silently if the
     * method is not found — navigation still works, just without the dialog.
     */
    private static void openFeaturesDialog(IRPModelElement elt, FeaturesTab tab) {
        try {
            Method m = elt.getClass().getMethod("openFeaturesDialog", int.class);
            m.invoke(elt, tab.index());
        } catch (NoSuchMethodException nsme) {
            // openFeaturesDialog not available in this Rhapsody version,
            // try the alternative openSpecificationsDialog (no tab control)
            try {
                Method fallback = elt.getClass().getMethod("openSpecificationsDialog");
                fallback.invoke(elt);
            } catch (Throwable ignored) {
                // Neither method available — silently skip (navigation still works)
            }
        } catch (Throwable ignored) {
            // Any other reflection error — silently skip
        }
    }

    /**
     * Open the cache folder in the system file manager.
     *
     * @return status message, or null if successful
     */
    public static String openCacheFolder(String modelPath) {
        if (modelPath == null || modelPath.isEmpty()) return "  No model path set";

        File cacheFile = ModelCacheManager.defaultCacheFile(modelPath);
        File cacheDir = cacheFile.getParentFile();

        if (cacheDir == null || !cacheDir.exists()) return "  No cache folder found";

        try {
            Desktop.getDesktop().open(cacheDir);
            return null;
        } catch (Throwable t) {
            try {
                Runtime.getRuntime().exec("explorer.exe " + cacheDir.getAbsolutePath());
                return null;
            } catch (Throwable t2) {
                return "  Could not open cache folder";
            }
        }
    }
}
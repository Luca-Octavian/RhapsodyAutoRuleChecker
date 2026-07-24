// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/service/NavigationService.java
package org.rhapsodychecker.rhapsodyruleverifier.core.service;

import com.telelogic.rhapsody.core.IRPModelElement;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCacheManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;

import java.awt.Desktop;
import java.io.File;

/**
 * Business logic for element navigation and cache folder access.
 */
public final class NavigationService {

    private NavigationService() {}

    /**
     * Navigate to an element in Rhapsody's browser.
     *
     * @return status message, or null if no action taken
     */
    public static String navigateToElement(String guid,
                                            RhapsodyModelSnapshot snapshot,
                                            boolean loadedFromCache) {
        if (snapshot == null || guid == null || guid.isEmpty()) return null;

        if (loadedFromCache) {
            return "  Navigation requires live Rhapsody connection (loaded from cache)";
        }

        IRPModelElement elt = snapshot.handleByGuid().get(guid);
        if (elt == null) {
            return "  Element not found in model: " + guid;
        }

        try {
            elt.locateInBrowser();
            return "  Navigated to: " + elt.getName();
        } catch (Throwable t1) {
            try {
                RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();
                conn.getApplication().highLightElement(elt);
                return "  Highlighted: " + elt.getName();
            } catch (Throwable t2) {
                return "  Could not navigate to element (is Rhapsody open?)";
            }
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
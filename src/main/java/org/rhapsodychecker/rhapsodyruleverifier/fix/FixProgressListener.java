package org.rhapsodychecker.rhapsodyruleverifier.fix;

/**
 * Callback for fix-application progress. Called after each entry is processed.
 */
public interface FixProgressListener {

    /**
     * Called after each entry is processed.
     *
     * @param current     1-based index of the entry just processed
     * @param total       total number of entries in the plan
     * @param elementName display name of the element just processed
     * @return {@code true} to continue, {@code false} to cancel remaining entries
     */
    boolean onProgress(int current, int total, String elementName);
}
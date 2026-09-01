// prefs/RecentFilesStore.java
package org.rhapsodychecker.rhapsodyruleverifier.prefs;

import java.util.*;
import java.util.prefs.Preferences;

/**
 * Stores the last N paths used (models .rpyx and configs .yaml),
 * per current user, without requiring administrator privileges.
 *
 * IMPORTANT: uses userNodeForPackage (HKEY_CURRENT_USER on Windows),
 * NOT systemNodeForPackage (HKEY_LOCAL_MACHINE, would require admin).
 */
public final class RecentFilesStore {

    private static final int MAX_ENTRIES = 5;

    private static final String KEY_RECENT_MODELS  = "recentModels";
    private static final String KEY_RECENT_CONFIGS = "recentConfigs";
    private static final String KEY_LAST_MODEL_TS   = "lastModelTimestamp_";

    private static final String ENTRY_SEPARATOR = "\u001F"; // control character, safe as separator

    private final Preferences prefs;

    public RecentFilesStore() {
        // userNodeForPackage = per current user, no admin required
        this.prefs = Preferences.userNodeForPackage(RecentFilesStore.class);
    }

    public List<String> recentModels() {
        return readList(KEY_RECENT_MODELS);
    }

    public void addRecentModel(String path) {
        addToList(KEY_RECENT_MODELS, path);
    }

    public Optional<String> lastModel() {
        List<String> list = recentModels();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<String> recentConfigs() {
        return readList(KEY_RECENT_CONFIGS);
    }

    public void addRecentConfig(String path) {
        addToList(KEY_RECENT_CONFIGS, path);
    }

    public Optional<String> lastConfig() {
        List<String> list = recentConfigs();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }


    /**
     * Saves the lastModified timestamp of the .rpyx file at the time of the last successful scan.
     * Used later to decide whether a full reconnect/rescan is needed, or if the file is unchanged.
     */
    public void recordModelTimestamp(String modelPath, long lastModifiedAtScanTime) {
        prefs.putLong(KEY_LAST_MODEL_TS + hashKey(modelPath), lastModifiedAtScanTime);
    }

    public Optional<Long> getRecordedModelTimestamp(String modelPath) {
        long v = prefs.getLong(KEY_LAST_MODEL_TS + hashKey(modelPath), -1L);
        return v < 0 ? Optional.empty() : Optional.of(v);
    }

    private List<String> readList(String key) {
        String raw = prefs.get(key, "");
        if (raw.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(Arrays.asList(raw.split(ENTRY_SEPARATOR)));
    }

    private void addToList(String key, String path) {
        if (path == null || path.trim().isEmpty()) return;

        List<String> current = new ArrayList<>(readList(key));
        // Remove duplicate if it already exists (regardless of position), then put it first
        current.removeIf(existing -> existing.equalsIgnoreCase(path));
        current.add(0, path);

        // Keep only the first MAX_ENTRIES
        List<String> trimmed = current.size() > MAX_ENTRIES
                ? current.subList(0, MAX_ENTRIES)
                : current;

        prefs.put(key, String.join(ENTRY_SEPARATOR, trimmed));
    }

    /** Stable and short key for Preferences, derived from the full path. */
    private static String hashKey(String path) {
        return Integer.toHexString(path.hashCode());
    }
}
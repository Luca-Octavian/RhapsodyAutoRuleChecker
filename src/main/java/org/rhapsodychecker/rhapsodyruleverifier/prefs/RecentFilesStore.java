// prefs/RecentFilesStore.java
package org.rhapsodychecker.rhapsodyruleverifier.prefs;

import java.util.*;
import java.util.prefs.Preferences;

/**
 * Retine ultimele N cai folosite (modele .rpyx si config-uri .yaml),
 * per utilizator curent, fara nevoie de drepturi de administrator.
 *
 * IMPORTANT: foloseste userNodeForPackage (HKEY_CURRENT_USER pe Windows),
 * NU systemNodeForPackage (HKEY_LOCAL_MACHINE, ar necesita admin).
 */
public final class RecentFilesStore {

    private static final int MAX_ENTRIES = 5;

    private static final String KEY_RECENT_MODELS  = "recentModels";
    private static final String KEY_RECENT_CONFIGS = "recentConfigs";
    private static final String KEY_LAST_MODEL_TS   = "lastModelTimestamp_";

    private static final String ENTRY_SEPARATOR = "\u001F"; // caracter de control, sigur ca separator

    private final Preferences prefs;

    public RecentFilesStore() {
        // userNodeForPackage = per-utilizator curent, fara admin necesar
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
     * Salveaza lastModified-ul fisierului .rpyx la momentul ultimei scanari reusite.
     * Folosit ulterior de ModelFreshnessChecker pentru a decide daca mai e nevoie
     * de o reconectare/rescanare completa, sau daca fisierul e neschimbat.
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
        // Elimina duplicat daca exista deja (indiferent de pozitie), apoi il pune primul
        current.removeIf(existing -> existing.equalsIgnoreCase(path));
        current.add(0, path);

        // Pastreaza doar primele MAX_ENTRIES
        List<String> trimmed = current.size() > MAX_ENTRIES
                ? current.subList(0, MAX_ENTRIES)
                : current;

        prefs.put(key, String.join(ENTRY_SEPARATOR, trimmed));
    }

    /** Cheie stabila si scurta pentru Preferences, derivata din calea completa. */
    private static String hashKey(String path) {
        return Integer.toHexString(path.hashCode());
    }
}
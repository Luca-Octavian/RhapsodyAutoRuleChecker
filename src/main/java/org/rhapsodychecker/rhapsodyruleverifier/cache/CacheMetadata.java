// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/cache/CacheMetadata.java
package org.rhapsodychecker.rhapsodyruleverifier.cache;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Metadata about a cached model snapshot.
 * Stored alongside element data so the cache can be identified and version-checked.
 *
 * <p>Unknown properties are ignored on read so that a cache written by a newer
 * build with extra metadata fields still fails on the explicit version check
 * (a clear, actionable error) rather than on an opaque Jackson binding error.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CacheMetadata {

    /**
     * Bump when the persisted format changes.
     *
     * <p>v4: added canonicalModelPath, saveUnitTimestamps, snapshotComplete.
     * <p>v5: ElementKind CONNECTOR was split into CONNECTOR (assembly
     *       connector / IRPLink) and STATE_CONNECTOR (statechart pseudostate /
     *       IRPConnector). A v4 cache stores statechart connectors under
     *       CONNECTOR, so it must be rebuilt rather than reused — otherwise the
     *       old, incorrect connector counts would survive the fix.
     * <p>v6: CONNECTOR redefined to match Rhapsody's Ctrl+F "Connector"
     *       category — only IRPConnector with getConnectorType()=="Junction".
     *       IRPLink moved to new LINK kind. A v5 cache assigns links to
     *       CONNECTOR, so a rebuild is required.
     */
    public static final int CURRENT_VERSION = 6;

    private String projectName;
    private String projectGuid;
    private String cachedAt;    // ISO-8601 timestamp
    private int elementCount;
    private int cacheVersion;   // bump when format changes

    /**
     * Canonical (normalized absolute) path of the model this cache was built
     * from. Used to reject a cache that belongs to a different project — the
     * old filename-only identity could not tell two projects named
     * {@code System.rpy} in different folders apart.
     */
    private String canonicalModelPath;

    /**
     * File timestamps captured at cache-write time.
     * Key = absolute file path, Value = lastModified millis.
     * Includes the .rpy file and any discovered unit (.sbs) files.
     * Used for fast-skip: if no file has changed, the cache is fresh.
     */
    private Map<String, Long> fileTimestamps;

    /**
     * Rhapsody save-unit modification markers captured at cache-write time.
     * Key = stable unit path (from {@code IRPUnit.getUnitPath(1)}),
     * Value = {@code IRPUnit.getLastModifiedTime()} as reported by Rhapsody.
     *
     * <p>This is Rhapsody's own notion of "which units changed", which is far
     * cheaper to compare than walking every element. Empty/absent means the
     * markers were unavailable and the caller must fall back to a full scan.
     */
    private Map<String, String> saveUnitTimestamps;

    /**
     * False when the snapshot that produced this cache was known to be
     * incomplete (e.g. OSLC proxies missing, removals deferred). A cache marked
     * incomplete must never be presented as an authoritative model state.
     */
    private boolean snapshotComplete = true;

    /** Jackson needs no-arg constructor. */
    public CacheMetadata() {}

    public CacheMetadata(String projectName, String projectGuid,
                         String cachedAt, int elementCount) {
        this.projectName = projectName;
        this.projectGuid = projectGuid;
        this.cachedAt = cachedAt;
        this.elementCount = elementCount;
        this.cacheVersion = CURRENT_VERSION;
    }

    public String getProjectName() { return projectName; }
    public void setProjectName(String v) { this.projectName = v; }

    public String getProjectGuid() { return projectGuid; }
    public void setProjectGuid(String v) { this.projectGuid = v; }

    public String getCachedAt() { return cachedAt; }
    public void setCachedAt(String v) { this.cachedAt = v; }

    public int getElementCount() { return elementCount; }
    public void setElementCount(int v) { this.elementCount = v; }

    public int getCacheVersion() { return cacheVersion; }
    public void setCacheVersion(int v) { this.cacheVersion = v; }

    public Map<String, Long> getFileTimestamps() { return fileTimestamps; }
    public void setFileTimestamps(Map<String, Long> v) { this.fileTimestamps = v; }

    public String getCanonicalModelPath() { return canonicalModelPath; }
    public void setCanonicalModelPath(String v) { this.canonicalModelPath = v; }

    public Map<String, String> getSaveUnitTimestamps() { return saveUnitTimestamps; }
    public void setSaveUnitTimestamps(Map<String, String> v) { this.saveUnitTimestamps = v; }

    public boolean isSnapshotComplete() { return snapshotComplete; }
    public void setSnapshotComplete(boolean v) { this.snapshotComplete = v; }

    /** True when save-unit markers are present and therefore comparable. */
    public boolean hasSaveUnitMarkers() {
        return saveUnitTimestamps != null && !saveUnitTimestamps.isEmpty();
    }
}
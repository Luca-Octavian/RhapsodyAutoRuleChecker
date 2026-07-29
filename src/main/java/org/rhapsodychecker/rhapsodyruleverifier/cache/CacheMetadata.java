// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/cache/CacheMetadata.java
package org.rhapsodychecker.rhapsodyruleverifier.cache;

import java.util.Map;

/**
 * Metadata about a cached model snapshot.
 * Stored alongside element data so the cache can be identified and version-checked.
 */
public final class CacheMetadata {

    public static final int CURRENT_VERSION = 2;

    private String projectName;
    private String projectGuid;
    private String cachedAt;    // ISO-8601 timestamp
    private int elementCount;
    private int cacheVersion;   // bump when format changes

    /**
     * File timestamps captured at cache-write time.
     * Key = absolute file path, Value = lastModified millis.
     * Includes the .rpy file and any discovered unit (.sbs) files.
     * Used for fast-skip: if no file has changed, the cache is fresh.
     */
    private Map<String, Long> fileTimestamps;

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
}
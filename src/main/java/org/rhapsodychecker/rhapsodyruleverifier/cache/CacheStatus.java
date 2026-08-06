// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/cache/CacheStatus.java
package org.rhapsodychecker.rhapsodyruleverifier.cache;

/**
 * Provenance / trust level of the model snapshot that was handed to the rest of
 * the pipeline.
 *
 * <p>This deliberately separates two questions that used to be collapsed into a
 * single {@code boolean fromCache}:
 * <ol>
 *   <li><b>Where did the data come from?</b> (cache vs live Rhapsody read)</li>
 *   <li><b>Was its freshness actually verified against a live project?</b></li>
 * </ol>
 *
 * <p>The distinction matters because loading a cache <i>without Rhapsody open at
 * all</i> is a fully supported workflow — but the results must not be presented
 * as if they had been confirmed against the live model. A cached snapshot that
 * nobody verified is usable; it is simply "as of &lt;cachedAt&gt;".
 *
 * <p>The {@code CACHE_*} rejection values are never silently swallowed: they
 * exist so the UI can say <i>why</i> a cache was not used instead of behaving as
 * if no cache had been found.
 */
public enum CacheStatus {

    // ── Usable snapshots ────────────────────────────────────────────────────

    /**
     * Cache was read successfully, but no live Rhapsody project was consulted.
     * Structurally valid; freshness unknown. This is the normal "user pressed
     * Load from Cache while Rhapsody is closed" path.
     */
    OFFLINE_CACHE_UNVERIFIED(true, false, false),

    /**
     * A live project was available and {@code isModifiedRecursive() == 0},
     * i.e. Rhapsody itself reports no unsaved modifications. The cache was
     * reused without any model traversal.
     */
    LIVE_CACHE_CONFIRMED_CLEAN(true, true, false),

    /**
     * A live project was available and reported modifications, but every
     * persisted save-unit marker still matches the cache, so no element scan
     * was required.
     */
    LIVE_UNITS_UNCHANGED(true, true, false),

    /** Live incremental scan ran and produced a complete, merged snapshot. */
    LIVE_INCREMENTAL_REFRESHED(true, true, false),

    /**
     * Live incremental scan ran but was detected as incomplete (typically
     * missing OSLC proxies), so removals were deferred and cached data was
     * retained. Results may contain stale relations.
     */
    LIVE_INCREMENTAL_PARTIAL(true, true, true),

    /** Full read straight from Rhapsody; the cache was (re)built from it. */
    LIVE_FULL_LOAD(false, true, false),

    // ── Rejections (cache not usable) ───────────────────────────────────────

    /** No cache file exists for this model. */
    CACHE_ABSENT(false, false, false),

    /** Cache exists but was written by an incompatible schema version. */
    CACHE_INVALID_VERSION(false, false, false),

    /** Cache exists but belongs to a different model / project. */
    CACHE_INVALID_IDENTITY(false, false, false),

    /** Cache exists but could not be parsed (truncated, corrupted, unreadable). */
    CACHE_CORRUPT(false, false, false);

    private final boolean fromCache;
    private final boolean freshnessVerified;
    private final boolean partial;

    CacheStatus(boolean fromCache, boolean freshnessVerified, boolean partial) {
        this.fromCache = fromCache;
        this.freshnessVerified = freshnessVerified;
        this.partial = partial;
    }

    /** True when the snapshot content originated (wholly or partly) from cache. */
    public boolean isFromCache() { return fromCache; }

    /** True when a live Rhapsody project was consulted to establish freshness. */
    public boolean isFreshnessVerified() { return freshnessVerified; }

    /** True when the snapshot is known to be incomplete and may hold stale data. */
    public boolean isPartial() { return partial; }

    /** True when this value means "the cache could not be used". */
    public boolean isRejection() {
        return this == CACHE_ABSENT
                || this == CACHE_INVALID_VERSION
                || this == CACHE_INVALID_IDENTITY
                || this == CACHE_CORRUPT;
    }

    /**
     * Short human-readable provenance note, suitable for a status bar or the
     * header of an exported report.
     */
    public String describe() {
        switch (this) {
            case OFFLINE_CACHE_UNVERIFIED:
                return "offline cache snapshot \u2014 live freshness not verified";
            case LIVE_CACHE_CONFIRMED_CLEAN:
                return "cache reused \u2014 Rhapsody reports no unsaved changes";
            case LIVE_UNITS_UNCHANGED:
                return "cache reused \u2014 no save unit changed";
            case LIVE_INCREMENTAL_REFRESHED:
                return "refreshed against live model";
            case LIVE_INCREMENTAL_PARTIAL:
                return "refreshed, but scan was incomplete \u2014 some cached data retained";
            case LIVE_FULL_LOAD:
                return "full read from Rhapsody";
            case CACHE_ABSENT:
                return "no cache found";
            case CACHE_INVALID_VERSION:
                return "cache rejected \u2014 incompatible cache format";
            case CACHE_INVALID_IDENTITY:
                return "cache rejected \u2014 belongs to a different model";
            case CACHE_CORRUPT:
                return "cache rejected \u2014 unreadable or corrupted";
            default:
                return name();
        }
    }
}
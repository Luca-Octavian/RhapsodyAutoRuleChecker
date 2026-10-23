package org.rhapsodychecker.rhapsodyruleverifier.cache;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests CacheStatus enum properties and fix-eligibility logic.
 */
public class CacheStatusTest {

    // ── Fix eligibility (mirrors ModelLoadController.isFixEligible) ──────

    @Test
    public void liveFullLoad_isFixEligible() {
        CacheStatus s = CacheStatus.LIVE_FULL_LOAD;
        assertTrue(s.isFreshnessVerified());
        assertFalse(s.isPartial());
        // Fix eligible = freshnessVerified && !partial
        assertTrue(s.isFreshnessVerified() && !s.isPartial());
    }

    @Test
    public void liveIncrementalRefreshed_isFixEligible() {
        CacheStatus s = CacheStatus.LIVE_INCREMENTAL_REFRESHED;
        assertTrue(s.isFreshnessVerified());
        assertFalse(s.isPartial());
        assertTrue(s.isFreshnessVerified() && !s.isPartial());
    }

    @Test
    public void liveCacheConfirmedClean_isFixEligible() {
        CacheStatus s = CacheStatus.LIVE_CACHE_CONFIRMED_CLEAN;
        assertTrue(s.isFreshnessVerified());
        assertFalse(s.isPartial());
        assertTrue(s.isFreshnessVerified() && !s.isPartial());
    }

    @Test
    public void liveUnitsUnchanged_isFixEligible() {
        CacheStatus s = CacheStatus.LIVE_UNITS_UNCHANGED;
        assertTrue(s.isFreshnessVerified());
        assertFalse(s.isPartial());
        assertTrue(s.isFreshnessVerified() && !s.isPartial());
    }

    @Test
    public void liveIncrementalPartial_isNotFixEligible() {
        CacheStatus s = CacheStatus.LIVE_INCREMENTAL_PARTIAL;
        assertTrue(s.isFreshnessVerified());
        assertTrue(s.isPartial());
        // Partial means NOT fix eligible
        assertFalse(s.isFreshnessVerified() && !s.isPartial());
    }

    @Test
    public void offlineCacheUnverified_isNotFixEligible() {
        CacheStatus s = CacheStatus.OFFLINE_CACHE_UNVERIFIED;
        assertFalse(s.isFreshnessVerified());
        assertFalse(s.isFreshnessVerified() && !s.isPartial());
    }

    // ── fromCache property ──────────────────────────────────────────────

    @Test
    public void liveFullLoad_isNotFromCache() {
        assertFalse(CacheStatus.LIVE_FULL_LOAD.isFromCache());
    }

    @Test
    public void offlineCache_isFromCache() {
        assertTrue(CacheStatus.OFFLINE_CACHE_UNVERIFIED.isFromCache());
    }

    @Test
    public void liveIncrementalPartial_isFromCache() {
        assertTrue(CacheStatus.LIVE_INCREMENTAL_PARTIAL.isFromCache());
    }

    // ── Rejections ──────────────────────────────────────────────────────

    @Test
    public void rejections_areNotFromCache() {
        assertFalse(CacheStatus.CACHE_ABSENT.isFromCache());
        assertFalse(CacheStatus.CACHE_INVALID_VERSION.isFromCache());
        assertFalse(CacheStatus.CACHE_INVALID_IDENTITY.isFromCache());
        assertFalse(CacheStatus.CACHE_CORRUPT.isFromCache());
    }

    @Test
    public void rejections_haveIsRejectionTrue() {
        assertTrue(CacheStatus.CACHE_ABSENT.isRejection());
        assertTrue(CacheStatus.CACHE_INVALID_VERSION.isRejection());
        assertTrue(CacheStatus.CACHE_INVALID_IDENTITY.isRejection());
        assertTrue(CacheStatus.CACHE_CORRUPT.isRejection());
    }

    @Test
    public void usableStatuses_areNotRejections() {
        assertFalse(CacheStatus.OFFLINE_CACHE_UNVERIFIED.isRejection());
        assertFalse(CacheStatus.LIVE_FULL_LOAD.isRejection());
        assertFalse(CacheStatus.LIVE_INCREMENTAL_REFRESHED.isRejection());
        assertFalse(CacheStatus.LIVE_INCREMENTAL_PARTIAL.isRejection());
        assertFalse(CacheStatus.LIVE_CACHE_CONFIRMED_CLEAN.isRejection());
        assertFalse(CacheStatus.LIVE_UNITS_UNCHANGED.isRejection());
    }

    @Test
    public void describe_returnsNonEmptyString() {
        for (CacheStatus s : CacheStatus.values()) {
            assertNotNull(s.describe());
            assertFalse(s.describe().isEmpty());
        }
    }
}
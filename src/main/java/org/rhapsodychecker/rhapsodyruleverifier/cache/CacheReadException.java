// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/cache/CacheReadException.java
package org.rhapsodychecker.rhapsodyruleverifier.cache;

import java.io.IOException;

/**
 * Thrown when a cache file exists but cannot be used.
 *
 * <p>Extends {@link IOException} so it slots into the existing
 * {@code throws IOException} signatures, but carries a {@link CacheStatus} so
 * callers can distinguish "corrupt" from "wrong project" from "old format" and
 * tell the user <i>why</i> the cache was rejected instead of silently behaving
 * as if no cache existed.
 */
@SuppressWarnings("serial")
public final class CacheReadException extends IOException {

    private final CacheStatus status;

    public CacheReadException(CacheStatus status, String message) {
        super(message);
        this.status = status;
    }

    public CacheReadException(CacheStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /** The reason the cache was rejected. Always one of the {@code CACHE_*} values. */
    public CacheStatus status() {
        return status;
    }
}
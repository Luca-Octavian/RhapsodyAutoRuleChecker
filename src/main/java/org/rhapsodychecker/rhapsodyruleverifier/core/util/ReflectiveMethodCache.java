// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/util/ReflectiveMethodCache.java
package org.rhapsodychecker.rhapsodyruleverifier.core.util;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches Method lookups for the small number of Rhapsody COM-bridge methods
 * that are not declared on any compiled com.telelogic.rhapsody.core interface
 * and therefore must be probed reflectively. Resolving a method (or learning
 * it doesn't exist) happens at most once per concrete class, not once per
 * element — this avoids both the repeated Class.getMethod() walk and, for
 * methods that genuinely don't exist, the repeated cost of a freshly thrown
 * NoSuchMethodException (which fills in a full stack trace every time).
 *
 * Prefer a direct interface cast wherever the javadoc confirms the method
 * exists — reserve this cache for the genuine reflection-only edge cases.
 */
public final class ReflectiveMethodCache {

    private static final Method NOT_FOUND = sentinel();
    private static final ConcurrentHashMap<String, Method> CACHE = new ConcurrentHashMap<String, Method>();

    private ReflectiveMethodCache() {}

    /** Returns the cached no-arg public Method named methodName on type, or null if it doesn't exist. */
    public static Method lookup(Class<?> type, String methodName) {
        String key = type.getName() + '#' + methodName;
        Method cached = CACHE.get(key);
        if (cached != null) {
            return cached == NOT_FOUND ? null : cached;
        }
        Method resolved;
        try {
            resolved = type.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            resolved = null;
        }
        Method toStore = resolved == null ? NOT_FOUND : resolved;
        Method winner = CACHE.putIfAbsent(key, toStore);
        Method effective = winner != null ? winner : toStore;
        return effective == NOT_FOUND ? null : effective;
    }

    /** Looks up and invokes a no-arg method, returning null on any failure. */
    public static Object invokeOrNull(Object target, String methodName) {
        if (target == null) return null;
        Method m = lookup(target.getClass(), methodName);
        if (m == null) return null;
        try {
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method sentinel() {
        try {
            return Object.class.getMethod("hashCode");
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }
}
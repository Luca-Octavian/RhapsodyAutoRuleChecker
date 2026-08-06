// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/adapter/rhapsody/RhapsodyFreshnessProbe.java
package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody;

import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.IRPUnit;
import org.rhapsodychecker.rhapsodyruleverifier.core.AppLogger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Asks Rhapsody itself whether anything changed, instead of walking every element
 * to find out.
 *
 * <p>Two signals are used, cheapest first:
 * <ol>
 *   <li>{@code IRPProject.isModifiedRecursive()} — O(1). Rhapsody's own answer to
 *       "does this project have unsaved modifications?". When it says no, the
 *       in-memory model equals what was last saved, so a cache built from that
 *       save is still valid and no scan is needed at all.</li>
 *   <li>{@code IRPUnit.getLastModifiedTime()} over the save-unit tree — O(number
 *       of units), which is typically orders of magnitude smaller than the
 *       element count. Persisting these markers lets a later run (even after
 *       closing and reopening Rhapsody) tell which units moved.</li>
 * </ol>
 *
 * <p>Every call is defensively guarded: these APIs are not uniformly available
 * across Rhapsody versions and project types. When a signal cannot be obtained
 * the probe reports {@code UNKNOWN} / empty rather than guessing, and the caller
 * falls back to the existing full incremental scan. <b>Absence of evidence is
 * never treated as evidence of freshness.</b>
 */
public final class RhapsodyFreshnessProbe {

    /** Result of the project-level modification query. */
    public enum ProjectState {
        /** Rhapsody reports no unsaved modifications. */
        CLEAN,
        /** Rhapsody reports unsaved modifications somewhere in the project. */
        MODIFIED,
        /** The query failed or is unsupported — treat as "must scan". */
        UNKNOWN
    }

    /** Safety valve for pathological/cyclic unit graphs. */
    private static final int MAX_UNITS = 20000;

    private RhapsodyFreshnessProbe() {}

    /**
     * O(1) check: does Rhapsody consider the open project modified?
     *
     * <p>Only meaningful for the <i>currently connected session</i>. It says
     * nothing about whether the cache matches a project that was edited, saved
     * and reopened — that is what the save-unit markers are for.
     */
    public static ProjectState projectState(IRPProject project) {
        if (project == null) return ProjectState.UNKNOWN;
        try {
            int modified = project.isModifiedRecursive();
            return modified == 0 ? ProjectState.CLEAN : ProjectState.MODIFIED;
        } catch (Throwable t) {
            AppLogger.debug("isModifiedRecursive() unavailable: " + t);
            return ProjectState.UNKNOWN;
        }
    }

    /**
     * Walk the save-unit tree and capture each unit's last-modified marker.
     *
     * <p>Key = stable unit path ({@code getUnitPath(1)}, falling back to the unit's
     * full path name), value = {@code getLastModifiedTime()}.
     *
     * @return marker map, or an <b>empty</b> map if the unit tree could not be read.
     *         Empty must be interpreted as "unknown", never as "nothing changed".
     */
    public static Map<String, String> captureSaveUnitMarkers(IRPProject project) {
        Map<String, String> markers = new LinkedHashMap<String, String>();
        if (project == null) return markers;

        try {
            IRPUnit rootUnit = asUnit(project);
            if (rootUnit == null) {
                AppLogger.debug("Project exposes no save unit; unit markers unavailable");
                return markers;
            }

            Deque<IRPUnit> queue = new ArrayDeque<IRPUnit>();
            Set<String> visited = new HashSet<String>();
            queue.push(rootUnit);

            while (!queue.isEmpty() && markers.size() < MAX_UNITS) {
                IRPUnit unit = queue.pop();
                if (unit == null) continue;

                String key = unitKey(unit);
                if (key == null || key.isEmpty()) continue;
                if (!visited.add(key)) continue; // guards against cycles / shared references

                String stamp = lastModified(unit);
                if (stamp != null) {
                    markers.put(key, stamp);
                }

                for (IRPUnit nested : nestedUnits(unit)) {
                    if (nested != null) queue.push(nested);
                }
            }

            if (markers.size() >= MAX_UNITS) {
                AppLogger.warn("Save-unit walk hit the " + MAX_UNITS
                        + " unit cap; treating unit markers as unavailable");
                return new LinkedHashMap<String, String>();
            }
        } catch (Throwable t) {
            AppLogger.debug("Save-unit marker capture failed: " + t);
            return new LinkedHashMap<String, String>();
        }

        return markers;
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private static IRPUnit asUnit(IRPProject project) {
        // A project is itself an IRPUnit in most versions; getSaveUnit() is the
        // portable way to obtain the owning unit handle.
        try {
            if (project instanceof IRPUnit) {
                return (IRPUnit) project;
            }
        } catch (Throwable ignored) { /* fall through */ }

        try {
            IRPModelElement asElement = project;
            IRPUnit saveUnit = asElement.getSaveUnit();
            return saveUnit;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String unitKey(IRPUnit unit) {
        // Prefer the on-disk unit path: stable across sessions and unaffected by
        // renames of unrelated siblings.
        try {
            String path = unit.getUnitPath(1);
            if (path != null && !path.trim().isEmpty()) {
                return path.trim();
            }
        } catch (Throwable ignored) { /* fall through */ }

        try {
            String full = unit.getFullPathName();
            if (full != null && !full.trim().isEmpty()) {
                return "model:" + full.trim();
            }
        } catch (Throwable ignored) { /* fall through */ }

        try {
            String guid = unit.getGUID();
            if (guid != null && !guid.trim().isEmpty()) {
                return "guid:" + guid.trim();
            }
        } catch (Throwable ignored) { /* give up */ }

        return null;
    }

    private static String lastModified(IRPUnit unit) {
        try {
            String stamp = unit.getLastModifiedTime();
            return stamp == null ? null : stamp.trim();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Iterable<IRPUnit> nestedUnits(IRPUnit unit) {
        java.util.List<IRPUnit> out = new java.util.ArrayList<IRPUnit>();
        try {
            IRPCollection nested = unit.getNestedSaveUnits();
            if (nested == null) return out;
            int count = nested.getCount();
            for (int i = 1; i <= count; i++) {
                try {
                    Object item = nested.getItem(i);
                    if (item instanceof IRPUnit) {
                        out.add((IRPUnit) item);
                    }
                } catch (Throwable ignored) {
                    // Skip this entry; a single bad handle must not abort the walk
                }
            }
        } catch (Throwable t) {
            AppLogger.debug("getNestedSaveUnits() failed for a unit: " + t);
        }
        return out;
    }
}
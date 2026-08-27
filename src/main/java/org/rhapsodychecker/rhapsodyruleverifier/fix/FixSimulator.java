package org.rhapsodychecker.rhapsodyruleverifier.fix;

import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.util.Objects;
import java.util.Optional;

/**
 * Dry-run validator for a {@link FixPlan}.
 * Checks each entry against the current {@link ElementIndex} snapshot
 * to detect conflicts before applying changes to Rhapsody.
 *
 * After simulation, each entry is marked either SIMULATED (safe) or CONFLICT.
 */
public final class FixSimulator {

    private final ElementIndex index;

    public FixSimulator(ElementIndex index) {
        this.index = Objects.requireNonNull(index, "index");
    }

    /**
     * Simulate all PENDING entries in the plan.
     * Returns the number of conflicts found.
     */
    public int simulate(FixPlan plan) {
        int conflicts = 0;
        for (FixEntry entry : plan.entries()) {
            if (entry.status() != FixStatus.PENDING) continue;
            String conflict = validateEntry(entry);
            if (conflict != null) {
                entry.markConflict(conflict);
                conflicts++;
            } else {
                entry.markSimulated();
            }
        }
        return conflicts;
    }

    /**
     * Validate a single entry. Returns null if valid, or a conflict reason string.
     */
    private String validateEntry(FixEntry entry) {
        FixAction action = entry.action();
        Optional<ElementRecord> opt = index.repository().get(action.elementGuid());

        if (!opt.isPresent()) {
            return "Element '" + action.elementName() + "' (GUID: " + action.elementGuid()
                    + ") no longer exists in the model";
        }

        ElementRecord record = opt.get();

        // Verify the old value still matches the snapshot
        switch (action.actionType()) {
            case SET_NAME:
                return checkOldValue("name", record.name(), action.oldValue());

            case SET_DESCRIPTION:
                String desc = record.description().orElse(null);
                return checkOldValue("description", desc, action.oldValue());

            case SET_TAG_VALUE:
                if (action.field() == null) {
                    return "SET_TAG_VALUE requires a field (tag name)";
                }
                String tagVal = record.tagValues().get(action.field());
                return checkOldValue("tag '" + action.field() + "'", tagVal, action.oldValue());

            case ADD_STEREOTYPE:
                if (action.newValue() == null || action.newValue().trim().isEmpty()) {
                    return null; // not yet filled in — leave PENDING
                }
                if (record.hasStereotypeIgnoreCase(action.newValue())) {
                    return "Stereotype '" + action.newValue() + "' already present on '"
                            + record.name() + "'";
                }
                return null;

            case REMOVE_STEREOTYPE:
                if (action.oldValue() == null && action.newValue() == null) {
                    return null; // not yet filled in — leave PENDING
                }
                String stereo = action.oldValue() != null ? action.oldValue() : action.newValue();
                if (!record.hasStereotypeIgnoreCase(stereo)) {
                    return "Stereotype '" + stereo + "' not found on '" + record.name() + "'";
                }
                return null;

            case SET_INITIAL_VALUE:
                String initVal = record.initialValue().orElse(null);
                return checkOldValue("initialValue", initVal, action.oldValue());

            default:
                return "Unknown action type: " + action.actionType();
        }
    }

    /**
     * Check that the current value in the snapshot matches the expected old value.
     * If oldValue in the action is null, we skip the check (the action didn't record it).
     */
    private static String checkOldValue(String fieldName, String actual, String expected) {
        if (expected == null) {
            // Action didn't record the old value — no conflict check possible
            return null;
        }
        String actualNorm = actual != null ? actual : "";
        String expectedNorm = expected;
        if (!actualNorm.equals(expectedNorm)) {
            return fieldName + " has changed since evaluation: expected '"
                    + expected + "' but found '" + actualNorm + "'";
        }
        return null;
    }
}
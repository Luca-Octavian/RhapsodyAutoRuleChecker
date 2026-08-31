package org.rhapsodychecker.rhapsodyruleverifier.ui.fix;

import org.rhapsodychecker.rhapsodyruleverifier.fix.FixAction;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixActionType;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixStatus;

/**
 * Shared user-facing presentation and actionability rules for the fix UI.
 * Keeps internal enum names out of controls while preserving the domain model.
 */
public final class FixUiSupport {

    private FixUiSupport() {
    }

    public static String statusLabel(FixStatus status) {
        if (status == null) return "";
        switch (status) {
            case PENDING:
                return "Pending";
            case SIMULATED:
                return "OK";
            case APPLIED:
                return "Applied";
            case FAILED:
                return "Failed";
            case ROLLED_BACK:
                return "Rolled Back";
            case CONFLICT:
                return "Conflict";
            case SKIPPED:
                return "Skipped";
            default:
                return status.name();
        }
    }

    public static FixStatus statusForLabel(String label) {
        if (label == null) return null;
        for (FixStatus status : FixStatus.values()) {
            if (statusLabel(status).equals(label)) return status;
        }
        return null;
    }

    /**
     * Removing a stereotype is identified by its old value and therefore does
     * not require a replacement value. All other current action types do.
     */
    public static boolean requiresNewValue(FixAction action) {
        return action != null && action.actionType() != FixActionType.REMOVE_STEREOTYPE;
    }

    public static boolean hasRequiredInput(FixAction action) {
        if (action == null) return false;
        if (!requiresNewValue(action)) {
            return hasText(action.oldValue()) || hasText(action.newValue());
        }
        return hasText(action.newValue());
    }

    public static boolean isActionable(FixAction action, boolean selected) {
        return selected && hasRequiredInput(action);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
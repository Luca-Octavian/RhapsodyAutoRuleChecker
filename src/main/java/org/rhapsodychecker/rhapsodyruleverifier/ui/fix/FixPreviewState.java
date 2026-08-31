package org.rhapsodychecker.rhapsodyruleverifier.ui.fix;

import java.util.ArrayList;
import java.util.List;

/**
 * Session-scoped UI state for a fix preview. The controller retains one
 * instance for the lifetime of the current fix plan.
 */
public final class FixPreviewState {

    private final List<Boolean> selectedRows = new ArrayList<Boolean>();

    public boolean isSelected(int row) {
        ensureSize(row + 1);
        return selectedRows.get(row).booleanValue();
    }

    public void setSelected(int row, boolean selected) {
        ensureSize(row + 1);
        selectedRows.set(row, Boolean.valueOf(selected));
    }

    private void ensureSize(int size) {
        while (selectedRows.size() < size) {
            selectedRows.add(Boolean.TRUE);
        }
    }
}
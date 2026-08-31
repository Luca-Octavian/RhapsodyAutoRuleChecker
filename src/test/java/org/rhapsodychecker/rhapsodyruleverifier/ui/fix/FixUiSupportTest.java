package org.rhapsodychecker.rhapsodyruleverifier.ui.fix;

import org.junit.Test;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixAction;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixActionType;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixStatus;

import static org.junit.Assert.*;

public class FixUiSupportTest {

    @Test
    public void mapsEveryInternalStatusToUserFacingLabel() {
        assertEquals("Pending", FixUiSupport.statusLabel(FixStatus.PENDING));
        assertEquals("OK", FixUiSupport.statusLabel(FixStatus.SIMULATED));
        assertEquals("Applied", FixUiSupport.statusLabel(FixStatus.APPLIED));
        assertEquals("Failed", FixUiSupport.statusLabel(FixStatus.FAILED));
        assertEquals("Rolled Back", FixUiSupport.statusLabel(FixStatus.ROLLED_BACK));
        assertEquals("Conflict", FixUiSupport.statusLabel(FixStatus.CONFLICT));
        assertEquals("Skipped", FixUiSupport.statusLabel(FixStatus.SKIPPED));

        for (FixStatus status : FixStatus.values()) {
            assertEquals(status, FixUiSupport.statusForLabel(FixUiSupport.statusLabel(status)));
        }
    }

    @Test
    public void removeStereotypeIsActionableWithoutNewValue() {
        FixAction action = action(FixActionType.REMOVE_STEREOTYPE, "ExistingStereo", null);

        assertFalse(FixUiSupport.requiresNewValue(action));
        assertTrue(FixUiSupport.hasRequiredInput(action));
        assertTrue(FixUiSupport.isActionable(action, true));
        assertFalse(FixUiSupport.isActionable(action, false));
    }

    @Test
    public void valueSettingActionRequiresNonBlankNewValue() {
        FixAction empty = action(FixActionType.SET_TAG_VALUE, "old", "  ");
        FixAction populated = action(FixActionType.SET_TAG_VALUE, "old", "new");

        assertTrue(FixUiSupport.requiresNewValue(empty));
        assertFalse(FixUiSupport.hasRequiredInput(empty));
        assertFalse(FixUiSupport.isActionable(empty, true));
        assertTrue(FixUiSupport.isActionable(populated, true));
    }

    @Test
    public void previewSelectionsPersistInSharedSessionState() {
        FixPreviewState state = new FixPreviewState();

        assertTrue(state.isSelected(0));
        state.setSelected(0, false);
        state.setSelected(2, false);

        assertFalse(state.isSelected(0));
        assertTrue(state.isSelected(1));
        assertFalse(state.isSelected(2));
    }

    private static FixAction action(FixActionType type, String oldValue, String newValue) {
        return FixAction.builder()
                .elementGuid("guid")
                .elementName("Element")
                .actionType(type)
                .oldValue(oldValue)
                .newValue(newValue)
                .ruleId("rule")
                .build();
    }
}
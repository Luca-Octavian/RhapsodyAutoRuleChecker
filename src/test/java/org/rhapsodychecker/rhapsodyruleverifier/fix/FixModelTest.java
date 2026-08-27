package org.rhapsodychecker.rhapsodyruleverifier.fix;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for FixAction, FixEntry, FixPlan, and FixPlanJournal.
 */
public class FixModelTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private FixAction sampleAction() {
        return FixAction.builder()
                .elementGuid("GUID-1")
                .elementName("MyBlock")
                .actionType(FixActionType.SET_NAME)
                .field(null)
                .oldValue("OldName")
                .newValue("NewName")
                .ruleId("rule-01")
                .build();
    }

    // ---- FixAction tests ----

    @Test
    public void fixAction_requiredFields() {
        FixAction a = sampleAction();
        assertEquals("GUID-1", a.elementGuid());
        assertEquals("MyBlock", a.elementName());
        assertEquals(FixActionType.SET_NAME, a.actionType());
        assertNull(a.field());
        assertEquals("OldName", a.oldValue());
        assertEquals("NewName", a.newValue());
        assertEquals("rule-01", a.ruleId());
    }

    @Test
    public void fixAction_defaultDescription() {
        FixAction a = sampleAction();
        String desc = a.description();
        assertTrue(desc.contains("SET_NAME"));
        assertTrue(desc.contains("MyBlock"));
        assertTrue(desc.contains("NewName"));
    }

    @Test
    public void fixAction_customDescription() {
        FixAction a = FixAction.builder()
                .elementGuid("G1").elementName("E1")
                .actionType(FixActionType.ADD_STEREOTYPE)
                .ruleId("r1")
                .description("Custom desc")
                .build();
        assertEquals("Custom desc", a.description());
    }

    @Test(expected = NullPointerException.class)
    public void fixAction_nullGuid_throws() {
        FixAction.builder()
                .elementName("E").actionType(FixActionType.SET_NAME).ruleId("r")
                .build();
    }

    @Test(expected = NullPointerException.class)
    public void fixAction_nullRuleId_throws() {
        FixAction.builder()
                .elementGuid("G").elementName("E").actionType(FixActionType.SET_NAME)
                .build();
    }

    @Test
    public void fixAction_equality() {
        FixAction a = sampleAction();
        FixAction b = sampleAction();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void fixAction_inequality_differentType() {
        FixAction a = sampleAction();
        FixAction b = FixAction.builder()
                .elementGuid("GUID-1").elementName("MyBlock")
                .actionType(FixActionType.SET_DESCRIPTION)
                .ruleId("rule-01").newValue("NewName")
                .build();
        assertNotEquals(a, b);
    }

    // ---- FixEntry tests ----

    @Test
    public void fixEntry_defaultsPending() {
        FixEntry e = new FixEntry(sampleAction());
        assertEquals(FixStatus.PENDING, e.status());
        assertNull(e.errorMessage());
        assertNull(e.liveOldValue());
    }

    @Test
    public void fixEntry_statusTransitions() {
        FixEntry e = new FixEntry(sampleAction());

        e.markSimulated();
        assertEquals(FixStatus.SIMULATED, e.status());

        e.markApplied("ActualOld");
        assertEquals(FixStatus.APPLIED, e.status());
        assertEquals("ActualOld", e.liveOldValue());

        e.markRolledBack();
        assertEquals(FixStatus.ROLLED_BACK, e.status());
    }

    @Test
    public void fixEntry_failedWithMessage() {
        FixEntry e = new FixEntry(sampleAction());
        e.markFailed("COM error");
        assertEquals(FixStatus.FAILED, e.status());
        assertEquals("COM error", e.errorMessage());
    }

    @Test
    public void fixEntry_conflictWithReason() {
        FixEntry e = new FixEntry(sampleAction());
        e.markConflict("Element no longer exists");
        assertEquals(FixStatus.CONFLICT, e.status());
        assertEquals("Element no longer exists", e.errorMessage());
    }

    // ---- FixPlan tests ----

    @Test
    public void fixPlan_addAndCount() {
        FixPlan plan = new FixPlan("MODEL-1", "config.yaml");
        assertEquals("MODEL-1", plan.modelGuid());
        assertEquals("config.yaml", plan.configPath());
        assertTrue(plan.entries().isEmpty());

        plan.addAction(sampleAction());
        assertEquals(1, plan.entries().size());
        assertEquals(1, plan.countByStatus(FixStatus.PENDING));
        assertEquals(0, plan.countByStatus(FixStatus.APPLIED));
    }

    @Test
    public void fixPlan_isReadyToApply() {
        FixPlan plan = new FixPlan("M", null);
        assertFalse(plan.isReadyToApply()); // empty

        plan.addAction(sampleAction());
        assertTrue(plan.isReadyToApply()); // one PENDING

        plan.entries().get(0).markSimulated();
        assertTrue(plan.isReadyToApply()); // one SIMULATED

        plan.entries().get(0).markFailed("err");
        assertFalse(plan.isReadyToApply()); // FAILED
    }

    @Test
    public void fixPlan_appliedEntries() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(sampleAction());
        plan.addAction(FixAction.builder()
                .elementGuid("G2").elementName("E2")
                .actionType(FixActionType.ADD_STEREOTYPE)
                .ruleId("r2").newValue("stereo")
                .build());

        plan.entries().get(0).markApplied("old");
        List<FixEntry> applied = plan.appliedEntries();
        assertEquals(1, applied.size());
        assertTrue(plan.hasAppliedEntries());
    }

    // ---- FixPlanJournal tests ----

    @Test
    public void journal_writeAndRead_roundtrip() throws Exception {
        File dir = tempDir.newFolder("journals");
        FixPlanJournal journal = new FixPlanJournal(dir);

        FixPlan plan = new FixPlan("MODEL-ABC", "test.yaml", 1234567890000L, null);
        plan.addAction(sampleAction());
        plan.addAction(FixAction.builder()
                .elementGuid("G2").elementName("Port1")
                .actionType(FixActionType.SET_TAG_VALUE)
                .field("color")
                .oldValue("blue")
                .newValue("red")
                .ruleId("rule-02")
                .build());

        // Mark one as applied
        plan.entries().get(0).markApplied("RealOld");

        File written = journal.write(plan);
        assertTrue(written.exists());
        assertTrue(written.getName().endsWith(".json"));

        // Read back
        FixPlan loaded = journal.read(written);
        assertEquals("MODEL-ABC", loaded.modelGuid());
        assertEquals("test.yaml", loaded.configPath());
        assertEquals(1234567890000L, loaded.timestamp());
        assertEquals(2, loaded.entries().size());

        FixEntry e0 = loaded.entries().get(0);
        assertEquals(FixStatus.APPLIED, e0.status());
        assertEquals("GUID-1", e0.action().elementGuid());
        assertEquals(FixActionType.SET_NAME, e0.action().actionType());

        FixEntry e1 = loaded.entries().get(1);
        assertEquals(FixStatus.PENDING, e1.status());
        assertEquals("color", e1.action().field());
        assertEquals("blue", e1.action().oldValue());
        assertEquals("red", e1.action().newValue());
    }

    @Test
    public void journal_listJournals() throws Exception {
        File dir = tempDir.newFolder("journals2");
        FixPlanJournal journal = new FixPlanJournal(dir);

        journal.write(new FixPlan("M1", null, 1000L, null));
        journal.write(new FixPlan("M2", null, 2000L, null));

        List<File> files = journal.listJournals();
        assertEquals(2, files.size());
    }

    @Test
    public void journal_emptyDir_returnsEmptyList() throws Exception {
        File dir = tempDir.newFolder("empty");
        FixPlanJournal journal = new FixPlanJournal(dir);
        assertTrue(journal.listJournals().isEmpty());
    }

    @Test
    public void journal_nonExistentDir_returnsEmptyList() {
        FixPlanJournal journal = new FixPlanJournal(new File("nonexistent_dir_xyz"));
        assertTrue(journal.listJournals().isEmpty());
    }
}
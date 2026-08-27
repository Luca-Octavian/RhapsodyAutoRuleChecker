package org.rhapsodychecker.rhapsodyruleverifier.fix;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.io.File;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Edge-case tests:
 * - Model inaccessible (executor throws / returns element-not-found)
 * - Partial apply + rollback
 * - Journal-based rollback (load from file, revert)
 * - Rollback of empty/already-rolled-back plans
 * - Concurrent modification detection (stale old values)
 */
public class FixEdgeCasesAndRollbackTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private ElementIndex index;
    private FixPlanJournal journal;

    @Before
    public void setUp() throws Exception {
        ElementRecord block = ElementRecord.builder()
                .guid("G1").name("Block1").metaClass("Class").kind(ElementKind.BLOCK)
                .stereotypes(Arrays.asList("existing"))
                .description("original desc")
                .build();
        ElementRecord port = ElementRecord.builder()
                .guid("G2").name("Port1").metaClass("Port").kind(ElementKind.PORT)
                .initialValue("10")
                .build();
        index = ElementIndex.build(Arrays.asList(block, port));
        journal = new FixPlanJournal(tempDir.newFolder("journals"));
    }

    private FixAction addStereotypeAction(String guid, String name, String stereo) {
        return FixAction.builder()
                .elementGuid(guid).elementName(name)
                .actionType(FixActionType.ADD_STEREOTYPE)
                .newValue(stereo)
                .ruleId("r1").build();
    }

    private FixAction setNameAction(String guid, String name, String oldVal, String newVal) {
        return FixAction.builder()
                .elementGuid(guid).elementName(name)
                .actionType(FixActionType.SET_NAME)
                .oldValue(oldVal).newValue(newVal)
                .ruleId("r1").build();
    }

    // ---- Model inaccessible: executor always fails ----

    @Test
    public void modelInaccessible_allEntriesFail() {
        FixExecutor deadExecutor = new FixExecutor() {
            @Override
            public void apply(FixEntry entry) {
                entry.markFailed("COM connection lost: model is closed");
            }
            @Override
            public void rollback(FixEntry entry) {
                entry.markFailed("COM connection lost: model is closed");
            }
        };

        FixService service = new FixService(deadExecutor, journal, index);
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(addStereotypeAction("G1", "Block1", "needed"));
        plan.addAction(setNameAction("G2", "Port1", "Port1", "RenamedPort"));

        // Simulate passes (works against snapshot, not COM)
        int conflicts = service.simulate(plan);
        assertEquals(0, conflicts);

        // Apply fails for all
        int applied = service.apply(plan, false);
        assertEquals(0, applied);
        assertEquals(FixStatus.FAILED, plan.entries().get(0).status());
        assertEquals(FixStatus.FAILED, plan.entries().get(1).status());
        assertTrue(plan.entries().get(0).errorMessage().contains("COM connection lost"));
    }

    @Test
    public void modelInaccessible_stopOnFailure_stopsAtFirst() {
        FixExecutor deadExecutor = new FixExecutor() {
            @Override
            public void apply(FixEntry entry) {
                entry.markFailed("Rhapsody not running");
            }
            @Override
            public void rollback(FixEntry entry) {
                entry.markFailed("Rhapsody not running");
            }
        };

        FixService service = new FixService(deadExecutor, journal, index);
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(addStereotypeAction("G1", "Block1", "s1"));
        plan.addAction(addStereotypeAction("G1", "Block1", "s2"));

        int applied = service.apply(plan, true);
        assertEquals(0, applied);
        // First entry failed, second still PENDING (not attempted)
        assertEquals(FixStatus.FAILED, plan.entries().get(0).status());
        assertEquals(FixStatus.PENDING, plan.entries().get(1).status());
    }

    // ---- Element-not-found (deleted between eval and fix) ----

    @Test
    public void elementNotFound_executorMarksFailed() {
        // Executor that simulates findElementByGUID returning null
        FixExecutor notFoundExecutor = new FixExecutor() {
            @Override
            public void apply(FixEntry entry) {
                entry.markFailed("Element not found in Rhapsody: GUID=" + entry.action().elementGuid());
            }
            @Override
            public void rollback(FixEntry entry) {
                entry.markFailed("Element not found for rollback");
            }
        };

        FixService service = new FixService(notFoundExecutor, journal, index);
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(addStereotypeAction("G1", "Block1", "s"));

        int applied = service.apply(plan, false);
        assertEquals(0, applied);
        assertTrue(plan.entries().get(0).errorMessage().contains("not found"));
    }

    // ---- Partial apply + rollback ----

    @Test
    public void partialApply_thenRollback_onlyRevertApplied() {
        // Executor that succeeds on first, fails on second
        FixExecutor partialExecutor = new FixExecutor() {
            int callCount = 0;
            @Override
            public void apply(FixEntry entry) {
                callCount++;
                if (callCount == 1) {
                    entry.markApplied("oldVal1");
                } else {
                    entry.markFailed("Simulated COM error on second element");
                }
            }
            @Override
            public void rollback(FixEntry entry) {
                entry.markRolledBack();
            }
        };

        FixService service = new FixService(partialExecutor, journal, index);
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(addStereotypeAction("G1", "Block1", "s1"));
        plan.addAction(setNameAction("G2", "Port1", "Port1", "NewPort"));

        int applied = service.apply(plan, false);
        assertEquals(1, applied); // only first succeeded

        // Rollback should only touch the one that was APPLIED
        int rolledBack = service.rollback(plan);
        assertEquals(1, rolledBack);
        assertEquals(FixStatus.ROLLED_BACK, plan.entries().get(0).status());
        assertEquals(FixStatus.FAILED, plan.entries().get(1).status()); // unchanged
    }

    // ---- Journal-based rollback (revert from file) ----

    @Test
    public void journalRollback_loadAndRevert() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        FixService service = new FixService(executor, journal, index);

        // Build and apply a plan
        FixPlan plan = new FixPlan("MODEL-1", "config.yaml");
        plan.addAction(addStereotypeAction("G1", "Block1", "needed"));
        plan.addAction(setNameAction("G2", "Port1", "Port1", "NewPort"));

        service.simulate(plan);
        service.apply(plan, false);
        assertEquals(2, executor.appliedEntries.size());

        // Journal was written — verify
        List<File> files = journal.listJournals();
        assertEquals(1, files.size());

        // Simulate new session: load journal, rollback
        FixPlan loaded = journal.read(files.get(0));
        assertEquals("MODEL-1", loaded.modelGuid());
        assertEquals(2, loaded.entries().size());
        assertEquals(FixStatus.APPLIED, loaded.entries().get(0).status());
        assertEquals(FixStatus.APPLIED, loaded.entries().get(1).status());

        // Rollback from loaded journal
        int rolledBack = service.rollback(loaded);
        assertEquals(2, rolledBack);
        assertEquals(FixStatus.ROLLED_BACK, loaded.entries().get(0).status());
        assertEquals(FixStatus.ROLLED_BACK, loaded.entries().get(1).status());
    }

    @Test
    public void journalRollback_preservesActionDetails() throws Exception {
        RecordingExecutor executor = new RecordingExecutor();
        FixService service = new FixService(executor, journal, index);

        FixPlan plan = new FixPlan("M", null);
        plan.addAction(FixAction.builder()
                .elementGuid("G1").elementName("Block1")
                .actionType(FixActionType.SET_DESCRIPTION)
                .oldValue("original desc").newValue("new desc")
                .ruleId("desc-rule").build());

        service.apply(plan, false);
        File journalFile = journal.listJournals().get(0);
        FixPlan loaded = journal.read(journalFile);

        FixAction loadedAction = loaded.entries().get(0).action();
        assertEquals("G1", loadedAction.elementGuid());
        assertEquals("Block1", loadedAction.elementName());
        assertEquals(FixActionType.SET_DESCRIPTION, loadedAction.actionType());
        assertEquals("original desc", loadedAction.oldValue());
        assertEquals("new desc", loadedAction.newValue());
        assertEquals("desc-rule", loadedAction.ruleId());
    }

    // ---- Rollback edge cases ----

    @Test
    public void rollback_emptyPlan_returnsZero() {
        RecordingExecutor executor = new RecordingExecutor();
        FixService service = new FixService(executor, journal, index);
        FixPlan plan = new FixPlan("M", null);
        int rolledBack = service.rollback(plan);
        assertEquals(0, rolledBack);
    }

    @Test
    public void rollback_alreadyRolledBack_noDoubleRollback() {
        RecordingExecutor executor = new RecordingExecutor();
        FixService service = new FixService(executor, journal, index);
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(addStereotypeAction("G1", "Block1", "s"));

        service.apply(plan, false);
        service.rollback(plan);
        assertEquals(FixStatus.ROLLED_BACK, plan.entries().get(0).status());

        // Second rollback — no APPLIED entries left
        int second = service.rollback(plan);
        assertEquals(0, second);
    }

    @Test
    public void rollbackLast_noAppliedPlan_returnsMinusOne() {
        RecordingExecutor executor = new RecordingExecutor();
        FixService service = new FixService(executor, journal, index);
        assertEquals(-1, service.rollbackLast());
    }

    // ---- Rollback when COM fails during rollback ----

    @Test
    public void rollback_comFailsDuringRollback_marksEntryFailed() {
        FixExecutor failRollbackExecutor = new FixExecutor() {
            @Override
            public void apply(FixEntry entry) {
                entry.markApplied("liveOld");
            }
            @Override
            public void rollback(FixEntry entry) {
                entry.markFailed("COM error during rollback: model locked");
            }
        };

        FixService service = new FixService(failRollbackExecutor, journal, index);
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(addStereotypeAction("G1", "Block1", "s"));
        service.apply(plan, false);

        int rolledBack = service.rollback(plan);
        assertEquals(0, rolledBack); // rollback failed
        assertEquals(FixStatus.FAILED, plan.entries().get(0).status());
        assertTrue(plan.entries().get(0).errorMessage().contains("model locked"));
    }

    // ---- Stale model: element changed between eval and fix ----

    @Test
    public void simulation_detectsStaleName() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(FixAction.builder()
                .elementGuid("G1").elementName("Block1")
                .actionType(FixActionType.SET_NAME)
                .oldValue("WasCalledSomethingElse")
                .newValue("CorrectName")
                .ruleId("r1").build());

        int conflicts = new FixSimulator(index).simulate(plan);
        assertEquals(1, conflicts);
        assertTrue(plan.entries().get(0).errorMessage().contains("has changed"));
    }

    @Test
    public void simulation_detectsStaleDescription() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(FixAction.builder()
                .elementGuid("G1").elementName("Block1")
                .actionType(FixActionType.SET_DESCRIPTION)
                .oldValue("wrong old desc")
                .newValue("new desc")
                .ruleId("r1").build());

        int conflicts = new FixSimulator(index).simulate(plan);
        assertEquals(1, conflicts);
    }

    @Test
    public void simulation_detectsStaleInitialValue() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(FixAction.builder()
                .elementGuid("G2").elementName("Port1")
                .actionType(FixActionType.SET_INITIAL_VALUE)
                .oldValue("999")  // actual is "10"
                .newValue("20")
                .ruleId("r1").build());

        int conflicts = new FixSimulator(index).simulate(plan);
        assertEquals(1, conflicts);
    }

    @Test
    public void simulation_passesWhenOldValueMatchesSnapshot() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(FixAction.builder()
                .elementGuid("G2").elementName("Port1")
                .actionType(FixActionType.SET_INITIAL_VALUE)
                .oldValue("10")  // matches snapshot
                .newValue("20")
                .ruleId("r1").build());

        int conflicts = new FixSimulator(index).simulate(plan);
        assertEquals(0, conflicts);
    }

    // ---- Recording executor for testing ----

    static class RecordingExecutor implements FixExecutor {
        List<FixEntry> appliedEntries = new ArrayList<FixEntry>();
        List<FixEntry> rolledBackEntries = new ArrayList<FixEntry>();

        @Override
        public void apply(FixEntry entry) {
            entry.markApplied("recorded-old-" + appliedEntries.size());
            appliedEntries.add(entry);
        }

        @Override
        public void rollback(FixEntry entry) {
            entry.markRolledBack();
            rolledBackEntries.add(entry);
        }
    }
}
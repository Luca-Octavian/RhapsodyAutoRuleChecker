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
 * Tests for FixService using a recording stub FixExecutor (no Rhapsody needed).
 */
public class FixServiceTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private ElementIndex index;
    private RecordingFixExecutor executor;
    private FixPlanJournal journal;
    private FixService service;

    @Before
    public void setUp() throws Exception {
        ElementRecord block = ElementRecord.builder()
                .guid("G1").name("Block1").metaClass("Class").kind(ElementKind.BLOCK)
                .build();
        index = ElementIndex.build(Arrays.asList(block));
        executor = new RecordingFixExecutor();
        journal = new FixPlanJournal(tempDir.newFolder("journals"));
        service = new FixService(executor, journal, index);
    }

    private FixAction sampleAction() {
        return FixAction.builder()
                .elementGuid("G1").elementName("Block1")
                .actionType(FixActionType.ADD_STEREOTYPE)
                .newValue("needed")
                .ruleId("r1").build();
    }

    // ---- Simulate ----

    @Test
    public void simulate_delegatesToSimulator() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(sampleAction());
        int conflicts = service.simulate(plan);
        assertEquals(0, conflicts);
        assertEquals(FixStatus.SIMULATED, plan.entries().get(0).status());
    }

    // ---- Apply ----

    @Test
    public void apply_appliesSimulatedEntries() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(sampleAction());
        service.simulate(plan);

        int applied = service.apply(plan, false);
        assertEquals(1, applied);
        assertEquals(1, executor.applyCalls);
        assertEquals(FixStatus.APPLIED, plan.entries().get(0).status());
    }

    @Test
    public void apply_writesJournal() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(sampleAction());
        service.apply(plan, false);

        List<File> journals = journal.listJournals();
        assertEquals(1, journals.size());
    }

    @Test
    public void apply_stopOnFailure_stopsAfterFirst() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(sampleAction());
        plan.addAction(sampleAction());

        // Make executor fail on everything
        executor.failOnApply = true;
        int applied = service.apply(plan, true);
        assertEquals(0, applied);
        // With stopOnFailure=true, should stop after first failure
        assertEquals(1, executor.applyCalls);
    }

    @Test
    public void apply_noStopOnFailure_continuesAfterFailure() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(sampleAction());
        plan.addAction(sampleAction());

        executor.failOnApply = true;
        int applied = service.apply(plan, false);
        assertEquals(0, applied);
        assertEquals(2, executor.applyCalls); // tried both
    }

    @Test
    public void apply_skipsConflictEntries() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(sampleAction());
        plan.entries().get(0).markConflict("stale");

        int applied = service.apply(plan, false);
        assertEquals(0, applied);
        assertEquals(0, executor.applyCalls);
    }

    // ---- Rollback ----

    @Test
    public void rollback_rollsBackAppliedEntries() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(sampleAction());
        service.apply(plan, false);

        int rolledBack = service.rollback(plan);
        assertEquals(1, rolledBack);
        assertEquals(1, executor.rollbackCalls);
        assertEquals(FixStatus.ROLLED_BACK, plan.entries().get(0).status());
    }

    @Test
    public void rollbackLast_worksAfterApply() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(sampleAction());
        service.apply(plan, false);

        assertNotNull(service.lastAppliedPlan());
        int rolledBack = service.rollbackLast();
        assertEquals(1, rolledBack);
    }

    @Test
    public void rollbackLast_returnsNegativeOneWhenNoPlan() {
        assertEquals(-1, service.rollbackLast());
    }

    // ---- Recording stub executor ----

    /**
     * Simple in-memory FixExecutor for testing.
     * Records calls and always succeeds unless failOnApply is set.
     */
    static class RecordingFixExecutor implements FixExecutor {
        int applyCalls = 0;
        int rollbackCalls = 0;
        boolean failOnApply = false;

        @Override
        public void apply(FixEntry entry) {
            applyCalls++;
            if (failOnApply) {
                entry.markFailed("Simulated failure");
            } else {
                entry.markApplied("oldValue-" + applyCalls);
            }
        }

        @Override
        public void rollback(FixEntry entry) {
            rollbackCalls++;
            entry.markRolledBack();
        }
    }
}
package org.rhapsodychecker.rhapsodyruleverifier.fix;

import org.junit.Before;
import org.junit.Test;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.AliasResolver;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.*;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl.RequiredStereotypeRule;

import java.util.*;

import static org.junit.Assert.*;

public class FixSimulatorAndCollectorTest {

    private ElementRecord block1;
    private ElementRecord block2;
    private ElementIndex index;

    @Before
    public void setUp() {
        block1 = ElementRecord.builder()
                .guid("G1").name("Block1").metaClass("Class").kind(ElementKind.BLOCK)
                .stereotypes(Arrays.asList("existing"))
                .description("some desc")
                .build();
        block2 = ElementRecord.builder()
                .guid("G2").name("Block2").metaClass("Class").kind(ElementKind.BLOCK)
                .build();
        index = ElementIndex.build(Arrays.asList(block1, block2));
    }

    // ---- FixSimulator ----

    @Test
    public void simulator_validSetName_marksSimulated() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(FixAction.builder()
                .elementGuid("G1").elementName("Block1")
                .actionType(FixActionType.SET_NAME)
                .oldValue("Block1").newValue("RenamedBlock")
                .ruleId("r1").build());

        int conflicts = new FixSimulator(index).simulate(plan);
        assertEquals(0, conflicts);
        assertEquals(FixStatus.SIMULATED, plan.entries().get(0).status());
    }

    @Test
    public void simulator_missingElement_marksConflict() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(FixAction.builder()
                .elementGuid("NONEXISTENT").elementName("Ghost")
                .actionType(FixActionType.SET_NAME)
                .ruleId("r1").build());

        int conflicts = new FixSimulator(index).simulate(plan);
        assertEquals(1, conflicts);
        assertEquals(FixStatus.CONFLICT, plan.entries().get(0).status());
        assertTrue(plan.entries().get(0).errorMessage().contains("no longer exists"));
    }

    @Test
    public void simulator_staleOldValue_marksConflict() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(FixAction.builder()
                .elementGuid("G1").elementName("Block1")
                .actionType(FixActionType.SET_NAME)
                .oldValue("WrongOldName").newValue("NewName")
                .ruleId("r1").build());

        int conflicts = new FixSimulator(index).simulate(plan);
        assertEquals(1, conflicts);
        assertTrue(plan.entries().get(0).errorMessage().contains("has changed"));
    }

    @Test
    public void simulator_nullOldValue_skipsConflictCheck() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(FixAction.builder()
                .elementGuid("G1").elementName("Block1")
                .actionType(FixActionType.SET_NAME)
                .oldValue(null).newValue("NewName")
                .ruleId("r1").build());

        int conflicts = new FixSimulator(index).simulate(plan);
        assertEquals(0, conflicts);
        assertEquals(FixStatus.SIMULATED, plan.entries().get(0).status());
    }

    @Test
    public void simulator_addStereotype_alreadyPresent_conflict() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(FixAction.builder()
                .elementGuid("G1").elementName("Block1")
                .actionType(FixActionType.ADD_STEREOTYPE)
                .newValue("existing")
                .ruleId("r1").build());

        int conflicts = new FixSimulator(index).simulate(plan);
        assertEquals(1, conflicts);
        assertTrue(plan.entries().get(0).errorMessage().contains("already present"));
    }

    @Test
    public void simulator_addStereotype_notPresent_ok() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(FixAction.builder()
                .elementGuid("G2").elementName("Block2")
                .actionType(FixActionType.ADD_STEREOTYPE)
                .newValue("newStereo")
                .ruleId("r1").build());

        int conflicts = new FixSimulator(index).simulate(plan);
        assertEquals(0, conflicts);
        assertEquals(FixStatus.SIMULATED, plan.entries().get(0).status());
    }

    @Test
    public void simulator_removeStereotype_notPresent_conflict() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(FixAction.builder()
                .elementGuid("G2").elementName("Block2")
                .actionType(FixActionType.REMOVE_STEREOTYPE)
                .oldValue("missing")
                .ruleId("r1").build());

        int conflicts = new FixSimulator(index).simulate(plan);
        assertEquals(1, conflicts);
        assertTrue(plan.entries().get(0).errorMessage().contains("not found"));
    }

    @Test
    public void simulator_setDescription_oldMatches() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(FixAction.builder()
                .elementGuid("G1").elementName("Block1")
                .actionType(FixActionType.SET_DESCRIPTION)
                .oldValue("some desc").newValue("new desc")
                .ruleId("r1").build());

        int conflicts = new FixSimulator(index).simulate(plan);
        assertEquals(0, conflicts);
    }

    @Test
    public void simulator_skipsNonPending() {
        FixPlan plan = new FixPlan("M", null);
        plan.addAction(FixAction.builder()
                .elementGuid("G1").elementName("Block1")
                .actionType(FixActionType.SET_NAME)
                .ruleId("r1").build());
        plan.entries().get(0).markSimulated(); // already simulated

        int conflicts = new FixSimulator(index).simulate(plan);
        assertEquals(0, conflicts);
        assertEquals(FixStatus.SIMULATED, plan.entries().get(0).status());
    }

    @Test
    public void simulator_multipleEntries_mixedResults() {
        FixPlan plan = new FixPlan("M", null);
        // Valid
        plan.addAction(FixAction.builder()
                .elementGuid("G2").elementName("Block2")
                .actionType(FixActionType.ADD_STEREOTYPE).newValue("s1")
                .ruleId("r1").build());
        // Conflict
        plan.addAction(FixAction.builder()
                .elementGuid("GONE").elementName("Deleted")
                .actionType(FixActionType.SET_NAME)
                .ruleId("r2").build());

        int conflicts = new FixSimulator(index).simulate(plan);
        assertEquals(1, conflicts);
        assertEquals(FixStatus.SIMULATED, plan.entries().get(0).status());
        assertEquals(FixStatus.CONFLICT, plan.entries().get(1).status());
    }

    // ---- RequiredStereotypeRule.suggestFix ----

    @Test
    public void requiredStereotypeRule_suggestsFix_whenMissing() {
        RequiredStereotypeRule rule = new RequiredStereotypeRule();
        Map<String, Object> config = new HashMap<String, Object>();
        config.put("ruleId", "stereo-rule");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("requiredStereotypes", Arrays.asList("block", "newStereo"));
        config.put("params", params);
        rule.configure(config);

        // block1 has "existing" but not "block" or "newStereo"
        Optional<FixAction> fix = rule.suggestFix(block1, null);
        assertTrue(fix.isPresent());
        assertEquals(FixActionType.ADD_STEREOTYPE, fix.get().actionType());
        assertEquals("block", fix.get().newValue());
        assertEquals("G1", fix.get().elementGuid());
    }

    @Test
    public void requiredStereotypeRule_noFix_whenAllPresent() {
        RequiredStereotypeRule rule = new RequiredStereotypeRule();
        Map<String, Object> config = new HashMap<String, Object>();
        config.put("ruleId", "stereo-rule");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("requiredStereotypes", Arrays.asList("existing"));
        config.put("params", params);
        rule.configure(config);

        Optional<FixAction> fix = rule.suggestFix(block1, null);
        assertFalse(fix.isPresent());
    }

    // ---- FixCollector ----

    @Test
    public void collector_collectsFixesFromFailedResults() {
        // Set up rule
        RequiredStereotypeRule rule = new RequiredStereotypeRule();
        Map<String, Object> config = new HashMap<String, Object>();
        config.put("ruleId", "r1");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("requiredStereotypes", Arrays.asList("needed"));
        config.put("params", params);
        rule.configure(config);

        Map<String, Rule> ruleMap = new HashMap<String, Rule>();
        ruleMap.put("r1", rule);

        // Fake fail result
        List<RuleResult> results = new ArrayList<RuleResult>();
        results.add(DefaultRuleResult.fail("r1", "G2", "missing stereotype"));

        FixCollector collector = new FixCollector();
        FixPlan plan = collector.collect(results, ruleMap, index, null, "MODEL", null);

        assertEquals(1, plan.entries().size());
        FixAction a = plan.entries().get(0).action();
        assertEquals("G2", a.elementGuid());
        assertEquals(FixActionType.ADD_STEREOTYPE, a.actionType());
        assertEquals("needed", a.newValue());
    }

    @Test
    public void collector_ignoresPassResults() {
        RequiredStereotypeRule rule = new RequiredStereotypeRule();
        Map<String, Object> config = new HashMap<String, Object>();
        config.put("ruleId", "r1");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("requiredStereotypes", Arrays.asList("existing"));
        config.put("params", params);
        rule.configure(config);

        Map<String, Rule> ruleMap = new HashMap<String, Rule>();
        ruleMap.put("r1", rule);

        List<RuleResult> results = new ArrayList<RuleResult>();
        results.add(DefaultRuleResult.pass("r1", "G1"));

        FixPlan plan = new FixCollector().collect(results, ruleMap, index, null, "M", null);
        assertTrue(plan.entries().isEmpty());
    }

    @Test
    public void collector_ignoresUnknownRuleId() {
        List<RuleResult> results = new ArrayList<RuleResult>();
        results.add(DefaultRuleResult.fail("unknown", "G1", "fail"));

        Map<String, Rule> ruleMap = new HashMap<String, Rule>();
        FixPlan plan = new FixCollector().collect(results, ruleMap, index, null, "M", null);
        assertTrue(plan.entries().isEmpty());
    }

    @Test
    public void collector_ignoresMissingElement() {
        RequiredStereotypeRule rule = new RequiredStereotypeRule();
        Map<String, Object> config = new HashMap<String, Object>();
        config.put("ruleId", "r1");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("requiredStereotypes", Arrays.asList("s"));
        config.put("params", params);
        rule.configure(config);

        Map<String, Rule> ruleMap = new HashMap<String, Rule>();
        ruleMap.put("r1", rule);

        List<RuleResult> results = new ArrayList<RuleResult>();
        results.add(DefaultRuleResult.fail("r1", "NONEXIST", "fail"));

        FixPlan plan = new FixCollector().collect(results, ruleMap, index, null, "M", null);
        assertTrue(plan.entries().isEmpty());
    }

    // ---- End-to-end: collect + simulate ----

    @Test
    public void endToEnd_collectThenSimulate() {
        RequiredStereotypeRule rule = new RequiredStereotypeRule();
        Map<String, Object> config = new HashMap<String, Object>();
        config.put("ruleId", "r1");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("requiredStereotypes", Arrays.asList("needed"));
        config.put("params", params);
        rule.configure(config);

        Map<String, Rule> ruleMap = new HashMap<String, Rule>();
        ruleMap.put("r1", rule);

        List<RuleResult> results = new ArrayList<RuleResult>();
        results.add(DefaultRuleResult.fail("r1", "G2", "missing"));

        // Collect
        FixPlan plan = new FixCollector().collect(results, ruleMap, index, null, "M", null);
        assertEquals(1, plan.entries().size());
        assertEquals(FixStatus.PENDING, plan.entries().get(0).status());

        // Simulate
        int conflicts = new FixSimulator(index).simulate(plan);
        assertEquals(0, conflicts);
        assertEquals(FixStatus.SIMULATED, plan.entries().get(0).status());
        assertTrue(plan.isReadyToApply());
    }
}
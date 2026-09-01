package org.rhapsodychecker.rhapsodyruleverifier.core.rule;

import org.junit.Test;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for RuleGrouping: verifies that group verdicts are computed correctly
 * from individual rule results, including conjunction logic (all must pass),
 * mixed pass/fail, and ungrouped rules being ignored.
 */
public class RuleGroupingTest {

    private static RuleSpec spec(String id, String group) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("startsWith", "X");
        return RuleSpec.builder()
                .id(id).type(RuleType.NAMING_PATTERN).enabled(true)
                .group(group).params(params).build();
    }

    private static RuleSpec ungroupedSpec(String id) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("startsWith", "X");
        return RuleSpec.builder()
                .id(id).type(RuleType.NAMING_PATTERN).enabled(true)
                .params(params).build();
    }

    // ---- All pass → verdict allPassed ----

    @Test
    public void allPass_verdictIsTrue() {
        List<RuleSpec> specs = Arrays.asList(spec("r1", "grp"), spec("r2", "grp"));
        List<RuleResult> results = Arrays.asList(
                DefaultRuleResult.pass("r1", "G1"),
                DefaultRuleResult.pass("r2", "G1")
        );

        List<RuleGrouping.GroupVerdict> verdicts = RuleGrouping.computeVerdicts(results, specs);
        assertEquals(1, verdicts.size());
        assertTrue(verdicts.get(0).allPassed());
        assertEquals("grp", verdicts.get(0).group());
        assertEquals("G1", verdicts.get(0).elementGuid());
        assertTrue(verdicts.get(0).failedMembers().isEmpty());
    }

    // ---- One fails → verdict is false ----

    @Test
    public void oneFails_verdictIsFalse() {
        List<RuleSpec> specs = Arrays.asList(spec("r1", "grp"), spec("r2", "grp"));
        List<RuleResult> results = Arrays.asList(
                DefaultRuleResult.pass("r1", "G1"),
                DefaultRuleResult.fail("r2", "G1", "naming violation")
        );

        List<RuleGrouping.GroupVerdict> verdicts = RuleGrouping.computeVerdicts(results, specs);
        assertEquals(1, verdicts.size());
        assertFalse(verdicts.get(0).allPassed());
        assertEquals(1, verdicts.get(0).failedMembers().size());
    }

    // ---- Multiple elements: separate verdicts per element ----

    @Test
    public void multipleElements_separateVerdicts() {
        List<RuleSpec> specs = Arrays.asList(spec("r1", "grp"));
        List<RuleResult> results = Arrays.asList(
                DefaultRuleResult.pass("r1", "G1"),
                DefaultRuleResult.fail("r1", "G2", "fail")
        );

        List<RuleGrouping.GroupVerdict> verdicts = RuleGrouping.computeVerdicts(results, specs);
        assertEquals(2, verdicts.size());

        Map<String, Boolean> verdictMap = new LinkedHashMap<>();
        for (RuleGrouping.GroupVerdict v : verdicts) verdictMap.put(v.elementGuid(), v.allPassed());
        assertTrue(verdictMap.get("G1"));
        assertFalse(verdictMap.get("G2"));
    }

    // ---- Ungrouped rules are ignored ----

    @Test
    public void ungroupedRules_noVerdicts() {
        List<RuleSpec> specs = Arrays.asList(ungroupedSpec("r1"));
        List<RuleResult> results = Arrays.asList(DefaultRuleResult.fail("r1", "G1", "fail"));

        List<RuleGrouping.GroupVerdict> verdicts = RuleGrouping.computeVerdicts(results, specs);
        assertTrue(verdicts.isEmpty());
    }

    // ---- No grouped specs → empty ----

    @Test
    public void noGroupedSpecs_emptyVerdicts() {
        List<RuleGrouping.GroupVerdict> verdicts = RuleGrouping.computeVerdicts(
                Collections.<RuleResult>emptyList(), Collections.<RuleSpec>emptyList());
        assertTrue(verdicts.isEmpty());
    }

    // ---- collectGroups ----

    @Test
    public void collectGroups_returnsDistinctLabels() {
        List<RuleSpec> specs = Arrays.asList(
                spec("r1", "alpha"), spec("r2", "beta"), spec("r3", "alpha"),
                ungroupedSpec("r4"));
        Set<String> groups = RuleGrouping.collectGroups(specs);
        assertEquals(2, groups.size());
        assertTrue(groups.contains("alpha"));
        assertTrue(groups.contains("beta"));
    }

    // ---- Member results are preserved ----

    @Test
    public void memberResults_preservedInVerdict() {
        List<RuleSpec> specs = Arrays.asList(spec("r1", "grp"), spec("r2", "grp"));
        List<RuleResult> results = Arrays.asList(
                DefaultRuleResult.pass("r1", "G1"),
                DefaultRuleResult.fail("r2", "G1", "msg")
        );

        RuleGrouping.GroupVerdict v = RuleGrouping.computeVerdicts(results, specs).get(0);
        assertEquals(2, v.memberResults().size());
    }

    // ---- SKIPPED results don't cause failure (only FAIL does) ----

    @Test
    public void skippedResults_dontCauseFailure() {
        List<RuleSpec> specs = Arrays.asList(spec("r1", "grp"), spec("r2", "grp"));
        List<RuleResult> results = Arrays.asList(
                DefaultRuleResult.pass("r1", "G1"),
                DefaultRuleResult.skipped("r2", "G1", "not applicable")
        );

        List<RuleGrouping.GroupVerdict> verdicts = RuleGrouping.computeVerdicts(results, specs);
        assertTrue("SKIPPED should not cause group failure", verdicts.get(0).allPassed());
    }
}
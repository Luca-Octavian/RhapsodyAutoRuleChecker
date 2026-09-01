package org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl;

import org.junit.Test;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.*;

import java.util.*;

import static org.junit.Assert.*;

public class ChildCountRuleTest {

    private static ElementRecord parent(String guid) {
        return ElementRecord.builder()
                .guid(guid).name("Parent").metaClass("Class").kind(ElementKind.BLOCK).build();
    }

    private static ElementRecord child(String guid, String name, ElementKind kind, String ownerGuid) {
        return ElementRecord.builder()
                .guid(guid).name(name).metaClass("Port").kind(kind)
                .ownerGuid(ownerGuid).build();
    }

    private static ChildCountRule rule(String childKind, int minCount) {
        ChildCountRule r = new ChildCountRule();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("ruleId", "child-count-test");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("child_kind", childKind);
        params.put("min_count", minCount);
        config.put("params", params);
        r.configure(config);
        return r;
    }

    private static EvaluationContext contextWithChildren(final List<ElementRecord> children) {
        return new EvaluationContext() {
            @Override public org.rhapsodychecker.rhapsodyruleverifier.core.resolve.AliasResolver aliases() { return null; }
            @Override public int countMatchingRelations(ElementRecord e, RelationExistsRule.RelationQuery q) { return 0; }
            @Override public Optional<Object> getOption(String key) { return Optional.empty(); }
            @Override public Optional<ElementRecord> findElementByGuid(String guid) { return Optional.empty(); }
            @Override public List<ElementRecord> findElementsByOwnerGuid(String ownerGuid) { return children; }
        };
    }

    @Test
    public void enoughChildren_pass() {
        ElementRecord p = parent("P1");
        List<ElementRecord> children = Arrays.asList(
                child("C1", "port1", ElementKind.PORT_FLOW, "P1"),
                child("C2", "port2", ElementKind.PORT_FLOW, "P1")
        );
        ChildCountRule r = rule("PORT_FLOW", 1);
        assertEquals(RuleStatus.PASS, r.evaluate(p, contextWithChildren(children)).status());
    }

    @Test
    public void notEnoughChildren_fail() {
        ElementRecord p = parent("P1");
        List<ElementRecord> children = Collections.emptyList();
        ChildCountRule r = rule("PORT_FLOW", 1);
        RuleResult result = r.evaluate(p, contextWithChildren(children));
        assertEquals(RuleStatus.FAIL, result.status());
        assertTrue(result.message().contains("0"));
        assertTrue(result.message().contains("PORT_FLOW"));
    }

    @Test
    public void wrongKindNotCounted() {
        ElementRecord p = parent("P1");
        List<ElementRecord> children = Arrays.asList(
                child("C1", "part1", ElementKind.PART, "P1")
        );
        ChildCountRule r = rule("PORT_FLOW", 1);
        assertEquals(RuleStatus.FAIL, r.evaluate(p, contextWithChildren(children)).status());
    }

    @Test
    public void exactThreshold_pass() {
        ElementRecord p = parent("P1");
        List<ElementRecord> children = Arrays.asList(
                child("C1", "port1", ElementKind.PORT_FLOW, "P1")
        );
        ChildCountRule r = rule("PORT_FLOW", 1);
        assertEquals(RuleStatus.PASS, r.evaluate(p, contextWithChildren(children)).status());
    }

    @Test
    public void minCountZero_alwaysPass() {
        ElementRecord p = parent("P1");
        ChildCountRule r = rule("BLOCK", 0);
        assertEquals(RuleStatus.PASS, r.evaluate(p, contextWithChildren(Collections.<ElementRecord>emptyList())).status());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidKind_throws() {
        rule("NONEXISTENT_KIND", 1);
    }
}
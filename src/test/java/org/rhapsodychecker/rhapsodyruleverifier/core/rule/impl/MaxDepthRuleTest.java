package org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl;

import org.junit.Test;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.*;

import java.util.*;

import static org.junit.Assert.*;

public class MaxDepthRuleTest {

    private static ElementRecord element(String ownerPath) {
        return ElementRecord.builder()
                .guid("G1").name("Elem").metaClass("Class").kind(ElementKind.BLOCK)
                .ownerPath(ownerPath).build();
    }

    private static MaxDepthRule rule(int maxDepth) {
        MaxDepthRule r = new MaxDepthRule();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("ruleId", "max-depth-test");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("max_depth", maxDepth);
        config.put("params", params);
        r.configure(config);
        return r;
    }

    @Test
    public void withinDepth_pass() {
        MaxDepthRule r = rule(3);
        assertEquals(RuleStatus.PASS, r.evaluate(element("A::B::C"), null).status());  // 2 separators
    }

    @Test
    public void atExactDepth_pass() {
        MaxDepthRule r = rule(3);
        assertEquals(RuleStatus.PASS, r.evaluate(element("A::B::C::D"), null).status());  // 3 separators
    }

    @Test
    public void exceedsDepth_fail() {
        MaxDepthRule r = rule(2);
        RuleResult result = r.evaluate(element("A::B::C::D"), null);
        assertEquals(RuleStatus.FAIL, result.status());
        assertTrue(result.message().contains("3"));
        assertTrue(result.message().contains("2"));
    }

    @Test
    public void noOwnerPath_pass() {
        MaxDepthRule r = rule(1);
        ElementRecord noPath = ElementRecord.builder()
                .guid("G1").name("X").metaClass("Class").kind(ElementKind.BLOCK).build();
        assertEquals(RuleStatus.PASS, r.evaluate(noPath, null).status());
    }

    @Test
    public void emptyPath_pass() {
        MaxDepthRule r = rule(0);
        assertEquals(RuleStatus.PASS, r.evaluate(element(""), null).status());
    }

    @Test
    public void singleSegment_pass() {
        MaxDepthRule r = rule(0);
        assertEquals(RuleStatus.PASS, r.evaluate(element("Root"), null).status());
    }

    @Test
    public void deepNesting_fail() {
        MaxDepthRule r = rule(4);
        // 6 separators
        RuleResult result = r.evaluate(element("A::B::C::D::E::F::G"), null);
        assertEquals(RuleStatus.FAIL, result.status());
    }

    @Test
    public void countSeparators_utility() {
        assertEquals(0, MaxDepthRule.countSeparators(""));
        assertEquals(0, MaxDepthRule.countSeparators("Root"));
        assertEquals(1, MaxDepthRule.countSeparators("A::B"));
        assertEquals(3, MaxDepthRule.countSeparators("A::B::C::D"));
        assertEquals(0, MaxDepthRule.countSeparators(null));
    }

    @Test
    public void defaultMaxDepth5() {
        MaxDepthRule r = new MaxDepthRule();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("ruleId", "default-test");
        config.put("params", Collections.emptyMap());
        r.configure(config);
        // 5 separators = at threshold, should pass
        assertEquals(RuleStatus.PASS, r.evaluate(element("A::B::C::D::E::F"), null).status());
        // 6 separators = exceeds, should fail
        assertEquals(RuleStatus.FAIL, r.evaluate(element("A::B::C::D::E::F::G"), null).status());
    }
}
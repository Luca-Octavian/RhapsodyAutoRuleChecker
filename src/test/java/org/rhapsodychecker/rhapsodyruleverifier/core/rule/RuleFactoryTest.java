package org.rhapsodychecker.rhapsodyruleverifier.core.rule;

import org.junit.Test;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.config.TargetSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.AliasKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl.*;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for RuleFactory: verifies every registered RuleType produces the correct
 * Rule implementation and that config is properly passed through.
 */
public class RuleFactoryTest {

    // ---- All registered types produce valid Rule instances ----

    @Test
    public void allRegisteredTypes_createSuccessfully() {
        for (RuleType type : RuleFactory.registeredTypes()) {
            RuleSpec spec = specForType(type);
            Rule rule = RuleFactory.createRule(spec);
            assertNotNull("Rule for " + type + " should not be null", rule);
            assertEquals("test-" + type.name(), rule.id());
        }
    }

    @Test
    public void registeredTypes_containsAll10() {
        Set<RuleType> types = RuleFactory.registeredTypes();
        assertTrue(types.contains(RuleType.REQUIRED_VALUE));
        assertTrue(types.contains(RuleType.REQUIRED_STEREOTYPE));
        assertTrue(types.contains(RuleType.REQUIRED_STEREOTYPE_ONE_OF));
        assertTrue(types.contains(RuleType.NAMING_PATTERN));
        assertTrue(types.contains(RuleType.OWNER_STEREOTYPE_CONSTRAINT));
        assertTrue(types.contains(RuleType.RELATION_EXISTS));
        assertTrue(types.contains(RuleType.FLOW_PROPERTY_CONSTRAINT));
        assertTrue(types.contains(RuleType.UNIQUE_NAME));
        assertTrue(types.contains(RuleType.CHILD_COUNT));
        assertTrue(types.contains(RuleType.MAX_DEPTH));
    }

    @Test
    public void requiredValueRule_producesCorrectType() {
        RuleSpec spec = RuleSpec.builder()
                .id("rv1").type(RuleType.REQUIRED_VALUE).enabled(true)
                .target(TargetSpec.builder().kind(AliasKind.NAME).build())
                .params(singleParam("operator", "eq", "value", "X"))
                .build();
        Rule rule = RuleFactory.createRule(spec);
        assertTrue(rule instanceof RequiredValueRule);
    }

    @Test
    public void requiredStereotypeRule_producesCorrectType() {
        RuleSpec spec = RuleSpec.builder()
                .id("rs1").type(RuleType.REQUIRED_STEREOTYPE).enabled(true)
                .params(singleParam("requiredStereotypes", Arrays.asList("block")))
                .build();
        Rule rule = RuleFactory.createRule(spec);
        assertTrue(rule instanceof RequiredStereotypeRule);
    }

    @Test
    public void namingPatternRule_producesCorrectType() {
        RuleSpec spec = RuleSpec.builder()
                .id("np1").type(RuleType.NAMING_PATTERN).enabled(true)
                .params(singleParam("startsWith", "Sys"))
                .build();
        Rule rule = RuleFactory.createRule(spec);
        assertTrue(rule instanceof NamingPatternRule);
    }

    @Test
    public void createRules_skipsDisabled() {
        RuleSpec enabled = RuleSpec.builder()
                .id("e1").type(RuleType.NAMING_PATTERN).enabled(true)
                .params(singleParam("startsWith", "X"))
                .build();
        RuleSpec disabled = RuleSpec.builder()
                .id("d1").type(RuleType.NAMING_PATTERN).enabled(false)
                .params(singleParam("startsWith", "Y"))
                .build();
        List<Rule> rules = RuleFactory.createRules(Arrays.asList(enabled, disabled));
        assertEquals(1, rules.size());
        assertEquals("e1", rules.get(0).id());
    }

    @Test
    public void configMap_passesRuleIdAndParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("requiredStereotypes", Arrays.asList("sysml"));
        RuleSpec spec = RuleSpec.builder()
                .id("my-rule").type(RuleType.REQUIRED_STEREOTYPE).enabled(true)
                .title("My Title")
                .params(params)
                .build();
        Rule rule = RuleFactory.createRule(spec);
        assertEquals("my-rule", rule.id());
        assertEquals("My Title", rule.title());
    }

    // ---- Helper methods ----

    private static RuleSpec specForType(RuleType type) {
        Map<String, Object> params = new LinkedHashMap<>();
        TargetSpec target = null;
        switch (type) {
            case REQUIRED_VALUE:
                target = TargetSpec.builder().kind(AliasKind.NAME).build();
                params.put("operator", "eq");
                params.put("value", "X");
                break;
            case REQUIRED_STEREOTYPE:
                params.put("requiredStereotypes", Arrays.asList("s"));
                break;
            case REQUIRED_STEREOTYPE_ONE_OF:
                params.put("anyOf", Arrays.asList("a", "b"));
                break;
            case NAMING_PATTERN:
                params.put("startsWith", "Prefix");
                break;
            case OWNER_STEREOTYPE_CONSTRAINT:
                params.put("ownerStereotype", "block");
                params.put("requiredStereotype", "sysml");
                params.put("allowedKinds", Arrays.asList("BLOCK"));
                break;
            case RELATION_EXISTS:
                params.put("relationType", "Dependency");
                break;
            case FLOW_PROPERTY_CONSTRAINT:
                Map<String, Object> typeConstraint = new LinkedHashMap<>();
                typeConstraint.put("nonEmpty", true);
                params.put("type", typeConstraint);
                break;
            case UNIQUE_NAME:
                params.put("scope", "parent");
                break;
            case CHILD_COUNT:
                params.put("child_kind", "BLOCK");
                params.put("min", 1);
                break;
            case MAX_DEPTH:
                params.put("max", 5);
                break;
            default:
                break;
        }
        RuleSpec.Builder b = RuleSpec.builder()
                .id("test-" + type.name())
                .type(type)
                .enabled(true)
                .params(params);
        if (target != null) b.target(target);
        return b.build();
    }

    private static Map<String, Object> singleParam(String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, value);
        return m;
    }

    private static Map<String, Object> singleParam(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }
}
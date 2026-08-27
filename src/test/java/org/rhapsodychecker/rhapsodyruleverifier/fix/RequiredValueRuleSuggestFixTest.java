package org.rhapsodychecker.rhapsodyruleverifier.fix;

import org.junit.Test;
import org.rhapsodychecker.rhapsodyruleverifier.config.TargetSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.AliasKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.EvaluationContext;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.Rule;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl.RequiredValueRule;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.AliasResolver;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.DefaultResolvedValue;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.ResolvedValue;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl.RelationExistsRule;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for {@link RequiredValueRule#suggestFix(ElementRecord, EvaluationContext)}.
 */
public class RequiredValueRuleSuggestFixTest {

    private static ElementRecord element(String guid, String name) {
        return ElementRecord.builder()
                .guid(guid).name(name).metaClass("Class").kind(ElementKind.BLOCK)
                .build();
    }

    private static final EvaluationContext STUB_CONTEXT = new EvaluationContext() {
        @Override
        public AliasResolver aliases() {
            return new AliasResolver() {
                @Override
                public ResolvedValue resolveValue(ElementRecord element, TargetSpec target) {
                    return DefaultResolvedValue.absent();
                }
            };
        }

        @Override
        public int countMatchingRelations(ElementRecord element, RelationExistsRule.RelationQuery query) {
            return 0;
        }

        @Override
        public Optional<Object> getOption(String key) {
            return Optional.empty();
        }

        @Override
        public Optional<ElementRecord> findElementByGuid(String guid) {
            return Optional.empty();
        }
    };

    private static Rule configuredRule(String ruleId, TargetSpec target,
                                       String operator, Object value) {
        RequiredValueRule rule = new RequiredValueRule();
        Map<String, Object> configMap = new LinkedHashMap<String, Object>();
        configMap.put("ruleId", ruleId);
        configMap.put("target", target);
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        if (operator != null) params.put("operator", operator);
        if (value != null) params.put("value", value);
        configMap.put("params", params);
        rule.configure(configMap);
        return rule;
    }

    @Test
    public void suggestFix_eqName_returnSetName() {
        TargetSpec target = TargetSpec.builder().kind(AliasKind.NAME).build();
        Rule rule = configuredRule("name-eq", target, "eq", "ExpectedName");
        ElementRecord el = element("guid-1", "WrongName");

        Optional<FixAction> fix = rule.suggestFix(el, STUB_CONTEXT);

        assertTrue("EQ on NAME should suggest a fix", fix.isPresent());
        assertEquals(FixActionType.SET_NAME, fix.get().actionType());
        assertEquals("ExpectedName", fix.get().newValue());
        assertEquals("guid-1", fix.get().elementGuid());
        assertEquals("name-eq", fix.get().ruleId());
        assertNull("NAME target has no field", fix.get().field());
    }

    @Test
    public void suggestFix_eqDescription_returnSetDescription() {
        TargetSpec target = TargetSpec.builder().kind(AliasKind.DESCRIPTION).build();
        Rule rule = configuredRule("desc-eq", target, "eq", "Required description");
        ElementRecord el = element("guid-2", "MyBlock");

        Optional<FixAction> fix = rule.suggestFix(el, STUB_CONTEXT);

        assertTrue(fix.isPresent());
        assertEquals(FixActionType.SET_DESCRIPTION, fix.get().actionType());
        assertEquals("Required description", fix.get().newValue());
    }

    @Test
    public void suggestFix_eqTaggedValue_returnSetTagValue() {
        TargetSpec target = TargetSpec.builder()
                .kind(AliasKind.TAGGED_VALUE)
                .profileName("MyProfile")
                .tagName("MyTag")
                .build();
        Rule rule = configuredRule("tag-eq", target, "eq", "42");
        ElementRecord el = element("guid-3", "TaggedBlock");

        Optional<FixAction> fix = rule.suggestFix(el, STUB_CONTEXT);

        assertTrue(fix.isPresent());
        assertEquals(FixActionType.SET_TAG_VALUE, fix.get().actionType());
        assertEquals("42", fix.get().newValue());
        assertEquals("MyTag", fix.get().field());
    }

    @Test
    public void suggestFix_neqOperator_returnsWithNullNewValue() {
        TargetSpec target = TargetSpec.builder().kind(AliasKind.NAME).build();
        Rule rule = configuredRule("name-neq", target, "neq", "Forbidden");

        Optional<FixAction> fix = rule.suggestFix(element("g4", "Forbidden"), STUB_CONTEXT);
        assertTrue("NEQ on NAME should still offer a row for user input", fix.isPresent());
        assertNull("NEQ cannot auto-suggest a value", fix.get().newValue());
    }

    @Test
    public void suggestFix_gtOperator_returnsWithNullNewValue() {
        TargetSpec target = TargetSpec.builder()
                .kind(AliasKind.TAGGED_VALUE)
                .profileName("P").tagName("T").build();
        Rule rule = configuredRule("tag-gt", target, "gt", "10");

        Optional<FixAction> fix = rule.suggestFix(element("g5", "Block"), STUB_CONTEXT);
        assertTrue("GT on TAG should still offer a row for user input", fix.isPresent());
        assertNull("GT cannot auto-suggest a value", fix.get().newValue());
    }

    @Test
    public void suggestFix_nonEmpty_returnsWithNullNewValue() {
        TargetSpec target = TargetSpec.builder().kind(AliasKind.NAME).build();
        RequiredValueRule rule = new RequiredValueRule();
        Map<String, Object> configMap = new LinkedHashMap<String, Object>();
        configMap.put("ruleId", "no-op");
        configMap.put("target", target);
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("nonEmpty", Boolean.TRUE);
        configMap.put("params", params);
        rule.configure(configMap);

        Optional<FixAction> fix = rule.suggestFix(element("g", "n"), STUB_CONTEXT);
        assertTrue("nonEmpty on NAME should offer a row for user input", fix.isPresent());
        assertNull("nonEmpty cannot auto-suggest a value", fix.get().newValue());
    }

    @Test
    public void suggestFix_portType_returnsEmpty() {
        TargetSpec target = TargetSpec.builder().kind(AliasKind.PORT_TYPE).build();
        Rule rule = configuredRule("port-eq", target, "eq", "SomeType");

        assertFalse("PORT_TYPE should not be auto-fixable",
                rule.suggestFix(element("g6", "Port1"), STUB_CONTEXT).isPresent());
    }

    @Test
    public void suggestFix_inOperator_returnsWithNullNewValue() {
        TargetSpec target = TargetSpec.builder().kind(AliasKind.NAME).build();
        RequiredValueRule rule = new RequiredValueRule();
        Map<String, Object> configMap = new LinkedHashMap<String, Object>();
        configMap.put("ruleId", "name-in");
        configMap.put("target", target);
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("operator", "in");
        List<String> values = new ArrayList<String>();
        values.add("A");
        values.add("B");
        params.put("values", values);
        configMap.put("params", params);
        rule.configure(configMap);

        Optional<FixAction> fix = rule.suggestFix(element("g", "C"), STUB_CONTEXT);
        assertTrue("IN on NAME should offer a row for user input", fix.isPresent());
        assertNull("IN cannot auto-suggest a value", fix.get().newValue());
    }

    @Test
    public void suggestFix_eqWithNullCompareValue_returnsWithNullNewValue() {
        TargetSpec target = TargetSpec.builder().kind(AliasKind.NAME).build();
        RequiredValueRule rule = new RequiredValueRule();
        Map<String, Object> configMap = new LinkedHashMap<String, Object>();
        configMap.put("ruleId", "null-val");
        configMap.put("target", target);
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("operator", "eq");
        configMap.put("params", params);
        rule.configure(configMap);

        Optional<FixAction> fix = rule.suggestFix(element("g", "n"), STUB_CONTEXT);
        assertTrue("EQ with null value should still offer a row", fix.isPresent());
        assertNull("EQ with null compareValue has no suggestion", fix.get().newValue());
    }
}
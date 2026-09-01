package org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl;

import org.junit.Test;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.*;

import java.util.*;

import static org.junit.Assert.*;

public class UniqueNameRuleTest {

    private static ElementRecord element(String guid, String name, String ownerGuid) {
        return ElementRecord.builder()
                .guid(guid).name(name).metaClass("Class").kind(ElementKind.BLOCK)
                .ownerGuid(ownerGuid).build();
    }

    private static UniqueNameRule rule() {
        UniqueNameRule r = new UniqueNameRule();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("ruleId", "unique-name-test");
        r.configure(config);
        return r;
    }

    private static EvaluationContext contextWithSiblings(final List<ElementRecord> siblings) {
        return new EvaluationContext() {
            @Override public org.rhapsodychecker.rhapsodyruleverifier.core.resolve.AliasResolver aliases() { return null; }
            @Override public int countMatchingRelations(ElementRecord e, RelationExistsRule.RelationQuery q) { return 0; }
            @Override public Optional<Object> getOption(String key) { return Optional.empty(); }
            @Override public Optional<ElementRecord> findElementByGuid(String guid) { return Optional.empty(); }
            @Override public List<ElementRecord> findElementsByOwnerGuid(String ownerGuid) { return siblings; }
        };
    }

    @Test
    public void uniqueNames_pass() {
        ElementRecord e1 = element("G1", "BlockA", "OWNER1");
        ElementRecord e2 = element("G2", "BlockB", "OWNER1");
        List<ElementRecord> siblings = Arrays.asList(e1, e2);
        EvaluationContext ctx = contextWithSiblings(siblings);

        assertEquals(RuleStatus.PASS, rule().evaluate(e1, ctx).status());
        assertEquals(RuleStatus.PASS, rule().evaluate(e2, ctx).status());
    }

    @Test
    public void duplicateNames_fail() {
        ElementRecord e1 = element("G1", "BlockA", "OWNER1");
        ElementRecord e2 = element("G2", "BlockA", "OWNER1");
        List<ElementRecord> siblings = Arrays.asList(e1, e2);
        EvaluationContext ctx = contextWithSiblings(siblings);

        RuleResult r1 = rule().evaluate(e1, ctx);
        assertEquals(RuleStatus.FAIL, r1.status());
        assertTrue(r1.message().contains("BlockA"));
        assertTrue(r1.message().contains("2"));

        assertEquals(RuleStatus.FAIL, rule().evaluate(e2, ctx).status());
    }

    @Test
    public void noOwner_pass() {
        ElementRecord noOwner = ElementRecord.builder()
                .guid("G1").name("X").metaClass("Class").kind(ElementKind.BLOCK).build();
        EvaluationContext ctx = contextWithSiblings(Collections.<ElementRecord>emptyList());
        assertEquals(RuleStatus.PASS, rule().evaluate(noOwner, ctx).status());
    }

    @Test
    public void threeDuplicates_allFail() {
        ElementRecord e1 = element("G1", "Dup", "O");
        ElementRecord e2 = element("G2", "Dup", "O");
        ElementRecord e3 = element("G3", "Dup", "O");
        List<ElementRecord> siblings = Arrays.asList(e1, e2, e3);
        EvaluationContext ctx = contextWithSiblings(siblings);

        for (ElementRecord e : siblings) {
            RuleResult r = rule().evaluate(e, ctx);
            assertEquals(RuleStatus.FAIL, r.status());
            assertTrue(r.message().contains("3"));
        }
    }
}
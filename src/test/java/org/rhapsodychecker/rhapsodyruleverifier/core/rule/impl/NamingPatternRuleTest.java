package org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl;

import org.junit.Test;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.*;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixAction;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixActionType;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for NamingPatternRule: evaluate(), suggestFix(), and edge cases.
 */
public class NamingPatternRuleTest {

    private static ElementRecord element(String name) {
        return ElementRecord.builder()
                .guid("G1").name(name).metaClass("Class").kind(ElementKind.BLOCK).build();
    }

    private static NamingPatternRule rule(String mode, String value) {
        return rule(mode, value, true);
    }

    private static NamingPatternRule rule(String mode, String value, boolean caseSensitive) {
        NamingPatternRule r = new NamingPatternRule();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("ruleId", "naming-test");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(mode, value);
        params.put("caseSensitive", caseSensitive);
        config.put("params", params);
        r.configure(config);
        return r;
    }

    // ---- startsWith ----

    @Test
    public void startsWith_matches_pass() {
        NamingPatternRule r = rule("startsWith", "Sys");
        RuleResult result = r.evaluate(element("SystemBlock"), null);
        assertEquals(RuleStatus.PASS, result.status());
    }

    @Test
    public void startsWith_noMatch_fail() {
        NamingPatternRule r = rule("startsWith", "Sys");
        RuleResult result = r.evaluate(element("MyBlock"), null);
        assertEquals(RuleStatus.FAIL, result.status());
        assertTrue(result.message().contains("start with"));
    }

    @Test
    public void startsWith_caseInsensitive() {
        NamingPatternRule r = rule("startsWith", "sys", false);
        assertEquals(RuleStatus.PASS, r.evaluate(element("SystemBlock"), null).status());
        assertEquals(RuleStatus.PASS, r.evaluate(element("SYSTemBlock"), null).status());
    }

    // ---- endsWith ----

    @Test
    public void endsWith_matches_pass() {
        NamingPatternRule r = rule("endsWith", "Block");
        assertEquals(RuleStatus.PASS, r.evaluate(element("SystemBlock"), null).status());
    }

    @Test
    public void endsWith_noMatch_fail() {
        NamingPatternRule r = rule("endsWith", "Block");
        assertEquals(RuleStatus.FAIL, r.evaluate(element("SystemPort"), null).status());
    }

    // ---- contains ----

    @Test
    public void contains_matches_pass() {
        NamingPatternRule r = rule("contains", "tem");
        assertEquals(RuleStatus.PASS, r.evaluate(element("SystemBlock"), null).status());
    }

    @Test
    public void contains_noMatch_fail() {
        NamingPatternRule r = rule("contains", "xyz");
        assertEquals(RuleStatus.FAIL, r.evaluate(element("SystemBlock"), null).status());
    }

    // ---- appliesTo always true (selector-driven) ----

    @Test
    public void appliesTo_alwaysTrue() {
        NamingPatternRule r = rule("startsWith", "X");
        assertTrue(r.appliesTo(element("anything"), null));
    }

    // ---- suggestFix ----

    @Test
    public void suggestFix_startsWith_prependsPrefix() {
        NamingPatternRule r = rule("startsWith", "Sys_");
        Optional<FixAction> fix = r.suggestFix(element("Block1"), null);
        assertTrue(fix.isPresent());
        assertEquals(FixActionType.SET_NAME, fix.get().actionType());
        assertEquals("Sys_Block1", fix.get().newValue());
        assertEquals("Block1", fix.get().oldValue());
    }

    @Test
    public void suggestFix_startsWith_alreadyMatches_empty() {
        NamingPatternRule r = rule("startsWith", "Sys");
        assertFalse(r.suggestFix(element("SystemBlock"), null).isPresent());
    }

    @Test
    public void suggestFix_endsWith_appendsSuffix() {
        NamingPatternRule r = rule("endsWith", "_v2");
        Optional<FixAction> fix = r.suggestFix(element("Block1"), null);
        assertTrue(fix.isPresent());
        assertEquals("Block1_v2", fix.get().newValue());
    }

    @Test
    public void suggestFix_contains_returnsEmpty() {
        NamingPatternRule r = rule("contains", "mid");
        assertFalse(r.suggestFix(element("Block1"), null).isPresent());
    }

    // ---- Config validation ----

    @Test(expected = IllegalArgumentException.class)
    public void configure_noMode_throws() {
        NamingPatternRule r = new NamingPatternRule();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("ruleId", "bad");
        config.put("params", Collections.emptyMap());
        r.configure(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void configure_multipleModes_throws() {
        NamingPatternRule r = new NamingPatternRule();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("ruleId", "bad");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("startsWith", "A");
        params.put("endsWith", "B");
        config.put("params", params);
        r.configure(config);
    }

    // ---- Details in fail result ----

    @Test
    public void failResult_containsDetails() {
        NamingPatternRule r = rule("startsWith", "Req_");
        RuleResult result = r.evaluate(element("WrongName"), null);
        assertEquals(RuleStatus.FAIL, result.status());
        assertTrue(result.details().isPresent());
        assertEquals("WrongName", result.details().get().get("actualName"));
        assertEquals("startsWith", result.details().get().get("mode"));
        assertEquals("Req_", result.details().get().get("expected"));
    }

    // ---- Edge: element name containing the pattern (Rhapsody may return unexpected values) ----

    @Test
    public void contains_matchesSubstring() {
        // "NoX" contains "X" — verifies contains is substring match
        NamingPatternRule r = rule("contains", "X");
        RuleResult result = r.evaluate(element("NoX"), null);
        assertEquals(RuleStatus.PASS, result.status());
    }

    @Test
    public void contains_caseSensitive_noMatch() {
        // "nox" does not contain uppercase "X" when case-sensitive
        NamingPatternRule r = rule("contains", "X", true);
        RuleResult result = r.evaluate(element("nox"), null);
        assertEquals(RuleStatus.FAIL, result.status());
    }
}
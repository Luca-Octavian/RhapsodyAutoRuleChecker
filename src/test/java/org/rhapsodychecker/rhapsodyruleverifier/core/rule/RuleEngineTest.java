package org.rhapsodychecker.rhapsodyruleverifier.core.rule;

import org.junit.Before;
import org.junit.Test;
import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.config.TargetSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.AliasKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.AliasResolver;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.DefaultResolvedValue;
import org.rhapsodychecker.rhapsodyruleverifier.core.resolve.ResolvedValue;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.impl.RelationExistsRule;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for RuleEngine: end-to-end config → select → evaluate → summary.
 * Uses NamingPatternRule (no Rhapsody dependency) to verify the pipeline.
 */
public class RuleEngineTest {

    private ElementIndex index;
    private EvaluationContext stubContext;

    @Before
    public void setUp() {
        ElementRecord block1 = ElementRecord.builder()
                .guid("G1").name("SysBlock").metaClass("Class").kind(ElementKind.BLOCK)
                .ownerGuid("G-PKG").ownerPath("TopPkg::SubPkg")
                .stereotypes(Arrays.asList("block"))
                .build();
        ElementRecord block2 = ElementRecord.builder()
                .guid("G2").name("OtherBlock").metaClass("Class").kind(ElementKind.BLOCK)
                .ownerGuid("G-PKG").ownerPath("TopPkg::SubPkg")
                .stereotypes(Arrays.asList("block"))
                .build();
        ElementRecord port = ElementRecord.builder()
                .guid("G3").name("DataPort").metaClass("Port").kind(ElementKind.PORT)
                .ownerGuid("G1").ownerPath("TopPkg::SubPkg::SysBlock")
                .build();

        index = ElementIndex.build(Arrays.asList(block1, block2, port));

        stubContext = new EvaluationContext() {
            @Override
            public AliasResolver aliases() {
                return (element, target) -> DefaultResolvedValue.absent();
            }
            @Override
            public int countMatchingRelations(ElementRecord element, RelationExistsRule.RelationQuery query) {
                return 0;
            }
            @Override
            public Optional<Object> getOption(String key) { return Optional.empty(); }
            @Override
            public Optional<ElementRecord> findElementByGuid(String guid) { return Optional.empty(); }
        };
    }

    private RuleCheckerConfig configWith(List<RuleSpec> rules) {
        return RuleCheckerConfig.builder()
                .schemaVersion(1)
                .elementSets(Collections.<String, ElementSetDefinition>emptyMap())
                .rules(rules)
                .build();
    }

    // ---- Basic evaluation: pass and fail ----

    @Test
    public void evaluateAll_namingRule_passAndFail() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("startsWith", "Sys");
        RuleSpec spec = RuleSpec.builder()
                .id("naming-1").type(RuleType.NAMING_PATTERN).enabled(true)
                .appliesToStereotypes(Arrays.asList("block"))
                .params(params)
                .build();

        RuleCheckerConfig cfg = configWith(Arrays.asList(spec));
        ElementSelector selector = new ElementSelector(index, cfg);
        RuleEngine engine = new RuleEngine(cfg, selector, stubContext);

        List<RuleResult> results = engine.evaluateAll();
        assertEquals(2, results.size());

        // SysBlock passes, OtherBlock fails
        int pass = 0, fail = 0;
        for (RuleResult r : results) {
            if (r.status() == RuleStatus.PASS) pass++;
            if (r.status() == RuleStatus.FAIL) fail++;
        }
        assertEquals(1, pass);
        assertEquals(1, fail);
    }

    // ---- EvaluationSummary counts ----

    @Test
    public void evaluateWithSummary_correctCounts() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("startsWith", "Sys");
        RuleSpec spec = RuleSpec.builder()
                .id("n1").type(RuleType.NAMING_PATTERN).enabled(true)
                .appliesToStereotypes(Arrays.asList("block"))
                .params(params)
                .build();

        RuleCheckerConfig cfg = configWith(Arrays.asList(spec));
        ElementSelector selector = new ElementSelector(index, cfg);
        RuleEngine.EvaluationSummary summary = new RuleEngine(cfg, selector, stubContext).evaluateWithSummary();

        assertEquals(2, summary.totalCount());
        assertEquals(1, summary.passCount());
        assertEquals(1, summary.failCount());
        assertEquals(0, summary.skipCount());
    }

    // ---- Disabled rules are skipped ----

    @Test
    public void disabledRule_notEvaluated() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("startsWith", "X");
        RuleSpec disabled = RuleSpec.builder()
                .id("d1").type(RuleType.NAMING_PATTERN).enabled(false)
                .params(params).build();

        RuleCheckerConfig cfg = configWith(Arrays.asList(disabled));
        ElementSelector selector = new ElementSelector(index, cfg);
        List<RuleResult> results = new RuleEngine(cfg, selector, stubContext).evaluateAll();
        assertTrue("Disabled rule should produce no results", results.isEmpty());
    }

    // ---- Scope path filtering ----

    @Test
    public void scopePath_filtersToMatchingOwnerPath() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("startsWith", "Data");
        RuleSpec spec = RuleSpec.builder()
                .id("scoped").type(RuleType.NAMING_PATTERN).enabled(true)
                .params(params).build();

        RuleCheckerConfig cfg = configWith(Arrays.asList(spec));
        ElementSelector selector = new ElementSelector(index, cfg);
        // Scope to SubPkg::SysBlock — only the port is there
        RuleEngine engine = new RuleEngine(cfg, selector, stubContext, "TopPkg::SubPkg::SysBlock");

        List<RuleResult> results = engine.evaluateAll();
        assertEquals(1, results.size());
        assertEquals("G3", results.get(0).elementGuid());
        assertEquals(RuleStatus.PASS, results.get(0).status());
    }

    // ---- Empty model: no candidates ----

    @Test
    public void emptyModel_producesNoResults() {
        ElementIndex emptyIndex = ElementIndex.build(Collections.<ElementRecord>emptyList());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("startsWith", "X");
        RuleSpec spec = RuleSpec.builder()
                .id("r1").type(RuleType.NAMING_PATTERN).enabled(true)
                .appliesToTypes(Arrays.asList("Class"))
                .params(params).build();

        RuleCheckerConfig cfg = configWith(Arrays.asList(spec));
        ElementSelector selector = new ElementSelector(emptyIndex, cfg);
        List<RuleResult> results = new RuleEngine(cfg, selector, stubContext).evaluateAll();
        assertTrue(results.isEmpty());
    }

    // ---- Multiple rules: results from all appear ----

    @Test
    public void multipleRules_allResultsPresent() {
        Map<String, Object> p1 = new LinkedHashMap<>();
        p1.put("startsWith", "Sys");
        RuleSpec r1 = RuleSpec.builder()
                .id("n1").type(RuleType.NAMING_PATTERN).enabled(true)
                .appliesToStereotypes(Arrays.asList("block"))
                .params(p1).build();

        Map<String, Object> p2 = new LinkedHashMap<>();
        p2.put("requiredStereotypes", Arrays.asList("sysml"));
        RuleSpec r2 = RuleSpec.builder()
                .id("s1").type(RuleType.REQUIRED_STEREOTYPE).enabled(true)
                .appliesToStereotypes(Arrays.asList("block"))
                .params(p2).build();

        RuleCheckerConfig cfg = configWith(Arrays.asList(r1, r2));
        ElementSelector selector = new ElementSelector(index, cfg);
        List<RuleResult> results = new RuleEngine(cfg, selector, stubContext).evaluateAll();

        // 2 blocks × 2 rules = 4 results
        assertEquals(4, results.size());

        Set<String> ruleIds = new HashSet<>();
        for (RuleResult r : results) ruleIds.add(r.ruleId());
        assertTrue(ruleIds.contains("n1"));
        assertTrue(ruleIds.contains("s1"));
    }

    // ---- Result fields are properly populated ----

    @Test
    public void resultFields_properlyPopulated() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("startsWith", "Sys");
        RuleSpec spec = RuleSpec.builder()
                .id("field-check").type(RuleType.NAMING_PATTERN).enabled(true)
                .appliesToStereotypes(Arrays.asList("block"))
                .params(params).build();

        RuleCheckerConfig cfg = configWith(Arrays.asList(spec));
        ElementSelector selector = new ElementSelector(index, cfg);
        List<RuleResult> results = new RuleEngine(cfg, selector, stubContext).evaluateAll();

        for (RuleResult r : results) {
            assertEquals("field-check", r.ruleId());
            assertNotNull("elementGuid should not be null", r.elementGuid());
            assertFalse("elementGuid should not be empty", r.elementGuid().isEmpty());
            assertNotNull("status should not be null", r.status());
            // Message should be non-null (even if empty for PASS)
            assertNotNull("message should not be null", r.message());
        }

        // The FAIL result should have meaningful details
        RuleResult failResult = null;
        for (RuleResult r : results) {
            if (r.status() == RuleStatus.FAIL) { failResult = r; break; }
        }
        assertNotNull("Should have a FAIL result", failResult);
        assertTrue("FAIL message should mention the pattern",
                failResult.message().contains("start with") || failResult.message().contains("Sys"));
    }
}
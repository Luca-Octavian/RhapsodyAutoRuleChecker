package org.rhapsodychecker.rhapsodyruleverifier.core.selector;

import org.junit.Before;
import org.junit.Test;
import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for ElementSelector: verifies that inline filters (types, stereotypes,
 * kinds, include/exclude packages) and element set references correctly resolve
 * to the right candidate sets. Also tests graceful behavior when elements are
 * missing (e.g., model lacks certain types).
 */
public class ElementSelectorTest {

    private ElementIndex index;
    private RuleCheckerConfig config;

    @Before
    public void setUp() {
        ElementRecord block1 = ElementRecord.builder()
                .guid("G-B1").name("SysBlock").metaClass("Class").kind(ElementKind.BLOCK)
                .ownerGuid("G-PKG").ownerPath("TopPkg::SubPkg")
                .stereotypes(Arrays.asList("block", "sysml"))
                .build();
        ElementRecord block2 = ElementRecord.builder()
                .guid("G-B2").name("SubBlock").metaClass("Class").kind(ElementKind.BLOCK)
                .ownerGuid("G-B1").ownerPath("TopPkg::SubPkg::SysBlock")
                .stereotypes(Arrays.asList("block"))
                .build();
        ElementRecord port1 = ElementRecord.builder()
                .guid("G-P1").name("DataIn").metaClass("Port").kind(ElementKind.PORT)
                .ownerGuid("G-B1").ownerPath("TopPkg::SubPkg::SysBlock")
                .build();
        ElementRecord req1 = ElementRecord.builder()
                .guid("G-R1").name("Req1").metaClass("Requirement").kind(ElementKind.REQUIREMENT)
                .ownerGuid("G-PKG").ownerPath("TopPkg::ReqPkg")
                .stereotypes(Arrays.asList("functional"))
                .build();

        index = ElementIndex.build(Arrays.asList(block1, block2, port1, req1));

        // Config with one element set
        Map<String, ElementSetDefinition> sets = new LinkedHashMap<>();
        sets.put("blocks-only", ElementSetDefinition.builder()
                .id("blocks-only")
                .types(Arrays.asList("Class"))
                .stereotypes(Arrays.asList("block"))
                .build());
        sets.put("ports-set", ElementSetDefinition.builder()
                .id("ports-set")
                .kinds(Arrays.asList("PORT"))
                .build());

        config = RuleCheckerConfig.builder()
                .schemaVersion(1)
                .elementSets(sets)
                .rules(Collections.<RuleSpec>emptyList())
                .build();
    }

    private ElementSelector selector() {
        return new ElementSelector(index, config);
    }

    // ---- Inline type filter ----

    @Test
    public void inlineFilter_byType_returnsMatchingMetaClass() {
        RuleSpec spec = RuleSpec.builder()
                .id("r1").type(RuleType.NAMING_PATTERN).enabled(true)
                .appliesToTypes(Arrays.asList("Port"))
                .params(singleParam("startsWith", "D"))
                .build();
        List<ElementRecord> candidates = selector().selectCandidates(spec);
        assertEquals(1, candidates.size());
        assertEquals("G-P1", candidates.get(0).guid());
    }

    // ---- Inline stereotype filter ----

    @Test
    public void inlineFilter_byStereotype_returnsMatching() {
        RuleSpec spec = RuleSpec.builder()
                .id("r2").type(RuleType.NAMING_PATTERN).enabled(true)
                .appliesToStereotypes(Arrays.asList("sysml"))
                .params(singleParam("startsWith", "X"))
                .build();
        List<ElementRecord> candidates = selector().selectCandidates(spec);
        assertEquals(1, candidates.size());
        assertEquals("G-B1", candidates.get(0).guid());
    }

    // ---- Element set reference ----

    @Test
    public void elementSetRef_blocksOnly_returnsBothBlocks() {
        RuleSpec spec = RuleSpec.builder()
                .id("r3").type(RuleType.NAMING_PATTERN).enabled(true)
                .appliesToSet("blocks-only")
                .params(singleParam("startsWith", "X"))
                .build();
        List<ElementRecord> candidates = selector().selectCandidates(spec);
        assertEquals(2, candidates.size());
        Set<String> guids = new HashSet<>();
        for (ElementRecord r : candidates) guids.add(r.guid());
        assertTrue(guids.contains("G-B1"));
        assertTrue(guids.contains("G-B2"));
    }

    @Test
    public void elementSetRef_portsSet_returnsPort() {
        RuleSpec spec = RuleSpec.builder()
                .id("r4").type(RuleType.NAMING_PATTERN).enabled(true)
                .appliesToSet("ports-set")
                .params(singleParam("startsWith", "X"))
                .build();
        List<ElementRecord> candidates = selector().selectCandidates(spec);
        assertEquals(1, candidates.size());
        assertEquals("G-P1", candidates.get(0).guid());
    }

    // ---- Nonexistent set returns empty (graceful) ----

    @Test
    public void elementSetRef_nonexistent_returnsEmpty() {
        RuleSpec spec = RuleSpec.builder()
                .id("r5").type(RuleType.NAMING_PATTERN).enabled(true)
                .appliesToSet("nonexistent-set")
                .params(singleParam("startsWith", "X"))
                .build();
        List<ElementRecord> candidates = selector().selectCandidates(spec);
        assertTrue("Nonexistent set should return empty", candidates.isEmpty());
    }

    // ---- Include packages filter ----

    @Test
    public void includePackages_filtersToMatchingOwnerPath() {
        RuleSpec spec = RuleSpec.builder()
                .id("r6").type(RuleType.NAMING_PATTERN).enabled(true)
                .appliesToTypes(Arrays.asList("Class"))
                .appliesToIncludePackages(Arrays.asList("SubPkg::SysBlock"))
                .params(singleParam("startsWith", "X"))
                .build();
        List<ElementRecord> candidates = selector().selectCandidates(spec);
        // Only block2 has ownerPath "TopPkg::SubPkg::SysBlock"
        assertEquals(1, candidates.size());
        assertEquals("G-B2", candidates.get(0).guid());
    }

    // ---- Exclude packages filter ----

    @Test
    public void excludePackages_removesMatchingOwnerPath() {
        RuleSpec spec = RuleSpec.builder()
                .id("r7").type(RuleType.NAMING_PATTERN).enabled(true)
                .appliesToStereotypes(Arrays.asList("block"))
                .appliesToExcludePackages(Arrays.asList("SysBlock"))
                .params(singleParam("startsWith", "X"))
                .build();
        List<ElementRecord> candidates = selector().selectCandidates(spec);
        // block2's ownerPath contains "SysBlock", so excluded; block1 remains
        assertEquals(1, candidates.size());
        assertEquals("G-B1", candidates.get(0).guid());
    }

    // ---- No filters returns all elements ----

    @Test
    public void noFilters_returnsAllElements() {
        RuleSpec spec = RuleSpec.builder()
                .id("r8").type(RuleType.NAMING_PATTERN).enabled(true)
                .params(singleParam("startsWith", "X"))
                .build();
        List<ElementRecord> candidates = selector().selectCandidates(spec);
        assertEquals(4, candidates.size());
    }

    // ---- Empty model: no elements of given type (Rhapsody model may lack certain kinds) ----

    @Test
    public void emptyModel_typeFilter_returnsEmpty() {
        ElementIndex emptyIndex = ElementIndex.build(Collections.<ElementRecord>emptyList());
        RuleCheckerConfig emptyCfg = RuleCheckerConfig.builder()
                .schemaVersion(1)
                .elementSets(Collections.<String, ElementSetDefinition>emptyMap())
                .rules(Collections.<RuleSpec>emptyList())
                .build();
        ElementSelector sel = new ElementSelector(emptyIndex, emptyCfg);
        RuleSpec spec = RuleSpec.builder()
                .id("r9").type(RuleType.NAMING_PATTERN).enabled(true)
                .appliesToTypes(Arrays.asList("Class"))
                .params(singleParam("startsWith", "X"))
                .build();
        assertTrue(sel.selectCandidates(spec).isEmpty());
    }

    // ---- Model without certain element kinds (not an error, just empty) ----

    @Test
    public void kindFilter_unusedKind_returnsEmpty() {
        // No FLOW_PROPERTY in our test data
        RuleCheckerConfig cfgWithKindSet = RuleCheckerConfig.builder()
                .schemaVersion(1)
                .elementSets(Collections.singletonMap("fp-set",
                        ElementSetDefinition.builder().id("fp-set")
                                .kinds(Arrays.asList("FLOW_PROPERTY")).build()))
                .rules(Collections.<RuleSpec>emptyList())
                .build();
        ElementSelector sel = new ElementSelector(index, cfgWithKindSet);
        RuleSpec spec = RuleSpec.builder()
                .id("r10").type(RuleType.NAMING_PATTERN).enabled(true)
                .appliesToSet("fp-set")
                .params(singleParam("startsWith", "X"))
                .build();
        assertTrue(sel.selectCandidates(spec).isEmpty());
    }

    // ---- Set caching: same set resolved twice returns same result ----

    @Test
    public void elementSet_isCached() {
        ElementSelector sel = selector();
        Set<String> first = sel.resolveElementSet("blocks-only");
        Set<String> second = sel.resolveElementSet("blocks-only");
        assertSame("Should return cached instance", first, second);
    }

    private static Map<String, Object> singleParam(String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, value);
        return m;
    }
}
package org.rhapsodychecker.rhapsodyruleverifier.core.index;

import org.junit.Before;
import org.junit.Test;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests that ElementIndex accurately indexes records and that lookups
 * return the correct data — verifying the cache/index produces accurate results.
 */
public class ElementIndexTest {

    private ElementRecord block1, block2, port1, flowProp1;
    private ElementIndex index;

    @Before
    public void setUp() {
        Map<String, String> tags = new HashMap<String, String>();
        tags.put("color", "red");
        tags.put("priority", "high");

        block1 = ElementRecord.builder()
                .guid("G-BLOCK-1").name("SystemBlock").metaClass("Class").kind(ElementKind.BLOCK)
                .ownerGuid("G-PKG-1").ownerPath("TopPkg::SubPkg")
                .stereotypes(Arrays.asList("block", "sysml"))
                .description("The main system block")
                .tagValues(tags)
                .build();

        block2 = ElementRecord.builder()
                .guid("G-BLOCK-2").name("SubBlock").metaClass("Class").kind(ElementKind.BLOCK)
                .ownerGuid("G-BLOCK-1").ownerPath("TopPkg::SubPkg::SystemBlock")
                .stereotypes(Arrays.asList("block"))
                .build();

        port1 = ElementRecord.builder()
                .guid("G-PORT-1").name("DataIn").metaClass("Port").kind(ElementKind.PORT)
                .ownerGuid("G-BLOCK-1").ownerPath("TopPkg::SubPkg::SystemBlock")
                .portDirection("in").portMultiplicity("1")
                .build();

        flowProp1 = ElementRecord.builder()
                .guid("G-FP-1").name("speed").metaClass("Attribute").kind(ElementKind.FLOW_PROPERTY)
                .ownerGuid("G-BLOCK-1").ownerPath("TopPkg::SubPkg::SystemBlock")
                .initialValue("0.0")
                .typeName("Real")
                .build();

        index = ElementIndex.build(Arrays.asList(block1, block2, port1, flowProp1));
    }

    // ---- GUID lookup accuracy ----

    @Test
    public void guidLookup_findsExactRecord() {
        assertTrue(index.repository().get("G-BLOCK-1").isPresent());
        assertEquals("SystemBlock", index.repository().get("G-BLOCK-1").get().name());
    }

    @Test
    public void guidLookup_missingGuid_returnsEmpty() {
        assertFalse(index.repository().get("NONEXISTENT").isPresent());
    }

    @Test
    public void guidLookup_allRecordsPresent() {
        assertTrue(index.repository().get("G-BLOCK-1").isPresent());
        assertTrue(index.repository().get("G-BLOCK-2").isPresent());
        assertTrue(index.repository().get("G-PORT-1").isPresent());
        assertTrue(index.repository().get("G-FP-1").isPresent());
    }

    // ---- Field accuracy after indexing ----

    @Test
    public void indexedRecord_preservesAllFields() {
        ElementRecord r = index.repository().get("G-BLOCK-1").get();
        assertEquals("SystemBlock", r.name());
        assertEquals("Class", r.metaClass());
        assertEquals(ElementKind.BLOCK, r.kind());
        assertEquals("G-PKG-1", r.ownerGuid().get());
        assertEquals("TopPkg::SubPkg", r.ownerPath().get());
        assertEquals("The main system block", r.description().get());
        assertTrue(r.hasStereotype("block"));
        assertTrue(r.hasStereotype("sysml"));
        assertEquals("red", r.tagValues().get("color"));
        assertEquals("high", r.tagValues().get("priority"));
    }

    @Test
    public void indexedRecord_preservesPortFields() {
        ElementRecord r = index.repository().get("G-PORT-1").get();
        assertEquals("in", r.portDirection().get());
        assertEquals("1", r.portMultiplicity().get());
    }

    @Test
    public void indexedRecord_preservesFlowPropertyFields() {
        ElementRecord r = index.repository().get("G-FP-1").get();
        assertEquals("0.0", r.initialValue().get());
        assertEquals("Real", r.typeName().get());
    }

    @Test
    public void indexedRecord_preservesAbsentOptionalFields() {
        ElementRecord r = index.repository().get("G-BLOCK-2").get();
        assertFalse(r.description().isPresent());
        assertFalse(r.typeGuid().isPresent());
        assertFalse(r.typeName().isPresent());
        assertFalse(r.portDirection().isPresent());
        assertFalse(r.initialValue().isPresent());
        assertTrue(r.tagValues().isEmpty());
    }

    // ---- Kind-based indexing ----

    @Test
    public void kindIndex_blocksReturnsBothBlocks() {
        Set<String> blockGuids = index.guidsByKind(ElementKind.BLOCK);
        assertNotNull(blockGuids);
        assertTrue(blockGuids.contains("G-BLOCK-1"));
        assertTrue(blockGuids.contains("G-BLOCK-2"));
        assertEquals(2, blockGuids.size());
    }

    @Test
    public void kindIndex_portReturnsPort() {
        Set<String> portGuids = index.guidsByKind(ElementKind.PORT);
        assertNotNull(portGuids);
        assertTrue(portGuids.contains("G-PORT-1"));
        assertEquals(1, portGuids.size());
    }

    @Test
    public void kindIndex_flowPropertyReturnsFlowProp() {
        Set<String> fpGuids = index.guidsByKind(ElementKind.FLOW_PROPERTY);
        assertNotNull(fpGuids);
        assertTrue(fpGuids.contains("G-FP-1"));
    }

    @Test
    public void kindIndex_unusedKind_returnsEmptyOrNull() {
        Set<String> reqGuids = index.guidsByKind(ElementKind.REQUIREMENT);
        assertTrue(reqGuids == null || reqGuids.isEmpty());
    }

    // ---- Stereotype-based indexing ----

    @Test
    public void stereotypeIndex_blockReturnsCorrectElements() {
        Set<String> guids = index.guidsByStereotype("block", true);
        assertNotNull(guids);
        assertTrue(guids.contains("G-BLOCK-1"));
        assertTrue(guids.contains("G-BLOCK-2"));
    }

    @Test
    public void stereotypeIndex_sysmlOnlyOnBlock1() {
        Set<String> guids = index.guidsByStereotype("sysml", true);
        assertNotNull(guids);
        assertTrue(guids.contains("G-BLOCK-1"));
        assertFalse(guids.contains("G-BLOCK-2"));
    }

    @Test
    public void stereotypeIndex_nonexistentStereotype() {
        Set<String> guids = index.guidsByStereotype("nonexistent", true);
        assertTrue(guids == null || guids.isEmpty());
    }

    // ---- Owner-based indexing ----

    @Test
    public void ownerIndex_block1OwnsPortAndFlowPropAndBlock2() {
        Set<String> children = index.guidsByOwnerGuid("G-BLOCK-1");
        assertNotNull(children);
        assertTrue(children.contains("G-BLOCK-2"));
        assertTrue(children.contains("G-PORT-1"));
        assertTrue(children.contains("G-FP-1"));
    }

    @Test
    public void ownerIndex_leafElementHasNoChildren() {
        Set<String> children = index.guidsByOwnerGuid("G-PORT-1");
        assertTrue(children == null || children.isEmpty());
    }

    // ---- Record count ----

    @Test
    public void totalRecordCount() {
        // Use allRecords or just verify all 4 GUIDs are retrievable
        assertTrue(index.repository().get("G-BLOCK-1").isPresent());
        assertTrue(index.repository().get("G-BLOCK-2").isPresent());
        assertTrue(index.repository().get("G-PORT-1").isPresent());
        assertTrue(index.repository().get("G-FP-1").isPresent());
    }

    // ---- Empty index ----

    @Test
    public void emptyIndex_lookupReturnsEmpty() {
        ElementIndex empty = ElementIndex.build(Collections.<ElementRecord>emptyList());
        assertFalse(empty.repository().get("any").isPresent());
    }
}
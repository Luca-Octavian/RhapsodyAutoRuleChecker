package org.rhapsodychecker.rhapsodyruleverifier.core.model;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ElementRecordTest {

    private ElementRecord.Builder minimalBuilder() {
        return ElementRecord.builder()
                .guid("GUID-001")
                .name("MyBlock")
                .metaClass("Class")
                .kind(ElementKind.BLOCK);
    }

    @Test
    public void minimalBuild_setsRequiredFields() {
        ElementRecord r = minimalBuilder().build();
        assertEquals("GUID-001", r.guid());
        assertEquals("MyBlock", r.name());
        assertEquals("Class", r.metaClass());
        assertEquals(ElementKind.BLOCK, r.kind());
    }

    @Test
    public void optionalFields_defaultToEmpty() {
        ElementRecord r = minimalBuilder().build();
        assertFalse(r.ownerGuid().isPresent());
        assertFalse(r.ownerPath().isPresent());
        assertFalse(r.typeGuid().isPresent());
        assertFalse(r.typeName().isPresent());
        assertFalse(r.description().isPresent());
        assertFalse(r.portDirection().isPresent());
        assertFalse(r.portMultiplicity().isPresent());
        assertFalse(r.initialValue().isPresent());
        assertTrue(r.stereotypes().isEmpty());
        assertTrue(r.tagValues().isEmpty());
    }

    @Test
    public void optionalFields_populated() {
        ElementRecord r = minimalBuilder()
                .ownerGuid("OWNER-1")
                .ownerPath("Pkg::Sub")
                .typeGuid("TYPE-1")
                .typeName("SomeType")
                .description("A description")
                .portDirection("in")
                .portMultiplicity("1")
                .initialValue("42")
                .build();
        assertEquals("OWNER-1", r.ownerGuid().get());
        assertEquals("Pkg::Sub", r.ownerPath().get());
        assertEquals("TYPE-1", r.typeGuid().get());
        assertEquals("SomeType", r.typeName().get());
        assertEquals("A description", r.description().get());
        assertEquals("in", r.portDirection().get());
        assertEquals("1", r.portMultiplicity().get());
        assertEquals("42", r.initialValue().get());
    }

    @Test
    public void emptyStrings_treatedAsAbsent() {
        ElementRecord r = minimalBuilder()
                .description("")
                .ownerGuid("  ")
                .build();
        assertFalse(r.description().isPresent());
        assertFalse(r.ownerGuid().isPresent());
    }

    @Test
    public void stereotypes_immutable() {
        ElementRecord r = minimalBuilder()
                .stereotypes(Arrays.asList("block", "sysml"))
                .build();
        assertEquals(2, r.stereotypes().size());
        assertTrue(r.hasStereotype("block"));
        assertTrue(r.hasStereotype("sysml"));
        assertFalse(r.hasStereotype("other"));
    }

    @Test
    public void stereotypes_caseInsensitiveLookup() {
        ElementRecord r = minimalBuilder()
                .stereotypes(Arrays.asList("Block"))
                .build();
        assertTrue(r.hasStereotypeIgnoreCase("block"));
        assertTrue(r.hasStereotypeIgnoreCase("BLOCK"));
        assertTrue(r.hasStereotypeIgnoreCase("Block"));
        assertFalse(r.hasStereotypeIgnoreCase("other"));
    }

    @Test
    public void stereotypes_nullsAndBlanksFiltered() {
        ElementRecord r = minimalBuilder()
                .stereotypes(Arrays.asList("valid", null, "", "  "))
                .build();
        assertEquals(1, r.stereotypes().size());
        assertTrue(r.hasStereotype("valid"));
    }

    @Test
    public void tagValues_preserved() {
        Map<String, String> tags = new HashMap<String, String>();
        tags.put("color", "red");
        tags.put("size", "large");
        ElementRecord r = minimalBuilder().tagValues(tags).build();
        assertEquals("red", r.tagValues().get("color"));
        assertEquals("large", r.tagValues().get("size"));
        assertEquals(2, r.tagValues().size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void tagValues_unmodifiable() {
        Map<String, String> tags = new HashMap<String, String>();
        tags.put("k", "v");
        ElementRecord r = minimalBuilder().tagValues(tags).build();
        r.tagValues().put("new", "val");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void stereotypes_unmodifiable() {
        ElementRecord r = minimalBuilder()
                .stereotypes(Arrays.asList("s1"))
                .build();
        r.stereotypes().add("s2");
    }

    @Test
    public void equality_byGuid() {
        ElementRecord a = minimalBuilder().build();
        ElementRecord b = minimalBuilder().name("DifferentName").build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void inequality_differentGuid() {
        ElementRecord a = minimalBuilder().build();
        ElementRecord b = minimalBuilder().guid("GUID-002").build();
        assertNotEquals(a, b);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullGuid_throws() {
        minimalBuilder().guid(null).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankName_throws() {
        minimalBuilder().name("  ").build();
    }

    @Test(expected = NullPointerException.class)
    public void nullKind_throws() {
        minimalBuilder().kind(null).build();
    }

    @Test
    public void withOwnerPath_copiesAllFields() {
        ElementRecord orig = minimalBuilder()
                .stereotypes(Arrays.asList("s1"))
                .description("desc")
                .build();
        ElementRecord copy = orig.withOwnerPath("New::Path");
        assertEquals("New::Path", copy.ownerPath().get());
        assertEquals(orig.guid(), copy.guid());
        assertEquals(orig.name(), copy.name());
        assertEquals(orig.stereotypes(), copy.stereotypes());
        assertEquals(orig.description(), copy.description());
    }

    @Test
    public void withKindAndType_changesKindAndType() {
        ElementRecord orig = minimalBuilder().build();
        ElementRecord copy = orig.withKindAndType(ElementKind.PART, "TG-1", "PartType");
        assertEquals(ElementKind.PART, copy.kind());
        assertEquals("TG-1", copy.typeGuid().get());
        assertEquals("PartType", copy.typeName().get());
        assertEquals(orig.guid(), copy.guid());
    }

    @Test
    public void toString_containsKeyInfo() {
        ElementRecord r = minimalBuilder().build();
        String s = r.toString();
        assertTrue(s.contains("GUID-001"));
        assertTrue(s.contains("MyBlock"));
        assertTrue(s.contains("BLOCK"));
    }
}
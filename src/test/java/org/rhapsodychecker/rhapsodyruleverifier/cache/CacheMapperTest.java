package org.rhapsodychecker.rhapsodyruleverifier.cache;

import org.junit.Test;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests round-trip equivalence of CacheMapper conversions.
 */
public class CacheMapperTest {

    @Test
    public void roundTrip_preservesAllFields() {
        ElementRecord original = ElementRecord.builder()
                .guid("GUID-123")
                .name("MyBlock")
                .metaClass("Class")
                .kind(ElementKind.BLOCK)
                .description("A test block")
                .ownerGuid("OWNER-1")
                .ownerPath("Root::Pkg1")
                .typeGuid("TYPE-1")
                .typeName("SomeType")
                .stereotypes(new LinkedHashSet<>(Arrays.asList("Block", "SysML")))
                .portDirection("in")
                .portMultiplicity("1")
                .initialValue("42")
                .tagValues(mapOf("tag1", "val1", "tag2", "val2"))
                .build();

        CachedElement cached = CacheMapper.toCachedElement(original);
        ElementRecord roundTripped = CacheMapper.toElementRecord(cached);

        assertEquals(original.guid(), roundTripped.guid());
        assertEquals(original.name(), roundTripped.name());
        assertEquals(original.metaClass(), roundTripped.metaClass());
        assertEquals(original.kind(), roundTripped.kind());
        assertEquals(original.description(), roundTripped.description());
        assertEquals(original.ownerGuid(), roundTripped.ownerGuid());
        assertEquals(original.ownerPath(), roundTripped.ownerPath());
        assertEquals(original.typeGuid(), roundTripped.typeGuid());
        assertEquals(original.typeName(), roundTripped.typeName());
        assertEquals(original.stereotypes(), roundTripped.stereotypes());
        assertEquals(original.portDirection(), roundTripped.portDirection());
        assertEquals(original.portMultiplicity(), roundTripped.portMultiplicity());
        assertEquals(original.initialValue(), roundTripped.initialValue());
        assertEquals(original.tagValues(), roundTripped.tagValues());
    }

    @Test
    public void roundTrip_handlesNullOptionalFields() {
        ElementRecord original = ElementRecord.builder()
                .guid("GUID-456")
                .name("MinimalElement")
                .metaClass("Package")
                .kind(ElementKind.PACKAGE)
                .build();

        CachedElement cached = CacheMapper.toCachedElement(original);
        ElementRecord roundTripped = CacheMapper.toElementRecord(cached);

        assertEquals("GUID-456", roundTripped.guid());
        assertEquals("MinimalElement", roundTripped.name());
        assertFalse(roundTripped.description().isPresent());
        assertFalse(roundTripped.ownerGuid().isPresent());
        assertTrue(roundTripped.stereotypes().isEmpty());
        assertTrue(roundTripped.tagValues().isEmpty());
    }

    @Test
    public void toCachedElement_setsCorrectFields() {
        ElementRecord record = ElementRecord.builder()
                .guid("G1")
                .name("Test")
                .metaClass("Class")
                .kind(ElementKind.BLOCK)
                .description("desc")
                .build();

        CachedElement ce = CacheMapper.toCachedElement(record);
        assertEquals("G1", ce.getGuid());
        assertEquals("Test", ce.getName());
        assertEquals("Class", ce.getMetaClass());
        assertEquals("BLOCK", ce.getKind());
        assertEquals("desc", ce.getDescription());
    }

    @Test
    public void toElementRecord_setsCorrectFields() {
        CachedElement ce = new CachedElement();
        ce.setGuid("G2");
        ce.setName("Elem");
        ce.setMetaClass("Package");
        ce.setKind("PACKAGE");
        ce.setDescription("pkg desc");

        ElementRecord rec = CacheMapper.toElementRecord(ce);
        assertEquals("G2", rec.guid());
        assertEquals("Elem", rec.name());
        assertEquals("Package", rec.metaClass());
        assertEquals(ElementKind.PACKAGE, rec.kind());
        assertEquals("pkg desc", rec.description().orElse(null));
    }

    private static Map<String, String> mapOf(String k1, String v1, String k2, String v2) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }
}
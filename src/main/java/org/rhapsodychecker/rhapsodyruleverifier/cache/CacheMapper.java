package org.rhapsodychecker.rhapsodyruleverifier.cache;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

/**
 * Single source of truth for converting between the domain {@link ElementRecord}
 * and the cache DTO {@link CachedElement}.
 *
 * <p>Previously this conversion was duplicated in {@code ModelCacheManager} and
 * {@code IncrementalCacheUpdater}, creating a drift risk whenever a field was
 * added to either type.
 */
public final class CacheMapper {

    private CacheMapper() {}

    /** Convert a domain record to a cache DTO for serialization. */
    public static CachedElement toCachedElement(ElementRecord r) {
        CachedElement c = new CachedElement();
        c.setGuid(r.guid());
        c.setName(r.name());
        c.setMetaClass(r.metaClass());
        c.setKind(r.kind().name());
        c.setDescription(r.description().orElse(null));
        c.setOwnerGuid(r.ownerGuid().orElse(null));
        c.setOwnerPath(r.ownerPath().orElse(null));
        c.setTypeGuid(r.typeGuid().orElse(null));
        c.setTypeName(r.typeName().orElse(null));
        c.setStereotypes(r.stereotypes().isEmpty() ? null : new LinkedHashSet<String>(r.stereotypes()));
        c.setPortDirection(r.portDirection().orElse(null));
        c.setPortMultiplicity(r.portMultiplicity().orElse(null));
        c.setInitialValue(r.initialValue().orElse(null));
        c.setTagValues(r.tagValues().isEmpty() ? null : new LinkedHashMap<String, String>(r.tagValues()));
        return c;
    }

    /** Convert a cache DTO back to a domain record after deserialization. */
    public static ElementRecord toElementRecord(CachedElement c) {
        return ElementRecord.builder()
                .guid(c.getGuid())
                .name(c.getName())
                .metaClass(c.getMetaClass())
                .kind(ElementKind.valueOf(c.getKind()))
                .description(c.getDescription())
                .ownerGuid(c.getOwnerGuid())
                .ownerPath(c.getOwnerPath())
                .typeGuid(c.getTypeGuid())
                .typeName(c.getTypeName())
                .stereotypes(c.getStereotypes() != null ? c.getStereotypes() : Collections.<String>emptySet())
                .portDirection(c.getPortDirection())
                .portMultiplicity(c.getPortMultiplicity())
                .initialValue(c.getInitialValue())
                .tagValues(c.getTagValues() != null ? c.getTagValues() : Collections.<String, String>emptyMap())
                .build();
    }
}
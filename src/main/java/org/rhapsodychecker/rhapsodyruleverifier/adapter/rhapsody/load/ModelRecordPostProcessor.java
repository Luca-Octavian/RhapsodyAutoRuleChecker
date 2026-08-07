package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.load;

import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPInstance;
import com.telelogic.rhapsody.core.IRPModelElement;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelLoader;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.util.*;

/**
 * Pure/local record enrichment performed after COM element hydration.
 */
public final class ModelRecordPostProcessor {

    private ModelRecordPostProcessor() {}

    public static void resolveOwnerPaths(List<ElementRecord> records) {
        Map<String, String> names = new HashMap<String, String>(records.size());
        Map<String, String> owners = new HashMap<String, String>(records.size());
        for (ElementRecord record : records) {
            names.put(record.guid(), record.name());
            if (record.ownerGuid().isPresent()) {
                owners.put(record.guid(), record.ownerGuid().get());
            }
        }

        Map<String, String> cache = new HashMap<String, String>(2048);
        for (int i = 0; i < records.size(); i++) {
            ElementRecord record = records.get(i);
            String ownerGuid = record.ownerGuid().orElse(null);
            if (ownerGuid == null) continue;

            String path = buildOwnerPath(ownerGuid, names, owners, cache);
            if (path != null) records.set(i, record.withOwnerPath(path));
        }
    }

    /**
     * Second-pass classification for Object elements (Rhapsody "Parts").
     * Any Object with a resolved type (IRPInstance.getOtherClass() != null) is
     * reclassified as PART, regardless of its owner.  Elements already classified
     * as PART (e.g. via a Part stereotype) are left untouched.
     */
    public static void classifyTypedObjects(
            List<ElementRecord> records,
            Map<String, IRPModelElement> handlesByGuid) {

        for (int i = 0; i < records.size(); i++) {
            ElementRecord record = records.get(i);
            if (!"Object".equals(record.metaClass())) continue;
            if (record.kind() == org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind.PART) continue;

            String typeGuid = record.typeGuid().orElse(null);
            String typeName = record.typeName().orElse(null);
            if (typeGuid == null || typeGuid.isEmpty()) {
                IRPModelElement handle = handlesByGuid.get(record.guid());
                if (handle instanceof IRPInstance) {
                    try {
                        IRPClassifier type = ((IRPInstance) handle).getOtherClass();
                        if (type != null) {
                            typeGuid = RhapsodyModelLoader.safeStr(type.getGUID());
                            typeName = RhapsodyModelLoader.safeStr(type.getName());
                        }
                    } catch (Throwable ignored) {}
                }
            }
            if (typeGuid == null || typeGuid.isEmpty()) continue;

            records.set(i, record.withKindAndType(
                    org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind.PART,
                    typeGuid, typeName));
        }
    }

    public static String buildOwnerPath(
            String ownerGuid,
            Map<String, String> names,
            Map<String, String> owners,
            Map<String, String> cache) {

        if (ownerGuid == null || ownerGuid.isEmpty()) return null;
        if (cache.containsKey(ownerGuid)) return cache.get(ownerGuid);

        Deque<String> parts = new ArrayDeque<String>();
        Set<String> visited = new HashSet<String>();
        String current = ownerGuid;
        while (current != null && !current.isEmpty() && visited.add(current)) {
            String name = names.get(current);
            if (name == null) break;
            parts.addFirst(name);
            current = owners.get(current);
        }

        String path = join(parts);
        cache.put(ownerGuid, path);
        return path;
    }

    private static String join(Deque<String> parts) {
        if (parts.isEmpty()) return null;
        StringBuilder value = new StringBuilder();
        for (String part : parts) {
            if (value.length() > 0) value.append("::");
            value.append(part);
        }
        return value.toString();
    }
}
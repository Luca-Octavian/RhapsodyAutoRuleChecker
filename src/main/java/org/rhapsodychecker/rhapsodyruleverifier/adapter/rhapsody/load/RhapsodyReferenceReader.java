package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.load;

import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPModelElement;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelLoader;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.profiler.ComLoadDiagnostics;

import java.util.*;

/**
 * Builds the incoming-reference index after base element hydration.
 *
 * <p>Reference GUIDs are always read from Rhapsody. Metaclass and stereotype
 * metadata is reused from the complete model scan when available, with direct
 * COM reads retained as a lossless fallback for external/unscanned references.
 */
public final class RhapsodyReferenceReader {

    private final ComLoadDiagnostics diagnostics;

    public RhapsodyReferenceReader(ComLoadDiagnostics diagnostics) {
        this.diagnostics = diagnostics != null
                ? diagnostics : ComLoadDiagnostics.disabled();
    }

    public Map<String, List<RhapsodyModelSnapshot.ReferenceInfo>> readAll(
            List<ElementRecord> records,
            Map<String, IRPModelElement> handlesByGuid) {

        Map<String, ElementMetadata> metadata = buildMetadata(records);
        Map<String, List<RhapsodyModelSnapshot.ReferenceInfo>> result =
                new HashMap<String, List<RhapsodyModelSnapshot.ReferenceInfo>>();

        for (ElementRecord record : records) {
            IRPModelElement element = handlesByGuid.get(record.guid());
            if (element == null) continue;
            readElementReferences(element, record.guid(), metadata, result);
        }
        return result;
    }

    public void readElementReferences(
            IRPModelElement element,
            String ownerGuid,
            Map<String, ElementMetadata> metadata,
            Map<String, List<RhapsodyModelSnapshot.ReferenceInfo>> destination) {

        long totalStarted = diagnostics.start();
        try {
            long collectionStarted = diagnostics.start();
            IRPCollection references = element.getReferences();
            diagnostics.success("references collection", collectionStarted);

            long countStarted = diagnostics.start();
            int count = references != null ? references.getCount() : 0;
            diagnostics.success("references count", countStarted);
            if (count == 0) {
                diagnostics.success("incoming references", totalStarted);
                return;
            }

            List<RhapsodyModelSnapshot.ReferenceInfo> values =
                    new ArrayList<RhapsodyModelSnapshot.ReferenceInfo>(count);
            for (int i = 1; i <= count; i++) {
                long itemStarted = diagnostics.start();
                Object value = references.getItem(i);
                diagnostics.success("reference item", itemStarted);
                if (!(value instanceof IRPModelElement)) continue;

                IRPModelElement reference = (IRPModelElement) value;
                long guidStarted = diagnostics.start();
                String guid = RhapsodyModelLoader.safeStr(reference.getGUID());
                diagnostics.success("reference GUID", guidStarted);
                if (guid.isEmpty()) continue;

                ElementMetadata known = metadata != null ? metadata.get(guid) : null;
                if (known != null) {
                    values.add(new RhapsodyModelSnapshot.ReferenceInfo(
                            guid, known.metaClass, known.stereotypes));
                } else {
                    long metadataStarted = diagnostics.start();
                    String metaClass = RhapsodyModelLoader.safeStr(reference.getMetaClass());
                    Set<String> stereotypes =
                            RhapsodyModelLoader.readStereotypeNames(reference);
                    diagnostics.success("reference metadata fallback", metadataStarted);
                    values.add(new RhapsodyModelSnapshot.ReferenceInfo(
                            guid, metaClass, stereotypes));
                }
            }

            if (!values.isEmpty()) destination.put(ownerGuid, values);
            diagnostics.success("incoming references", totalStarted);
        } catch (Throwable error) {
            diagnostics.failure("incoming references", totalStarted);
        }
    }

    public static Map<String, ElementMetadata> buildMetadata(
            List<ElementRecord> records) {
        Map<String, ElementMetadata> result =
                new HashMap<String, ElementMetadata>(records.size());
        for (ElementRecord record : records) {
            result.put(record.guid(), new ElementMetadata(
                    record.metaClass(), record.stereotypes()));
        }
        return result;
    }

    public static final class ElementMetadata {
        private final String metaClass;
        private final Set<String> stereotypes;

        public ElementMetadata(String metaClass, Set<String> stereotypes) {
            this.metaClass = metaClass != null ? metaClass : "";
            this.stereotypes = stereotypes != null
                    ? stereotypes : Collections.<String>emptySet();
        }

        public String metaClass() { return metaClass; }
        public Set<String> stereotypes() { return stereotypes; }
    }
}
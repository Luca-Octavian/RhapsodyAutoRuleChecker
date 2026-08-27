
package org.rhapsodychecker.rhapsodyruleverifier.core.index;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.util.*;

public final class ElementRepository {
    private final Map<String, ElementRecord> byGuid;
    private List<ElementRecord> all;

    public ElementRepository(Collection<ElementRecord> records) {
        Objects.requireNonNull(records, "records");
        Map<String, ElementRecord> map = new LinkedHashMap<>(records.size());
        for (ElementRecord r : records) {
            if (r == null || r.guid() == null) continue;
            map.put(r.guid(), r);
        }
        this.byGuid = map; // mutable internally for patching after fix-apply
        this.all = Collections.unmodifiableList(new ArrayList<>(map.values()));
    }

    public Optional<ElementRecord> get(String guid) {
        return Optional.ofNullable(byGuid.get(guid));
    }

    public List<ElementRecord> allRecords() {
        return all;
    }

    public Map<String, ElementRecord> asMap() {
        return Collections.unmodifiableMap(byGuid);
    }

    /**
     * Replace a record in-place (by GUID). Used to patch the in-memory model
     * after fixes are applied to Rhapsody, so re-evaluation reflects the changes.
     */
    public void replace(ElementRecord updated) {
        Objects.requireNonNull(updated, "updated");
        if (byGuid.containsKey(updated.guid())) {
            byGuid.put(updated.guid(), updated);
            this.all = Collections.unmodifiableList(new ArrayList<>(byGuid.values()));
        }
    }
}

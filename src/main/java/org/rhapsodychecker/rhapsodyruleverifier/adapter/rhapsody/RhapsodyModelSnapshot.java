package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody;

import com.telelogic.rhapsody.core.IRPModelElement;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable container for the loaded model state.
 * Exposes:
 * - tool-agnostic records for rules/UI/export
 * - GUID -> IRPModelElement map for adapter/resolver access
 */
public final class RhapsodyModelSnapshot {

    private final List<ElementRecord> records;
    private final Map<String, IRPModelElement> handleByGuid;

    public RhapsodyModelSnapshot(List<ElementRecord> records,
                                 Map<String, IRPModelElement> handleByGuid) {
        this.records = Objects.requireNonNull(records, "records");
        this.handleByGuid = Objects.requireNonNull(handleByGuid, "handleByGuid");
    }

    public List<ElementRecord> records() {
        return records;
    }

    public Map<String, IRPModelElement> handleByGuid() {
        return handleByGuid;
    }
}
package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.load;

import com.telelogic.rhapsody.core.*;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelLoader;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.profiler.ComLoadDiagnostics;

import java.util.*;

/**
 * Hydrates one complete model element and its owned relation metadata.
 */
public final class RhapsodyElementReader {

    private final ComLoadDiagnostics diagnostics;

    public RhapsodyElementReader(ComLoadDiagnostics diagnostics) {
        this.diagnostics = diagnostics != null
                ? diagnostics : ComLoadDiagnostics.disabled();
    }

    public ElementRecord read(
            IRPModelElement element,
            Map<String, List<RhapsodyModelSnapshot.RelationInfo>> relationsByOwner) {

        if (element == null) return null;

        long identityStarted = diagnostics.start();
        String guid = RhapsodyModelLoader.safeStr(element.getGUID());
        String name = RhapsodyModelLoader.safeStr(element.getName());
        String metaClass = RhapsodyModelLoader.safeStr(element.getMetaClass());
        diagnostics.success("identity fields", identityStarted);
        if (guid.isEmpty() || name.isEmpty()) return null;

        String ownerGuid = readOwnerGuid(element);

        long stereotypesStarted = diagnostics.start();
        Set<String> stereotypes =
                RhapsodyModelLoader.readStereotypeNames(element);
        diagnostics.success("stereotypes", stereotypesStarted);

        long descriptionStarted = diagnostics.start();
        String description =
                RhapsodyModelLoader.safeGetDescription(element);
        diagnostics.success("description", descriptionStarted);

        long tagsStarted = diagnostics.start();
        Map<String, String> tags = RhapsodyTagReader.readLocalTags(element);
        diagnostics.success("local tags", tagsStarted);

        SpecializedFields specialized = readSpecializedFields(element, tags);
        tags = specialized.tags;

        ElementKind kind = RhapsodyModelLoader.classify(metaClass, stereotypes);
        kind = refineConnectorKind(element, kind);
        if (specialized.flowProperty && kind == ElementKind.OTHER) {
            kind = ElementKind.FLOW_PROPERTY;
        }
        if (kind == ElementKind.OTHER
                && "InterfaceBlock".equals(
                        RhapsodyModelLoader.safeGetUserDefinedMetaClass(element))) {
            kind = ElementKind.INTERFACE_BLOCK;
        }
        if (kind == ElementKind.OTHER && "Object".equals(metaClass)
                && specialized.typeGuid != null && !specialized.typeGuid.isEmpty()) {
            kind = ElementKind.PART;
        }

        if (relationsByOwner != null) {
            RhapsodyModelLoader.collectRelation(
                    metaClass, guid, ownerGuid, stereotypes,
                    element, relationsByOwner);
        }

        String portDirection = null;
        String portMultiplicity = null;
        String typeGuid = specialized.typeGuid;
        String typeName = specialized.typeName;
        if (kind.isPortKind()) {
            RhapsodyModelLoader.PortFields ports =
                    RhapsodyModelLoader.resolvePortFields(
                            element, typeGuid, typeName);
            portDirection = ports.portDirection;
            portMultiplicity = ports.portMultiplicity;
            typeGuid = ports.typeGuid;
            typeName = ports.typeName;
        }

        return ElementRecord.builder()
                .guid(guid)
                .name(name)
                .metaClass(metaClass)
                .kind(kind)
                .ownerGuid(ownerGuid)
                .ownerPath(null)
                .stereotypes(stereotypes)
                .typeGuid(typeGuid)
                .typeName(typeName)
                .description(description)
                .portDirection(portDirection)
                .portMultiplicity(portMultiplicity)
                .initialValue(specialized.initialValue)
                .tagValues(tags)
                .build();
    }

    /**
     * Disambiguates connector/link kinds using the live API type, which is
     * authoritative where the metaClass string alone is not.
     *
     * <p>{@code IRPLink} (metaClass "Link") is a structural link between
     * Parts/Ports → {@code LINK}.
     *
     * <p>{@code IRPConnector} (metaClass "Connector") is a statechart/activity
     * pseudostate. Only the {@code Junction} connector type corresponds to
     * Rhapsody's user-visible "Connector" category (the elements returned by
     * Ctrl+F search for "Connector"). Every other connector type (Condition,
     * Diagram, EnterExit, Fork, History, Join, Termination, InPin, OutPin,
     * InOutPin) remains {@code STATE_CONNECTOR}.
     *
     * <p>Elements that are neither keep the metaClass-based classification.
     */
    private ElementKind refineConnectorKind(
            IRPModelElement element, ElementKind kind) {

        if (element instanceof IRPLink) {
            return ElementKind.LINK;
        }
        if (element instanceof IRPConnector) {
            try {
                String connectorType = ((IRPConnector) element).getConnectorType();
                if (connectorType != null
                        && "junction".equalsIgnoreCase(connectorType.trim())) {
                    return ElementKind.CONNECTOR;
                }
            } catch (Throwable ignored) { /* fall through to STATE_CONNECTOR */ }
            return ElementKind.STATE_CONNECTOR;
        }
        return kind;
    }

    private String readOwnerGuid(IRPModelElement element) {
        long started = diagnostics.start();
        try {
            IRPModelElement owner = element.getOwner();
            String guid = owner != null && !(owner instanceof IRPProject)
                    ? RhapsodyModelLoader.safeStr(owner.getGUID()) : null;
            diagnostics.success("owner", started);
            return guid;
        } catch (Throwable error) {
            diagnostics.failure("owner", started);
            return null;
        }
    }

    private SpecializedFields readSpecializedFields(
            IRPModelElement element, Map<String, String> tags) {

        long started = diagnostics.start();
        String typeGuid = null;
        String typeName = null;
        String initialValue = null;
        boolean flowProperty = false;

        if (element instanceof IRPAttribute) {
            IRPClassifier type = RhapsodyModelLoader.safeAttributeType(
                    (IRPAttribute) element);
            if (type != null) {
                typeGuid = RhapsodyModelLoader.safeStr(type.getGUID());
                typeName = RhapsodyModelLoader.safeStr(type.getName());
            }

            if ("FlowProperty".equals(
                    RhapsodyModelLoader.safeGetUserDefinedMetaClass(element))) {
                flowProperty = true;
                initialValue = RhapsodyModelLoader.safeGetDefaultValue(
                        (IRPAttribute) element);
                Map<String, String> allTags =
                        RhapsodyTagReader.readAllTags(element);
                if (!allTags.isEmpty()) {
                    Map<String, String> merged =
                            new LinkedHashMap<String, String>(tags);
                    merged.putAll(allTags);
                    tags = merged;
                }
            }
        } else if (element instanceof IRPPort) {
            IRPClassifier type =
                    RhapsodyModelLoader.safePortType((IRPPort) element);
            if (type != null) {
                typeGuid = RhapsodyModelLoader.safeStr(type.getGUID());
                typeName = RhapsodyModelLoader.safeStr(type.getName());
            }
        } else if (element instanceof IRPInstance) {
            try {
                IRPClassifier type = ((IRPInstance) element).getOtherClass();
                if (type != null) {
                    typeGuid = RhapsodyModelLoader.safeStr(type.getGUID());
                    typeName = RhapsodyModelLoader.safeStr(type.getName());
                }
            } catch (Throwable ignored) { /* ignore */ }
        }

        diagnostics.success("type/port preparation", started);
        return new SpecializedFields(
                typeGuid, typeName, initialValue, flowProperty, tags);
    }

    private static final class SpecializedFields {
        private final String typeGuid;
        private final String typeName;
        private final String initialValue;
        private final boolean flowProperty;
        private final Map<String, String> tags;

        private SpecializedFields(
                String typeGuid, String typeName, String initialValue,
                boolean flowProperty, Map<String, String> tags) {
            this.typeGuid = typeGuid;
            this.typeName = typeName;
            this.initialValue = initialValue;
            this.flowProperty = flowProperty;
            this.tags = tags;
        }
    }
}
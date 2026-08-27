package org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody;

import com.telelogic.rhapsody.core.*;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.fix.*;

import java.util.List;
import java.util.logging.Logger;

/**
 * Applies {@link FixAction}s to a live Rhapsody model via the COM API.
 * Each operation reads the current value before writing (for rollback journaling).
 * Does NOT auto-save — the user must save the Rhapsody project manually.
 */
public final class RhapsodyFixExecutor implements FixExecutor {

    private static final Logger LOG = Logger.getLogger(RhapsodyFixExecutor.class.getName());

    private final IRPProject project;

    public RhapsodyFixExecutor(IRPProject project) {
        if (project == null) throw new IllegalStateException("No active Rhapsody project");
        this.project = project;
    }

    public RhapsodyFixExecutor() {
        this(RhapsodyConnectionManager.getInstance().getProject());
    }

    @Override
    public void apply(FixEntry entry) {
        FixAction action = entry.action();
        try {
            IRPModelElement element = project.findElementByGUID(action.elementGuid());
            if (element == null) {
                entry.markFailed("Element not found in Rhapsody: GUID=" + action.elementGuid());
                return;
            }

            switch (action.actionType()) {
                case SET_NAME:
                    applySetName(entry, element);
                    break;
                case SET_DESCRIPTION:
                    applySetDescription(entry, element);
                    break;
                case SET_TAG_VALUE:
                    applySetTagValue(entry, element, action);
                    break;
                case ADD_STEREOTYPE:
                    applyAddStereotype(entry, element, action);
                    break;
                case REMOVE_STEREOTYPE:
                    applyRemoveStereotype(entry, element, action);
                    break;
                case SET_INITIAL_VALUE:
                    applySetInitialValue(entry, element);
                    break;
                default:
                    entry.markFailed("Unsupported action type: " + action.actionType());
            }
        } catch (Exception e) {
            LOG.warning("Fix apply failed for " + action.elementGuid() + ": " + e.getMessage());
            entry.markFailed("COM error: " + e.getMessage());
        }
    }

    @Override
    public void rollback(FixEntry entry) {
        if (entry.status() != FixStatus.APPLIED) {
            entry.markFailed("Cannot rollback entry that is not APPLIED (status=" + entry.status() + ")");
            return;
        }
        FixAction action = entry.action();
        try {
            IRPModelElement element = project.findElementByGUID(action.elementGuid());
            if (element == null) {
                entry.markFailed("Element not found for rollback: GUID=" + action.elementGuid());
                return;
            }

            String restoreValue = entry.liveOldValue();

            switch (action.actionType()) {
                case SET_NAME:
                    element.setName(restoreValue != null ? restoreValue : "");
                    break;
                case SET_DESCRIPTION:
                    element.setDescription(restoreValue != null ? restoreValue : "");
                    break;
                case SET_TAG_VALUE:
                    setTagValueOnElement(element, action.field(), restoreValue != null ? restoreValue : "");
                    break;
                case ADD_STEREOTYPE:
                    // Rollback of ADD = remove
                    removeStereotypeFromElement(element, action.newValue());
                    break;
                case REMOVE_STEREOTYPE:
                    // Rollback of REMOVE = add back
                    addStereotypeToElement(element, restoreValue != null ? restoreValue : action.oldValue());
                    break;
                case SET_INITIAL_VALUE:
                    element.setPropertyValue("CG.Attribute.InitialValue", restoreValue != null ? restoreValue : "");
                    break;
                default:
                    entry.markFailed("Unsupported rollback for type: " + action.actionType());
                    return;
            }
            entry.markRolledBack();
            LOG.info("Rolled back: " + action.description());
        } catch (Exception e) {
            LOG.warning("Rollback failed for " + action.elementGuid() + ": " + e.getMessage());
            entry.markFailed("Rollback COM error: " + e.getMessage());
        }
    }

    // ---- Individual apply methods ----

    private void applySetName(FixEntry entry, IRPModelElement element) {
        String oldValue = element.getName();
        element.setName(entry.action().newValue());
        entry.markApplied(oldValue);
        LOG.info("Applied SET_NAME: '" + oldValue + "' -> '" + entry.action().newValue() + "'");
    }

    private void applySetDescription(FixEntry entry, IRPModelElement element) {
        String oldValue = element.getDescription();
        element.setDescription(entry.action().newValue() != null ? entry.action().newValue() : "");
        entry.markApplied(oldValue);
        LOG.info("Applied SET_DESCRIPTION on " + element.getName());
    }

    private void applySetTagValue(FixEntry entry, IRPModelElement element, FixAction action) {
        String tagName = action.field();
        if (tagName == null) {
            entry.markFailed("SET_TAG_VALUE: no tag name specified in field");
            return;
        }
        String oldValue = getTagValueFromElement(element, tagName);
        setTagValueOnElement(element, tagName, action.newValue() != null ? action.newValue() : "");
        entry.markApplied(oldValue);
        LOG.info("Applied SET_TAG_VALUE [" + tagName + "]: '" + oldValue + "' -> '" + action.newValue() + "'");
    }

    private void applyAddStereotype(FixEntry entry, IRPModelElement element, FixAction action) {
        String stereoName = action.newValue();
        // Record current stereotypes for rollback verification
        String oldState = hasStereotype(element, stereoName) ? stereoName : null;
        addStereotypeToElement(element, stereoName);
        entry.markApplied(oldState);
        LOG.info("Applied ADD_STEREOTYPE '" + stereoName + "' on " + element.getName());
    }

    private void applyRemoveStereotype(FixEntry entry, IRPModelElement element, FixAction action) {
        String stereoName = action.oldValue() != null ? action.oldValue() : action.newValue();
        String oldState = hasStereotype(element, stereoName) ? stereoName : null;
        removeStereotypeFromElement(element, stereoName);
        entry.markApplied(oldState);
        LOG.info("Applied REMOVE_STEREOTYPE '" + stereoName + "' from " + element.getName());
    }

    private void applySetInitialValue(FixEntry entry, IRPModelElement element) {
        // Initial value for attributes/FlowProperties
        String oldValue = "";
        try {
            oldValue = element.getPropertyValue("CG.Attribute.InitialValue");
        } catch (Exception ignored) { }
        element.setPropertyValue("CG.Attribute.InitialValue", 
                entry.action().newValue() != null ? entry.action().newValue() : "");
        entry.markApplied(oldValue);
        LOG.info("Applied SET_INITIAL_VALUE on " + element.getName());
    }

    // ---- Rhapsody helper methods ----

    @SuppressWarnings("unchecked")
    private String getTagValueFromElement(IRPModelElement element, String tagName) {
        try {
            IRPTag tag = element.getTag(tagName);
            if (tag != null) {
                return tag.getValue();
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void setTagValueOnElement(IRPModelElement element, String tagName, String value) {
        IRPTag tag = element.getTag(tagName);
        if (tag != null) {
            tag.setValue(value);
        } else {
            // If tag doesn't exist yet, use setTagValue which creates it
            element.setTagValue(element.getTag(tagName), value);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean hasStereotype(IRPModelElement element, String stereoName) {
        try {
            List<IRPStereotype> stereos = element.getStereotypes().toList();
            for (IRPStereotype s : stereos) {
                if (s.getName().equalsIgnoreCase(stereoName)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private void addStereotypeToElement(IRPModelElement element, String stereoName) {
        element.addStereotype(stereoName, "");
    }

    @SuppressWarnings("unchecked")
    private void removeStereotypeFromElement(IRPModelElement element, String stereoName) {
        List<IRPStereotype> stereos = element.getStereotypes().toList();
        for (IRPStereotype s : stereos) {
            if (s.getName().equalsIgnoreCase(stereoName)) {
                element.removeStereotype(s);
                return;
            }
        }
    }
}
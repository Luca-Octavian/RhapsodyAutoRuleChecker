package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.panel;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.CheckboxListField;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.RuleFormLayout;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class OwnerStereotypeConstraintPanel implements RuleParamPanel {

    private static final List<String> ELEMENT_KINDS;
    static {
        List<String> values = new ArrayList<String>();
        for (ElementKind kind : ElementKind.values()) values.add(kind.name());
        ELEMENT_KINDS = Collections.unmodifiableList(values);
    }

    private final CheckboxListField ownerStereotype;
    private final CheckboxListField allowedKinds;
    private final JPanel root;

    public OwnerStereotypeConstraintPanel(List<String> detectedStereotypes, Runnable onChange) {
        root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = RuleFormLayout.constraints();
        ownerStereotype = PanelSupport.addSelection(
                root, gbc, "Owner Stereotype (pick 1) *:",
                "rule.params.ownerConstraint.ownerStereotype",
                detectedStereotypes != null ? detectedStereotypes : Collections.<String>emptyList(),
                0, 0.5, onChange);
        allowedKinds = PanelSupport.addSelection(
                root, gbc, "Allowed Kinds (min 1) *:",
                "rule.params.ownerConstraint.allowedKinds",
                ELEMENT_KINDS, 2, 1.0, onChange);
    }

    @Override public JComponent component() { return root; }

    @Override
    public String validationMessage() {
        if (PanelSupport.empty(ownerStereotype)) return "Owner Stereotype is required.";
        if (PanelSupport.empty(allowedKinds)) return "At least one Allowed Kind is required.";
        return null;
    }

    @Override
    public Map<String, Object> buildParams() {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        PanelSupport.putFirst(params, "ownerStereotype", ownerStereotype);
        if (!PanelSupport.empty(allowedKinds)) {
            params.put("allowedKinds", allowedKinds.getSelectedValues());
        }
        return params;
    }

    @Override
    public void prefill(Map<String, Object> params) {
        if (params == null) return;
        PanelSupport.selectOne(ownerStereotype, params.get("ownerStereotype"));
        PanelSupport.selectMany(allowedKinds, params.get("allowedKinds"));
    }

    @Override
    public void updateValidationMarkers() {
        PanelSupport.mark(ownerStereotype);
        PanelSupport.mark(allowedKinds);
    }
}
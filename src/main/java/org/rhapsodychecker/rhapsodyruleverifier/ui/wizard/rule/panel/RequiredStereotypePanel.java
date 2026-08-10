package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.panel;

import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.CheckboxListField;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.RuleFormLayout;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class RequiredStereotypePanel implements RuleParamPanel {

    private final CheckboxListField requiredStereotype;
    private final JPanel root;

    public RequiredStereotypePanel(List<String> detectedStereotypes, Runnable onChange) {
        root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = RuleFormLayout.constraints();
        requiredStereotype = PanelSupport.addSelection(
                root, gbc, "Stereotype *:",
                "rule.params.requiredStereotype.stereotype",
                detectedStereotypes != null ? detectedStereotypes : Collections.<String>emptyList(),
                0, 1.0, onChange);
    }

    @Override public JComponent component() { return root; }

    @Override
    public String validationMessage() {
        return PanelSupport.empty(requiredStereotype) ? "Stereotype is required." : null;
    }

    @Override
    public Map<String, Object> buildParams() {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        PanelSupport.putFirst(params, "requiredStereotypes", requiredStereotype);
        return params;
    }

    @Override
    public void prefill(Map<String, Object> params) {
        if (params == null) return;
        PanelSupport.selectOne(requiredStereotype, params.get("requiredStereotypes"));
    }

    @Override
    public void updateValidationMarkers() {
        PanelSupport.mark(requiredStereotype);
    }
}
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.panel;

import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.CheckboxListField;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.RuleFormLayout;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class RequiredStereotypeOneOfPanel implements RuleParamPanel {

    private final CheckboxListField oneOfStereotypes;
    private final JPanel root;

    public RequiredStereotypeOneOfPanel(List<String> detectedStereotypes, Runnable onChange) {
        root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = RuleFormLayout.constraints();
        oneOfStereotypes = PanelSupport.addSelection(
                root, gbc, "Stereotypes (pick at least 1) *:",
                "rule.params.requiredStereotypeOneOf.stereotypes",
                detectedStereotypes != null ? detectedStereotypes : Collections.<String>emptyList(),
                0, 1.0, onChange);
    }

    @Override public JComponent component() { return root; }

    @Override
    public String validationMessage() {
        return PanelSupport.empty(oneOfStereotypes)
                ? "At least one stereotype is required." : null;
    }

    @Override
    public Map<String, Object> buildParams() {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("anyOf", oneOfStereotypes.getSelectedValues());
        return params;
    }

    @Override
    public void prefill(Map<String, Object> params) {
        if (params == null) return;
        PanelSupport.selectMany(oneOfStereotypes, params.get("anyOf"));
    }

    @Override
    public void updateValidationMarkers() {
        PanelSupport.mark(oneOfStereotypes);
    }
}
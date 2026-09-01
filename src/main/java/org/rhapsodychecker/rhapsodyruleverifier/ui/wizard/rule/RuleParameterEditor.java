package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule;

import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.panel.RuleParamPanel;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.panel.RuleParamPanelFactory;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Thin dispatcher that delegates to a {@link RuleParamPanel} per rule type.
 */
public final class RuleParameterEditor extends JPanel {

    private static final long serialVersionUID = 1L;

    private final List<String> detectedStereotypes;
    private final Runnable onChange;
    private RuleParamPanel active;

    public RuleParameterEditor(List<String> detectedStereotypes, Runnable onChange) {
        super(new GridBagLayout());
        this.detectedStereotypes = detectedStereotypes != null
                ? detectedStereotypes : Collections.<String>emptyList();
        this.onChange = onChange != null ? onChange : new Runnable() {
            @Override public void run() {}
        };
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    }

    public void showRuleType(RuleType type) {
        removeAll();
        active = type != null
                ? RuleParamPanelFactory.create(type, detectedStereotypes, onChange)
                : null;
        if (active != null) add(active.component());
        revalidate();
        repaint();
        onChange.run();
    }

    public String validationMessage() {
        return active == null ? "Rule type is required." : active.validationMessage();
    }

    public boolean isInputValid() {
        return validationMessage() == null;
    }

    public Map<String, Object> buildParams() {
        return active == null ? Collections.<String, Object>emptyMap() : active.buildParams();
    }

    public void prefill(Map<String, Object> params) {
        if (active != null) active.prefill(params);
        onChange.run();
    }

    public void updateValidationMarkers() {
        if (active != null) active.updateValidationMarkers();
    }

    public boolean usesTargetSpec() {
        return active != null && active.usesTargetSpec();
    }

    public boolean usesElementSet() {
        return active == null || active.usesElementSet();
    }

    public JTextField liveValidationField() {
        return active == null ? null : active.liveValidationField();
    }
}
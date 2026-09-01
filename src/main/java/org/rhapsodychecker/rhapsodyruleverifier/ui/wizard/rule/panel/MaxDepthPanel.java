package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.panel;

import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.RuleFormLayout;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Panel for MaxDepthRule — single max_depth number field.
 */
public final class MaxDepthPanel implements RuleParamPanel {

    private final JTextField maxDepthField = new JTextField("5", 6);
    private final JPanel root;

    public MaxDepthPanel(List<String> detectedStereotypes, Runnable onChange) {
        root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = RuleFormLayout.constraints();

        RuleFormLayout.addRow(root, gbc, 0, "Maximum Depth *",
                "rule.params.maxDepth.maxDepth", maxDepthField);
    }

    @Override public JComponent component() { return root; }
    @Override public boolean usesElementSet() { return false; }

    @Override
    public String validationMessage() {
        String text = maxDepthField.getText().trim();
        if (text.isEmpty()) return "Maximum depth is required.";
        try {
            int val = Integer.parseInt(text);
            if (val < 0) return "Maximum depth must be >= 0.";
        } catch (NumberFormatException e) {
            return "Maximum depth must be a number.";
        }
        return null;
    }

    @Override
    public JTextField liveValidationField() {
        return maxDepthField;
    }

    @Override
    public Map<String, Object> buildParams() {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("max_depth", Integer.parseInt(maxDepthField.getText().trim()));
        return params;
    }

    @Override
    public void prefill(Map<String, Object> params) {
        if (params == null) return;
        if (params.get("max_depth") != null) {
            maxDepthField.setText(params.get("max_depth").toString());
        }
    }
}
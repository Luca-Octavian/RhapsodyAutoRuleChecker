package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.panel;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.RuleFormLayout;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Panel for ChildCountRule — child_kind dropdown + min_count number field.
 */
public final class ChildCountPanel implements RuleParamPanel {

    private final JComboBox<String> childKindCombo;
    private final JTextField minCountField = new JTextField("1", 6);
    private final JPanel root;

    public ChildCountPanel(List<String> detectedStereotypes, Runnable onChange) {
        Runnable safeOnChange = onChange != null ? onChange : new Runnable() {
            @Override public void run() {}
        };

        // Populate child kind dropdown from ElementKind enum
        String[] kindNames = new String[ElementKind.values().length];
        for (int i = 0; i < ElementKind.values().length; i++) {
            kindNames[i] = ElementKind.values()[i].name();
        }
        childKindCombo = new JComboBox<String>(kindNames);

        root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = RuleFormLayout.constraints();

        RuleFormLayout.addRow(root, gbc, 0, "Child Kind *",
                "rule.params.childCount.childKind", childKindCombo);
        RuleFormLayout.addRow(root, gbc, 1, "Minimum Count *",
                "rule.params.childCount.minCount", minCountField);

        childKindCombo.addActionListener(e -> safeOnChange.run());
    }

    @Override public JComponent component() { return root; }

    @Override
    public String validationMessage() {
        String text = minCountField.getText().trim();
        if (text.isEmpty()) return "Minimum count is required.";
        try {
            int val = Integer.parseInt(text);
            if (val < 0) return "Minimum count must be >= 0.";
        } catch (NumberFormatException e) {
            return "Minimum count must be a number.";
        }
        return null;
    }

    @Override
    public JTextField liveValidationField() {
        return minCountField;
    }

    @Override
    public Map<String, Object> buildParams() {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("child_kind", (String) childKindCombo.getSelectedItem());
        params.put("min_count", Integer.parseInt(minCountField.getText().trim()));
        return params;
    }

    @Override
    public void prefill(Map<String, Object> params) {
        if (params == null) return;
        if (params.get("child_kind") != null) {
            childKindCombo.setSelectedItem(params.get("child_kind").toString());
        }
        if (params.get("min_count") != null) {
            minCountField.setText(params.get("min_count").toString());
        }
    }
}
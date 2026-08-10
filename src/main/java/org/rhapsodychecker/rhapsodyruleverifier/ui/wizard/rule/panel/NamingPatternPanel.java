package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.panel;

import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.RuleFormLayout;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class NamingPatternPanel implements RuleParamPanel {

    private static final String[] NAMING_MODES =
            {"Starts with", "Ends with", "Contains"};

    private final JComboBox<String> namingMode =
            new JComboBox<String>(NAMING_MODES);
    private final JTextField namingValue = new JTextField(25);
    private final JCheckBox namingCaseSensitive =
            new JCheckBox("Case-sensitive", true);
    private final JPanel root;

    public NamingPatternPanel(List<String> detectedStereotypes, Runnable onChange) {
        Runnable safeOnChange = onChange != null ? onChange : new Runnable() {
            @Override public void run() {}
        };
        root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = RuleFormLayout.constraints();
        RuleFormLayout.addRow(root, gbc, 0, "Match Mode *",
                "rule.params.namingPattern.mode", namingMode);
        RuleFormLayout.addRow(root, gbc, 1, "Value *",
                "rule.params.namingPattern.value", namingValue);
        RuleFormLayout.addFullWidth(root, gbc, 2, namingCaseSensitive);

        namingMode.addActionListener(e -> safeOnChange.run());
        namingCaseSensitive.addActionListener(e -> safeOnChange.run());
    }

    @Override public JComponent component() { return root; }

    @Override
    public String validationMessage() {
        return namingValue.getText().trim().isEmpty()
                ? "Match value is required." : null;
    }

    @Override
    public JTextField liveValidationField() {
        return namingValue;
    }

    @Override
    public Map<String, Object> buildParams() {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        String mode = (String) namingMode.getSelectedItem();
        String value = namingValue.getText().trim();
        if ("Starts with".equals(mode)) params.put("startsWith", value);
        else if ("Ends with".equals(mode)) params.put("endsWith", value);
        else params.put("contains", value);
        params.put("caseSensitive", namingCaseSensitive.isSelected());
        return params;
    }

    @Override
    public void prefill(Map<String, Object> params) {
        if (params == null) return;
        if (params.get("startsWith") != null) {
            namingMode.setSelectedItem("Starts with");
            namingValue.setText(params.get("startsWith").toString());
        } else if (params.get("endsWith") != null) {
            namingMode.setSelectedItem("Ends with");
            namingValue.setText(params.get("endsWith").toString());
        } else if (params.get("contains") != null) {
            namingMode.setSelectedItem("Contains");
            namingValue.setText(params.get("contains").toString());
        }
        if (params.get("caseSensitive") != null) {
            namingCaseSensitive.setSelected(Boolean.parseBoolean(
                    params.get("caseSensitive").toString()));
        }
    }
}
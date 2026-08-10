package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.panel;

import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.CheckboxListField;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help.HelpIcon;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.RuleFormLayout;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.RuleValueCodec;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class FlowPropertyConstraintPanel implements RuleParamPanel {

    private static final List<String> FLOW_DIRECTIONS =
            Arrays.asList("In", "Out", "Bidirectional");

    private final JCheckBox typeRequired = new JCheckBox("Type is required");
    private final JTextField allowedTypes = new JTextField(25);
    private final JCheckBox initialValueRequired =
            new JCheckBox("Initial value is required");
    private final JCheckBox initialValueEmpty =
            new JCheckBox("Initial value must be empty");
    private final JCheckBox directionRequired =
            new JCheckBox("Direction is required");
    private CheckboxListField allowedDirections;

    private final JPanel root;

    public FlowPropertyConstraintPanel(List<String> detectedStereotypes, Runnable onChange) {
        Runnable safeOnChange = onChange != null ? onChange : new Runnable() {
            @Override public void run() {}
        };
        root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = RuleFormLayout.constraints();

        int row = 0;
        RuleFormLayout.addFullWidth(root, gbc, row++,
                HelpIcon.labelWithHelp("Type Constraints:",
                        "rule.params.flowProperty.type"));
        RuleFormLayout.addFullWidth(root, gbc, row++, typeRequired);
        RuleFormLayout.addRow(root, gbc, row++, "Allowed types",
                "rule.params.flowProperty.type.allowed", allowedTypes);

        RuleFormLayout.addFullWidth(root, gbc, row++,
                HelpIcon.labelWithHelp("Initial Value Constraints:",
                        "rule.params.flowProperty.initialValue"));
        RuleFormLayout.addFullWidth(root, gbc, row++, initialValueRequired);
        RuleFormLayout.addFullWidth(root, gbc, row++, initialValueEmpty);

        RuleFormLayout.addFullWidth(root, gbc, row++,
                HelpIcon.labelWithHelp("Direction Constraints:",
                        "rule.params.flowProperty.direction"));
        RuleFormLayout.addFullWidth(root, gbc, row++, directionRequired);
        allowedDirections = PanelSupport.addSelection(
                root, gbc, "Allowed directions:",
                "rule.params.flowProperty.direction.allowed",
                FLOW_DIRECTIONS, row, 0.5, onChange);

        // Mutual-exclusion listeners
        typeRequired.addActionListener(e -> safeOnChange.run());
        directionRequired.addActionListener(e -> safeOnChange.run());
        initialValueRequired.addActionListener(e -> {
            if (initialValueRequired.isSelected()) initialValueEmpty.setSelected(false);
            safeOnChange.run();
        });
        initialValueEmpty.addActionListener(e -> {
            if (initialValueEmpty.isSelected()) initialValueRequired.setSelected(false);
            safeOnChange.run();
        });
    }

    @Override public JComponent component() { return root; }

    @Override
    public String validationMessage() {
        return hasFlowConstraint() ? null
                : "At least one FlowProperty constraint must be configured.";
    }

    private boolean hasFlowConstraint() {
        return typeRequired.isSelected()
                || !allowedTypes.getText().trim().isEmpty()
                || initialValueRequired.isSelected()
                || initialValueEmpty.isSelected()
                || directionRequired.isSelected()
                || !PanelSupport.empty(allowedDirections);
    }

    @Override
    public Map<String, Object> buildParams() {
        Map<String, Object> params = new LinkedHashMap<String, Object>();

        Map<String, Object> typeBlock = new LinkedHashMap<String, Object>();
        if (typeRequired.isSelected()) typeBlock.put("required", true);
        List<String> types = RuleValueCodec.splitValues(allowedTypes.getText());
        if (!types.isEmpty()) typeBlock.put("allowed", types);
        if (!typeBlock.isEmpty()) params.put("type", typeBlock);

        Map<String, Object> initial = new LinkedHashMap<String, Object>();
        if (initialValueRequired.isSelected()) initial.put("required", true);
        if (initialValueEmpty.isSelected()) initial.put("mustBeEmpty", true);
        if (!initial.isEmpty()) params.put("initialValue", initial);

        Map<String, Object> direction = new LinkedHashMap<String, Object>();
        if (directionRequired.isSelected()) direction.put("required", true);
        if (!PanelSupport.empty(allowedDirections)) {
            direction.put("allowed", allowedDirections.getSelectedValues());
        }
        if (!direction.isEmpty()) params.put("direction", direction);

        return params;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void prefill(Map<String, Object> params) {
        if (params == null) return;

        Object typeValue = params.get("type");
        if (typeValue instanceof Map) {
            Map<String, Object> block = (Map<String, Object>) typeValue;
            typeRequired.setSelected(Boolean.TRUE.equals(block.get("required")));
            Object allowed = block.get("allowed");
            if (allowed instanceof Iterable) {
                allowedTypes.setText(
                        RuleValueCodec.joinValues((Iterable<?>) allowed));
            }
        }

        Object initialValue = params.get("initialValue");
        if (initialValue instanceof Map) {
            Map<String, Object> block = (Map<String, Object>) initialValue;
            initialValueRequired.setSelected(
                    Boolean.TRUE.equals(block.get("required")));
            initialValueEmpty.setSelected(
                    Boolean.TRUE.equals(block.get("mustBeEmpty")));
        }

        Object directionValue = params.get("direction");
        if (directionValue instanceof Map) {
            Map<String, Object> block = (Map<String, Object>) directionValue;
            directionRequired.setSelected(
                    Boolean.TRUE.equals(block.get("required")));
            PanelSupport.selectMany(allowedDirections, block.get("allowed"));
        }
    }
}
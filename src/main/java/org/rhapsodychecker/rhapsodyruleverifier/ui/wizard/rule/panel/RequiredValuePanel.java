package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.panel;

import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.RuleFormLayout;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.RuleValueCodec;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class RequiredValuePanel implements RuleParamPanel {

    private static final String[] VALUE_CHECK_MODES = {
            "Must not be empty", "Must match specific value(s)",
            "Length constraint", "Regex pattern", "Numeric comparison"
    };
    private static final String[] NUMERIC_COMPARISONS = {
            "equals (=)", "not equals (\u2260)", "greater than (>)",
            "at least (\u2265)", "less than (<)", "at most (\u2264)", "between"
    };

    private final Runnable onChange;

    private final JComboBox<String> valueCheckMode =
            new JComboBox<String>(VALUE_CHECK_MODES);
    private final JTextField values = new JTextField(25);
    private final JTextField minLength = new JTextField(8);
    private final JTextField maxLength = new JTextField(8);
    private final JTextField valuePattern = new JTextField(25);
    private final JComboBox<String> numericComparison =
            new JComboBox<String>(NUMERIC_COMPARISONS);
    private final JTextField numericValue = new JTextField(8);
    private final JTextField numericMin = new JTextField(8);
    private final JTextField numericMax = new JTextField(8);

    private JPanel matchPanel;
    private JPanel lengthPanel;
    private JPanel patternPanel;
    private JPanel numericPanel;

    private final JPanel root;

    public RequiredValuePanel(List<String> detectedStereotypes, Runnable onChange) {
        this.onChange = onChange != null ? onChange : new Runnable() {
            @Override public void run() {}
        };
        this.root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        valueCheckMode.addActionListener(e -> {
            updateValueMode();
            this.onChange.run();
        });
        numericComparison.addActionListener(e -> {
            updateNumericMode();
            this.onChange.run();
        });

        buildComponent();
    }

    private void buildComponent() {
        GridBagConstraints gbc = RuleFormLayout.constraints();
        RuleFormLayout.addRow(root, gbc, 0, "Check Mode",
                "rule.params.requiredValue.checkMode", valueCheckMode);

        matchPanel = PanelSupport.formPanel();
        lengthPanel = PanelSupport.formPanel();
        patternPanel = PanelSupport.formPanel();
        numericPanel = PanelSupport.formPanel();

        GridBagConstraints sub = RuleFormLayout.constraints();
        RuleFormLayout.addRow(matchPanel, sub, 0, "Allowed values",
                "rule.params.requiredValue.values", values);
        RuleFormLayout.addRow(lengthPanel, sub, 0, "Min Length",
                "rule.params.requiredValue.minLength", minLength);
        RuleFormLayout.addRow(lengthPanel, sub, 1, "Max Length",
                "rule.params.requiredValue.maxLength", maxLength);
        RuleFormLayout.addRow(patternPanel, sub, 0, "Regex pattern *",
                "rule.params.requiredValue.pattern", valuePattern);

        JPanel comparisonRow = PanelSupport.formPanel();
        JPanel valueRow = PanelSupport.formPanel();
        JPanel minRow = PanelSupport.formPanel();
        JPanel maxRow = PanelSupport.formPanel();
        GridBagConstraints rowConstraints = RuleFormLayout.constraints();
        RuleFormLayout.addRow(comparisonRow, rowConstraints, 0, "Comparison",
                "rule.params.requiredValue.numericComp", numericComparison);
        RuleFormLayout.addRow(valueRow, rowConstraints, 0, "Value",
                "rule.params.requiredValue.numericValue", numericValue);
        RuleFormLayout.addRow(minRow, rowConstraints, 0, "Range Min",
                "rule.params.requiredValue.rangeMin", numericMin);
        RuleFormLayout.addRow(maxRow, rowConstraints, 0, "Range Max",
                "rule.params.requiredValue.rangeMax", numericMax);
        RuleFormLayout.addFullWidth(numericPanel, sub, 0, comparisonRow);
        RuleFormLayout.addFullWidth(numericPanel, sub, 1, valueRow);
        RuleFormLayout.addFullWidth(numericPanel, sub, 2, minRow);
        RuleFormLayout.addFullWidth(numericPanel, sub, 3, maxRow);

        RuleFormLayout.addFullWidth(root, gbc, 1, matchPanel);
        RuleFormLayout.addFullWidth(root, gbc, 2, lengthPanel);
        RuleFormLayout.addFullWidth(root, gbc, 3, patternPanel);
        RuleFormLayout.addFullWidth(root, gbc, 4, numericPanel);
        updateValueMode();
    }

    @Override
    public JComponent component() {
        return root;
    }

    @Override
    public boolean usesTargetSpec() {
        return true;
    }

    @Override
    public Map<String, Object> buildParams() {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        String mode = (String) valueCheckMode.getSelectedItem();
        if ("Must not be empty".equals(mode)) {
            params.put("nonEmpty", true);
        } else if ("Must match specific value(s)".equals(mode)) {
            List<String> allowed = RuleValueCodec.splitValues(values.getText());
            if (!allowed.isEmpty()) {
                params.put("operator", "in");
                params.put("values", allowed);
            }
        } else if ("Length constraint".equals(mode)) {
            PanelSupport.putInteger(params, "minLength", minLength);
            PanelSupport.putInteger(params, "maxLength", maxLength);
        } else if ("Regex pattern".equals(mode)) {
            params.put("operator", "matches");
            params.put("pattern", valuePattern.getText().trim());
        } else if ("Numeric comparison".equals(mode)) {
            String comparison = (String) numericComparison.getSelectedItem();
            params.put("operator",
                    RuleValueCodec.numericComparisonToOperator(comparison));
            if ("between".equals(comparison)) {
                PanelSupport.putText(params, "min", numericMin);
                PanelSupport.putText(params, "max", numericMax);
            } else {
                PanelSupport.putText(params, "value", numericValue);
            }
        }
        return params;
    }

    @Override
    public void prefill(Map<String, Object> params) {
        if (params == null) return;
        Object operator = params.get("operator");
        if (params.containsKey("pattern") || "matches".equals(operator)) {
            valueCheckMode.setSelectedItem("Regex pattern");
            PanelSupport.setText(valuePattern, params.get("pattern"));
        } else if (params.containsKey("minLength")
                || params.containsKey("maxLength")) {
            valueCheckMode.setSelectedItem("Length constraint");
            PanelSupport.setText(minLength, params.get("minLength"));
            PanelSupport.setText(maxLength, params.get("maxLength"));
        } else if (RuleValueCodec.isNumericOperator(operator)) {
            valueCheckMode.setSelectedItem("Numeric comparison");
            numericComparison.setSelectedItem(
                    RuleValueCodec.operatorToNumericComparison(
                            operator != null ? operator.toString() : "eq"));
            PanelSupport.setText(numericValue, params.get("value"));
            PanelSupport.setText(numericMin, params.get("min"));
            PanelSupport.setText(numericMax, params.get("max"));
        } else if (params.containsKey("values") || params.containsKey("value")
                || "in".equals(operator) || "not_in".equals(operator)
                || "eq".equals(operator)) {
            valueCheckMode.setSelectedItem("Must match specific value(s)");
            Object selected = params.get("values");
            if (selected instanceof Iterable) {
                values.setText(RuleValueCodec.joinValues((Iterable<?>) selected));
            } else {
                PanelSupport.setText(values, params.get("value"));
            }
        } else {
            valueCheckMode.setSelectedItem("Must not be empty");
        }
        updateValueMode();
    }

    private void updateValueMode() {
        String mode = (String) valueCheckMode.getSelectedItem();
        setVisible(matchPanel, "Must match specific value(s)".equals(mode));
        setVisible(lengthPanel, "Length constraint".equals(mode));
        setVisible(patternPanel, "Regex pattern".equals(mode));
        setVisible(numericPanel, "Numeric comparison".equals(mode));
        updateNumericMode();
        root.revalidate();
        root.repaint();
    }

    private void updateNumericMode() {
        boolean between = "between".equals(numericComparison.getSelectedItem());
        setParentVisible(numericValue, !between);
        setParentVisible(numericMin, between);
        setParentVisible(numericMax, between);
        if (numericPanel != null) {
            numericPanel.revalidate();
            numericPanel.repaint();
        }
    }

    private static void setVisible(JComponent component, boolean visible) {
        if (component != null) component.setVisible(visible);
    }

    private static void setParentVisible(JComponent component, boolean visible) {
        if (component.getParent() != null) component.getParent().setVisible(visible);
    }
}
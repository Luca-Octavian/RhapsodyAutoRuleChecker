package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule;

import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.CheckboxListField;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help.HelpIcon;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Owns all rule-type-specific controls, layout, validation, prefill and
 * serialization. RuleDialog only coordinates common rule fields.
 */
public final class RuleParameterEditor extends JPanel {

    private static final String[] VALUE_CHECK_MODES = {
            "Must not be empty", "Must match specific value(s)",
            "Length constraint", "Regex pattern", "Numeric comparison"
    };
    private static final String[] NUMERIC_COMPARISONS = {
            "equals (=)", "not equals (\u2260)", "greater than (>)",
            "at least (\u2265)", "less than (<)", "at most (\u2264)", "between"
    };
    private static final String[] NAMING_MODES =
            {"Starts with", "Ends with", "Contains"};
    private static final String[] RELATION_COUNT_MODES =
            {"At least", "Exactly", "At most", "More than", "Fewer than"};
    private static final List<String> RELATION_KINDS = Arrays.asList(
            "any", "dependency", "association", "generalization",
            "usage", "realization", "abstraction", "link");
    private static final List<String> FLOW_DIRECTIONS =
            Arrays.asList("In", "Out", "Bidirectional");
    private static final List<String> ELEMENT_KINDS;

    static {
        List<String> values = new ArrayList<String>();
        for (ElementKind kind : ElementKind.values()) values.add(kind.name());
        ELEMENT_KINDS = Collections.unmodifiableList(values);
    }

    private final List<String> detectedStereotypes;
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

    private CheckboxListField requiredStereotype;
    private CheckboxListField oneOfStereotypes;

    private final JComboBox<String> namingMode =
            new JComboBox<String>(NAMING_MODES);
    private final JTextField namingValue = new JTextField(25);
    private final JCheckBox namingCaseSensitive =
            new JCheckBox("Case-sensitive", true);

    private CheckboxListField relationKind;
    private final JComboBox<String> relationDirection =
            new JComboBox<String>(new String[]{"any", "outgoing", "incoming"});
    private CheckboxListField relationStereotypes;
    private final JComboBox<String> relationCountMode =
            new JComboBox<String>(RELATION_COUNT_MODES);
    private final JSpinner relationCount =
            new JSpinner(new SpinnerNumberModel(1, 0, 9999, 1));

    private CheckboxListField ownerStereotype;
    private CheckboxListField allowedKinds;

    private final JCheckBox typeRequired = new JCheckBox("Type is required");
    private final JTextField allowedTypes = new JTextField(25);
    private final JCheckBox initialValueRequired =
            new JCheckBox("Initial value is required");
    private final JCheckBox initialValueEmpty =
            new JCheckBox("Initial value must be empty");
    private final JCheckBox directionRequired =
            new JCheckBox("Direction is required");
    private CheckboxListField allowedDirections;

    private RuleType type;

    public RuleParameterEditor(List<String> detectedStereotypes, Runnable onChange) {
        super(new GridBagLayout());
        this.detectedStereotypes = detectedStereotypes != null
                ? detectedStereotypes : Collections.<String>emptyList();
        this.onChange = onChange != null ? onChange : new Runnable() {
            @Override public void run() {}
        };
        setBorder(BorderFactory.createTitledBorder("Rule Parameters"));
        installStableListeners();
    }

    public void showRuleType(RuleType ruleType) {
        this.type = ruleType;
        removeAll();
        if (ruleType != null) build(ruleType);
        revalidate();
        repaint();
        changed();
    }

    private void build(RuleType ruleType) {
        GridBagConstraints gbc = RuleFormLayout.constraints();
        switch (ruleType) {
            case REQUIRED_VALUE:
                buildRequiredValue(gbc);
                break;
            case REQUIRED_STEREOTYPE:
                requiredStereotype = addSelection(
                        gbc, "Stereotype *:",
                        "rule.params.requiredStereotype.stereotype",
                        detectedStereotypes, 0, 1.0);
                break;
            case REQUIRED_STEREOTYPE_ONE_OF:
                oneOfStereotypes = addSelection(
                        gbc, "Stereotypes (pick at least 1) *:",
                        "rule.params.requiredStereotypeOneOf.stereotypes",
                        detectedStereotypes, 0, 1.0);
                break;
            case NAMING_PATTERN:
                RuleFormLayout.addRow(this, gbc, 0, "Match Mode *",
                        "rule.params.namingPattern.mode", namingMode);
                RuleFormLayout.addRow(this, gbc, 1, "Value *",
                        "rule.params.namingPattern.value", namingValue);
                RuleFormLayout.addFullWidth(this, gbc, 2, namingCaseSensitive);
                break;
            case RELATION_EXISTS:
                buildRelation(gbc);
                break;
            case OWNER_STEREOTYPE_CONSTRAINT:
                ownerStereotype = addSelection(
                        gbc, "Owner Stereotype (pick 1) *:",
                        "rule.params.ownerConstraint.ownerStereotype",
                        detectedStereotypes, 0, 0.5);
                allowedKinds = addSelection(
                        gbc, "Allowed Kinds (min 1) *:",
                        "rule.params.ownerConstraint.allowedKinds",
                        ELEMENT_KINDS, 2, 1.0);
                break;
            case FLOW_PROPERTY_CONSTRAINT:
                buildFlowProperty(gbc);
                break;
        }
    }

    private void buildRequiredValue(GridBagConstraints gbc) {
        RuleFormLayout.addRow(this, gbc, 0, "Check Mode",
                "rule.params.requiredValue.checkMode", valueCheckMode);

        matchPanel = formPanel();
        lengthPanel = formPanel();
        patternPanel = formPanel();
        numericPanel = formPanel();

        GridBagConstraints sub = RuleFormLayout.constraints();
        RuleFormLayout.addRow(matchPanel, sub, 0, "Allowed values",
                "rule.params.requiredValue.values", values);
        RuleFormLayout.addRow(lengthPanel, sub, 0, "Min Length",
                "rule.params.requiredValue.minLength", minLength);
        RuleFormLayout.addRow(lengthPanel, sub, 1, "Max Length",
                "rule.params.requiredValue.maxLength", maxLength);
        RuleFormLayout.addRow(patternPanel, sub, 0, "Regex pattern *",
                "rule.params.requiredValue.pattern", valuePattern);

        JPanel comparisonRow = formPanel();
        JPanel valueRow = formPanel();
        JPanel minRow = formPanel();
        JPanel maxRow = formPanel();
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

        RuleFormLayout.addFullWidth(this, gbc, 1, matchPanel);
        RuleFormLayout.addFullWidth(this, gbc, 2, lengthPanel);
        RuleFormLayout.addFullWidth(this, gbc, 3, patternPanel);
        RuleFormLayout.addFullWidth(this, gbc, 4, numericPanel);
        updateValueMode();
    }

    private void buildRelation(GridBagConstraints gbc) {
        relationKind = addSelection(
                gbc, "Relation Kind:", "rule.params.relationExists.relationKind",
                RELATION_KINDS, 0, 0.5);
        RuleFormLayout.addRow(this, gbc, 2, "Direction",
                "rule.params.relationExists.direction", relationDirection);
        relationStereotypes = addSelection(
                gbc, "Relation Stereotypes:",
                "rule.params.relationExists.relationStereotypes",
                detectedStereotypes, 3, 0.5);

        JPanel count = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        count.add(new JLabel("Require"));
        count.add(relationCountMode);
        count.add(relationCount);
        count.add(new JLabel("relation(s)"));
        RuleFormLayout.addFullWidth(this, gbc, 5,
                HelpIcon.labelWithHelp("Required Count:",
                        "rule.params.relationExists.count"));
        RuleFormLayout.addFullWidth(this, gbc, 6, count);
    }

    private void buildFlowProperty(GridBagConstraints gbc) {
        int row = 0;
        RuleFormLayout.addFullWidth(this, gbc, row++,
                HelpIcon.labelWithHelp("Type Constraints:",
                        "rule.params.flowProperty.type"));
        RuleFormLayout.addFullWidth(this, gbc, row++, typeRequired);
        RuleFormLayout.addRow(this, gbc, row++, "Allowed types",
                "rule.params.flowProperty.type.allowed", allowedTypes);

        RuleFormLayout.addFullWidth(this, gbc, row++,
                HelpIcon.labelWithHelp("Initial Value Constraints:",
                        "rule.params.flowProperty.initialValue"));
        RuleFormLayout.addFullWidth(this, gbc, row++, initialValueRequired);
        RuleFormLayout.addFullWidth(this, gbc, row++, initialValueEmpty);

        RuleFormLayout.addFullWidth(this, gbc, row++,
                HelpIcon.labelWithHelp("Direction Constraints:",
                        "rule.params.flowProperty.direction"));
        RuleFormLayout.addFullWidth(this, gbc, row++, directionRequired);
        allowedDirections = addSelection(
                gbc, "Allowed directions:",
                "rule.params.flowProperty.direction.allowed",
                FLOW_DIRECTIONS, row, 0.5);
    }

    private CheckboxListField addSelection(
            GridBagConstraints gbc, String label, String helpKey,
            List<String> values, int row, double weight) {
        RuleFormLayout.addFullWidth(this, gbc, row,
                HelpIcon.labelWithHelp(label, helpKey));
        CheckboxListField field = new CheckboxListField(values);
        field.addChangeListener(onChange);
        gbc.weighty = weight;
        gbc.fill = GridBagConstraints.BOTH;
        RuleFormLayout.addFullWidth(this, gbc, row + 1, field);
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        return field;
    }

    private static JPanel formPanel() {
        return new JPanel(new GridBagLayout());
    }

    private void installStableListeners() {
        valueCheckMode.addActionListener(e -> {
            updateValueMode();
            changed();
        });
        numericComparison.addActionListener(e -> {
            updateNumericMode();
            changed();
        });
        namingMode.addActionListener(e -> changed());
        namingCaseSensitive.addActionListener(e -> changed());

        typeRequired.addActionListener(e -> changed());
        directionRequired.addActionListener(e -> changed());
        initialValueRequired.addActionListener(e -> {
            if (initialValueRequired.isSelected()) initialValueEmpty.setSelected(false);
            changed();
        });
        initialValueEmpty.addActionListener(e -> {
            if (initialValueEmpty.isSelected()) initialValueRequired.setSelected(false);
            changed();
        });
    }

    private void changed() {
        onChange.run();
    }

    private void updateValueMode() {
        String mode = (String) valueCheckMode.getSelectedItem();
        setVisible(matchPanel, "Must match specific value(s)".equals(mode));
        setVisible(lengthPanel, "Length constraint".equals(mode));
        setVisible(patternPanel, "Regex pattern".equals(mode));
        setVisible(numericPanel, "Numeric comparison".equals(mode));
        updateNumericMode();
        revalidate();
        repaint();
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

    public boolean isInputValid() {
        return validationMessage() == null;
    }

    public String validationMessage() {
        if (type == null) return "Rule type is required.";
        switch (type) {
            case REQUIRED_STEREOTYPE:
                return empty(requiredStereotype) ? "Stereotype is required." : null;
            case REQUIRED_STEREOTYPE_ONE_OF:
                return empty(oneOfStereotypes)
                        ? "At least one stereotype is required." : null;
            case NAMING_PATTERN:
                return namingValue.getText().trim().isEmpty()
                        ? "Match value is required." : null;
            case OWNER_STEREOTYPE_CONSTRAINT:
                if (empty(ownerStereotype)) return "Owner Stereotype is required.";
                if (empty(allowedKinds)) return "At least one Allowed Kind is required.";
                return null;
            case FLOW_PROPERTY_CONSTRAINT:
                return hasFlowConstraint() ? null
                        : "At least one FlowProperty constraint must be configured.";
            default:
                return null;
        }
    }

    public void updateValidationMarkers() {
        mark(requiredStereotype);
        mark(oneOfStereotypes);
        mark(ownerStereotype);
        mark(allowedKinds);
    }

    private static void mark(CheckboxListField field) {
        if (field != null) field.setValid(!field.getSelectedValues().isEmpty());
    }

    private static boolean empty(CheckboxListField field) {
        return field == null || field.getSelectedValues().isEmpty();
    }

    private boolean hasFlowConstraint() {
        return typeRequired.isSelected()
                || !allowedTypes.getText().trim().isEmpty()
                || initialValueRequired.isSelected()
                || initialValueEmpty.isSelected()
                || directionRequired.isSelected()
                || !empty(allowedDirections);
    }

    public Map<String, Object> buildParams() {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        if (type == null) return params;

        switch (type) {
            case REQUIRED_VALUE:
                buildRequiredValueParams(params);
                break;
            case REQUIRED_STEREOTYPE:
                putFirst(params, "requiredStereotypes", requiredStereotype);
                break;
            case REQUIRED_STEREOTYPE_ONE_OF:
                params.put("anyOf", oneOfStereotypes.getSelectedValues());
                break;
            case NAMING_PATTERN:
                String mode = (String) namingMode.getSelectedItem();
                String value = namingValue.getText().trim();
                if ("Starts with".equals(mode)) params.put("startsWith", value);
                else if ("Ends with".equals(mode)) params.put("endsWith", value);
                else params.put("contains", value);
                params.put("caseSensitive", namingCaseSensitive.isSelected());
                break;
            case RELATION_EXISTS:
                putFirst(params, "relationKind", relationKind);
                params.put("direction", relationDirection.getSelectedItem());
                if (!empty(relationStereotypes)) {
                    params.put("relationStereotypes",
                            relationStereotypes.getSelectedValues());
                }
                params.put("operator", RuleValueCodec.countModeToOperator(
                        (String) relationCountMode.getSelectedItem()));
                params.put("value", ((Number) relationCount.getValue()).intValue());
                break;
            case OWNER_STEREOTYPE_CONSTRAINT:
                putFirst(params, "ownerStereotype", ownerStereotype);
                if (!empty(allowedKinds)) {
                    params.put("allowedKinds", allowedKinds.getSelectedValues());
                }
                break;
            case FLOW_PROPERTY_CONSTRAINT:
                buildFlowParams(params);
                break;
        }
        return params;
    }

    private void buildRequiredValueParams(Map<String, Object> params) {
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
            putInteger(params, "minLength", minLength);
            putInteger(params, "maxLength", maxLength);
        } else if ("Regex pattern".equals(mode)) {
            params.put("operator", "matches");
            params.put("pattern", valuePattern.getText().trim());
        } else if ("Numeric comparison".equals(mode)) {
            String comparison = (String) numericComparison.getSelectedItem();
            params.put("operator",
                    RuleValueCodec.numericComparisonToOperator(comparison));
            if ("between".equals(comparison)) {
                putText(params, "min", numericMin);
                putText(params, "max", numericMax);
            } else {
                putText(params, "value", numericValue);
            }
        }
    }

    private void buildFlowParams(Map<String, Object> params) {
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
        if (!empty(allowedDirections)) {
            direction.put("allowed", allowedDirections.getSelectedValues());
        }
        if (!direction.isEmpty()) params.put("direction", direction);
    }

    @SuppressWarnings("unchecked")
    public void prefill(Map<String, Object> params) {
        if (params == null || type == null) return;
        switch (type) {
            case REQUIRED_VALUE:
                prefillRequiredValue(params);
                break;
            case REQUIRED_STEREOTYPE:
                selectOne(requiredStereotype, params.get("requiredStereotypes"));
                break;
            case REQUIRED_STEREOTYPE_ONE_OF:
                selectMany(oneOfStereotypes, params.get("anyOf"));
                break;
            case NAMING_PATTERN:
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
                break;
            case RELATION_EXISTS:
                selectOne(relationKind, params.get("relationKind"));
                if (params.get("direction") != null) {
                    relationDirection.setSelectedItem(params.get("direction").toString());
                }
                selectMany(relationStereotypes, params.get("relationStereotypes"));
                relationCountMode.setSelectedItem(
                        RuleValueCodec.operatorToCountMode(text(params, "operator", "gte")));
                if (params.get("value") != null) {
                    try {
                        relationCount.setValue(
                                Integer.parseInt(params.get("value").toString()));
                    } catch (NumberFormatException ignored) {}
                }
                break;
            case OWNER_STEREOTYPE_CONSTRAINT:
                selectOne(ownerStereotype, params.get("ownerStereotype"));
                selectMany(allowedKinds, params.get("allowedKinds"));
                break;
            case FLOW_PROPERTY_CONSTRAINT:
                prefillFlow(params);
                break;
        }
        changed();
    }

    private void prefillRequiredValue(Map<String, Object> params) {
        Object operator = params.get("operator");
        if (params.containsKey("pattern") || "matches".equals(operator)) {
            valueCheckMode.setSelectedItem("Regex pattern");
            setText(valuePattern, params.get("pattern"));
        } else if (params.containsKey("minLength")
                || params.containsKey("maxLength")) {
            valueCheckMode.setSelectedItem("Length constraint");
            setText(minLength, params.get("minLength"));
            setText(maxLength, params.get("maxLength"));
        } else if (RuleValueCodec.isNumericOperator(operator)) {
            valueCheckMode.setSelectedItem("Numeric comparison");
            numericComparison.setSelectedItem(
                    RuleValueCodec.operatorToNumericComparison(
                            operator != null ? operator.toString() : "eq"));
            setText(numericValue, params.get("value"));
            setText(numericMin, params.get("min"));
            setText(numericMax, params.get("max"));
        } else if (params.containsKey("values") || params.containsKey("value")
                || "in".equals(operator) || "not_in".equals(operator)
                || "eq".equals(operator)) {
            valueCheckMode.setSelectedItem("Must match specific value(s)");
            Object selected = params.get("values");
            if (selected instanceof Iterable) {
                values.setText(RuleValueCodec.joinValues((Iterable<?>) selected));
            } else {
                setText(values, params.get("value"));
            }
        } else {
            valueCheckMode.setSelectedItem("Must not be empty");
        }
        updateValueMode();
    }

    @SuppressWarnings("unchecked")
    private void prefillFlow(Map<String, Object> params) {
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
            selectMany(allowedDirections, block.get("allowed"));
        }
    }

    private static void putFirst(Map<String, Object> params, String key,
                                 CheckboxListField field) {
        if (!empty(field)) params.put(key, field.getSelectedValues().get(0));
    }

    private static void putInteger(Map<String, Object> params, String key,
                                   JTextField field) {
        String value = field.getText().trim();
        if (!value.isEmpty()) params.put(key, Integer.parseInt(value));
    }

    private static void putText(Map<String, Object> params, String key,
                                JTextField field) {
        String value = field.getText().trim();
        if (!value.isEmpty()) params.put(key, value);
    }

    private static String text(Map<String, Object> params,
                               String key, String fallback) {
        Object value = params.get(key);
        return value != null ? value.toString() : fallback;
    }

    private static void setText(JTextField field, Object value) {
        if (value != null) field.setText(value.toString());
    }

    private static void selectOne(CheckboxListField field, Object value) {
        if (field != null && value != null) {
            field.setSelectedValues(Collections.singletonList(value.toString()));
        }
    }

    private static void selectMany(CheckboxListField field, Object value) {
        if (field == null || value == null) return;
        List<String> values = new ArrayList<String>();
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                if (item != null) values.add(item.toString());
            }
        } else {
            values.add(value.toString());
        }
        field.setSelectedValues(values);
    }

    public JTextField namingValueField() {
        return namingValue;
    }
}
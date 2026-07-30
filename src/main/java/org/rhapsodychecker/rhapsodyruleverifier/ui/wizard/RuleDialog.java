// ui/wizard/RuleDialog.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.AliasDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardState;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help.FieldValidation;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help.HelpIcon;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AccentColors;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public final class RuleDialog extends JDialog {

    private final WizardState         state;
    private final FastDetectionResult fast;
    private final List<String>        detectedStereos;

    // Common fields
    private final JTextField          idField      = new JTextField(20);
    private final JComboBox<RuleType> typeCombo    = new JComboBox<>(RuleType.values());
    private final JComboBox<String>   setCombo     = new JComboBox<>();
    private final JComboBox<String>   targetCombo  = new JComboBox<>();
    private final JTextField          messageField = new JTextField(40);
    private final JComboBox<String>   groupCombo   = new JComboBox<>();

    // Dynamic params area
    private final JPanel paramsPanel = new JPanel(new GridBagLayout());

    // ── RequiredValue fields ──────────────────────────────────────────────────
    // Mode selector replaces the old "dump everything" approach
    private static final String[] VALUE_CHECK_MODES = {
        "Must not be empty",
        "Must match specific value(s)",
        "Length constraint",
        "Regex pattern",
        "Numeric comparison"
    };
    private final JComboBox<String> valueCheckModeCombo = new JComboBox<>(VALUE_CHECK_MODES);

    // Mode-specific fields (shown/hidden based on mode)
    private final JTextField valuesField       = new JTextField(25);   // "Must match specific value(s)"
    private final JTextField minLenField       = new JTextField(8);    // "Length constraint"
    private final JTextField maxLenField       = new JTextField(8);    // "Length constraint"
    private final JTextField rvPatternField    = new JTextField(25);   // "Regex pattern"
    // Numeric comparison
    private static final String[] NUMERIC_COMPARISONS = {
        "equals (=)", "not equals (\u2260)",
        "greater than (>)", "at least (\u2265)",
        "less than (<)", "at most (\u2264)",
        "between"
    };
    private final JComboBox<String> numericCompCombo = new JComboBox<>(NUMERIC_COMPARISONS);
    private final JTextField numericValueField = new JTextField(8);
    private final JTextField numericMinField   = new JTextField(8);
    private final JTextField numericMaxField   = new JTextField(8);

    // Panels for each mode (shown/hidden)
    private JPanel rvMatchPanel;
    private JPanel rvLengthPanel;
    private JPanel rvPatternPanel;
    private JPanel rvNumericPanel;

    // RequiredStereotype — rebuilt in rebuildParams()
    private CheckboxListField requiredStereoField;

    // RequiredStereotypeOneOf — rebuilt in rebuildParams()
    private CheckboxListField oneOfStereoField;

    // NamingPattern
    private final JTextField patternField = new JTextField(25);

    // ── RelationExists fields ─────────────────────────────────────────────────
    private static final List<String> KNOWN_RELATION_KINDS = Arrays.asList(
            "any", "dependency", "association", "generalization",
            "usage", "realization", "abstraction", "link"
    );
    private CheckboxListField       relKindField;
    private final JComboBox<String> directionCombo = new JComboBox<>(
            new String[]{"any", "outgoing", "incoming"});
    private CheckboxListField       relStereoField;
    // Human-readable count: "At least / Exactly / At most / More than / Fewer than" + spinner
    private static final String[] REL_COUNT_MODES = {
        "At least", "Exactly", "At most", "More than", "Fewer than"
    };
    private final JComboBox<String> relCountModeCombo = new JComboBox<>(REL_COUNT_MODES);
    private final JSpinner relCountSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 9999, 1));

    // OwnerStereotypeConstraint
    private CheckboxListField       ownerStereoField;
    private CheckboxListField       allowedKindsField;
    private static final List<String> ALL_ELEMENT_KINDS;
    static {
        List<String> kinds = new ArrayList<>();
        for (ElementKind ek : ElementKind.values()) {
            kinds.add(ek.name());
        }
        ALL_ELEMENT_KINDS = Collections.unmodifiableList(kinds);
    }

    // Stored references for target alias row (to show/hide based on rule type)
    private JComponent targetLabel;
    private JComponent targetField;

    private WizardState.RuleRequest result = null;
    private final JButton okBtn = new JButton("Save Rule");

    public RuleDialog(Window parent, WizardState state,
                      FastDetectionResult fast,
                      WizardState.RuleRequest prefill) {
        super(parent, "Configure Rule", ModalityType.APPLICATION_MODAL);
        this.state = state;
        this.fast  = fast;

        List<String> stereos = new ArrayList<>();
        if (fast != null) {
            stereos.addAll(fast.countsByStereotype().keySet());
            Collections.sort(stereos);
        }
        this.detectedStereos = stereos;

        setSize(560, 680);
        setLocationRelativeTo(parent);
        build(prefill);
    }

    private void build(WizardState.RuleRequest pre) {
        setLayout(new BorderLayout(5, 5));

        typeCombo.setSelectedItem(RuleType.REQUIRED_VALUE);

        // ── Static form ───────────────────────────────────────────────────────
        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(12, 20, 5, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(4, 5, 4, 5);
        gbc.anchor  = GridBagConstraints.WEST;
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        int row = 0;
        addFormRow(top, gbc, row++, "ID *",          "rule.id",   idField);
        addFormRow(top, gbc, row++, "Rule Type *",    "rule.type", typeCombo);

        setCombo.addItem("");
        for (ElementSetDefinition s : state.sets()) setCombo.addItem(s.id());
        addFormRow(top, gbc, row++, "Applies To Set", "rule.appliesToSet", setCombo);

        targetCombo.addItem("");
        for (AliasDefinition a : state.aliases()) targetCombo.addItem(a.id());
        targetLabel = HelpIcon.labelWithHelp("Target Alias:", "rule.target");
        targetField = targetCombo;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.gridwidth = 1;
        top.add(targetLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        top.add(targetField, gbc);
        row++;

        groupCombo.setEditable(true);
        groupCombo.addItem("");
        Set<String> existingGroups = new LinkedHashSet<>();
        for (WizardState.RuleRequest r : state.rules()) {
            if (r.group() != null && !r.group().trim().isEmpty()) {
                existingGroups.add(r.group());
            }
        }
        for (String g : existingGroups) groupCombo.addItem(g);
        addFormRow(top, gbc, row++, "Group",          "rule.group", groupCombo);
        addFormRow(top, gbc, row++, "Message",        "rule.message", messageField);

        add(top, BorderLayout.NORTH);

        // ── Dynamic params ────────────────────────────────────────────────────
        paramsPanel.setBorder(BorderFactory.createTitledBorder("Rule Parameters"));
        JScrollPane paramsScroll = new JScrollPane(paramsPanel);
        paramsScroll.getVerticalScrollBar().setUnitIncrement(16);
        paramsScroll.setPreferredSize(new Dimension(500, 300));
        add(paramsScroll, BorderLayout.CENTER);

        typeCombo.addActionListener(e -> { rebuildParams(); updateTargetVisibility(); revalidateLive(); });
        rebuildParams();
        updateTargetVisibility();

        if (pre != null) prefill(pre);

        FieldValidation.onChange(idField,      this::revalidateLive);
        FieldValidation.onChange(patternField, this::revalidateLive);
        FieldValidation.onChange(targetCombo,  this::revalidateLive);
        revalidateLive();

        // ── Buttons ───────────────────────────────────────────────────────────
        JButton cancelBtn = new JButton("Cancel");
        okBtn.putClientProperty("FlatLaf.style", AccentColors.PURPLE_HOVER_STYLE);
        cancelBtn.putClientProperty("FlatLaf.style", AccentColors.PURPLE_HOVER_STYLE);
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnRow.add(cancelBtn);
        btnRow.add(okBtn);
        add(btnRow, BorderLayout.SOUTH);

        cancelBtn.addActionListener(e -> dispose());
        okBtn.addActionListener(e -> {
            if (!validateForm()) return;
            result = buildRequest();
            dispose();
        });
    }

    private void rebuildParams() {
        paramsPanel.removeAll();
        RuleType type = (RuleType) typeCombo.getSelectedItem();
        if (type == null) { paramsPanel.revalidate(); paramsPanel.repaint(); return; }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(4, 10, 4, 10);
        gbc.anchor  = GridBagConstraints.NORTHWEST;
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        int row = 0;
        switch (type) {
            case REQUIRED_VALUE:
                addFormRow(paramsPanel, gbc, row++, "Check Mode",
                        "rule.params.requiredValue.checkMode", valueCheckModeCombo);
                valueCheckModeCombo.addActionListener(e -> updateValueCheckModeFields());

                // Build sub-panels for each mode
                rvMatchPanel = new JPanel(new GridBagLayout());
                rvLengthPanel = new JPanel(new GridBagLayout());
                rvPatternPanel = new JPanel(new GridBagLayout());
                rvNumericPanel = new JPanel(new GridBagLayout());

                GridBagConstraints sgbc = new GridBagConstraints();
                sgbc.insets = new Insets(3, 5, 3, 5);
                sgbc.anchor = GridBagConstraints.WEST;
                sgbc.fill = GridBagConstraints.HORIZONTAL;
                sgbc.weightx = 1;

                // Match panel
                addFormRow(rvMatchPanel, sgbc, 0, "Allowed values",
                        "rule.params.requiredValue.values", valuesField);

                // Length panel
                addFormRow(rvLengthPanel, sgbc, 0, "Min Length",
                        "rule.params.requiredValue.minLength", minLenField);
                addFormRow(rvLengthPanel, sgbc, 1, "Max Length",
                        "rule.params.requiredValue.maxLength", maxLenField);

                // Pattern panel
                addFormRow(rvPatternPanel, sgbc, 0, "Regex pattern *",
                        "rule.params.requiredValue.pattern", rvPatternField);

                // Numeric panel — each sub-row in its own panel for show/hide
                JPanel numCompRow = new JPanel(new GridBagLayout());
                addFormRow(numCompRow, sgbc, 0, "Comparison",
                        "rule.params.requiredValue.numericComp", numericCompCombo);
                JPanel numValueRow = new JPanel(new GridBagLayout());
                addFormRow(numValueRow, sgbc, 0, "Value",
                        "rule.params.requiredValue.numericValue", numericValueField);
                JPanel numMinRow = new JPanel(new GridBagLayout());
                addFormRow(numMinRow, sgbc, 0, "Range Min",
                        "rule.params.requiredValue.rangeMin", numericMinField);
                JPanel numMaxRow = new JPanel(new GridBagLayout());
                addFormRow(numMaxRow, sgbc, 0, "Range Max",
                        "rule.params.requiredValue.rangeMax", numericMaxField);

                sgbc.gridx = 0; sgbc.gridy = 0; sgbc.gridwidth = 2;
                rvNumericPanel.add(numCompRow, sgbc);
                sgbc.gridy = 1;
                rvNumericPanel.add(numValueRow, sgbc);
                sgbc.gridy = 2;
                rvNumericPanel.add(numMinRow, sgbc);
                sgbc.gridy = 3;
                rvNumericPanel.add(numMaxRow, sgbc);

                numericCompCombo.addActionListener(e -> updateNumericFields());

                gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
                paramsPanel.add(rvMatchPanel, gbc);
                gbc.gridy = row++;
                paramsPanel.add(rvLengthPanel, gbc);
                gbc.gridy = row++;
                paramsPanel.add(rvPatternPanel, gbc);
                gbc.gridy = row++;
                paramsPanel.add(rvNumericPanel, gbc);

                updateValueCheckModeFields();
                break;

            case REQUIRED_STEREOTYPE:
                gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
                paramsPanel.add(HelpIcon.labelWithHelp("Stereotype *:", "rule.params.requiredStereotype.stereotype"), gbc);
                requiredStereoField = new CheckboxListField(detectedStereos);
                requiredStereoField.addChangeListener(this::revalidateLive);
                gbc.gridy = row++; gbc.weighty = 1; gbc.fill = GridBagConstraints.BOTH;
                paramsPanel.add(requiredStereoField, gbc);
                gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
                break;

            case REQUIRED_STEREOTYPE_ONE_OF:
                gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
                paramsPanel.add(HelpIcon.labelWithHelp("Stereotypes (pick at least 1) *:", "rule.params.requiredStereotypeOneOf.stereotypes"), gbc);
                oneOfStereoField = new CheckboxListField(detectedStereos);
                oneOfStereoField.addChangeListener(this::revalidateLive);
                gbc.gridy = row++; gbc.weighty = 1; gbc.fill = GridBagConstraints.BOTH;
                paramsPanel.add(oneOfStereoField, gbc);
                gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
                break;

            case NAMING_PATTERN:
                addFormRow(paramsPanel, gbc, row++, "Regex pattern *", "rule.params.namingPattern.pattern", patternField);
                break;

            case RELATION_EXISTS:
                gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
                paramsPanel.add(HelpIcon.labelWithHelp("Relation Kind:", "rule.params.relationExists.relationKind"), gbc);
                relKindField = new CheckboxListField(KNOWN_RELATION_KINDS);
                gbc.gridy = row++; gbc.weighty = 0.5; gbc.fill = GridBagConstraints.BOTH;
                paramsPanel.add(relKindField, gbc);
                gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;

                addFormRow(paramsPanel, gbc, row++, "Direction",
                        "rule.params.relationExists.direction", directionCombo);

                gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
                paramsPanel.add(HelpIcon.labelWithHelp("Relation Stereotypes:", "rule.params.relationExists.relationStereotypes"), gbc);
                relStereoField = new CheckboxListField(detectedStereos);
                gbc.gridy = row++; gbc.weighty = 0.5; gbc.fill = GridBagConstraints.BOTH;
                paramsPanel.add(relStereoField, gbc);
                gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;

                // Human-readable count row: "Require [At least v] [1] relation(s)"
                JPanel countRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                countRow.add(new JLabel("Require"));
                countRow.add(relCountModeCombo);
                countRow.add(relCountSpinner);
                countRow.add(new JLabel("relation(s)"));
                gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
                paramsPanel.add(HelpIcon.labelWithHelp("Required Count:", "rule.params.relationExists.count"), gbc);
                gbc.gridy = row++;
                paramsPanel.add(countRow, gbc);
                break;

            case OWNER_STEREOTYPE_CONSTRAINT:
                gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
                paramsPanel.add(HelpIcon.labelWithHelp("Owner Stereotype (pick 1) *:",
                        "rule.params.ownerConstraint.ownerStereotype"), gbc);
                ownerStereoField = new CheckboxListField(detectedStereos);
                ownerStereoField.addChangeListener(this::revalidateLive);
                gbc.gridy = row++; gbc.weighty = 0.5; gbc.fill = GridBagConstraints.BOTH;
                paramsPanel.add(ownerStereoField, gbc);
                gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;

                gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
                paramsPanel.add(HelpIcon.labelWithHelp("Allowed Kinds (min 1) *:",
                        "rule.params.ownerConstraint.allowedKinds"), gbc);
                allowedKindsField = new CheckboxListField(ALL_ELEMENT_KINDS);
                allowedKindsField.addChangeListener(this::revalidateLive);
                gbc.gridy = row++; gbc.weighty = 1; gbc.fill = GridBagConstraints.BOTH;
                paramsPanel.add(allowedKindsField, gbc);
                gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
                break;
        }

        paramsPanel.revalidate();
        paramsPanel.repaint();
    }

    /** Shows/hides RequiredValue sub-panels based on the selected check mode. */
    private void updateValueCheckModeFields() {
        String mode = (String) valueCheckModeCombo.getSelectedItem();
        if (mode == null) mode = VALUE_CHECK_MODES[0];

        boolean showMatch   = "Must match specific value(s)".equals(mode);
        boolean showLength  = "Length constraint".equals(mode);
        boolean showPattern = "Regex pattern".equals(mode);
        boolean showNumeric = "Numeric comparison".equals(mode);

        if (rvMatchPanel != null)   rvMatchPanel.setVisible(showMatch);
        if (rvLengthPanel != null)  rvLengthPanel.setVisible(showLength);
        if (rvPatternPanel != null) rvPatternPanel.setVisible(showPattern);
        if (rvNumericPanel != null) rvNumericPanel.setVisible(showNumeric);

        if (showNumeric) updateNumericFields();

        paramsPanel.revalidate();
        paramsPanel.repaint();
    }

    /** Shows/hides the "between" min/max rows vs the single value row. */
    private void updateNumericFields() {
        String comp = (String) numericCompCombo.getSelectedItem();
        boolean isBetween = "between".equals(comp);
        // The value, min, max fields are each wrapped in their own panel
        Container valueRow = numericValueField.getParent();
        Container minRow = numericMinField.getParent();
        Container maxRow = numericMaxField.getParent();
        if (valueRow != null) valueRow.setVisible(!isBetween);
        if (minRow != null)   minRow.setVisible(isBetween);
        if (maxRow != null)   maxRow.setVisible(isBetween);
        if (rvNumericPanel != null) {
            rvNumericPanel.revalidate();
            rvNumericPanel.repaint();
        }
    }

    @SuppressWarnings("unchecked")
    private void prefill(WizardState.RuleRequest r) {
        idField.setText(r.id());

        try { typeCombo.setSelectedItem(RuleType.valueOf(r.ruleType())); }
        catch (Exception ignored) {}
        rebuildParams();

        if (r.elementSetId()  != null) setCombo.setSelectedItem(r.elementSetId());
        if (r.targetAliasId() != null) targetCombo.setSelectedItem(r.targetAliasId());
        if (r.message()       != null) messageField.setText(r.message());
        if (r.group() != null && !r.group().isEmpty()) groupCombo.setSelectedItem(r.group());

        Map<String, Object> p = r.params();
        RuleType type = (RuleType) typeCombo.getSelectedItem();
        if (type == null) return;

        switch (type) {
            case REQUIRED_VALUE:
                // Determine which mode to select based on existing params
                if (p.containsKey("pattern") || "matches".equals(p.get("operator"))) {
                    valueCheckModeCombo.setSelectedItem("Regex pattern");
                    if (p.get("pattern") != null) rvPatternField.setText(p.get("pattern").toString());
                } else if (p.containsKey("minLength") || p.containsKey("maxLength")) {
                    valueCheckModeCombo.setSelectedItem("Length constraint");
                    if (p.get("minLength") != null) minLenField.setText(p.get("minLength").toString());
                    if (p.get("maxLength") != null) maxLenField.setText(p.get("maxLength").toString());
                } else if (p.containsKey("values") || "in".equals(p.get("operator"))
                        || "not_in".equals(p.get("operator")) || "eq".equals(p.get("operator"))) {
                    valueCheckModeCombo.setSelectedItem("Must match specific value(s)");
                    // Populate values field
                    Object vals = p.get("values");
                    Object singleVal = p.get("value");
                    if (vals instanceof List) {
                        StringBuilder sb = new StringBuilder();
                        for (Object item : (List<?>) vals) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(item != null ? item.toString() : "");
                        }
                        valuesField.setText(sb.toString());
                    } else if (singleVal != null) {
                        valuesField.setText(singleVal.toString());
                    }
                } else if (isNumericOp(p.get("operator"))) {
                    valueCheckModeCombo.setSelectedItem("Numeric comparison");
                    prefillNumericComp(p);
                } else {
                    // Default: nonEmpty
                    valueCheckModeCombo.setSelectedItem("Must not be empty");
                }
                updateValueCheckModeFields();
                break;

            case REQUIRED_STEREOTYPE:
                if (requiredStereoField != null && p.get("requiredStereotypes") != null) {
                    requiredStereoField.setSelectedValues(
                            Collections.singletonList(p.get("requiredStereotypes").toString()));
                }
                break;

            case REQUIRED_STEREOTYPE_ONE_OF:
                if (oneOfStereoField != null && p.get("anyOf") != null) {
                    Object v = p.get("anyOf");
                    oneOfStereoField.setSelectedValues(v instanceof List
                            ? (List<String>) v
                            : Collections.singletonList(v.toString()));
                }
                break;

            case NAMING_PATTERN:
                if (p.get("pattern") != null) patternField.setText(p.get("pattern").toString());
                break;

            case RELATION_EXISTS:
                if (relKindField != null && p.get("relationKind") != null)
                    relKindField.setSelectedValues(Collections.singletonList(p.get("relationKind").toString()));
                if (p.get("direction") != null)
                    directionCombo.setSelectedItem(p.get("direction").toString());
                if (relStereoField != null && p.get("relationStereotypes") != null) {
                    Object v = p.get("relationStereotypes");
                    relStereoField.setSelectedValues(v instanceof List
                            ? (List<String>) v : Collections.singletonList(v.toString()));
                }
                // Map operator back to human-readable
                String op = p.get("operator") != null ? p.get("operator").toString() : "gte";
                relCountModeCombo.setSelectedItem(operatorToCountMode(op));
                if (p.get("value") != null) {
                    try { relCountSpinner.setValue(Integer.parseInt(p.get("value").toString())); }
                    catch (NumberFormatException ignored) {}
                }
                break;

            case OWNER_STEREOTYPE_CONSTRAINT:
                if (ownerStereoField != null && p.get("ownerStereotype") != null)
                    ownerStereoField.setSelectedValues(
                            Collections.singletonList(p.get("ownerStereotype").toString()));
                if (allowedKindsField != null && p.get("allowedKinds") != null) {
                    Object v = p.get("allowedKinds");
                    allowedKindsField.setSelectedValues(v instanceof List
                            ? (List<String>) v : Collections.singletonList(v.toString()));
                }
                break;
        }
    }

    private static boolean isNumericOp(Object op) {
        if (op == null) return false;
        String s = op.toString();
        return "gt".equals(s) || "gte".equals(s) || "lt".equals(s) || "lte".equals(s)
                || "neq".equals(s) || "between".equals(s);
    }

    private void prefillNumericComp(Map<String, Object> p) {
        String op = p.get("operator") != null ? p.get("operator").toString() : "eq";
        switch (op) {
            case "eq":      numericCompCombo.setSelectedItem("equals (=)"); break;
            case "neq":     numericCompCombo.setSelectedItem("not equals (\u2260)"); break;
            case "gt":      numericCompCombo.setSelectedItem("greater than (>)"); break;
            case "gte":     numericCompCombo.setSelectedItem("at least (\u2265)"); break;
            case "lt":      numericCompCombo.setSelectedItem("less than (<)"); break;
            case "lte":     numericCompCombo.setSelectedItem("at most (\u2264)"); break;
            case "between": numericCompCombo.setSelectedItem("between"); break;
        }
        if (p.get("value") != null) numericValueField.setText(p.get("value").toString());
        if (p.get("min") != null) numericMinField.setText(p.get("min").toString());
        if (p.get("max") != null) numericMaxField.setText(p.get("max").toString());
        updateNumericFields();
    }

    private void updateTargetVisibility() {
        RuleType type = (RuleType) typeCombo.getSelectedItem();
        boolean needsTarget = type == RuleType.REQUIRED_VALUE || type == RuleType.NAMING_PATTERN;
        if (targetLabel != null) targetLabel.setVisible(needsTarget);
        if (targetField != null) targetField.setVisible(needsTarget);
    }

    private void revalidateLive() {
        boolean valid = !idField.getText().trim().isEmpty();
        if (valid) FieldValidation.markValid(idField); else FieldValidation.markInvalid(idField);

        RuleType type = (RuleType) typeCombo.getSelectedItem();
        FieldValidation.markValid(targetCombo);
        if (type != null) {
            switch (type) {
                case REQUIRED_VALUE:
                    String target = (String) targetCombo.getSelectedItem();
                    boolean targetOk = target != null && !target.trim().isEmpty();
                    if (targetOk) FieldValidation.markValid(targetCombo); else FieldValidation.markInvalid(targetCombo);
                    valid = valid && targetOk;
                    break;
                case REQUIRED_STEREOTYPE:
                    boolean reqOk = requiredStereoField != null && !requiredStereoField.getSelectedValues().isEmpty();
                    if (requiredStereoField != null) requiredStereoField.setValid(reqOk);
                    valid = valid && reqOk;
                    break;
                case REQUIRED_STEREOTYPE_ONE_OF:
                    boolean oneOfOk = oneOfStereoField != null && !oneOfStereoField.getSelectedValues().isEmpty();
                    if (oneOfStereoField != null) oneOfStereoField.setValid(oneOfOk);
                    valid = valid && oneOfOk;
                    break;
                case NAMING_PATTERN:
                    boolean patternOk = !patternField.getText().trim().isEmpty();
                    if (patternOk) FieldValidation.markValid(patternField); else FieldValidation.markInvalid(patternField);
                    valid = valid && patternOk;
                    break;
                case OWNER_STEREOTYPE_CONSTRAINT:
                    boolean ownerOk = ownerStereoField != null && !ownerStereoField.getSelectedValues().isEmpty();
                    if (ownerStereoField != null) ownerStereoField.setValid(ownerOk);
                    boolean kindsOk = allowedKindsField != null && !allowedKindsField.getSelectedValues().isEmpty();
                    if (allowedKindsField != null) allowedKindsField.setValid(kindsOk);
                    valid = valid && ownerOk && kindsOk;
                    break;
                default:
                    break;
            }
        }
        okBtn.setEnabled(valid);
    }

    @SuppressWarnings("incomplete-switch")
    private boolean validateForm() {
        if (idField.getText().trim().isEmpty()) { warn("ID is required."); return false; }
        if (typeCombo.getSelectedItem() == null) { warn("Rule type is required."); return false; }
        RuleType type = (RuleType) typeCombo.getSelectedItem();
        switch (type) {
            case REQUIRED_VALUE:
                String target = (String) targetCombo.getSelectedItem();
                if (target == null || target.trim().isEmpty()) {
                    warn("Target Alias is required for RequiredValue."); return false;
                }
                break;
            case REQUIRED_STEREOTYPE:
                if (requiredStereoField == null || requiredStereoField.getSelectedValues().isEmpty()) {
                    warn("Stereotype is required."); return false;
                }
                break;
            case REQUIRED_STEREOTYPE_ONE_OF:
                if (oneOfStereoField == null || oneOfStereoField.getSelectedValues().isEmpty()) {
                    warn("At least one stereotype is required."); return false;
                }
                break;
            case NAMING_PATTERN:
                if (patternField.getText().trim().isEmpty()) {
                    warn("Pattern is required."); return false;
                }
                break;
            case OWNER_STEREOTYPE_CONSTRAINT:
                if (ownerStereoField == null || ownerStereoField.getSelectedValues().isEmpty()) {
                    warn("Owner Stereotype is required."); return false;
                }
                if (allowedKindsField == null || allowedKindsField.getSelectedValues().isEmpty()) {
                    warn("At least one Allowed Kind is required."); return false;
                }
                break;
        }
        return true;
    }

    @SuppressWarnings("incomplete-switch")
    private WizardState.RuleRequest buildRequest() {
        RuleType type = (RuleType) typeCombo.getSelectedItem();
        Map<String, Object> params = new LinkedHashMap<>();

        switch (type) {
            case REQUIRED_VALUE:
                String mode = (String) valueCheckModeCombo.getSelectedItem();
                if ("Must not be empty".equals(mode)) {
                    params.put("nonEmpty", true);
                } else if ("Must match specific value(s)".equals(mode)) {
                    List<String> vals = splitValues(valuesField.getText());
                    if (vals.size() == 1) {
                        params.put("operator", "in");
                        params.put("values", vals);
                    } else if (!vals.isEmpty()) {
                        params.put("operator", "in");
                        params.put("values", vals);
                    }
                } else if ("Length constraint".equals(mode)) {
                    if (!minLenField.getText().trim().isEmpty())
                        params.put("minLength", Integer.parseInt(minLenField.getText().trim()));
                    if (!maxLenField.getText().trim().isEmpty())
                        params.put("maxLength", Integer.parseInt(maxLenField.getText().trim()));
                } else if ("Regex pattern".equals(mode)) {
                    params.put("operator", "matches");
                    params.put("pattern", rvPatternField.getText().trim());
                } else if ("Numeric comparison".equals(mode)) {
                    String comp = (String) numericCompCombo.getSelectedItem();
                    params.put("operator", numericCompToOperator(comp));
                    if ("between".equals(comp)) {
                        if (!numericMinField.getText().trim().isEmpty())
                            params.put("min", numericMinField.getText().trim());
                        if (!numericMaxField.getText().trim().isEmpty())
                            params.put("max", numericMaxField.getText().trim());
                    } else {
                        if (!numericValueField.getText().trim().isEmpty())
                            params.put("value", numericValueField.getText().trim());
                    }
                }
                break;

            case REQUIRED_STEREOTYPE:
                List<String> stereoSel = requiredStereoField.getSelectedValues();
                if (!stereoSel.isEmpty()) params.put("requiredStereotypes", stereoSel.get(0));
                break;

            case REQUIRED_STEREOTYPE_ONE_OF:
                params.put("anyOf", oneOfStereoField.getSelectedValues());
                break;

            case NAMING_PATTERN:
                params.put("pattern", patternField.getText().trim());
                break;

            case RELATION_EXISTS:
                if (relKindField != null && !relKindField.getSelectedValues().isEmpty())
                    params.put("relationKind", relKindField.getSelectedValues().get(0));
                params.put("direction", directionCombo.getSelectedItem());
                if (relStereoField != null && !relStereoField.getSelectedValues().isEmpty())
                    params.put("relationStereotypes", relStereoField.getSelectedValues());
                params.put("operator", countModeToOperator((String) relCountModeCombo.getSelectedItem()));
                params.put("value", ((Number) relCountSpinner.getValue()).intValue());
                break;

            case OWNER_STEREOTYPE_CONSTRAINT:
                if (ownerStereoField != null && !ownerStereoField.getSelectedValues().isEmpty())
                    params.put("ownerStereotype", ownerStereoField.getSelectedValues().get(0));
                if (allowedKindsField != null && !allowedKindsField.getSelectedValues().isEmpty())
                    params.put("allowedKinds", allowedKindsField.getSelectedValues());
                break;
        }

        String setId    = (String) setCombo.getSelectedItem();
        String targetId = (String) targetCombo.getSelectedItem();
        String groupVal = groupCombo.getSelectedItem() != null
                ? groupCombo.getSelectedItem().toString().trim() : null;
        if (groupVal != null && groupVal.isEmpty()) groupVal = null;

        return new WizardState.RuleRequest(
                idField.getText().trim(), null, type.name(),
                (targetId == null || targetId.trim().isEmpty()) ? null : targetId,
                (setId    == null || setId.trim().isEmpty())    ? null : setId,
                params, nullable(messageField.getText()), true, groupVal
        );
    }

    // ── Operator mapping helpers ──────────────────────────────────────────────

    private static String countModeToOperator(String mode) {
        if (mode == null) return "gte";
        switch (mode) {
            case "At least":    return "gte";
            case "Exactly":     return "eq";
            case "At most":     return "lte";
            case "More than":   return "gt";
            case "Fewer than":  return "lt";
            default:            return "gte";
        }
    }

    private static String operatorToCountMode(String op) {
        if (op == null) return "At least";
        switch (op) {
            case "gte": return "At least";
            case "eq":  return "Exactly";
            case "lte": return "At most";
            case "gt":  return "More than";
            case "lt":  return "Fewer than";
            default:    return "At least";
        }
    }

    private static String numericCompToOperator(String comp) {
        if (comp == null) return "eq";
        if (comp.startsWith("equals"))       return "eq";
        if (comp.startsWith("not equals"))   return "neq";
        if (comp.startsWith("greater than")) return "gt";
        if (comp.startsWith("at least"))     return "gte";
        if (comp.startsWith("less than"))    return "lt";
        if (comp.startsWith("at most"))      return "lte";
        if (comp.equals("between"))          return "between";
        return "eq";
    }

    // ── General helpers ───────────────────────────────────────────────────────

    private void warn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Validation", JOptionPane.WARNING_MESSAGE);
    }

    private static void addFormRow(JPanel p, GridBagConstraints gbc,
                                   int row, String label, String helpKey, JComponent field) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.gridwidth = 1;
        p.add(HelpIcon.labelWithHelp(label + ":", helpKey), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        p.add(field, gbc);
    }

    private static String nullable(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    private static List<String> splitValues(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public Optional<WizardState.RuleRequest> getResult() {
        return Optional.ofNullable(result);
    }
}
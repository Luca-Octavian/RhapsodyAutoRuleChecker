// ui/wizard/RuleDialog.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.AliasDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardState;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public final class RuleDialog extends JDialog {

    private final WizardState         state;
    private final FastDetectionResult fast;
    private final List<String>        detectedStereos;

    // Câmpuri comune
    private final JTextField          idField      = new JTextField(20);
    private final JTextField          titleField   = new JTextField(30);
    private final JComboBox<RuleType> typeCombo    = new JComboBox<>(RuleType.values());
    private final JComboBox<String>   setCombo     = new JComboBox<>();
    private final JComboBox<String>   targetCombo  = new JComboBox<>();
    private final JTextField          messageField = new JTextField(40);

    // Zona dinamica params
    private final JPanel paramsPanel = new JPanel(new GridBagLayout());

    // ── Câmpuri params per tip ────────────────────────────────────────────────

    // RequiredValue
    private final JCheckBox  nonEmptyCheck = new JCheckBox("Non-empty");
    private final JTextField minLenField   = new JTextField(8);
    private final JTextField maxLenField   = new JTextField(8);
    private final JTextField operatorField = new JTextField(8);
    private final JTextField valuesField   = new JTextField(25);

    // RequiredStereotype — rebuilt in rebuildParams()
    private CheckboxListField requiredStereoField;

    // RequiredStereotypeOneOf — rebuilt in rebuildParams()
    private CheckboxListField oneOfStereoField;

    // NamingPattern
    private final JTextField patternField = new JTextField(25);

    // RelationExists
    private final JTextField        relKindField   = new JTextField(15);
    private final JComboBox<String> directionCombo = new JComboBox<>(
            new String[]{"any", "outgoing", "incoming"});
    private CheckboxListField       relStereoField;
    private final JTextField        relOpField    = new JTextField(8);
    private final JTextField        relValueField = new JTextField(8);

    private WizardState.RuleRequest result = null;

    public RuleDialog(Window parent, WizardState state, FastDetectionResult fast,
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

        setSize(560, 640);
        setLocationRelativeTo(parent);
        build(prefill);
    }

    private void build(WizardState.RuleRequest pre) {
        setLayout(new BorderLayout(5, 5));

        // ── Form statica ──────────────────────────────────────────────────────
        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(12, 20, 5, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(4, 5, 4, 5);
        gbc.anchor  = GridBagConstraints.WEST;
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        int row = 0;
        addFormRow(top, gbc, row++, "ID *",          idField);
        addFormRow(top, gbc, row++, "Title",          titleField);
        addFormRow(top, gbc, row++, "Rule Type *",    typeCombo);

        // Populeaza setCombo din state.sets()
        setCombo.addItem("");
        for (ElementSetDefinition s : state.sets()) {
            setCombo.addItem(s.id());
        }
        addFormRow(top, gbc, row++, "Applies To Set", setCombo);

        // Populeaza targetCombo din state.aliases()
        targetCombo.addItem("");
        for (AliasDefinition a : state.aliases()) {
            targetCombo.addItem(a.id());
        }
        addFormRow(top, gbc, row++, "Target Alias",   targetCombo);
        addFormRow(top, gbc, row++, "Message",        messageField);

        add(top, BorderLayout.NORTH);

        // ── Params dinamici ───────────────────────────────────────────────────
        paramsPanel.setBorder(BorderFactory.createTitledBorder("Rule Parameters"));
        JScrollPane paramsScroll = new JScrollPane(paramsPanel);
        paramsScroll.setPreferredSize(new Dimension(500, 260));
        add(paramsScroll, BorderLayout.CENTER);

        typeCombo.addActionListener(e -> rebuildParams());
        rebuildParams();

        // Prefill daca editam
        if (pre != null) prefill(pre);

        // ── Butoane ───────────────────────────────────────────────────────────
        JButton okBtn     = new JButton("Save Rule");
        JButton cancelBtn = new JButton("Cancel");
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
                addFormRow(paramsPanel, gbc, row++, "Non-empty",               nonEmptyCheck);
                addFormRow(paramsPanel, gbc, row++, "Min Length",              minLenField);
                addFormRow(paramsPanel, gbc, row++, "Max Length",              maxLenField);
                addFormRow(paramsPanel, gbc, row++, "Operator (in/eq/gte/lte)", operatorField);
                addFormRow(paramsPanel, gbc, row++, "Values (CSV)",            valuesField);
                break;

            case REQUIRED_STEREOTYPE:
                gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
                paramsPanel.add(new JLabel("Stereotype *:"), gbc);
                requiredStereoField = new CheckboxListField(detectedStereos);
                gbc.gridy = row++; gbc.weighty = 1;
                gbc.fill  = GridBagConstraints.BOTH;
                paramsPanel.add(requiredStereoField, gbc);
                gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
                break;

            case REQUIRED_STEREOTYPE_ONE_OF:
                gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
                paramsPanel.add(new JLabel("Stereotypes (min 1) *:"), gbc);
                oneOfStereoField = new CheckboxListField(detectedStereos);
                gbc.gridy = row++; gbc.weighty = 1;
                gbc.fill  = GridBagConstraints.BOTH;
                paramsPanel.add(oneOfStereoField, gbc);
                gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
                break;

            case NAMING_PATTERN:
                addFormRow(paramsPanel, gbc, row++, "Regex pattern *", patternField);
                break;

            case RELATION_EXISTS:
                addFormRow(paramsPanel, gbc, row++, "Relation Kind",          relKindField);
                addFormRow(paramsPanel, gbc, row++, "Direction",              directionCombo);
                gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
                paramsPanel.add(new JLabel("Relation Stereotypes:"), gbc);
                relStereoField = new CheckboxListField(detectedStereos);
                gbc.gridy = row++; gbc.weighty = 1;
                gbc.fill  = GridBagConstraints.BOTH;
                paramsPanel.add(relStereoField, gbc);
                gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
                addFormRow(paramsPanel, gbc, row++, "Operator (gte/lte/eq)", relOpField);
                addFormRow(paramsPanel, gbc, row++, "Value (count)",         relValueField);
                break;
        }

        paramsPanel.revalidate();
        paramsPanel.repaint();
    }

    @SuppressWarnings("unchecked")
    private void prefill(WizardState.RuleRequest r) {
        idField.setText(r.id());
        if (r.title() != null) titleField.setText(r.title());

        try { typeCombo.setSelectedItem(RuleType.valueOf(r.ruleType())); }
        catch (Exception ignored) {}
        rebuildParams();

        if (r.elementSetId()  != null) setCombo.setSelectedItem(r.elementSetId());
        if (r.targetAliasId() != null) targetCombo.setSelectedItem(r.targetAliasId());
        if (r.message()       != null) messageField.setText(r.message());

        Map<String, Object> p = r.params();
        RuleType type = (RuleType) typeCombo.getSelectedItem();
        if (type == null) return;

        switch (type) {
            case REQUIRED_VALUE:
                nonEmptyCheck.setSelected(Boolean.TRUE.equals(p.get("nonEmpty")));
                if (p.get("minLength") != null) minLenField.setText(p.get("minLength").toString());
                if (p.get("maxLength") != null) maxLenField.setText(p.get("maxLength").toString());
                if (p.get("operator")  != null) operatorField.setText(p.get("operator").toString());
                if (p.get("values")    != null) {
                    Object v = p.get("values");
                    valuesField.setText(v instanceof List
                            ? String.join(", ", (List<String>) v)
                            : v.toString());
                }
                break;

            case REQUIRED_STEREOTYPE:
                if (requiredStereoField != null && p.get("stereotype") != null) {
                    requiredStereoField.setSelectedValues(
                            Collections.singletonList(p.get("stereotype").toString()));
                }
                break;

            case REQUIRED_STEREOTYPE_ONE_OF:
                if (oneOfStereoField != null && p.get("stereotypes") != null) {
                    Object v = p.get("stereotypes");
                    oneOfStereoField.setSelectedValues(v instanceof List
                            ? (List<String>) v
                            : Collections.singletonList(v.toString()));
                }
                break;

            case NAMING_PATTERN:
                if (p.get("pattern") != null)
                    patternField.setText(p.get("pattern").toString());
                break;

            case RELATION_EXISTS:
                if (p.get("relationKind") != null)
                    relKindField.setText(p.get("relationKind").toString());
                if (p.get("direction") != null)
                    directionCombo.setSelectedItem(p.get("direction").toString());
                if (relStereoField != null && p.get("relationStereotypes") != null) {
                    Object v = p.get("relationStereotypes");
                    relStereoField.setSelectedValues(v instanceof List
                            ? (List<String>) v
                            : Collections.singletonList(v.toString()));
                }
                if (p.get("operator") != null) relOpField.setText(p.get("operator").toString());
                if (p.get("value")    != null) relValueField.setText(p.get("value").toString());
                break;
        }
    }

    @SuppressWarnings("incomplete-switch")
    private boolean validateForm() {
        if (idField.getText().trim().isEmpty()) {
            warn("ID is required."); return false;
        }
        if (typeCombo.getSelectedItem() == null) {
            warn("Rule type is required."); return false;
        }
        RuleType type = (RuleType) typeCombo.getSelectedItem();
        switch (type) {
            case REQUIRED_STEREOTYPE:
                if (requiredStereoField == null
                        || requiredStereoField.getSelectedValues().isEmpty()) {
                    warn("Stereotype is required for RequiredStereotype."); return false;
                }
                break;
            case REQUIRED_STEREOTYPE_ONE_OF:
                if (oneOfStereoField == null
                        || oneOfStereoField.getSelectedValues().isEmpty()) {
                    warn("At least one stereotype is required for RequiredStereotypeOneOf."); return false;
                }
                break;
            case NAMING_PATTERN:
                if (patternField.getText().trim().isEmpty()) {
                    warn("Pattern is required for NamingPattern."); return false;
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
                if (nonEmptyCheck.isSelected())
                    params.put("nonEmpty", true);
                if (!minLenField.getText().trim().isEmpty())
                    params.put("minLength", Integer.parseInt(minLenField.getText().trim()));
                if (!maxLenField.getText().trim().isEmpty())
                    params.put("maxLength", Integer.parseInt(maxLenField.getText().trim()));
                if (!operatorField.getText().trim().isEmpty())
                    params.put("operator", operatorField.getText().trim());
                if (!valuesField.getText().trim().isEmpty())
                    params.put("values", splitCsv(valuesField.getText()));
                break;

            case REQUIRED_STEREOTYPE:
                List<String> stereoSel = requiredStereoField.getSelectedValues();
                if (!stereoSel.isEmpty())
                    params.put("stereotype", stereoSel.get(0));
                break;

            case REQUIRED_STEREOTYPE_ONE_OF:
                params.put("stereotypes", oneOfStereoField.getSelectedValues());
                break;

            case NAMING_PATTERN:
                params.put("pattern", patternField.getText().trim());
                break;

            case RELATION_EXISTS:
                if (!relKindField.getText().trim().isEmpty())
                    params.put("relationKind", relKindField.getText().trim());
                params.put("direction", directionCombo.getSelectedItem());
                if (relStereoField != null && !relStereoField.getSelectedValues().isEmpty())
                    params.put("relationStereotypes", relStereoField.getSelectedValues());
                if (!relOpField.getText().trim().isEmpty())
                    params.put("operator", relOpField.getText().trim());
                if (!relValueField.getText().trim().isEmpty())
                    params.put("value", Integer.parseInt(relValueField.getText().trim()));
                break;
        }

        String setId    = (String) setCombo.getSelectedItem();
        String targetId = (String) targetCombo.getSelectedItem();

        return new WizardState.RuleRequest(
                idField.getText().trim(),
                nullable(titleField.getText()),
                type.name(),
                (targetId == null || targetId.isBlank()) ? null : targetId,
                (setId    == null || setId.isBlank())    ? null : setId,
                params,
                nullable(messageField.getText())
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void warn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Validation", JOptionPane.WARNING_MESSAGE);
    }

    private static void addFormRow(JPanel p, GridBagConstraints gbc,
                                   int row, String label, JComponent field) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.gridwidth = 1;
        p.add(new JLabel(label + ":"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        p.add(field, gbc);
    }

    private static String nullable(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    private static List<String> splitCsv(String raw) {
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

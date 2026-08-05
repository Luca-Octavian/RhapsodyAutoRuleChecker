package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.TargetSpec;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardState;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.AliasKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AccentColors;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AppTheme;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help.CollapsibleSection;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help.FieldValidation;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help.HelpIcon;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.RuleFormLayout;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.RuleParameterEditor;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.RuleValueCodec;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Dialog orchestration for rule editing.
 *
 * <p>Rule-type-specific controls and parameter serialization live in
 * {@link RuleParameterEditor}; this class owns only common rule fields,
 * target configuration, dialog validation and result construction.
 */
public final class RuleDialog extends JDialog {

    private static final AliasKind[] TARGET_KINDS = {
            AliasKind.DESCRIPTION, AliasKind.NAME, AliasKind.TAGGED_VALUE,
            AliasKind.PORT_TYPE, AliasKind.PORT_DIRECTION,
            AliasKind.PORT_MULTIPLICITY
    };

    private final WizardState state;

    private final JTextField idField = new JTextField(20);
    private final JComboBox<RuleType> typeCombo =
            new JComboBox<RuleType>(RuleType.values());
    private final JComboBox<String> setCombo = new JComboBox<String>();
    private final JTextField messageField = new JTextField(40);
    private final JComboBox<String> groupCombo = new JComboBox<String>();

    private final JComboBox<AliasKind> targetKind =
            new JComboBox<AliasKind>(TARGET_KINDS);
    private final JTextField targetProfile = new JTextField(20);
    private final JTextField targetTagName = new JTextField(20);
    private final JTextField targetValues = new JTextField(20);

    private JComponent targetKindLabel;
    private JComponent targetProfileLabel;
    private JComponent targetTagNameLabel;
    private JComponent targetValuesLabel;

    private final RuleParameterEditor parameterEditor;
    private final JButton saveButton = new JButton("Save Rule");
    private WizardState.RuleRequest result;

    public RuleDialog(Window parent, WizardState state,
                      FastDetectionResult detection,
                      WizardState.RuleRequest prefill) {
        super(parent, "Configure Rule", ModalityType.APPLICATION_MODAL);
        this.state = state;
        this.parameterEditor = new RuleParameterEditor(
                detectedStereotypes(detection), this::revalidateLive);

        setSize(800, 880);
        setMinimumSize(new Dimension(600, 700));
        setLocationRelativeTo(parent);
        AppTheme.guardMinimumSize(this, new Dimension(600, 700));
        build(prefill);
    }

    private void build(WizardState.RuleRequest prefill) {
        setLayout(new BorderLayout(5, 5));
        typeCombo.setSelectedItem(RuleType.REQUIRED_VALUE);

        add(buildCommonForm(prefill), BorderLayout.NORTH);

        JScrollPane parameterScroll = new JScrollPane(parameterEditor);
        parameterScroll.getVerticalScrollBar().setUnitIncrement(16);
        parameterScroll.setPreferredSize(new Dimension(620, 340));
        add(parameterScroll, BorderLayout.CENTER);

        add(buildButtons(), BorderLayout.SOUTH);
        installListeners();

        showSelectedRuleType();
        if (prefill != null) prefill(prefill);
        revalidateLive();
    }

    private JPanel buildCommonForm(WizardState.RuleRequest prefill) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 20, 5, 20));
        GridBagConstraints gbc = RuleFormLayout.constraints();

        int row = 0;
        RuleFormLayout.addRow(panel, gbc, row++, "ID *", "rule.id", idField);
        RuleFormLayout.addRow(panel, gbc, row++, "Rule Type *",
                "rule.type", typeCombo);

        setCombo.addItem("");
        for (ElementSetDefinition set : state.sets()) setCombo.addItem(set.id());
        RuleFormLayout.addRow(panel, gbc, row++, "Applies To Set",
                "rule.appliesToSet", setCombo);

        targetKindLabel = addTargetRow(panel, gbc, row++, "Target:",
                "rule.target", targetKind);
        targetProfileLabel = addTargetRow(panel, gbc, row++, "Profile *:",
                "rule.target.taggedValue.profile", targetProfile);
        targetTagNameLabel = addTargetRow(panel, gbc, row++, "Tag Name *:",
                "rule.target.taggedValue.tagName", targetTagName);
        targetValuesLabel = addTargetRow(panel, gbc, row++, "Allowed Values:",
                "rule.target.taggedValue.allowedValues", targetValues);

        // ── Advanced section (Group + Message) ────────────────────────────────
        configureGroups();

        JPanel advancedContent = new JPanel(new GridBagLayout());
        GridBagConstraints agbc = RuleFormLayout.constraints();
        RuleFormLayout.addRow(advancedContent, agbc, 0, "Group",
                "rule.group", groupCombo);
        RuleFormLayout.addRow(advancedContent, agbc, 1, "Message",
                "rule.message", messageField);

        boolean startExpanded = prefill != null
                && ((prefill.group() != null && !prefill.group().isEmpty())
                    || (prefill.message() != null && !prefill.message().isEmpty()));

        CollapsibleSection advancedSection =
                new CollapsibleSection("Advanced", advancedContent, startExpanded);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        panel.add(advancedSection, gbc);

        targetKind.setSelectedItem(AliasKind.DESCRIPTION);
        updateTargetVisibility();
        return panel;
    }

    private JComponent addTargetRow(
            JPanel panel, GridBagConstraints gbc, int row,
            String label, String helpKey, JComponent field) {
        JComponent labelComponent = HelpIcon.labelWithHelp(label, helpKey);
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        panel.add(labelComponent, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
        return labelComponent;
    }

    private void configureGroups() {
        groupCombo.setEditable(true);
        AppTheme.styleComboBox(groupCombo);
        groupCombo.addItem("");

        Set<String> groups = new LinkedHashSet<String>();
        for (WizardState.RuleRequest rule : state.rules()) {
            String group = RuleValueCodec.nullable(rule.group());
            if (group != null) groups.add(group);
        }
        for (String group : groups) groupCombo.addItem(group);
    }

    private JPanel buildButtons() {
        JButton cancelButton = new JButton("Cancel");
        saveButton.putClientProperty(
                "FlatLaf.style", AccentColors.PURPLE_FILL_STYLE);
        cancelButton.putClientProperty(
                "FlatLaf.style", AccentColors.PURPLE_HOVER_STYLE);

        cancelButton.addActionListener(e -> dispose());
        saveButton.addActionListener(e -> save());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancelButton);
        buttons.add(saveButton);
        return buttons;
    }

    private void installListeners() {
        typeCombo.addActionListener(e -> showSelectedRuleType());
        targetKind.addActionListener(e -> {
            updateTargetVisibility();
            revalidateLive();
        });

        FieldValidation.onChange(idField, this::revalidateLive);
        FieldValidation.onChange(targetKind, this::revalidateLive);
        FieldValidation.onChange(
                parameterEditor.namingValueField(), this::revalidateLive);
    }

    private void showSelectedRuleType() {
        parameterEditor.showRuleType(selectedRuleType());
        updateTargetVisibility();
        revalidateLive();
    }

    private void updateTargetVisibility() {
        boolean targetRequired = selectedRuleType() == RuleType.REQUIRED_VALUE;
        boolean taggedValue = targetRequired
                && targetKind.getSelectedItem() == AliasKind.TAGGED_VALUE;

        setVisible(targetKindLabel, targetRequired);
        setVisible(targetKind, targetRequired);
        setVisible(targetProfileLabel, taggedValue);
        setVisible(targetProfile, taggedValue);
        setVisible(targetTagNameLabel, taggedValue);
        setVisible(targetTagName, taggedValue);
        setVisible(targetValuesLabel, taggedValue);
        setVisible(targetValues, taggedValue);

        Container parent = targetKind.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    private void revalidateLive() {
        boolean hasId = !idField.getText().trim().isEmpty();
        if (hasId) FieldValidation.markValid(idField);
        else FieldValidation.markInvalid(idField);

        parameterEditor.updateValidationMarkers();
        saveButton.setEnabled(hasId
                && selectedRuleType() != null
                && targetValidationMessage() == null
                && parameterEditor.isInputValid());
    }

    private void save() {
        String validation = validationMessage();
        if (validation != null) {
            JOptionPane.showMessageDialog(
                    this, validation, "Validation",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        result = buildRequest();
        dispose();
    }

    private String validationMessage() {
        if (idField.getText().trim().isEmpty()) return "ID is required.";
        if (selectedRuleType() == null) return "Rule type is required.";

        String targetError = targetValidationMessage();
        return targetError != null
                ? targetError : parameterEditor.validationMessage();
    }

    private String targetValidationMessage() {
        if (selectedRuleType() != RuleType.REQUIRED_VALUE
                || targetKind.getSelectedItem() != AliasKind.TAGGED_VALUE) {
            return null;
        }
        if (targetProfile.getText().trim().isEmpty()) {
            return "Profile Name is required for Tagged Value targets.";
        }
        if (targetTagName.getText().trim().isEmpty()) {
            return "Tag Name is required for Tagged Value targets.";
        }
        return null;
    }

    private WizardState.RuleRequest buildRequest() {
        RuleType type = selectedRuleType();
        String setId = RuleValueCodec.nullable(
                (String) setCombo.getSelectedItem());
        String group = groupCombo.getSelectedItem() != null
                ? RuleValueCodec.nullable(
                        groupCombo.getSelectedItem().toString())
                : null;

        return new WizardState.RuleRequest(
                idField.getText().trim(),
                null,
                type.name(),
                buildTargetSpec(),
                setId,
                parameterEditor.buildParams(),
                RuleValueCodec.nullable(messageField.getText()),
                true,
                group);
    }

    private TargetSpec buildTargetSpec() {
        if (selectedRuleType() != RuleType.REQUIRED_VALUE) return null;

        AliasKind kind = (AliasKind) targetKind.getSelectedItem();
        if (kind == null) return null;

        TargetSpec.Builder builder = TargetSpec.builder().kind(kind);
        if (kind == AliasKind.TAGGED_VALUE) {
            builder.profileName(RuleValueCodec.nullable(targetProfile.getText()));
            builder.tagName(RuleValueCodec.nullable(targetTagName.getText()));
            List<String> values =
                    RuleValueCodec.splitValues(targetValues.getText());
            if (!values.isEmpty()) builder.values(values);
        }
        return builder.build();
    }

    private void prefill(WizardState.RuleRequest request) {
        idField.setText(request.id());

        RuleType type = parseRuleType(request.ruleType());
        if (type != null) typeCombo.setSelectedItem(type);
        parameterEditor.showRuleType(selectedRuleType());

        if (request.elementSetId() != null) {
            setCombo.setSelectedItem(request.elementSetId());
        }
        if (request.message() != null) messageField.setText(request.message());
        if (request.group() != null && !request.group().isEmpty()) {
            groupCombo.setSelectedItem(request.group());
        }

        prefillTarget(request.targetSpec());
        parameterEditor.prefill(request.params());
        updateTargetVisibility();
    }

    private void prefillTarget(TargetSpec target) {
        if (target == null) return;

        targetKind.setSelectedItem(target.kind());
        if (target.profileName().isPresent()) {
            targetProfile.setText(target.profileName().get());
        }
        if (target.tagName().isPresent()) {
            targetTagName.setText(target.tagName().get());
        }
        if (!target.values().isEmpty()) {
            targetValues.setText(
                    RuleValueCodec.joinValues(target.values()));
        }
    }

    private RuleType selectedRuleType() {
        return (RuleType) typeCombo.getSelectedItem();
    }

    private static RuleType parseRuleType(String value) {
        try {
            return value != null ? RuleType.valueOf(value) : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static List<String> detectedStereotypes(
            FastDetectionResult detection) {
        if (detection == null) return Collections.emptyList();

        List<String> values = new ArrayList<String>(
                detection.countsByStereotype().keySet());
        Collections.sort(values);
        return values;
    }

    private static void setVisible(JComponent component, boolean visible) {
        if (component != null) component.setVisible(visible);
    }

    public Optional<WizardState.RuleRequest> getResult() {
        return Optional.ofNullable(result);
    }
}
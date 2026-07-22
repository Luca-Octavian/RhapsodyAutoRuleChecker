// ui/wizard/AliasDialog.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.AliasDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.AliasKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.ValueType;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help.FieldValidation;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help.HelpIcon;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AccentColors;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public final class AliasDialog extends JDialog {

    private final FastDetectionResult  fast;
    private final List<String>         detectedStereos;

    // Campuri comune
    private final JTextField           idField    = new JTextField(20);
    private final JTextField           helpField  = new JTextField(30);
    private final JComboBox<AliasKind> kindCombo  = new JComboBox<>(AliasKind.values());

    // taggedValue
    private final JTextField           profileField     = new JTextField(20);
    private final JTextField           tagNameField     = new JTextField(20);
    private final JTextField           stereoOwnerField = new JTextField(20);
    private final JComboBox<ValueType> valueTypeCombo   = new JComboBox<>(ValueType.values());
    private final JTextField           valuesField      = new JTextField(30);

    // stereotype — autocomplete din model
    private CheckboxListField stereoNameField;

    // stereotypeSet — checkbox list din model
    private CheckboxListField stereoNamesField;
    private final JTextField  setProfileField = new JTextField(20);

    // Zona dinamica
    private final JPanel dynamicPanel = new JPanel(new GridBagLayout());

    private final JButton okBtn = new JButton("Save");

    private AliasDefinition result = null;

    public AliasDialog(Window parent, FastDetectionResult fast, AliasDefinition prefill) {
        super(parent, "Define Alias", ModalityType.APPLICATION_MODAL);
        this.fast = fast;

        detectedStereos = new ArrayList<>();
        if (fast != null) {
            detectedStereos.addAll(fast.countsByStereotype().keySet());
            Collections.sort(detectedStereos);
        }

        setSize(520, 560);
        setLocationRelativeTo(parent);
        build(prefill);
    }

    private void build(AliasDefinition pre) {
        setLayout(new BorderLayout(5, 5));

        // Default pentru un alias nou: DESCRIPTION - cel mai simplu kind,
        // fara parametri suplimentari de completat. La editare (pre != null)
        // e suprascris mai jos, in prefill().
        kindCombo.setSelectedItem(AliasKind.DESCRIPTION);

        // ── Form statica ──────────────────────────────────────────────────────
        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(15, 20, 5, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(4, 5, 4, 5);
        gbc.anchor  = GridBagConstraints.WEST;
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        int row = 0;
        addFormRow(top, gbc, row++, "ID *",   "alias.id",   idField);
        addFormRow(top, gbc, row++, "Help",   "alias.help",  helpField);
        addFormRow(top, gbc, row++, "Kind *", "alias.kind",  kindCombo);
        add(top, BorderLayout.NORTH);

        // ── Zona dinamica ─────────────────────────────────────────────────────
        dynamicPanel.setBorder(BorderFactory.createTitledBorder("Kind parameters"));
        add(dynamicPanel, BorderLayout.CENTER);

        kindCombo.addActionListener(e -> { rebuildDynamic(); revalidateLive(); });
        rebuildDynamic();

        if (pre != null) prefill(pre);

        // Validare live: contur rosu + Save dezactivat cat timp campurile
        // obligatorii pentru kind-ul curent nu sunt completate. Cancel ramane
        // mereu activ - e singura iesire posibila fara sa completezi.
        FieldValidation.onChange(idField,      this::revalidateLive);
        FieldValidation.onChange(profileField, this::revalidateLive);
        FieldValidation.onChange(tagNameField,  this::revalidateLive);
        revalidateLive();

        // ── Butoane ───────────────────────────────────────────────────────────
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
            result = buildDefinition();
            dispose();
        });
    }

    @SuppressWarnings("incomplete-switch")
	private void rebuildDynamic() {
        dynamicPanel.removeAll();
        AliasKind kind = (AliasKind) kindCombo.getSelectedItem();
        if (kind == null) { dynamicPanel.revalidate(); dynamicPanel.repaint(); return; }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(4, 10, 4, 10);
        gbc.anchor  = GridBagConstraints.NORTHWEST;
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        int row = 0;
        switch (kind) {
            case TAGGED_VALUE:
                addFormRow(dynamicPanel, gbc, row++, "Profile Name *",       "alias.profileName",     profileField);
                addFormRow(dynamicPanel, gbc, row++, "Tag Name *",           "alias.tagName",         tagNameField);
                addFormRow(dynamicPanel, gbc, row++, "Stereotype owner",     "alias.stereotypeOwner", stereoOwnerField);
                addFormRow(dynamicPanel, gbc, row++, "Value type",           "alias.valueType",       valueTypeCombo);
                // Default: STRING - acelasi default folosit in ValueType.fromString().
                // La editare, este suprascris mai jos in prefill().
                valueTypeCombo.setSelectedItem(ValueType.STRING);
                addFormRow(dynamicPanel, gbc, row++, "Allowed values (CSV)", "alias.values",          valuesField);
                break;
            case STEREOTYPE:
                gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
                dynamicPanel.add(HelpIcon.labelWithHelp("Stereotype Name *:", "alias.stereotypeName"), gbc);
                stereoNameField = new CheckboxListField(detectedStereos);
                stereoNameField.addChangeListener(this::revalidateLive);
                gbc.gridy = row++; gbc.weighty = 1;
                gbc.fill  = GridBagConstraints.BOTH;
                dynamicPanel.add(stereoNameField, gbc);
                gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
                break;
            case STEREOTYPE_SET:
                addFormRow(dynamicPanel, gbc, row++, "Profile Name", "alias.profileName", setProfileField);
                gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
                dynamicPanel.add(HelpIcon.labelWithHelp("Stereotype Names *:", "alias.stereotypeNames"), gbc);
                stereoNamesField = new CheckboxListField(detectedStereos);
                stereoNamesField.addChangeListener(this::revalidateLive);
                gbc.gridy = row++; gbc.weighty = 1;
                gbc.fill  = GridBagConstraints.BOTH;
                dynamicPanel.add(stereoNamesField, gbc);
                gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
                break;
            case DESCRIPTION:
            case NAME:
                gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
                dynamicPanel.add(new JLabel("No additional parameters required."), gbc);
                break;
        }

        dynamicPanel.revalidate();
        dynamicPanel.repaint();
    }

    private void prefill(AliasDefinition a) {
        idField.setText(a.id());
        a.help().ifPresent(helpField::setText);
        kindCombo.setSelectedItem(a.kind());
        rebuildDynamic();

        switch (a.kind()) {
            case TAGGED_VALUE:
                a.profileName().ifPresent(profileField::setText);
                a.tagName().ifPresent(tagNameField::setText);
                a.stereotypeName().ifPresent(stereoOwnerField::setText);
                valueTypeCombo.setSelectedItem(a.valueType());
                if (!a.values().isEmpty())
                    valuesField.setText(String.join(", ", a.values()));
                break;
            case STEREOTYPE:
                if (stereoNameField != null && !a.stereotypeNames().isEmpty())
                    stereoNameField.setSelectedValues(a.stereotypeNames());
                else if (stereoNameField != null)
                    a.stereotypeName().ifPresent(s ->
                            stereoNameField.setSelectedValues(Collections.singletonList(s)));
                break;
            case STEREOTYPE_SET:
                a.profileName().ifPresent(setProfileField::setText);
                if (stereoNamesField != null)
                    stereoNamesField.setSelectedValues(a.stereotypeNames());
                break;
            default:
                break;
        }
    }

    /**
     * Validare LIVE (fara popup): ruleaza la fiecare schimbare relevanta,
     * marcheaza contur rosu pe campurile obligatorii goale si (de)activeaza
     * Save in consecinta. Cancel ramane mereu activ - e singura iesire
     * posibila fara sa completezi campurile obligatorii pentru kind-ul curent.
     */
    private void revalidateLive() {
        boolean valid = !idField.getText().trim().isEmpty();
        if (valid) FieldValidation.markValid(idField); else FieldValidation.markInvalid(idField);

        AliasKind kind = (AliasKind) kindCombo.getSelectedItem();
        if (kind != null) {
            switch (kind) {
                case TAGGED_VALUE:
                    boolean profileOk = !profileField.getText().trim().isEmpty();
                    boolean tagOk     = !tagNameField.getText().trim().isEmpty();
                    if (profileOk) FieldValidation.markValid(profileField); else FieldValidation.markInvalid(profileField);
                    if (tagOk)     FieldValidation.markValid(tagNameField); else FieldValidation.markInvalid(tagNameField);
                    valid = valid && profileOk && tagOk;
                    break;
                case STEREOTYPE:
                    boolean stereoOk = stereoNameField != null && !stereoNameField.getSelectedValues().isEmpty();
                    if (stereoNameField != null) stereoNameField.setValid(stereoOk);
                    valid = valid && stereoOk;
                    break;
                case STEREOTYPE_SET:
                    boolean namesOk = stereoNamesField != null && !stereoNamesField.getSelectedValues().isEmpty();
                    if (stereoNamesField != null) stereoNamesField.setValid(namesOk);
                    valid = valid && namesOk;
                    break;
                default:
                    break;
            }
        }
        okBtn.setEnabled(valid);
    }

    @SuppressWarnings("incomplete-switch")
	private boolean validateForm() {
        if (idField.getText().trim().isEmpty()) {
            warn("ID is required."); return false;
        }
        AliasKind kind = (AliasKind) kindCombo.getSelectedItem();
        if (kind == null) { warn("Kind is required."); return false; }
        switch (kind) {
            case TAGGED_VALUE:
                if (profileField.getText().trim().isEmpty()) {
                    warn("Profile Name is required for taggedValue."); return false;
                }
                if (tagNameField.getText().trim().isEmpty()) {
                    warn("Tag Name is required for taggedValue."); return false;
                }
                break;
            case STEREOTYPE:
                if (stereoNameField == null || stereoNameField.getSelectedValues().isEmpty()) {
                    warn("At least one Stereotype Name is required."); return false;
                }
                break;
            case STEREOTYPE_SET:
                if (stereoNamesField == null || stereoNamesField.getSelectedValues().isEmpty()) {
                    warn("At least one Stereotype Name is required."); return false;
                }
                break;
        }
        return true;
    }

    private AliasDefinition buildDefinition() {
        AliasKind kind = (AliasKind) kindCombo.getSelectedItem();
        AliasDefinition.Builder b = AliasDefinition.builder()
                .id(idField.getText().trim())
                .kind(kind)
                .help(nullable(helpField.getText()));

        switch (kind) {
            case TAGGED_VALUE:
                b.profileName(profileField.getText().trim())
                 .tagName(tagNameField.getText().trim())
                 .stereotypeName(nullable(stereoOwnerField.getText()))
                 .valueType((ValueType) valueTypeCombo.getSelectedItem())
                 .values(splitCsv(valuesField.getText()));
                break;
            case STEREOTYPE:
                List<String> stereoSel = stereoNameField.getSelectedValues();
                // AliasDefinition.stereotype preia primul selectat ca stereotypeName
                if (!stereoSel.isEmpty()) {
                    b.stereotypeName(stereoSel.get(0));
                }
                break;
            case STEREOTYPE_SET:
                b.profileName(nullable(setProfileField.getText()))
                 .stereotypeNames(stereoNamesField.getSelectedValues());
                break;
            default:
                break;
        }
        return b.build();
    }

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

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public Optional<AliasDefinition> getResult() {
        return Optional.ofNullable(result);
    }
}
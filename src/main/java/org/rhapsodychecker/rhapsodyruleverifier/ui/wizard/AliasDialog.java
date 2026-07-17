// ui/wizard/AliasDialog.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.AliasDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.AliasKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.ValueType;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;

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
    private final JTextField           titleField = new JTextField(30);
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

        // ── Form statica ──────────────────────────────────────────────────────
        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(15, 20, 5, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(4, 5, 4, 5);
        gbc.anchor  = GridBagConstraints.WEST;
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        int row = 0;
        addFormRow(top, gbc, row++, "ID *",   idField);
        addFormRow(top, gbc, row++, "Title",  titleField);
        addFormRow(top, gbc, row++, "Help",   helpField);
        addFormRow(top, gbc, row++, "Kind *", kindCombo);
        add(top, BorderLayout.NORTH);

        // ── Zona dinamica ─────────────────────────────────────────────────────
        dynamicPanel.setBorder(BorderFactory.createTitledBorder("Kind parameters"));
        add(dynamicPanel, BorderLayout.CENTER);

        kindCombo.addActionListener(e -> rebuildDynamic());
        rebuildDynamic();

        if (pre != null) prefill(pre);

        // ── Butoane ───────────────────────────────────────────────────────────
        JButton okBtn     = new JButton("Save");
        JButton cancelBtn = new JButton("Cancel");
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
                addFormRow(dynamicPanel, gbc, row++, "Profile Name *",       profileField);
                addFormRow(dynamicPanel, gbc, row++, "Tag Name *",           tagNameField);
                addFormRow(dynamicPanel, gbc, row++, "Stereotype owner",     stereoOwnerField);
                addFormRow(dynamicPanel, gbc, row++, "Value type",           valueTypeCombo);
                addFormRow(dynamicPanel, gbc, row++, "Allowed values (CSV)", valuesField);
                break;
            case STEREOTYPE:
                gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
                dynamicPanel.add(new JLabel("Stereotype Name *:"), gbc);
                stereoNameField = new CheckboxListField(detectedStereos);
                gbc.gridy = row++; gbc.weighty = 1;
                gbc.fill  = GridBagConstraints.BOTH;
                dynamicPanel.add(stereoNameField, gbc);
                gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
                break;
            case STEREOTYPE_SET:
                addFormRow(dynamicPanel, gbc, row++, "Profile Name", setProfileField);
                gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
                dynamicPanel.add(new JLabel("Stereotype Names *:"), gbc);
                stereoNamesField = new CheckboxListField(detectedStereos);
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
        a.title().ifPresent(titleField::setText);
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
                .title(nullable(titleField.getText()))
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

    public Optional<AliasDefinition> getResult() {
        return Optional.ofNullable(result);
    }
}

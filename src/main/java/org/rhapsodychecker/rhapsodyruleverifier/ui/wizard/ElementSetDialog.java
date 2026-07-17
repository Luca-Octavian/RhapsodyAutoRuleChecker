// ui/wizard/ElementSetDialog.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public final class ElementSetDialog extends JDialog {

    private final JTextField         idField     = new JTextField(20);
    private final JTextField         titleField  = new JTextField(30);
    private final CheckboxListField  typesField;
    private final CheckboxListField  stereoField;
    private final JTextField         inclField   = new JTextField(30);
    private final JTextField         exclField   = new JTextField(30);

    private ElementSetDefinition result = null;

    public ElementSetDialog(Window parent, FastDetectionResult fast,
                            ElementSetDefinition prefill) {
        super(parent, "Define Element Set", ModalityType.APPLICATION_MODAL);
        setSize(480, 560);
        setLocationRelativeTo(parent);

        // Sugestii din model
        List<String> detectedTypes = new ArrayList<>();
        List<String> detectedStereos = new ArrayList<>();
        if (fast != null) {
            detectedTypes.addAll(fast.countsByMetaClass().keySet());
            detectedStereos.addAll(fast.countsByStereotype().keySet());
            Collections.sort(detectedTypes);
            Collections.sort(detectedStereos);
        }

        typesField  = new CheckboxListField(detectedTypes);
        stereoField = new CheckboxListField(detectedStereos);

        build(prefill);
    }

    private void build(ElementSetDefinition pre) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(15, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(4, 5, 4, 5);
        gbc.anchor  = GridBagConstraints.NORTHWEST;
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        int row = 0;

        // ID
        addLabelRow(form, gbc, row++, "ID *", idField);

        // Title
        addLabelRow(form, gbc, row++, "Title", titleField);

        // Types
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        form.add(new JLabel("Types:"), gbc);
        gbc.gridy = row++; gbc.weighty = 1; gbc.fill = GridBagConstraints.BOTH;
        form.add(typesField, gbc);
        gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;

        // Stereotypes
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        form.add(new JLabel("Stereotypes:"), gbc);
        gbc.gridy = row++; gbc.weighty = 1; gbc.fill = GridBagConstraints.BOTH;
        form.add(stereoField, gbc);
        gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;

        // Include / Exclude packages
        addLabelRow(form, gbc, row++, "Include packages (regex CSV)", inclField);
        addLabelRow(form, gbc, row++, "Exclude packages (regex CSV)", exclField);

        // Prefill
        if (pre != null) {
            idField.setText(pre.id());
            pre.title().ifPresent(titleField::setText);
            typesField.setSelectedValues(pre.types());
            stereoField.setSelectedValues(pre.stereotypes());
            inclField.setText(String.join(", ", pre.includePackages()));
            exclField.setText(String.join(", ", pre.excludePackages()));
        }

        // Buttons
        JButton okBtn     = new JButton("Save");
        JButton cancelBtn = new JButton("Cancel");
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnRow.add(cancelBtn);
        btnRow.add(okBtn);

        setLayout(new BorderLayout());
        add(new JScrollPane(form), BorderLayout.CENTER);
        add(btnRow, BorderLayout.SOUTH);

        cancelBtn.addActionListener(e -> dispose());
        okBtn.addActionListener(e -> {
            if (!validateForm()) return;
            result = buildDefinition();
            dispose();
        });
    }

    private void addLabelRow(JPanel p, GridBagConstraints gbc,
                             int row, String label, JTextField field) {
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1; gbc.weightx = 0;
        p.add(new JLabel(label + ":"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        p.add(field, gbc);
    }

    private boolean validateForm() {
        if (idField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "ID is required.",
                    "Validation", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private ElementSetDefinition buildDefinition() {
        return ElementSetDefinition.builder()
                .id(idField.getText().trim())
                .title(nullable(titleField.getText()))
                .types(typesField.getSelectedValues())
                .stereotypes(stereoField.getSelectedValues())
                .includePackages(splitCsv(inclField.getText()))
                .excludePackages(splitCsv(exclField.getText()))
                .build();
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static String nullable(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    public Optional<ElementSetDefinition> getResult() {
        return Optional.ofNullable(result);
    }
}

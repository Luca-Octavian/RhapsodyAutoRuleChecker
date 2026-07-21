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

    private static final List<String> KNOWN_KINDS = Arrays.asList(
            "BLOCK", "INTERFACE_BLOCK", "PART",
            "PORT", "PORT_FULL", "PORT_PROXY", "PORT_FLOW",
            "INTERFACE", "PACKAGE", "REQUIREMENT", "CONNECTOR"
    );

    private final JTextField         idField     = new JTextField(20);
    private final JTextField         titleField  = new JTextField(30);
    private final CheckboxListField  kindsField;
    private final CheckboxListField  typesField;
    private final CheckboxListField  stereoField;
    private final JTextField         inclField   = new JTextField(30);
    private final JTextField         exclField   = new JTextField(30);

    private ElementSetDefinition result = null;

    public ElementSetDialog(Window parent, FastDetectionResult fast,
                            ElementSetDefinition prefill) {
        super(parent, "Define Element Set", ModalityType.APPLICATION_MODAL);
        setSize(500, 680);
        setLocationRelativeTo(parent);

        kindsField = new CheckboxListField(KNOWN_KINDS);

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
        gbc.gridwidth = 2;

        int row = 0;

        // ID
        gbc.gridy = row++; gbc.gridwidth = 1; gbc.weightx = 0;
        form.add(new JLabel("ID *:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(idField, gbc);
        gbc.gridx = 0;

        // Title
        gbc.gridy = row++; gbc.gridwidth = 1; gbc.weightx = 0;
        form.add(new JLabel("Title:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(titleField, gbc);
        gbc.gridx = 0;

        // Kinds
        gbc.gridy = row++; gbc.gridwidth = 2; gbc.weightx = 1;
        JLabel kindsLabel = new JLabel("Kinds:");
        kindsLabel.setToolTipText("ElementKind filters — use for BLOCK, PART, PORT etc. " +
                "More reliable than Types for SysML elements. ANDed with Types and Stereotypes.");
        form.add(kindsLabel, gbc);
        gbc.gridy = row++; gbc.weighty = 1; gbc.fill = GridBagConstraints.BOTH;
        form.add(kindsField, gbc);
        gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;

        // Types
        gbc.gridy = row++; gbc.gridwidth = 2; gbc.weightx = 1;
        JLabel typesLabel = new JLabel("Types:");
        typesLabel.setToolTipText("Rhapsody meta-classes (e.g. Class, Port, Requirement). " +
                "ANDed with Kinds and Stereotypes.");
        form.add(typesLabel, gbc);
        gbc.gridy = row++; gbc.weighty = 1; gbc.fill = GridBagConstraints.BOTH;
        form.add(typesField, gbc);
        gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;

        // Stereotypes
        gbc.gridy = row++; gbc.gridwidth = 2; gbc.weightx = 1;
        JLabel stereoLabel = new JLabel("Stereotypes:");
        stereoLabel.setToolTipText("Stereotypes elements must have (e.g. Block, ASIL_A). " +
                "ANDed with Kinds and Types.");
        form.add(stereoLabel, gbc);
        gbc.gridy = row++; gbc.weighty = 1; gbc.fill = GridBagConstraints.BOTH;
        form.add(stereoField, gbc);
        gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;

        // Include packages
        gbc.gridy = row++; gbc.gridwidth = 1; gbc.weightx = 0; gbc.gridx = 0;
        JLabel inclLabel = new JLabel("Include packages (regex CSV):");
        inclLabel.setToolTipText("Regex patterns — only elements in matching packages are included.");
        form.add(inclLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(inclField, gbc);
        gbc.gridx = 0;

        // Exclude packages
        gbc.gridy = row++; gbc.gridwidth = 1; gbc.weightx = 0;
        JLabel exclLabel = new JLabel("Exclude packages (regex CSV):");
        exclLabel.setToolTipText("Regex patterns — elements in matching packages are excluded.");
        form.add(exclLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(exclField, gbc);

        // Prefill
        if (pre != null) {
            idField.setText(pre.id());
            pre.title().ifPresent(titleField::setText);
            kindsField.setSelectedValues(pre.kinds());
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
                .kinds(kindsField.getSelectedValues())
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

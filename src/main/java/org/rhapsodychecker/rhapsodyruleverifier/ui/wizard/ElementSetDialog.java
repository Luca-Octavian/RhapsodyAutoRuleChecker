// ui/wizard/ElementSetDialog.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AccentColors;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help.FieldValidation;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help.HelpIcon;

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

    private final FastDetectionResult fast;

    private final JTextField         idField     = new JTextField(20);
    private final CheckboxListField  kindsField;
    private final CheckboxListField  typesField;
    private final CheckboxListField  stereoField;
    private final JTextField         inclField   = new JTextField(30);
    private final JTextField         exclField   = new JTextField(30);
    private final JButton            okBtn       = new JButton("Save");

    // Count labels per section + total
    private final JLabel kindsCountLabel  = createCountLabel();
    private final JLabel typesCountLabel  = createCountLabel();
    private final JLabel stereoCountLabel = createCountLabel();
    private final JLabel totalCountLabel  = createTotalLabel();

    private ElementSetDefinition result = null;

    public ElementSetDialog(Window parent, FastDetectionResult fast,
                            ElementSetDefinition prefill) {
        super(parent, "Define Element Set", ModalityType.APPLICATION_MODAL);
        this.fast = fast;
        setSize(680, 700);
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
        gbc.insets  = new Insets(3, 5, 3, 5);
        gbc.anchor  = GridBagConstraints.NORTHWEST;
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.gridwidth = 2;

        int row = 0;

        // ID
        gbc.gridy = row++; gbc.gridwidth = 1; gbc.weightx = 0; gbc.gridx = 0;
        form.add(HelpIcon.labelWithHelp("ID *:", "elementSet.id"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(idField, gbc);
        gbc.gridx = 0;

        // ── Kinds ─────────────────────────────────────────────────────────────
        gbc.gridy = row++; gbc.gridwidth = 2; gbc.weightx = 1;
        form.add(buildSectionHeader("Kinds:", kindsCountLabel, "elementSet.kinds"), gbc);
        gbc.gridy = row++; gbc.weighty = 1; gbc.fill = GridBagConstraints.BOTH;
        form.add(kindsField, gbc);
        gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;

        // ── Types ─────────────────────────────────────────────────────────────
        gbc.gridy = row++; gbc.gridwidth = 2;
        form.add(buildSectionHeader("Types:", typesCountLabel, "elementSet.types"), gbc);
        gbc.gridy = row++; gbc.weighty = 1; gbc.fill = GridBagConstraints.BOTH;
        form.add(typesField, gbc);
        gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;

        // ── Stereotypes ───────────────────────────────────────────────────────
        gbc.gridy = row++; gbc.gridwidth = 2;
        form.add(buildSectionHeader("Stereotypes:", stereoCountLabel, "elementSet.stereotypes"), gbc);
        gbc.gridy = row++; gbc.weighty = 1; gbc.fill = GridBagConstraints.BOTH;
        form.add(stereoField, gbc);
        gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;

        // ── Include / Exclude packages ────────────────────────────────────────
        gbc.gridy = row++; gbc.gridwidth = 1; gbc.weightx = 0; gbc.gridx = 0;
        form.add(HelpIcon.labelWithHelp("Include packages (regex CSV):", "elementSet.includePackages"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(inclField, gbc);
        gbc.gridx = 0;

        gbc.gridy = row++; gbc.gridwidth = 1; gbc.weightx = 0;
        form.add(HelpIcon.labelWithHelp("Exclude packages (regex CSV):", "elementSet.excludePackages"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(exclField, gbc);

        // ── Total count ───────────────────────────────────────────────────────
        gbc.gridy = row++; gbc.gridx = 0; gbc.gridwidth = 2; gbc.weightx = 1;
        gbc.insets = new Insets(8, 5, 3, 5);
        totalCountLabel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        form.add(totalCountLabel, gbc);

        // Prefill
        if (pre != null) {
            idField.setText(pre.id());
            kindsField.setSelectedValues(pre.kinds());
            typesField.setSelectedValues(pre.types());
            stereoField.setSelectedValues(pre.stereotypes());
            inclField.setText(String.join(", ", pre.includePackages()));
            exclField.setText(String.join(", ", pre.excludePackages()));
        }

        // Live validation
        FieldValidation.onChange(idField, this::revalidateLive);
        revalidateLive();

        // Live counts — listen to checkbox changes in all three fields
        kindsField.addChangeListener(this::updateCounts);
        typesField.addChangeListener(this::updateCounts);
        stereoField.addChangeListener(this::updateCounts);
        updateCounts();

        // Buttons
        JButton cancelBtn = new JButton("Cancel");
        okBtn.putClientProperty("FlatLaf.style", AccentColors.PURPLE_HOVER_STYLE);
        cancelBtn.putClientProperty("FlatLaf.style", AccentColors.PURPLE_HOVER_STYLE);
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnRow.add(cancelBtn);
        btnRow.add(okBtn);

        setLayout(new BorderLayout());
        JScrollPane formScroll = new JScrollPane(form);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(formScroll, BorderLayout.CENTER);
        add(btnRow, BorderLayout.SOUTH);

        cancelBtn.addActionListener(e -> dispose());
        okBtn.addActionListener(e -> {
            if (!validateForm()) return;
            result = buildDefinition();
            dispose();
        });
    }

    // ── Section header: label + count on right ────────────────────────────────

    private JPanel buildSectionHeader(String text, JLabel countLabel, String helpKey) {
        JPanel header = new JPanel(new BorderLayout());
        header.add(HelpIcon.labelWithHelp(text, helpKey), BorderLayout.WEST);
        header.add(countLabel, BorderLayout.EAST);
        return header;
    }

    // ── Count update logic ────────────────────────────────────────────────────

    private void updateCounts() {
        List<String> kinds    = kindsField.getSelectedValues();
        List<String> types    = typesField.getSelectedValues();
        List<String> stereos  = stereoField.getSelectedValues();

        int kc = ElementSetCountEstimator.estimateKinds(fast, kinds);
        int tc = ElementSetCountEstimator.estimateTypes(fast, types);
        int sc = ElementSetCountEstimator.estimateStereotypes(fast, stereos);
        int total = ElementSetCountEstimator.estimateTotal(fast, kinds, types, stereos);

        kindsCountLabel.setText(kc >= 0 ? kc + " elements" : "");
        typesCountLabel.setText(tc >= 0 ? tc + " elements" : "");
        stereoCountLabel.setText(sc >= 0 ? sc + " elements" : "");

        if (total >= 0) {
            totalCountLabel.setText("  Matching elements: " + total);
            totalCountLabel.setVisible(true);
        } else {
            totalCountLabel.setText("");
            totalCountLabel.setVisible(false);
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void revalidateLive() {
        boolean valid = !idField.getText().trim().isEmpty();
        if (valid) FieldValidation.markValid(idField);
        else FieldValidation.markInvalid(idField);
        okBtn.setEnabled(valid);
    }

    private boolean validateForm() {
        if (idField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "ID is required.",
                    "Validation", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    // ── Build result ──────────────────────────────────────────────────────────

    private ElementSetDefinition buildDefinition() {
        return ElementSetDefinition.builder()
                .id(idField.getText().trim())
                .kinds(kindsField.getSelectedValues())
                .types(typesField.getSelectedValues())
                .stereotypes(stereoField.getSelectedValues())
                .includePackages(splitCsv(inclField.getText()))
                .excludePackages(splitCsv(exclField.getText()))
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JLabel createCountLabel() {
        JLabel label = new JLabel("");
        label.setFont(label.getFont().deriveFont(Font.ITALIC, 11f));
        label.setForeground(new Color(100, 140, 180));
        return label;
    }

    private static JLabel createTotalLabel() {
        JLabel label = new JLabel("");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        label.setForeground(new Color(60, 120, 60));
        return label;
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public Optional<ElementSetDefinition> getResult() {
        return Optional.ofNullable(result);
    }
}
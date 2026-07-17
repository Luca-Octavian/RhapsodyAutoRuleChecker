// ui/wizard/ScopeStepPanel.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardState;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;

import javax.swing.*;
import java.awt.*;

/**
 * Pasul 1: selectează modul global (lenient / strict).
 */
public final class ScopeStepPanel extends JPanel {

    private final WizardState state;

    private final JRadioButton lenientBtn = new JRadioButton("Lenient — report only, never block");
    private final JRadioButton strictBtn  = new JRadioButton("Strict — treat failures as errors");
    private final JTextArea    infoArea   = new JTextArea();

    public ScopeStepPanel(WizardState state, FastDetectionResult fast) {
        super(new BorderLayout(10, 10));
        this.state = state;
        setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        build();
        loadFromState();
    }

    private void build() {
        JLabel title = new JLabel("Select evaluation mode");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(6, 0, 6, 0);
        gbc.anchor  = GridBagConstraints.WEST;
        gbc.weightx = 1;
        gbc.fill    = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        center.add(lenientBtn, gbc);
        gbc.gridy = 1;
        center.add(strictBtn, gbc);

        ButtonGroup group = new ButtonGroup();
        group.add(lenientBtn);
        group.add(strictBtn);

        infoArea.setEditable(false);
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setBackground(UIManager.getColor("Panel.background"));
        infoArea.setFont(infoArea.getFont().deriveFont(12f));
        infoArea.setRows(4);

        gbc.gridy = 2;
        center.add(new JScrollPane(infoArea), gbc);

        add(center, BorderLayout.CENTER);

        // Persist la state + update info
        lenientBtn.addActionListener(e -> { state.mode("lenient"); updateInfo(); });
        strictBtn.addActionListener(e  -> { state.mode("strict");  updateInfo(); });
    }

    private void loadFromState() {
        if ("strict".equalsIgnoreCase(state.mode())) {
            strictBtn.setSelected(true);
        } else {
            lenientBtn.setSelected(true);
        }
        updateInfo();
    }

    private void updateInfo() {
        if (lenientBtn.isSelected()) {
            infoArea.setText("Lenient mode: evaluation continues even if rules fail. "
                    + "All failures are collected and reported. "
                    + "Suitable for development and review phases.");
        } else {
            infoArea.setText("Strict mode: first rule failure stops evaluation. "
                    + "Use this to enforce mandatory quality gates.");
        }
    }
}

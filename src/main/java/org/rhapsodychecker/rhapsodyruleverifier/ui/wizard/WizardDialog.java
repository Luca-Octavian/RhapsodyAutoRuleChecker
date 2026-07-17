// ui/wizard/WizardDialog.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardState;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.YamlPresetWriter;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Optional;

/**
 * Dialog modal cu 5 pași (CardLayout) pentru configurarea regulilor.
 * Acceptă un WizardState pre-populat (edit) sau null (new config).
 *
 * După închidere, getSavedConfigPath() returnează calea YAML salvată
 * dacă user-ul a finalizat, sau Optional.empty() dacă a anulat.
 */
public final class WizardDialog extends JDialog {

    private static final String STEP_SCOPE   = "SCOPE";
    private static final String STEP_SETS    = "SETS";
    private static final String STEP_ALIASES = "ALIASES";
    private static final String STEP_RULES   = "RULES";
    private static final String STEP_REVIEW  = "REVIEW";

    private static final String[] STEP_ORDER = {
            STEP_SCOPE, STEP_SETS, STEP_ALIASES, STEP_RULES, STEP_REVIEW
    };

    private final CardLayout cardLayout  = new CardLayout();
    private final JPanel     cardPanel   = new JPanel(cardLayout);
    private final JButton    backBtn     = new JButton("← Back");
    private final JButton    nextBtn     = new JButton("Next →");
    private final JButton    cancelBtn   = new JButton("Cancel");
    private final JLabel     stepLabel   = new JLabel();

    private final WizardState       wizardState;
    private final ReviewStepPanel   reviewPanel;

    private int    currentStep     = 0;
    private String savedConfigPath = null;

    public WizardDialog(Frame parent, FastDetectionResult fast, WizardState initialState) {
        super(parent, "Config Wizard", true);
        this.wizardState = initialState != null ? initialState : new WizardState();

        setSize(800, 620);
        setLocationRelativeTo(parent);
        setResizable(true);

        // Construieste pașii
        reviewPanel = new ReviewStepPanel(wizardState);

        cardPanel.add(new ScopeStepPanel(wizardState, fast),        STEP_SCOPE);
        cardPanel.add(new ElementSetStepPanel(wizardState, fast),    STEP_SETS);
        cardPanel.add(new AliasStepPanel(wizardState, fast),         STEP_ALIASES);
        cardPanel.add(new RuleStepPanel(wizardState, fast),          STEP_RULES);
        cardPanel.add(reviewPanel,                                    STEP_REVIEW);

        initLayout();
        initListeners();
        updateStepUI();
    }

    private void initLayout() {
        setLayout(new BorderLayout(5, 5));

        // ── Header ────────────────────────────────────────────────────────────
        stepLabel.setFont(stepLabel.getFont().deriveFont(Font.BOLD, 13f));
        stepLabel.setBorder(BorderFactory.createEmptyBorder(10, 15, 5, 15));
        add(stepLabel, BorderLayout.NORTH);

        // ── Conținut pași ─────────────────────────────────────────────────────
        add(cardPanel, BorderLayout.CENTER);

        // ── Navigare ──────────────────────────────────────────────────────────
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        navPanel.add(cancelBtn);
        navPanel.add(backBtn);
        navPanel.add(nextBtn);
        add(navPanel, BorderLayout.SOUTH);
    }

    private void initListeners() {
        cancelBtn.addActionListener(e -> dispose());

        backBtn.addActionListener(e -> {
            if (currentStep > 0) {
                currentStep--;
                updateStepUI();
            }
        });

        nextBtn.addActionListener(e -> {
            if (currentStep < STEP_ORDER.length - 1) {
                currentStep++;
                updateStepUI();
            } else {
                saveConfig();
            }
        });
    }

    private void updateStepUI() {
        // Daca intram pe Review, actualizam sumarul
        if (STEP_ORDER[currentStep].equals(STEP_REVIEW)) {
            reviewPanel.refresh();
        }

        cardLayout.show(cardPanel, STEP_ORDER[currentStep]);

        int total = STEP_ORDER.length;
        stepLabel.setText("Step " + (currentStep + 1) + " of " + total
                + "  —  " + stepTitle(STEP_ORDER[currentStep]));

        backBtn.setEnabled(currentStep > 0);
        nextBtn.setText(currentStep == STEP_ORDER.length - 1 ? "Save & Close" : "Next →");
    }

    private void saveConfig() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Config As");
        chooser.setSelectedFile(new File("rule-config.yaml"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "YAML Config", "yaml", "yml"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        String path = chooser.getSelectedFile().getAbsolutePath();
        if (!path.endsWith(".yaml") && !path.endsWith(".yml")) path += ".yaml";

        try {
            YamlPresetWriter.write(wizardState, path);
            savedConfigPath = path;
            JOptionPane.showMessageDialog(this,
                    "Config saved to:\n" + path,
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error saving config:\n" + ex.getMessage(),
                    "Save Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    public Optional<String> getSavedConfigPath() {
        return Optional.ofNullable(savedConfigPath);
    }

    private static String stepTitle(String step) {
        switch (step) {
            case STEP_SCOPE:   return "Select Mode";
            case STEP_SETS:    return "Define Element Sets";
            case STEP_ALIASES: return "Configure Aliases";
            case STEP_RULES:   return "Add Rules";
            case STEP_REVIEW:  return "Review & Save";
            default:           return step;
        }
    }
}

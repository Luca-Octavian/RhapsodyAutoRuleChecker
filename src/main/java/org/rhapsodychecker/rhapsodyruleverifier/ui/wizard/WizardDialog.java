// ui/wizard/WizardDialog.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardState;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.YamlPresetWriter;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AccentColors;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AppTheme;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.GradientAccentButton;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help.StepIndicator;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Optional;

/**
 * Dialog modal cu 3 pași (CardLayout) pentru configurarea regulilor.
 * Acceptă un WizardState pre-populat (edit) sau null (new config).
 *
 * După închidere, getSavedConfigPath() returnează calea YAML salvată
 * dacă user-ul a finalizat, sau Optional.empty() dacă a anulat.
 */
public final class WizardDialog extends JDialog {

    private static final String STEP_SETS    = "SETS";
    private static final String STEP_RULES   = "RULES";
    private static final String STEP_REVIEW  = "REVIEW";

    private static final String[] STEP_ORDER = {
            STEP_SETS, STEP_RULES, STEP_REVIEW
    };

    private final CardLayout cardLayout  = new CardLayout();
    private final JPanel     cardPanel   = new JPanel(cardLayout);
    // Purple is the wizard/config accent. Next is the one action that advances
    // the flow, so it is PRIMARY; Cancel/Back/Save As support it and stay
    // SECONDARY — same silhouette and height, quieter at rest.
    private final GradientAccentButton backBtn   = GradientAccentButton.secondary("← Back", AccentColors.PURPLE_HEX);
    private final GradientAccentButton nextBtn   = GradientAccentButton.primary("Next →", AccentColors.PURPLE_HEX);
    private final GradientAccentButton cancelBtn = GradientAccentButton.secondary("Cancel", AccentColors.PURPLE_HEX);
    private final GradientAccentButton saveAsBtn = GradientAccentButton.secondary("Save As...", AccentColors.PURPLE_HEX);
    private final StepIndicator stepIndicator =
            new StepIndicator(new String[]{"Element sets", "Rules", "Review"});
    private final JLabel     stepLabel   = new JLabel();

    private final WizardState       wizardState;
    private final ReviewStepPanel   reviewPanel;

    private int    currentStep     = 0;
    private String savedConfigPath = null;

    public WizardDialog(Frame parent, FastDetectionResult fast, WizardState initialState) {
        super(parent, "Config Wizard", true);
        this.wizardState = initialState != null ? initialState : new WizardState();

        setSize(1100, 820);
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(parent);
        setResizable(true);
        AppTheme.guardMinimumSize(this, new Dimension(800, 600));

        // Construieste pașii
        reviewPanel = new ReviewStepPanel(wizardState);

        cardPanel.add(new ElementSetStepPanel(wizardState, fast),    STEP_SETS);
        cardPanel.add(new RuleStepPanel(wizardState, fast),          STEP_RULES);
        cardPanel.add(reviewPanel,                                    STEP_REVIEW);

        initLayout();
        initListeners();
        updateStepUI();
    }

    private void initLayout() {
        setLayout(new BorderLayout(5, 5));

        // ── Header ────────────────────────────────────────────────────────────
        stepIndicator.setBorder(BorderFactory.createEmptyBorder(10, 15, 0, 15));
        stepIndicator.setAlignmentX(Component.CENTER_ALIGNMENT);

        stepLabel.setFont(stepLabel.getFont().deriveFont(Font.BOLD, 13f));
        stepLabel.setHorizontalAlignment(SwingConstants.CENTER);
        stepLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        stepLabel.setBorder(BorderFactory.createEmptyBorder(2, 15, 5, 15));

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                AppTheme.edgeBorder(0, 0, 2, 0),
                BorderFactory.createEmptyBorder(4, 0, 2, 0)));
        headerPanel.add(stepIndicator);
        headerPanel.add(stepLabel);
        add(headerPanel, BorderLayout.NORTH);

        // ── Conținut pași ─────────────────────────────────────────────────────
        add(cardPanel, BorderLayout.CENTER);

        // ── Navigare ──────────────────────────────────────────────────────────
        // Variants are set on the field declarations above.
        saveAsBtn.setVisible(false);

        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        navPanel.setBorder(BorderFactory.createCompoundBorder(
                AppTheme.edgeBorder(1, 0, 0, 0),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        navPanel.add(cancelBtn);
        navPanel.add(backBtn);
        navPanel.add(saveAsBtn);
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

        saveAsBtn.addActionListener(e -> {
            reviewPanel.applyToState();
            promptAndSave();
        });
    }

    private void updateStepUI() {
        if (STEP_ORDER[currentStep].equals(STEP_REVIEW)) {
            reviewPanel.refresh();
        }

        cardLayout.show(cardPanel, STEP_ORDER[currentStep]);

        stepLabel.setText(stepTitle(STEP_ORDER[currentStep]));

        stepIndicator.setCurrentStep(currentStep);
        backBtn.setEnabled(currentStep > 0);
        nextBtn.setText(currentStep == STEP_ORDER.length - 1 ? "Save & Close" : "Next →");

        boolean onReview = STEP_ORDER[currentStep].equals(STEP_REVIEW);
        saveAsBtn.setVisible(onReview && wizardState.sourcePath() != null);
    }

    private void saveConfig() {
        reviewPanel.applyToState();

        String existingPath = wizardState.sourcePath();
        if (existingPath != null && !existingPath.trim().isEmpty()) {
            writeConfig(existingPath);
        } else {
            promptAndSave();
        }
    }

    private void promptAndSave() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Config As");
        chooser.setSelectedFile(new File("rule-config.yaml"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "YAML Config", "yaml", "yml"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        String path = chooser.getSelectedFile().getAbsolutePath();
        if (!path.endsWith(".yaml") && !path.endsWith(".yml")) path += ".yaml";

        writeConfig(path);
    }

    private void writeConfig(String path) {
        try {
            YamlPresetWriter.write(wizardState, path);
            savedConfigPath = path;
            wizardState.sourcePath(path);
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
            case STEP_SETS:    return "Define Element Sets";
            case STEP_RULES:   return "Add Rules";
            case STEP_REVIEW:  return "Review & Save";
            default:           return step;
        }
    }
}
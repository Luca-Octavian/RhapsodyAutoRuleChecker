// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/ui/MainFrame.java
package org.rhapsodychecker.rhapsodyruleverifier.ui;

import org.rhapsodychecker.rhapsodyruleverifier.prefs.RhapsodyPathSetup;
import org.rhapsodychecker.rhapsodyruleverifier.core.AppLogger;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.ui.controller.MainFrameController;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AccentColors;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AppTheme;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.GradientAccentButton;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.GradientProgressBar;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.ThemeAware;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.List;
import java.util.Vector;

/**
 * Pure UI skeleton — component creation and layout only.
 * All logic delegated to {@link MainFrameController}.
 */
@SuppressWarnings("serial")
public class MainFrame extends JFrame implements MainFrameController.View, ThemeAware {

    // ── Components ──────────────────────────────────────────────────────────
    private final JComboBox<String> modelPathField  = new JComboBox<>();
    private final JComboBox<String> configPathField = new JComboBox<>();
    // Compact neutral: these sit inline beside the path combo boxes, so they use
    // the reduced padding that matches a combo's height instead of the taller
    // standard button height used in the action bar below.
    private final GradientAccentButton modelBrowseBtn  = GradientAccentButton.neutralCompact("Browse...");
    private final GradientAccentButton configBrowseBtn = GradientAccentButton.neutralCompact("Browse...");

    // Action bar. Orange marks model actions, green the Excel export, purple the
    // wizard/config actions. All are PRIMARY here because each is the headline
    // action for its own concern rather than a supporting one.
    private final GradientAccentButton loadModelBtn        = GradientAccentButton.primary("Load Model", AccentColors.ORANGE_HEX);
    private final GradientAccentButton runBtn              = GradientAccentButton.primary("Run", AccentColors.ORANGE_HEX);
    private final GradientAccentButton exportBtn           = GradientAccentButton.primary("Export Excel", AccentColors.GREEN_HEX);
    private final GradientAccentButton newConfigWizardBtn  = GradientAccentButton.primary("New Config (Wizard)", AccentColors.PURPLE_HEX);
    private final GradientAccentButton editConfigWizardBtn = GradientAccentButton.primary("Edit Config (Wizard)", AccentColors.PURPLE_HEX);
    private final GradientAccentButton updateModelBtn      = GradientAccentButton.primary("Update Model", AccentColors.ORANGE_HEX);
    private final GradientAccentButton fixBtn              = GradientAccentButton.primary("Review & Fix\u2026", AccentColors.RED_HEX);
    // Neutral: opening a folder in Explorer carries no semantic colour.
    private final GradientAccentButton openCacheBtn        = GradientAccentButton.neutral("\uD83D\uDCC1 Cache");
    private final GradientAccentButton fixHistoryBtn       = GradientAccentButton.neutral("\uD83D\uDCCB Fix Log");
    // Neutral: help opens local HTML docs in the default browser.
    private final GradientAccentButton helpBtn             = GradientAccentButton.neutral("Help");
    private final JCheckBox darkModeToggle = new JCheckBox("Dark mode");

    private final PackageTreePanel  treePanel    = new PackageTreePanel();
    private final ResultsTablePanel resultsPanel = new ResultsTablePanel();

    private final JLabel       statusBar   = new JLabel("  Ready");
    private final JProgressBar progressBar = new GradientProgressBar();
    private JPanel topPanel;
    private JPanel pathGroup;

    // ── Controller ──────────────────────────────────────────────────────────
    private final MainFrameController controller;

    // ── Constructor ─────────────────────────────────────────────────────────

    private static final Dimension MIN_SIZE = new Dimension(1000, 600);

    public MainFrame() {
        super("Rhapsody Model Checker");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1600, 900);
        setMinimumSize(MIN_SIZE);
        setLocationRelativeTo(null);

        // Guard against external resize interference (e.g. Rhapsody COM automation)
        AppTheme.guardMinimumSize(this, MIN_SIZE);

        controller = new MainFrameController(this,
                new SwingProgressReporter(progressBar, statusBar));

        buildLayout();
        wireListeners();
        controller.init();
        updateButtonStates();
    }

    // ── Layout ──────────────────────────────────────────────────────────────

    private void buildLayout() {
        setLayout(new BorderLayout(5, 5));

        // Top panel
        topPanel = new JPanel(new GridBagLayout());
        // A rule along the bottom, separating the control strip from the
        // content below it. Without it the buttons appeared to float on the
        // same surface as the trees, since everything shares one background.
        topPanel.setBorder(topPanelBorder());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        modelPathField.setEditable(true);
        configPathField.setEditable(true);

        // Wrap Model + Config rows in a visually grouped sub-panel.
        // The stock Swing border is intentionally square; it cannot honor FlatLaf's arc property.
        pathGroup = new JPanel(new GridBagLayout());
        pathGroup.setBorder(pathGroupBorder());

        GridBagConstraints pgbc = new GridBagConstraints();
        pgbc.insets = new Insets(2, 5, 2, 5);
        pgbc.fill = GridBagConstraints.HORIZONTAL;

        pgbc.gridx = 0; pgbc.gridy = 0; pgbc.weightx = 0;
        pathGroup.add(new JLabel("Model (.rpyx):"), pgbc);
        pgbc.gridx = 1; pgbc.weightx = 1;
        pathGroup.add(modelPathField, pgbc);
        pgbc.gridx = 2; pgbc.weightx = 0;
        pathGroup.add(modelBrowseBtn, pgbc);

        pgbc.gridx = 0; pgbc.gridy = 1;
        pathGroup.add(new JLabel("Config (.yaml):"), pgbc);
        pgbc.gridx = 1; pgbc.weightx = 1;
        pathGroup.add(configPathField, pgbc);
        pgbc.gridx = 2; pgbc.weightx = 0;
        pathGroup.add(configBrowseBtn, pgbc);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3; gbc.weightx = 1;
        topPanel.add(pathGroup, gbc);

        // Colour/variant assignment lives on the field declarations above; see
        // GradientAccentButton for the actual gradient-ring / hover-fill painting.
        openCacheBtn.setToolTipText("Open cache folder in Explorer");

        // Button bar
        JPanel buttonPanel = new JPanel(new BorderLayout());
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftButtons.add(loadModelBtn);
        leftButtons.add(runBtn);
        leftButtons.add(exportBtn);
        leftButtons.add(newConfigWizardBtn);
        leftButtons.add(editConfigWizardBtn);
        leftButtons.add(updateModelBtn);
        leftButtons.add(fixBtn);
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        darkModeToggle.setSelected(AppTheme.isDarkMode());
        darkModeToggle.setToolTipText("Switch between the light and dark application appearances");
        rightButtons.add(darkModeToggle);
        rightButtons.add(helpBtn);
        rightButtons.add(fixHistoryBtn);
        rightButtons.add(openCacheBtn);
        buttonPanel.add(leftButtons, BorderLayout.WEST);
        buttonPanel.add(rightButtons, BorderLayout.EAST);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 3; gbc.weightx = 1;
        topPanel.add(buttonPanel, gbc);
        add(topPanel, BorderLayout.NORTH);

        // Center
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, treePanel, resultsPanel);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.3);
        splitPane.setDividerSize(6);
        add(splitPane, BorderLayout.CENTER);

        // Bottom
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                AppTheme.edgeBorder(1, 0, 0, 0),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));
        progressBar.setStringPainted(true);
        progressBar.setString("");
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(statusBar, BorderLayout.CENTER);
        bottomPanel.add(progressBar, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    // ── Listener wiring ─────────────────────────────────────────────────────

    private void wireListeners() {
        modelBrowseBtn.addActionListener(e ->
                browseFile(modelPathField, "Rhapsody Model", "rpyx", "rpy"));
        configBrowseBtn.addActionListener(e ->
                browseFile(configPathField, "YAML Config", "yaml", "yml"));

        loadModelBtn.addActionListener(e -> controller.onLoadModel());
        updateModelBtn.addActionListener(e -> controller.onUpdateModel());
        runBtn.addActionListener(e -> controller.onRunEvaluation());
        exportBtn.addActionListener(e -> controller.onExportExcel());
        newConfigWizardBtn.addActionListener(e -> controller.onWizardNew());
        editConfigWizardBtn.addActionListener(e -> controller.onWizardEdit());
        fixBtn.addActionListener(e -> controller.onApplySuggestedFixes());
        openCacheBtn.addActionListener(e -> controller.onOpenCacheFolder());
        fixHistoryBtn.addActionListener(e -> controller.onOpenFixHistory());
        helpBtn.addActionListener(e -> controller.onHelp());
        darkModeToggle.addActionListener(e -> {
            boolean enabled = darkModeToggle.isSelected();
            // Let the checkbox commit its state before the Look-and-Feel swap
            // performs its unavoidable EDT work.
            SwingUtilities.invokeLater(() -> AppTheme.setDarkMode(enabled));
        });

        resultsPanel.setOnElementDoubleClick(controller::onNavigateToElement);
    }

    @Override
    public void refreshTheme() {
        if (topPanel != null) topPanel.setBorder(topPanelBorder());
        if (pathGroup != null) pathGroup.setBorder(pathGroupBorder());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                AppTheme.edgeBorder(1, 0, 0, 0),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));
        repaint();
    }

    private static Border topPanelBorder() {
        return BorderFactory.createCompoundBorder(
                AppTheme.edgeBorder(0, 0, 1, 0),
                BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private static Border pathGroupBorder() {
        return BorderFactory.createCompoundBorder(
                AppTheme.panelBorder(),
                BorderFactory.createEmptyBorder(12, 16, 12, 16));
    }

    private void browseFile(JComboBox<String> target, String description,
                            String... extensions) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select " + description);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                description, extensions));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setComboText(target, chooser.getSelectedFile().getAbsolutePath());
        }
    }

    // ── View interface implementation ───────────────────────────────────────

    @Override public String modelPath()  { return comboText(modelPathField); }
    @Override public String configPath() { return comboText(configPathField); }

    @Override public void setModelPath(String path)  { setComboText(modelPathField, path); }
    @Override public void setConfigPath(String path) { setComboText(configPathField, path); }

    @Override public String selectedScope() { return treePanel.getSelectedPath(); }

    @Override public void setStatus(String text) { statusBar.setText(text); }

    @Override public void loadTree(PackageNode tree) { treePanel.loadTree(tree); }
    @Override public void loadResults(List<RuleResult> results, ElementIndex index, List<RuleSpec> specs) {
        resultsPanel.loadResults(results, index, specs);
    }

    @Override public void clearTree()    { treePanel.clear(); }
    @Override public void clearResults() { resultsPanel.clear(); }

    @Override
    public void setBusy(boolean busy) {
        boolean enabled = !busy;
        modelBrowseBtn.setEnabled(enabled);
        configBrowseBtn.setEnabled(enabled);
        loadModelBtn.setEnabled(enabled);
        runBtn.setEnabled(enabled);
        exportBtn.setEnabled(enabled);
        newConfigWizardBtn.setEnabled(enabled);
        editConfigWizardBtn.setEnabled(enabled);
        updateModelBtn.setEnabled(enabled);
        fixBtn.setEnabled(enabled);
        fixHistoryBtn.setEnabled(enabled);
        openCacheBtn.setEnabled(enabled);
        helpBtn.setEnabled(enabled);
    }

    @Override
    public void resetProgress() {
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        progressBar.setString("");
    }

    @Override
    public void refreshRecentModels(List<String> paths) {
        refreshCombo(modelPathField, paths);
    }

    @Override
    public void refreshRecentConfigs(List<String> paths) {
        refreshCombo(configPathField, paths);
    }

    @Override public void showWarning(String msg, String title) {
        JOptionPane.showMessageDialog(this, msg, title, JOptionPane.WARNING_MESSAGE);
    }

    @Override public void showError(String msg, String title) {
        JOptionPane.showMessageDialog(this, msg, title, JOptionPane.ERROR_MESSAGE);
    }

    @Override public void showInfo(String msg, String title) {
        JOptionPane.showMessageDialog(this, msg, title, JOptionPane.INFORMATION_MESSAGE);
    }

    @Override public JFrame frame() { return this; }

    @Override
    public void updateButtonStates() {
        String fieldPath = comboText(modelPathField);
        runBtn.setEnabled(controller.isModelLoaded());
        exportBtn.setEnabled(controller.hasResults());
        // "Update Model" is only meaningful for the model that is currently in
        // memory. If the user browsed to a different file the button is greyed
        // out so they get a visual hint rather than a surprising runtime error.
        updateModelBtn.setEnabled(controller.isUpdateModelEnabled(fieldPath));
        newConfigWizardBtn.setEnabled(controller.isModelLoaded());
        editConfigWizardBtn.setEnabled(controller.isModelLoaded() && controller.hasConfig());
        fixBtn.setEnabled(controller.hasFixPlan() && !controller.isLoadedFromCache());
        fixHistoryBtn.setEnabled(controller.hasFixHistory());
        openCacheBtn.setEnabled(controller.isCacheAvailable(fieldPath));
    }

    // ── Combo helpers ───────────────────────────────────────────────────────

    private static String comboText(JComboBox<String> combo) {
        Object item = combo.getEditor().getItem();
        return item == null ? "" : item.toString().trim();
    }

    private static void setComboText(JComboBox<String> combo, String text) {
        combo.getEditor().setItem(text);
        combo.setSelectedItem(text);
    }

    private static void refreshCombo(JComboBox<String> combo, List<String> items) {
        String current = comboText(combo);
        combo.setModel(new DefaultComboBoxModel<>(new Vector<>(items)));
        if (!current.isEmpty()) {
            setComboText(combo, current);
        } else if (!items.isEmpty()) {
            combo.setSelectedIndex(0);
        }
    }

    // ── Entry point ─────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.setProperty("sun.java2d.dpiaware", "true");
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        System.setProperty("sun.java2d.d3d", "false");
        RhapsodyPathSetup.ensureConfigured();
        AppLogger.init();
        Runtime.getRuntime().addShutdownHook(new Thread(AppLogger::close));
        AppTheme.apply();
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/ui/MainFrame.java
package org.rhapsodychecker.rhapsodyruleverifier.ui;

import com.formdev.flatlaf.intellijthemes.FlatNordIJTheme;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.*;
import org.rhapsodychecker.rhapsodyruleverifier.config.ConfigLoader;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.ConfigToWizardStateMapper;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleEngine;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleStatus;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;
import org.rhapsodychecker.rhapsodyruleverifier.detection.DetectionFacade;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;
import org.rhapsodychecker.rhapsodyruleverifier.detection.rhapsody.PortProbeService;
import org.rhapsodychecker.rhapsodyruleverifier.export.ExcelReportExporter;
import org.rhapsodychecker.rhapsodyruleverifier.prefs.ModelFreshnessChecker;
import org.rhapsodychecker.rhapsodyruleverifier.prefs.RecentFilesStore;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.LoadingStep;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.WizardDialog;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Vector;

@SuppressWarnings("serial")
public class MainFrame extends JFrame {

    // Top panel: file paths (JComboBox editabil = camp text + dropdown cu ultimele 5 folosite)
    private final JComboBox<String> modelPathField  = new JComboBox<>();
    private final JComboBox<String> configPathField = new JComboBox<>();
    private final JButton    modelBrowseBtn  = new JButton("Browse...");
    private final JButton    configBrowseBtn = new JButton("Browse...");

    // Action buttons
    private final JButton loadModelBtn       = new JButton("Load Model");
    private final JButton runBtn             = new JButton("Run");
    private final JButton exportBtn          = new JButton("Export Excel");
    private final JButton newConfigWizardBtn  = new JButton("New Config (Wizard)");
    private final JButton editConfigWizardBtn = new JButton("Edit Config (Wizard)");

    // Main panels
    private final PackageTreePanel  treePanel    = new PackageTreePanel();
    private final ResultsTablePanel resultsPanel = new ResultsTablePanel();

    // Status
    private final JLabel       statusBar   = new JLabel("  Ready");
    private final JProgressBar progressBar = new JProgressBar();

    // Drives progressBar/statusBar from background work (loadModel / runEvaluation).
    // Not passed into the backend classes yet (they don't accept one) — steps are
    // reported manually around each backend call below, which is enough for a
    // simple per-phase indicator without changing scanner/loader/engine signatures.
    private final ProgressReporter progressReporter = new SwingProgressReporter(progressBar, statusBar);

    // Recent files / model freshness (persisted per-user via java.util.prefs)
    private final RecentFilesStore     recentFiles      = new RecentFilesStore();
    private final ModelFreshnessChecker freshnessChecker = new ModelFreshnessChecker(recentFiles);

    // State
    private RhapsodyModelSnapshot snapshot;
    private ElementIndex           index;
    private RuleCheckerConfig      config;
    private List<RuleResult>       lastResults;
    private FastDetectionResult    fastDetectionResult;

    public MainFrame() {
        super("Rhapsody Model Checker");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 700);
        setLocationRelativeTo(null);
        initLayout();
        initListeners();
        prefillRecentPaths();
        updateButtonStates();
    }

    /**
     * Populeaza dropdown-urile cu ultimele cai folosite (cel mult 5, sau
     * mai putine daca nu exista atatea in istoric) si preselecteaza cea
     * mai recenta. Utilizatorul tot trebuie sa apese "Load Model" explicit --
     * nu se conecteaza automat la deschiderea aplicatiei.
     */
    private void prefillRecentPaths() {
        refreshRecentItems(modelPathField, recentFiles.recentModels());
        refreshRecentItems(configPathField, recentFiles.recentConfigs());
    }

    /**
     * Reincarca lista de itemi a unui combo din istoricul curent, pastrand
     * textul curent selectat (daca exista) ca item selectat dupa refresh.
     */
    private void refreshRecentItems(JComboBox<String> combo, List<String> recentPaths) {
        String current = textOf(combo);
        combo.setModel(new DefaultComboBoxModel<>(new Vector<>(recentPaths)));
        if (!current.isEmpty()) {
            setTextOf(combo, current);
        } else if (!recentPaths.isEmpty()) {
            combo.setSelectedIndex(0);
        }
    }

    /** Citeste textul curent dintr-un JComboBox editabil (camp + dropdown). */
    private static String textOf(JComboBox<String> combo) {
        Object item = combo.getEditor().getItem();
        return item == null ? "" : item.toString().trim();
    }

    /** Seteaza textul curent intr-un JComboBox editabil, fara sa fie nevoie ca valoarea sa existe deja in lista. */
    private static void setTextOf(JComboBox<String> combo, String text) {
        combo.getEditor().setItem(text);
        combo.setSelectedItem(text);
    }

    private void initLayout() {
        setLayout(new BorderLayout(5, 5));

        // ── Top panel: file paths + buttons ──────────────────────────────────
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        modelPathField.setEditable(true);
        configPathField.setEditable(true);

        // Row 0: Model path
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        topPanel.add(new JLabel("Model (.rpyx):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        topPanel.add(modelPathField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        topPanel.add(modelBrowseBtn, gbc);

        // Row 1: Config path
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        topPanel.add(new JLabel("Config (.yaml):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        topPanel.add(configPathField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        topPanel.add(configBrowseBtn, gbc);

        // Row 2: Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttonPanel.add(loadModelBtn);
        buttonPanel.add(runBtn);
        buttonPanel.add(exportBtn);
        buttonPanel.add(newConfigWizardBtn);
        buttonPanel.add(editConfigWizardBtn);
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3; gbc.weightx = 1;
        topPanel.add(buttonPanel, gbc);

        add(topPanel, BorderLayout.NORTH);

        // ── Center: tree + results ────────────────────────────────────────────
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, treePanel, resultsPanel);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.3);
        add(splitPane, BorderLayout.CENTER);

        // ── Bottom: status bar + loading progress ─────────────────────────────
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));

        progressBar.setStringPainted(true);
        progressBar.setString("");

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(statusBar, BorderLayout.CENTER);
        bottomPanel.add(progressBar, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void initListeners() {
        modelBrowseBtn.addActionListener(e ->
                browseFile(modelPathField, "Rhapsody Model", "rpyx", "rpy"));
        configBrowseBtn.addActionListener(e ->
                browseFile(configPathField, "YAML Config", "yaml", "yml"));
        loadModelBtn.addActionListener(e       -> loadModel());
        runBtn.addActionListener(e             -> runEvaluation());
        exportBtn.addActionListener(e          -> exportExcel());
        newConfigWizardBtn.addActionListener(e  -> openWizardNew());
        editConfigWizardBtn.addActionListener(e -> openWizardEdit());

        // Double-click on result row -> navigate in Rhapsody
        resultsPanel.setOnElementDoubleClick(this::navigateToElement);
    }

    private void navigateToElement(String guid) {
        if (snapshot == null || guid == null || guid.isEmpty()) return;

        com.telelogic.rhapsody.core.IRPModelElement elt =
                snapshot.handleByGuid().get(guid);
        if (elt == null) {
            statusBar.setText("  Element not found in model: " + guid);
            return;
        }

        try {
            elt.locateInBrowser();
            statusBar.setText("  Navigated to: " + elt.getName());
        } catch (Throwable t1) {
            try {
                RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();
                conn.getApplication().highLightElement(elt);
                statusBar.setText("  Highlighted: " + elt.getName());
            } catch (Throwable t2) {
                statusBar.setText("  Could not navigate to element (is Rhapsody open?)");
            }
        }
    }

    private void browseFile(JComboBox<String> target, String description, String... extensions) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select " + description);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                description, extensions));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setTextOf(target, chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void loadModel() {
        String modelPath = textOf(modelPathField);
        if (modelPath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select a model file.", "Missing Model",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        setBusy(true);
        statusBar.setText("  Loading model...");
        treePanel.clear();
        resultsPanel.clear();

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private String      error       = null;
            private PackageNode packageTree = null;

            @Override
            protected Void doInBackground() {
                try {
                    progressReporter.onStepStarted(LoadingStep.CONNECTING);
                    RhapsodyConnectionManager conn =
                            RhapsodyConnectionManager.getInstance();
                    conn.connect(modelPath);
                    progressReporter.onStepCompleted(LoadingStep.CONNECTING);

                    progressReporter.onStepStarted(LoadingStep.SCANNING_PACKAGES);
                    RhapsodyPackageScanner scanner = new RhapsodyPackageScanner();
                    packageTree = scanner.scanPackages(conn.getProject());
                    progressReporter.onStepCompleted(LoadingStep.SCANNING_PACKAGES);

                    // Loader reports its own LOADING_ELEMENTS / BUILDING_INDEX steps
                    // internally (with real per-element onProgress counts), so we
                    // just hand it the reporter instead of wrapping it ourselves.
                    RhapsodyModelLoader loader = new RhapsodyModelLoader(progressReporter);
                    snapshot = loader.loadModel(conn.getProject());

                    index = ElementIndex.build(snapshot.records());

                    // Fast scan per wizard suggestions
                    progressReporter.onStepStarted(LoadingStep.FAST_DETECTION);
                    RhapsodyPortInfoResolver portResolver =
                            new RhapsodyPortInfoResolver(snapshot);
                    PortProbeService portProbeService =
                            new PortProbeService(portResolver);
                    DetectionFacade detectionFacade =
                            DetectionFacade.create(portProbeService);
                    fastDetectionResult = detectionFacade.fastScan(
                            snapshot.records(),
                            snapshot.handleByGuid(),
                            conn.getApplication());
                    progressReporter.onStepCompleted(LoadingStep.FAST_DETECTION);

                } catch (Throwable t) {
                    error = t.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                setBusy(false);
                resetProgressBar();
                if (error != null) {
                    statusBar.setText("  Error loading model");
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Error: " + error, "Load Failed",
                            JOptionPane.ERROR_MESSAGE);
                } else {
                    treePanel.loadTree(packageTree);
                    statusBar.setText("  Model loaded: "
                            + snapshot.records().size() + " elements");

                    // Reține calea + timestamp-ul fișierului la momentul acestei scanări
                    // reușite, pentru verificarea rapidă "s-a schimbat modelul?" de mai
                    // târziu (fără nicio reconectare la Rhapsody).
                    recentFiles.addRecentModel(modelPath);
                    freshnessChecker.recordSuccessfulScan(modelPath);
                    refreshRecentItems(modelPathField, recentFiles.recentModels());
                }
                updateButtonStates();
            }
        };
        worker.execute();
    }

    private void runEvaluation() {
        if (snapshot == null || index == null) {
            JOptionPane.showMessageDialog(this,
                    "Load a model first.", "No Model",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String configPath = textOf(configPathField);
        if (configPath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select a config file.", "Missing Config",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Verificare ieftină (doar File.lastModified(), fără conectare la Rhapsody):
        // dacă fișierul modelului pare modificat de la ultima încărcare, avertizează
        // utilizatorul înainte de a rula regulile pe date potențial vechi.
        String modelPath = textOf(modelPathField);
        if (!modelPath.isEmpty() && !freshnessChecker.isModelUnchangedSinceLastScan(modelPath)) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Fișierul modelului pare modificat de la ultima încărcare.\n"
                            + "Rezultatele pot fi neactualizate. Reîncarci modelul acum?",
                    "Model posibil modificat",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                loadModel();
                return; // utilizatorul apasă din nou "Run" după ce reîncărcarea se termină
            }
            // altfel: continuă cu snapshot-ul existent, la alegerea utilizatorului
        }

        String selectedPath = treePanel.getSelectedPath();
        statusBar.setText("  Loading config & running evaluation...");
        setBusy(true);

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private String error = null;

            @Override
            protected Void doInBackground() {
                try {
                    progressReporter.onStepStarted(LoadingStep.LOADING_CONFIG);
                    config = ConfigLoader.load(Paths.get(configPath));
                    progressReporter.onStepCompleted(LoadingStep.LOADING_CONFIG);

                    progressReporter.onStepStarted(LoadingStep.SELECTING_ELEMENTS);
                    RhapsodyAliasResolver aliasResolver =
                            new RhapsodyAliasResolver(config, snapshot);
                    ElementSelector selector = new ElementSelector(index, config);
                    RhapsodyEvaluationContext context = new RhapsodyEvaluationContext(
                            aliasResolver, snapshot, config, index, selector);
                    progressReporter.onStepCompleted(LoadingStep.SELECTING_ELEMENTS);

                    // RuleEngine reports its own EVALUATING_RULES step internally
                    // (with real candidate-count progress + onDone()), so we just
                    // hand it the reporter instead of wrapping it ourselves.
                    RuleEngine engine = new RuleEngine(
                            config, selector, context, selectedPath, progressReporter);

                    RuleEngine.EvaluationSummary summary =
                            engine.evaluateWithSummary();
                    lastResults = summary.allResults();

                } catch (Throwable t) {
                    error = t.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                setBusy(false);
                resetProgressBar();
                if (error != null) {
                    statusBar.setText("  Evaluation error");
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Error: " + error, "Run Failed",
                            JOptionPane.ERROR_MESSAGE);
                } else {
                    resultsPanel.loadResults(lastResults, index);
                    long failCount = lastResults.stream()
                            .filter(r -> r.status() == RuleStatus.FAIL).count();
                    statusBar.setText("  Evaluation complete: " + failCount
                            + " failures"
                            + (selectedPath.isEmpty()
                                    ? "" : " (scope: " + selectedPath + ")"));

                    recentFiles.addRecentConfig(configPath);
                    refreshRecentItems(configPathField, recentFiles.recentConfigs());
                }
                updateButtonStates();
            }
        };
        worker.execute();
    }

    private void exportExcel() {
        if (lastResults == null || lastResults.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No results to export. Run evaluation first.",
                    "No Results", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Excel Report");
        chooser.setSelectedFile(new File("rule-failures-report.xlsx"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Excel Files", "xlsx"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            if (!path.endsWith(".xlsx")) path += ".xlsx";

            try {
                ExcelReportExporter.exportFailures(lastResults, index, path);
                statusBar.setText("  Exported to: " + path);
                JOptionPane.showMessageDialog(this,
                        "Report exported successfully!",
                        "Export Done", JOptionPane.INFORMATION_MESSAGE);
            } catch (Throwable t) {
                JOptionPane.showMessageDialog(this,
                        "Export error: " + t.getMessage(),
                        "Export Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void openWizardNew() {
        WizardDialog dialog = new WizardDialog(this, fastDetectionResult, null);
        dialog.setVisible(true);
        dialog.getSavedConfigPath().ifPresent(path -> {
            setTextOf(configPathField, path);
            try {
                config = ConfigLoader.load(Paths.get(path));
                statusBar.setText("  Config loaded from wizard: " + path);
                updateButtonStates();
            } catch (Exception e) {
                statusBar.setText("  Error reloading saved config");
            }
        });
    }

    private void openWizardEdit() {
        if (config == null) {
            JOptionPane.showMessageDialog(this,
                    "Load a config file first.",
                    "No Config", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (fastDetectionResult == null) {
            JOptionPane.showMessageDialog(this,
                    "No model loaded. Stereotypes and types will not be suggested.\n"
                    + "Load a model first for the best experience.",
                    "No Model Loaded",
                    JOptionPane.INFORMATION_MESSAGE);
        }

        ConfigToWizardStateMapper.MappingResult mapped =
                ConfigToWizardStateMapper.map(config, fastDetectionResult);

        if (mapped.hasWarnings()) {
            String warningText = String.join("\n", mapped.warnings());
            int choice = JOptionPane.showConfirmDialog(this,
                    "Some elements from the config were not detected "
                            + "in the current model:\n\n"
                            + warningText + "\n\nOpen wizard anyway?",
                    "Config Warnings",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
        }

        WizardDialog dialog = new WizardDialog(
                this, fastDetectionResult, mapped.wizardState());
        dialog.setVisible(true);

        dialog.getSavedConfigPath().ifPresent(path -> {
            setTextOf(configPathField, path);
            try {
                config = ConfigLoader.load(Paths.get(path));
                statusBar.setText("  Config updated from wizard: " + path);
                updateButtonStates();
            } catch (Exception e) {
                statusBar.setText("  Error reloading updated config");
            }
        });
    }

    /**
     * Enables/disables every action button at once, used while a background
     * operation (model load or rule evaluation) is running so the user can't
     * trigger overlapping actions (e.g. browsing to a new model path, or
     * running the wizard, while a load/run is still in flight).
     * Once the operation finishes, callers should follow up with
     * updateButtonStates() to restore the state-dependent buttons correctly.
     */
    private void setBusy(boolean busy) {
        boolean enabled = !busy;
        modelBrowseBtn.setEnabled(enabled);
        configBrowseBtn.setEnabled(enabled);
        loadModelBtn.setEnabled(enabled);
        runBtn.setEnabled(enabled);
        exportBtn.setEnabled(enabled);
        newConfigWizardBtn.setEnabled(enabled);
        editConfigWizardBtn.setEnabled(enabled);
    }

    /**
     * Returns the progress bar to its idle look once a background operation
     * finishes (success or failure) — the final outcome message goes on
     * statusBar via the normal done() logic, not through the reporter, so
     * it doesn't get overwritten by onDone()'s generic "Ready" text.
     */
    private void resetProgressBar() {
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        progressBar.setString("");
    }

    private void updateButtonStates() {
        boolean modelLoaded = snapshot != null && index != null;
        runBtn.setEnabled(modelLoaded);
        exportBtn.setEnabled(lastResults != null && !lastResults.isEmpty());
        newConfigWizardBtn.setEnabled(modelLoaded);
        editConfigWizardBtn.setEnabled(modelLoaded && config != null);
    }

    // ── Entry point ───────────────────────────────────────────────────────────
    public static void main(String[] args) {
        FlatNordIJTheme.setup();
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
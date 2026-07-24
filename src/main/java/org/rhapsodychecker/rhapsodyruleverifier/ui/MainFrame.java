// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/ui/MainFrame.java
package org.rhapsodychecker.rhapsodyruleverifier.ui;

import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AppTheme;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.*;
import org.rhapsodychecker.rhapsodyruleverifier.cache.CacheMetadata;
import org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCacheManager;
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
import org.rhapsodychecker.rhapsodyruleverifier.prefs.RecentFilesStore;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.LoadingStep;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AccentColors;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.WizardDialog;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

@SuppressWarnings("serial")
public class MainFrame extends JFrame {

    // Top panel: file paths
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
    private final JButton updateModelBtn      = new JButton("Update Model");
    private final JButton openCacheBtn        = new JButton("\uD83D\uDCC1 Cache");

    // Main panels
    private final PackageTreePanel  treePanel    = new PackageTreePanel();
    private final ResultsTablePanel resultsPanel = new ResultsTablePanel();

    // Status
    private final JLabel       statusBar   = new JLabel("  Ready");
    private final JProgressBar progressBar = new JProgressBar();

    private final ProgressReporter progressReporter =
            new SwingProgressReporter(progressBar, statusBar);

    // Recent files
    private final RecentFilesStore recentFiles = new RecentFilesStore();

    // State
    private RhapsodyModelSnapshot snapshot;
    private ElementIndex           index;
    private RuleCheckerConfig      config;
    private List<RuleResult>       lastResults;
    private FastDetectionResult    fastDetectionResult;
    private boolean                loadedFromCache = false;

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

    private void prefillRecentPaths() {
        refreshRecentItems(modelPathField, recentFiles.recentModels());
        refreshRecentItems(configPathField, recentFiles.recentConfigs());
    }

    private void refreshRecentItems(JComboBox<String> combo, List<String> recentPaths) {
        String current = textOf(combo);
        combo.setModel(new DefaultComboBoxModel<>(new Vector<>(recentPaths)));
        if (!current.isEmpty()) {
            setTextOf(combo, current);
        } else if (!recentPaths.isEmpty()) {
            combo.setSelectedIndex(0);
        }
    }

    private static String textOf(JComboBox<String> combo) {
        Object item = combo.getEditor().getItem();
        return item == null ? "" : item.toString().trim();
    }

    private static void setTextOf(JComboBox<String> combo, String text) {
        combo.getEditor().setItem(text);
        combo.setSelectedItem(text);
    }

    private void initLayout() {
        setLayout(new BorderLayout(5, 5));

        // ── Top panel ────────────────────────────────────────────────────────
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        modelPathField.setEditable(true);
        configPathField.setEditable(true);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        topPanel.add(new JLabel("Model (.rpyx):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        topPanel.add(modelPathField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        topPanel.add(modelBrowseBtn, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        topPanel.add(new JLabel("Config (.yaml):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        topPanel.add(configPathField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        topPanel.add(configBrowseBtn, gbc);

        // Model / run side — orange outline
        modelBrowseBtn.putClientProperty("FlatLaf.style", AccentColors.ORANGE_HOVER_STYLE);
        loadModelBtn.putClientProperty("FlatLaf.style", AccentColors.ORANGE_HOVER_STYLE);
        updateModelBtn.putClientProperty("FlatLaf.style", AccentColors.ORANGE_HOVER_STYLE);
        runBtn.putClientProperty("FlatLaf.style", AccentColors.ORANGE_HOVER_STYLE);

        // Config / wizard side — purple outline
        configBrowseBtn.putClientProperty("FlatLaf.style", AccentColors.PURPLE_HOVER_STYLE);
        newConfigWizardBtn.putClientProperty("FlatLaf.style", AccentColors.PURPLE_HOVER_STYLE);
        editConfigWizardBtn.putClientProperty("FlatLaf.style", AccentColors.PURPLE_HOVER_STYLE);

        // Cache button tooltip
        openCacheBtn.setToolTipText("Open cache folder in Explorer");

        JPanel buttonPanel = new JPanel(new BorderLayout());

        // Left-aligned buttons
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftButtons.add(loadModelBtn);
        leftButtons.add(runBtn);
        leftButtons.add(exportBtn);
        leftButtons.add(newConfigWizardBtn);
        leftButtons.add(editConfigWizardBtn);
        leftButtons.add(updateModelBtn);

        // Right-aligned cache button
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightButtons.add(openCacheBtn);

        buttonPanel.add(leftButtons, BorderLayout.WEST);
        buttonPanel.add(rightButtons, BorderLayout.EAST);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3; gbc.weightx = 1;
        topPanel.add(buttonPanel, gbc);

        add(topPanel, BorderLayout.NORTH);

        // ── Center ───────────────────────────────────────────────────────────
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, treePanel, resultsPanel);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.3);
        add(splitPane, BorderLayout.CENTER);

        // ── Bottom ───────────────────────────────────────────────────────────
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
        loadModelBtn.addActionListener(e   -> loadModelWithCacheCheck());
        runBtn.addActionListener(e         -> runEvaluation());
        exportBtn.addActionListener(e      -> exportExcel());
        newConfigWizardBtn.addActionListener(e  -> openWizardNew());
        editConfigWizardBtn.addActionListener(e -> openWizardEdit());

        // Update Model = always reload from Rhapsody, overwrite cache
        updateModelBtn.addActionListener(e -> loadModelFromRhapsody());

        // Open cache folder in Explorer
        openCacheBtn.addActionListener(e -> openCacheFolder());

        resultsPanel.setOnElementDoubleClick(this::navigateToElement);
    }

    // ── Cache-aware model loading ────────────────────────────────────────────

    /**
     * "Load Model" button: checks if cache exists, offers user the choice.
     */
    private void loadModelWithCacheCheck() {
        String modelPath = textOf(modelPathField);
        if (modelPath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select a model file.", "Missing Model",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        File cacheFile = ModelCacheManager.defaultCacheFile(modelPath);
        if (ModelCacheManager.cacheExists(cacheFile)) {
            int choice = JOptionPane.showOptionDialog(this,
                    "A cached version of this model was found.\n"
                    + "Load from cache (fast) or reload from Rhapsody?",
                    "Cache Available",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    new String[]{"Load from Cache", "Reload from Rhapsody"},
                    "Load from Cache");

            if (choice == 0) {
                loadModelFromCache(modelPath, cacheFile);
            } else {
                loadModelFromRhapsody();
            }
        } else {
            loadModelFromRhapsody();
        }
    }

    /**
     * Load model from JSON cache — no Rhapsody connection needed.
     */
    private void loadModelFromCache(String modelPath, File cacheFile) {
        setBusy(true);
        statusBar.setText("  Loading model from cache...");
        treePanel.clear();
        resultsPanel.clear();

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private String error = null;
            private CacheMetadata metadata = null;

            @Override
            protected Void doInBackground() {
                try {
                    ModelCacheManager.CacheLoadResult result =
                            ModelCacheManager.readCache(cacheFile);
                    snapshot = result.snapshot();
                    metadata = result.metadata();
                    index = ElementIndex.build(snapshot.records());

                    // Build package tree from cached element data
                    // No fast detection from cache
                    fastDetectionResult = null;
                    loadedFromCache = true;

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
                    loadedFromCache = false;
                    statusBar.setText("  Error loading cache");
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Cache load failed: " + error
                            + "\n\nTry reloading from Rhapsody.",
                            "Cache Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    // Build and show package tree from cached records
                    PackageNode cachedTree = buildPackageTreeFromRecords(
                            snapshot.records(), metadata.getProjectName());
                    treePanel.loadTree(cachedTree);
                    statusBar.setText("  Loaded from cache (" + metadata.getCachedAt()
                            + ") \u2014 " + snapshot.records().size() + " elements");
                    recentFiles.addRecentModel(modelPath);
                    refreshRecentItems(modelPathField, recentFiles.recentModels());
                }
                updateButtonStates();
            }
        };
        worker.execute();
    }

    /**
     * Load model from live Rhapsody connection, then write/overwrite cache.
     */
    private void loadModelFromRhapsody() {
        String modelPath = textOf(modelPathField);
        if (modelPath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select a model file.", "Missing Model",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        setBusy(true);
        statusBar.setText("  Loading model from Rhapsody...");
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

                    RhapsodyModelLoader loader = new RhapsodyModelLoader(progressReporter);
                    snapshot = loader.loadModel(conn.getProject());

                    index = ElementIndex.build(snapshot.records());
                    loadedFromCache = false;

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

                    // Write cache after successful load
                    try {
                        String projectName = conn.getProject().getName();
                        String projectGuid = conn.getProject().getGUID();
                        File cacheFile = ModelCacheManager.defaultCacheFile(modelPath);
                        ModelCacheManager.writeCache(snapshot, projectName, projectGuid, cacheFile);
                    } catch (Throwable cacheErr) {
                        // Cache write failure is non-fatal — just log and continue
                        System.err.println("Warning: cache write failed: " + cacheErr.getMessage());
                    }

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
                    statusBar.setText("  Model loaded from Rhapsody (cache updated): "
                            + snapshot.records().size() + " elements");
                    recentFiles.addRecentModel(modelPath);
                    refreshRecentItems(modelPathField, recentFiles.recentModels());
                }
                updateButtonStates();
            }
        };
        worker.execute();
    }

    // ── Navigation ──────────────────────────────────────────────────────────

    private void navigateToElement(String guid) {
        if (snapshot == null || guid == null || guid.isEmpty()) return;

        if (loadedFromCache) {
            statusBar.setText("  Navigation requires live Rhapsody connection (loaded from cache)");
            return;
        }

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

    // ── Cache folder ────────────────────────────────────────────────────────

    private void openCacheFolder() {
        String modelPath = textOf(modelPathField);
        if (modelPath.isEmpty()) return;

        File cacheFile = ModelCacheManager.defaultCacheFile(modelPath);
        File cacheDir = cacheFile.getParentFile();

        if (cacheDir == null || !cacheDir.exists()) {
            statusBar.setText("  No cache folder found");
            return;
        }

        try {
            Desktop.getDesktop().open(cacheDir);
        } catch (Throwable t) {
            try {
                Runtime.getRuntime().exec("explorer.exe " + cacheDir.getAbsolutePath());
            } catch (Throwable t2) {
                statusBar.setText("  Could not open cache folder");
            }
        }
    }

    // ── Evaluation ──────────────────────────────────────────────────────────

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

                    RuleEngine engine = new RuleEngine(
                            config, selector, context, selectedPath, progressReporter);
                    lastResults = engine.evaluateWithSummary().allResults();

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
                    String source = loadedFromCache ? " (from cache)" : "";
                    statusBar.setText("  Evaluation complete" + source + ": " + failCount
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

    // ── Export ───────────────────────────────────────────────────────────────

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

    // ── Wizard ──────────────────────────────────────────────────────────────

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

    // ── UI helpers ──────────────────────────────────────────────────────────

    private void browseFile(JComboBox<String> target, String description,
                            String... extensions) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select " + description);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                description, extensions));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setTextOf(target, chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void setBusy(boolean busy) {
        boolean enabled = !busy;
        modelBrowseBtn.setEnabled(enabled);
        configBrowseBtn.setEnabled(enabled);
        loadModelBtn.setEnabled(enabled);
        runBtn.setEnabled(enabled);
        exportBtn.setEnabled(enabled);
        newConfigWizardBtn.setEnabled(enabled);
        editConfigWizardBtn.setEnabled(enabled);
        updateModelBtn.setEnabled(enabled);
        openCacheBtn.setEnabled(enabled);
    }

    private void resetProgressBar() {
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        progressBar.setString("");
    }

    private void updateButtonStates() {
        boolean modelLoaded = snapshot != null && index != null;
        runBtn.setEnabled(modelLoaded);
        exportBtn.setEnabled(lastResults != null && !lastResults.isEmpty());
        updateModelBtn.setEnabled(modelLoaded);

        // Wizard works with or without fast detection — just won't have auto-suggestions from cache
        newConfigWizardBtn.setEnabled(modelLoaded);
        editConfigWizardBtn.setEnabled(modelLoaded && config != null);

        // Cache button: enabled if a model path is set and cache file exists
        String modelPath = textOf(modelPathField);
        boolean cacheAvailable = !modelPath.isEmpty()
                && ModelCacheManager.cacheExists(ModelCacheManager.defaultCacheFile(modelPath));
        openCacheBtn.setEnabled(cacheAvailable);
    }

    // ── Package tree from cached data ───────────────────────────────────────

    /**
     * Builds a PackageNode tree from cached ElementRecords.
     * Filters for PACKAGE elements and reconstructs the hierarchy from ownerPath.
     */
    private static PackageNode buildPackageTreeFromRecords(
            List<ElementRecord> records, String projectName) {
        PackageNode root = new PackageNode("", projectName != null ? projectName : "Project", "");

        // Collect all package elements
        Map<String, PackageNode> nodesByPath = new LinkedHashMap<String, PackageNode>();
        for (ElementRecord r : records) {
            if (r.kind() != ElementKind.PACKAGE) continue;
            String ownerPath = r.ownerPath().orElse("");
            String qualifiedPath = ownerPath.isEmpty() ? r.name() : ownerPath + "::" + r.name();
            PackageNode node = new PackageNode(r.guid(), r.name(), qualifiedPath);
            nodesByPath.put(qualifiedPath, node);
        }

        // Build hierarchy: attach each node to its parent
        for (Map.Entry<String, PackageNode> entry : nodesByPath.entrySet()) {
            String path = entry.getKey();
            PackageNode node = entry.getValue();

            int lastSep = path.lastIndexOf("::");
            if (lastSep < 0) {
                // Top-level package — child of root
                root.addChild(node);
            } else {
                String parentPath = path.substring(0, lastSep);
                PackageNode parent = nodesByPath.get(parentPath);
                if (parent != null) {
                    parent.addChild(node);
                } else {
                    // Parent not found (shouldn't happen), attach to root
                    root.addChild(node);
                }
            }
        }

        return root;
    }

    // ── Entry point ───────────────────────────────────────────────────────────
    public static void main(String[] args) {
        AppTheme.apply();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/ui/MainFrame.java
package org.rhapsodychecker.rhapsodyruleverifier.ui;

import com.formdev.flatlaf.FlatLightLaf;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.*;
import org.rhapsodychecker.rhapsodyruleverifier.config.ConfigLoader;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleEngine;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleStatus;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;
import org.rhapsodychecker.rhapsodyruleverifier.export.ExcelReportExporter;
import com.telelogic.rhapsody.core.IRPModelElement;


import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Paths;
import java.util.List;

public class MainFrame extends JFrame {

    // Top panel: file paths
    private final JTextField modelPathField = new JTextField(40);
    private final JTextField configPathField = new JTextField(40);
    private final JButton modelBrowseBtn = new JButton("Browse...");
    private final JButton configBrowseBtn = new JButton("Browse...");

    // Action buttons
    private final JButton loadModelBtn = new JButton("Load Model");
    private final JButton runBtn = new JButton("Run");
    private final JButton exportBtn = new JButton("Export Excel");

    // Main panels
    private final PackageTreePanel treePanel = new PackageTreePanel();
    private final ResultsTablePanel resultsPanel = new ResultsTablePanel();

    // Status
    private final JLabel statusBar = new JLabel("  Ready");

    // State
    private RhapsodyModelSnapshot snapshot;
    private ElementIndex index;
    private RuleCheckerConfig config;
    private List<RuleResult> lastResults;

    public MainFrame() {
        super("Rhapsody Rule Checker");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 700);
        setLocationRelativeTo(null);
        initLayout();
        initListeners();
        updateButtonStates();
    }

    private void initLayout() {
        setLayout(new BorderLayout(5, 5));

        // ── Top panel: file paths + buttons ──
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

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
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3; gbc.weightx = 1;
        topPanel.add(buttonPanel, gbc);

        add(topPanel, BorderLayout.NORTH);

        // ── Center: tree + results ──
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel, resultsPanel);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.3);
        add(splitPane, BorderLayout.CENTER);

        // ── Bottom: status bar ──
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(3, 5, 3, 5)));
        add(statusBar, BorderLayout.SOUTH);
    }

    private void initListeners() {
        modelBrowseBtn.addActionListener(e -> browseFile(modelPathField, "Rhapsody Model", "rpyx", "rpy"));
        configBrowseBtn.addActionListener(e -> browseFile(configPathField, "YAML Config", "yaml", "yml"));
        loadModelBtn.addActionListener(e -> loadModel());
        runBtn.addActionListener(e -> runEvaluation());
        exportBtn.addActionListener(e -> exportExcel());

        // Double-click on result row -> navigate in Rhapsody
        resultsPanel.setOnElementDoubleClick(this::navigateToElement);
    }

    private void navigateToElement(String guid) {
        if (snapshot == null || guid == null || guid.isEmpty()) return;

        com.telelogic.rhapsody.core.IRPModelElement elt = snapshot.handleByGuid().get(guid);
        if (elt == null) {
            statusBar.setText("  Element not found in model: " + guid);
            return;
        }

        try {
            // Try locateInBrowser first (opens and highlights in Rhapsody browser)
            elt.locateInBrowser();
            statusBar.setText("  Navigated to: " + elt.getName());
        } catch (Throwable t1) {
            try {
                // Fallback: try highLightElement via the application
                RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();
                conn.getApplication().highLightElement(elt);
                statusBar.setText("  Highlighted: " + elt.getName());
            } catch (Throwable t2) {
                statusBar.setText("  Could not navigate to element (is Rhapsody open?)");
            }
        }
    }


    private void browseFile(JTextField target, String description, String... extensions) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select " + description);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(description, extensions));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            target.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void loadModel() {
        String modelPath = modelPathField.getText().trim();

        if (modelPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a model file.", "Missing Model", JOptionPane.WARNING_MESSAGE);
            return;
        }

        loadModelBtn.setEnabled(false);
        statusBar.setText("  Loading model...");
        resultsPanel.clear();

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private String error = null;
            private PackageNode packageTree = null;

            @Override
            protected Void doInBackground() {
                try {
                    RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();
                    conn.connect(modelPath);

                    RhapsodyPackageScanner scanner = new RhapsodyPackageScanner();
                    packageTree = scanner.scanPackages(conn.getProject());

                    RhapsodyModelLoader loader = new RhapsodyModelLoader();
                    snapshot = loader.loadModel(conn.getProject());

                    index = ElementIndex.build(snapshot.records());

                    // Config NOT loaded here anymore
                } catch (Throwable t) {
                    error = t.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                loadModelBtn.setEnabled(true);
                if (error != null) {
                    statusBar.setText("  Error loading model");
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Error: " + error, "Load Failed", JOptionPane.ERROR_MESSAGE);
                } else {
                    treePanel.loadTree(packageTree);
                    statusBar.setText("  Model loaded: " + snapshot.records().size() + " elements");
                }
                updateButtonStates();
            }
        };
        worker.execute();
    }

    private void runEvaluation() {
        if (snapshot == null || index == null) {
            JOptionPane.showMessageDialog(this, "Load a model first.", "No Model", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String configPath = configPathField.getText().trim();
        if (configPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a config file.", "Missing Config", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String selectedPath = treePanel.getSelectedPath();
        statusBar.setText("  Loading config & running evaluation...");
        runBtn.setEnabled(false);

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private String error = null;

            @Override
            protected Void doInBackground() {
                try {
                    config = ConfigLoader.load(Paths.get(configPath));

                    RhapsodyAliasResolver aliasResolver = new RhapsodyAliasResolver(config, snapshot);
                    ElementSelector selector = new ElementSelector(index, config);
                    RhapsodyEvaluationContext context = new RhapsodyEvaluationContext(
                            aliasResolver, snapshot, config, index, selector);

                    // Scope filtering is now inside the engine
                    RuleEngine engine = new RuleEngine(config, selector, context, selectedPath);

                    RuleEngine.EvaluationSummary summary = engine.evaluateWithSummary();
                    lastResults = summary.allResults();
                } catch (Throwable t) {
                    error = t.getMessage();
                }
                return null;
            }


            @Override
            protected void done() {
                runBtn.setEnabled(true);
                if (error != null) {
                    statusBar.setText("  Evaluation error");
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Error: " + error, "Run Failed", JOptionPane.ERROR_MESSAGE);
                } else {
                    resultsPanel.loadResults(lastResults, index);
                    long failCount = lastResults.stream()
                            .filter(r -> r.status() == RuleStatus.FAIL).count();
                    statusBar.setText("  Evaluation complete: " + failCount + " failures"
                            + (selectedPath.isEmpty() ? "" : " (scope: " + selectedPath + ")"));
                }
                updateButtonStates();
            }
        };
        worker.execute();
    }


    private void exportExcel() {
        if (lastResults == null || lastResults.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No results to export. Run evaluation first.",
                    "No Results", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Excel Report");
        chooser.setSelectedFile(new File("rule-failures-report.xlsx"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Excel Files", "xlsx"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            if (!path.endsWith(".xlsx")) path += ".xlsx";

            try {
                ExcelReportExporter.exportFailures(lastResults, index, path);
                statusBar.setText("  Exported to: " + path);
                JOptionPane.showMessageDialog(this, "Report exported successfully!",
                        "Export Done", JOptionPane.INFORMATION_MESSAGE);
            } catch (Throwable t) {
                JOptionPane.showMessageDialog(this, "Export error: " + t.getMessage(),
                        "Export Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void updateButtonStates() {
        boolean modelLoaded = snapshot != null && index != null;
        runBtn.setEnabled(modelLoaded);
        exportBtn.setEnabled(lastResults != null && !lastResults.isEmpty());
    }


    // ── Entry point ──
    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}

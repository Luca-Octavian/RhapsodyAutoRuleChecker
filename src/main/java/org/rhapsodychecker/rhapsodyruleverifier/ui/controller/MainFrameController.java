// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/ui/controller/MainFrameController.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.controller;

import org.rhapsodychecker.rhapsodyruleverifier.core.AppLogger;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyAliasResolver;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyEvaluationContext;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCacheManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.config.ConfigLoader;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.ConfigToWizardStateMapper;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;
import org.rhapsodychecker.rhapsodyruleverifier.core.service.EvaluationService;
import org.rhapsodychecker.rhapsodyruleverifier.core.service.ExportService;
import org.rhapsodychecker.rhapsodyruleverifier.core.service.ModelLoadService;
import org.rhapsodychecker.rhapsodyruleverifier.core.service.NavigationService;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;
import org.rhapsodychecker.rhapsodyruleverifier.prefs.RecentFilesStore;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.WizardDialog;

import javax.swing.*;
import java.io.File;
import java.nio.file.Paths;
import java.util.List;

/**
 * Coordinates UI events → business logic → UI updates.
 * Holds application state, manages SwingWorkers, delegates to services.
 * No layout or component creation — that's MainFrame's job.
 */
public final class MainFrameController {

    /**
     * Minimal interface for MainFrame to expose its UI components to the controller.
     */
    public interface View {
        String modelPath();
        String configPath();
        void setModelPath(String path);
        void setConfigPath(String path);
        String selectedScope();
        void setStatus(String text);
        void loadTree(org.rhapsodychecker.rhapsodyruleverifier.ui.PackageNode tree);
        void loadResults(List<RuleResult> results, ElementIndex index, List<RuleSpec> specs);
        void clearTree();
        void clearResults();
        void setBusy(boolean busy);
        void resetProgress();
        void refreshRecentModels(List<String> paths);
        void refreshRecentConfigs(List<String> paths);
        void showWarning(String message, String title);
        void showError(String message, String title);
        void showInfo(String message, String title);
        void updateButtonStates();
        JFrame frame();
    }

    // ── State ───────────────────────────────────────────────────────────────
    private final View view;
    private final ProgressReporter progressReporter;
    private final RecentFilesStore recentFiles = new RecentFilesStore();

    private RhapsodyModelSnapshot snapshot;
    private ElementIndex          index;
    private RuleCheckerConfig     config;
    private List<RuleResult>      lastResults;
    private FastDetectionResult   fastDetectionResult;
    private boolean               loadedFromCache = false;

    public MainFrameController(View view, ProgressReporter progressReporter) {
        this.view = view;
        this.progressReporter = progressReporter;
    }

    // ── Initialization ──────────────────────────────────────────────────────

    public void init() {
        view.refreshRecentModels(recentFiles.recentModels());
        view.refreshRecentConfigs(recentFiles.recentConfigs());
        updateButtonStates();
    }

    // ── Action handlers (called by MainFrame button listeners) ──────────────

    public void onLoadModel() {
        String modelPath = view.modelPath();
        if (modelPath.isEmpty()) {
            view.showWarning("Please select a model file.", "Missing Model");
            return;
        }

        File cacheFile = ModelCacheManager.defaultCacheFile(modelPath);
        if (ModelCacheManager.cacheExists(cacheFile)) {
            int choice = JOptionPane.showOptionDialog(view.frame(),
                    "A cached version of this model was found.\n"
                    + "Load from cache (fast) or reload from Rhapsody?",
                    "Cache Available",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    new String[]{"Load from Cache", "Reload from Rhapsody"},
                    "Load from Cache");
            if (choice == 0) {
                doLoadFromCache(modelPath, cacheFile);
            } else {
                doLoadFromRhapsody(modelPath);
            }
        } else {
            doLoadFromRhapsody(modelPath);
        }
    }

    public void onUpdateModel() {
        String modelPath = view.modelPath();
        if (modelPath.isEmpty()) {
            view.showWarning("Please select a model file.", "Missing Model");
            return;
        }

        // Use incremental update whenever a cache exists — works for both warm start
        // (Rhapsody already connected) and cold start (loaded from cache only).
        // loadIncrementalUpdate() handles Rhapsody connection + package scan warm-up
        // internally. File timestamp fast-skip avoids COM entirely when nothing changed.
        File cacheFile = ModelCacheManager.defaultCacheFile(modelPath);

        if (ModelCacheManager.cacheExists(cacheFile)) {
            doIncrementalUpdate(modelPath, cacheFile);
        } else {
            doLoadFromRhapsody(modelPath);
        }
    }

    public void onRunEvaluation() {
        if (snapshot == null || index == null) {
            view.showWarning("Load a model first.", "No Model");
            return;
        }
        String configPath = view.configPath();
        if (configPath.isEmpty()) {
            view.showWarning("Please select a config file.", "Missing Config");
            return;
        }

        String scopePath = view.selectedScope();
        view.setBusy(true);
        view.setStatus("  Loading config & running evaluation...");

        new SwingWorker<EvaluationService.EvalResult, Void>() {
            private String error;
            private EvaluationService.EvalResult evalResult;

            @Override
            protected EvaluationService.EvalResult doInBackground() {
                try {
                    // Adapter wiring: build Rhapsody-specific context via factory
                    EvaluationService.ContextFactory contextFactory = (cfg, idx) -> {
                        RhapsodyAliasResolver aliasResolver = new RhapsodyAliasResolver(snapshot);
                        ElementSelector selector = new ElementSelector(idx, cfg);
                        RhapsodyEvaluationContext context = new RhapsodyEvaluationContext(
                                aliasResolver, snapshot, idx, selector);
                        return new EvaluationService.ContextPair(context, selector);
                    };
                    evalResult = EvaluationService.evaluate(
                            configPath, contextFactory, index, scopePath,
                            loadedFromCache, progressReporter);
                } catch (Throwable t) {
                    error = t.getMessage();
                }
                return evalResult;
            }

            @Override
            protected void done() {
                view.setBusy(false);
                view.resetProgress();
                if (error != null) {
                    AppLogger.error("Evaluation failed: " + error);
                    view.setStatus("  Evaluation error");
                    view.showError("Error: " + error, "Run Failed");
                } else {
                    config = evalResult.config();
                    lastResults = evalResult.results();
                    view.loadResults(lastResults, index, config.rules());
                    view.setStatus("  " + evalResult.statusMessage());
                    recentFiles.addRecentConfig(configPath);
                    view.refreshRecentConfigs(recentFiles.recentConfigs());
                }
                updateButtonStates();
            }
        }.execute();
    }

    public void onExportExcel() {
        if (lastResults == null || lastResults.isEmpty()) {
            view.showWarning("No results to export. Run evaluation first.", "No Results");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Excel Report");
        chooser.setSelectedFile(new File("rule-failures-report.xlsx"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Excel Files", "xlsx"));

        if (chooser.showSaveDialog(view.frame()) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            if (!path.endsWith(".xlsx")) path += ".xlsx";
            try {
                ExportService.exportToExcel(lastResults, index, path);
                view.setStatus("  Exported to: " + path);
                view.showInfo("Report exported successfully!", "Export Done");
            } catch (Throwable t) {
                view.showError("Export error: " + t.getMessage(), "Export Failed");
            }
        }
    }

    public void onNavigateToElement(String guid) {
        String msg = NavigationService.navigateToElement(guid, snapshot, loadedFromCache);
        if (msg != null) view.setStatus(msg);
    }

    public void onOpenCacheFolder() {
        String msg = NavigationService.openCacheFolder(view.modelPath());
        if (msg != null) view.setStatus(msg);
    }

    public void onWizardNew() {
        WizardDialog dialog = new WizardDialog(view.frame(), fastDetectionResult, null);
        dialog.setVisible(true);
        dialog.getSavedConfigPath().ifPresent(this::reloadConfigFromWizard);
    }

    public void onWizardEdit() {
        if (config == null) {
            view.showWarning("Load a config file first.", "No Config");
            return;
        }
        if (fastDetectionResult == null) {
            view.showInfo(
                    "No model loaded. Stereotypes and types will not be suggested.\n"
                    + "Load a model first for the best experience.",
                    "No Model Loaded");
        }

        ConfigToWizardStateMapper.MappingResult mapped =
                ConfigToWizardStateMapper.map(config, fastDetectionResult, view.configPath());

        if (mapped.hasWarnings()) {
            String warningText = String.join("\n", mapped.warnings());
            int choice = JOptionPane.showConfirmDialog(view.frame(),
                    "Some elements from the config were not detected "
                    + "in the current model:\n\n" + warningText + "\n\nOpen wizard anyway?",
                    "Config Warnings", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
        }

        WizardDialog dialog = new WizardDialog(
                view.frame(), fastDetectionResult, mapped.wizardState());
        dialog.setVisible(true);
        dialog.getSavedConfigPath().ifPresent(this::reloadConfigFromWizard);
    }

    // ── Button state queries (called by MainFrame.updateButtonStates) ───────

    public boolean isModelLoaded() { return snapshot != null && index != null; }
    public boolean hasResults() { return lastResults != null && !lastResults.isEmpty(); }
    public boolean hasConfig() { return config != null; }
    public boolean isCacheAvailable(String modelPath) {
        return !modelPath.isEmpty()
                && ModelCacheManager.cacheExists(ModelCacheManager.defaultCacheFile(modelPath));
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private void doLoadFromCache(String modelPath, File cacheFile) {
        view.setBusy(true);
        view.setStatus("  Loading model from cache...");
        view.clearTree();
        view.clearResults();

        new SwingWorker<ModelLoadService.LoadResult, Void>() {
            private String error;
            private ModelLoadService.LoadResult loadResult;

            @Override
            protected ModelLoadService.LoadResult doInBackground() {
                try {
                    loadResult = ModelLoadService.loadFromCache(cacheFile);
                } catch (Throwable t) {
                    error = t.getMessage();
                }
                return loadResult;
            }

            @Override
            protected void done() {
                view.setBusy(false);
                view.resetProgress();
                if (error != null) {
                    AppLogger.error("Cache load failed: " + error);
                    loadedFromCache = false;
                    view.setStatus("  Error loading cache");
                    view.showError("Cache load failed: " + error
                            + "\n\nTry reloading from Rhapsody.", "Cache Error");
                } else {
                    applyLoadResult(loadResult, modelPath);
                }
                updateButtonStates();
            }
        }.execute();
    }

    private void doIncrementalUpdate(String modelPath, File cacheFile) {
        view.setBusy(true);
        view.setStatus("  Smart Update: scanning for changes...");
        view.clearResults();

        new SwingWorker<ModelLoadService.LoadResult, Void>() {
            private String error;
            private ModelLoadService.LoadResult loadResult;

            @Override
            protected ModelLoadService.LoadResult doInBackground() {
                try {
                    loadResult = ModelLoadService.loadIncrementalUpdate(
                            modelPath, cacheFile, progressReporter);
                } catch (Throwable t) {
                    error = t.getMessage();
                }
                return loadResult;
            }

            @Override
            protected void done() {
                view.setBusy(false);
                view.resetProgress();
                if (error != null) {
                    AppLogger.error("Incremental update failed: " + error);
                    view.setStatus("  Smart Update failed, falling back to full reload");
                    // Fallback to full reload
                    doLoadFromRhapsody(modelPath);
                } else {
                    applyLoadResult(loadResult, modelPath);
                }
                updateButtonStates();
            }
        }.execute();
    }

    private void doLoadFromRhapsody(String modelPath) {
        view.setBusy(true);
        view.setStatus("  Loading model from Rhapsody...");
        view.clearTree();
        view.clearResults();

        new SwingWorker<ModelLoadService.LoadResult, Void>() {
            private String error;
            private ModelLoadService.LoadResult loadResult;

            @Override
            protected ModelLoadService.LoadResult doInBackground() {
                try {
                    loadResult = ModelLoadService.loadFromRhapsody(modelPath, progressReporter);
                } catch (Throwable t) {
                    error = t.getMessage();
                }
                return loadResult;
            }

            @Override
            protected void done() {
                view.setBusy(false);
                view.resetProgress();
                if (error != null) {
                    AppLogger.error("Model load from Rhapsody failed: " + error);
                    view.setStatus("  Error loading model");
                    view.showError("Error: " + error, "Load Failed");
                } else {
                    applyLoadResult(loadResult, modelPath);
                }
                updateButtonStates();
            }
        }.execute();
    }

    private void applyLoadResult(ModelLoadService.LoadResult result, String modelPath) {
        snapshot = result.snapshot();
        index = result.index();
        fastDetectionResult = result.detectionResult();
        loadedFromCache = result.isFromCache();
        view.loadTree(result.packageTree());
        view.setStatus("  " + result.statusMessage());
        recentFiles.addRecentModel(modelPath);
        view.refreshRecentModels(recentFiles.recentModels());
    }

    private void reloadConfigFromWizard(String path) {
        view.setConfigPath(path);
        try {
            config = ConfigLoader.load(Paths.get(path));
            view.setStatus("  Config loaded from wizard: " + path);
            updateButtonStates();
        } catch (Exception e) {
            view.setStatus("  Error reloading saved config");
        }
    }

    private void updateButtonStates() {
        view.updateButtonStates();
    }
}
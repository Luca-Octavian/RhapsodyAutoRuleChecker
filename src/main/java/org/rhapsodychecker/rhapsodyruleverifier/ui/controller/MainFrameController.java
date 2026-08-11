// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/ui/controller/MainFrameController.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.controller;

import org.rhapsodychecker.rhapsodyruleverifier.core.AppLogger;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyAliasResolver;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyEvaluationContext;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.cache.CacheMetadata;
import org.rhapsodychecker.rhapsodyruleverifier.cache.CacheReadException;
import org.rhapsodychecker.rhapsodyruleverifier.cache.CacheStatus;
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
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AccentColors;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.GradientAccentButton;

import javax.swing.*;
import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
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
    /** Canonical path of the model that is currently loaded in memory. */
    private String                loadedModelPath = null;

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

        File cacheFile = ModelCacheManager.resolveCacheFile(modelPath);
        if (ModelCacheManager.cacheExists(cacheFile)) {
            // Inspect the cache before offering it: a corrupt / foreign / outdated
            // cache must be reported as such, not silently presented as an option.
            CacheMetadata metadata = inspectCache(modelPath, cacheFile);
            if (metadata == null) {
                // inspectCache() already told the user why; go straight to Rhapsody.
                doLoadFromRhapsody(modelPath);
                return;
            }

            int choice = showCacheLoadChoice(metadata);
            if (choice == 0) {
                doLoadFromCache(modelPath, cacheFile);
            } else if (choice == 1) {
                doLoadFromRhapsody(modelPath);
            }
        } else {
            doLoadFromRhapsody(modelPath);
        }
    }

    /**
     * Validate a cache file before offering/using it.
     *
     * @return its metadata when usable, or null after warning the user why not
     */
    private CacheMetadata inspectCache(String modelPath, File cacheFile) {
        try {
            CacheMetadata metadata = ModelCacheManager.readMetadataOnly(cacheFile);
            if (metadata == null) {
                view.showWarning("The cache file contains no metadata and will be rebuilt.\n\n"
                        + cacheFile.getAbsolutePath(), "Cache Unusable");
                return null;
            }
            if (metadata.getCacheVersion() != CacheMetadata.CURRENT_VERSION) {
                view.showWarning("This cache was written by a different version of the tool"
                        + " (format " + metadata.getCacheVersion() + ", expected "
                        + CacheMetadata.CURRENT_VERSION + ").\n\n"
                        + "It will be rebuilt from Rhapsody.", "Cache Outdated");
                return null;
            }
            String cachedPath = metadata.getCanonicalModelPath();
            if (cachedPath != null && !cachedPath.isEmpty()
                    && !cachedPath.equals(ModelCacheManager.canonicalPath(modelPath))) {
                view.showWarning("The cache found for this file belongs to a different model:\n\n"
                        + cachedPath + "\n\nIt will not be used.", "Cache Mismatch");
                return null;
            }
            return metadata;
        } catch (CacheReadException e) {
            AppLogger.warn("Cache rejected (" + e.status() + "): " + e.getMessage());
            view.showWarning(e.getMessage() + "\n\nThe model will be loaded from Rhapsody instead.",
                    "Cache " + (e.status() == CacheStatus.CACHE_CORRUPT ? "Corrupted" : "Rejected"));
            return null;
        } catch (Exception e) {
            AppLogger.warn("Cache could not be inspected: " + e.getMessage());
            view.showWarning("The cache could not be read: " + e.getMessage()
                    + "\n\nThe model will be loaded from Rhapsody instead.", "Cache Unreadable");
            return null;
        }
    }

    /**
     * Offers the cached-model choice after Load Model.
     */
    private int showCacheLoadChoice(CacheMetadata metadata) {
        JOptionPane pane = new JOptionPane(
                "A cached version of this model was found.\n\n"
                + "  Captured:  " + metadata.getCachedAt() + "\n"
                + "  Elements:  " + metadata.getElementCount() + "\n"
                + (metadata.isSnapshotComplete()
                        ? "" : "  Warning:   the cached scan was incomplete\n")
                + "\nLoading from cache does not contact Rhapsody, so it reflects the\n"
                + "model as of the time above rather than its current state.",
                JOptionPane.QUESTION_MESSAGE,
                JOptionPane.DEFAULT_OPTION);

        // Loading from cache is the fast, recommended path, so it reads as the
        // PRIMARY orange model action; the full Rhapsody reload supports it.
        // Both are GradientAccentButtons so this prompt matches the rest of the
        // app rather than showing stock JOptionPane buttons.
        GradientAccentButton loadFromCacheButton =
                GradientAccentButton.primary("Load from Cache", AccentColors.ORANGE_HEX);
        GradientAccentButton reloadButton =
                GradientAccentButton.secondary("Reload from Rhapsody", AccentColors.ORANGE_HEX);

        pane.setOptions(new Object[]{loadFromCacheButton, reloadButton});
        JDialog dialog = pane.createDialog(view.frame(), "Cache Available");
        dialog.setModal(true);

        final int[] choice = {JOptionPane.CLOSED_OPTION};
        loadFromCacheButton.addActionListener(e -> {
            choice[0] = 0;
            dialog.dispose();
        });
        reloadButton.addActionListener(e -> {
            choice[0] = 1;
            dialog.dispose();
        });

        dialog.setVisible(true);
        return choice[0];
    }

    public void onUpdateModel() {
        String modelPath = view.modelPath();
        if (modelPath.isEmpty()) {
            view.showWarning("Please select a model file.", "Missing Model");
            return;
        }

        // Guard: "Update Model" means update the currently-loaded model.
        // If the user browsed to a different file without loading it first,
        // the path in the field no longer matches what is held in memory.
        // Proceeding silently would switch models entirely — warn instead.
        if (snapshot != null && loadedModelPath != null) {
            String canonicalField = ModelCacheManager.canonicalPath(modelPath);
            String canonicalLoaded = ModelCacheManager.canonicalPath(loadedModelPath);
            if (!canonicalField.equals(canonicalLoaded)) {
                view.showWarning(
                        "The selected path differs from the currently loaded model.\n\n"
                        + "  Loaded:   " + loadedModelPath + "\n"
                        + "  Selected: " + modelPath + "\n\n"
                        + "Use 'Load Model' to load a new model, or restore the original\n"
                        + "path before using 'Update Model'.",
                        "Model Path Mismatch");
                return;
            }
        }

        // Use incremental update whenever a cache exists — works for both warm start
        // (Rhapsody already connected) and cold start (loaded from cache only).
        // loadIncrementalUpdate() handles Rhapsody connection + package scan warm-up
        // internally, and short-circuits entirely when Rhapsody reports the project
        // clean or when no save unit changed.
        File cacheFile = ModelCacheManager.resolveCacheFile(modelPath);

        if (ModelCacheManager.cacheExists(cacheFile)) {
            // Validate the cache before handing it to the incremental updater.
            // An outdated-version or corrupt cache would cause undefined behaviour
            // deep inside loadIncrementalUpdate(); catch it here with a clear message
            // and fall through to a full Rhapsody load instead.
            CacheMetadata metadata = inspectCache(modelPath, cacheFile);
            if (metadata == null) {
                // inspectCache() already told the user why; go straight to Rhapsody.
                doLoadFromRhapsody(modelPath);
                return;
            }
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
                } else if (evalResult == null) {
                    AppLogger.error("Evaluation returned no result");
                    view.setStatus("  Evaluation returned no result");
                    view.showError("Evaluation completed but returned no result.", "Run Failed");
                } else {
                	System.out.println("SCOPE: '" + scopePath + "'");
                    System.out.println("RESULTS: " + evalResult.results().size());
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

    public void onHelp() {
        // Resolve docs/USER_GUIDE.html relative to the running JAR/exe location.
        // When shipped, the exe and docs/ folder sit side by side.
        // When running from Eclipse, the file won't exist — show a friendly message.
        try {
            Path appDir = resolveAppDirectory();
            File helpFile = appDir.resolve("docs").resolve("USER_GUIDE.html").toFile();
            if (helpFile.isFile()) {
                Desktop.getDesktop().browse(helpFile.toURI());
            } else {
                view.showInfo(
                        "Help is available in the shipped version.\n\n"
                        + "Look for docs/USER_GUIDE.html next to the exe.",
                        "Help Not Found");
            }
        } catch (Exception e) {
            AppLogger.warn("Could not open help: " + e.getMessage());
            view.showInfo(
                    "Help is available in the shipped version.\n\n"
                    + "Look for docs/USER_GUIDE.html next to the exe.",
                    "Help Not Found");
        }
    }

    /**
     * Best-effort resolution of the directory that contains the running
     * application (the exe in a shipped build, or the project root in Eclipse).
     */
    private static Path resolveAppDirectory() {
        try {
            URI jarUri = MainFrameController.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI();
            Path jarPath = Paths.get(jarUri);
            // If running from a JAR/exe, jarPath points to the jar file itself;
            // its parent is the application directory.
            // If running from Eclipse class output, it points to target/classes.
            return jarPath.toFile().isFile() ? jarPath.getParent() : jarPath;
        } catch (Exception e) {
            // Fallback: current working directory
            return Paths.get("").toAbsolutePath();
        }
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
                && ModelCacheManager.cacheExists(ModelCacheManager.resolveCacheFile(modelPath));
    }

    /**
     * True when a model is loaded AND the field path matches what was loaded.
     * Used to enable "Update Model" — updating is only meaningful for the model
     * currently in memory; a path mismatch means the user browsed elsewhere.
     */
    public boolean isUpdateModelEnabled(String fieldPath) {
        if (!isModelLoaded()) return false;
        if (fieldPath == null || fieldPath.isEmpty()) return false;
        if (loadedModelPath == null) return false;
        return ModelCacheManager.canonicalPath(fieldPath)
                .equals(ModelCacheManager.canonicalPath(loadedModelPath));
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
                    // Offline path: no Rhapsody contact. Passing modelPath still lets
                    // the reader reject a cache that belongs to a different project.
                    loadResult = ModelLoadService.loadFromCache(
                            cacheFile, modelPath, CacheStatus.OFFLINE_CACHE_UNVERIFIED);
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
        loadedModelPath = modelPath;
        view.loadTree(result.packageTree());
        view.setStatus("  " + result.statusMessage());
        recentFiles.addRecentModel(modelPath);
        view.refreshRecentModels(recentFiles.recentModels());

        AppLogger.info("Snapshot provenance: " + result.cacheStatus()
                + " (" + result.cacheStatus().describe() + ")");

        // A partial snapshot silently retains cached data that may no longer exist
        // in the model. That is a correctness caveat, so say it out loud once.
        if (result.cacheStatus().isPartial()) {
            view.showWarning(
                    "The model scan was incomplete, so some cached data was kept.\n\n"
                    + "Relation- and requirement-based results may reflect the previous\n"
                    + "snapshot rather than the current model. Reload from Rhapsody for\n"
                    + "an authoritative result.",
                    "Incomplete Model Scan");
        }
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
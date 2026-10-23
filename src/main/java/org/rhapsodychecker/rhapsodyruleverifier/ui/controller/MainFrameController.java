// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/ui/controller/MainFrameController.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.controller;

import org.rhapsodychecker.rhapsodyruleverifier.core.AppLogger;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.service.NavigationService;
import org.rhapsodychecker.rhapsodyruleverifier.prefs.RecentFilesStore;

import javax.swing.*;
import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Thin coordinator that delegates to focused sub-controllers.
 * <p>
 * Owns no application state directly — all state lives in the sub-controllers:
 * <ul>
 *   <li>{@link ModelLoadController} — model loading, cache, incremental update</li>
 *   <li>{@link EvaluationController} — rule evaluation, fix plan building, export, wizard</li>
 *   <li>{@link FixController} — fix preview, application, history</li>
 * </ul>
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

    // ── Sub-controllers ─────────────────────────────────────────────────────
    private final View view;
    private final RecentFilesStore recentFiles = new RecentFilesStore();
    private final ModelLoadController modelCtrl;
    private final EvaluationController evalCtrl;
    private final FixController fixCtrl;

    public MainFrameController(View view, ProgressReporter progressReporter) {
        this.view = view;
        this.modelCtrl = new ModelLoadController(view, progressReporter, recentFiles,
                this::onModelChanged);
        this.evalCtrl = new EvaluationController(view, progressReporter, modelCtrl, recentFiles);
        this.fixCtrl = new FixController(view, modelCtrl, evalCtrl);
    }

    // ── Initialization ──────────────────────────────────────────────────────

    public void init() {
        view.refreshRecentModels(recentFiles.recentModels());
        view.refreshRecentConfigs(recentFiles.recentConfigs());
        view.updateButtonStates();
    }

    // ── Delegated action handlers ───────────────────────────────────────────

    public void onLoadModel()           { modelCtrl.onLoadModel(); }
    public void onUpdateModel()         { modelCtrl.onUpdateModel(); }
    public void onRunEvaluation()       { evalCtrl.onRunEvaluation(); }
    public void onExportExcel()         { evalCtrl.onExportExcel(); }
    public void onApplySuggestedFixes() { fixCtrl.onApplySuggestedFixes(); }
    public void onOpenFixHistory()      { fixCtrl.onOpenFixHistory(); }
    public void onWizardNew()           { evalCtrl.onWizardNew(); }
    public void onWizardEdit()          { evalCtrl.onWizardEdit(); }

    // ── Lightweight actions (kept here — single-line utilities) ─────────────

    public void onNavigateToElement(String guid) {
        String msg = NavigationService.navigateToElement(
                guid, modelCtrl.snapshot(), modelCtrl.isLoadedFromCache());
        if (msg != null) view.setStatus(msg);
    }

    public void onOpenCacheFolder() {
        String msg = NavigationService.openCacheFolder(view.modelPath());
        if (msg != null) view.setStatus(msg);
    }

    public void onHelp() {
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

    // ── Button state queries ────────────────────────────────────────────────

    public boolean isModelLoaded()      { return modelCtrl.isModelLoaded(); }
    public boolean hasResults()         { return evalCtrl.hasResults(); }
    public boolean hasConfig()          { return evalCtrl.hasConfig(); }
    public boolean hasFixPlan()         { return evalCtrl.hasFixPlan(); }
    public boolean isLoadedFromCache()  { return modelCtrl.isLoadedFromCache(); }
    public boolean isFixEligible()      { return modelCtrl.isFixEligible(); }
    public boolean hasFixHistory()      { return fixCtrl.hasFixHistory(); }

    public boolean isCacheAvailable(String modelPath) {
        return modelCtrl.isCacheAvailable(modelPath);
    }

    public boolean isUpdateModelEnabled(String fieldPath) {
        return modelCtrl.isUpdateModelEnabled(fieldPath);
    }

    // ── Internal callbacks ──────────────────────────────────────────────────

    /**
     * Called by ModelLoadController after a new model is successfully loaded.
     * Clears stale evaluation and fix state that belonged to the previous model.
     */
    private void onModelChanged() {
        evalCtrl.clearState();
        fixCtrl.clearState();
    }

    private static Path resolveAppDirectory() {
        try {
            URI jarUri = MainFrameController.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI();
            Path jarPath = Paths.get(jarUri);
            return jarPath.toFile().isFile() ? jarPath.getParent() : jarPath;
        } catch (Exception e) {
            return Paths.get("").toAbsolutePath();
        }
    }
}
package org.rhapsodychecker.rhapsodyruleverifier.ui.controller;

import org.rhapsodychecker.rhapsodyruleverifier.core.AppLogger;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyAliasResolver;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyEvaluationContext;
import org.rhapsodychecker.rhapsodyruleverifier.config.ConfigLoader;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.ConfigToWizardStateMapper;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.EvaluationContext;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.Rule;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleFactory;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleStatus;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;
import org.rhapsodychecker.rhapsodyruleverifier.core.service.EvaluationService;
import org.rhapsodychecker.rhapsodyruleverifier.core.service.ExportService;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixCollector;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixPlan;
import org.rhapsodychecker.rhapsodyruleverifier.prefs.RecentFilesStore;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.WizardDialog;

import javax.swing.*;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages evaluation state and logic: running rules, building fix plans,
 * exporting results, and wizard interactions.
 */
final class EvaluationController {

    private final MainFrameController.View view;
    private final ProgressReporter progressReporter;
    private final ModelLoadController modelCtrl;
    private final RecentFilesStore recentFiles;

    private RuleCheckerConfig config;
    private List<RuleResult>  lastResults;
    private EvaluationContext lastContext;
    private FixPlan           fixPlan;

    EvaluationController(MainFrameController.View view,
                         ProgressReporter progressReporter,
                         ModelLoadController modelCtrl,
                         RecentFilesStore recentFiles) {
        this.view = view;
        this.progressReporter = progressReporter;
        this.modelCtrl = modelCtrl;
        this.recentFiles = recentFiles;
    }

    // ── Getters ─────────────────────────────────────────────────────────────

    RuleCheckerConfig config() { return config; }
    List<RuleResult> lastResults() { return lastResults; }
    EvaluationContext lastContext() { return lastContext; }
    FixPlan fixPlan() { return fixPlan; }

    boolean hasResults() { return lastResults != null && !lastResults.isEmpty(); }
    boolean hasConfig() { return config != null; }
    boolean hasFixPlan() { return fixPlan != null && !fixPlan.entries().isEmpty(); }

    /** Called by the parent controller when a new model is loaded. */
    void clearState() {
        fixPlan = null;
        lastResults = null;
        lastContext = null;
        // config is kept — it's independent of the model
    }

    // ── Actions ─────────────────────────────────────────────────────────────

    void onRunEvaluation() {
        if (!modelCtrl.isModelLoaded()) {
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

        // Holder for the context created inside the factory callback
        final EvaluationContext[] contextHolder = { null };

        new BackgroundTask<EvaluationService.EvalResult>(view, () -> {
            EvaluationService.ContextFactory contextFactory = (cfg, idx) -> {
                RhapsodyAliasResolver aliasResolver =
                        new RhapsodyAliasResolver(modelCtrl.snapshot());
                ElementSelector selector = new ElementSelector(idx, cfg);
                RhapsodyEvaluationContext context = new RhapsodyEvaluationContext(
                        aliasResolver, modelCtrl.snapshot(), idx, selector);
                contextHolder[0] = context;
                return new EvaluationService.ContextPair(context, selector);
            };
            return EvaluationService.evaluate(
                    configPath, contextFactory, modelCtrl.index(), scopePath,
                    modelCtrl.isLoadedFromCache(), progressReporter);
        })
                .errorLogPrefix("Evaluation failed")
                .onSuccess(evalResult -> {
                    if (evalResult == null) {
                        AppLogger.error("Evaluation returned no result");
                        view.setStatus("  Evaluation returned no result");
                        view.showError("Evaluation completed but returned no result.", "Run Failed");
                        return;
                    }
                    config = evalResult.config();
                    lastContext = contextHolder[0];
                    lastResults = evalResult.results();
                    view.loadResults(lastResults, modelCtrl.index(), config.rules());
                    view.setStatus("  " + evalResult.statusMessage());
                    recentFiles.addRecentConfig(configPath);
                    view.refreshRecentConfigs(recentFiles.recentConfigs());
                    buildFixPlan(configPath);
                })
                .onError(e -> {
                    view.setStatus("  Evaluation error");
                    view.showError("Error: " + e.getMessage(), "Run Failed");
                })
                .execute();
    }

    void onExportExcel() {
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
                ExportService.exportToExcel(lastResults, modelCtrl.index(), path);
                view.setStatus("  Exported to: " + path);
                view.showInfo("Report exported successfully!", "Export Done");
            } catch (Throwable t) {
                view.showError("Export error: " + t.getMessage(), "Export Failed");
            }
        }
    }

    void onWizardNew() {
        WizardDialog dialog = new WizardDialog(
                view.frame(), modelCtrl.fastDetectionResult(), null);
        dialog.setVisible(true);
        dialog.getSavedConfigPath().ifPresent(this::reloadConfigFromWizard);
    }

    void onWizardEdit() {
        if (config == null) {
            view.showWarning("Load a config file first.", "No Config");
            return;
        }
        if (modelCtrl.fastDetectionResult() == null) {
            view.showInfo(
                    "No model loaded. Stereotypes and types will not be suggested.\n"
                    + "Load a model first for the best experience.",
                    "No Model Loaded");
        }

        ConfigToWizardStateMapper.MappingResult mapped =
                ConfigToWizardStateMapper.map(
                        config, modelCtrl.fastDetectionResult(), view.configPath());

        if (mapped.hasWarnings()) {
            String warningText = String.join("\n", mapped.warnings());
            int choice = JOptionPane.showConfirmDialog(view.frame(),
                    "Some elements from the config were not detected "
                    + "in the current model:\n\n" + warningText + "\n\nOpen wizard anyway?",
                    "Config Warnings", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
        }

        WizardDialog dialog = new WizardDialog(
                view.frame(), modelCtrl.fastDetectionResult(), mapped.wizardState());
        dialog.setVisible(true);
        dialog.getSavedConfigPath().ifPresent(this::reloadConfigFromWizard);
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private void buildFixPlan(String configPath) {
        if (lastResults == null || config == null || modelCtrl.index() == null) {
            fixPlan = null;
            return;
        }

        try {
            Map<String, Rule> ruleMap = new LinkedHashMap<String, Rule>();
            for (RuleSpec spec : config.enabledRules()) {
                try {
                    Rule rule = RuleFactory.createRule(spec);
                    ruleMap.put(rule.id(), rule);
                } catch (Throwable ignored) {
                    // skip rules that fail to create
                }
            }

            List<RuleResult> failedResults = new ArrayList<RuleResult>();
            for (RuleResult r : lastResults) {
                if (r.status() == RuleStatus.FAIL) {
                    failedResults.add(r);
                }
            }

            EvaluationContext stubContext = lastContext;
            String modelGuid = modelCtrl.snapshot() != null ? "model" : "unknown";
            fixPlan = new FixCollector().collect(
                    failedResults, ruleMap, modelCtrl.index(),
                    stubContext, modelGuid, configPath);

            AppLogger.info("Fix plan: " + fixPlan.entries().size() + " suggestions collected");
        } catch (Throwable t) {
            AppLogger.warn("Failed to build fix plan: " + t.getMessage());
            fixPlan = null;
        }

        view.updateButtonStates();
    }

    private void reloadConfigFromWizard(String path) {
        view.setConfigPath(path);
        try {
            config = ConfigLoader.load(Paths.get(path));
            view.setStatus("  Config loaded from wizard: " + path);
            view.updateButtonStates();
        } catch (Exception e) {
            view.setStatus("  Error reloading saved config");
        }
    }
}
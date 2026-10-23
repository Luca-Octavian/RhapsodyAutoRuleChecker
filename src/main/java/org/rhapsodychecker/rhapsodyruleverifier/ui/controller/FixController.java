package org.rhapsodychecker.rhapsodyruleverifier.ui.controller;

import org.rhapsodychecker.rhapsodyruleverifier.core.AppLogger;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyFixExecutor;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixAction;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixEntry;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixPlan;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixPlanJournal;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixService;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixStatus;
import org.rhapsodychecker.rhapsodyruleverifier.ui.fix.FixHistoryDialog;
import org.rhapsodychecker.rhapsodyruleverifier.ui.fix.FixPreviewDialog;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Manages fix application state and logic: preview dialog, index patching,
 * fix history, and FixService lifecycle.
 */
final class FixController {

    private final MainFrameController.View view;
    private final ModelLoadController modelCtrl;
    private final EvaluationController evalCtrl;

    private FixService fixService;

    FixController(MainFrameController.View view,
                  ModelLoadController modelCtrl,
                  EvaluationController evalCtrl) {
        this.view = view;
        this.modelCtrl = modelCtrl;
        this.evalCtrl = evalCtrl;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    boolean hasFixHistory() {
        File journalDir = new File(new File(System.getProperty("java.io.tmpdir"),
                ".rhapsody-logs"), "fix-journals");
        if (!journalDir.isDirectory()) return false;
        String[] files = journalDir.list();
        return files != null && files.length > 0;
    }

    /** Called by the parent controller when a new model is loaded. */
    void clearState() {
        fixService = null;
    }

    // ── Actions ─────────────────────────────────────────────────────────────

    void onApplySuggestedFixes() {
        FixPlan fixPlan = evalCtrl.fixPlan();
        if (fixPlan == null || fixPlan.entries().isEmpty()) {
            view.showWarning("No fix suggestions available. Run evaluation first.", "No Fixes");
            return;
        }
        if (modelCtrl.isLoadedFromCache()) {
            view.showWarning(
                    "Fixes cannot be applied when the model was loaded from cache.\n"
                    + "Load the model from Rhapsody first.",
                    "Offline Mode");
            return;
        }

        if (fixService == null) {
            RhapsodyFixExecutor executor = new RhapsodyFixExecutor(
                    RhapsodyConnectionManager.getInstance().getProject());
            fixService = new FixService(executor, new FixPlanJournal(), modelCtrl.index());
        }

        FixPreviewDialog dialog = new FixPreviewDialog(
                view.frame(), fixPlan, fixService);
        dialog.setVisible(true);

        if (dialog.wasApplied()) {
            patchIndexAfterFixes(fixPlan);
            view.setStatus("  Fixes applied \u2014 remember to save in Rhapsody if satisfied");
        }
    }

    void onOpenFixHistory() {
        FixService svc = fixService;
        if (svc == null) {
            try {
                RhapsodyFixExecutor executor = new RhapsodyFixExecutor(
                        RhapsodyConnectionManager.getInstance().getProject());
                svc = new FixService(executor, new FixPlanJournal(), modelCtrl.index());
            } catch (Exception e) {
                svc = new FixService(null, new FixPlanJournal(), modelCtrl.index());
            }
        }
        FixHistoryDialog dialog = new FixHistoryDialog(view.frame(), svc);
        dialog.setVisible(true);
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private void patchIndexAfterFixes(FixPlan plan) {
        if (modelCtrl.index() == null || plan == null) return;
        for (FixEntry entry : plan.entries()) {
            if (entry.status() != FixStatus.APPLIED) continue;
            FixAction action = entry.action();
            Optional<ElementRecord> opt =
                    modelCtrl.index().repository().get(action.elementGuid());
            if (!opt.isPresent()) continue;

            ElementRecord record = opt.get();
            ElementRecord.Builder b = record.toBuilder();

            switch (action.actionType()) {
                case SET_NAME:
                    b.name(action.newValue());
                    break;
                case SET_DESCRIPTION:
                    b.description(action.newValue());
                    break;
                case SET_TAG_VALUE:
                    Map<String, String> tags = new LinkedHashMap<>(record.tagValues());
                    tags.put(action.field(), action.newValue());
                    b.tagValues(tags);
                    break;
                case ADD_STEREOTYPE:
                    Set<String> added = new LinkedHashSet<>(record.stereotypes());
                    added.add(action.newValue());
                    b.stereotypes(added);
                    break;
                case REMOVE_STEREOTYPE:
                    String stereo = action.oldValue() != null ? action.oldValue() : action.newValue();
                    Set<String> removed = new LinkedHashSet<>(record.stereotypes());
                    removed.remove(stereo);
                    b.stereotypes(removed);
                    break;
                case SET_INITIAL_VALUE:
                    b.initialValue(action.newValue());
                    break;
                default:
                    continue;
            }

            modelCtrl.index().repository().replace(b.build());
        }
        AppLogger.info("In-memory index patched with "
                + plan.countByStatus(FixStatus.APPLIED) + " applied fixes");
    }
}
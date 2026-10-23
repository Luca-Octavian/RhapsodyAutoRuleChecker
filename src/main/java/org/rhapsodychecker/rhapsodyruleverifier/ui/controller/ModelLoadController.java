package org.rhapsodychecker.rhapsodyruleverifier.ui.controller;

import org.rhapsodychecker.rhapsodyruleverifier.core.AppLogger;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.cache.CacheMetadata;
import org.rhapsodychecker.rhapsodyruleverifier.cache.CacheReadException;
import org.rhapsodychecker.rhapsodyruleverifier.cache.CacheStatus;
import org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCacheManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;
import org.rhapsodychecker.rhapsodyruleverifier.core.service.ModelLoadService;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;
import org.rhapsodychecker.rhapsodyruleverifier.prefs.RecentFilesStore;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AccentColors;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.GradientAccentButton;

import javax.swing.*;
import java.io.File;

/**
 * Manages model loading state and logic: full Rhapsody load, cache load,
 * incremental update, and cache validation.
 */
final class ModelLoadController {

    private final MainFrameController.View view;
    private final ProgressReporter progressReporter;
    private final RecentFilesStore recentFiles;
    private final Runnable onModelChanged;

    private RhapsodyModelSnapshot snapshot;
    private ElementIndex          index;
    private FastDetectionResult   fastDetectionResult;
    private CacheStatus           cacheStatus = null;
    private String                loadedModelPath = null;

    ModelLoadController(MainFrameController.View view,
                        ProgressReporter progressReporter,
                        RecentFilesStore recentFiles,
                        Runnable onModelChanged) {
        this.view = view;
        this.progressReporter = progressReporter;
        this.recentFiles = recentFiles;
        this.onModelChanged = onModelChanged;
    }

    // ── Getters ─────────────────────────────────────────────────────────────

    RhapsodyModelSnapshot snapshot() { return snapshot; }
    ElementIndex index() { return index; }
    FastDetectionResult fastDetectionResult() { return fastDetectionResult; }
    /**
     * Returns the full CacheStatus provenance of the current snapshot,
     * or {@code null} if no model is loaded.
     */
    CacheStatus cacheStatus() { return cacheStatus; }

    /**
     * True when the snapshot originated from cache without live verification.
     * This is used for display purposes (e.g. status bar messages).
     */
    boolean isLoadedFromCache() {
        return cacheStatus != null && cacheStatus.isFromCache() && !cacheStatus.isFreshnessVerified();
    }

    /**
     * True when fixes may safely be applied against the current snapshot.
     * Requires a fully verified, non-partial live snapshot.
     */
    boolean isFixEligible() {
        return cacheStatus != null
                && cacheStatus.isFreshnessVerified()
                && !cacheStatus.isPartial();
    }
    String loadedModelPath() { return loadedModelPath; }

    boolean isModelLoaded() { return snapshot != null && index != null; }

    boolean isCacheAvailable(String modelPath) {
        return !modelPath.isEmpty()
                && ModelCacheManager.cacheExists(ModelCacheManager.resolveCacheFile(modelPath));
    }

    boolean isUpdateModelEnabled(String fieldPath) {
        if (!isModelLoaded()) return false;
        if (fieldPath == null || fieldPath.isEmpty()) return false;
        if (loadedModelPath == null) return false;
        return ModelCacheManager.canonicalPath(fieldPath)
                .equals(ModelCacheManager.canonicalPath(loadedModelPath));
    }

    // ── Actions ─────────────────────────────────────────────────────────────

    void onLoadModel() {
        String modelPath = view.modelPath();
        if (modelPath.isEmpty()) {
            view.showWarning("Please select a model file.", "Missing Model");
            return;
        }

        File cacheFile = ModelCacheManager.resolveCacheFile(modelPath);
        if (ModelCacheManager.cacheExists(cacheFile)) {
            CacheMetadata metadata = inspectCache(modelPath, cacheFile);
            if (metadata == null) {
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

    void onUpdateModel() {
        String modelPath = view.modelPath();
        if (modelPath.isEmpty()) {
            view.showWarning("Please select a model file.", "Missing Model");
            return;
        }

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

        File cacheFile = ModelCacheManager.resolveCacheFile(modelPath);

        if (ModelCacheManager.cacheExists(cacheFile)) {
            CacheMetadata metadata = inspectCache(modelPath, cacheFile);
            if (metadata == null) {
                doLoadFromRhapsody(modelPath);
                return;
            }
            doIncrementalUpdate(modelPath, cacheFile);
        } else {
            doLoadFromRhapsody(modelPath);
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────

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

    private void doLoadFromCache(String modelPath, File cacheFile) {
        view.setBusy(true);
        view.setStatus("  Loading model from cache...");
        view.clearTree();
        view.clearResults();

        new BackgroundTask<ModelLoadService.LoadResult>(view,
                () -> ModelLoadService.loadFromCache(
                        cacheFile, modelPath, CacheStatus.OFFLINE_CACHE_UNVERIFIED))
                .errorLogPrefix("Cache load failed")
                .onSuccess(result -> applyLoadResult(result, modelPath))
                .onError(e -> {
                    cacheStatus = null;
                    view.setStatus("  Error loading cache");
                    view.showError("Cache load failed: " + e.getMessage()
                            + "\n\nTry reloading from Rhapsody.", "Cache Error");
                })
                .execute();
    }

    private void doIncrementalUpdate(String modelPath, File cacheFile) {
        view.setBusy(true);
        view.setStatus("  Smart Update: scanning for changes...");
        view.clearResults();

        new BackgroundTask<ModelLoadService.LoadResult>(view,
                () -> ModelLoadService.loadIncrementalUpdate(
                        modelPath, cacheFile, progressReporter))
                .errorLogPrefix("Incremental update failed")
                .onSuccess(result -> applyLoadResult(result, modelPath))
                .onError(e -> {
                    view.setStatus("  Smart Update failed, falling back to full reload");
                    doLoadFromRhapsody(modelPath);
                })
                .execute();
    }

    private void doLoadFromRhapsody(String modelPath) {
        view.setBusy(true);
        view.setStatus("  Loading model from Rhapsody...");
        view.clearTree();
        view.clearResults();

        new BackgroundTask<ModelLoadService.LoadResult>(view,
                () -> ModelLoadService.loadFromRhapsody(modelPath, progressReporter))
                .errorLogPrefix("Model load from Rhapsody failed")
                .onSuccess(result -> applyLoadResult(result, modelPath))
                .onError(e -> {
                    view.setStatus("  Error loading model");
                    view.showError("Error: " + e.getMessage(), "Load Failed");
                })
                .execute();
    }

    private void applyLoadResult(ModelLoadService.LoadResult result, String modelPath) {
        snapshot = result.snapshot();
        index = result.index();
        fastDetectionResult = result.detectionResult();
        cacheStatus = result.cacheStatus();
        loadedModelPath = modelPath;

        // Notify parent to clear stale evaluation/fix state
        onModelChanged.run();

        view.loadTree(result.packageTree());
        view.setStatus("  " + result.statusMessage());
        recentFiles.addRecentModel(modelPath);
        view.refreshRecentModels(recentFiles.recentModels());

        AppLogger.info("Snapshot provenance: " + result.cacheStatus()
                + " (" + result.cacheStatus().describe() + ")");

        if (result.cacheStatus().isPartial()) {
            view.showWarning(
                    "The model scan was incomplete, so some cached data was kept.\n\n"
                    + "Relation- and requirement-based results may reflect the previous\n"
                    + "snapshot rather than the current model. Reload from Rhapsody for\n"
                    + "an authoritative result.",
                    "Incomplete Model Scan");
        }
    }
}
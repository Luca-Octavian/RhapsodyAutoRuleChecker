// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/LoadPipelineBenchmark.java
package org.rhapsodychecker.rhapsodyruleverifier;

import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.cache.CacheMetadata;
import org.rhapsodychecker.rhapsodyruleverifier.cache.ModelCacheManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.profiler.PhaseTimer;
import org.rhapsodychecker.rhapsodyruleverifier.core.profiler.PipelineProfiler;
import org.rhapsodychecker.rhapsodyruleverifier.core.service.ModelLoadService;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;
import org.rhapsodychecker.rhapsodyruleverifier.ui.PackageNode;

import java.io.File;
import java.io.FilenameFilter;
import java.util.List;

/**
 * Standalone benchmark for the cache-load pipeline.
 * No Rhapsody/COM needed — works purely from a JSON cache file.
 *
 * <p>Usage:
 * <pre>
 *   java -cp "target/classes;target/dependency/*" org.rhapsodychecker.rhapsodyruleverifier.LoadPipelineBenchmark [cache-file]
 * </pre>
 * If no cache file is specified, auto-discovers one from the default temp dir.
 *
 * <p>Runs the load pipeline 3 times: 1 warm-up + 2 measured runs with
 * fine-grained phase profiling. Also runs individual sub-phase micro-benchmarks.
 */
public final class LoadPipelineBenchmark {

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  Load Pipeline Benchmark");
        System.out.println("═══════════════════════════════════════════════════════════");

        File cacheFile = resolveCacheFile(args);
        if (cacheFile == null) {
            System.err.println("No cache file found. Provide path as argument or load a model first.");
            System.exit(1);
            return;
        }

        System.out.println("Cache file: " + cacheFile.getAbsolutePath());
        System.out.printf("File size:  %,d KB%n", cacheFile.length() / 1024);
        System.out.println();

        // ── Warm-up run ─────────────────────────────────────────────────────
        System.out.println("── Warm-up run (JIT, class loading) ──");
        try {
            runFullPipeline(cacheFile, "warm-up");
        } catch (Exception e) {
            System.err.println("Warm-up failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
            return;
        }

        // ── Measured runs ───────────────────────────────────────────────────
        for (int i = 1; i <= 2; i++) {
            System.out.println("\n── Measured run " + i + " ──");
            try {
                runFullPipeline(cacheFile, "run-" + i);
            } catch (Exception e) {
                System.err.println("Run " + i + " failed: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // ── Sub-phase micro-benchmarks ──────────────────────────────────────
        System.out.println("\n── Sub-phase micro-benchmarks (3 iterations each) ──");
        try {
            runSubPhaseBenchmarks(cacheFile);
        } catch (Exception e) {
            System.err.println("Sub-phase benchmarks failed: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\nDone.");
    }

    private static void runFullPipeline(File cacheFile, String label) throws Exception {
        PipelineProfiler profiler = new PipelineProfiler("loadFromCache (" + label + ")");
        profiler.startPipeline();

        PhaseTimer tRead = profiler.startPhase("JSON deserialization");
        ModelCacheManager.CacheLoadResult result = ModelCacheManager.readCache(cacheFile);
        RhapsodyModelSnapshot snapshot = result.snapshot();
        CacheMetadata metadata = result.metadata();
        tRead.stop().items(snapshot.records().size());
        profiler.record(tRead);

        PhaseTimer tIndex = profiler.startPhase("ElementIndex.build()");
        ElementIndex index = ElementIndex.build(snapshot.records());
        tIndex.stop().items(snapshot.records().size());
        profiler.record(tIndex);

        PhaseTimer tTree = profiler.startPhase("buildPackageTree");
        PackageNode packageTree = ModelLoadService.buildPackageTreeFromRecords(
                snapshot.records(), metadata.getProjectName());
        tTree.stop();
        profiler.record(tTree);

        PhaseTimer tDetect = profiler.startPhase("buildDetectionFromRecords");
        FastDetectionResult detection = ModelLoadService.buildDetectionFromRecords(snapshot.records());
        tDetect.stop().items(snapshot.records().size());
        profiler.record(tDetect);

        profiler.stopPipeline();
        System.out.println(profiler.summary());
    }

    private static void runSubPhaseBenchmarks(File cacheFile) throws Exception {
        // First, load the raw data once
        ModelCacheManager.CacheLoadResult result = ModelCacheManager.readCache(cacheFile);
        RhapsodyModelSnapshot snapshot = result.snapshot();
        CacheMetadata metadata = result.metadata();
        List<ElementRecord> records = snapshot.records();

        System.out.printf("  Working with %,d elements%n%n", records.size());

        // ── JSON deserialization (includes POJO construction + record conversion)
        benchmarkPhase("JSON deser + record conversion", 3, new Runnable() {
            @Override
            public void run() {
                try {
                    ModelCacheManager.readCache(cacheFile);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, records.size());

        // ── ElementIndex.build()
        benchmarkPhase("ElementIndex.build()", 3, new Runnable() {
            @Override
            public void run() {
                ElementIndex.build(records);
            }
        }, records.size());

        // ── buildPackageTreeFromRecords
        benchmarkPhase("buildPackageTree", 3, new Runnable() {
            @Override
            public void run() {
                ModelLoadService.buildPackageTreeFromRecords(records, metadata.getProjectName());
            }
        }, records.size());

        // ── buildDetectionFromRecords
        benchmarkPhase("buildDetectionFromRecords", 3, new Runnable() {
            @Override
            public void run() {
                ModelLoadService.buildDetectionFromRecords(records);
            }
        }, records.size());

        // ── Full loadFromCache (end-to-end)
        benchmarkPhase("Full loadFromCache (end-to-end)", 3, new Runnable() {
            @Override
            public void run() {
                try {
                    ModelLoadService.loadFromCache(cacheFile);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, records.size());
    }

    private static void benchmarkPhase(String name, int iterations,
                                        Runnable action, int itemCount) {
        double totalMs = 0;
        double minMs = Double.MAX_VALUE;
        double maxMs = 0;

        for (int i = 0; i < iterations; i++) {
            // Suggest GC before each run for consistency (best-effort)
            System.gc();
            try { Thread.sleep(50); } catch (InterruptedException e) { /* ignore */ }

            long start = System.nanoTime();
            action.run();
            double ms = (System.nanoTime() - start) / 1_000_000.0;

            totalMs += ms;
            if (ms < minMs) minMs = ms;
            if (ms > maxMs) maxMs = ms;
        }

        double avgMs = totalMs / iterations;
        double throughput = itemCount > 0 ? itemCount / (avgMs / 1000.0) : 0;

        System.out.printf("  %-38s avg=%7.1f ms  min=%7.1f ms  max=%7.1f ms  (%,.0f items/s)%n",
                name, avgMs, minMs, maxMs, throughput);
    }

    private static File resolveCacheFile(String[] args) {
        if (args.length > 0) {
            File f = new File(args[0]);
            if (f.exists() && f.isFile()) return f;
            System.err.println("Specified file not found: " + args[0]);
        }

        // Auto-discover from default temp dir
        String tempDir = System.getProperty("java.io.tmpdir");
        File cacheDir = new File(tempDir, ".rhapsody-cache");
        if (!cacheDir.exists() || !cacheDir.isDirectory()) {
            System.err.println("Cache directory not found: " + cacheDir.getAbsolutePath());
            return null;
        }

        File[] cacheFiles = cacheDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith("-cache.json");
            }
        });

        if (cacheFiles == null || cacheFiles.length == 0) {
            System.err.println("No cache files found in: " + cacheDir.getAbsolutePath());
            return null;
        }

        // Pick the newest (by last modified)
        File newest = cacheFiles[0];
        for (File f : cacheFiles) {
            if (f.lastModified() > newest.lastModified()) {
                newest = f;
            }
        }

        return newest;
    }
}
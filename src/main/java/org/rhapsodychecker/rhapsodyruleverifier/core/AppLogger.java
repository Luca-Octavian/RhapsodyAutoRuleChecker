// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/AppLogger.java
package org.rhapsodychecker.rhapsodyruleverifier.core;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

/**
 * Application-wide logger using java.util.logging (JUL).
 * Zero dependencies — built into the JDK.
 * Logs to file (all levels) + console (INFO and above).
 * Log files stored in user temp: %TEMP%/.rhapsody-logs/
 */
public final class AppLogger {

    private static final Logger LOGGER = Logger.getLogger("RhapsodyRuleChecker");
    private static FileHandler fileHandler;
    private static long startTime;

    private AppLogger() {}

    /**
     * Initialize logging. Call once at application startup.
     * Creates a timestamped log file in the user's temp directory.
     */
    public static void init() {
        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            File logDir = new File(tempDir, ".rhapsody-logs");
            logDir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String logFile = new File(logDir, "rule-checker-" + timestamp + ".log").getAbsolutePath();

            fileHandler = new FileHandler(logFile, true);
            fileHandler.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format("[%1$tF %1$tT] [%2$s] %3$s%n",
                            record.getMillis(), record.getLevel(), record.getMessage());
                }
            });

            LOGGER.addHandler(fileHandler);
            LOGGER.setLevel(Level.ALL);
            LOGGER.setUseParentHandlers(false);

            // Console handler at INFO level
            ConsoleHandler console = new ConsoleHandler();
            console.setLevel(Level.INFO);
            console.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format("[%s] %s%n", record.getLevel(), record.getMessage());
                }
            });
            LOGGER.addHandler(console);

            LOGGER.info("Logger initialized. Log file: " + logFile);
        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }

    // ── Timer ───────────────────────────────────────────────────────────────

    public static void startTimer() {
        startTime = System.currentTimeMillis();
    }

    public static long elapsed() {
        return System.currentTimeMillis() - startTime;
    }

    // ── Log methods ─────────────────────────────────────────────────────────

    public static void info(String msg)  { LOGGER.info(msg); }
    public static void warn(String msg)  { LOGGER.warning(msg); }
    public static void error(String msg) { LOGGER.severe(msg); }

    public static void error(String msg, Throwable t) {
        LOGGER.log(Level.SEVERE, msg, t);
    }

    public static void debug(String msg) { LOGGER.fine(msg); }

    // ── Structured log helpers ──────────────────────────────────────────────

    public static void logModelLoad(int elementCount, int depOwners,
                                     int refOwners, long durationMs) {
        info("\u2550\u2550\u2550 Model Load Complete \u2550\u2550\u2550");
        info("  Elements loaded:    " + elementCount);
        info("  Dependency owners:  " + depOwners);
        info("  Reference owners:   " + refOwners);
        info("  Duration:           " + formatDuration(durationMs));
    }

    public static void logEvaluation(int totalRules, int totalChecks,
                                      int passed, int failed, int skipped,
                                      long durationMs) {
        info("\u2550\u2550\u2550 Evaluation Complete \u2550\u2550\u2550");
        info("  Rules evaluated:    " + totalRules);
        info("  Total checks:       " + totalChecks);
        info("  Passed:             " + passed);
        info("  Failed:             " + failed);
        info("  Skipped:            " + skipped);
        info("  Duration:           " + formatDuration(durationMs));
    }

    public static void logCacheWrite(String path, int elements, long sizeKb) {
        info("\u2550\u2550\u2550 Cache Written \u2550\u2550\u2550");
        info("  Path:               " + path);
        info("  Elements:           " + elements);
        info("  Size:               " + sizeKb + " KB");
    }

    public static void logCacheLoad(String path, int elements, String cachedAt) {
        info("\u2550\u2550\u2550 Cache Loaded \u2550\u2550\u2550");
        info("  Path:               " + path);
        info("  Elements:           " + elements);
        info("  Cached at:          " + cachedAt);
    }

    public static void logIncrementalUpdate(int changed, int newCount, int removed,
                                             int unchanged, int totalScanned,
                                             long durationMs) {
        info("\u2550\u2550\u2550 Incremental Update Complete \u2550\u2550\u2550");
        info("  Scanned:            " + totalScanned);
        info("  Changed:            " + changed);
        info("  New:                " + newCount);
        info("  Removed:            " + removed);
        info("  Unchanged:          " + unchanged);
        info("  Full reads:         " + (changed + newCount));
        info("  Duration:           " + formatDuration(durationMs));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static String formatDuration(long ms) {
        long secs = ms / 1000;
        long mins = secs / 60;
        if (mins > 0) return String.format("%dm %ds (%dms)", mins, secs % 60, ms);
        return String.format("%ds (%dms)", secs, ms);
    }

    public static void close() {
        if (fileHandler != null) {
            fileHandler.close();
        }
    }
}
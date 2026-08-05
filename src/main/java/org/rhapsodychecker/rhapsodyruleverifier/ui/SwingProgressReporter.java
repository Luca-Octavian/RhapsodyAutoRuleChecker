// ui/SwingProgressReporter.java
package org.rhapsodychecker.rhapsodyruleverifier.ui;

import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;

import javax.swing.*;

/**
 * ProgressReporter implementation that updates Swing components on the EDT.
 * Safe to call from any thread — all updates are marshalled via
 * SwingUtilities.invokeLater.
 *
 * Usage:
 *   SwingProgressReporter reporter = new SwingProgressReporter(progressBar, statusLabel);
 *   // pass reporter to RhapsodyModelLoader / RuleEngine
 */
public final class SwingProgressReporter implements ProgressReporter {

    private final JProgressBar progressBar;
    private final JLabel       statusLabel;  // optional, can be null

    /**
     * @param progressBar  Bar to update (must not be null)
     * @param statusLabel  Label to show step name (can be null)
     */
    public SwingProgressReporter(JProgressBar progressBar, JLabel statusLabel) {
        if (progressBar == null) throw new IllegalArgumentException("progressBar must not be null");
        this.progressBar = progressBar;
        this.statusLabel = statusLabel;
    }

    @Override
    public void onStepStarted(String step) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(true);
            progressBar.setString(step);
            progressBar.setStringPainted(true);
            if (statusLabel != null) statusLabel.setText("  " + step + "...");
        });
    }

    @Override
    public void onProgress(int current, int total) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setMinimum(0);
            progressBar.setMaximum(total);
            progressBar.setValue(current);
            int pct = total > 0 ? (int) ((current * 100L) / total) : 0;
            progressBar.setString(current + " / " + total + "  (" + pct + "%)");
        });
    }

    @Override
    public void onStepCompleted(String step) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setValue(0);
            progressBar.setString(step + " — done");
        });
    }

    @Override
    public void onDone() {
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setValue(progressBar.getMaximum());
            progressBar.setString("Done");
            if (statusLabel != null) statusLabel.setText("  Ready");
        });
    }
}

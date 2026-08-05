package org.rhapsodychecker.rhapsodyruleverifier;

import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelLoader;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.profiler.ComLoadDiagnostics;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;

/**
 * Legacy entry point retained for existing Eclipse launch configurations.
 *
 * <p>The rejected toList experiment has been removed. This now runs the
 * production indexed loader with diagnostics enabled.
 */
public final class CollectionStrategySmokeTest {

    private CollectionStrategySmokeTest() {}

    public static void main(String[] args) throws Exception {
        String modelPath =
                "C:\\Users\\uik11305\\Downloads\\OneDrive_2026-07-13\\Rhapsody Model\\Summer_Practice_Model.rpyx";
        if (args.length > 0) modelPath = args[0];

        RhapsodyConnectionManager connection = RhapsodyConnectionManager.getInstance();
        try {
            connection.connect(modelPath);
            RhapsodyModelLoader loader = new RhapsodyModelLoader(
                    ProgressReporter.NOOP, new ComLoadDiagnostics(true));
            RhapsodyModelSnapshot snapshot = loader.loadModel(connection.getProject());
            System.out.println("Production indexed load complete: "
                    + snapshot.records().size() + " records");
        } finally {
            connection.shutdown();
        }
    }
}
package org.rhapsodychecker.rhapsodyruleverifier.prefs;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.prefs.Preferences;

/**
 * Checks that the Rhapsody Java API path is valid on startup.
 * If not, prompts the user to browse for the correct {@code Share\JavaAPI} folder,
 * updates the {@code .l4j.ini} file, and requests a restart.
 */
public final class RhapsodyPathSetup {

    private static final String PREF_KEY = "rhapsodyJavaApiPath";
    private static final String INI_FILE_NAME = "RhapsodyRuleVerifier.l4j.ini";

    /** Marker file we expect inside a valid JavaAPI folder. */
    private static final String MARKER_FILE = "rhapsody.jar";

    private RhapsodyPathSetup() {}

    /**
     * Call once from {@code main()} before creating the UI.
     * If the current {@code java.library.path} already points to a valid
     * Rhapsody JavaAPI folder the method returns immediately.
     * Otherwise a folder-chooser dialog is shown.
     */
    public static void ensureConfigured() {
        String currentPath = System.getProperty("java.library.path", "");
        if (isValidJavaApiPath(currentPath)) {
            return; // already good
        }

        // Check if we previously saved a path in Preferences
        Preferences prefs = Preferences.userNodeForPackage(RhapsodyPathSetup.class);
        String saved = prefs.get(PREF_KEY, null);
        if (saved != null && isValidJavaApiPath(saved) && saved.equals(currentPath)) {
            return; // ini already matches saved path — shouldn't happen but guard
        }

        // Need to prompt the user
        UIManager.put("FileChooser.readOnly", Boolean.TRUE);

        String chosen = promptForPath(saved);
        if (chosen == null) {
            // User cancelled — exit
            JOptionPane.showMessageDialog(null,
                    "The Rhapsody Java API path is required to run this application.\n"
                            + "The application will now exit.",
                    "Rhapsody Rule Verifier", JOptionPane.WARNING_MESSAGE);
            System.exit(1);
            return;
        }

        // Save to preferences
        prefs.put(PREF_KEY, chosen);

        // Update the ini file
        boolean iniUpdated = updateIniFile(chosen);

        String restartMsg = "Rhapsody Java API path configured:\n" + chosen + "\n\n";
        if (iniUpdated) {
            restartMsg += "The configuration file has been updated.\n"
                    + "Please restart the application for the change to take effect.";
        } else {
            restartMsg += "Could not update " + INI_FILE_NAME + " automatically.\n"
                    + "Please edit it manually and set:\n"
                    + "-Djava.library.path=\"" + chosen + "\"";
        }
        JOptionPane.showMessageDialog(null, restartMsg,
                "Rhapsody Rule Verifier", JOptionPane.INFORMATION_MESSAGE);
        System.exit(0);
    }

    // ── Validation ───────────────────────────────────────────────────────────

    /** Package-private for testing. */
    static boolean isValidJavaApiPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        // java.library.path can contain multiple entries separated by ;
        // We check each segment
        for (String segment : path.split(";")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) continue;
            Path dir = Paths.get(trimmed);
            if (Files.isDirectory(dir) && Files.exists(dir.resolve(MARKER_FILE))) {
                return true;
            }
        }
        return false;
    }

    // ── Folder chooser ───────────────────────────────────────────────────────

    private static String promptForPath(String initialPath) {
        JOptionPane.showMessageDialog(null,
                "The Rhapsody Java API was not found at the configured path.\n\n"
                        + "Please browse to your Rhapsody installation's Share\\JavaAPI folder\n"
                        + "(e.g. C:\\Program Files\\IBM\\Rhapsody\\Share\\JavaAPI).",
                "Rhapsody Rule Verifier — Setup", JOptionPane.INFORMATION_MESSAGE);

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Rhapsody Share\\JavaAPI folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        if (initialPath != null && !initialPath.isEmpty()) {
            File init = new File(initialPath);
            if (init.isDirectory()) {
                chooser.setCurrentDirectory(init);
            }
        }

        while (true) {
            int result = chooser.showOpenDialog(null);
            if (result != JFileChooser.APPROVE_OPTION) {
                return null; // cancelled
            }
            File selected = chooser.getSelectedFile();
            if (selected != null && isValidJavaApiPath(selected.getAbsolutePath())) {
                return selected.getAbsolutePath();
            }
            JOptionPane.showMessageDialog(null,
                    "The selected folder does not appear to be a valid Rhapsody JavaAPI folder.\n"
                            + "It should contain " + MARKER_FILE + ".\n\n"
                            + "Please try again.",
                    "Invalid folder", JOptionPane.WARNING_MESSAGE);
        }
    }

    // ── INI file update ──────────────────────────────────────────────────────

    /** Package-private for testing. */
    static boolean updateIniFile(String newPath) {
        Path iniPath = findIniFile();
        if (iniPath == null) {
            return false;
        }
        try {
            String newLine = "-Djava.library.path=\"" + newPath + "\"";
            // Read existing lines, replace the java.library.path line
            java.util.List<String> lines = Files.readAllLines(iniPath, StandardCharsets.UTF_8);
            boolean replaced = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().startsWith("-Djava.library.path=")) {
                    lines.set(i, newLine);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                lines.add(newLine);
            }
            Files.write(iniPath, lines, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Package-private overload for testing with explicit ini path. */
    static boolean updateIniFile(String newPath, Path iniPath) {
        if (iniPath == null) {
            return false;
        }
        try {
            String newLine = "-Djava.library.path=\"" + newPath + "\"";
            java.util.List<String> lines = Files.readAllLines(iniPath, StandardCharsets.UTF_8);
            boolean replaced = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().startsWith("-Djava.library.path=")) {
                    lines.set(i, newLine);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                lines.add(newLine);
            }
            Files.write(iniPath, lines, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static Path findIniFile() {
        // Try next to the JAR / exe
        try {
            Path jarDir = Paths.get(
                    RhapsodyPathSetup.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).getParent();
            Path candidate = jarDir.resolve(INI_FILE_NAME);
            if (Files.exists(candidate)) {
                return candidate;
            }
            // Also try parent (in case jar is in a lib/ subfolder)
            candidate = jarDir.getParent().resolve(INI_FILE_NAME);
            if (Files.exists(candidate)) {
                return candidate;
            }
        } catch (Exception ignored) {
        }
        // Try current working directory
        Path cwd = Paths.get(INI_FILE_NAME);
        if (Files.exists(cwd)) {
            return cwd;
        }
        return null;
    }
}
package org.rhapsodychecker.rhapsodyruleverifier.core;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.RhapsodyAppServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages a single connection to IBM Rhapsody.
 * First tries to open a project from a given .rpy/.rpyx path; if that fails, falls back to the active Rhapsody instance.
 * Singleton, thread-safe, single-responsibility: creating/holding the IRPApplication and IRPProject handles.
 */
public final class RhapsodyConnectionManager {

    private volatile IRPApplication application;
    private volatile IRPProject project;

    // Track lifecycle to avoid quitting a user-started instance
    private volatile boolean applicationCreatedByManager = false;

    // Which project path (if any) the current application/project handles belong to.
    // Lets connect() tell "same model requested again" (safe no-op) apart from
    // "different model requested while already connected" (must reconnect).
    private volatile String currentProjectPath;

    private RhapsodyConnectionManager() {
    }

    public static RhapsodyConnectionManager getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final RhapsodyConnectionManager INSTANCE = new RhapsodyConnectionManager();
    }

    /**
     * Connect to Rhapsody by opening the given project file; if that fails, attach to an active instance and use its active project.
     * If already connected, this is a no-op.
     *
     * @param projectFilePath absolute path to .rpy/.rpyx (can be null/empty to force attach to active project)
     * @throws RhapsodyConnectionException if neither opening the file nor attaching to an active project succeeds
     */
    public synchronized void connect(String projectFilePath) throws RhapsodyConnectionException {
        String normalizedPath = (projectFilePath == null || projectFilePath.trim().isEmpty())
                ? null : projectFilePath.trim();

        if (isConnected()) {
            if (java.util.Objects.equals(normalizedPath, currentProjectPath)) {
                return; // already connected to this exact project — true no-op
            }
            // A different project was requested: drop the stale handles first,
            // otherwise we'd silently keep serving the previously loaded model.
            disconnectInternal();
        }

        try {
            application = tryGetActiveApplication();
            if (application == null) {
                application = RhapsodyAppServer.createRhapsodyApplication();
                applicationCreatedByManager = true;
            }

            // 1) Try to open the provided project file (if any)
            if (normalizedPath != null) {
                project = tryOpenProject(application, normalizedPath);
            }

            // 2) Fallback: use active project (if open)
            if (project == null) {
                project = application.activeProject();
            }

            if (project == null) {
                throw new RhapsodyConnectionException("Could not open project and no active project is available.");
            }

            currentProjectPath = normalizedPath;
        } catch (UnsatisfiedLinkError e) {
            // Common when Rhapsody native DLLs are not on PATH (…\\Share\\bin, …\\bin)
            cleanupOnFailure();
            throw new RhapsodyConnectionException("Failed to load Rhapsody native libraries. Check PATH to Rhapsody \\Share\\bin and \\bin.", e);
        } catch (Throwable t) {
            cleanupOnFailure();
            throw new RhapsodyConnectionException("Failed to connect to Rhapsody: " + t.getMessage(), t);
        }
    }

    /**
     * Drops the current application/project handles before reconnecting to a
     * different project. Quits the Rhapsody instance only if this manager
     * created it; an externally attached instance is left running (its
     * currently open project just stops being tracked here).
     */
    private void disconnectInternal() {
        if (applicationCreatedByManager && application != null) {
            try {
                application.quit();
            } catch (Throwable ignored) {
                // Best-effort; ignore
            }
        }
        application = null;
        project = null;
        applicationCreatedByManager = false;
        currentProjectPath = null;
    }

    /**
     * @return true if both application and project are available and the COM
     *         handles are still alive (i.e. Rhapsody has not been closed).
     */
    public boolean isConnected() {
        if (application == null || project == null) return false;
        // Validate that the cached COM handles are still alive.
        // If Rhapsody was closed/restarted, any COM call will throw.
        try {
            project.getName();
            return true;
        } catch (Throwable t) {
            // Stale handles — clear them so the next connect() does a full reconnect
            application = null;
            project = null;
            applicationCreatedByManager = false;
            currentProjectPath = null;
            return false;
        }
    }

    public IRPApplication getApplication() {
        ensureConnected();
        return application;
    }

    public IRPProject getProject() {
        ensureConnected();
        return project;
    }

    /**
     * Gracefully shuts down the application only if this manager created it.
     * If attached to a user-started instance, it will not quit it.
     */
    public synchronized void shutdown() {
        if (application != null) {
            try {
                // If we opened a project, Rhapsody will close it when quitting.
                if (applicationCreatedByManager) {
                    try {
                        // IRPApplication typically provides quit(); if not available in your version, remove this call.
                        application.quit();
                    } catch (Throwable ignored) {
                        // Best-effort shutdown; ignore
                    }
                }
            } finally {
                application = null;
                project = null;
                applicationCreatedByManager = false;
                currentProjectPath = null;
            }
        }
    }

    // --- Internal helpers ---

    private IRPApplication tryGetActiveApplication() {
        try {
            return RhapsodyAppServer.getActiveRhapsodyApplication();
        } catch (Throwable t) {
            return null;
        }
    }

    private IRPProject tryOpenProject(IRPApplication app, String pathStr) {
        Path path = Paths.get(pathStr);
        if (!Files.exists(path)) {
            return null;
        }
        IRPProject prj = app.openProject(path.toString());
        if (prj == null) {
            // Some versions might still set activeProject on success
            prj = app.activeProject();
        }
        return prj;
    }

    private void ensureConnected() throws IllegalStateException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to Rhapsody. Call connect(...) first.");
        }
    }

    private void cleanupOnFailure() {
        // Do not quit an external instance on failure; only clear fields if we created one
        application = null;
        project = null;
        applicationCreatedByManager = false;
        currentProjectPath = null;
    }

    // Keep exception local to avoid extra files at this stage
    public static class RhapsodyConnectionException extends Exception {
        private static final long serialVersionUID = 1L;

        public RhapsodyConnectionException(String message) {
            super(message);
        }
        public RhapsodyConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
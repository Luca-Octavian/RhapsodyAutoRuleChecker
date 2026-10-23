package org.rhapsodychecker.rhapsodyruleverifier.ui.controller;

import org.rhapsodychecker.rhapsodyruleverifier.core.AppLogger;

import javax.swing.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Reusable background task runner that eliminates duplicated SwingWorker
 * boilerplate across controllers.
 *
 * <p>Handles:
 * <ul>
 *   <li>busy state management (setBusy/resetProgress)</li>
 *   <li>exception capture with full stack trace logging</li>
 *   <li>typed success/failure callbacks on the EDT</li>
 *   <li>guaranteed cleanup in {@code done()} via try/finally</li>
 * </ul>
 *
 * @param <T> the result type produced by the background computation
 */
final class BackgroundTask<T> {

    private final MainFrameController.View view;
    private final Callable<T> computation;
    private Consumer<T> onSuccess;
    private Consumer<Exception> onError;
    private Runnable onFinally;
    private String errorLogPrefix = "Background task failed";

    BackgroundTask(MainFrameController.View view, Callable<T> computation) {
        this.view = view;
        this.computation = computation;
    }

    /** Called on EDT with the result when the computation succeeds. */
    BackgroundTask<T> onSuccess(Consumer<T> handler) {
        this.onSuccess = handler;
        return this;
    }

    /** Called on EDT with the exception when the computation fails. */
    BackgroundTask<T> onError(Consumer<Exception> handler) {
        this.onError = handler;
        return this;
    }

    /** Called on EDT after success or error, always (like finally). */
    BackgroundTask<T> onFinally(Runnable handler) {
        this.onFinally = handler;
        return this;
    }

    /** Prefix for the log message when an error occurs. */
    BackgroundTask<T> errorLogPrefix(String prefix) {
        this.errorLogPrefix = prefix;
        return this;
    }

    /** Starts the background task. */
    void execute() {
        new SwingWorker<T, Void>() {
            private T result;
            private Exception error;

            @Override
            protected T doInBackground() {
                try {
                    result = computation.call();
                } catch (Exception e) {
                    error = e;
                }
                return result;
            }

            @Override
            protected void done() {
                try {
                    view.setBusy(false);
                    view.resetProgress();

                    if (error != null) {
                        AppLogger.error(errorLogPrefix + ": " + error.getMessage(), error);
                        if (onError != null) {
                            onError.accept(error);
                        }
                    } else if (onSuccess != null) {
                        onSuccess.accept(result);
                    }
                } finally {
                    if (onFinally != null) {
                        onFinally.run();
                    }
                    view.updateButtonStates();
                }
            }
        }.execute();
    }
}
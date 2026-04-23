/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.services;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.api.ArchiteezyClient;
import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.auth.ProfileRegistry;
import com.architeezy.archi.connector.auth.ProfileStatus;
import com.architeezy.archi.connector.io.TrackedModelStore;
import com.architeezy.archi.connector.model.ConnectorProperties;
import com.architeezy.archi.connector.model.IEditorModelManagerAdapter;

/**
 * Background service that periodically checks for newer versions of tracked
 * models and notifies registered listeners when the update state changes.
 *
 * Must be started from the UI thread via {@link #start()}.
 */
public final class UpdateCheckService {

    /** Check interval in milliseconds. */
    private static final long CHECK_INTERVAL_MS = 60 * 1000L;

    private final ArchiteezyClient client;

    private final AuthService authService;

    private final ProfileRegistry profileRegistry;

    private final TrackedModelStore trackedModels;

    private final IEditorModelManagerAdapter editorModelManager;

    private final Map<String, RemoteModel> pendingUpdates = new ConcurrentHashMap<>();

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    @SuppressWarnings("java:S3077")
    private volatile Job checkJob;

    /**
     * Creates an update checker that polls via {@code client} and resolves
     * tokens through {@code authService} / {@code profileRegistry}.
     *
     * @param client HTTP client
     * @param authService provider of valid OAuth access tokens
     * @param profileRegistry registry used to find a profile per server URL
     * @param trackedModels workspace metadata for last-seen modification dates
     * @param editorModelManager editor-model manager adapter
     */
    public UpdateCheckService(ArchiteezyClient client, AuthService authService,
            ProfileRegistry profileRegistry, TrackedModelStore trackedModels,
            IEditorModelManagerAdapter editorModelManager) {
        this.client = client;
        this.authService = authService;
        this.profileRegistry = profileRegistry;
        this.trackedModels = trackedModels;
        this.editorModelManager = editorModelManager;
    }

    // -----------------------------------------------------------------------
    // Lifecycle

    /** Starts the periodic update check. */
    public void start() {
        scheduleCheck(0);
    }

    /** Cancels the periodic update check and clears all state. */
    public void stop() {
        var job = checkJob;
        if (job != null) {
            job.cancel();
            checkJob = null;
        }
        pendingUpdates.clear();
    }

    // -----------------------------------------------------------------------
    // State queries

    /**
     * Returns {@code true} if the server has a newer version of the given model.
     *
     * @param model the model to check
     * @return true if an update is available
     */
    public boolean hasUpdate(IArchimateModel model) {
        var url = ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL);
        return url != null && pendingUpdates.containsKey(url);
    }

    /**
     * Returns the remote model metadata for an available update, or {@code null} if
     * no update is known.
     *
     * @param model the model to check
     * @return remote metadata, or null
     */
    public RemoteModel getAvailableUpdate(IArchimateModel model) {
        var url = ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL);
        return url == null ? null : pendingUpdates.get(url);
    }

    /**
     * Clears the pending-update marker for the given model (called after a
     * successful pull).
     *
     * @param model the model whose update marker should be removed
     */
    public void clearUpdate(IArchimateModel model) {
        var url = ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL);
        if (url != null && pendingUpdates.remove(url) != null) {
            notifyListeners();
        }
    }

    // -----------------------------------------------------------------------
    // Listeners

    /**
     * Registers a listener that is invoked on the calling thread whenever the
     * set of models with available updates changes.
     *
     * @param listener the listener to register
     */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /** Schedules an immediate update check (in addition to the periodic one). */
    public void triggerCheck() {
        scheduleCheck(0);
    }

    // -----------------------------------------------------------------------
    // Background check

    private void scheduleCheck(long delayMs) {
        var job = Job.create("Checking Architeezy model updates", this::runCheck); //$NON-NLS-1$
        job.setSystem(true);
        job.schedule(delayMs);
        checkJob = job;
    }

    private IStatus runCheck(IProgressMonitor monitor) {
        try {
            checkAllTrackedModels(monitor);
        } catch (Exception e) {
            Platform.getLog(UpdateCheckService.class).warn("Architeezy update check failed", e); //$NON-NLS-1$
        } finally {
            if (!monitor.isCanceled()) {
                scheduleCheck(CHECK_INTERVAL_MS);
            }
        }
        return Status.OK_STATUS;
    }

    private void checkAllTrackedModels(IProgressMonitor monitor) {
        var models = editorModelManager.getModels();
        if (models == null || models.isEmpty()) {
            return;
        }
        var changed = false;
        for (var model : List.copyOf(models)) {
            if (monitor.isCanceled()) {
                break;
            }
            changed |= checkModel(model);
        }
        if (changed) {
            notifyListeners();
        }
    }

    boolean checkModel(IArchimateModel model) {
        var modelUrl = ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL);
        if (modelUrl == null) {
            return false;
        }
        try {
            var serverUrl = ConnectorProperties.extractServerUrl(modelUrl);
            var modelId = ConnectorProperties.extractModelId(modelUrl);
            var token = resolveToken(serverUrl);
            var remote = client.getModel(serverUrl, token, modelId);
            var localDate = trackedModels.getLastModified(modelId);
            if (isNewer(remote.lastModified(), localDate)) {
                var prev = pendingUpdates.put(modelUrl, remote);
                return prev == null;
            } else {
                var prev = pendingUpdates.remove(modelUrl);
                return prev != null;
            }
        } catch (Exception e) {
            Platform.getLog(UpdateCheckService.class)
                    .warn("Failed to check update for " + modelUrl, e); //$NON-NLS-1$
            return false;
        }
    }

    private String resolveToken(String serverUrl) {
        var profile = profileRegistry.findProfileForServer(serverUrl);
        if (profile == null || profile.getStatus() != ProfileStatus.CONNECTED) {
            return null;
        }
        try {
            return authService.getValidAccessToken(profile);
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isNewer(String serverDate, String localDate) {
        if (serverDate == null || serverDate.isBlank()) {
            return false;
        }
        if (localDate == null || localDate.isBlank()) {
            return true;
        }
        return serverDate.compareTo(localDate) > 0;
    }

    private void notifyListeners() {
        for (var listener : listeners) {
            try {
                listener.run();
            } catch (Exception e) {
                Platform.getLog(UpdateCheckService.class)
                        .warn("Update listener threw an exception", e); //$NON-NLS-1$
            }
        }
    }

}

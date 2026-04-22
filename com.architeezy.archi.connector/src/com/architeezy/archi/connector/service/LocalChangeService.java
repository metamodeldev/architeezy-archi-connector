/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.ConnectorPlugin;
import com.architeezy.archi.connector.io.ModelSerializer;
import com.architeezy.archi.connector.io.SnapshotStore;
import com.architeezy.archi.connector.model.ConnectorProperties;

/**
 * Background service that periodically checks whether tracked models have local
 * changes relative to their base snapshot and notifies listeners when the state
 * changes.
 */
public final class LocalChangeService {

    /** The singleton instance. */
    public static final LocalChangeService INSTANCE = new LocalChangeService();

    private static final long CHECK_INTERVAL_MS = 5 * 1000L;

    /** Model URL set for models that have local changes relative to the base snapshot. */
    private final Set<String> modelsWithChanges = ConcurrentHashMap.newKeySet();

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private volatile Job checkJob;

    private LocalChangeService() {
    }

    // -----------------------------------------------------------------------
    // Lifecycle

    /** Starts the periodic local-change check. */
    public void start() {
        scheduleCheck(0);
    }

    /** Cancels the periodic check and clears all tracked state. */
    public void stop() {
        var job = checkJob;
        if (job != null) {
            job.cancel();
            checkJob = null;
        }
        modelsWithChanges.clear();
    }

    // -----------------------------------------------------------------------
    // State queries

    /**
     * Returns {@code true} if the local model has uncommitted changes relative to
     * its base snapshot.
     *
     * @param model the model to check
     * @return true if local changes are present
     */
    public boolean hasLocalChanges(IArchimateModel model) {
        var url = ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL);
        return url != null && modelsWithChanges.contains(url);
    }

    /**
     * Clears the local-change marker for the given model (called after a successful
     * push or pull that resets the base snapshot).
     *
     * @param model the model whose change marker should be removed
     */
    public void clearLocalChanges(IArchimateModel model) {
        var url = ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL);
        if (url != null && modelsWithChanges.remove(url)) {
            notifyListeners();
        }
    }

    // -----------------------------------------------------------------------
    // Listeners

    /**
     * Registers a listener that is invoked whenever the set of locally modified
     * models changes.
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

    /** Schedules an immediate check in addition to the periodic one. */
    public void triggerCheck() {
        scheduleCheck(0);
    }

    // -----------------------------------------------------------------------
    // Background check

    private void scheduleCheck(long delayMs) {
        var job = Job.create("Checking local model changes", this::runCheck); //$NON-NLS-1$
        job.setSystem(true);
        job.schedule(delayMs);
        checkJob = job;
    }

    private IStatus runCheck(IProgressMonitor monitor) {
        try {
            checkAllTrackedModels(monitor);
        } catch (Exception e) {
            ConnectorPlugin.getInstance().getLog().warn("Local change check failed", e); //$NON-NLS-1$
        } finally {
            if (!monitor.isCanceled()) {
                scheduleCheck(CHECK_INTERVAL_MS);
            }
        }
        return Status.OK_STATUS;
    }

    private void checkAllTrackedModels(IProgressMonitor monitor) {
        var models = IEditorModelManager.INSTANCE.getModels();
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

    private boolean checkModel(IArchimateModel model) {
        var modelUrl = ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL);
        if (modelUrl == null) {
            return false;
        }
        var modelId = ConnectorProperties.extractModelId(modelUrl);
        if (!SnapshotStore.INSTANCE.hasSnapshot(modelId)) {
            return false;
        }
        try {
            var base = SnapshotStore.INSTANCE.loadSnapshot(modelId);
            var local = ModelSerializer.INSTANCE.serialize(model);
            var hasChanges = !Arrays.equals(local, base);
            if (hasChanges) {
                return modelsWithChanges.add(modelUrl); // true = state changed (newly detected)
            } else {
                return modelsWithChanges.remove(modelUrl); // true = state changed (cleared)
            }
        } catch (Exception e) {
            ConnectorPlugin.getInstance().getLog()
                    .warn("Failed to check local changes for " + modelUrl, e); //$NON-NLS-1$
            return false;
        }
    }

    private void notifyListeners() {
        for (var listener : listeners) {
            try {
                listener.run();
            } catch (Exception e) {
                ConnectorPlugin.getInstance().getLog()
                        .warn("Local change listener threw an exception", e); //$NON-NLS-1$
            }
        }
    }

}

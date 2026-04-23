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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobFunction;
import org.eclipse.core.runtime.jobs.Job;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.io.ModelSerializer;
import com.architeezy.archi.connector.io.SnapshotStore;
import com.architeezy.archi.connector.model.ConnectorProperties;
import com.architeezy.archi.connector.model.IEditorModelManagerAdapter;

/**
 * Tracks whether tracked models have local changes relative to their base
 * snapshot and notifies listeners when the state changes.
 */
public final class LocalChangeService {

    private final SnapshotStore snapshotStore;

    private final ModelSerializer serializer;

    private final IEditorModelManagerAdapter editorModelManager;

    private final Set<String> savedFilesDiffer = ConcurrentHashMap.newKeySet();

    private final ConcurrentMap<String, Job> pendingRechecks = new ConcurrentHashMap<>();

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private final PropertyChangeListener modelManagerListener = this::onModelManagerEvent;

    /**
     * Creates a service that compares models against their snapshots stored in
     * {@code snapshotStore} using the given {@code serializer}.
     *
     * @param snapshotStore store holding base snapshots for tracked models
     * @param serializer serializer used for local-vs-base comparison
     * @param editorModelManager editor-model manager adapter
     */
    public LocalChangeService(SnapshotStore snapshotStore, ModelSerializer serializer,
            IEditorModelManagerAdapter editorModelManager) {
        this.snapshotStore = snapshotStore;
        this.serializer = serializer;
        this.editorModelManager = editorModelManager;
    }

    // -----------------------------------------------------------------------
    // Lifecycle

    /** Starts listening to model lifecycle and command-stack events. */
    public void start() {
        editorModelManager.addPropertyChangeListener(modelManagerListener);
        scheduleRecheckAll();
    }

    /** Stops listening and clears all tracked state. */
    public void stop() {
        editorModelManager.removePropertyChangeListener(modelManagerListener);
        cancelAllPendingRechecks();
        savedFilesDiffer.clear();
    }

    // -----------------------------------------------------------------------
    // State queries

    /**
     * Returns {@code true} if the model has uncommitted changes relative to
     * its base snapshot.
     *
     * @param model the model to check
     * @return true if local changes are present
     */
    public boolean hasLocalChanges(IArchimateModel model) {
        if (model == null) {
            return false;
        }
        if (editorModelManager.isModelDirty(model)) {
            return true;
        }
        var url = ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL);
        return url != null && savedFilesDiffer.contains(url);
    }

    /**
     * Clears the local-change marker for the given model (called after a
     * successful push or pull that resets the base snapshot).
     *
     * @param model the model whose change marker should be removed
     */
    public void clearLocalChanges(IArchimateModel model) {
        var url = ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL);
        if (url == null) {
            return;
        }
        var pending = pendingRechecks.remove(url);
        if (pending != null) {
            pending.cancel();
        }
        if (savedFilesDiffer.remove(url)) {
            notifyListeners();
        }
    }

    // -----------------------------------------------------------------------
    // Listeners

    /**
     * Registers a listener that is invoked whenever the local-change state may
     * have changed.
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

    // -----------------------------------------------------------------------
    // Event handling

    private void onModelManagerEvent(PropertyChangeEvent evt) {
        var prop = evt.getPropertyName();
        var value = evt.getNewValue();
        if (IEditorModelManager.COMMAND_STACK_CHANGED.equals(prop)) {
            notifyListeners();
        } else if (isRecheckTrigger(prop) && value instanceof IArchimateModel model) {
            scheduleRecheck(model);
        } else if (IEditorModelManager.PROPERTY_MODEL_REMOVED.equals(prop)
                && value instanceof IArchimateModel model) {
            clearLocalChanges(model);
        }
    }

    static boolean isRecheckTrigger(String prop) {
        return IEditorModelManager.PROPERTY_MODEL_LOADED.equals(prop)
                || IEditorModelManager.PROPERTY_MODEL_OPENED.equals(prop)
                || IEditorModelManager.PROPERTY_MODEL_CREATED.equals(prop)
                || IEditorModelManager.PROPERTY_MODEL_SAVED.equals(prop);
    }

    private void scheduleRecheck(IArchimateModel model) {
        var url = ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL);
        var jobRef = new Job[1];
        var job = Job.create("Checking local model changes", //$NON-NLS-1$
                (IJobFunction) monitor -> runRecheck(model, url, jobRef[0], monitor));
        jobRef[0] = job;
        job.setSystem(true);
        if (url != null) {
            var previous = pendingRechecks.put(url, job);
            if (previous != null) {
                previous.cancel();
            }
        }
        job.schedule();
    }

    private void cancelAllPendingRechecks() {
        for (var job : pendingRechecks.values()) {
            job.cancel();
        }
        pendingRechecks.clear();
    }

    private void scheduleRecheckAll() {
        var job = Job.create("Checking local model changes", //$NON-NLS-1$
                (IJobFunction) this::runRecheckAll);
        job.setSystem(true);
        job.schedule();
    }

    private IStatus runRecheck(IArchimateModel model, String url, Job self, IProgressMonitor monitor) {
        try {
            if (monitor != null && monitor.isCanceled()) {
                return Status.CANCEL_STATUS;
            }
            if (url != null && self != null && pendingRechecks.get(url) != self) {
                return Status.CANCEL_STATUS;
            }
            if (recheckModel(model)) {
                notifyListeners();
            }
        } catch (Exception e) {
            Platform.getLog(LocalChangeService.class).warn("Local change recheck failed", e); //$NON-NLS-1$
        } finally {
            if (url != null && self != null) {
                pendingRechecks.remove(url, self);
            }
        }
        return Status.OK_STATUS;
    }

    private IStatus runRecheckAll(IProgressMonitor monitor) {
        try {
            var models = editorModelManager.getModels();
            if (models == null || models.isEmpty()) {
                return Status.OK_STATUS;
            }
            var changed = false;
            for (var model : List.copyOf(models)) {
                if (monitor.isCanceled()) {
                    break;
                }
                changed |= recheckModel(model);
            }
            if (changed) {
                notifyListeners();
            }
        } catch (Exception e) {
            Platform.getLog(LocalChangeService.class).warn("Local change recheck failed", e); //$NON-NLS-1$
        }
        return Status.OK_STATUS;
    }

    boolean recheckModel(IArchimateModel model) {
        var modelUrl = ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL);
        if (modelUrl == null) {
            return false;
        }
        var modelId = ConnectorProperties.extractModelId(modelUrl);
        if (!snapshotStore.hasSnapshot(modelId)) {
            return false;
        }
        try {
            var base = snapshotStore.loadSnapshot(modelId);
            var local = serializer.serialize(model);
            if (Arrays.equals(local, base)) {
                return savedFilesDiffer.remove(modelUrl);
            }
            return savedFilesDiffer.add(modelUrl);
        } catch (Exception e) {
            Platform.getLog(LocalChangeService.class)
                    .warn("Failed to check local changes for " + modelUrl, e); //$NON-NLS-1$
            return false;
        }
    }

    private void notifyListeners() {
        for (var listener : listeners) {
            try {
                listener.run();
            } catch (Exception e) {
                Platform.getLog(LocalChangeService.class)
                        .warn("Local change listener threw an exception", e); //$NON-NLS-1$
            }
        }
    }

}

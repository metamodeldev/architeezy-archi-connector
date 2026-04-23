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
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobFunction;
import org.eclipse.core.runtime.jobs.Job;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.ConnectorPlugin;
import com.architeezy.archi.connector.io.ModelSerializer;
import com.architeezy.archi.connector.io.SnapshotStore;
import com.architeezy.archi.connector.model.ConnectorProperties;

/**
 * Tracks whether tracked models have local changes relative to their base
 * snapshot and notifies listeners when the state changes.
 *
 * <p>
 * Fast path: a dirty CommandStack already proves the model differs from the
 * saved file (and therefore from the snapshot, since the snapshot is captured
 * at sync time when the model is clean). No serialization or byte-compare is
 * needed in that case --
 * {@link IEditorModelManager#isModelDirty(IArchimateModel)}
 * is consulted live.
 *
 * <p>
 * Slow path (full serialize + byte compare against the snapshot) only runs
 * on the events that can actually change the saved-file-vs-snapshot relation:
 * model load/open/create (initial state from disk) and model save (file was
 * just rewritten). Push/pull resets the snapshot and explicitly calls
 * {@link #clearLocalChanges(IArchimateModel)}.
 */
public final class LocalChangeService {

    /** The singleton instance. */
    public static final LocalChangeService INSTANCE = new LocalChangeService();

    /**
     * Model URL set for models whose saved file differs from the snapshot,
     * independently of the live (in-memory) dirty state. Used to keep
     * {@link #hasLocalChanges} correct after a save without push.
     */
    private final Set<String> savedFilesDiffer = ConcurrentHashMap.newKeySet();

    /**
     * Pending recheck jobs keyed by model URL. Tracked so that a subsequent
     * scheduleRecheck for the same model cancels the previous one, and so that
     * {@link #clearLocalChanges(IArchimateModel)} can cancel any in-flight
     * recheck that would otherwise race with a just-completed push/pull and
     * re-add the URL to {@link #savedFilesDiffer}.
     */
    private final ConcurrentMap<String, Job> pendingRechecks = new ConcurrentHashMap<>();

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private final PropertyChangeListener modelManagerListener = this::onModelManagerEvent;

    private LocalChangeService() {
    }

    // -----------------------------------------------------------------------
    // Lifecycle

    /** Starts listening to model lifecycle and command-stack events. */
    public void start() {
        IEditorModelManager.INSTANCE.addPropertyChangeListener(modelManagerListener);
        // Initial pass for models already loaded at startup.
        scheduleRecheckAll();
    }

    /** Stops listening and clears all tracked state. */
    public void stop() {
        IEditorModelManager.INSTANCE.removePropertyChangeListener(modelManagerListener);
        cancelAllPendingRechecks();
        savedFilesDiffer.clear();
    }

    // -----------------------------------------------------------------------
    // State queries

    /**
     * Returns {@code true} if the model has uncommitted changes relative to
     * its base snapshot. Fast path: a dirty CommandStack short-circuits the
     * answer without any I/O.
     *
     * @param model the model to check
     * @return true if local changes are present
     */
    public boolean hasLocalChanges(IArchimateModel model) {
        if (model == null) {
            return false;
        }
        if (IEditorModelManager.INSTANCE.isModelDirty(model)) {
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
        // Cancel any recheck that was queued by PROPERTY_MODEL_SAVED during
        // push/pull. Without this, a stale recheck can run after we've just
        // written a fresh snapshot and removed the URL from savedFilesDiffer,
        // re-adding it and making the push button flicker back on even though
        // the model is in sync with the snapshot.
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
     * have changed (either the dirty flag toggled, or a saved-file-vs-snapshot
     * recheck flipped).
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
            // Dirty state changed for this model - hasLocalChanges() reads it
            // live, so no state to update; just notify listeners so the UI
            // re-renders immediately instead of waiting for a poll tick.
            notifyListeners();
        } else if (isRecheckTrigger(prop) && value instanceof IArchimateModel model) {
            scheduleRecheck(model);
        } else if (IEditorModelManager.PROPERTY_MODEL_REMOVED.equals(prop)
                && value instanceof IArchimateModel model) {
            onModelRemoved(model);
        }
    }

    private static boolean isRecheckTrigger(String prop) {
        return IEditorModelManager.PROPERTY_MODEL_LOADED.equals(prop)
                || IEditorModelManager.PROPERTY_MODEL_OPENED.equals(prop)
                || IEditorModelManager.PROPERTY_MODEL_CREATED.equals(prop)
                || IEditorModelManager.PROPERTY_MODEL_SAVED.equals(prop);
    }

    private void onModelRemoved(IArchimateModel model) {
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
            // If clearLocalChanges or a newer scheduleRecheck has removed/replaced
            // our entry while we were queued, skip the compare entirely. This
            // closes the race where the job was scheduled before a push but
            // starts running after clearLocalChanges - without this check, a
            // non-deterministic serialize() could re-add the URL right after
            // push cleared it.
            if (url != null && self != null && pendingRechecks.get(url) != self) {
                return Status.CANCEL_STATUS;
            }
            if (recheckModel(model)) {
                notifyListeners();
            }
        } catch (Exception e) {
            ConnectorPlugin.getInstance().getLog().warn("Local change recheck failed", e); //$NON-NLS-1$
        } finally {
            if (url != null && self != null) {
                pendingRechecks.remove(url, self);
            }
        }
        return Status.OK_STATUS;
    }

    private IStatus runRecheckAll(IProgressMonitor monitor) {
        try {
            var models = IEditorModelManager.INSTANCE.getModels();
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
            ConnectorPlugin.getInstance().getLog().warn("Local change recheck failed", e); //$NON-NLS-1$
        }
        return Status.OK_STATUS;
    }

    /**
     * Compares the model's current serialized form against its snapshot and
     * updates {@link #savedFilesDiffer} accordingly.
     *
     * @param model the model to compare against its snapshot
     * @return true if the set membership changed for this model
     */
    private boolean recheckModel(IArchimateModel model) {
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
            if (Arrays.equals(local, base)) {
                return savedFilesDiffer.remove(modelUrl);
            }
            return savedFilesDiffer.add(modelUrl);
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

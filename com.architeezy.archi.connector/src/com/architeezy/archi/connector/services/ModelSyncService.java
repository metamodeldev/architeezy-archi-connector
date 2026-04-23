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

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelimporter.ModelImporter;
import com.architeezy.archi.connector.api.ArchiteezyClient;
import com.architeezy.archi.connector.api.CancelSignal;
import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.auth.ProfileRegistry;
import com.architeezy.archi.connector.auth.ProfileStatus;
import com.architeezy.archi.connector.io.ModelSerializer;
import com.architeezy.archi.connector.io.SnapshotStore;
import com.architeezy.archi.connector.io.SnapshotSupport;
import com.architeezy.archi.connector.io.TrackedModelStore;
import com.architeezy.archi.connector.model.ConnectorProperties;
import com.architeezy.archi.connector.model.IEditorModelManagerAdapter;
import com.architeezy.archi.connector.model.PullOutcome;
import com.architeezy.archi.connector.model.SyncScenario;

/**
 * Non-destructive pull and conflict-aware push for locally open models.
 *
 * Must be called from a background thread (Job / IRunnableWithProgress).
 */
@SuppressWarnings("java:S112")
public final class ModelSyncService {

    private final ArchiteezyClient client;

    private final AuthService authService;

    private final ProfileRegistry profileRegistry;

    private final SnapshotStore snapshotStore;

    private final ModelSerializer serializer;

    private final TrackedModelStore trackedModels;

    private final MergeService mergeService;

    private final LocalChangeService localChangeService;

    private final UpdateCheckService updateCheckService;

    private final IEditorModelManagerAdapter editorModelManager;

    private final UiSynchronizer uiSync;

    private final SyncScenarioDetector scenarioDetector;

    /**
     * Creates a sync service wired with all required collaborators.
     *
     * @param client HTTP client
     * @param authService OAuth token provider
     * @param profileRegistry profile registry used to find the server's profile
     * @param snapshotStore snapshot store
     * @param serializer model serializer
     * @param trackedModels workspace metadata store for tracked models
     * @param mergeService 3-way merge service
     * @param localChangeService local-change tracker (cleared after pull/push)
     * @param updateCheckService remote-update tracker (cleared after pull/push)
     * @param editorModelManager editor-model manager adapter used to save the
     *        model after a pull
     * @param uiSync UI-thread synchronizer used to run the model-import step
     *        on the UI thread and propagate any exception back to the caller
     */
    @SuppressWarnings({ "checkstyle:ParameterNumber", "java:S107" })
    public ModelSyncService(ArchiteezyClient client, AuthService authService, ProfileRegistry profileRegistry,
            SnapshotStore snapshotStore, ModelSerializer serializer, TrackedModelStore trackedModels,
            MergeService mergeService, LocalChangeService localChangeService,
            UpdateCheckService updateCheckService, IEditorModelManagerAdapter editorModelManager,
            UiSynchronizer uiSync) {
        this.client = client;
        this.authService = authService;
        this.profileRegistry = profileRegistry;
        this.snapshotStore = snapshotStore;
        this.serializer = serializer;
        this.trackedModels = trackedModels;
        this.mergeService = mergeService;
        this.localChangeService = localChangeService;
        this.updateCheckService = updateCheckService;
        this.editorModelManager = editorModelManager;
        this.uiSync = uiSync;
        this.scenarioDetector = new SyncScenarioDetector(serializer, snapshotStore);
    }

    // -----------------------------------------------------------------------
    // Pull

    /**
     * Downloads the latest model content from the Architeezy server and
     * applies it to the locally open model without closing or reopening it.
     *
     * <p>
     * When a 3-way merge surfaces real conflicts, this method does <b>not</b>
     * open the resolution dialog: it returns a
     * {@link PullResult#pending(PendingConflict)} so the caller can finish its
     * Job, open the dialog on the UI thread, and schedule a follow-up Job that
     * calls {@link #applyMergedPull}. This keeps Jobs from appearing stuck in
     * the Progress View while the user decides.
     *
     * @param model the locally open model to update
     * @param monitor progress monitor
     * @return the pull result
     * @throws IllegalStateException if the model is not tracked
     * @throws Exception if the pull fails
     */
    public PullResult pullModel(IArchimateModel model, IProgressMonitor monitor) throws Exception {
        var modelUrl = ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL);
        if (modelUrl == null) {
            throw new IllegalStateException("Model is not tracked by Architeezy"); //$NON-NLS-1$
        }
        var serverUrl = ConnectorProperties.extractServerUrl(modelUrl);
        final var modelId = ConnectorProperties.extractModelId(modelUrl);

        var profile = profileRegistry.findProfileForServer(serverUrl);
        String token = null;
        if (profile != null && profile.getStatus() == ProfileStatus.CONNECTED) {
            token = authService.getValidAccessToken(profile);
        }

        SnapshotSupport.setSubTask(monitor, "Fetching metadata"); //$NON-NLS-1$
        var remote = client.getModel(serverUrl, token, modelId);

        SnapshotSupport.setSubTask(monitor, "Downloading " + remote.name()); //$NON-NLS-1$
        CancelSignal cancel = monitor == null ? CancelSignal.NEVER : monitor::isCanceled;
        final var remoteContent = client.getModelContent(token, remote.contentUrl(), cancel);

        SnapshotSupport.setSubTask(monitor, "Analyzing changes"); //$NON-NLS-1$
        var scenario = scenarioDetector.detect(model, modelId, remoteContent);

        switch (scenario) {
        case UP_TO_DATE:
            updateCheckService.clearUpdate(model);
            return PullResult.completed(PullOutcome.UP_TO_DATE);
        case SIMPLE_PULL:
            SnapshotSupport.setSubTask(monitor, "Applying remote changes"); //$NON-NLS-1$
            applyNonDestructivePull(model, remoteContent, remote, modelId);
            return PullResult.completed(PullOutcome.APPLIED);
        case SIMPLE_PUSH:
            updateCheckService.clearUpdate(model);
            return PullResult.completed(PullOutcome.REMOTE_UNCHANGED);
        case DIVERGED:
            return prepareDivergedPull(model, modelId, remoteContent, remote, monitor);
        default:
            return PullResult.completed(PullOutcome.UP_TO_DATE);
        }
    }

    /**
     * Completes a pull after the UI has resolved a {@link PendingConflict}
     * produced by {@link #pullModel}.
     *
     * @param model the locally open model
     * @param mergedBytes the merged content produced by the UI
     * @param pending the conflict context returned by the first-stage pull
     * @param monitor progress monitor
     * @throws Exception if applying the merged bytes fails
     */
    public void applyMergedPull(IArchimateModel model, byte[] mergedBytes, PendingConflict pending,
            IProgressMonitor monitor) throws Exception {
        SnapshotSupport.setSubTask(monitor, "Applying merged changes"); //$NON-NLS-1$
        applyNonDestructivePull(model, mergedBytes, pending.remote(), pending.modelId());
    }

    private void applyNonDestructivePull(IArchimateModel target, byte[] remoteContent,
            RemoteModel remote, String modelId) throws Exception {
        var incoming = serializer.deserializeInMemory(remoteContent);

        uiSync.syncCall(() -> {
            importIntoTarget(incoming, target, remote);
            return null;
        });

        try {
            snapshotStore.saveSnapshot(modelId, serializer.serialize(target));
        } catch (Exception e) {
            Platform.getLog(ModelSyncService.class).warn("Failed to save snapshot after pull", e); //$NON-NLS-1$
        }

        localChangeService.clearLocalChanges(target);
        updateCheckService.clearUpdate(target);
    }

    private PullResult prepareDivergedPull(IArchimateModel model, String modelId,
            byte[] remoteContent, RemoteModel remote, IProgressMonitor monitor) throws Exception {
        SnapshotSupport.setSubTask(monitor, "Analyzing 3-way merge"); //$NON-NLS-1$
        var baseBytes = snapshotStore.loadSnapshot(modelId);
        var prep = mergeService.prepareMerge(model, baseBytes, remoteContent, remote, modelId);
        if (prep.mergedBytes() != null) {
            SnapshotSupport.setSubTask(monitor, "Applying merged changes"); //$NON-NLS-1$
            applyNonDestructivePull(model, prep.mergedBytes(), remote, modelId);
            return PullResult.completed(PullOutcome.APPLIED);
        }
        return PullResult.pending(prep.pending());
    }

    private void importIntoTarget(IArchimateModel incoming, IArchimateModel target, RemoteModel remote)
            throws Exception {
        var importer = new ModelImporter();
        importer.setUpdateAll(true);
        importer.setUpdateFolderStructure(true);
        importer.doImport(incoming, target);
        ConnectorProperties.setProperty(target, ConnectorProperties.KEY_URL, remote.selfUrl());
        editorModelManager.saveModel(target);
        trackedModels.setLastModified(
                ConnectorProperties.extractModelId(remote.selfUrl()), remote.lastModified());
    }

    // -----------------------------------------------------------------------
    // Push

    /**
     * Uploads the current local model to the Architeezy server.
     *
     * @param model the locally open model to push
     * @param monitor progress monitor
     * @throws IllegalStateException if the model is not tracked or no profile is
     *         connected
     * @throws Exception if the push fails
     */
    public void pushModel(IArchimateModel model, IProgressMonitor monitor) throws Exception {
        var modelUrl = ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL);
        if (modelUrl == null) {
            throw new IllegalStateException("Model is not tracked by Architeezy"); //$NON-NLS-1$
        }
        var serverUrl = ConnectorProperties.extractServerUrl(modelUrl);
        final var modelId = ConnectorProperties.extractModelId(modelUrl);

        var profile = profileRegistry.findProfileForServer(serverUrl);
        if (profile == null || profile.getStatus() != ProfileStatus.CONNECTED) {
            throw new IllegalStateException("Not authenticated with server: " + serverUrl); //$NON-NLS-1$
        }
        var token = authService.getValidAccessToken(profile);

        SnapshotSupport.setSubTask(monitor, "Checking for remote updates"); //$NON-NLS-1$
        var remote = client.getModel(serverUrl, token, modelId);
        CancelSignal cancel = monitor == null ? CancelSignal.NEVER : monitor::isCanceled;
        final var remoteContent = client.getModelContent(token, remote.contentUrl(), cancel);
        final var scenario = scenarioDetector.detect(model, modelId, remoteContent);

        if (scenario == SyncScenario.SIMPLE_PULL || scenario == SyncScenario.DIVERGED) {
            SnapshotSupport.setSubTask(monitor, "Applying remote changes"); //$NON-NLS-1$
            byte[] contentToApply;
            if (scenario == SyncScenario.DIVERGED) {
                var baseBytes = snapshotStore.loadSnapshot(modelId);
                contentToApply = mergeService.computeMergedContent(model, baseBytes, remoteContent);
                if (contentToApply == null) {
                    return;
                }
            } else {
                contentToApply = remoteContent;
            }
            applyNonDestructivePull(model, contentToApply, remote, modelId);
            token = authService.getValidAccessToken(profile);
        }

        uploadAndFinalize(model, token, serverUrl, modelId, modelUrl, monitor);
    }

    private void uploadAndFinalize(IArchimateModel model, String token, String serverUrl,
            String modelId, String modelUrl, IProgressMonitor monitor) throws Exception {
        SnapshotSupport.setSubTask(monitor, "Uploading model"); //$NON-NLS-1$
        var uploadContent = serializer.serialize(model);
        CancelSignal cancel = monitor == null ? CancelSignal.NEVER : monitor::isCanceled;
        var putResult = client.pushModelContent(token, modelUrl, uploadContent, cancel);

        SnapshotSupport.setSubTask(monitor, "Updating metadata"); //$NON-NLS-1$
        final var updatedRemote = putResult != null
                ? putResult
                : client.getModel(serverUrl, token, modelId);
        trackedModels.setLastModified(modelId, updatedRemote.lastModified());

        try {
            snapshotStore.saveSnapshot(modelId, uploadContent);
        } catch (Exception e) {
            Platform.getLog(ModelSyncService.class).warn("Failed to save snapshot after push", e); //$NON-NLS-1$
        }

        localChangeService.clearLocalChanges(model);
        updateCheckService.clearUpdate(model);
    }

}

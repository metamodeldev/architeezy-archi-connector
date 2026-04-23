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

import java.io.IOException;
import java.util.Arrays;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelimporter.ModelImporter;
import com.architeezy.archi.connector.ConnectorPlugin;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.api.ArchiteezyClient;
import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.auth.ProfileRegistry;
import com.architeezy.archi.connector.auth.ProfileStatus;
import com.architeezy.archi.connector.io.ModelSerializer;
import com.architeezy.archi.connector.io.SnapshotStore;
import com.architeezy.archi.connector.io.SnapshotSupport;
import com.architeezy.archi.connector.io.TrackedModelStore;
import com.architeezy.archi.connector.model.ConnectorProperties;
import com.architeezy.archi.connector.model.SyncScenario;

/**
 * Non-destructive pull and conflict-aware push for locally open models.
 *
 * Must be called from a background thread (Job / IRunnableWithProgress).
 */
@SuppressWarnings({ "java:S6548", "java:S112" })
public final class ModelSyncService {

    /** The singleton instance. */
    public static final ModelSyncService INSTANCE = new ModelSyncService();

    private final ArchiteezyClient client = new ArchiteezyClient();

    private ModelSyncService() {
    }

    // -----------------------------------------------------------------------
    // Pull

    /**
     * Downloads the latest model content from the Architeezy server and
     * applies it to the locally open model without closing or reopening it.
     *
     * <p>
     * The operation first classifies the relationship between the local model,
     * the remote model, and the stored base snapshot into one of four scenarios:
     * <ul>
     * <li><b>UP_TO_DATE</b> - nothing to do.</li>
     * <li><b>SIMPLE_PULL</b> - remote changed, local did not; remote changes are
     * applied via {@link ModelImporter} so open diagrams and the Undo stack
     * are preserved.</li>
     * <li><b>SIMPLE_PUSH</b> - local changed, remote did not; the user is reminded
     * to push their changes.</li>
     * <li><b>DIVERGED</b> - both sides changed; the user is asked whether to
     * overwrite local changes with the remote version.</li>
     * </ul>
     *
     * @param model the locally open model to update
     * @param monitor progress monitor
     * @return {@code true} if remote changes were applied, {@code false} if the
     *         user cancelled the conflict-resolution dialog or no changes were
     *         applied
     * @throws IllegalStateException if the model is not tracked
     * @throws Exception if the pull fails
     */
    public boolean pullModel(IArchimateModel model, IProgressMonitor monitor) throws Exception {
        var modelUrl = ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL);
        if (modelUrl == null) {
            throw new IllegalStateException("Model is not tracked by Architeezy"); //$NON-NLS-1$
        }
        var serverUrl = ConnectorProperties.extractServerUrl(modelUrl);
        final var modelId = ConnectorProperties.extractModelId(modelUrl);

        var profile = ProfileRegistry.INSTANCE.findProfileForServer(serverUrl);
        String token = null;
        if (profile != null && profile.getStatus() == ProfileStatus.CONNECTED) {
            token = AuthService.INSTANCE.getValidAccessToken(profile);
        }

        SnapshotSupport.setSubTask(monitor, "Fetching metadata"); //$NON-NLS-1$
        var remote = client.getModel(serverUrl, token, modelId);

        SnapshotSupport.setSubTask(monitor, "Downloading " + remote.name()); //$NON-NLS-1$
        final var remoteContent = client.getModelContent(token, remote.contentUrl());

        SnapshotSupport.setSubTask(monitor, "Analyzing changes"); //$NON-NLS-1$
        var scenario = detectSyncScenario(model, modelId, remoteContent);

        switch (scenario) {
        case UP_TO_DATE:
            UpdateCheckService.INSTANCE.clearUpdate(model);
            return false;
        case SIMPLE_PULL:
            SnapshotSupport.setSubTask(monitor, "Applying remote changes"); //$NON-NLS-1$
            applyNonDestructivePull(model, remoteContent, remote, modelId);
            return true;
        case SIMPLE_PUSH:
            Display.getDefault().syncExec(() -> MessageDialog.openInformation(
                    Display.getDefault().getActiveShell(),
                    Messages.PullHandler_title,
                    Messages.PullHandler_remoteUnchanged));
            UpdateCheckService.INSTANCE.clearUpdate(model);
            return false;
        case DIVERGED:
            return applyDivergedPull(model, modelId, remoteContent, remote, monitor);
        default:
            return false;
        }
    }

    /**
     * Classifies the pull scenario by comparing Local, Base, and Remote content.
     *
     * <p>
     * When no base snapshot exists the method conservatively returns
     * {@link SyncScenario#SIMPLE_PULL} so the remote is applied without conflict.
     *
     * @param model the locally open model
     * @param modelId the repository model identifier
     * @param remoteContent raw XMI bytes from the server
     * @return the applicable {@link SyncScenario}
     * @throws IOException if the local model cannot be serialized
     */
    SyncScenario detectSyncScenario(IArchimateModel model, String modelId,
            byte[] remoteContent) throws IOException {
        if (!SnapshotStore.INSTANCE.hasSnapshot(modelId)) {
            return SyncScenario.SIMPLE_PULL;
        }
        var base = SnapshotStore.INSTANCE.loadSnapshot(modelId);
        var local = ModelSerializer.INSTANCE.serialize(model);
        return computeSyncScenario(Arrays.equals(local, base), Arrays.equals(remoteContent, base));
    }

    /**
     * Pure mapping from comparison flags to scenario, extracted for unit testing.
     *
     * @param localEqualsBase whether the serialized local model equals the base
     *        snapshot
     * @param remoteEqualsBase whether the remote content equals the base snapshot
     * @return the applicable {@link SyncScenario}
     */
    static SyncScenario computeSyncScenario(boolean localEqualsBase, boolean remoteEqualsBase) {
        if (localEqualsBase && remoteEqualsBase) {
            return SyncScenario.UP_TO_DATE;
        }
        if (localEqualsBase) {
            return SyncScenario.SIMPLE_PULL;
        }
        if (remoteEqualsBase) {
            return SyncScenario.SIMPLE_PUSH;
        }
        return SyncScenario.DIVERGED;
    }

    /**
     * Applies remote model content to the open target model non-destructively
     * using {@link ModelImporter}. Open diagrams remain open and the Undo stack
     * is preserved.
     *
     * <p>
     * After import, connector metadata is updated and a new base snapshot is saved.
     *
     * @param target the model currently open in Archi
     * @param remoteContent raw XMI bytes from the server
     * @param remote server-side metadata of the model
     * @param modelId the repository model identifier
     * @throws Exception if import or snapshot saving fails
     */
    private void applyNonDestructivePull(IArchimateModel target, byte[] remoteContent,
            RemoteModel remote, String modelId) throws Exception {
        var incoming = ModelSerializer.INSTANCE.deserializeInMemory(remoteContent);

        var uiError = new Exception[1];
        Display.getDefault().syncExec(() -> runImportOnDisplay(incoming, target, remote, uiError));
        if (uiError[0] != null) {
            throw uiError[0];
        }

        try {
            // Serialize AFTER connector properties are set so the snapshot matches future local comparisons.
            SnapshotStore.INSTANCE.saveSnapshot(modelId, ModelSerializer.INSTANCE.serialize(target));
        } catch (Exception e) {
            ConnectorPlugin.getInstance().getLog().warn("Failed to save snapshot after pull", e); //$NON-NLS-1$
        }

        // The saveModel inside runImportOnDisplay fires PROPERTY_MODEL_SAVED,
        // which schedules an async recheck. That recheck can race against the
        // saveSnapshot above - if it runs first, it compares post-import model
        // bytes to the stale pre-pull snapshot and marks the URL as having
        // local changes. Clear the flag (and cancel the pending recheck) so
        // the push button doesn't light up after a clean pull.
        LocalChangeService.INSTANCE.clearLocalChanges(target);
        UpdateCheckService.INSTANCE.clearUpdate(target);
    }

    private boolean applyDivergedPull(IArchimateModel model, String modelId,
            byte[] remoteContent, RemoteModel remote, IProgressMonitor monitor) throws Exception {
        SnapshotSupport.setSubTask(monitor, "Resolving conflicts"); //$NON-NLS-1$
        var baseBytes = SnapshotStore.INSTANCE.loadSnapshot(modelId);
        var mergedBytes = MergeService.INSTANCE.computeMergedContent(model, baseBytes, remoteContent);
        if (mergedBytes == null) {
            return false;
        }
        SnapshotSupport.setSubTask(monitor, "Applying merged changes"); //$NON-NLS-1$
        applyNonDestructivePull(model, mergedBytes, remote, modelId);
        return true;
    }

    private static void runImportOnDisplay(IArchimateModel incoming, IArchimateModel target,
            RemoteModel remote, Exception[] uiError) {
        try {
            var importer = new ModelImporter();
            importer.setUpdateAll(true);
            importer.setUpdateFolderStructure(true);
            importer.doImport(incoming, target);
            ConnectorProperties.setProperty(target, ConnectorProperties.KEY_URL, remote.selfUrl());
            IEditorModelManager.INSTANCE.saveModel(target);
            TrackedModelStore.INSTANCE.setLastModified(
                    ConnectorProperties.extractModelId(remote.selfUrl()), remote.lastModified());
        } catch (Exception e) {
            uiError[0] = e;
        }
    }

    // -----------------------------------------------------------------------
    // Push

    /**
     * Uploads the current local model to the Architeezy server.
     *
     * <p>
     * Before uploading, the server is checked for newer remote content. If the
     * remote has changed, a pull (or 3-way merge for {@link SyncScenario#DIVERGED})
     * is performed first. The push is aborted if the user cancels the merge dialog.
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

        var profile = ProfileRegistry.INSTANCE.findProfileForServer(serverUrl);
        if (profile == null || profile.getStatus() != ProfileStatus.CONNECTED) {
            throw new IllegalStateException("Not authenticated with server: " + serverUrl); //$NON-NLS-1$
        }
        var token = AuthService.INSTANCE.getValidAccessToken(profile);

        // Step 1: Check for remote updates and pull if needed
        SnapshotSupport.setSubTask(monitor, "Checking for remote updates"); //$NON-NLS-1$
        var remote = client.getModel(serverUrl, token, modelId);
        final var remoteContent = client.getModelContent(token, remote.contentUrl());
        final var scenario = detectSyncScenario(model, modelId, remoteContent);

        if (scenario == SyncScenario.SIMPLE_PULL || scenario == SyncScenario.DIVERGED) {
            SnapshotSupport.setSubTask(monitor, "Applying remote changes"); //$NON-NLS-1$
            byte[] contentToApply;
            if (scenario == SyncScenario.DIVERGED) {
                var baseBytes = SnapshotStore.INSTANCE.loadSnapshot(modelId);
                contentToApply = MergeService.INSTANCE.computeMergedContent(model, baseBytes, remoteContent);
                if (contentToApply == null) {
                    return; // user cancelled merge dialog
                }
            } else {
                contentToApply = remoteContent;
            }
            applyNonDestructivePull(model, contentToApply, remote, modelId);
            // Refresh token after potentially long merge interaction
            token = AuthService.INSTANCE.getValidAccessToken(profile);
        }

        uploadAndFinalize(model, token, serverUrl, modelId, modelUrl, monitor);
    }

    private void uploadAndFinalize(IArchimateModel model, String token, String serverUrl,
            String modelId, String modelUrl, IProgressMonitor monitor) throws Exception {
        // Step 2: Upload local content. The bytes we upload are exactly what
        // the server will store and hand back, so reusing them as the snapshot
        // guarantees snapshot == server content == serialize(model) right
        // after the push - no follow-up sync check can classify the model as
        // out of date.
        SnapshotSupport.setSubTask(monitor, "Uploading model"); //$NON-NLS-1$
        var uploadContent = ModelSerializer.INSTANCE.serialize(model);
        var putResult = client.pushModelContent(token, modelUrl, uploadContent);

        // Step 3: Record the server-assigned lastModified in the workspace
        // store (not in the model), and save the snapshot from the uploaded
        // bytes. Prefer the PUT response; fall back to GET only if the server
        // returned no parseable body.
        SnapshotSupport.setSubTask(monitor, "Updating metadata"); //$NON-NLS-1$
        final var updatedRemote = putResult != null
                ? putResult
                : client.getModel(serverUrl, token, modelId);
        TrackedModelStore.INSTANCE.setLastModified(modelId, updatedRemote.lastModified());

        try {
            SnapshotStore.INSTANCE.saveSnapshot(modelId, uploadContent);
        } catch (Exception e) {
            ConnectorPlugin.getInstance().getLog().warn("Failed to save snapshot after push", e); //$NON-NLS-1$
        }

        LocalChangeService.INSTANCE.clearLocalChanges(model);
        UpdateCheckService.INSTANCE.clearUpdate(model);
    }

}

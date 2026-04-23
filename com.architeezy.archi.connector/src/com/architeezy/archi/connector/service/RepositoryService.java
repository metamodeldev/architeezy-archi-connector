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

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.modelimporter.ModelImporter;
import com.architeezy.archi.connector.ConnectorPlugin;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.api.ArchiteezyClient;
import com.architeezy.archi.connector.api.PagedResult;
import com.architeezy.archi.connector.api.RemoteModel;
import com.architeezy.archi.connector.api.RemoteProject;
import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.auth.ProfileStatus;
import com.architeezy.archi.connector.io.ModelSerializer;
import com.architeezy.archi.connector.io.SnapshotStore;
import com.architeezy.archi.connector.model.ConnectorProperties;

/**
 * High-level operations on the remote repository: browse, import, publish.
 *
 * Must be called from a background thread (Job / IRunnableWithProgress).
 */
public final class RepositoryService {

    /** The singleton instance of RepositoryService. */
    public static final RepositoryService INSTANCE = new RepositoryService();

    private static final DateTimeFormatter EXPORT_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"); //$NON-NLS-1$

    /** Client used for communication with the Architeezy server. */
    private final ArchiteezyClient client = new ArchiteezyClient();

    private RepositoryService() {
    }

    // -----------------------------------------------------------------------
    // Browse

    /**
     * Lists the models available in the remote repository.
     *
     * @param profile the connection profile to use
     * @param page the page number to retrieve
     * @param size the number of items per page
     * @return a paged result containing the list of remote models
     * @throws Exception if a communication error occurs
     */
    public PagedResult<RemoteModel> listModels(ConnectionProfile profile,
            int page, int size) throws Exception {
        var token = profile.getStatus() == ProfileStatus.CONNECTED
                ? AuthService.INSTANCE.getValidAccessToken(profile)
                : null;
        return client.listModels(profile.getServerUrl(), token, page, size);
    }

    // -----------------------------------------------------------------------
    // Import (download + open in Archi)

    /**
     * Downloads the model content, writes it to {@code targetFile}, sets
     * {@link ConnectorProperties} on the opened model, and saves a base snapshot.
     *
     * The operation is atomic: if anything fails after the file is written,
     * the file is deleted before the exception propagates.
     *
     * @param profile the connection profile to use
     * @param remote the remote model to import
     * @param targetFile the local file where the model should be saved
     * @param monitor progress monitor for updating the UI
     * @return the imported IArchimateModel
     * @throws Exception if the import process fails
     */
    public IArchimateModel importModel(ConnectionProfile profile, RemoteModel remote, File targetFile,
            IProgressMonitor monitor) throws Exception {
        if (monitor != null) {
            monitor.subTask("Downloading " + remote.name()); //$NON-NLS-1$
        }

        var authenticated = profile.getStatus() == ProfileStatus.CONNECTED;
        var token = authenticated ? AuthService.INSTANCE.getValidAccessToken(profile) : null;
        var content = client.getModelContent(token, remote.contentUrl());

        var model = ModelSerializer.INSTANCE.deserialize(content, targetFile);

        try {
            openAndConfigureModel(model, remote, authenticated);
        } catch (Exception e) {
            if (targetFile.exists()) {
                targetFile.delete();
            }
            throw e;
        }

        if (authenticated) {
            saveSnapshotAfterConfigure(model, ConnectorProperties.extractModelId(remote.selfUrl()), monitor);
        }

        return model;
    }

    private void openAndConfigureModel(IArchimateModel model, RemoteModel remote, boolean authenticated)
            throws Exception {
        var uiError = new Exception[1];
        Display.getDefault().syncExec(() -> configureModelOnDisplay(model, remote, authenticated, uiError));
        if (uiError[0] != null) {
            throw uiError[0];
        }
    }

    private static void configureModelOnDisplay(IArchimateModel model, RemoteModel remote,
            boolean authenticated, Exception[] uiError) {
        try {
            IEditorModelManager.INSTANCE.openModel(model);
            if (authenticated) {
                ConnectorProperties.setProperty(model, ConnectorProperties.KEY_URL, remote.selfUrl());
                ConnectorProperties.setProperty(model, ConnectorProperties.KEY_LAST_MODIFICATION_DATE_TIME,
                        remote.lastModified());
            }
            IEditorModelManager.INSTANCE.saveModel(model);
        } catch (Exception e) {
            uiError[0] = e;
        }
    }

    /**
     * Serializes the model WITH connector properties already set and saves the
     * result as the base snapshot. Storing the post-configure bytes ensures that
     * the next comparison (local vs. base) produces equal results when no edits
     * have been made since the last import/pull/publish.
     *
     * @param model the configured model
     * @param modelId the repository model identifier
     * @param monitor progress monitor
     */
    private void saveSnapshotAfterConfigure(IArchimateModel model, String modelId,
            IProgressMonitor monitor) {
        if (monitor != null) {
            monitor.subTask("Saving snapshot"); //$NON-NLS-1$
        }
        try {
            var bytes = ModelSerializer.INSTANCE.serialize(model);
            SnapshotStore.INSTANCE.saveSnapshot(modelId, bytes);
        } catch (Exception e) {
            ConnectorPlugin.getInstance().getLog().error("Failed to save initial snapshot", e); //$NON-NLS-1$
        }
    }

    // -----------------------------------------------------------------------
    // Export (upload to project)

    /**
     * Serializes the local model and exports it to the given project on the server.
     *
     * @param profile the connection profile to use
     * @param model the model to export
     * @param projectId the target project identifier
     * @param monitor progress monitor for updating the UI
     * @throws Exception if the export process fails
     */
    public void exportModel(ConnectionProfile profile, IArchimateModel model, String projectId,
            IProgressMonitor monitor) throws Exception {
        final var token = AuthService.INSTANCE.getValidAccessToken(profile);
        if (monitor != null) {
            monitor.subTask("Serializing model"); //$NON-NLS-1$
        }
        var fileName = buildExportFileName(model);
        var content = ModelSerializer.INSTANCE.serialize(model);
        if (monitor != null) {
            monitor.subTask("Uploading model"); //$NON-NLS-1$
        }
        var created = client.exportModel(profile.getServerUrl(), token, projectId, fileName, content);

        if (monitor != null) {
            monitor.subTask("Updating metadata"); //$NON-NLS-1$
        }
        var uiError = new Exception[1];
        Display.getDefault().syncExec(() -> {
            try {
                ConnectorProperties.setProperty(model, ConnectorProperties.KEY_URL, created.selfUrl());
                ConnectorProperties.setProperty(model, ConnectorProperties.KEY_LAST_MODIFICATION_DATE_TIME,
                        created.lastModified());
                IEditorModelManager.INSTANCE.saveModel(model);
            } catch (Exception e) {
                uiError[0] = e;
            }
        });
        if (uiError[0] != null) {
            throw uiError[0];
        }

        var modelId = ConnectorProperties.extractModelId(created.selfUrl());
        if (modelId != null) {
            saveSnapshotAfterConfigure(model, modelId, monitor);
        }
    }

    private static String buildExportFileName(IArchimateModel model) {
        var base = model.getName() != null && !model.getName().isBlank()
                ? model.getName().replaceAll("[\\\\/:*?\"<>|]", "_") //$NON-NLS-1$ //$NON-NLS-2$
                : "model"; //$NON-NLS-1$
        return base + "-" + LocalDateTime.now().format(EXPORT_TS) + ".archimate"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    // -----------------------------------------------------------------------
    // Projects

    /**
     * Lists the projects available in the remote repository.
     *
     * @param profile the connection profile to use
     * @return list of remote projects
     * @throws Exception if a communication error occurs
     */
    public List<RemoteProject> listProjects(ConnectionProfile profile) throws Exception {
        var token = AuthService.INSTANCE.getValidAccessToken(profile);
        return client.listProjects(profile.getServerUrl(), token);
    }

    // -----------------------------------------------------------------------
    // Pull (smart non-destructive update)

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

        var profile = AuthService.INSTANCE.findProfileForServer(serverUrl);
        String token = null;
        if (profile != null && profile.getStatus() == ProfileStatus.CONNECTED) {
            token = AuthService.INSTANCE.getValidAccessToken(profile);
        }

        setSubTask(monitor, "Fetching metadata"); //$NON-NLS-1$
        var remote = client.getModel(serverUrl, token, modelId);

        setSubTask(monitor, "Downloading " + remote.name()); //$NON-NLS-1$
        final var remoteContent = client.getModelContent(token, remote.contentUrl());

        setSubTask(monitor, "Analyzing changes"); //$NON-NLS-1$
        var scenario = detectSyncScenario(model, modelId, remoteContent);

        switch (scenario) {
        case UP_TO_DATE:
            UpdateCheckService.INSTANCE.clearUpdate(model);
            return false;
        case SIMPLE_PULL:
            setSubTask(monitor, "Applying remote changes"); //$NON-NLS-1$
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

        UpdateCheckService.INSTANCE.clearUpdate(target);
    }

    private boolean applyDivergedPull(IArchimateModel model, String modelId,
            byte[] remoteContent, RemoteModel remote, IProgressMonitor monitor) throws Exception {
        setSubTask(monitor, "Resolving conflicts"); //$NON-NLS-1$
        var baseBytes = SnapshotStore.INSTANCE.loadSnapshot(modelId);
        var mergedBytes = MergeService.INSTANCE.computeMergedContent(model, baseBytes, remoteContent);
        if (mergedBytes == null) {
            return false;
        }
        setSubTask(monitor, "Applying merged changes"); //$NON-NLS-1$
        applyNonDestructivePull(model, mergedBytes, remote, modelId);
        return true;
    }

    private static void setSubTask(IProgressMonitor monitor, String task) {
        if (monitor != null) {
            monitor.subTask(task);
        }
    }

    private static void runImportOnDisplay(IArchimateModel incoming, IArchimateModel target,
            RemoteModel remote, Exception[] uiError) {
        try {
            var importer = new ModelImporter();
            importer.setUpdateAll(true);
            importer.setUpdateFolderStructure(true);
            importer.doImport(incoming, target);
            ConnectorProperties.setProperty(target, ConnectorProperties.KEY_URL, remote.selfUrl());
            ConnectorProperties.setProperty(target,
                    ConnectorProperties.KEY_LAST_MODIFICATION_DATE_TIME, remote.lastModified());
            IEditorModelManager.INSTANCE.saveModel(target);
        } catch (Exception e) {
            uiError[0] = e;
        }
    }

    // -----------------------------------------------------------------------
    // Push (upload local changes to server)

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

        var profile = AuthService.INSTANCE.findProfileForServer(serverUrl);
        if (profile == null || profile.getStatus() != ProfileStatus.CONNECTED) {
            throw new IllegalStateException("Not authenticated with server: " + serverUrl); //$NON-NLS-1$
        }
        var token = AuthService.INSTANCE.getValidAccessToken(profile);

        // Step 1: Check for remote updates and pull if needed
        if (monitor != null) {
            monitor.subTask("Checking for remote updates"); //$NON-NLS-1$
        }
        var remote = client.getModel(serverUrl, token, modelId);
        final var remoteContent = client.getModelContent(token, remote.contentUrl());
        final var scenario = detectSyncScenario(model, modelId, remoteContent);

        if (scenario == SyncScenario.SIMPLE_PULL || scenario == SyncScenario.DIVERGED) {
            if (monitor != null) {
                monitor.subTask("Applying remote changes"); //$NON-NLS-1$
            }
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
        // Step 2: Upload local content
        if (monitor != null) {
            monitor.subTask("Uploading model"); //$NON-NLS-1$
        }
        var uploadContent = ModelSerializer.INSTANCE.serialize(model);
        var putResult = client.pushModelContent(token, modelUrl, uploadContent);

        // Step 3: Update connector properties from the PUT response. Avoid a
        // follow-up GET so the stored lastModified matches exactly the write
        // we just made (a separate GET can race against server-side
        // re-indexing and return a different value, which would make the push
        // button re-activate because the snapshot captured here wouldn't
        // match the server's view on the next sync check).
        if (monitor != null) {
            monitor.subTask("Updating metadata"); //$NON-NLS-1$
        }
        final var updatedRemote = putResult != null
                ? putResult
                : client.getModel(serverUrl, token, modelId);

        var uiError = new Exception[1];
        Display.getDefault().syncExec(() -> {
            try {
                ConnectorProperties.setProperty(model, ConnectorProperties.KEY_LAST_MODIFICATION_DATE_TIME,
                        updatedRemote.lastModified());
                IEditorModelManager.INSTANCE.saveModel(model);
            } catch (Exception e) {
                uiError[0] = e;
            }
        });
        if (uiError[0] != null) {
            throw uiError[0];
        }

        // Save snapshot after metadata update so future comparisons see local == base
        try {
            SnapshotStore.INSTANCE.saveSnapshot(modelId, ModelSerializer.INSTANCE.serialize(model));
        } catch (Exception e) {
            ConnectorPlugin.getInstance().getLog().warn("Failed to save snapshot after push", e); //$NON-NLS-1$
        }

        LocalChangeService.INSTANCE.clearLocalChanges(model);
        UpdateCheckService.INSTANCE.clearUpdate(model);
    }

    // -----------------------------------------------------------------------
    // Delete

    /**
     * Deletes the specified remote model from the repository.
     *
     * @param profile the connection profile to use
     * @param remote the remote model to delete
     * @throws Exception if the deletion fails
     */
    public void deleteModel(ConnectionProfile profile, RemoteModel remote) throws Exception {
        var token = AuthService.INSTANCE.getValidAccessToken(profile);
        client.deleteModel(token, remote.selfUrl());
    }

}

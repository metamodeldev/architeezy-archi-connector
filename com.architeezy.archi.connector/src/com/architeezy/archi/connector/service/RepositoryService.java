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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.ConnectorPlugin;
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
     * @param profile The connection profile to use.
     * @param page The page number to retrieve.
     * @param size The number of items per page.
     * @return A paged result containing the list of remote models.
     * @throws Exception If a communication error occurs.
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
     * @param profile The connection profile to use.
     * @param remote The remote model to import.
     * @param targetFile The local file where the model should be saved.
     * @param monitor Progress monitor for updating the UI.
     * @return The imported IArchimateModel.
     * @throws Exception If the import process fails.
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
            saveInitialSnapshot(remote, content, monitor);
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

    private void saveInitialSnapshot(RemoteModel remote, byte[] content, IProgressMonitor monitor) {
        if (monitor != null) {
            monitor.subTask("Saving snapshot"); //$NON-NLS-1$
        }
        try {
            var modelId = ConnectorProperties.extractModelId(remote.selfUrl());
            SnapshotStore.INSTANCE.saveSnapshot(modelId, content);
        } catch (Exception e) {
            ConnectorPlugin.getInstance().getLog().error("Failed to save initial snapshot", e); //$NON-NLS-1$
        }
    }

    // -----------------------------------------------------------------------
    // Publish (create remote record + upload content)

    /**
     * Creates a new model entry on the server, uploads the current content,
     * sets {@link ConnectorProperties}, and saves a base snapshot.
     *
     * @param profile The connection profile to use.
     * @param model The model to publish.
     * @param name The name for the remote model.
     * @param description The description for the remote model.
     * @param monitor Progress monitor for updating the UI.
     * @return The created RemoteModel.
     * @throws Exception If the publish process fails.
     */
    public RemoteModel publishModel(ConnectionProfile profile, IArchimateModel model, String name, String description,
            IProgressMonitor monitor) throws Exception {
        var token = AuthService.INSTANCE.getValidAccessToken(profile);

        if (monitor != null) {
            monitor.subTask("Creating repository entry"); //$NON-NLS-1$
        }
        var remote = client.createModel(profile.getServerUrl(), token, name, description);

        if (monitor != null) {
            monitor.subTask("Uploading model content"); //$NON-NLS-1$
        }
        var content = ModelSerializer.INSTANCE.serialize(model);
        client.updateModelContent(token, remote.selfUrl(), content);

        ConnectorProperties.setProperty(model, ConnectorProperties.KEY_URL, remote.selfUrl());
        ConnectorProperties.setProperty(model, ConnectorProperties.KEY_LAST_MODIFICATION_DATE_TIME,
                remote.lastModified());
        IEditorModelManager.INSTANCE.saveModel(model);

        var modelId = ConnectorProperties.extractModelId(remote.selfUrl());
        SnapshotStore.INSTANCE.saveSnapshot(modelId, content);

        return remote;
    }

    // -----------------------------------------------------------------------
    // Export (upload to project)

    /**
     * Serializes the local model and exports it to the given project on the server.
     *
     * @param profile The connection profile to use.
     * @param model The model to export.
     * @param projectId The target project ID.
     * @param monitor Progress monitor for updating the UI.
     * @throws Exception If the export process fails.
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
        client.exportModel(profile.getServerUrl(), token, projectId, fileName, content);
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
     * @param profile The connection profile to use.
     * @return List of remote projects.
     * @throws Exception If a communication error occurs.
     */
    public List<RemoteProject> listProjects(ConnectionProfile profile) throws Exception {
        var token = AuthService.INSTANCE.getValidAccessToken(profile);
        return client.listProjects(profile.getServerUrl(), token);
    }

    // -----------------------------------------------------------------------
    // Delete

    /**
     * Deletes the specified remote model from the repository.
     *
     * @param profile The connection profile to use.
     * @param remote The remote model to delete.
     * @throws Exception If the deletion fails.
     */
    public void deleteModel(ConnectionProfile profile, RemoteModel remote) throws Exception {
        var token = AuthService.INSTANCE.getValidAccessToken(profile);
        client.deleteModel(token, remote.selfUrl());
    }

}

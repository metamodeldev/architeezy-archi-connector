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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.eclipse.core.runtime.IProgressMonitor;

import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.api.ArchiteezyClient;
import com.architeezy.archi.connector.api.CancelSignal;
import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.auth.ProfileStatus;
import com.architeezy.archi.connector.io.ModelSerializer;
import com.architeezy.archi.connector.io.SnapshotSupport;
import com.architeezy.archi.connector.io.TrackedModelStore;
import com.architeezy.archi.connector.model.ConnectorProperties;
import com.architeezy.archi.connector.model.IEditorModelManagerAdapter;

/**
 * Downloads a remote model, opens it in Archi, and stores the initial snapshot.
 *
 * Must be called from a background thread (Job / IRunnableWithProgress).
 */
@SuppressWarnings("java:S112")
public final class ModelImportService {

    private final ArchiteezyClient client;

    private final AuthService authService;

    private final ModelSerializer serializer;

    private final TrackedModelStore trackedModels;

    private final SnapshotSupport snapshotSupport;

    private final IEditorModelManagerAdapter editorModelManager;

    private final UiSynchronizer uiSync;

    /**
     * Creates an import service that uses the given collaborators.
     *
     * @param client HTTP client
     * @param authService provider of valid OAuth access tokens
     * @param serializer model serializer
     * @param trackedModels workspace metadata store for tracked models
     * @param snapshotSupport helper that saves base snapshots
     * @param editorModelManager editor-model manager adapter
     * @param uiSync UI-thread synchronizer used to open and save the model on
     *         the UI thread and propagate any exception back to the caller
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public ModelImportService(ArchiteezyClient client, AuthService authService, ModelSerializer serializer,
            TrackedModelStore trackedModels, SnapshotSupport snapshotSupport,
            IEditorModelManagerAdapter editorModelManager, UiSynchronizer uiSync) {
        this.client = client;
        this.authService = authService;
        this.serializer = serializer;
        this.trackedModels = trackedModels;
        this.snapshotSupport = snapshotSupport;
        this.editorModelManager = editorModelManager;
        this.uiSync = uiSync;
    }

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
        var token = authenticated ? authService.getValidAccessToken(profile) : null;
        CancelSignal cancel = monitor == null ? CancelSignal.NEVER : monitor::isCanceled;
        var content = client.getModelContent(token, remote.contentUrl(), cancel);

        var model = serializer.deserialize(content, targetFile);

        try {
            openAndConfigureModel(model, remote, authenticated);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(targetFile.toPath());
            } catch (IOException ignored) {
                // best-effort cleanup before rethrow
            }
            throw e;
        }

        if (authenticated) {
            snapshotSupport.saveSnapshotAfterConfigure(
                    model, ConnectorProperties.extractModelId(remote.selfUrl()), monitor);
        }

        return model;
    }

    private void openAndConfigureModel(IArchimateModel model, RemoteModel remote, boolean authenticated)
            throws Exception {
        uiSync.syncCall(() -> {
            editorModelManager.openModel(model);
            if (authenticated) {
                ConnectorProperties.setProperty(model, ConnectorProperties.KEY_URL, remote.selfUrl());
                trackedModels.setLastModified(
                        ConnectorProperties.extractModelId(remote.selfUrl()), remote.lastModified());
            }
            editorModelManager.saveModel(model);
            return null;
        });
    }

}

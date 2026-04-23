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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executor;

import org.eclipse.core.runtime.IProgressMonitor;

import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.api.ArchiteezyClient;
import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.io.ModelSerializer;
import com.architeezy.archi.connector.io.SnapshotSupport;
import com.architeezy.archi.connector.io.TrackedModelStore;
import com.architeezy.archi.connector.model.ConnectorProperties;
import com.architeezy.archi.connector.model.IEditorModelManagerAdapter;

/**
 * Uploads a local model as a new file to an Architeezy project.
 *
 * Must be called from a background thread (Job / IRunnableWithProgress).
 */
@SuppressWarnings("java:S112")
public final class ModelExportService {

    private static final DateTimeFormatter EXPORT_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"); //$NON-NLS-1$

    private final ArchiteezyClient client;

    private final AuthService authService;

    private final ModelSerializer serializer;

    private final TrackedModelStore trackedModels;

    private final SnapshotSupport snapshotSupport;

    private final IEditorModelManagerAdapter editorModelManager;

    private final Executor uiExecutor;

    /**
     * Creates an export service that uses the given collaborators.
     *
     * @param client HTTP client
     * @param authService provider of valid OAuth access tokens
     * @param serializer model serializer
     * @param trackedModels workspace metadata store for tracked models
     * @param snapshotSupport helper that saves base snapshots
     * @param editorModelManager editor-model manager adapter used to save the
     *         model after the URL property is set
     * @param uiExecutor synchronous executor used to set the tracking property
     *         and save the model on the UI thread; must block until the given
     *         runnable finishes (e.g. {@code Display.getDefault()::syncExec})
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public ModelExportService(ArchiteezyClient client, AuthService authService, ModelSerializer serializer,
            TrackedModelStore trackedModels, SnapshotSupport snapshotSupport,
            IEditorModelManagerAdapter editorModelManager, Executor uiExecutor) {
        this.client = client;
        this.authService = authService;
        this.serializer = serializer;
        this.trackedModels = trackedModels;
        this.snapshotSupport = snapshotSupport;
        this.editorModelManager = editorModelManager;
        this.uiExecutor = uiExecutor;
    }

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
        final var token = authService.getValidAccessToken(profile);
        SnapshotSupport.setSubTask(monitor, "Serializing model"); //$NON-NLS-1$
        var fileName = buildExportFileName(model);
        var content = serializer.serialize(model);
        SnapshotSupport.setSubTask(monitor, "Uploading model"); //$NON-NLS-1$
        var created = client.exportModel(profile.getServerUrl(), token, projectId, fileName, content);

        SnapshotSupport.setSubTask(monitor, "Updating metadata"); //$NON-NLS-1$
        var uiError = new Exception[1];
        uiExecutor.execute(() -> {
            try {
                ConnectorProperties.setProperty(model, ConnectorProperties.KEY_URL, created.selfUrl());
                editorModelManager.saveModel(model);
            } catch (Exception e) {
                uiError[0] = e;
            }
        });
        if (uiError[0] != null) {
            throw uiError[0];
        }

        var modelId = ConnectorProperties.extractModelId(created.selfUrl());
        if (modelId != null) {
            trackedModels.setLastModified(modelId, created.lastModified());
            snapshotSupport.saveSnapshotAfterConfigure(model, modelId, monitor);
        }
    }

    static String buildExportFileName(IArchimateModel model) {
        var base = model.getName() != null && !model.getName().isBlank()
                ? model.getName().replaceAll("[\\\\/:*?\"<>|]", "_") //$NON-NLS-1$ //$NON-NLS-2$
                : "model"; //$NON-NLS-1$
        return base + "-" + LocalDateTime.now().format(EXPORT_TS) + ".archimate"; //$NON-NLS-1$ //$NON-NLS-2$
    }

}

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
import org.eclipse.swt.widgets.Display;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.api.ArchiteezyClient;
import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.auth.ProfileStatus;
import com.architeezy.archi.connector.io.ModelSerializer;
import com.architeezy.archi.connector.io.SnapshotSupport;
import com.architeezy.archi.connector.io.TrackedModelStore;
import com.architeezy.archi.connector.model.ConnectorProperties;

/**
 * Downloads a remote model, opens it in Archi, and stores the initial snapshot.
 *
 * Must be called from a background thread (Job / IRunnableWithProgress).
 */
@SuppressWarnings({ "java:S6548", "java:S112" })
public final class ModelImportService {

    /** The singleton instance. */
    public static final ModelImportService INSTANCE = new ModelImportService();

    private final ArchiteezyClient client = new ArchiteezyClient();

    private ModelImportService() {
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
        var token = authenticated ? AuthService.INSTANCE.getValidAccessToken(profile) : null;
        var content = client.getModelContent(token, remote.contentUrl());

        var model = ModelSerializer.INSTANCE.deserialize(content, targetFile);

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
            SnapshotSupport.saveSnapshotAfterConfigure(
                    model, ConnectorProperties.extractModelId(remote.selfUrl()), monitor);
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
                TrackedModelStore.INSTANCE.setLastModified(
                        ConnectorProperties.extractModelId(remote.selfUrl()), remote.lastModified());
            }
            IEditorModelManager.INSTANCE.saveModel(model);
        } catch (Exception e) {
            uiError[0] = e;
        }
    }

}

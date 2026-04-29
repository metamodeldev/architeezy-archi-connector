/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.ui.handlers;

import java.text.MessageFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModel;
import com.architeezy.archi.connector.ConnectorPlugin;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.api.ApiException;
import com.architeezy.archi.connector.api.ArchiteezyClient;
import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.model.ConnectorProperties;
import com.architeezy.archi.connector.ui.dialogs.ProgressResultDialog;
import com.architeezy.archi.connector.util.UuidV5;

/**
 * Toolbar handler that opens the active tracked model in the user's default
 * browser. When a diagram is open in the active editor and belongs to the
 * target tracked model, the URL points at that specific representation;
 * otherwise it points at the model root.
 *
 * <p>The model URL is {@code {server}/{scope}/{project}/{version}/{model}}.
 * A representation appends {@code /{representationSlug}} unless it is both
 * the default and the root - that one is opened with no extra segment, to
 * mirror the front-end behaviour in {@code EditingContext.tsx}.
 *
 * <p>The diagram's representation slug is resolved on demand: the connector
 * recomputes the EObject UUID via {@link UuidV5} (matching the server's
 * {@code Generators.nameBasedGenerator(model.id).generate(archimateId)}) and
 * queries {@code /api/representations} by {@code targetObjectId}.
 */
public class OpenInBrowserHandler extends AbstractTrackedModelHandler {

    private static final String SLASH = "/"; //$NON-NLS-1$

    @Override
    protected boolean isEnabledForModel(IArchimateModel model) {
        return true;
    }

    @Override
    @SuppressWarnings("java:S3516")
    public Object execute(ExecutionEvent event) throws ExecutionException {
        var shell = HandlerUtil.getActiveShell(event);
        var model = getTargetModel(m -> true);
        var modelUrl = ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL);
        var serverUrl = ConnectorProperties.extractServerUrl(modelUrl);
        var modelId = ConnectorProperties.extractModelId(modelUrl);
        var profile = ConnectorPlugin.getInstance().services().profileRegistry().findProfileForServer(serverUrl);
        var diagramId = activeDiagramIdFor(event, model);

        runAuthenticated(model, shell, Messages.OpenInBrowserHandler_title,
                () -> fetchAndOpen(shell, serverUrl, modelId, diagramId, profile));
        return null;
    }

    /**
     * Returns the active diagram's Archi id when the active editor's diagram
     * belongs to the same tracked model we are about to open; otherwise
     * {@code null}, which makes the handler fall back to the model URL.
     *
     * @param event the current execution event
     * @param target the tracked model the handler will open
     * @return the diagram's Archi id, or {@code null} when no relevant
     *         diagram editor is active
     */
    private static String activeDiagramIdFor(ExecutionEvent event, IArchimateModel target) {
        var editor = HandlerUtil.getActiveEditor(event);
        if (editor == null || target == null) {
            return null;
        }
        var diagram = editor.getAdapter(IDiagramModel.class);
        if (diagram == null || diagram.getArchimateModel() != target) {
            return null;
        }
        var id = diagram.getId();
        return id == null || id.isBlank() ? null : id;
    }

    private static void fetchAndOpen(Shell shell, String serverUrl, String modelId, String diagramId,
            ConnectionProfile profile) {
        var urlRef = new AtomicReference<String>();
        var dialog = new ProgressResultDialog(shell, Messages.OpenInBrowserHandler_title,
                Messages.OpenInBrowserHandler_jobName, Messages.OpenInBrowserHandler_failed,
                monitor -> resolveUrl(serverUrl, modelId, diagramId, profile, urlRef));
        dialog.open();
        var url = urlRef.get();
        if (url != null) {
            Display.getDefault().asyncExec(() -> {
                if (!Program.launch(url)) {
                    MessageDialog.openError(shell, Messages.OpenInBrowserHandler_title,
                            MessageFormat.format(Messages.OpenInBrowserHandler_failed, url));
                }
            });
        }
    }

    @SuppressWarnings("java:S112")
    private static ProgressResultDialog.Outcome resolveUrl(String serverUrl, String modelId, String diagramId,
            ConnectionProfile profile, AtomicReference<String> urlRef) throws Exception {
        var services = ConnectorPlugin.getInstance().services();
        var token = services.authService().getValidAccessToken(profile);
        var remote = services.apiClient().getModel(serverUrl, token, modelId);
        var modelBaseUrl = buildBrowserUrl(serverUrl, remote.scopeSlug(), remote.projectSlug(),
                remote.projectVersion(), remote.slug());
        urlRef.set(appendDiagramSlugIfAvailable(modelBaseUrl, services.apiClient(), serverUrl, token, modelId,
                diagramId));
        return ProgressResultDialog.Outcome.silent();
    }

    /**
     * Looks up the representation that wraps {@code diagramId} and appends its
     * slug to {@code modelBaseUrl}. The default-root representation is opened
     * via the model URL with no extra segment (matches the front end's
     * navigation rule). When {@code diagramId} is {@code null} or the lookup
     * fails to find a match, the model URL is used unchanged.
     *
     * @param modelBaseUrl the {@code {server}/{scope}/{project}/{ver}/{model}} URL
     * @param client the API client used to resolve the representation slug
     * @param serverUrl base URL of the Architeezy server
     * @param token OAuth2 bearer token
     * @param modelId target model id (UUID string)
     * @param diagramId active diagram's Archi id, or {@code null}
     * @return the URL to launch in the browser
     */
    private static String appendDiagramSlugIfAvailable(String modelBaseUrl, ArchiteezyClient client,
            String serverUrl, String token, String modelId, String diagramId) {
        if (diagramId == null) {
            return modelBaseUrl;
        }
        UUID modelUuid;
        try {
            modelUuid = UUID.fromString(modelId);
        } catch (IllegalArgumentException e) {
            return modelBaseUrl;
        }
        var targetObjectId = UuidV5.nameUuid(modelUuid, diagramId);
        try {
            var representation = client.findRepresentationByTargetObjectId(serverUrl, token, modelId, targetObjectId);
            if (representation.isEmpty()) {
                return modelBaseUrl;
            }
            var r = representation.get();
            if (r.isDefault() && r.isRoot()) {
                return modelBaseUrl;
            }
            return r.slug() == null ? modelBaseUrl : modelBaseUrl + SLASH + r.slug();
        } catch (ApiException e) {
            // Lookup is best-effort: fall back to opening the model.
            return modelBaseUrl;
        }
    }

    static String buildBrowserUrl(String serverUrl, String scopeSlug, String projectSlug,
            String projectVersion, String slug) {
        return serverUrl + SLASH + scopeSlug + SLASH + projectSlug + SLASH + projectVersion + SLASH + slug;
    }

}

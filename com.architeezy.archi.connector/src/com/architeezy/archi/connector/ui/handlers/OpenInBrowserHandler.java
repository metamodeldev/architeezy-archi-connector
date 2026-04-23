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
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.ConnectorPlugin;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.model.ConnectorProperties;
import com.architeezy.archi.connector.ui.dialogs.ProgressResultDialog;

/**
 * Toolbar handler that opens the active tracked model in the user's default
 * browser using the Architeezy web URL. The URL is formed as
 * {@code {server}/{scopeSlug}/{projectSlug}/{projectVersion}/{slug}} and is
 * resolved on demand by fetching the model metadata.
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

        runAuthenticated(model, shell, Messages.OpenInBrowserHandler_title,
                () -> fetchAndOpen(shell, serverUrl, modelId, profile));
        return null;
    }

    private static void fetchAndOpen(Shell shell, String serverUrl, String modelId, ConnectionProfile profile) {
        var urlRef = new AtomicReference<String>();
        var dialog = new ProgressResultDialog(shell, Messages.OpenInBrowserHandler_title,
                Messages.OpenInBrowserHandler_jobName, Messages.OpenInBrowserHandler_failed,
                monitor -> resolveUrl(serverUrl, modelId, profile, urlRef));
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
    private static ProgressResultDialog.Outcome resolveUrl(String serverUrl, String modelId,
            ConnectionProfile profile, AtomicReference<String> urlRef) throws Exception {
        var services = ConnectorPlugin.getInstance().services();
        var token = services.authService().getValidAccessToken(profile);
        var remote = services.apiClient().getModel(serverUrl, token, modelId);
        var url = buildBrowserUrl(serverUrl, remote.scopeSlug(), remote.projectSlug(),
                remote.projectVersion(), remote.slug());
        if (url == null) {
            throw new IllegalStateException(Messages.OpenInBrowserHandler_missingSlugs);
        }
        urlRef.set(url);
        return ProgressResultDialog.Outcome.silent();
    }

    @SuppressWarnings("checkstyle:BooleanExpressionComplexity")
    static String buildBrowserUrl(String serverUrl, String scopeSlug, String projectSlug,
            String projectVersion, String slug) {
        if (serverUrl == null || scopeSlug == null || projectSlug == null
                || projectVersion == null || slug == null) {
            return null;
        }
        return serverUrl + SLASH + scopeSlug + SLASH + projectSlug + SLASH + projectVersion + SLASH + slug;
    }

}

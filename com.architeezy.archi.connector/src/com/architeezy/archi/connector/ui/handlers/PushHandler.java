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

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.ConnectorPlugin;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.services.LocalChangeService;
import com.architeezy.archi.connector.services.ModelSyncService;
import com.architeezy.archi.connector.ui.dialogs.ProgressResultDialog;

/**
 * Toolbar handler that uploads the active model's local changes to the
 * Architeezy server, pulling any remote updates first if necessary.
 */
public class PushHandler extends AbstractTrackedModelHandler {

    private final Runnable changeListener = this::refreshEnabled;

    /** Creates the handler and subscribes to local change notifications. */
    public PushHandler() {
        localChangeService().addListener(changeListener);
    }

    private static LocalChangeService localChangeService() {
        return ConnectorPlugin.getInstance().services().localChangeService();
    }

    private static ModelSyncService modelSyncService() {
        return ConnectorPlugin.getInstance().services().modelSyncService();
    }

    @Override
    protected boolean isEnabledForModel(IArchimateModel model) {
        return localChangeService().hasLocalChanges(model);
    }

    @Override
    @SuppressWarnings("java:S3516")
    public Object execute(ExecutionEvent event) throws ExecutionException {
        var shell = HandlerUtil.getActiveShell(event);
        var model = getTargetModel(localChangeService()::hasLocalChanges);
        if (model == null) {
            MessageDialog.openInformation(shell, Messages.PushHandler_title, Messages.PushHandler_noModel);
            return null;
        }
        try {
            IEditorModelManager.INSTANCE.saveModel(model);
        } catch (java.io.IOException e) {
            Platform.getLog(PushHandler.class).error("Save before push failed", e); //$NON-NLS-1$
            MessageDialog.openError(shell, Messages.PushHandler_title,
                    MessageFormat.format(Messages.PushHandler_failed, e.getMessage()));
            return null;
        }

        runAuthenticated(model, shell, Messages.PushHandler_title, () -> {
            var dialog = new ProgressResultDialog(shell, Messages.PushHandler_title,
                    Messages.PushHandler_jobName, Messages.PushHandler_failed,
                    monitor -> {
                        modelSyncService().pushModel(model, monitor);
                        return ProgressResultDialog.Outcome.silent();
                    });
            dialog.open();
        });
        return null;
    }

    @Override
    public void dispose() {
        localChangeService().removeListener(changeListener);
        super.dispose();
    }

}

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
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.ConnectorPlugin;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.model.PushOutcome;
import com.architeezy.archi.connector.services.LocalChangeService;
import com.architeezy.archi.connector.services.ModelSyncService;
import com.architeezy.archi.connector.services.PendingConflict;
import com.architeezy.archi.connector.ui.dialogs.ConflictResolutionDialog;
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

        runAuthenticated(model, shell, Messages.PushHandler_title, () -> runPush(model, shell));
        return null;
    }

    private static void runPush(IArchimateModel model, Shell shell) {
        var pendingRef = new AtomicReference<PendingConflict>();
        var dialog = new ProgressResultDialog(shell, Messages.PushHandler_title,
                Messages.PushHandler_jobName, Messages.PushHandler_failed,
                monitor -> runPushTask(model, monitor, pendingRef));
        dialog.open();
        var pending = pendingRef.get();
        if (pending != null) {
            openConflictDialog(model, shell, pending);
        }
    }

    private static ProgressResultDialog.Outcome runPushTask(IArchimateModel model, IProgressMonitor monitor,
            AtomicReference<PendingConflict> pendingRef) throws Exception {
        var result = modelSyncService().pushModel(model, monitor);
        if (result.outcome() == PushOutcome.CONFLICT_PENDING) {
            pendingRef.set(result.pending());
        }
        return ProgressResultDialog.Outcome.silent();
    }

    private static void openConflictDialog(IArchimateModel model, Shell shell, PendingConflict pending) {
        var dialog = new ConflictResolutionDialog(shell, pending.comparison(), pending.localResource(),
                pending.mergerRegistry(),
                ConnectorPlugin.getInstance().services().modelSerializer());
        if (dialog.open() != org.eclipse.jface.window.Window.OK) {
            return;
        }
        if (dialog.getMergeError() != null) {
            var e = dialog.getMergeError();
            Platform.getLog(PushHandler.class).error("Conflict resolution failed", e); //$NON-NLS-1$
            MessageDialog.openError(shell, Messages.PushHandler_title,
                    MessageFormat.format(Messages.PushHandler_failed, e.getMessage()));
            return;
        }
        var merged = dialog.getMergedContent();
        if (merged == null) {
            return;
        }
        var applyDialog = new ProgressResultDialog(shell, Messages.PushHandler_title,
                Messages.PushHandler_jobName, Messages.PushHandler_failed,
                monitor -> {
                    modelSyncService().applyMergedPush(model, merged, pending, monitor);
                    return ProgressResultDialog.Outcome.silent();
                });
        applyDialog.open();
    }

    @Override
    public void dispose() {
        localChangeService().removeListener(changeListener);
        super.dispose();
    }

}

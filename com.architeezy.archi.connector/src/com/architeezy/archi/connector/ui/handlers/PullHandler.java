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
import com.architeezy.archi.connector.model.PullOutcome;
import com.architeezy.archi.connector.services.ModelSyncService;
import com.architeezy.archi.connector.services.PendingConflict;
import com.architeezy.archi.connector.ui.dialogs.ConflictResolutionDialog;
import com.architeezy.archi.connector.ui.dialogs.ProgressResultDialog;

/**
 * Toolbar handler that downloads the latest model content from the Architeezy
 * server and overwrites the local copy without merging.
 */
public class PullHandler extends AbstractTrackedModelHandler {

    private static ModelSyncService modelSyncService() {
        return ConnectorPlugin.getInstance().services().modelSyncService();
    }

    @Override
    protected boolean isEnabledForModel(IArchimateModel model) {
        return true;
    }

    @Override
    @SuppressWarnings("java:S3516")
    public Object execute(ExecutionEvent event) throws ExecutionException {
        var shell = HandlerUtil.getActiveShell(event);
        var model = getTargetModel(m -> true);
        if (model == null) {
            MessageDialog.openInformation(shell, Messages.PullHandler_title, Messages.PullHandler_noModel);
            return null;
        }
        try {
            IEditorModelManager.INSTANCE.saveModel(model);
        } catch (java.io.IOException e) {
            Platform.getLog(PullHandler.class).error("Save before pull failed", e); //$NON-NLS-1$
            MessageDialog.openError(shell, Messages.PullHandler_title,
                    MessageFormat.format(Messages.PullHandler_failed, e.getMessage()));
            return null;
        }

        runAuthenticated(model, shell, Messages.PullHandler_title, () -> runPull(model, shell));
        return null;
    }

    private static void runPull(IArchimateModel model, Shell shell) {
        var pendingRef = new AtomicReference<PendingConflict>();
        var dialog = new ProgressResultDialog(shell, Messages.PullHandler_title,
                Messages.PullHandler_jobName, Messages.PullHandler_failed,
                monitor -> runPullTask(model, monitor, pendingRef));
        dialog.open();
        var pending = pendingRef.get();
        if (pending != null) {
            openConflictDialog(model, shell, pending);
        }
    }

    private static ProgressResultDialog.Outcome runPullTask(IArchimateModel model, IProgressMonitor monitor,
            AtomicReference<PendingConflict> pendingRef) throws Exception {
        var result = modelSyncService().pullModel(model, monitor);
        if (result.outcome() == PullOutcome.CONFLICT_PENDING) {
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
            Platform.getLog(PullHandler.class).error("Conflict resolution failed", e); //$NON-NLS-1$
            MessageDialog.openError(shell, Messages.PullHandler_title,
                    MessageFormat.format(Messages.PullHandler_failed, e.getMessage()));
            return;
        }
        var merged = dialog.getMergedContent();
        if (merged == null) {
            return;
        }
        var applyDialog = new ProgressResultDialog(shell, Messages.PullHandler_title,
                Messages.PullHandler_jobName, Messages.PullHandler_failed,
                monitor -> {
                    modelSyncService().applyMergedPull(model, merged, pending, monitor);
                    return ProgressResultDialog.Outcome.silent();
                });
        applyDialog.open();
    }

}

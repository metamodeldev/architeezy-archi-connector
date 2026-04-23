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
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobFunction;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.ConnectorPlugin;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.services.ModelSyncService;
import com.architeezy.archi.connector.services.UpdateCheckService;

/**
 * Toolbar handler that downloads the latest model content from the Architeezy
 * server and overwrites the local copy without merging.
 *
 * <p>Enabled when any active editor or navigator-selected tracked model has
 * pending remote updates.
 */
public class PullHandler extends AbstractTrackedModelHandler {

    private final Runnable updateListener = this::refreshEnabled;

    /** Creates the handler and subscribes to remote update notifications. */
    public PullHandler() {
        UpdateCheckService.INSTANCE.addListener(updateListener);
    }

    @Override
    protected boolean isEnabledForModel(IArchimateModel model) {
        return UpdateCheckService.INSTANCE.hasUpdate(model);
    }

    @Override
    @SuppressWarnings("java:S3516")
    public Object execute(ExecutionEvent event) throws ExecutionException {
        var shell = HandlerUtil.getActiveShell(event);
        var model = getTargetModel(UpdateCheckService.INSTANCE::hasUpdate);
        if (model == null) {
            MessageDialog.openInformation(shell, Messages.PullHandler_title, Messages.PullHandler_noModel);
            return null;
        }
        if (!UpdateCheckService.INSTANCE.hasUpdate(model)) {
            MessageDialog.openInformation(shell, Messages.PullHandler_title, Messages.PullHandler_noUpdate);
            return null;
        }
        try {
            IEditorModelManager.INSTANCE.saveModel(model);
        } catch (java.io.IOException e) {
            ConnectorPlugin.getInstance().getLog().error("Save before pull failed", e); //$NON-NLS-1$
            MessageDialog.openError(shell, Messages.PullHandler_title,
                    MessageFormat.format(Messages.PullHandler_failed, e.getMessage()));
            return null;
        }

        runAuthenticated(model, shell, Messages.PullHandler_title, () -> {
            var job = Job.create(Messages.PullHandler_jobName,
                    (IJobFunction) monitor -> executePullJob(model, shell, monitor));
            job.setUser(true);
            job.schedule();
        });
        return null;
    }

    @Override
    public void dispose() {
        UpdateCheckService.INSTANCE.removeListener(updateListener);
        super.dispose();
    }

    private static IStatus executePullJob(IArchimateModel model, Shell shell, IProgressMonitor monitor) {
        var status = Status.OK_STATUS;
        try {
            var applied = ModelSyncService.INSTANCE.pullModel(model, monitor);
            if (applied) {
                Display.getDefault().asyncExec(() -> MessageDialog.openInformation(shell,
                        Messages.PullHandler_title, Messages.PullHandler_success));
            }
        } catch (Exception e) {
            ConnectorPlugin.getInstance().getLog().error("Pull failed", e); //$NON-NLS-1$
            status = Status.error(MessageFormat.format(Messages.PullHandler_failed, e.getMessage()), e);
            Display.getDefault().asyncExec(() -> MessageDialog.openError(shell, Messages.PullHandler_title,
                    MessageFormat.format(Messages.PullHandler_failed, e.getMessage())));
        }
        return status;
    }

}

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
import com.architeezy.archi.connector.services.LocalChangeService;
import com.architeezy.archi.connector.services.ModelSyncService;

/**
 * Toolbar handler that uploads the active model's local changes to the
 * Architeezy server, pulling any remote updates first if necessary.
 *
 * <p>Enabled when any active editor or navigator-selected tracked model has
 * local changes relative to its base snapshot.
 */
public class PushHandler extends AbstractTrackedModelHandler {

    private final Runnable changeListener = this::refreshEnabled;

    /** Creates the handler and subscribes to local change notifications. */
    public PushHandler() {
        LocalChangeService.INSTANCE.addListener(changeListener);
    }

    @Override
    protected boolean isEnabledForModel(IArchimateModel model) {
        return LocalChangeService.INSTANCE.hasLocalChanges(model);
    }

    @Override
    @SuppressWarnings("java:S3516")
    public Object execute(ExecutionEvent event) throws ExecutionException {
        var shell = HandlerUtil.getActiveShell(event);
        var model = getTargetModel(LocalChangeService.INSTANCE::hasLocalChanges);
        if (model == null) {
            MessageDialog.openInformation(shell, Messages.PushHandler_title, Messages.PushHandler_noModel);
            return null;
        }
        try {
            IEditorModelManager.INSTANCE.saveModel(model);
        } catch (java.io.IOException e) {
            ConnectorPlugin.getInstance().getLog().error("Save before push failed", e); //$NON-NLS-1$
            MessageDialog.openError(shell, Messages.PushHandler_title,
                    MessageFormat.format(Messages.PushHandler_failed, e.getMessage()));
            return null;
        }

        runAuthenticated(model, shell, Messages.PushHandler_title, () -> {
            var job = Job.create(Messages.PushHandler_jobName,
                    (IJobFunction) monitor -> executePushJob(model, shell, monitor));
            job.setUser(true);
            job.schedule();
        });
        return null;
    }

    @Override
    public void dispose() {
        LocalChangeService.INSTANCE.removeListener(changeListener);
        super.dispose();
    }

    private static IStatus executePushJob(IArchimateModel model, Shell shell, IProgressMonitor monitor) {
        var status = Status.OK_STATUS;
        try {
            ModelSyncService.INSTANCE.pushModel(model, monitor);
            Display.getDefault().asyncExec(() -> MessageDialog.openInformation(shell,
                    Messages.PushHandler_title, Messages.PushHandler_success));
        } catch (Exception e) {
            ConnectorPlugin.getInstance().getLog().error("Push failed", e); //$NON-NLS-1$
            status = Status.error(MessageFormat.format(Messages.PushHandler_failed, e.getMessage()), e);
            Display.getDefault().asyncExec(() -> MessageDialog.openError(shell, Messages.PushHandler_title,
                    MessageFormat.format(Messages.PushHandler_failed, e.getMessage())));
        }
        return status;
    }

}

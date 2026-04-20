/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.handlers;

import java.text.MessageFormat;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.architeezy.archi.connector.ConnectorPlugin;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.model.ConnectorProperties;
import com.architeezy.archi.connector.service.RepositoryService;
import com.architeezy.archi.connector.service.UpdateCheckService;

/**
 * Toolbar handler that downloads the latest model content from the Architeezy
 * server and overwrites the local copy without merging.
 *
 * <p>
 * Enabled when the active editor or the current navigator selection belongs
 * to a tracked model that has a newer version available on the server.
 */
public class PullFromArchiteezyHandler extends AbstractHandler {

    private IWorkbenchWindow window;

    private ISelectionListener selectionListener;

    private final Runnable updateListener = this::onUpdateStateChanged;

    /** Creates the handler and subscribes to update-state and selection changes. */
    public PullFromArchiteezyHandler() {
        UpdateCheckService.INSTANCE.addListener(updateListener);
        hookSelectionListener();
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        var shell = HandlerUtil.getActiveShell(event);
        var model = getActiveTrackedModel();
        if (model == null) {
            MessageDialog.openInformation(shell, Messages.PullHandler_title, Messages.PullHandler_noModel);
            return null;
        }
        if (!UpdateCheckService.INSTANCE.hasUpdate(model)) {
            MessageDialog.openInformation(shell, Messages.PullHandler_title, Messages.PullHandler_noUpdate);
            return null;
        }
        if (IEditorModelManager.INSTANCE.isModelDirty(model)) {
            var proceed = MessageDialog.openQuestion(shell,
                    Messages.PullHandler_title, Messages.PullHandler_dirtyConfirm);
            if (!proceed) {
                return null;
            }
        }

        var job = Job.create(Messages.PullHandler_jobName, (IProgressMonitor monitor) -> {
            IStatus status = Status.OK_STATUS;
            try {
                RepositoryService.INSTANCE.pullModel(model, monitor);
                Display.getDefault().asyncExec(() -> MessageDialog.openInformation(shell, Messages.PullHandler_title,
                        Messages.PullHandler_success));
            } catch (Exception e) {
                ConnectorPlugin.getInstance().getLog().error("Pull failed", e); //$NON-NLS-1$
                status = Status.error(MessageFormat.format(Messages.PullHandler_failed, e.getMessage()), e);
                Display.getDefault().asyncExec(() -> MessageDialog.openError(shell, Messages.PullHandler_title,
                        MessageFormat.format(Messages.PullHandler_failed, e.getMessage())));
            }
            return status;
        });
        job.setUser(true);
        job.schedule();
        return null;
    }

    @Override
    public boolean isEnabled() {
        var model = getActiveTrackedModel();
        return model != null && UpdateCheckService.INSTANCE.hasUpdate(model);
    }

    @Override
    public void dispose() {
        UpdateCheckService.INSTANCE.removeListener(updateListener);
        unhookSelectionListener();
        super.dispose();
    }

    // -----------------------------------------------------------------------
    // Active model resolution

    /**
     * Returns the tracked model from the active editor, or if no editor is
     * active, from the first selected element in any workbench view.
     *
     * @return the tracked model
     */
    private IArchimateModel getActiveTrackedModel() {
        var w = getWindow();
        if (w == null) {
            return null;
        }
        // 1. Try the active editor
        var page = w.getActivePage();
        if (page != null) {
            var editor = page.getActiveEditor();
            if (editor != null) {
                var model = editor.getAdapter(IArchimateModel.class);
                if (model != null && ConnectorProperties.isTracked(model)) {
                    return model;
                }
            }
        }
        // 2. Try the current workbench selection
        var selection = w.getSelectionService().getSelection();
        return modelFromSelection(selection);
    }

    private static IArchimateModel modelFromSelection(ISelection selection) {
        if (!(selection instanceof IStructuredSelection ss) || ss.isEmpty()) {
            return null;
        }
        for (var element : ss.toList()) {
            IArchimateModel model = null;
            if (element instanceof IArchimateModel m) {
                model = m;
            } else if (element instanceof IArchimateModelObject obj) {
                model = obj.getArchimateModel();
            }
            if (model != null && ConnectorProperties.isTracked(model)) {
                return model;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Enablement wiring

    private void hookSelectionListener() {
        var display = Display.getDefault();
        if (display == null) {
            return;
        }
        display.asyncExec(() -> {
            var w = getWindow();
            if (w == null) {
                return;
            }
            selectionListener = (IWorkbenchPart part, ISelection sel) -> setBaseEnabled(isEnabled());
            w.getSelectionService().addSelectionListener(selectionListener);
            window = w;
        });
    }

    private void unhookSelectionListener() {
        if (window != null && selectionListener != null) {
            window.getSelectionService().removeSelectionListener(selectionListener);
        }
    }

    private void onUpdateStateChanged() {
        var display = Display.getDefault();
        if (display != null && !display.isDisposed()) {
            display.asyncExec(() -> setBaseEnabled(isEnabled()));
        }
    }

    private static IWorkbenchWindow getWindow() {
        if (!PlatformUI.isWorkbenchRunning()) {
            return null;
        }
        return PlatformUI.getWorkbench().getActiveWorkbenchWindow();
    }

}

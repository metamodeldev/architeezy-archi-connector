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

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.model.ConnectorProperties;
import com.architeezy.archi.connector.ui.wizards.ExportWizard;

/**
 * Menu handler that opens the Export to Architeezy wizard.
 *
 * <p>
 * Enabled only when the active editor or the navigator selection refers to a
 * model that is not yet tracked by Architeezy (no url property).
 */
public class ExportMenuHandler extends AbstractHandler {

    private IWorkbenchWindow window;

    private ISelectionListener selectionListener;

    /** Creates the handler and subscribes to selection events. */
    public ExportMenuHandler() {
        hookSelectionListener();
    }

    @Override
    @SuppressWarnings("java:S3516")
    public Object execute(ExecutionEvent event) throws ExecutionException {
        var shell = HandlerUtil.getActiveShell(event);

        var model = getExportableModel();
        if (model == null) {
            MessageDialog.openInformation(shell, Messages.ExportHandler_title, Messages.ExportHandler_noModel);
            return null;
        }

        var wizard = new ExportWizard(model);
        var dialog = new WizardDialog(shell, wizard);
        dialog.open();
        return null;
    }

    @Override
    public boolean isEnabled() {
        return getExportableModel() != null;
    }

    @Override
    public void dispose() {
        unhookSelectionListener();
        super.dispose();
    }

    private IArchimateModel getExportableModel() {
        var w = getWindow();
        if (w == null) {
            return null;
        }
        var page = w.getActivePage();
        if (page != null) {
            var editor = page.getActiveEditor();
            if (editor != null) {
                var model = editor.getAdapter(IArchimateModel.class);
                if (model != null && !ConnectorProperties.isTracked(model)) {
                    return model;
                }
            }
        }
        return modelFromSelection(w.getSelectionService().getSelection());
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
            if (model != null && !ConnectorProperties.isTracked(model)) {
                return model;
            }
        }
        return null;
    }

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

    private static IWorkbenchWindow getWindow() {
        if (!PlatformUI.isWorkbenchRunning()) {
            return null;
        }
        var w = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (w != null) {
            return w;
        }
        var windows = PlatformUI.getWorkbench().getWorkbenchWindows();
        return windows.length > 0 ? windows[0] : null;
    }

}

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

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.wizard.ExportToArchiteezyWizard;

/** Menu handler that opens the Export to Architeezy wizard. */
public class ExportToArchiteezyMenuHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        var shell = HandlerUtil.getActiveShell(event);

        var model = getActiveModel();
        if (model == null) {
            MessageDialog.openInformation(shell, Messages.ExportHandler_title, Messages.ExportHandler_noModel);
            return null;
        }

        var wizard = new ExportToArchiteezyWizard(model);
        var dialog = new WizardDialog(shell, wizard);
        dialog.open();
        return null;
    }

    private IArchimateModel getActiveModel() {
        var editor = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow()
                .getActivePage()
                .getActiveEditor();
        if (editor == null) {
            return null;
        }
        return editor.getAdapter(IArchimateModel.class);
    }

}

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
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.handlers.HandlerUtil;

import com.architeezy.archi.connector.wizard.ImportFromArchiteezyWizard;

/** Menu handler that opens the Import from Architeezy wizard. */
public class ImportFromArchiteezyMenuHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        var wizard = new ImportFromArchiteezyWizard();
        var dialog = new WizardDialog(HandlerUtil.getActiveShell(event), wizard);
        dialog.open();
        return null;
    }

}

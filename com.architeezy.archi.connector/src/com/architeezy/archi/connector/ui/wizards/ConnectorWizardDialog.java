/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.ui.wizards;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;

/**
 * {@link WizardDialog} subclass that exposes mutators for the Finish and
 * Cancel buttons so that a terminal result page can swap Finish for Close
 * and disable Cancel once the operation has completed.
 */
public class ConnectorWizardDialog extends WizardDialog {

    /**
     * Creates the dialog.
     *
     * @param parentShell parent shell
     * @param newWizard the wizard to host
     */
    public ConnectorWizardDialog(Shell parentShell, IWizard newWizard) {
        super(parentShell, newWizard);
    }

    /**
     * Sets the text shown on the Finish button.
     *
     * @param label the new button text; ignored if the button is not available
     */
    public void setFinishButtonText(String label) {
        var button = getButton(IDialogConstants.FINISH_ID);
        if (button != null && !button.isDisposed() && label != null) {
            button.setText(label);
        }
    }

    /**
     * Enables or disables the Cancel button.
     *
     * @param enabled {@code true} to enable, {@code false} to disable
     */
    public void setCancelButtonEnabled(boolean enabled) {
        var button = getButton(IDialogConstants.CANCEL_ID);
        if (button != null && !button.isDisposed()) {
            button.setEnabled(enabled);
        }
    }
}

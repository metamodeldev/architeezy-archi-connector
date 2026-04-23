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

import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

/**
 * Terminal wizard page that reports the outcome of
 * {@link org.eclipse.jface.wizard.Wizard#performFinish()} inside the wizard
 * shell so no separate message dialog is needed.
 */
@SuppressWarnings("checkstyle:MagicNumber")
public class ResultWizardPage extends WizardPage {

    private final String successTitle;

    private final String errorTitle;

    private final String cancelledTitle;

    private Label iconLabel;

    private Label messageLabel;

    /**
     * Creates the result page.
     *
     * @param successTitle page title for successful completion
     * @param errorTitle page title for error completion
     * @param cancelledTitle page title when the user cancelled the operation
     */
    public ResultWizardPage(String successTitle, String errorTitle, String cancelledTitle) {
        super("resultPage"); //$NON-NLS-1$
        this.successTitle = successTitle;
        this.errorTitle = errorTitle;
        this.cancelledTitle = cancelledTitle;
        setPageComplete(true);
    }

    @Override
    public void createControl(Composite parent) {
        var container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(2, false));
        iconLabel = new Label(container, SWT.NONE);
        iconLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
        messageLabel = new Label(container, SWT.WRAP);
        var gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        gd.widthHint = 360;
        messageLabel.setLayoutData(gd);
        setControl(container);
    }

    /**
     * Populates the page with the given outcome. Safe to call after the controls
     * have been created.
     *
     * @param kind the kind of outcome to display
     * @param message the message text to show
     */
    public void showResult(Kind kind, String message) {
        String title = successTitle;
        int iconId = SWT.ICON_INFORMATION;
        if (kind == Kind.ERROR) {
            title = errorTitle;
            iconId = SWT.ICON_ERROR;
        } else if (kind == Kind.CANCELLED) {
            title = cancelledTitle;
            iconId = SWT.ICON_WARNING;
        }
        setTitle(title);
        setDescription(message);
        if (iconLabel != null && !iconLabel.isDisposed()) {
            iconLabel.setImage(iconLabel.getDisplay().getSystemImage(iconId));
            messageLabel.setText(message != null ? message : ""); //$NON-NLS-1$
            iconLabel.getParent().layout();
        }
    }

    /** Kind of terminal outcome shown to the user. */
    public enum Kind {
        /** Operation completed normally. */
        SUCCESS,
        /** Operation failed with an exception. */
        ERROR,
        /** User cancelled the operation. */
        CANCELLED
    }
}

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

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IPageChangeProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;

import com.architeezy.archi.connector.ConnectorPlugin;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.auth.ConnectionProfile;

/**
 * Wizard for importing a model from the Architeezy repository.
 */
public class ImportWizard extends Wizard implements IImportWizard {

    private ProfileSelectionPage profilePage;

    private ModelSelectionPage modelPage;

    private ResultWizardPage resultPage;

    private boolean cancelled;

    /**
     * Default constructor.
     */
    public ImportWizard() {
        setNeedsProgressMonitor(true);
        setWindowTitle(Messages.ImportWizard_title);
    }

    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        // Do nothing
    }

    @Override
    public void addPages() {
        profilePage = new ProfileSelectionPage();
        modelPage = new ModelSelectionPage();
        resultPage = new ResultWizardPage(Messages.ImportWizard_successTitle,
                Messages.ImportWizard_importFailed, Messages.ProgressDialog_cancelledTitle);
        addPage(profilePage);
        addPage(modelPage);
        addPage(resultPage);
    }

    @Override
    public void setContainer(IWizardContainer wizardContainer) {
        super.setContainer(wizardContainer);
        if (wizardContainer instanceof IPageChangeProvider provider) {
            provider.addPageChangedListener(event -> refreshButtonsForPage(event.getSelectedPage()));
        }
    }

    @Override
    public IWizardPage getNextPage(IWizardPage page) {
        if (page == modelPage) {
            return null;
        }
        return super.getNextPage(page);
    }

    @Override
    public boolean performFinish() {
        if (getContainer().getCurrentPage() == resultPage) {
            return true;
        }
        var profile = profilePage.getSelectedProfile();
        var remote = modelPage.getSelectedModel();
        var targetFile = modelPage.getTargetFile();

        cancelled = false;
        try {
            getContainer().run(true, true, monitor -> doImport(profile, remote, targetFile, monitor));
            resultPage.showResult(ResultWizardPage.Kind.SUCCESS,
                    NLS.bind(Messages.ImportWizard_success, remote.name()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            resultPage.showResult(ResultWizardPage.Kind.CANCELLED, Messages.ProgressDialog_cancelled);
        } catch (InvocationTargetException e) {
            final var root = e.getCause() != null ? e.getCause() : e;
            final var message = root.getMessage() != null && !root.getMessage().isBlank()
                    ? root.getMessage()
                    : root.getClass().getSimpleName();
            if (cancelled) {
                resultPage.showResult(ResultWizardPage.Kind.CANCELLED, Messages.ProgressDialog_cancelled);
            } else {
                resultPage.showResult(ResultWizardPage.Kind.ERROR,
                        MessageFormat.format(Messages.ImportWizard_importFailedMessage, message));
            }
        }
        getContainer().showPage(resultPage);
        return false;
    }

    private void refreshButtonsForPage(Object selectedPage) {
        if (!(getContainer() instanceof ConnectorWizardDialog dialog)) {
            return;
        }
        if (selectedPage == resultPage) {
            dialog.setFinishButtonText(Messages.ProgressDialog_close);
            dialog.setCancelButtonEnabled(false);
        } else {
            dialog.setFinishButtonText(IDialogConstants.FINISH_LABEL);
            dialog.setCancelButtonEnabled(true);
        }
    }

    private void doImport(ConnectionProfile profile, RemoteModel remote, File targetFile, IProgressMonitor monitor)
            throws InvocationTargetException {
        monitor.beginTask(NLS.bind(Messages.ImportWizard_importing, remote.name()), IProgressMonitor.UNKNOWN);
        try {
            ConnectorPlugin.getInstance().services().modelImportService()
                    .importModel(profile, remote, targetFile, monitor);
        } catch (Exception e) {
            if (monitor.isCanceled()) {
                cancelled = true;
            }
            throw new InvocationTargetException(e);
        } finally {
            monitor.done();
        }
    }

}

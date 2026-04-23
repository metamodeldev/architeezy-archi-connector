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
import org.eclipse.ui.IExportWizard;
import org.eclipse.ui.IWorkbench;

import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.ConnectorPlugin;
import com.architeezy.archi.connector.Messages;

/**
 * Wizard for exporting the active model to the Architeezy repository.
 */
public class ExportWizard extends Wizard implements IExportWizard {

    private final IArchimateModel model;

    private ProfileSelectionPage profilePage;

    private ProjectSelectionPage projectPage;

    private ResultWizardPage resultPage;

    private boolean cancelled;

    /**
     * Creates the wizard for exporting {@code model}.
     *
     * @param model the model to export
     */
    public ExportWizard(IArchimateModel model) {
        this.model = model;
        setNeedsProgressMonitor(true);
        setWindowTitle(Messages.ExportWizard_title);
    }

    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        // Do nothing
    }

    @Override
    public void addPages() {
        profilePage = new ProfileSelectionPage(true);
        projectPage = new ProjectSelectionPage();
        resultPage = new ResultWizardPage(Messages.ExportWizard_successTitle,
                Messages.ExportWizard_exportFailed, Messages.ProgressDialog_cancelledTitle);
        addPage(profilePage);
        addPage(projectPage);
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
        if (page == projectPage) {
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
        var project = projectPage.getSelectedProject();

        cancelled = false;
        try {
            getContainer().run(true, true, monitor -> doExport(profile, project, monitor));
            resultPage.showResult(ResultWizardPage.Kind.SUCCESS,
                    NLS.bind(Messages.ExportWizard_success, model.getName()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            resultPage.showResult(ResultWizardPage.Kind.CANCELLED, Messages.ProgressDialog_cancelled);
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            var message = cause != null && cause.getMessage() != null ? cause.getMessage() : e.getMessage();
            if (cancelled) {
                resultPage.showResult(ResultWizardPage.Kind.CANCELLED, Messages.ProgressDialog_cancelled);
            } else {
                resultPage.showResult(ResultWizardPage.Kind.ERROR,
                        MessageFormat.format(Messages.ExportWizard_exportFailedMessage, message));
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

    private void doExport(com.architeezy.archi.connector.auth.ConnectionProfile profile,
            com.architeezy.archi.connector.api.dto.RemoteProject project, IProgressMonitor monitor)
            throws InvocationTargetException {
        monitor.beginTask(NLS.bind(Messages.ExportWizard_exporting, model.getName()), IProgressMonitor.UNKNOWN);
        try {
            ConnectorPlugin.getInstance().services().modelExportService()
                    .exportModel(profile, model, project.id(), monitor);
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

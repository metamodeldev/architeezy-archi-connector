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

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
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
        addPage(profilePage);
        addPage(projectPage);
    }

    @Override
    public boolean performFinish() {
        var profile = profilePage.getSelectedProfile();
        var project = projectPage.getSelectedProject();

        try {
            getContainer().run(true, false, monitor -> doExport(profile, project, monitor));
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            MessageDialog.openError(getShell(), Messages.ExportWizard_exportFailed,
                    cause != null ? cause.getMessage() : e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }

    private void doExport(com.architeezy.archi.connector.auth.ConnectionProfile profile,
            com.architeezy.archi.connector.api.dto.RemoteProject project, IProgressMonitor monitor)
            throws InvocationTargetException {
        monitor.beginTask(NLS.bind(Messages.ExportWizard_exporting, model.getName()), IProgressMonitor.UNKNOWN);
        try {
            ConnectorPlugin.getInstance().services().modelExportService()
                    .exportModel(profile, model, project.id(), monitor);
        } catch (Exception e) {
            throw new InvocationTargetException(e);
        } finally {
            monitor.done();
        }
    }

}

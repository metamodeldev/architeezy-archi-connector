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

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;

import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.services.ModelImportService;

/**
 * Wizard for importing a model from the Architeezy repository.
 */
public class ImportWizard extends Wizard implements IImportWizard {

    private ProfileSelectionPage profilePage;

    private ModelSelectionPage modelPage;

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
        addPage(profilePage);
        addPage(modelPage);
    }

    @Override
    public boolean performFinish() {
        var profile = profilePage.getSelectedProfile();
        var remote = modelPage.getSelectedModel();
        var targetFile = modelPage.getTargetFile();

        try {
            getContainer().run(true, false, monitor -> doImport(profile, remote, targetFile, monitor));
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            MessageDialog.openError(getShell(), Messages.ImportWizard_importFailed,
                    cause != null ? cause.getMessage() : e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }

    private void doImport(ConnectionProfile profile, RemoteModel remote, File targetFile, IProgressMonitor monitor)
            throws InvocationTargetException {
        monitor.beginTask(NLS.bind(Messages.ImportWizard_importing, remote.name()), IProgressMonitor.UNKNOWN);
        try {
            ModelImportService.INSTANCE.importModel(profile, remote, targetFile, monitor);
        } catch (Exception e) {
            throw new InvocationTargetException(e);
        } finally {
            monitor.done();
        }
    }

}

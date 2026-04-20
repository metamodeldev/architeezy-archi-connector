/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.wizard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;

import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.api.RemoteProject;
import com.architeezy.archi.connector.service.AuthService;
import com.architeezy.archi.connector.service.RepositoryService;

/**
 * Wizard page for selecting the target project during model export.
 */
@SuppressWarnings("checkstyle:MagicNumber")
public class ProjectSelectionPage extends WizardPage {

    private Text searchField;

    private ListViewer listViewer;

    private List<RemoteProject> allProjects = Collections.emptyList();

    private Job loadJob;

    /**
     * Default constructor.
     */
    public ProjectSelectionPage() {
        super("projectSelectionPage"); //$NON-NLS-1$
        setTitle(Messages.ProjectPage_title);
        setDescription(Messages.ProjectPage_description);
        setPageComplete(false);
    }

    @Override
    public void createControl(Composite parent) {
        var container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));
        setControl(container);

        searchField = new Text(container, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL);
        searchField.setMessage(Messages.ProjectPage_searchPlaceholder);
        searchField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        searchField.addModifyListener((ModifyListener) e -> applyFilter(searchField.getText()));

        var list = new org.eclipse.swt.widgets.List(container, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
        var gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        gd.heightHint = 250;
        list.setLayoutData(gd);

        listViewer = new ListViewer(list);
        listViewer.setContentProvider(ArrayContentProvider.getInstance());
        listViewer.setLabelProvider(new LabelProvider() {

            @Override
            public String getText(Object element) {
                if (element instanceof RemoteProject p) {
                    return p.name() != null ? p.name() : p.id();
                }
                return super.getText(element);
            }

        });
        listViewer.addSelectionChangedListener(e -> {
            setPageComplete(getSelectedProject() != null);
        });
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            loadProjects();
        }
    }

    // -----------------------------------------------------------------------

    private void loadProjects() {
        var profile = AuthService.INSTANCE.getActiveProfile();
        if (profile == null) {
            setMessage(Messages.ProjectPage_noProfile, ERROR);
            return;
        }
        if (loadJob != null) {
            loadJob.cancel();
        }
        setMessage(Messages.ProjectPage_loading);
        listViewer.setInput(Collections.emptyList());
        loadJob = new Job(Messages.ProjectPage_loadingJob) {

            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    var projects = RepositoryService.INSTANCE.listProjects(profile);
                    Display.getDefault().asyncExec(() -> updateUiWithProjects(projects));
                } catch (Exception ex) {
                    Display.getDefault().asyncExec(() -> {
                        if (!getControl().isDisposed()) {
                            setMessage(NLS.bind(Messages.ProjectPage_loadError, ex.getMessage()), ERROR);
                        }
                    });
                }
                return Status.OK_STATUS;
            }

        };
        loadJob.schedule();
    }

    private void updateUiWithProjects(List<RemoteProject> projects) {
        if (getControl().isDisposed()) {
            return;
        }
        allProjects = projects;
        applyFilter(searchField.getText());
        setMessage(getDescription());
    }

    private void applyFilter(String query) {
        if (query == null || query.isBlank()) {
            listViewer.setInput(allProjects);
        } else {
            String lc = query.toLowerCase();
            List<RemoteProject> filtered = new ArrayList<>();
            for (RemoteProject p : allProjects) {
                if (p.name() != null && p.name().toLowerCase().contains(lc)) {
                    filtered.add(p);
                }
            }
            listViewer.setInput(filtered);
        }
    }

    // -----------------------------------------------------------------------
    // Public API

    /**
     * Returns the currently selected project, or {@code null} if none.
     *
     * @return the selected project, or {@code null}
     */
    public RemoteProject getSelectedProject() {
        var sel = (IStructuredSelection) listViewer.getSelection();
        if (sel == null || sel.isEmpty()) {
            return null;
        }
        var o = sel.getFirstElement();
        return o instanceof RemoteProject remoteProject ? remoteProject : null;
    }

}

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
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
import org.eclipse.swt.widgets.Text;

import com.architeezy.archi.connector.ConnectorPlugin;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.api.dto.PagedResult;
import com.architeezy.archi.connector.api.dto.RemoteProject;
import com.architeezy.archi.connector.auth.ConnectionProfile;

/**
 * Wizard page for selecting the target project during model export.
 */
@SuppressWarnings("checkstyle:MagicNumber")
public class ProjectSelectionPage extends WizardPage {

    private Text searchField;

    private ListViewer listViewer;

    private List<RemoteProject> allProjects = Collections.emptyList();

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
                if (element instanceof RemoteProject(String id, String name)) {
                    return name != null ? name : id;
                }
                return super.getText(element);
            }

        });
        listViewer.addSelectionChangedListener(e -> setPageComplete(getSelectedProject() != null));
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
        var profile = ConnectorPlugin.getInstance().services().profileRegistry().getActiveProfile();
        if (profile == null) {
            setMessage(Messages.ProjectPage_noProfile, ERROR);
            return;
        }
        setMessage(Messages.ProjectPage_loading);
        listViewer.setInput(Collections.emptyList());
        var loaded = new ArrayList<RemoteProject>();
        try {
            getContainer().run(true, true, monitor -> loadAllProjectPages(profile, loaded, monitor));
            updateUiWithProjects(loaded);
        } catch (InvocationTargetException e) {
            if (!getControl().isDisposed()) {
                var cause = e.getCause();
                setMessage(NLS.bind(Messages.ProjectPage_loadError,
                        cause != null ? cause.getMessage() : e.getMessage()), ERROR);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void loadAllProjectPages(ConnectionProfile profile, List<RemoteProject> out, IProgressMonitor monitor)
            throws InvocationTargetException {
        var progress = SubMonitor.convert(monitor, Messages.ProjectPage_loadingJob, 1);
        try {
            var service = ConnectorPlugin.getInstance().services().repositoryService();
            int page = 0;
            PagedResult<RemoteProject> result = service.listProjects(profile, page);
            out.addAll(result.items());
            int totalPages = Math.max(result.totalPages(), 1);
            progress.setWorkRemaining(totalPages);
            progress.worked(1);
            while (result.hasMore()) {
                if (progress.isCanceled()) {
                    throw new OperationCanceledException();
                }
                page++;
                result = service.listProjects(profile, page);
                out.addAll(result.items());
                progress.worked(1);
            }
        } catch (OperationCanceledException e) {
            throw e;
        } catch (Exception e) {
            throw new InvocationTargetException(e);
        }
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

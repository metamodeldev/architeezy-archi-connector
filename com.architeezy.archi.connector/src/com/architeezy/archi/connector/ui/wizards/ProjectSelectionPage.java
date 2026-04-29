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
import java.util.LinkedHashMap;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;

import com.architeezy.archi.connector.ConnectorPlugin;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.api.dto.PagedResult;
import com.architeezy.archi.connector.api.dto.RemoteProject;
import com.architeezy.archi.connector.auth.ConnectionProfile;

/**
 * Wizard page for selecting the target project during model export. Projects
 * are grouped by their owning scope so the tree is {@code scope → project}.
 */
@SuppressWarnings("checkstyle:MagicNumber")
public class ProjectSelectionPage extends WizardPage {

    private Text searchField;

    private TreeViewer treeViewer;

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

        var tree = new Tree(container, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
        var gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        gd.heightHint = 250;
        tree.setLayoutData(gd);

        treeViewer = new TreeViewer(tree);
        treeViewer.setContentProvider(new ProjectTreeContentProvider());
        treeViewer.setLabelProvider(new ProjectTreeLabelProvider());
        treeViewer.setComparator(new AlphabeticalComparator());
        treeViewer.addSelectionChangedListener(e -> setPageComplete(getSelectedProject() != null));
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
        treeViewer.setInput(Collections.emptyList());
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
        final List<RemoteProject> visible;
        if (query == null || query.isBlank()) {
            visible = allProjects;
        } else {
            String lc = query.toLowerCase();
            visible = new ArrayList<>();
            for (RemoteProject p : allProjects) {
                if (matches(p, lc)) {
                    visible.add(p);
                }
            }
        }
        treeViewer.setInput(buildTree(visible));
        treeViewer.expandAll();
    }

    /**
     * A project is kept when the query is contained in either its own name or
     * its scope's name, so a scope-name match brings every project in that
     * scope into view.
     *
     * @param p the project to test
     * @param lcQuery the search query, already lower-cased
     * @return {@code true} if the project name or its scope name contains the query
     */
    static boolean matches(RemoteProject p, String lcQuery) {
        return containsLc(p.name(), lcQuery) || containsLc(p.scopeName(), lcQuery);
    }

    private static boolean containsLc(String haystack, String lcNeedle) {
        return haystack != null && haystack.toLowerCase().contains(lcNeedle);
    }

    /**
     * Groups projects by their owning scope, preserving server order within
     * each group. Scopes without a usable label are bucketed under a
     * placeholder so projects are never silently dropped.
     *
     * @param projects the flat list of projects from the server
     * @return the root list of scope nodes
     */
    static List<ScopeNode> buildTree(List<RemoteProject> projects) {
        var groups = new LinkedHashMap<String, ScopeNode>();
        for (var p : projects) {
            var key = p.scopeId() != null ? p.scopeId()
                    : (p.scopeName() != null ? "name:" + p.scopeName() : "__none__"); //$NON-NLS-1$ //$NON-NLS-2$
            var label = p.scopeName() != null ? p.scopeName() : Messages.ProjectPage_unknownScope;
            groups.computeIfAbsent(key, k -> new ScopeNode(label, new ArrayList<>())).projects().add(p);
        }
        return new ArrayList<>(groups.values());
    }

    // -----------------------------------------------------------------------
    // Public API

    /**
     * Returns the currently selected project, or {@code null} if none.
     *
     * @return the selected project, or {@code null}
     */
    public RemoteProject getSelectedProject() {
        var sel = (IStructuredSelection) treeViewer.getSelection();
        if (sel == null || sel.isEmpty()) {
            return null;
        }
        var o = sel.getFirstElement();
        return o instanceof RemoteProject remoteProject ? remoteProject : null;
    }

    // -----------------------------------------------------------------------
    // Tree model + JFace providers

    /**
     * Scope grouping node holding the projects that belong to a single scope.
     *
     * @param label display text for the scope row
     * @param projects projects belonging to this scope, in server order
     */
    record ScopeNode(String label, List<RemoteProject> projects) {
    }

    private static final class ProjectTreeContentProvider implements ITreeContentProvider {

        @Override
        public Object[] getElements(Object inputElement) {
            return inputElement instanceof List<?> list ? list.toArray() : new Object[0];
        }

        @Override
        public Object[] getChildren(Object parentElement) {
            if (parentElement instanceof ScopeNode scope) {
                return scope.projects().toArray();
            }
            return new Object[0];
        }

        @Override
        public Object getParent(Object element) {
            return null;
        }

        @Override
        public boolean hasChildren(Object element) {
            return element instanceof ScopeNode scope && !scope.projects().isEmpty();
        }

    }

    private static final class AlphabeticalComparator extends ViewerComparator {

        @Override
        public int compare(Viewer viewer, Object e1, Object e2) {
            return compareNullable(label(e1), label(e2));
        }

        private static String label(Object o) {
            if (o instanceof ScopeNode s) {
                return s.label();
            }
            if (o instanceof RemoteProject p) {
                return p.name() != null ? p.name() : p.id();
            }
            return null;
        }

        private static int compareNullable(String a, String b) {
            if (a == null && b == null) {
                return 0;
            }
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            return a.compareToIgnoreCase(b);
        }

    }

    private static final class ProjectTreeLabelProvider extends LabelProvider {

        @Override
        public String getText(Object element) {
            if (element instanceof ScopeNode scope) {
                return scope.label();
            }
            if (element instanceof RemoteProject p) {
                return p.name() != null ? p.name() : p.id();
            }
            return super.getText(element);
        }

    }

}

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jface.layout.TreeColumnLayout;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;

import com.archimatetool.editor.ArchiPlugin;
import com.architeezy.archi.connector.ConnectorPlugin;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.api.dto.PagedResult;
import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.auth.ProfileStatus;
import com.architeezy.archi.connector.io.FileNames;
import com.architeezy.archi.connector.ui.wizards.ModelTreeNodes.ProjectNode;
import com.architeezy.archi.connector.ui.wizards.ModelTreeNodes.ScopeNode;
import com.architeezy.archi.connector.util.DateFormats;

/**
 * Page for selecting a remote model to import. Models are arranged in a
 * {@code scope -> project -> model} tree.
 */
@SuppressWarnings({ "checkstyle:MagicNumber", "checkstyle:ClassDataAbstractionCoupling",
        "checkstyle:ClassFanOutComplexity" })
public class ModelSelectionPage extends WizardPage {

    private static final String ARCHIMATE_EXTENSION = ".archimate";

    private EmptyHintBar emptyHint;

    private Text searchField;

    private TreeViewer treeViewer;

    private Text savePathText;

    private List<RemoteModel> allModels = Collections.emptyList();

    /**
     * Default constructor.
     */
    public ModelSelectionPage() {
        super("modelSelectionPage"); //$NON-NLS-1$
        setTitle(Messages.ModelPage_title);
        setDescription(Messages.ModelPage_description);
        setPageComplete(false);
    }

    @Override
    public void createControl(Composite parent) {
        var container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));
        setControl(container);

        emptyHint = new EmptyHintBar(container);
        createSearchBar(container);
        createTree(container);
        createSaveAsRow(container);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            loadModels();
        }
    }

    // -----------------------------------------------------------------------

    private void createSearchBar(Composite parent) {
        var search = new Text(parent, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL);
        search.setMessage(Messages.ModelPage_searchPlaceholder);
        search.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        this.searchField = search;
        search.addModifyListener((ModifyListener) e -> applyFilter(search.getText()));
    }

    private void createTree(Composite parent) {
        var treeHolder = new Composite(parent, SWT.NONE);
        var gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        gd.heightHint = 250;
        treeHolder.setLayoutData(gd);
        var treeLayout = new TreeColumnLayout();
        treeHolder.setLayout(treeLayout);

        var tree = new Tree(treeHolder, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE | SWT.V_SCROLL);
        tree.setHeaderVisible(true);
        tree.setLinesVisible(true);

        treeViewer = new TreeViewer(tree);
        treeViewer.setContentProvider(new ModelTreeContentProvider());

        var nameCol = new TreeViewerColumn(treeViewer, SWT.NONE);
        nameCol.getColumn().setText(Messages.ModelPage_columnName);
        treeLayout.setColumnData(nameCol.getColumn(), new ColumnWeightData(1, 200, true));
        nameCol.setLabelProvider(new NameColumnLabelProvider());

        var modifiedCol = new TreeViewerColumn(treeViewer, SWT.NONE);
        modifiedCol.getColumn().setText(Messages.ModelPage_columnLastModified);
        treeLayout.setColumnData(modifiedCol.getColumn(), new ColumnPixelData(200, true, false));
        modifiedCol.setLabelProvider(new LastModifiedColumnLabelProvider());

        treeViewer.addSelectionChangedListener(e -> onSelectionChanged());

        // Sort the leaf models within each project by name; clicking the column
        // header flips ascending/descending. Branch order keeps the server's
        // listing order.
        SelectionListener nameSort = SelectionListener.widgetSelectedAdapter(
                e -> toggleSort(nameCol.getColumn(), true));
        SelectionListener modifiedSort = SelectionListener.widgetSelectedAdapter(
                e -> toggleSort(modifiedCol.getColumn(), false));
        nameCol.getColumn().addSelectionListener(nameSort);
        modifiedCol.getColumn().addSelectionListener(modifiedSort);
        tree.setSortColumn(nameCol.getColumn());
        tree.setSortDirection(SWT.UP);
    }

    private void toggleSort(TreeColumn column, boolean byName) {
        var tree = treeViewer.getTree();
        boolean descending;
        if (tree.getSortColumn() == column) {
            descending = tree.getSortDirection() != SWT.DOWN;
        } else {
            descending = false;
        }
        tree.setSortColumn(column);
        tree.setSortDirection(descending ? SWT.DOWN : SWT.UP);
        ((ModelTreeContentProvider) treeViewer.getContentProvider()).setSort(byName, descending);
        treeViewer.refresh();
    }

    private void createSaveAsRow(Composite parent) {
        var row = new Composite(parent, SWT.NONE);
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        row.setLayout(new GridLayout(3, false));

        var lbl = new Label(row, SWT.NONE);
        lbl.setText(Messages.ModelPage_saveAs);

        savePathText = new Text(row, SWT.BORDER | SWT.READ_ONLY);
        savePathText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        savePathText.addModifyListener((ModifyListener) e -> refreshFileStatus());

        var browseBtn = new Button(row, SWT.PUSH);
        browseBtn.setText(Messages.ModelPage_browse);
        browseBtn.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> browse()));
    }

    // -----------------------------------------------------------------------

    private void loadModels() {
        var profile = ConnectorPlugin.getInstance().services().profileRegistry().getActiveProfile();
        if (profile == null) {
            setMessage(Messages.ModelPage_noProfile, ERROR);
            return;
        }
        setMessage(Messages.ModelPage_loading);
        treeViewer.setInput(Collections.emptyList());
        var loaded = new ArrayList<RemoteModel>();
        try {
            getContainer().run(true, true, monitor -> loadAllModelPages(profile, loaded, monitor));
            updateUiWithModels(loaded, profile);
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            handleLoadError(cause instanceof Exception ex ? ex : new RuntimeException(cause));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void loadAllModelPages(ConnectionProfile profile, List<RemoteModel> out, IProgressMonitor monitor)
            throws InvocationTargetException {
        var progress = SubMonitor.convert(monitor, Messages.ModelPage_loadingJob, 1);
        try {
            var service = ConnectorPlugin.getInstance().services().repositoryService();
            int page = 0;
            PagedResult<RemoteModel> result = service.listModels(profile, page);
            out.addAll(result.items());
            int totalPages = Math.max(result.totalPages(), 1);
            progress.setWorkRemaining(totalPages);
            progress.worked(1);
            while (result.hasMore()) {
                if (progress.isCanceled()) {
                    throw new OperationCanceledException();
                }
                page++;
                result = service.listModels(profile, page);
                out.addAll(result.items());
                progress.worked(1);
            }
        } catch (OperationCanceledException e) {
            throw e;
        } catch (Exception e) {
            throw new InvocationTargetException(e);
        }
    }

    private void updateUiWithModels(List<RemoteModel> models, ConnectionProfile profile) {
        if (getControl().isDisposed()) {
            return;
        }
        allModels = models;
        applyFilter(searchField.getText());
        emptyHint.show(models.isEmpty()
                ? NLS.bind(Messages.ModelPage_noModelsHint, profile.getServerUrl()) : null);
        if (profile.getStatus() == ProfileStatus.CONNECTED) {
            setMessage(getDescription());
        } else {
            setMessage(Messages.WizardMessages_notSignedIn, WARNING);
        }
    }

    private void handleLoadError(Exception ex) {
        if (!getControl().isDisposed()) {
            setMessage(NLS.bind(Messages.ModelPage_loadError, ex.getMessage()), ERROR);
        }
    }

    private void applyFilter(String query) {
        final List<RemoteModel> visible;
        if (query == null || query.isBlank()) {
            visible = allModels;
        } else {
            String lc = query.toLowerCase();
            visible = new ArrayList<>();
            for (var m : allModels) {
                if (matches(m, lc)) {
                    visible.add(m);
                }
            }
        }
        treeViewer.setInput(buildTree(visible));
        treeViewer.expandAll();
    }

    /**
     * A model is kept when the query is contained in any text on its path -
     * scope, project, or model. A hit anywhere up the path pulls the whole
     * branch into view, so e.g. typing a scope name lists every model under it.
     *
     * @param m the model to test
     * @param lcQuery the search query, already lower-cased
     * @return {@code true} if any name or slug along the model's path contains the query
     */
    static boolean matches(RemoteModel m, String lcQuery) {
        for (var s : new String[] { m.name(), m.slug(), m.projectName(), m.projectSlug(),
                m.scopeName(), m.scopeSlug() }) {
            if (containsLc(s, lcQuery)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsLc(String haystack, String lcNeedle) {
        return haystack != null && haystack.toLowerCase().contains(lcNeedle);
    }

    /**
     * Groups models into {@code scope -> project -> model}, preserving the
     * server-supplied ordering of the input list within each level.
     *
     * @param models the flat list of models from the server
     * @return the root list of scope nodes
     */
    static List<ScopeNode> buildTree(List<RemoteModel> models) {
        var scopes = new LinkedHashMap<String, ScopeNode>();
        for (var m : models) {
            var scopeKey = m.scopeSlug() != null ? m.scopeSlug()
                    : (m.scopeName() != null ? "name:" + m.scopeName() : "__none__"); //$NON-NLS-1$ //$NON-NLS-2$
            var scopeLabel = m.scopeName() != null ? m.scopeName()
                    : (m.scopeSlug() != null ? m.scopeSlug() : Messages.ModelPage_unknownScope);
            var scope = scopes.computeIfAbsent(scopeKey,
                    k -> new ScopeNode(scopeLabel, new LinkedHashMap<>()));

            var projectKey = (m.projectSlug() != null ? m.projectSlug() : "__none__") //$NON-NLS-1$
                    + "@" + (m.projectVersion() != null ? m.projectVersion() : ""); //$NON-NLS-1$ //$NON-NLS-2$
            var projectLabel = projectLabel(m);
            var project = scope.projects().computeIfAbsent(projectKey,
                    k -> new ProjectNode(projectLabel, new ArrayList<>()));
            project.models().add(m);
        }
        return new ArrayList<>(scopes.values());
    }

    private static String projectLabel(RemoteModel m) {
        var name = m.projectName() != null ? m.projectName()
                : (m.projectSlug() != null ? m.projectSlug() : Messages.ModelPage_unknownProject);
        return m.projectVersion() != null ? name + " (" + m.projectVersion() + ")" : name; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private void browse() {
        var selected = getSelectedModel();
        var fd = new FileDialog(getShell(), SWT.SAVE);
        fd.setText(Messages.ModelPage_saveDialogTitle);
        var current = getTargetFile();
        if (current != null) {
            var parent = current.getParentFile();
            if (parent != null) {
                fd.setFilterPath(parent.getAbsolutePath());
            }
            fd.setFileName(current.getName());
        } else {
            fd.setFilterPath(defaultSaveFolder().getAbsolutePath());
            if (selected != null) {
                fd.setFileName(defaultFileName(selected));
            }
        }
        fd.setFilterExtensions(new String[] { "*" + ARCHIMATE_EXTENSION, "*.*" }); //$NON-NLS-1$ //$NON-NLS-2$
        var path = fd.open();
        if (path != null) {
            if (!path.endsWith(ARCHIMATE_EXTENSION)) {
                path += ARCHIMATE_EXTENSION;
            }
            savePathText.setText(path);
        }
        updatePageComplete();
    }

    private void onSelectionChanged() {
        var model = getSelectedModel();
        if (model != null) {
            var path = new File(defaultSaveFolder(), defaultFileName(model));
            savePathText.setText(path.getAbsolutePath());
        }
        updatePageComplete();
    }

    private void updatePageComplete() {
        var complete = getSelectedModel() != null && !savePathText.getText().isBlank();
        setPageComplete(complete);
        refreshFileStatus();
    }

    private void refreshFileStatus() {
        if (getControl() == null || getControl().isDisposed()) {
            return;
        }
        var selected = getSelectedModel();
        if (selected != null && !selected.updatable()) {
            setMessage(Messages.ModelPage_readOnlyWarning, WARNING);
            return;
        }
        var file = getTargetFile();
        if (file != null && file.isFile()) {
            setMessage(NLS.bind(Messages.ModelPage_overwriteWarning, file.getName()), WARNING);
        } else if (selected != null) {
            setMessage(getDescription());
        }
    }

    private static File defaultSaveFolder() {
        File folder = null;
        try {
            folder = ArchiPlugin.getInstance().getUserDataFolder();
        } catch (Exception ignored) {
            // fall through
        }
        if (folder == null) {
            folder = new File(System.getProperty("user.home"), "Documents/Archi"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (!folder.isDirectory()) {
            folder.mkdirs();
        }
        return folder;
    }

    private static String defaultFileName(RemoteModel model) {
        return String.join("-", //$NON-NLS-1$
                FileNames.sanitize(model.scopeSlug()),
                FileNames.sanitize(model.projectSlug()),
                FileNames.sanitize(model.projectVersion()),
                FileNames.sanitize(model.slug())) + ARCHIMATE_EXTENSION;
    }

    // -----------------------------------------------------------------------
    // Public API

    /**
     * Returns the currently selected remote model.
     *
     * @return the selected {@link RemoteModel}, or {@code null} if none is selected
     */
    public RemoteModel getSelectedModel() {
        var sel = (IStructuredSelection) treeViewer.getSelection();
        if (sel == null || sel.isEmpty()) {
            return null;
        }
        Object o = sel.getFirstElement();
        return o instanceof RemoteModel remoteModel ? remoteModel : null;
    }

    /**
     * Returns the target file for importing the model.
     *
     * @return the target {@link File}, or {@code null} if no valid path is
     *         specified
     */
    public File getTargetFile() {
        var path = savePathText.getText().trim();
        return path.isEmpty() ? null : new File(path);
    }

    // -----------------------------------------------------------------------
    // JFace label providers

    private static final class NameColumnLabelProvider extends ColumnLabelProvider {

        @Override
        public String getText(Object element) {
            if (element instanceof ScopeNode scope) {
                return scope.label();
            }
            if (element instanceof ProjectNode project) {
                return project.label();
            }
            if (element instanceof RemoteModel m) {
                var label = m.name() != null ? m.name() : m.id();
                return m.updatable() ? label : label + " " + Messages.ModelPage_readOnlySuffix; //$NON-NLS-1$
            }
            return ""; //$NON-NLS-1$
        }

    }

    private static final class LastModifiedColumnLabelProvider extends ColumnLabelProvider {

        @Override
        public String getText(Object element) {
            if (element instanceof RemoteModel m) {
                return DateFormats.formatIsoDateTime(m.lastModified());
            }
            return ""; //$NON-NLS-1$
        }

    }

}

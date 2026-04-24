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
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
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
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;

import com.archimatetool.editor.ArchiPlugin;
import com.architeezy.archi.connector.ConnectorPlugin;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.api.dto.PagedResult;
import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.auth.ProfileStatus;
import com.architeezy.archi.connector.io.FileNames;
import com.architeezy.archi.connector.util.DateFormats;

/**
 * Page for selecting a remote model to import.
 */
@SuppressWarnings({ "checkstyle:MagicNumber", "checkstyle:ClassDataAbstractionCoupling" })
public class ModelSelectionPage extends WizardPage {

    private static final String ARCHIMATE_EXTENSION = ".archimate";

    private Text searchField;

    private TableViewer tableViewer;

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

        createSearchBar(container);
        createTable(container);
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

    private void createTable(Composite parent) {
        var tableHolder = new Composite(parent, SWT.NONE);
        var gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        gd.heightHint = 250;
        tableHolder.setLayoutData(gd);
        var tableLayout = new TableColumnLayout();
        tableHolder.setLayout(tableLayout);

        var table = new Table(tableHolder, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE | SWT.V_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);

        var colName = new TableColumn(table, SWT.NONE);
        colName.setText(Messages.ModelPage_columnName);
        tableLayout.setColumnData(colName, new ColumnWeightData(1, 200, true));

        var colModified = new TableColumn(table, SWT.NONE);
        colModified.setText(Messages.ModelPage_columnLastModified);
        tableLayout.setColumnData(colModified, new ColumnPixelData(200, true, false));

        tableViewer = new TableViewer(table);
        tableViewer.setContentProvider(ArrayContentProvider.getInstance());
        tableViewer.setLabelProvider(new ModelTableLabelProvider());
        tableViewer.addSelectionChangedListener(e -> onSelectionChanged());

        var comparator = new ModelColumnComparator();
        tableViewer.setComparator(comparator);
        colName.addSelectionListener(SelectionListener.widgetSelectedAdapter(
                e -> toggleSort(comparator, colName, ModelColumnComparator.BY_NAME)));
        colModified.addSelectionListener(SelectionListener.widgetSelectedAdapter(
                e -> toggleSort(comparator, colModified, ModelColumnComparator.BY_MODIFIED)));
        table.setSortColumn(colName);
        table.setSortDirection(SWT.UP);
        comparator.setSort(ModelColumnComparator.BY_NAME, false);
    }

    private void toggleSort(ModelColumnComparator comparator, TableColumn column, int columnId) {
        var table = tableViewer.getTable();
        boolean descending;
        if (table.getSortColumn() == column) {
            descending = table.getSortDirection() != SWT.DOWN;
        } else {
            descending = false;
        }
        comparator.setSort(columnId, descending);
        table.setSortColumn(column);
        table.setSortDirection(descending ? SWT.DOWN : SWT.UP);
        tableViewer.refresh();
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
        tableViewer.setInput(Collections.emptyList());
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
        if (query == null || query.isBlank()) {
            tableViewer.setInput(allModels);
        } else {
            String lc = query.toLowerCase();
            var filtered = new ArrayList<RemoteModel>();
            for (var m : allModels) {
                if (m.name() != null && m.name().toLowerCase().contains(lc)) {
                    filtered.add(m);
                }
            }
            tableViewer.setInput(filtered);
        }
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
        var file = getTargetFile();
        if (file != null && file.isFile()) {
            setMessage(NLS.bind(Messages.ModelPage_overwriteWarning, file.getName()), WARNING);
        } else if (getSelectedModel() != null) {
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
        var sel = (IStructuredSelection) tableViewer.getSelection();
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

    private static final class ModelTableLabelProvider extends LabelProvider implements ITableLabelProvider {

        @Override
        public String getColumnText(Object element, int columnIndex) {
            if (!(element instanceof RemoteModel m)) {
                return ""; //$NON-NLS-1$
            }
            return switch (columnIndex) {
            case 0 -> m.name() != null ? m.name() : m.id();
            case 1 -> DateFormats.formatIsoDateTime(m.lastModified());
            default -> ""; //$NON-NLS-1$
            };
        }

        @Override
        public org.eclipse.swt.graphics.Image getColumnImage(Object element, int columnIndex) {
            return null;
        }

    }

    private static final class ModelColumnComparator extends ViewerComparator {

        static final int BY_NAME = 0;

        static final int BY_MODIFIED = 1;

        private static final Comparator<String> NULLS_LAST_CI = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);

        private int column = BY_NAME;

        private boolean descending;

        void setSort(int columnId, boolean desc) {
            this.column = columnId;
            this.descending = desc;
        }

        @Override
        public int compare(Viewer viewer, Object e1, Object e2) {
            if (!(e1 instanceof RemoteModel a) || !(e2 instanceof RemoteModel b)) {
                return 0;
            }
            int cmp = switch (column) {
            case BY_MODIFIED -> NULLS_LAST_CI.compare(a.lastModified(), b.lastModified());
            default -> NULLS_LAST_CI.compare(displayName(a), displayName(b));
            };
            return descending ? -cmp : cmp;
        }

        private static String displayName(RemoteModel m) {
            return m.name() != null ? m.name() : m.id();
        }

    }

}

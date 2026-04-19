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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;

import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.api.PagedResult;
import com.architeezy.archi.connector.api.RemoteModel;
import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.auth.ProfileStatus;
import com.architeezy.archi.connector.service.AuthService;
import com.architeezy.archi.connector.service.RepositoryService;

/**
 * Page for selecting a remote model to import.
 */
@SuppressWarnings({ "checkstyle:MagicNumber", "checkstyle:ClassDataAbstractionCoupling" })
public class ModelSelectionPage extends WizardPage {

    private static final String ARCHIMATE_EXTENSION = ".archimate";

    private static final int PAGE_SIZE = 100;

    private Text searchField;

    private TableViewer tableViewer;

    private Text savePathText;

    private List<RemoteModel> allModels = Collections.emptyList();

    private Job loadJob;

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
        var table = new Table(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE | SWT.V_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        var gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        gd.heightHint = 250;
        table.setLayoutData(gd);

        var colName = new TableColumn(table, SWT.NONE);
        colName.setText(Messages.ModelPage_columnName);
        colName.setWidth(260);

        var colAuthor = new TableColumn(table, SWT.NONE);
        colAuthor.setText(Messages.ModelPage_columnAuthor);
        colAuthor.setWidth(120);

        var colModified = new TableColumn(table, SWT.NONE);
        colModified.setText(Messages.ModelPage_columnLastModified);
        colModified.setWidth(140);

        tableViewer = new TableViewer(table);
        tableViewer.setContentProvider(ArrayContentProvider.getInstance());
        tableViewer.setLabelProvider(new ModelTableLabelProvider());
        tableViewer.addSelectionChangedListener(e -> onSelectionChanged());
    }

    private void createSaveAsRow(Composite parent) {
        var row = new Composite(parent, SWT.NONE);
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        row.setLayout(new GridLayout(3, false));

        var lbl = new Label(row, SWT.NONE);
        lbl.setText(Messages.ModelPage_saveAs);

        savePathText = new Text(row, SWT.BORDER | SWT.READ_ONLY);
        savePathText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        var browseBtn = new Button(row, SWT.PUSH);
        browseBtn.setText(Messages.ModelPage_browse);
        browseBtn.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> browse()));
    }

    // -----------------------------------------------------------------------

    private void loadModels() {
        var profile = AuthService.INSTANCE.getActiveProfile();
        if (profile == null) {
            setMessage(Messages.ModelPage_noProfile, ERROR);
            return;
        }
        if (loadJob != null) {
            loadJob.cancel();
        }
        setMessage(Messages.ModelPage_loading);
        tableViewer.setInput(Collections.emptyList());
        loadJob = createLoadModelsJob(profile);
        loadJob.schedule();
    }

    private Job createLoadModelsJob(ConnectionProfile profile) {
        return new Job(Messages.ModelPage_loadingJob) {

            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    PagedResult<RemoteModel> result = RepositoryService.INSTANCE.listModels(profile, 0, PAGE_SIZE);
                    Display.getDefault().asyncExec(() -> updateUiWithModels(result.items(), profile));
                } catch (Exception ex) {
                    Display.getDefault().asyncExec(() -> handleLoadError(ex));
                }
                return Status.OK_STATUS;
            }

        };
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
        if (selected != null) {
            fd.setFileName(sanitize(selected.name()) + ARCHIMATE_EXTENSION);
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
        if (model != null && (savePathText.getText().isBlank())) {
            // Pre-fill filename when user selects a model for the first time
            savePathText.setText(""); //$NON-NLS-1$
        }
        updatePageComplete();
    }

    private void updatePageComplete() {
        var complete = getSelectedModel() != null && !savePathText.getText().isBlank();
        setPageComplete(complete);
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

    private static String sanitize(String name) {
        if (name == null) {
            return "model"; //$NON-NLS-1$
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_"); //$NON-NLS-1$ //$NON-NLS-2$
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
            case 1 -> nvl(m.author());
            case 2 -> nvl(m.lastModified());
            default -> ""; //$NON-NLS-1$
            };
        }

        @Override
        public org.eclipse.swt.graphics.Image getColumnImage(Object element, int columnIndex) {
            return null;
        }

        private static String nvl(String s) {
            return s != null ? s : ""; //$NON-NLS-1$
        }

    }

}

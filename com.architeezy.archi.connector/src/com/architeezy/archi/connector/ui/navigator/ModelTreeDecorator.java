/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.ui.navigator;

import java.text.MessageFormat;

import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.views.tree.ITreeModelView;
import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.io.TrackedModelStore;
import com.architeezy.archi.connector.model.ConnectorProperties;
import com.architeezy.archi.connector.services.LocalChangeService;
import com.architeezy.archi.connector.services.UpdateCheckService;
import com.architeezy.archi.connector.util.DateFormats;

/**
 * Installs a wrapper {@link CellLabelProvider} on Archi's model tree viewer to
 * display a down-arrow indicator (&#x2193;) next to tracked models that have a
 * newer version available on the Architeezy server, and to supply tooltip text
 * with version details.
 */
public final class ModelTreeDecorator {

    private final UpdateCheckService updateCheckService;

    private final LocalChangeService localChangeService;

    private final TrackedModelStore trackedModels;

    private final Runnable updateListener = this::onUpdateStateChanged;

    private TreeViewer installedViewer;

    /**
     * Creates a decorator that pulls update/local-change state from the given services.
     *
     * @param updateCheckService remote-update tracker
     * @param localChangeService local-change tracker
     * @param trackedModels store exposing last-known remote modification times
     */
    public ModelTreeDecorator(UpdateCheckService updateCheckService, LocalChangeService localChangeService,
            TrackedModelStore trackedModels) {
        this.updateCheckService = updateCheckService;
        this.localChangeService = localChangeService;
        this.trackedModels = trackedModels;
    }

    // -----------------------------------------------------------------------
    // Install / uninstall

    /**
     * Registers the update listener and attempts an immediate viewer installation.
     * Must be called on the UI thread.
     */
    public void install() {
        updateCheckService.addListener(updateListener);
        localChangeService.addListener(updateListener);
        tryInstallViewer();
    }

    /** Removes the update-check listener and clears the installed viewer reference. */
    public void uninstall() {
        updateCheckService.removeListener(updateListener);
        localChangeService.removeListener(updateListener);
        installedViewer = null;
    }

    // -----------------------------------------------------------------------

    private void tryInstallViewer() {
        if (!PlatformUI.isWorkbenchRunning()) {
            return;
        }
        for (var window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
            var page = window.getActivePage();
            if (page == null) {
                continue;
            }
            var view = page.findView(ITreeModelView.ID);
            if (view instanceof ITreeModelView treeView) {
                installOnViewer((TreeViewer) treeView.getViewer());
                return;
            }
        }
    }

    private void installOnViewer(TreeViewer treeViewer) {
        if (treeViewer == installedViewer) {
            return;
        }
        ColumnViewer columnViewer = treeViewer;
        var original = (CellLabelProvider) columnViewer.getLabelProvider();
        columnViewer.setLabelProvider(new UpdateIndicatorLabelProvider(original));
        ColumnViewerToolTipSupport.enableFor(treeViewer);
        installedViewer = treeViewer;
    }

    private void onUpdateStateChanged() {
        var display = Display.getDefault();
        if (display == null || display.isDisposed()) {
            return;
        }
        display.asyncExec(this::refreshDecoratedViewer);
    }

    private void refreshDecoratedViewer() {
        if (installedViewer == null || installedViewer.getControl().isDisposed()) {
            installedViewer = null;
            tryInstallViewer();
        }
        var viewer = installedViewer;
        if (viewer == null || viewer.getControl().isDisposed()) {
            return;
        }
        for (var model : IEditorModelManager.INSTANCE.getModels()) {
            if (viewer.testFindItem(model) != null) {
                viewer.update(model, null);
            }
        }
    }

    // -----------------------------------------------------------------------

    private final class UpdateIndicatorLabelProvider extends CellLabelProvider {

        private final CellLabelProvider delegate;

        UpdateIndicatorLabelProvider(CellLabelProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public void update(ViewerCell cell) {
            delegate.update(cell);
            if (cell.getElement() instanceof IArchimateModel model && ConnectorProperties.isTracked(model)) {
                var text = cell.getText();
                var hasUpdate = updateCheckService.hasUpdate(model);
                var hasLocalChanges = localChangeService.hasLocalChanges(model);
                if (hasUpdate || hasLocalChanges) {
                    text += " ";
                    if (hasUpdate) {
                        text = text + "\u2193"; //$NON-NLS-1$
                    }
                    if (hasLocalChanges) {
                        text = text + "\u2191"; //$NON-NLS-1$
                    }
                    cell.setText(text);
                }
            }
        }

        @Override
        public String getToolTipText(Object element) {
            if (element instanceof IArchimateModel model) {
                var hasLocal = localChangeService.hasLocalChanges(model);
                var update = updateCheckService.getAvailableUpdate(model);
                var modelUrl = ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL);
                var localDate = trackedModels.getLastModified(
                        ConnectorProperties.extractModelId(modelUrl));

                if (hasLocal && update != null) {
                    return MessageFormat.format(Messages.Decorator_localAndRemoteTooltip,
                            DateFormats.formatIsoDateTime(update.lastModified()), DateFormats.formatIsoDateTime(localDate));
                }
                if (hasLocal) {
                    return MessageFormat.format(Messages.Decorator_localChangesTooltip,
                            DateFormats.formatIsoDateTime(localDate));
                }
                if (update != null) {
                    return MessageFormat.format(Messages.Decorator_updateTooltip,
                            DateFormats.formatIsoDateTime(update.lastModified()), DateFormats.formatIsoDateTime(localDate));
                }
            }
            return null;
        }

    }

}

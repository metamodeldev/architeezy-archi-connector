/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.navigator;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

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
import com.architeezy.archi.connector.model.ConnectorProperties;
import com.architeezy.archi.connector.service.LocalChangeService;
import com.architeezy.archi.connector.service.UpdateCheckService;

/**
 * Installs a wrapper {@link CellLabelProvider} on Archi's model tree viewer to
 * display a down-arrow indicator (&#x2193;) next to tracked models that have a
 * newer version available on the Architeezy server, and to supply tooltip text
 * with version details.
 */
public final class ModelTreeDecorator {

    /** The singleton instance. */
    public static final ModelTreeDecorator INSTANCE = new ModelTreeDecorator();

    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") //$NON-NLS-1$
            .withZone(ZoneId.systemDefault());

    private TreeViewer installedViewer;

    private final Runnable updateListener = this::onUpdateStateChanged;

    private ModelTreeDecorator() {
    }

    // -----------------------------------------------------------------------
    // Install / uninstall

    /**
     * Registers the update listener and attempts an immediate viewer installation.
     * If the tree view is not yet open, it will be installed when the first update
     * is detected (which happens after the workbench is fully initialized).
     *
     * Must be called on the UI thread.
     */
    public void install() {
        UpdateCheckService.INSTANCE.addListener(updateListener);
        LocalChangeService.INSTANCE.addListener(updateListener);
        tryInstallViewer();
    }

    /**
     * Removes the update-check listener and clears the installed viewer reference.
     */
    public void uninstall() {
        UpdateCheckService.INSTANCE.removeListener(updateListener);
        LocalChangeService.INSTANCE.removeListener(updateListener);
        installedViewer = null;
    }

    // -----------------------------------------------------------------------
    // Internal

    /**
     * Tries to find the tree viewer across all workbench windows and install on it.
     */
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
        // Cast to ColumnViewer so Java selects setLabelProvider(CellLabelProvider),
        // not the StructuredViewer overload setLabelProvider(IBaseLabelProvider).
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
        // Install on the viewer now if not yet done (tree may not have been
        // available when install() was first called)
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
    // Helpers

    static String formatDate(String iso) {
        if (iso == null || iso.isBlank()) {
            return "\u2014"; //$NON-NLS-1$
        }
        try {
            // Try offset-aware format first (e.g. 2026-04-19T08:30:00+03:00)
            return DISPLAY_FMT.format(OffsetDateTime.parse(iso).toInstant());
        } catch (DateTimeParseException e1) {
            try {
                // Fall back to UTC instant (e.g. 2026-04-19T08:30:00Z)
                return DISPLAY_FMT.format(Instant.parse(iso));
            } catch (DateTimeParseException e2) {
                return iso;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Label provider

    /**
     * Wraps the original provider, appending &#x2193; and a tooltip for tracked
     * models.
     */
    private static final class UpdateIndicatorLabelProvider extends CellLabelProvider {

        private final CellLabelProvider delegate;

        UpdateIndicatorLabelProvider(CellLabelProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public void update(ViewerCell cell) {
            delegate.update(cell);
            if (cell.getElement() instanceof IArchimateModel model && ConnectorProperties.isTracked(model)) {
                var text = cell.getText();
                var hasUpdate = UpdateCheckService.INSTANCE.hasUpdate(model);
                var hasLocalChanges = LocalChangeService.INSTANCE.hasLocalChanges(model);
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
                var hasLocal = LocalChangeService.INSTANCE.hasLocalChanges(model);
                var update = UpdateCheckService.INSTANCE.getAvailableUpdate(model);
                var localDate = ConnectorProperties.getProperty(model,
                        ConnectorProperties.KEY_LAST_MODIFICATION_DATE_TIME);

                if (hasLocal && update != null) {
                    return MessageFormat.format(Messages.Decorator_localAndRemoteTooltip,
                            formatDate(update.lastModified()), formatDate(localDate));
                }
                if (hasLocal) {
                    return MessageFormat.format(Messages.Decorator_localChangesTooltip,
                            formatDate(localDate));
                }
                if (update != null) {
                    return MessageFormat.format(Messages.Decorator_updateTooltip,
                            formatDate(update.lastModified()), formatDate(localDate));
                }
            }
            return null;
        }

    }

}

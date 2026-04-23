/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;

/**
 * Helpers for resolving tracked Archimate models from the current workbench
 * state (active editor, active view, navigator selection).
 */
public final class TrackedModels {

    /** View ID of the Archi model navigator. */
    private static final String TREE_MODEL_VIEW_ID = "com.archimatetool.editor.treeModelView"; //$NON-NLS-1$

    private TrackedModels() {
    }

    /**
     * Returns the first tracked candidate model that satisfies {@code filter},
     * or if none matches, the first tracked candidate, or {@code null}.
     *
     * @param filter condition the preferred candidate must satisfy
     * @return the preferred matching model, a fallback candidate, or null
     */
    public static IArchimateModel find(Predicate<IArchimateModel> filter) {
        IArchimateModel fallback = null;
        for (var model : getCandidates()) {
            if (filter.test(model)) {
                return model;
            }
            if (fallback == null) {
                fallback = model;
            }
        }
        return fallback;
    }

    /**
     * Returns {@code true} if any candidate tracked model satisfies the filter.
     *
     * @param filter the predicate to test
     * @return true if any candidate matches
     */
    public static boolean anyMatches(Predicate<IArchimateModel> filter) {
        for (var model : getCandidates()) {
            if (filter.test(model)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects tracked model candidates from the active editor, the active
     * part, and the Archi model navigator, in priority order.
     *
     * @return a list of tracked candidate models, possibly empty
     */
    public static List<IArchimateModel> getCandidates() {
        var result = new ArrayList<IArchimateModel>();
        var seen = new LinkedHashSet<IArchimateModel>();
        var w = getWindow();
        if (w == null) {
            return result;
        }
        var page = w.getActivePage();
        if (page != null) {
            var editor = page.getActiveEditor();
            if (editor != null) {
                addIfTracked(editor.getAdapter(IArchimateModel.class), seen, result);
            }
            var treeView = page.findView(TREE_MODEL_VIEW_ID);
            if (treeView != null) {
                addIfTracked(treeView.getAdapter(IArchimateModel.class), seen, result);
            }
            var activePart = page.getActivePart();
            if (activePart != null) {
                addIfTracked(activePart.getAdapter(IArchimateModel.class), seen, result);
            }
        }
        var selectionService = w.getSelectionService();
        addModelsFromSelection(selectionService.getSelection(TREE_MODEL_VIEW_ID), seen, result);
        addModelsFromSelection(selectionService.getSelection(), seen, result);
        return result;
    }

    private static void addModelsFromSelection(ISelection selection,
            Set<IArchimateModel> seen, List<IArchimateModel> result) {
        if (!(selection instanceof IStructuredSelection ss) || ss.isEmpty()) {
            return;
        }
        for (var element : ss.toList()) {
            IArchimateModel model = null;
            if (element instanceof IArchimateModel m) {
                model = m;
            } else if (element instanceof IArchimateModelObject obj) {
                model = obj.getArchimateModel();
            }
            addIfTracked(model, seen, result);
        }
    }

    private static void addIfTracked(IArchimateModel model,
            Set<IArchimateModel> seen, List<IArchimateModel> result) {
        if (model != null && ConnectorProperties.isTracked(model) && seen.add(model)) {
            result.add(model);
        }
    }

    private static IWorkbenchWindow getWindow() {
        if (!PlatformUI.isWorkbenchRunning()) {
            return null;
        }
        var w = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (w != null) {
            return w;
        }
        var windows = PlatformUI.getWorkbench().getWorkbenchWindows();
        return windows.length > 0 ? windows[0] : null;
    }

}

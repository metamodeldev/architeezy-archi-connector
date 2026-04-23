/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.ui.dialogs;

import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.jface.viewers.ITreeContentProvider;

import com.architeezy.archi.connector.model.diff.ConflictTreeNode;

/**
 * Content provider for the conflict-resolution tree. The input is expected to
 * be a {@code List<ConflictTreeNode>} of root nodes.
 *
 * <p>
 * Filtering by conflict presence is handled by
 * {@link ConflictTreeNode#visibleChildren(boolean)}; this class is just a
 * JFace adapter over that logic.
 */
public class ConflictTreeContentProvider implements ITreeContentProvider {

    private final BooleanSupplier showAll;

    /**
     * Instantiates a new conflict tree content provider.
     *
     * @param showAll supplier that returns {@code true} when all changed nodes
     *        should be visible, {@code false} to show only conflict paths
     */
    public ConflictTreeContentProvider(BooleanSupplier showAll) {
        this.showAll = showAll;
    }

    @Override
    public Object[] getElements(Object inputElement) {
        if (inputElement instanceof List<?> list) {
            return list.toArray();
        }
        return new Object[0];
    }

    @Override
    public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof ConflictTreeNode node) {
            return node.visibleChildren(showAll.getAsBoolean()).toArray();
        }
        return new Object[0];
    }

    @Override
    public Object getParent(Object element) {
        return null;
    }

    @Override
    public boolean hasChildren(Object element) {
        if (element instanceof ConflictTreeNode node) {
            return !node.visibleChildren(showAll.getAsBoolean()).isEmpty();
        }
        return false;
    }

}

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jface.viewers.ITreeContentProvider;

import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.ui.wizards.ModelTreeNodes.ProjectNode;
import com.architeezy.archi.connector.ui.wizards.ModelTreeNodes.ScopeNode;

/**
 * Tree content provider for the import wizard. Roots are scope nodes; their
 * children are project nodes; the leaves are {@link RemoteModel} entries.
 * Sorting applies to every level - branches are always sorted by their label,
 * leaves either by name or by last-modified timestamp depending on which
 * column is active.
 */
final class ModelTreeContentProvider implements ITreeContentProvider {

    private boolean sortByName = true;

    private boolean descending;

    void setSort(boolean byName, boolean desc) {
        this.sortByName = byName;
        this.descending = desc;
    }

    @Override
    public Object[] getElements(Object inputElement) {
        if (inputElement instanceof List<?> list) {
            return sortedScopes(list).toArray();
        }
        return new Object[0];
    }

    @Override
    public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof ScopeNode scope) {
            return sortedProjects(scope.projects().values()).toArray();
        }
        if (parentElement instanceof ProjectNode project) {
            return sortedModels(project.models()).toArray();
        }
        return new Object[0];
    }

    @Override
    public Object getParent(Object element) {
        return null;
    }

    @Override
    public boolean hasChildren(Object element) {
        if (element instanceof ScopeNode scope) {
            return !scope.projects().isEmpty();
        }
        if (element instanceof ProjectNode project) {
            return !project.models().isEmpty();
        }
        return false;
    }

    private List<ScopeNode> sortedScopes(List<?> input) {
        var copy = new ArrayList<ScopeNode>(input.size());
        for (var o : input) {
            if (o instanceof ScopeNode s) {
                copy.add(s);
            }
        }
        copy.sort((a, b) -> direction(compareNullable(a.label(), b.label())));
        return copy;
    }

    private List<ProjectNode> sortedProjects(Collection<ProjectNode> input) {
        var copy = new ArrayList<>(input);
        copy.sort((a, b) -> direction(compareNullable(a.label(), b.label())));
        return copy;
    }

    private List<RemoteModel> sortedModels(List<RemoteModel> models) {
        var copy = new ArrayList<>(models);
        copy.sort((a, b) -> {
            int cmp = sortByName ? compareNullable(displayName(a), displayName(b))
                    : compareNullable(a.lastModified(), b.lastModified());
            return direction(cmp);
        });
        return copy;
    }

    private int direction(int cmp) {
        return descending ? -cmp : cmp;
    }

    private static String displayName(RemoteModel m) {
        return m.name() != null ? m.name() : m.id();
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

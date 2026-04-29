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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.ui.wizards.ModelTreeNodes.ProjectNode;
import com.architeezy.archi.connector.ui.wizards.ModelTreeNodes.ScopeNode;

class ModelTreeContentProviderTests {

    private static RemoteModel model(String name, String scopeSlug, String scopeName,
            String projectSlug, String projectVersion, String projectName, String lastModified) {
        return new RemoteModel("id-" + name, name, null, null, lastModified, null, null,
                "slug-" + name, projectSlug, projectVersion, scopeSlug, projectName, scopeName);
    }

    @Test
    void scopesSortedAlphabeticallyByDefault() {
        var m1 = model("M1", "z-scope", "Zebra", "p", "1", "P", null);
        var m2 = model("M2", "a-scope", "Alpha", "p", "1", "P", null);
        var roots = ModelSelectionPage.buildTree(List.of(m1, m2));

        var provider = new ModelTreeContentProvider();
        var sorted = provider.getElements(roots);
        assertEquals("Alpha", ((ScopeNode) sorted[0]).label());
        assertEquals("Zebra", ((ScopeNode) sorted[1]).label());
    }

    @Test
    void scopesReversedWhenDescending() {
        var m1 = model("M1", "z-scope", "Zebra", "p", "1", "P", null);
        var m2 = model("M2", "a-scope", "Alpha", "p", "1", "P", null);
        var roots = ModelSelectionPage.buildTree(List.of(m1, m2));

        var provider = new ModelTreeContentProvider();
        provider.setSort(true, true);
        var sorted = provider.getElements(roots);
        assertEquals("Zebra", ((ScopeNode) sorted[0]).label());
        assertEquals("Alpha", ((ScopeNode) sorted[1]).label());
    }

    @Test
    void projectsAndModelsSortedWithinScope() {
        var m1 = model("Beta", "s", "Scope", "z", "1", "Zeta", "2026-01-02");
        var m2 = model("Alpha", "s", "Scope", "a", "1", "Alpha", "2026-01-01");
        var m3 = model("Gamma", "s", "Scope", "a", "1", "Alpha", "2026-01-03");
        var roots = ModelSelectionPage.buildTree(List.of(m1, m2, m3));

        var provider = new ModelTreeContentProvider();
        var scope = (ScopeNode) provider.getElements(roots)[0];

        var projects = provider.getChildren(scope);
        assertEquals(2, projects.length);
        assertEquals("Alpha (1)", ((ProjectNode) projects[0]).label());
        assertEquals("Zeta (1)", ((ProjectNode) projects[1]).label());

        var alphaModels = provider.getChildren(projects[0]);
        assertEquals("Alpha", ((RemoteModel) alphaModels[0]).name());
        assertEquals("Gamma", ((RemoteModel) alphaModels[1]).name());
    }

    @Test
    void modelsSortByLastModifiedWhenColumnSwitched() {
        var older = model("Older", "s", "Scope", "p", "1", "P", "2026-01-01");
        var newer = model("Newer", "s", "Scope", "p", "1", "P", "2026-06-01");
        var roots = ModelSelectionPage.buildTree(List.of(newer, older));

        var provider = new ModelTreeContentProvider();
        provider.setSort(false, false);
        var scope = (ScopeNode) provider.getElements(roots)[0];
        var project = provider.getChildren(scope)[0];
        var models = provider.getChildren(project);
        assertEquals("Older", ((RemoteModel) models[0]).name());
        assertEquals("Newer", ((RemoteModel) models[1]).name());

        provider.setSort(false, true);
        models = provider.getChildren(project);
        assertEquals("Newer", ((RemoteModel) models[0]).name());
        assertEquals("Older", ((RemoteModel) models[1]).name());
    }

}

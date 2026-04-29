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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.architeezy.archi.connector.api.dto.RemoteProject;

class ProjectSelectionPageBuildTreeTests {

    @Test
    void groupsProjectsByScope() {
        var p1 = new RemoteProject("p1", "Alpha", "s1", "Scope One");
        var p2 = new RemoteProject("p2", "Beta", "s1", "Scope One");
        var p3 = new RemoteProject("p3", "Gamma", "s2", "Scope Two");

        var groups = ProjectSelectionPage.buildTree(List.of(p1, p2, p3));

        assertEquals(2, groups.size());
        assertEquals("Scope One", groups.get(0).label());
        assertEquals(2, groups.get(0).projects().size());
        assertEquals("Scope Two", groups.get(1).label());
        assertEquals(1, groups.get(1).projects().size());
    }

    @Test
    void distinctScopesShareSameNameButDifferentIds() {
        var p1 = new RemoteProject("p1", "A", "s1", "Same");
        var p2 = new RemoteProject("p2", "B", "s2", "Same");
        var groups = ProjectSelectionPage.buildTree(List.of(p1, p2));
        assertEquals(2, groups.size());
    }

    @Test
    void searchMatchesProjectOrScope() {
        var p = new RemoteProject("p1", "Alpha", "s1", "ACME");
        assertTrue(ProjectSelectionPage.matches(p, "alpha"));
        assertTrue(ProjectSelectionPage.matches(p, "acme"));
        assertTrue(ProjectSelectionPage.matches(p, "ac"));
        assertTrue(!ProjectSelectionPage.matches(p, "zzz"));
    }

    @Test
    void projectsWithoutScopeAreBucketed() {
        var p = new RemoteProject("p", "Loose");
        var groups = ProjectSelectionPage.buildTree(List.of(p));
        assertEquals(1, groups.size());
        assertEquals(1, groups.get(0).projects().size());
    }

}

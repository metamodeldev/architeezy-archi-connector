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

import com.architeezy.archi.connector.api.dto.RemoteModel;

class ModelSelectionPageBuildTreeTests {

    private static RemoteModel model(String name, String scopeSlug, String scopeName,
            String projectSlug, String projectVersion, String projectName) {
        return new RemoteModel("id-" + name, name, null, null, null, null, null,
                "slug-" + name, projectSlug, projectVersion, scopeSlug, projectName, scopeName);
    }

    @Test
    void groupsModelsByScopeThenProject() {
        var m1 = model("M1", "acme", "ACME", "alpha", "1.0", "Alpha");
        var m2 = model("M2", "acme", "ACME", "alpha", "1.0", "Alpha");
        var m3 = model("M3", "acme", "ACME", "beta", "2.0", "Beta");
        var m4 = model("M4", "globex", "Globex", "gamma", "1.0", "Gamma");

        var roots = ModelSelectionPage.buildTree(List.of(m1, m2, m3, m4));

        assertEquals(2, roots.size());
        var acme = roots.get(0);
        assertEquals("ACME", acme.label());
        assertEquals(2, acme.projects().size());
        var alpha = acme.projects().values().iterator().next();
        assertEquals(2, alpha.models().size());
        assertTrue(alpha.label().contains("Alpha"));
        assertTrue(alpha.label().contains("1.0"));

        var globex = roots.get(1);
        assertEquals("Globex", globex.label());
        assertEquals(1, globex.projects().size());
    }

    @Test
    void searchMatchesAtAnyLevel() {
        final var m1 = model("Aardvark", "acme", "ACME", "alpha", "1.0", "Alpha");
        final var m2 = model("Bee", "globex", "Globex", "alpha", "1.0", "Alpha");
        final var m3 = model("Crow", "globex", "Globex", "beta", "1.0", "Beta Project");

        // Scope name match
        assertTrue(ModelSelectionPage.matches(m1, "acm"));
        assertTrue(ModelSelectionPage.matches(m1, "acme"));
        // Project name match
        assertTrue(ModelSelectionPage.matches(m2, "alpha"));
        assertTrue(ModelSelectionPage.matches(m3, "beta project"));
        // Model name match
        assertTrue(ModelSelectionPage.matches(m1, "aard"));
        // No match
        assertTrue(!ModelSelectionPage.matches(m1, "zzz"));
    }

    @Test
    void groupsModelsWithMissingParentInfoUnderPlaceholderBucket() {
        var m1 = model("Loose", null, null, null, null, null);
        var roots = ModelSelectionPage.buildTree(List.of(m1));
        assertEquals(1, roots.size());
        // Placeholder labels live in Messages — just assert the tree is well-formed.
        var bucket = roots.get(0);
        assertEquals(1, bucket.projects().size());
        assertEquals(1, bucket.projects().values().iterator().next().models().size());
    }

}

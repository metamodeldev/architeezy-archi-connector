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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.emf.compare.AttributeChange;
import org.eclipse.emf.compare.CompareFactory;
import org.eclipse.emf.compare.Conflict;
import org.eclipse.emf.compare.ConflictKind;
import org.eclipse.emf.compare.Diff;
import org.junit.jupiter.api.Test;

import com.architeezy.archi.connector.model.diff.ConflictTreeNode;

class ConflictTreeContentProviderTests {

    private static Diff realConflictDiff() {
        AttributeChange d = CompareFactory.eINSTANCE.createAttributeChange();
        Conflict c = CompareFactory.eINSTANCE.createConflict();
        c.setKind(ConflictKind.REAL);
        c.getDifferences().add(d);
        return d;
    }

    private static ConflictTreeNode node(String label, List<ConflictTreeNode> children,
            List<Diff> local) {
        return new ConflictTreeNode(null, label, local, List.of(), children,
                false, false, false, false);
    }

    private static ConflictTreeNode leaf(String label, List<Diff> local) {
        return node(label, List.of(), local);
    }

    // getElements ---------------------------------------------------------

    @Test
    void getElementsReturnsListContents() {
        var provider = new ConflictTreeContentProvider(() -> true);
        var roots = List.of(leaf("a", List.of()), leaf("b", List.of()));
        assertArrayEquals(roots.toArray(), provider.getElements(roots));
    }

    @Test
    void getElementsReturnsEmptyForNonList() {
        var provider = new ConflictTreeContentProvider(() -> true);
        assertEquals(0, provider.getElements("not a list").length);
    }

    @Test
    void getParentAlwaysNull() {
        var provider = new ConflictTreeContentProvider(() -> true);
        assertNull(provider.getParent(leaf("a", List.of())));
    }

    // getChildren / hasChildren in showAll=true ---------------------------

    @Test
    void showAllExposesAllChildren() {
        var cleanChild = leaf("clean", List.of());
        var conflictChild = leaf("conf", List.of(realConflictDiff()));
        var parent = node("p", List.of(cleanChild, conflictChild), List.of());

        var provider = new ConflictTreeContentProvider(() -> true);
        assertArrayEquals(new Object[] {cleanChild, conflictChild}, provider.getChildren(parent));
        assertTrue(provider.hasChildren(parent));
    }

    @Test
    void showAllHasChildrenFalseForEmptyNode() {
        var leaf = leaf("leaf", List.of());
        var provider = new ConflictTreeContentProvider(() -> true);
        assertFalse(provider.hasChildren(leaf));
    }

    // getChildren / hasChildren in showAll=false --------------------------

    @Test
    void showConflictsOnlyFiltersNonConflictSubtrees() {
        var cleanChild = leaf("clean", List.of());
        var conflictChild = leaf("conf", List.of(realConflictDiff()));
        var parent = node("p", List.of(cleanChild, conflictChild), List.of());

        var provider = new ConflictTreeContentProvider(() -> false);
        assertArrayEquals(new Object[] {conflictChild}, provider.getChildren(parent));
        assertTrue(provider.hasChildren(parent));
    }

    @Test
    void showConflictsOnlyHidesSubtreeWithNoConflict() {
        var cleanChild = leaf("clean", List.of());
        var parent = node("p", List.of(cleanChild), List.of());

        var provider = new ConflictTreeContentProvider(() -> false);
        assertEquals(0, provider.getChildren(parent).length);
        assertFalse(provider.hasChildren(parent));
    }

    @Test
    void showConflictsOnlyIncludesDescendantConflicts() {
        // grandchild has conflict; intermediate parent should be visible
        var gcConflict = leaf("gc", List.of(realConflictDiff()));
        var intermediate = node("mid", List.of(gcConflict), List.of());
        var parent = node("p", List.of(intermediate), List.of());

        var provider = new ConflictTreeContentProvider(() -> false);
        assertArrayEquals(new Object[] {intermediate}, provider.getChildren(parent));
        assertTrue(provider.hasChildren(parent));
        assertTrue(provider.hasChildren(intermediate));
    }

    @Test
    void getChildrenReturnsEmptyForNonNode() {
        var provider = new ConflictTreeContentProvider(() -> true);
        assertEquals(0, provider.getChildren("not a node").length);
    }

}

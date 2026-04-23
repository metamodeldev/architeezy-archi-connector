/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.model.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.emf.compare.AttributeChange;
import org.eclipse.emf.compare.CompareFactory;
import org.eclipse.emf.compare.Conflict;
import org.eclipse.emf.compare.ConflictKind;
import org.eclipse.emf.compare.Diff;
import org.junit.jupiter.api.Test;

class ConflictTreeNodeTests {

    private static Diff diffWithoutConflict() {
        return CompareFactory.eINSTANCE.createAttributeChange();
    }

    private static Diff diffWithConflict(ConflictKind kind) {
        AttributeChange diff = CompareFactory.eINSTANCE.createAttributeChange();
        Conflict conflict = CompareFactory.eINSTANCE.createConflict();
        conflict.setKind(kind);
        conflict.getDifferences().add(diff);
        return diff;
    }

    private static Diff realConflictDiff() {
        return diffWithConflict(ConflictKind.REAL);
    }

    private static ConflictTreeNode leaf(String label, List<Diff> local) {
        return new ConflictTreeNode(null, label, local, List.of(), List.of(),
                false, false, false, false);
    }

    private static ConflictTreeNode leaf(List<Diff> local, List<Diff> remote) {
        return new ConflictTreeNode(null, "n", local, remote, List.of(),
                false, false, false, false);
    }

    private static ConflictTreeNode structural(boolean addedLocal, boolean addedRemote,
            boolean deletedLocal, boolean deletedRemote) {
        return new ConflictTreeNode(null, "n", List.of(), List.of(), List.of(),
                addedLocal, addedRemote, deletedLocal, deletedRemote);
    }

    private static ConflictTreeNode withChildren(String label, List<ConflictTreeNode> children) {
        return new ConflictTreeNode(null, label, List.of(), List.of(), children,
                false, false, false, false);
    }

    // isConflict -----------------------------------------------------------

    @Test
    void isConflictFalseWithoutDiffs() {
        assertFalse(leaf(List.of(), List.of()).isConflict());
    }

    @Test
    void isConflictFalseForDiffWithoutConflict() {
        assertFalse(leaf(List.of(diffWithoutConflict()), List.of()).isConflict());
    }

    @Test
    void isConflictFalseForPseudoConflict() {
        assertFalse(leaf(List.of(diffWithConflict(ConflictKind.PSEUDO)), List.of()).isConflict());
    }

    @Test
    void isConflictTrueForRealConflictOnLocal() {
        assertTrue(leaf(List.of(diffWithConflict(ConflictKind.REAL)), List.of()).isConflict());
    }

    @Test
    void isConflictTrueForRealConflictOnRemote() {
        assertTrue(leaf(List.of(), List.of(diffWithConflict(ConflictKind.REAL))).isConflict());
    }

    // hasConflictInSubtree -------------------------------------------------

    @Test
    void hasConflictInSubtreeDetectsDescendant() {
        var child = leaf(List.of(diffWithConflict(ConflictKind.REAL)), List.of());
        var parent = new ConflictTreeNode(null, "p", List.of(), List.of(), List.of(child),
                false, false, false, false);
        assertTrue(parent.hasConflictInSubtree());
        assertFalse(parent.isConflict());
    }

    @Test
    void hasConflictInSubtreeFalseWhenNoRealConflict() {
        var child = leaf(List.of(diffWithoutConflict()), List.of());
        var parent = new ConflictTreeNode(null, "p", List.of(), List.of(), List.of(child),
                false, false, false, false);
        assertFalse(parent.hasConflictInSubtree());
    }

    // hasAnyChange ---------------------------------------------------------

    @Test
    void hasAnyChangeFalseForEmptyLeaf() {
        assertFalse(leaf(List.of(), List.of()).hasAnyChange());
    }

    @Test
    void hasAnyChangeTrueForLocalDiff() {
        assertTrue(leaf(List.of(diffWithoutConflict()), List.of()).hasAnyChange());
    }

    @Test
    void hasAnyChangeTrueForRemoteDiff() {
        assertTrue(leaf(List.of(), List.of(diffWithoutConflict())).hasAnyChange());
    }

    @Test
    void hasAnyChangeTrueForAddedLocal() {
        assertTrue(structural(true, false, false, false).hasAnyChange());
    }

    @Test
    void hasAnyChangeTrueForAddedRemote() {
        assertTrue(structural(false, true, false, false).hasAnyChange());
    }

    @Test
    void hasAnyChangeTrueForDeletedLocal() {
        assertTrue(structural(false, false, true, false).hasAnyChange());
    }

    @Test
    void hasAnyChangeTrueForDeletedRemote() {
        assertTrue(structural(false, false, false, true).hasAnyChange());
    }

    @Test
    void hasAnyChangeDetectsDescendantChange() {
        var child = structural(true, false, false, false);
        var parent = new ConflictTreeNode(null, "p", List.of(), List.of(), List.of(child),
                false, false, false, false);
        assertTrue(parent.hasAnyChange());
    }

    @Test
    void hasAnyChangeFalseWhenSubtreeEmpty() {
        var child = leaf(List.of(), List.of());
        var parent = new ConflictTreeNode(null, "p", List.of(), List.of(), List.of(child),
                false, false, false, false);
        assertFalse(parent.hasAnyChange());
    }

    // visibleChildren ------------------------------------------------------

    @Test
    void visibleChildrenShowAllReturnsEveryChild() {
        var cleanChild = leaf("clean", List.of());
        var conflictChild = leaf("conf", List.of(realConflictDiff()));
        var parent = withChildren("p", List.of(cleanChild, conflictChild));
        assertEquals(List.of(cleanChild, conflictChild), parent.visibleChildren(true));
    }

    @Test
    void visibleChildrenShowConflictsOnlyFiltersCleanSubtrees() {
        var cleanChild = leaf("clean", List.of());
        var conflictChild = leaf("conf", List.of(realConflictDiff()));
        var parent = withChildren("p", List.of(cleanChild, conflictChild));
        assertEquals(List.of(conflictChild), parent.visibleChildren(false));
    }

    @Test
    void visibleChildrenIncludesDescendantConflicts() {
        var gcConflict = leaf("gc", List.of(realConflictDiff()));
        var intermediate = withChildren("mid", List.of(gcConflict));
        var parent = withChildren("p", List.of(intermediate));
        assertEquals(List.of(intermediate), parent.visibleChildren(false));
    }

    @Test
    void visibleChildrenEmptyWhenNoConflictsAndHidden() {
        var cleanChild = leaf("clean", List.of());
        var parent = withChildren("p", List.of(cleanChild));
        assertTrue(parent.visibleChildren(false).isEmpty());
    }

    // collectConflictNodes -------------------------------------------------

    @Test
    void collectReturnsEmptyForNoConflicts() {
        var list = List.of(leaf("a", List.of()), leaf("b", List.of()));
        assertTrue(ConflictTreeNode.collectConflictNodes(list).isEmpty());
    }

    @Test
    void collectGathersRootLevelConflicts() {
        var conflict = leaf("c", List.of(realConflictDiff()));
        var result = ConflictTreeNode.collectConflictNodes(List.of(conflict));
        assertEquals(1, result.size());
        assertEquals("c", result.get(0).label());
    }

    @Test
    void collectRecursesIntoChildren() {
        var nested = leaf("nested", List.of(realConflictDiff()));
        var parent = withChildren("p", List.of(leaf("clean", List.of()), nested));
        var result = ConflictTreeNode.collectConflictNodes(List.of(parent));
        assertEquals(1, result.size());
        assertEquals("nested", result.get(0).label());
    }

    @Test
    void collectPreservesDepthFirstOrder() {
        var deepConflict = leaf("deep", List.of(realConflictDiff()));
        var rootConflict = new ConflictTreeNode(null, "root",
                List.of(realConflictDiff()), List.of(),
                List.of(deepConflict),
                false, false, false, false);
        var result = ConflictTreeNode.collectConflictNodes(List.of(rootConflict));
        assertEquals(2, result.size());
        assertEquals("root", result.get(0).label());
        assertEquals("deep", result.get(1).label());
    }

}

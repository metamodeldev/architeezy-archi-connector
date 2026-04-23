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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.compare.ConflictKind;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.ecore.EObject;

/**
 * A node in the conflict-resolution tree. Represents one EObject from the
 * Archi model together with the diffs that affect it on each side.
 *
 * @param modelElement the EObject from the Archi model (base version preferred;
 *         may be from local or remote for newly added elements)
 * @param label the human-readable display name
 * @param localDiffs diffs originating from the left (local) side
 * @param remoteDiffs diffs originating from the right (remote) side
 * @param children child nodes reflecting the model containment hierarchy
 * @param addedLocal true if the object was created on the local side (no base)
 * @param addedRemote true if the object was created on the remote side (no base)
 * @param deletedLocal true if the object existed in base and was deleted locally
 * @param deletedRemote true if the object existed in base and was deleted remotely
 */
public record ConflictTreeNode(
        EObject modelElement,
        String label,
        List<Diff> localDiffs,
        List<Diff> remoteDiffs,
        List<ConflictTreeNode> children,
        boolean addedLocal,
        boolean addedRemote,
        boolean deletedLocal,
        boolean deletedRemote) {

    /**
     * Returns {@code true} if any diff on this node participates in a
     * {@link ConflictKind#REAL REAL} conflict.
     */
    public boolean isConflict() {
        return localDiffs.stream().anyMatch(ConflictTreeNode::isRealConflictDiff)
                || remoteDiffs.stream().anyMatch(ConflictTreeNode::isRealConflictDiff);
    }

    /**
     * Returns {@code true} if this node or any descendant participates in a
     * {@link ConflictKind#REAL REAL} conflict.
     */
    public boolean hasConflictInSubtree() {
        return isConflict() || children.stream().anyMatch(ConflictTreeNode::hasConflictInSubtree);
    }

    /**
     * Returns {@code true} if this node or any descendant has at least one diff
     * or represents a structurally added or deleted object.
     */
    public boolean hasAnyChange() {
        if (!localDiffs.isEmpty() || !remoteDiffs.isEmpty()) {
            return true;
        }
        if (addedLocal || addedRemote || deletedLocal || deletedRemote) {
            return true;
        }
        return children.stream().anyMatch(ConflictTreeNode::hasAnyChange);
    }

    /**
     * Returns the children of this node that should be visible given the view
     * mode.
     *
     * <p>
     * When {@code showAll} is {@code true} all children are returned. When it
     * is {@code false}, only children that have a conflict somewhere in their
     * subtree are returned, so the tree shows only the conflict-relevant
     * structure.
     *
     * @param showAll if {@code true} return all children; if {@code false}
     *         return only children with a conflict in their subtree
     * @return the filtered list of visible children (never {@code null})
     */
    public List<ConflictTreeNode> visibleChildren(boolean showAll) {
        if (showAll) {
            return children;
        }
        return children.stream().filter(ConflictTreeNode::hasConflictInSubtree).toList();
    }

    /**
     * Recursively collects every node in {@code nodes} (and their descendants)
     * that participates in a {@link ConflictKind#REAL REAL} conflict. Traversal
     * is depth-first pre-order.
     *
     * @param nodes the root nodes to walk
     * @return a new list of conflicting nodes in depth-first pre-order
     */
    public static List<ConflictTreeNode> collectConflictNodes(List<ConflictTreeNode> nodes) {
        var result = new ArrayList<ConflictTreeNode>();
        for (var node : nodes) {
            if (node.isConflict()) {
                result.add(node);
            }
            result.addAll(collectConflictNodes(node.children()));
        }
        return result;
    }

    private static boolean isRealConflictDiff(Diff diff) {
        var conflict = diff.getConflict();
        return conflict != null && conflict.getKind() == ConflictKind.REAL;
    }
}

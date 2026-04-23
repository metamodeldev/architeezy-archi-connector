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

    private static boolean isRealConflictDiff(Diff diff) {
        var conflict = diff.getConflict();
        return conflict != null && conflict.getKind() == ConflictKind.REAL;
    }
}

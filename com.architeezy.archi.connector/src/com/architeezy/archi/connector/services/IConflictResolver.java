/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.services;

import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.merge.IMerger;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * Strategy for resolving real conflicts detected by
 * {@link MergeService#computeMergedContent}.
 *
 * The production implementation shows a modal conflict-resolution dialog on
 * the UI thread; tests can substitute a headless fake that returns canned
 * bytes or cancels.
 */
public interface IConflictResolver {

    /**
     * Resolves the real conflicts in {@code comparison} and returns the merged
     * content for the local resource, or {@code null} if the user cancelled.
     *
     * @param comparison EMF Compare comparison containing the conflicts
     * @param localResource resource receiving the merge result
     * @param registry merger registry used to apply resolved diffs
     * @return merged bytes, or {@code null} if the resolution was cancelled
     * @throws Exception if applying a resolved diff fails
     */
    @SuppressWarnings("java:S112")
    byte[] resolve(Comparison comparison, Resource localResource, IMerger.Registry registry) throws Exception;

}

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

import com.architeezy.archi.connector.api.dto.RemoteModel;

/**
 * Snapshot of the state needed to continue a pull after the user resolves a
 * real merge conflict in the UI thread.
 *
 * <p>Produced by {@link ModelSyncService#pullModel} when a DIVERGED scenario
 * with real conflicts is detected; consumed by
 * {@link ModelSyncService#applyMergedPull} once the UI has produced the merged
 * bytes.
 *
 * @param comparison EMF Compare result
 * @param localResource in-memory local copy resource used by the dialog
 * @param mergerRegistry merger registry to apply diffs with
 * @param remote remote metadata carried through to the continuation Job
 * @param modelId tracked-model identifier carried through
 */
public record PendingConflict(
        Comparison comparison,
        Resource localResource,
        IMerger.Registry mergerRegistry,
        RemoteModel remote,
        String modelId) {
}

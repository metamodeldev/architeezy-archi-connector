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

import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.ConflictKind;
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.DifferenceState;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.merge.BatchMerger;
import org.eclipse.emf.compare.merge.IMerger;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;

import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.io.ModelSerializer;

/**
 * Performs 3-way merges for the DIVERGED scenario where both local and remote
 * models have changed since their last common base snapshot.
 *
 * <p>
 * Uses EMF Compare to compute a structural diff of base, local, and remote.
 * Non-conflicting remote changes are applied automatically. Real conflicts are
 * delegated to an {@link IConflictResolver} (a modal dialog in production).
 *
 * <p>
 * Must be called from a background thread; the conflict resolver is
 * responsible for any UI-thread hand-off it requires.
 */
@SuppressWarnings("java:S112")
public final class MergeService {

    private final ModelSerializer serializer;

    private final IConflictResolver conflictResolver;

    /**
     * Creates a new merge service.
     *
     * @param serializer model serializer used for base/local/remote bytes
     * @param conflictResolver strategy invoked when real conflicts are detected
     */
    public MergeService(ModelSerializer serializer, IConflictResolver conflictResolver) {
        this.serializer = serializer;
        this.conflictResolver = conflictResolver;
    }

    /**
     * Computes a 3-way merge of the live model against the base snapshot and
     * remote content.
     *
     * <p>
     * Non-conflicting remote changes are applied automatically. Real conflicts
     * are delegated to the configured {@link IConflictResolver}.
     *
     * @param liveModel the locally open model
     * @param baseBytes the base snapshot bytes (last known common state)
     * @param remoteBytes the remote model bytes downloaded from the server
     * @return the merged model bytes, or {@code null} if the conflict resolver cancelled
     * @throws Exception if serialization, comparison, or conflict resolution fails
     */
    public byte[] computeMergedContent(IArchimateModel liveModel, byte[] baseBytes, byte[] remoteBytes)
            throws Exception {
        var prep = prepareMerge(liveModel, baseBytes, remoteBytes, null, null);
        if (prep.mergedBytes() != null) {
            return prep.mergedBytes();
        }
        var p = prep.pending();
        return conflictResolver.resolve(p.comparison(), p.localResource(), p.mergerRegistry());
    }

    /**
     * Runs the first stage of a 3-way merge without blocking on the UI.
     *
     * If no real conflicts are found, the remote changes are applied in
     * memory and the merged bytes are returned via
     * {@link MergePreparation#autoMerged}. Otherwise a {@link PendingConflict}
     * is returned carrying the EMF Compare state the UI needs to open the
     * resolution dialog, so the caller can finish its Job and hand off to the
     * UI thread.
     *
     * @param liveModel the locally open model
     * @param baseBytes the base snapshot bytes
     * @param remoteBytes the remote model bytes
     * @param remote metadata of the remote model (carried through for the
     *        continuation Job); may be {@code null} when called from push
     * @param modelId tracked-model identifier (carried through); may be
     *        {@code null} when called from push
     * @return a {@link MergePreparation} describing what the UI must do next
     * @throws Exception if serialization or comparison fails
     */
    public MergePreparation prepareMerge(IArchimateModel liveModel, byte[] baseBytes, byte[] remoteBytes,
            RemoteModel remote, String modelId) throws Exception {
        var baseModel = serializer.deserializeInMemory(baseBytes);
        var localCopy = serializer.deserializeInMemory(serializer.serialize(liveModel));
        var remoteModel = serializer.deserializeInMemory(remoteBytes);

        var localResource = localCopy.eResource();
        var remoteResource = remoteModel.eResource();
        var baseResource = baseModel.eResource();

        var scope = new DefaultComparisonScope(localResource, remoteResource, baseResource);
        var comparison = EMFCompare.builder().build().compare(scope);

        boolean hasRealConflicts = comparison.getConflicts().stream()
                .anyMatch(c -> c.getKind() == ConflictKind.REAL);

        if (!hasRealConflicts) {
            applyAllRemoteChanges(comparison);
            return MergePreparation.autoMerged(serializer.serialize(localCopy));
        }

        var registry = IMerger.RegistryImpl.createStandaloneInstance();
        return MergePreparation.needsResolution(
                new PendingConflict(comparison, localResource, registry, remote, modelId));
    }

    private static void applyAllRemoteChanges(Comparison comparison) {
        var remoteDiffs = comparison.getDifferences().stream()
                .filter(d -> d.getSource() == DifferenceSource.RIGHT)
                .filter(d -> d.getState() == DifferenceState.UNRESOLVED)
                .toList();

        var registry = IMerger.RegistryImpl.createStandaloneInstance();
        var batchMerger = new BatchMerger(registry);
        batchMerger.copyAllRightToLeft(remoteDiffs, new BasicMonitor());
    }

}

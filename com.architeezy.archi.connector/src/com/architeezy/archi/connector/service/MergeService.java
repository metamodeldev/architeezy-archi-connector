/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.service;

import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.ConflictKind;
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.DifferenceState;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.merge.BatchMerger;
import org.eclipse.emf.compare.merge.IMerger;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.dialog.ConflictResolutionDialog;
import com.architeezy.archi.connector.io.ModelSerializer;

/**
 * Performs 3-way merges for the DIVERGED scenario where both local and remote
 * models have changed since their last common base snapshot.
 *
 * <p>
 * Uses EMF Compare to compute a structural diff of base, local, and remote.
 * Non-conflicting remote changes are applied automatically. Real conflicts are
 * presented to the user via {@link ConflictResolutionDialog}.
 *
 * <p>
 * Must be called from a background thread; UI operations switch to the UI
 * thread internally.
 */
public final class MergeService {

    /** The singleton instance of MergeService. */
    public static final MergeService INSTANCE = new MergeService();

    private MergeService() {
    }

    /**
     * Computes a 3-way merge of the live model against the base snapshot and
     * remote content.
     *
     * <p>
     * Non-conflicting remote changes are applied automatically. Real conflicts
     * are shown in the conflict-resolution dialog for the user to resolve.
     *
     * @param liveModel the locally open model
     * @param baseBytes the base snapshot bytes (last known common state)
     * @param remoteBytes the remote model bytes downloaded from the server
     * @return the merged model bytes ready to pass to
     *         {@code RepositoryService.applyNonDestructivePull}, or {@code null}
     *         if the user cancelled the conflict-resolution dialog
     * @throws Exception if serialization or comparison fails
     */
    public byte[] computeMergedContent(IArchimateModel liveModel, byte[] baseBytes, byte[] remoteBytes)
            throws Exception {
        var baseModel = ModelSerializer.INSTANCE.deserializeInMemory(baseBytes);
        var localCopy = ModelSerializer.INSTANCE.deserializeInMemory(
                ModelSerializer.INSTANCE.serialize(liveModel));
        var remoteModel = ModelSerializer.INSTANCE.deserializeInMemory(remoteBytes);

        var localResource = localCopy.eResource();
        var remoteResource = remoteModel.eResource();
        var baseResource = baseModel.eResource();

        var scope = new DefaultComparisonScope(localResource, remoteResource, baseResource);
        var comparison = EMFCompare.builder().build().compare(scope);

        boolean hasRealConflicts = comparison.getConflicts().stream()
                .anyMatch(c -> c.getKind() == ConflictKind.REAL);

        if (!hasRealConflicts) {
            applyAllRemoteChanges(comparison);
            return ModelSerializer.INSTANCE.serialize(localCopy);
        }

        var registry = IMerger.RegistryImpl.createStandaloneInstance();
        byte[][] mergedBytes = { null };
        Exception[] runError = { null };
        Display.getDefault().syncExec(() ->
                showConflictDialog(comparison, localResource, registry, mergedBytes, runError));

        if (runError[0] != null) {
            throw runError[0];
        }
        return mergedBytes[0];
    }

    private static void showConflictDialog(Comparison comparison, Resource localResource,
            IMerger.Registry registry, byte[][] mergedBytes, Exception[] runError) {
        var dialog = new ConflictResolutionDialog(
                Display.getDefault().getActiveShell(), comparison, localResource, registry);
        if (dialog.open() == Window.OK) {
            if (dialog.getMergeError() != null) {
                runError[0] = dialog.getMergeError();
            } else {
                mergedBytes[0] = dialog.getMergedContent();
            }
        }
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

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

import java.util.Arrays;
import java.util.Objects;

/**
 * Output of {@link MergeService#prepareMerge}: exactly one of the two fields
 * is non-null.
 *
 * <p>
 * If {@code mergedBytes} is set, the merge completed without real conflicts
 * and the bytes can be applied to the live model. If {@code pending} is set,
 * real conflicts were found and the UI must open the resolution dialog.
 *
 * @param mergedBytes auto-merged bytes, or {@code null} when conflicts need
 *        resolution
 * @param pending conflict data, or {@code null} when the merge completed
 *        automatically
 */
public record MergePreparation(byte[] mergedBytes, PendingConflict pending) {

    /**
     * Builds an auto-merged preparation.
     *
     * @param bytes auto-merged model bytes
     * @return preparation carrying ready-to-apply bytes
     */
    public static MergePreparation autoMerged(byte[] bytes) {
        return new MergePreparation(bytes, null);
    }

    /**
     * Builds a preparation that requires user conflict resolution.
     *
     * @param pending conflict data for the UI
     * @return preparation requiring UI conflict resolution
     */
    public static MergePreparation needsResolution(PendingConflict pending) {
        return new MergePreparation(null, pending);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MergePreparation(byte[] otherBytes, PendingConflict otherPending)
                && Arrays.equals(mergedBytes, otherBytes)
                && Objects.equals(pending, otherPending);
    }

    @Override
    @SuppressWarnings("checkstyle:MagicNumber")
    public int hashCode() {
        return 31 * Arrays.hashCode(mergedBytes) + Objects.hashCode(pending);
    }

    @Override
    public String toString() {
        return "MergePreparation[mergedBytes=" + Arrays.toString(mergedBytes) //$NON-NLS-1$
                + ", pending=" + pending + "]"; //$NON-NLS-1$ //$NON-NLS-2$
    }

}

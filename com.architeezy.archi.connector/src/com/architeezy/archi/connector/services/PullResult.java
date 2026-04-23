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

import com.architeezy.archi.connector.model.PullOutcome;

/**
 * Result of {@link ModelSyncService#pullModel}: either a terminal outcome, or
 * {@link PullOutcome#CONFLICT_PENDING} together with a {@link PendingConflict}
 * carrying the data needed to open the conflict dialog on the UI thread and
 * continue with {@link ModelSyncService#applyMergedPull}.
 *
 * @param outcome terminal outcome; equals {@link PullOutcome#CONFLICT_PENDING}
 *        when the caller must resolve a conflict
 * @param pending conflict data when {@code outcome} is {@code CONFLICT_PENDING},
 *        otherwise {@code null}
 */
public record PullResult(PullOutcome outcome, PendingConflict pending) {

    /**
     * Builds a terminal pull result.
     *
     * @param outcome terminal outcome
     * @return result that carries no pending conflict
     */
    public static PullResult completed(PullOutcome outcome) {
        return new PullResult(outcome, null);
    }

    /**
     * Builds a pending-conflict pull result.
     *
     * @param pending conflict data for the UI to resolve
     * @return result requesting user resolution of a real conflict
     */
    public static PullResult pending(PendingConflict pending) {
        return new PullResult(PullOutcome.CONFLICT_PENDING, pending);
    }
}

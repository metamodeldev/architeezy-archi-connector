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

import com.architeezy.archi.connector.model.PushOutcome;

/**
 * Result of {@link ModelSyncService#pushModel}: either a terminal outcome, or
 * {@link PushOutcome#CONFLICT_PENDING} together with a {@link PendingConflict}
 * carrying the data needed to open the conflict dialog on the UI thread and
 * continue with {@link ModelSyncService#applyMergedPush}.
 *
 * @param outcome terminal outcome; equals {@link PushOutcome#CONFLICT_PENDING}
 *        when the caller must resolve a conflict
 * @param pending conflict data when {@code outcome} is {@code CONFLICT_PENDING},
 *        otherwise {@code null}
 */
public record PushResult(PushOutcome outcome, PendingConflict pending) {

    /**
     * Builds a terminal push result.
     *
     * @param outcome terminal outcome
     * @return result that carries no pending conflict
     */
    public static PushResult completed(PushOutcome outcome) {
        return new PushResult(outcome, null);
    }

    /**
     * Builds a pending-conflict push result.
     *
     * @param pending conflict data for the UI to resolve
     * @return result requesting user resolution of a real conflict
     */
    public static PushResult pending(PendingConflict pending) {
        return new PushResult(PushOutcome.CONFLICT_PENDING, pending);
    }
}

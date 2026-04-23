/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.model;

/**
 * Result of a pull operation, so the UI layer can decide which dialog to show
 * without the service knowing about dialogs.
 */
public enum PullOutcome {

    /** Remote changes were merged into the local model. */
    APPLIED,

    /** Local and remote were already identical; nothing to do. */
    UP_TO_DATE,

    /**
     * Local has unpushed changes but the remote has not moved; the pull is a
     * no-op from the user's perspective.
     */
    REMOTE_UNCHANGED,

    /** A 3-way merge was needed and the user cancelled it. */
    CONFLICT_CANCELLED,

    /**
     * A 3-way merge with real conflicts was detected; the pull Job returned
     * without applying changes so the UI can open the resolution dialog and
     * then schedule a second Job that applies the merged bytes via
     * {@code applyMergedPull}.
     */
    CONFLICT_PENDING

}

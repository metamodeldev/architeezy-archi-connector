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
 * Result of a push operation, so the UI layer can decide which dialog to show
 * without the service knowing about dialogs.
 */
public enum PushOutcome {

    /** Local content was uploaded to the server. */
    UPLOADED,

    /**
     * A 3-way merge with real conflicts was detected; the push Job returned
     * without applying or uploading so the UI can open the resolution dialog
     * and then schedule a second Job that applies the merged bytes and
     * uploads via {@code applyMergedPush}.
     */
    CONFLICT_PENDING

}

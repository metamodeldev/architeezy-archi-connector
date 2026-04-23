/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.api;

/**
 * Cooperative cancellation signal used by long-running HTTP transfers.
 *
 * Kept Eclipse-free so {@link ArchiteezyClient} does not depend on
 * {@code org.eclipse.core.runtime}; callers typically pass
 * {@code monitor::isCanceled}.
 */
@FunctionalInterface
public interface CancelSignal {

    /** Never-cancelled signal for callers that do not need cancellation. */
    CancelSignal NEVER = () -> false;

    /**
     * Returns whether cancellation has been requested by the caller.
     *
     * @return {@code true} if cancellation has been requested
     */
    boolean isCanceled();
}

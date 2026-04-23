/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.model.diff;

/**
 * Resolution choice for a single conflicting tree node in a merge.
 */
public enum Resolution {
    /** Retain the local version of the conflicting element. */
    LOCAL,

    /** Accept the remote version of the conflicting element. */
    REMOTE
}

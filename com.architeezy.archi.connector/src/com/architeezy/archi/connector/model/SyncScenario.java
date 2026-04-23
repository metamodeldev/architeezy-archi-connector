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
 * Classifies the relationship between the local model, the remote model, and
 * their common base snapshot before a pull operation.
 */
public enum SyncScenario {

    /** Local and remote are both identical to the base: nothing to do. */
    UP_TO_DATE,

    /** Only the remote side changed: a non-destructive pull can be applied safely. */
    SIMPLE_PULL,

    /** Only the local side changed: the remote has not been updated since the last pull. */
    SIMPLE_PUSH,

    /** Both sides changed independently: a 3-way merge is required. */
    DIVERGED

}

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class SyncScenarioTests {

    @Test
    void enumExposesAllScenarios() {
        assertEquals(4, SyncScenario.values().length);
        assertSame(SyncScenario.UP_TO_DATE, SyncScenario.valueOf("UP_TO_DATE"));
        assertSame(SyncScenario.SIMPLE_PULL, SyncScenario.valueOf("SIMPLE_PULL"));
        assertSame(SyncScenario.SIMPLE_PUSH, SyncScenario.valueOf("SIMPLE_PUSH"));
        assertSame(SyncScenario.DIVERGED, SyncScenario.valueOf("DIVERGED"));
    }

}

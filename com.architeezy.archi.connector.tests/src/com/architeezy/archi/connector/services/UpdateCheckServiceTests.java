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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateCheckServiceTests {

    @Test
    void remoteIsNewerWhenLocalMissing() {
        assertTrue(UpdateCheckService.isNewer("2026-04-23T10:00:00Z", null));
        assertTrue(UpdateCheckService.isNewer("2026-04-23T10:00:00Z", ""));
        assertTrue(UpdateCheckService.isNewer("2026-04-23T10:00:00Z", "   "));
    }

    @Test
    void remoteIsNotNewerWhenServerMissing() {
        assertFalse(UpdateCheckService.isNewer(null, "2026-04-23T10:00:00Z"));
        assertFalse(UpdateCheckService.isNewer("", "2026-04-23T10:00:00Z"));
    }

    @Test
    void isoTimestampsCompareLexicographically() {
        assertTrue(UpdateCheckService.isNewer(
                "2026-04-23T11:00:00Z", "2026-04-23T10:00:00Z"));
        assertFalse(UpdateCheckService.isNewer(
                "2026-04-23T10:00:00Z", "2026-04-23T11:00:00Z"));
    }

    @Test
    void equalTimestampsAreNotNewer() {
        assertFalse(UpdateCheckService.isNewer(
                "2026-04-23T10:00:00Z", "2026-04-23T10:00:00Z"));
    }

    @Test
    void bothBlankReturnsFalse() {
        assertFalse(UpdateCheckService.isNewer(null, null));
        assertFalse(UpdateCheckService.isNewer("", ""));
    }

}

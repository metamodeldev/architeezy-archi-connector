/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class ProfileStatusTests {

    @Test
    void enumExposesAllStates() {
        assertEquals(4, ProfileStatus.values().length);
        assertSame(ProfileStatus.DISCONNECTED, ProfileStatus.valueOf("DISCONNECTED"));
        assertSame(ProfileStatus.CONNECTING, ProfileStatus.valueOf("CONNECTING"));
        assertSame(ProfileStatus.CONNECTED, ProfileStatus.valueOf("CONNECTED"));
        assertSame(ProfileStatus.SESSION_EXPIRED, ProfileStatus.valueOf("SESSION_EXPIRED"));
    }

}

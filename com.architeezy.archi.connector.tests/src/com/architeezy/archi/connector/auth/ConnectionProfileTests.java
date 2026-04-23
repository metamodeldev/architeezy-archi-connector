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

import org.junit.jupiter.api.Test;

class ConnectionProfileTests {

    @Test
    void newProfileIsDisconnected() {
        var p = new ConnectionProfile("local", "https://srv.example.com", "cli");
        assertEquals("local", p.getName());
        assertEquals("https://srv.example.com", p.getServerUrl());
        assertEquals("cli", p.getClientId());
        assertEquals(ProfileStatus.DISCONNECTED, p.getStatus());
    }

    @Test
    void setStatusUpdatesState() {
        var p = new ConnectionProfile("n", "u", "c");
        p.setStatus(ProfileStatus.CONNECTING);
        assertEquals(ProfileStatus.CONNECTING, p.getStatus());
        p.setStatus(ProfileStatus.CONNECTED);
        assertEquals(ProfileStatus.CONNECTED, p.getStatus());
        p.setStatus(ProfileStatus.SESSION_EXPIRED);
        assertEquals(ProfileStatus.SESSION_EXPIRED, p.getStatus());
    }

    @Test
    void toStringCombinesNameAndUrl() {
        var p = new ConnectionProfile("prod", "https://srv", "c");
        assertEquals("prod (https://srv)", p.toString());
    }

}

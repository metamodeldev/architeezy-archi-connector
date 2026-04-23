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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenStoreIoTests {

    private static final String SERVER = "https://srv.example.com";

    private static final String NODE_PATH = "/com/architeezy/archi/connector/profiles/"
            + TokenStore.sha1(SERVER);

    private MemorySecurePreferences prefs;

    private TokenStore store;

    @BeforeEach
    void setUp() {
        prefs = new MemorySecurePreferences();
        store = new TokenStore(() -> prefs);
    }

    @Test
    void savedTokensCanBeReadBack() {
        store.saveTokens(SERVER, "access-xyz", "refresh-abc", 1_700_000_000_000L);

        assertEquals("access-xyz", store.getAccessToken(SERVER));
        assertEquals("refresh-abc", store.getRefreshToken(SERVER));
        assertEquals(1_700_000_000_000L, store.getExpiresAt(SERVER));
    }

    @Test
    void unknownServerReturnsNullAndZero() {
        assertNull(store.getAccessToken(SERVER));
        assertNull(store.getRefreshToken(SERVER));
        assertEquals(0L, store.getExpiresAt(SERVER));
    }

    @Test
    void differentServersAreStoredInSeparateNodes() {
        var other = "https://other.example.com";
        store.saveTokens(SERVER, "A1", "R1", 111L);
        store.saveTokens(other, "A2", "R2", 222L);

        assertEquals("A1", store.getAccessToken(SERVER));
        assertEquals("A2", store.getAccessToken(other));
        assertEquals(111L, store.getExpiresAt(SERVER));
        assertEquals(222L, store.getExpiresAt(other));
    }

    @Test
    void clearTokensRemovesAllThreeFields() {
        store.saveTokens(SERVER, "A", "R", 123L);

        store.clearTokens(SERVER);

        assertNull(store.getAccessToken(SERVER));
        assertNull(store.getRefreshToken(SERVER));
        assertEquals(0L, store.getExpiresAt(SERVER));
    }

    @Test
    void malformedExpiresAtReturnsZero() throws Exception {
        prefs.node(NODE_PATH).put("expires_at", "not-a-number", false);

        assertEquals(0L, store.getExpiresAt(SERVER));
    }

    @Test
    void saveTokensOverwritesPreviousValues() {
        store.saveTokens(SERVER, "A1", "R1", 111L);
        store.saveTokens(SERVER, "A2", "R2", 222L);

        assertEquals("A2", store.getAccessToken(SERVER));
        assertEquals("R2", store.getRefreshToken(SERVER));
        assertEquals(222L, store.getExpiresAt(SERVER));
    }

}

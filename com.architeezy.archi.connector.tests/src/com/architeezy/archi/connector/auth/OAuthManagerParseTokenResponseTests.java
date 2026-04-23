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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class OAuthManagerParseTokenResponseTests {

    @Test
    void parsesAllFields() throws OAuthException {
        final var before = Instant.now().getEpochSecond();
        var json = "{\"access_token\":\"AT\",\"refresh_token\":\"RT\",\"expires_in\":3600}";
        var tr = OAuthManager.parseTokenResponse(json);
        final var after = Instant.now().getEpochSecond();

        assertNotNull(tr);
        assertEquals("AT", tr.accessToken());
        assertEquals("RT", tr.refreshToken());
        assertTrue(tr.expiresAt() >= before + 3600 && tr.expiresAt() <= after + 3600,
                "expiresAt should be ~now + expires_in: " + tr.expiresAt());
    }

    @Test
    void defaultsExpiresInWhenMissing() throws OAuthException {
        var now = Instant.now().getEpochSecond();
        var json = "{\"access_token\":\"AT\",\"refresh_token\":\"RT\"}";
        var tr = OAuthManager.parseTokenResponse(json);

        // default is 3600
        assertTrue(tr.expiresAt() >= now + 3599,
                "expiresAt should be >= now + 3599, was " + tr.expiresAt() + " (now=" + now + ")");
    }

    @Test
    void allowsMissingRefreshToken() throws OAuthException {
        var tr = OAuthManager.parseTokenResponse("{\"access_token\":\"AT\",\"expires_in\":60}");
        assertEquals("AT", tr.accessToken());
        assertNull(tr.refreshToken());
    }

    @Test
    void throwsWhenAccessTokenMissing() {
        var ex = assertThrows(OAuthException.class,
                () -> OAuthManager.parseTokenResponse("{\"expires_in\":60}"));
        assertTrue(ex.getMessage().contains("access_token"), ex.getMessage());
    }

}

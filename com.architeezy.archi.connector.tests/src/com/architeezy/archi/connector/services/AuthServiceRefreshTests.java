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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.eclipse.jface.preference.PreferenceStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.auth.MemorySecurePreferences;
import com.architeezy.archi.connector.auth.OAuthException;
import com.architeezy.archi.connector.auth.OAuthManager;
import com.architeezy.archi.connector.auth.ProfileRegistry;
import com.architeezy.archi.connector.auth.ProfileStatus;
import com.architeezy.archi.connector.auth.TokenStore;
import com.sun.net.httpserver.HttpServer;

/**
 * Exercises {@link AuthService#getValidAccessToken} against a real
 * {@link OAuthManager} talking to a local HTTP server.
 */
class AuthServiceRefreshTests {

    private HttpServer server;

    private String baseUrl;

    private TokenStore tokens;

    private AuthService service;

    private ConnectionProfile profile;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        var securePrefs = new MemorySecurePreferences();
        tokens = new TokenStore(() -> securePrefs);
        var registry = new ProfileRegistry(new PreferenceStore(), tokens);
        registry.removeProfile("Architeezy");
        service = new AuthService(new OAuthManager(), tokens, registry);
        profile = new ConnectionProfile("corp", baseUrl, "cid");
        registry.addProfile(profile, baseUrl + "/auth", baseUrl + "/token");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void onTokenEndpoint(int status, String body) {
        server.createContext("/token", ex -> {
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            try (var os = ex.getResponseBody()) {
                os.write(bytes);
            }
            ex.close();
        });
    }

    @Test
    void validTokenReturnsWithoutRefresh() throws OAuthException {
        // The /token endpoint isn't registered: the HTTP server will 404 if the
        // service tries to call it. This makes "no refresh" a genuine test.
        var future = Instant.now().getEpochSecond() + 3600;
        tokens.saveTokens(baseUrl, "still-good", "R", future);

        assertEquals("still-good", service.getValidAccessToken(profile));
        assertEquals("still-good", tokens.getAccessToken(baseUrl));
        assertEquals(future, tokens.getExpiresAt(baseUrl));
    }

    @Test
    void tokenNearExpiryTriggersRefreshAgainstServer() throws OAuthException {
        var now = Instant.now().getEpochSecond();
        tokens.saveTokens(baseUrl, "old", "refresh-1", now);
        profile.setStatus(ProfileStatus.CONNECTED);

        onTokenEndpoint(200,
                "{\"access_token\":\"new-A\",\"refresh_token\":\"new-R\",\"expires_in\":3600}");

        var result = service.getValidAccessToken(profile);

        assertEquals("new-A", result);
        assertEquals("new-A", tokens.getAccessToken(baseUrl));
        assertEquals("new-R", tokens.getRefreshToken(baseUrl));
        // expires_in=3600 => expiresAt should be roughly now+3600; allow drift.
        var expires = tokens.getExpiresAt(baseUrl);
        assertTrue(expires >= now + 3500 && expires <= now + 3700,
                "expected expiry near now+3600, got " + (expires - now));
        assertEquals(ProfileStatus.CONNECTED, profile.getStatus());
    }

    @Test
    void refreshFailurePropagatesAndMarksExpired() {
        var now = Instant.now().getEpochSecond();
        tokens.saveTokens(baseUrl, "old", "refresh-1", now);
        profile.setStatus(ProfileStatus.CONNECTED);

        onTokenEndpoint(400, "{\"error\":\"invalid_grant\"}");

        var ex = assertThrows(OAuthException.class, () -> service.getValidAccessToken(profile));
        assertTrue(ex.getMessage().contains("400"), ex.getMessage());
        assertEquals(ProfileStatus.SESSION_EXPIRED, profile.getStatus());
    }

}

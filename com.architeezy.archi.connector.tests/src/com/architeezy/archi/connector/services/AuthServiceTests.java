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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.eclipse.jface.preference.PreferenceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.auth.FakeOAuthManager;
import com.architeezy.archi.connector.auth.MemorySecurePreferences;
import com.architeezy.archi.connector.auth.OAuthException;
import com.architeezy.archi.connector.auth.OAuthManager.TokenResponse;
import com.architeezy.archi.connector.auth.ProfileRegistry;
import com.architeezy.archi.connector.auth.ProfileStatus;
import com.architeezy.archi.connector.auth.TokenStore;

/**
 * Tests for login, logout, and cancel. Refresh-related behaviour lives in
 * {@link AuthServiceRefreshTests} where a real {@link com.architeezy.archi.connector.auth.OAuthManager}
 * talks to a local HTTP server. {@link FakeOAuthManager} is kept here only
 * because login requires opening a browser, which cannot be exercised in a
 * headless unit test.
 */
class AuthServiceTests {

    private FakeOAuthManager oauth;

    private TokenStore tokens;

    private ProfileRegistry registry;

    private AuthService service;

    private ConnectionProfile profile;

    @BeforeEach
    void setUp() {
        oauth = new FakeOAuthManager();
        var securePrefs = new MemorySecurePreferences();
        tokens = new TokenStore(() -> securePrefs);
        registry = new ProfileRegistry(new PreferenceStore(), tokens);
        registry.removeProfile("Architeezy");
        service = new AuthService(oauth, tokens, registry);
        profile = new ConnectionProfile("corp", "https://corp", "cid");
        registry.addProfile(profile, "https://corp/auth", "https://corp/token");
    }

    @Test
    void loginSavesTokensAndMarksConnected() throws OAuthException {
        var expiresAt = Instant.now().getEpochSecond() + 3600;
        oauth.nextLoginResponse = Optional.of(new TokenResponse("A1", "R1", expiresAt));

        service.login(profile);

        assertEquals(ProfileStatus.CONNECTED, profile.getStatus());
        assertEquals("A1", tokens.getAccessToken("https://corp"));
        assertEquals("R1", tokens.getRefreshToken("https://corp"));
        assertEquals(expiresAt, tokens.getExpiresAt("https://corp"));
    }

    @Test
    void loginCancelledLeavesProfileDisconnected() throws OAuthException {
        oauth.nextLoginResponse = Optional.empty();

        service.login(profile);

        assertEquals(ProfileStatus.DISCONNECTED, profile.getStatus());
        assertNull(tokens.getAccessToken("https://corp"));
    }

    @Test
    void loginPropagatesOAuthExceptionAndSetsDisconnected() {
        oauth.nextLoginError = new OAuthException("boom");

        var ex = assertThrows(OAuthException.class, () -> service.login(profile));
        assertEquals("boom", ex.getMessage());
        assertEquals(ProfileStatus.DISCONNECTED, profile.getStatus());
    }

    @Test
    void logoutClearsTokensAndMarksDisconnected() {
        tokens.saveTokens("https://corp", "A", "R", 0L);
        profile.setStatus(ProfileStatus.CONNECTED);

        service.logout(profile);

        assertEquals(ProfileStatus.DISCONNECTED, profile.getStatus());
        assertNull(tokens.getAccessToken("https://corp"));
    }

    @Test
    void cancelLoginDelegatesAndMarksDisconnected() {
        profile.setStatus(ProfileStatus.CONNECTING);

        service.cancelLogin(profile);

        assertEquals(ProfileStatus.DISCONNECTED, profile.getStatus());
        assertTrue(oauth.cancelled);
    }

    @Test
    void missingRefreshTokenThrowsAndMarksExpired() {
        // No token in the store — AuthService rejects before calling oauth.refreshToken,
        // so it doesn't matter which OAuthManager implementation is wired up.
        var ex = assertThrows(OAuthException.class, () -> service.getValidAccessToken(profile));
        assertTrue(ex.getMessage().toLowerCase().contains("refresh"));
        assertEquals(ProfileStatus.SESSION_EXPIRED, profile.getStatus());
    }

}

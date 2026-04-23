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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.auth.OAuthException;
import com.architeezy.archi.connector.auth.OAuthManager;
import com.architeezy.archi.connector.auth.ProfileRegistry;
import com.architeezy.archi.connector.auth.ProfileStatus;
import com.architeezy.archi.connector.auth.TokenStore;

/**
 * OAuth session lifecycle: login, logout, and access-token refresh.
 *
 * Profile CRUD and persistence live in {@link ProfileRegistry}.
 *
 * Thread-safety: token refresh uses a per-profile lock to ensure only one
 * refresh request is in-flight at a time.
 */
@SuppressWarnings("java:S6548")
public final class AuthService {

    /** The singleton instance of AuthService. */
    public static final AuthService INSTANCE = new AuthService();

    private static final long REFRESH_MARGIN_SECONDS = 60;

    private final Map<String, ReentrantLock> refreshLocks = new HashMap<>();

    private final OAuthManager oauth = new OAuthManager();

    private AuthService() {
    }

    // -----------------------------------------------------------------------
    // Authentication flow

    /**
     * Starts the OAuth login flow for the given profile.
     *
     * @param profile the profile to authenticate
     * @throws OAuthException if authentication fails
     */
    public void login(ConnectionProfile profile) throws OAuthException {
        profile.setStatus(ProfileStatus.CONNECTING);
        try {
            var authEndpoint = ProfileRegistry.INSTANCE.getAuthEndpoint(profile.getName());
            var tokenEndpoint = ProfileRegistry.INSTANCE.getTokenEndpoint(profile.getName());

            var tokens = oauth.login(
                    profile.getServerUrl(), profile.getClientId(),
                    authEndpoint, tokenEndpoint);

            if (tokens.isEmpty()) {
                // login was cancelled
                profile.setStatus(ProfileStatus.DISCONNECTED);
                return;
            }
            var response = tokens.get();
            TokenStore.INSTANCE.saveTokens(profile.getServerUrl(),
                    response.accessToken(), response.refreshToken(), response.expiresAt());
            profile.setStatus(ProfileStatus.CONNECTED);
        } catch (OAuthException e) {
            profile.setStatus(ProfileStatus.DISCONNECTED);
            throw e;
        }
    }

    /**
     * Logs out the given profile by clearing its tokens.
     *
     * @param profile the profile to log out
     */
    public void logout(ConnectionProfile profile) {
        TokenStore.INSTANCE.clearTokens(profile.getServerUrl());
        profile.setStatus(ProfileStatus.DISCONNECTED);
    }

    /**
     * Cancels the ongoing login flow for the given profile.
     *
     * @param profile the profile whose login should be cancelled
     */
    public void cancelLogin(ConnectionProfile profile) {
        oauth.cancelLogin();
        profile.setStatus(ProfileStatus.DISCONNECTED);
    }

    /**
     * Returns a valid access token, performing a proactive refresh if the token
     * is near expiry. Multiple concurrent callers share a single refresh request.
     *
     * @param profile the connection profile to use
     * @return a valid access token
     * @throws OAuthException if the token cannot be retrieved or refreshed
     */
    public String getValidAccessToken(ConnectionProfile profile) throws OAuthException {
        var serverUrl = profile.getServerUrl();

        var expiresAt = TokenStore.INSTANCE.getExpiresAt(serverUrl);
        var now = Instant.now().getEpochSecond();

        if (expiresAt - now > REFRESH_MARGIN_SECONDS) {
            var token = TokenStore.INSTANCE.getAccessToken(serverUrl);
            if (token != null) {
                return token;
            }
        }

        return performRefresh(profile);
    }

    private String performRefresh(ConnectionProfile profile) throws OAuthException {
        var serverUrl = profile.getServerUrl();

        ReentrantLock lock;
        synchronized (this) {
            refreshLocks.putIfAbsent(serverUrl, new ReentrantLock());
            lock = refreshLocks.get(serverUrl);
        }

        lock.lock();
        try {
            // Re-check after acquiring lock (another thread may have refreshed already)
            var expiresAt = TokenStore.INSTANCE.getExpiresAt(serverUrl);
            var now = Instant.now().getEpochSecond();
            if (expiresAt - now > REFRESH_MARGIN_SECONDS) {
                var token = TokenStore.INSTANCE.getAccessToken(serverUrl);
                if (token != null) {
                    return token;
                }
            }

            var refreshToken = TokenStore.INSTANCE.getRefreshToken(serverUrl);
            if (refreshToken == null) {
                profile.setStatus(ProfileStatus.SESSION_EXPIRED);
                throw new OAuthException("No refresh token available. Please sign in again."); //$NON-NLS-1$
            }

            var tokenEndpoint = ProfileRegistry.INSTANCE.getTokenEndpoint(profile.getName());

            try {
                var tokens = oauth.refreshToken(tokenEndpoint, profile.getClientId(), refreshToken);
                TokenStore.INSTANCE.saveTokens(serverUrl, tokens.accessToken(), tokens.refreshToken(),
                        tokens.expiresAt());
                profile.setStatus(ProfileStatus.CONNECTED);
                return tokens.accessToken();
            } catch (OAuthException e) {
                profile.setStatus(ProfileStatus.SESSION_EXPIRED);
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

}

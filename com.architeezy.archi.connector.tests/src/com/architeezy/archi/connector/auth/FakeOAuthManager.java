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

import java.util.Optional;

import com.architeezy.archi.connector.auth.OAuthManager.TokenResponse;

/**
 * Scriptable fake {@link IOAuthManager} for unit tests: returns pre-seeded
 * responses. The only observable effect {@link #cancelLogin} has is the
 * {@link #cancelled} flag, because there is no state to assert against.
 */
public final class FakeOAuthManager implements IOAuthManager {

    /** Set to {@code true} by {@link #cancelLogin()}. */
    public volatile boolean cancelled;

    /** Response returned by the next {@link #login} call. */
    public Optional<TokenResponse> nextLoginResponse = Optional.empty();

    /** Exception thrown by the next {@link #login} call, if non-null. */
    public OAuthException nextLoginError;

    /** Response returned by the next {@link #refreshToken} call. */
    public TokenResponse nextRefreshResponse;

    /** Exception thrown by the next {@link #refreshToken} call, if non-null. */
    public OAuthException nextRefreshError;

    @Override
    public Optional<TokenResponse> login(String serverUrl, String clientId,
            String authEndpoint, String tokenEndpoint) throws OAuthException {
        if (nextLoginError != null) {
            throw nextLoginError;
        }
        return nextLoginResponse;
    }

    @Override
    public TokenResponse refreshToken(String tokenEndpoint, String clientId, String refreshToken)
            throws OAuthException {
        if (nextRefreshError != null) {
            throw nextRefreshError;
        }
        return nextRefreshResponse;
    }

    @Override
    public void cancelLogin() {
        cancelled = true;
    }

}

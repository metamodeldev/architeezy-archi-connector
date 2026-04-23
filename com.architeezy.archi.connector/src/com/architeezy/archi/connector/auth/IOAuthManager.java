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
 * Abstraction over the OAuth 2.0 authorization code + PKCE flow.
 *
 * The production implementation opens a browser and listens on a loopback
 * port for the redirect; tests can substitute a fake that returns canned
 * responses without performing any network I/O.
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public interface IOAuthManager {

    /**
     * Runs the OAuth 2.0 authorization code + PKCE flow.
     *
     * @param serverUrl base URL of the Architeezy server
     * @param clientId OAuth2 client identifier
     * @param authEndpoint authorization endpoint URL
     * @param tokenEndpoint token endpoint URL
     * @return the obtained token response, or an empty {@link Optional} if the user
     *         cancelled
     * @throws OAuthException if the flow fails or times out
     */
    Optional<TokenResponse> login(String serverUrl, String clientId, String authEndpoint, String tokenEndpoint)
            throws OAuthException;

    /**
     * Exchanges a refresh token for a new access token.
     *
     * @param tokenEndpoint token endpoint URL
     * @param clientId OAuth2 client identifier
     * @param refreshToken the current refresh token
     * @return the new token response
     * @throws OAuthException if the refresh request fails
     */
    TokenResponse refreshToken(String tokenEndpoint, String clientId, String refreshToken) throws OAuthException;

    /**
     * Signals an in-progress {@link #login} call to abort.
     */
    void cancelLogin();

}

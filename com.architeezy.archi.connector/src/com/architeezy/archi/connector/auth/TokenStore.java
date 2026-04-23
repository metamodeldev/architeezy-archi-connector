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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.StorageException;

/**
 * Persists OAuth tokens in the Eclipse Equinox secure preferences store.
 */
public class TokenStore {

    private static final String NODE_ROOT = "/com/architeezy/archi/connector/profiles/"; //$NON-NLS-1$

    private static final String KEY_ACCESS = "access_token"; //$NON-NLS-1$

    private static final String KEY_REFRESH = "refresh_token"; //$NON-NLS-1$

    private static final String KEY_EXPIRES = "expires_at"; //$NON-NLS-1$

    private final Supplier<ISecurePreferences> rootNode;

    /**
     * Creates a new token store backed by the given secure-preferences root.
     *
     * @param rootNode supplier of the root {@link ISecurePreferences} node
     */
    public TokenStore(Supplier<ISecurePreferences> rootNode) {
        this.rootNode = rootNode;
    }

    /**
     * Returns the stored access token for the given server URL, or {@code null}.
     *
     * @param serverUrl the server URL used as the storage key
     * @return the access token, or {@code null} if absent
     */
    public String getAccessToken(String serverUrl) {
        try {
            return node(serverUrl).get(KEY_ACCESS, null);
        } catch (StorageException e) {
            Platform.getLog(TokenStore.class).error("Failed to read access token", e); //$NON-NLS-1$
            return null;
        }
    }

    /**
     * Returns the stored refresh token for the given server URL, or {@code null}.
     *
     * @param serverUrl the server URL used as the storage key
     * @return the refresh token, or {@code null} if absent
     */
    public String getRefreshToken(String serverUrl) {
        try {
            return node(serverUrl).get(KEY_REFRESH, null);
        } catch (StorageException e) {
            Platform.getLog(TokenStore.class).error("Failed to read refresh token", e); //$NON-NLS-1$
            return null;
        }
    }

    /**
     * Returns the token expiry time (epoch millis) for the given server URL, or
     * {@code 0}.
     *
     * @param serverUrl the server URL used as the storage key
     * @return epoch millis at which the token expires, or {@code 0} if unknown
     */
    public long getExpiresAt(String serverUrl) {
        try {
            var value = node(serverUrl).get(KEY_EXPIRES, "0"); //$NON-NLS-1$
            return Long.parseLong(value);
        } catch (StorageException | NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Persists the access token, refresh token, and expiry for the given server.
     *
     * @param serverUrl the server URL used as the storage key
     * @param accessToken the access token to store
     * @param refreshToken the refresh token to store
     * @param expiresAt epoch millis at which the access token expires
     */
    public void saveTokens(String serverUrl, String accessToken, String refreshToken, long expiresAt) {
        try {
            var n = node(serverUrl);
            n.put(KEY_ACCESS, accessToken, true);
            n.put(KEY_REFRESH, refreshToken, true);
            n.put(KEY_EXPIRES, String.valueOf(expiresAt), false);
            n.flush();
        } catch (Exception e) {
            Platform.getLog(TokenStore.class).error("Failed to save tokens", e); //$NON-NLS-1$
        }
    }

    /**
     * Removes all stored tokens for the given server URL.
     *
     * @param serverUrl the server URL used as the storage key
     */
    public void clearTokens(String serverUrl) {
        try {
            var n = node(serverUrl);
            n.remove(KEY_ACCESS);
            n.remove(KEY_REFRESH);
            n.remove(KEY_EXPIRES);
            n.flush();
        } catch (Exception e) {
            Platform.getLog(TokenStore.class).error("Failed to clear tokens", e); //$NON-NLS-1$
        }
    }

    private ISecurePreferences node(String serverUrl) {
        return rootNode.get().node(NODE_ROOT + sha1(serverUrl));
    }

    static String sha1(String input) {
        try {
            var md = MessageDigest.getInstance("SHA-1"); //$NON-NLS-1$
            var hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (var b : hash) {
                sb.append(String.format("%02x", b)); //$NON-NLS-1$
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

}

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.architeezy.archi.connector.ConnectorPlugin;

/**
 * Implements the OAuth 2.0 Authorization Code + PKCE flow.
 *
 * A local ServerSocket captures the redirect, so no registered redirect URI is
 * needed beyond http://127.0.0.1:{dynamicPort}/callback.
 */
@SuppressWarnings("checkstyle:MagicNumber")
public class OAuthManager {

    private static final int TIMEOUT_MS = 300_000; // 5 minutes

    private static final String REDIRECT_PATH = "/callback"; //$NON-NLS-1$

    private static final String PARAM_CLIENT_ID = "&client_id="; //$NON-NLS-1$

    private static final String HEADER_CONTENT_TYPE = "Content-Type"; //$NON-NLS-1$

    private static final String QUOTE = "\""; //$NON-NLS-1$

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @SuppressWarnings("java:S3077")
    private volatile ServerSocket activeServer;

    private volatile boolean loginCancelled;

    /**
     * Signals the in-progress login to abort and closes the redirect server socket.
     */
    public void cancelLogin() {
        loginCancelled = true;
        var s = activeServer;
        if (s != null && !s.isClosed()) {
            try {
                s.close();
            } catch (IOException ignored) {
                // intentional: best-effort close during cancellation
            }
        }
    }

    /**
     * Runs the OAuth 2.0 Authorization Code + PKCE flow for the given server.
     *
     * @param serverUrl base URL of the Architeezy server
     * @param clientId OAuth2 client identifier
     * @param authEndpoint authorization endpoint URL
     * @param tokenEndpoint token endpoint URL
     * @return the obtained token response, or an empty {@link Optional} if the user cancelled
     * @throws OAuthException if the flow fails or times out
     */
    public Optional<TokenResponse> login(String serverUrl, String clientId, String authEndpoint, String tokenEndpoint)
            throws OAuthException {
        loginCancelled = false;

        validateUrl(authEndpoint, "auth endpoint"); //$NON-NLS-1$
        validateUrl(tokenEndpoint, "token endpoint"); //$NON-NLS-1$

        var verifier = generateCodeVerifier();
        var challenge = generateCodeChallenge(verifier);
        var state = generateState();

        try (var server = new ServerSocket(0)) {
            activeServer = server;
            server.setSoTimeout(TIMEOUT_MS);
            var port = server.getLocalPort();
            String redirectUri = "http://127.0.0.1:" + port + REDIRECT_PATH; //$NON-NLS-1$

            var authUrl = authEndpoint
                    + "?response_type=code" //$NON-NLS-1$
                    + PARAM_CLIENT_ID + encode(clientId)
                    + "&redirect_uri=" + encode(redirectUri) //$NON-NLS-1$
                    + "&code_challenge=" + encode(challenge) //$NON-NLS-1$
                    + "&code_challenge_method=S256" //$NON-NLS-1$
                    + "&state=" + encode(state); //$NON-NLS-1$

            openBrowser(authUrl);

            var code = waitForCode(server, state);
            return Optional.of(exchangeCode(tokenEndpoint, clientId, code, verifier, redirectUri));

        } catch (SocketTimeoutException e) {
            if (loginCancelled) {
                return Optional.empty();
            }
            throw new OAuthException("Authorization timed out", e); //$NON-NLS-1$
        } catch (OAuthException e) {
            throw e;
        } catch (Exception e) {
            if (loginCancelled) {
                return Optional.empty();
            }
            throw new OAuthException("OAuth flow failed: " + e.getMessage(), e); //$NON-NLS-1$
        } finally {
            activeServer = null;
        }
    }

    private static void validateUrl(String url, String fieldName) throws OAuthException {
        try {
            URI.create(url).toURL();
        } catch (MalformedURLException e) {
            throw new OAuthException("Invalid " + fieldName + ": " + url, e); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    /**
     * Exchanges a refresh token for a new access token.
     *
     * @param tokenEndpoint token endpoint URL
     * @param clientId OAuth2 client identifier
     * @param refreshToken the current refresh token
     * @return the new token response
     * @throws OAuthException if the refresh request fails
     */
    public TokenResponse refreshToken(String tokenEndpoint, String clientId, String refreshToken)
            throws OAuthException {
        var body = "grant_type=refresh_token" //$NON-NLS-1$
                + PARAM_CLIENT_ID + encode(clientId)
                + "&refresh_token=" + encode(refreshToken); //$NON-NLS-1$

        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .header(HEADER_CONTENT_TYPE, "application/x-www-form-urlencoded") //$NON-NLS-1$
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new OAuthException("Token refresh failed: HTTP " + response.statusCode()); //$NON-NLS-1$
            }
            return parseTokenResponse(response.body());
        } catch (OAuthException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OAuthException("Token refresh interrupted", e); //$NON-NLS-1$
        } catch (Exception e) {
            throw new OAuthException("Token refresh failed: " + e.getMessage(), e); //$NON-NLS-1$
        }
    }

    // -----------------------------------------------------------------------

    private String waitForCode(ServerSocket server, String expectedState) throws IOException, OAuthException {
        try (var socket = server.accept()) {
            var reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            var requestLine = reader.readLine();
            if (requestLine == null) {
                throw new OAuthException("Empty request from browser"); //$NON-NLS-1$
            }

            // GET /callback?code=...&state=... HTTP/1.1
            var path = requestLine.split(" ")[1]; //$NON-NLS-1$
            final var params = parseQueryParams(path);

            // Send minimal HTTP response so browser shows a success page
            var html = "<html><body><h2>Authentication successful. You may close this tab.</h2></body></html>"; //$NON-NLS-1$
            var out = new PrintWriter(socket.getOutputStream());
            out.print("HTTP/1.1 200 OK\r\n"); //$NON-NLS-1$
            out.print("Content-Type: text/html\r\n"); //$NON-NLS-1$
            out.print("Content-Length: " + html.length() + "\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
            out.print("Connection: close\r\n\r\n"); //$NON-NLS-1$
            out.print(html);
            out.flush();

            if (params.containsKey("error")) { //$NON-NLS-1$
                throw new OAuthException("Authorization denied: " + params.get("error")); //$NON-NLS-1$ //$NON-NLS-2$
            }
            if (!expectedState.equals(params.get("state"))) { //$NON-NLS-1$
                throw new OAuthException("State mismatch - possible CSRF attack"); //$NON-NLS-1$
            }
            var code = params.get("code"); //$NON-NLS-1$
            if (code == null || code.isBlank()) {
                throw new OAuthException("No authorization code received"); //$NON-NLS-1$
            }
            return code;
        }
    }

    private TokenResponse exchangeCode(String tokenEndpoint, String clientId, String code,
            String verifier, String redirectUri) throws OAuthException {
        var body = "grant_type=authorization_code" //$NON-NLS-1$
                + PARAM_CLIENT_ID + encode(clientId)
                + "&code=" + encode(code) //$NON-NLS-1$
                + "&code_verifier=" + encode(verifier) //$NON-NLS-1$
                + "&redirect_uri=" + encode(redirectUri); //$NON-NLS-1$

        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .header(HEADER_CONTENT_TYPE, "application/x-www-form-urlencoded") //$NON-NLS-1$
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new OAuthException("Token exchange failed: HTTP " + response.statusCode()); //$NON-NLS-1$
            }
            return parseTokenResponse(response.body());
        } catch (OAuthException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OAuthException("Token exchange interrupted", e); //$NON-NLS-1$
        } catch (Exception e) {
            throw new OAuthException("Token exchange failed: " + e.getMessage(), e); //$NON-NLS-1$
        }
    }

    private TokenResponse parseTokenResponse(String json) throws OAuthException {
        var accessToken = extractJsonString(json, "access_token"); //$NON-NLS-1$
        var refreshToken = extractJsonString(json, "refresh_token"); //$NON-NLS-1$
        var expiresIn = extractJsonLong(json, "expires_in", 3600); //$NON-NLS-1$

        if (accessToken == null) {
            throw new OAuthException("No access_token in response"); //$NON-NLS-1$
        }
        var expiresAt = Instant.now().getEpochSecond() + expiresIn;
        return new TokenResponse(accessToken, refreshToken, expiresAt);
    }

    // -----------------------------------------------------------------------
    // PKCE helpers

    private static String generateCodeVerifier() {
        var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String generateCodeChallenge(String verifier) {
        try {
            var md = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
            var hash = md.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String generateState() {
        var bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void openBrowser(String url) {
        try {
            org.eclipse.ui.PlatformUI.getWorkbench()
                    .getBrowserSupport()
                    .getExternalBrowser()
                    .openURL(URI.create(url).toURL());
        } catch (Exception e) {
            ConnectorPlugin.getInstance().getLog().error("Failed to open browser", e); //$NON-NLS-1$
        }
    }

    /**
     * Parses key=value pairs from a URL query string (after '?').
     *
     * @param path the request path including the query string
     * @return map of decoded query parameter names to values
     */
    private static Map<String, String> parseQueryParams(String path) {
        var map = new HashMap<String, String>();
        var q = path.indexOf('?');
        if (q < 0) {
            return map;
        }
        var query = path.substring(q + 1);
        // Strip HTTP version if present
        var space = query.indexOf(' ');
        if (space > 0) {
            query = query.substring(0, space);
        }
        for (var pair : query.split("&")) { //$NON-NLS-1$
            var kv = pair.split("=", 2); //$NON-NLS-1$
            if (kv.length == 2) {
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    // -----------------------------------------------------------------------
    // Minimal JSON field extraction (avoids external library dependencies)

    /**
     * Extracts the string value for the given JSON key using minimal parsing.
     *
     * @param json JSON text to search
     * @param key field name
     * @return the string value, or {@code null} if not found
     */
    public static String extractJsonString(String json, String key) {
        var search = QUOTE + key + QUOTE;
        var idx = json.indexOf(search);
        if (idx < 0) {
            return null;
        }
        var colon = json.indexOf(':', idx + search.length());
        if (colon < 0) {
            return null;
        }
        var start = json.indexOf('"', colon + 1);
        if (start < 0) {
            return null;
        }
        var end = json.indexOf('"', start + 1);
        if (end < 0) {
            return null;
        }
        return json.substring(start + 1, end);
    }

    /**
     * Extracts the long value for the given JSON key using minimal parsing.
     *
     * @param json JSON text to search
     * @param key field name
     * @param defaultValue value to return if the key is not found or not a number
     * @return the numeric value, or {@code defaultValue}
     */
    public static long extractJsonLong(String json, String key, long defaultValue) {
        var valuePos = findJsonValuePosition(json, key);
        if (valuePos < 0) {
            return defaultValue;
        }
        var start = valuePos;
        while (start < json.length() && json.charAt(start) == ' ') {
            start++;
        }
        var end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end == start) {
            return defaultValue;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int findJsonValuePosition(String json, String key) {
        var search = QUOTE + key + QUOTE;
        var idx = json.indexOf(search);
        if (idx < 0) {
            return -1;
        }
        var colon = json.indexOf(':', idx + search.length());
        return colon < 0 ? -1 : colon + 1;
    }

    /**
     * Holds the tokens returned by a successful OAuth token request.
     *
     * @param accessToken the OAuth2 access token
     * @param refreshToken the OAuth2 refresh token
     * @param expiresAt epoch millis at which the access token expires
     */
    public record TokenResponse(String accessToken, String refreshToken, long expiresAt) {
    }

}

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class OAuthManagerRefreshTokenTests {

    private HttpServer server;

    private String tokenEndpoint;

    private final OAuthManager oauth = new OAuthManager();

    private final List<RecordedRequest> requests = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        tokenEndpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/token";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void onToken(int status, String contentType, String body) {
        server.createContext("/token", exchange -> respond(exchange, status, contentType, body));
    }

    private void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        recordRequest(exchange);
        var respBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        if (contentType != null) {
            exchange.getResponseHeaders().add("Content-Type", contentType);
        }
        exchange.sendResponseHeaders(status, respBytes.length == 0 ? -1 : respBytes.length);
        if (respBytes.length > 0) {
            try (var os = exchange.getResponseBody()) {
                os.write(respBytes);
            }
        }
        exchange.close();
    }

    private void recordRequest(HttpExchange exchange) throws IOException {
        var headers = new HashMap<String, String>();
        exchange.getRequestHeaders().forEach((k, v) ->
                headers.put(k.toLowerCase(Locale.ROOT), v.isEmpty() ? null : v.get(0)));
        byte[] body;
        try (var is = exchange.getRequestBody()) {
            body = is.readAllBytes();
        }
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                headers,
                new String(body, StandardCharsets.UTF_8)));
    }

    // ---------------------------------------------------------------------

    @Test
    void refreshTokenSendsFormEncodedBody() throws Exception {
        onToken(200, "application/json",
                "{\"access_token\":\"new-at\",\"refresh_token\":\"new-rt\",\"expires_in\":120}");

        var tr = oauth.refreshToken(tokenEndpoint, "my-client", "old-rt");

        assertNotNull(tr);
        assertEquals("new-at", tr.accessToken());
        assertEquals("new-rt", tr.refreshToken());

        assertEquals(1, requests.size());
        var req = requests.get(0);
        assertEquals("POST", req.method());
        assertEquals("/token", req.path());
        assertEquals("application/x-www-form-urlencoded", req.headers().get("content-type"));
        assertTrue(req.body().contains("grant_type=refresh_token"), req.body());
        assertTrue(req.body().contains("client_id=my-client"), req.body());
        assertTrue(req.body().contains("refresh_token=old-rt"), req.body());
    }

    @Test
    void refreshTokenUrlEncodesSpecialChars() throws Exception {
        onToken(200, "application/json",
                "{\"access_token\":\"at\",\"refresh_token\":\"rt\",\"expires_in\":60}");

        oauth.refreshToken(tokenEndpoint, "cli/ent id", "a+b=c&d");

        var body = requests.get(0).body();
        // URLEncoder.encode turns space into '+' and '+' into '%2B'
        assertTrue(body.contains("client_id=cli%2Fent+id"), body);
        assertTrue(body.contains("refresh_token=a%2Bb%3Dc%26d"), body);
    }

    @Test
    void refreshTokenThrowsOnNon200() {
        onToken(400, "application/json", "{\"error\":\"invalid_grant\"}");

        var ex = assertThrows(OAuthException.class,
                () -> oauth.refreshToken(tokenEndpoint, "c", "r"));
        assertTrue(ex.getMessage().contains("400"), ex.getMessage());
    }

    @Test
    void refreshTokenThrowsWhenAccessTokenMissingFromBody() {
        onToken(200, "application/json", "{\"refresh_token\":\"rt\"}");

        var ex = assertThrows(OAuthException.class,
                () -> oauth.refreshToken(tokenEndpoint, "c", "r"));
        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("access_token"),
                ex.getMessage());
    }

    @Test
    void refreshTokenWrapsTransportErrors() {
        // Bad URL forces an exception inside send()
        var ex = assertThrows(OAuthException.class,
                () -> oauth.refreshToken("http://127.0.0.1:1/does-not-exist", "c", "r"));
        assertTrue(ex.getMessage().startsWith("Token refresh failed"), ex.getMessage());
    }

    private record RecordedRequest(String method, String path, Map<String, String> headers, String body) {
    }

}

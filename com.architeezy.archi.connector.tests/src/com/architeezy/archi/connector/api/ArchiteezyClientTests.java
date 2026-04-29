/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class ArchiteezyClientTests {

    private HttpServer server;

    private String baseUrl;

    private final ArchiteezyClient client = new ArchiteezyClient();

    private final List<RecordedRequest> requests = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void onPath(String path, int status, String contentType, byte[] body) {
        server.createContext(path, exchange -> respond(exchange, status, contentType, body));
    }

    private void onPath(String path, int status, String contentType, String body) {
        onPath(path, status, contentType, body == null ? null : body.getBytes(StandardCharsets.UTF_8));
    }

    private void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        recordRequest(exchange);
        var respBytes = body != null ? body : new byte[0];
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
        // Lowercase keys so lookups are case-insensitive (HTTP headers are case-insensitive).
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
                exchange.getRequestURI().getRawQuery(),
                headers,
                body));
    }

    private static String header(RecordedRequest req, String name) {
        return req.headers().get(name.toLowerCase(Locale.ROOT));
    }

    // listModels -----------------------------------------------------------

    @Test
    void listModelsSendsBearerTokenAndQueryParams() throws Exception {
        var json = """
                {
                  "_embedded": {"models": []},
                  "page": {"size": 10, "totalElements": 0, "totalPages": 0, "number": 2}
                }
                """;
        onPath("/api/models", 200, "application/hal+json", json);

        var result = client.listModels(baseUrl, "tkn-123", 2, 10);

        assertNotNull(result);
        assertEquals(1, requests.size());
        var req = requests.get(0);
        assertEquals("GET", req.method());
        assertEquals("/api/models", req.path());
        assertTrue(req.query().contains("page=2"));
        assertTrue(req.query().contains("size=10"));
        assertEquals("Bearer tkn-123", header(req, "Authorization"));
    }

    @Test
    void listModelsThrowsApiExceptionOnServerError() {
        onPath("/api/models", 500, "text/plain", "boom");
        var ex = assertThrows(ApiException.class,
                () -> client.listModels(baseUrl, "tkn", 0, 10));
        assertEquals(500, ex.getStatusCode());
        assertTrue(ex.isServerError());
    }

    @Test
    void listModelsThrowsApiExceptionOnUnauthorized() {
        onPath("/api/models", 401, "text/plain", "no");
        var ex = assertThrows(ApiException.class,
                () -> client.listModels(baseUrl, "bad", 0, 10));
        assertEquals(401, ex.getStatusCode());
        assertTrue(ex.isUnauthorized());
    }

    @Test
    void listModelsOmitsAuthorizationHeaderForNullToken() throws Exception {
        onPath("/api/models", 200, "application/hal+json",
                "{\"_embedded\":{\"models\":[]},\"page\":{\"size\":10,\"totalElements\":0,\"totalPages\":0,\"number\":0}}");

        client.listModels(baseUrl, null, 0, 10);

        assertNull(header(requests.get(0), "Authorization"),
                "Authorization header must be absent for null token");
        assertEquals("application/json", header(requests.get(0), "Accept"));
    }

    // listProjects ---------------------------------------------------------

    @Test
    void listProjectsSendsBearerToken() throws Exception {
        onPath("/api/projects", 200, "application/hal+json", "[]");

        var result = client.listProjects(baseUrl, "tkn-x");

        assertNotNull(result);
        assertEquals(0, result.size());
        assertEquals("Bearer tkn-x", header(requests.get(0), "Authorization"));
        assertTrue(requests.get(0).query().contains("size=100"));
    }

    // getModel -------------------------------------------------------------

    @Test
    void getModelHitsIdEndpoint() throws Exception {
        var json = """
                {"id":"abc","name":"M","_links":{"self":{"href":"%s/api/models/abc"}}}
                """.formatted(baseUrl);
        onPath("/api/models/abc", 200, "application/hal+json", json);

        var model = client.getModel(baseUrl, "tkn", "abc");

        assertNotNull(model);
        assertEquals("abc", model.id());
        assertEquals("/api/models/abc", requests.get(0).path());
    }

    @Test
    void getModelThrowsOnNotFound() {
        onPath("/api/models/missing", 404, "text/plain", "nope");
        var ex = assertThrows(ApiException.class,
                () -> client.getModel(baseUrl, "tkn", "missing"));
        assertEquals(404, ex.getStatusCode());
        assertTrue(ex.isNotFound());
    }

    // getModelContent ------------------------------------------------------

    @Test
    void getModelContentReturnsRawBytes() throws Exception {
        var payload = new byte[] {1, 2, 3, 4, 5};
        onPath("/content/abc", 200, "application/octet-stream", payload);

        var body = client.getModelContent("tkn", baseUrl + "/content/abc",
                com.architeezy.archi.connector.api.CancelSignal.NEVER);

        assertEquals(5, body.length);
        assertEquals(1, body[0]);
        assertEquals("Bearer tkn", header(requests.get(0), "Authorization"));
    }

    @Test
    void getModelContentOmitsAuthHeaderForBlankToken() throws Exception {
        onPath("/public/abc", 200, "application/octet-stream", new byte[] {9});

        client.getModelContent("", baseUrl + "/public/abc",
                com.architeezy.archi.connector.api.CancelSignal.NEVER);

        assertNull(header(requests.get(0), "Authorization"),
                "Authorization header must be absent for blank token");
    }

    // deleteModel ----------------------------------------------------------

    @Test
    void deleteModelSendsDeleteAndAcceptsNoContent() throws Exception {
        onPath("/api/models/abc", 204, null, (byte[]) null);

        client.deleteModel("tkn", baseUrl + "/api/models/abc");

        var req = requests.get(0);
        assertEquals("DELETE", req.method());
        assertEquals("/api/models/abc", req.path());
        assertEquals("Bearer tkn", header(req, "Authorization"));
    }

    @Test
    void deleteModelThrowsOnNotFound() {
        onPath("/api/models/missing", 404, null, (byte[]) null);
        var ex = assertThrows(ApiException.class,
                () -> client.deleteModel("tkn", baseUrl + "/api/models/missing"));
        assertEquals(404, ex.getStatusCode());
        assertTrue(ex.isNotFound());
    }

    // pushModelContent -----------------------------------------------------

    @Test
    void pushModelContentPutsBytesToContentEndpoint() throws Exception {
        var responseJson = """
                {"id":"abc","name":"N","lastModificationDateTime":"2026-01-02T03:04:05Z",
                 "_links":{"self":{"href":"%s/api/models/abc"}}}
                """.formatted(baseUrl);
        onPath("/api/models/abc/content", 200, "application/hal+json", responseJson);

        var payload = "bytes".getBytes(StandardCharsets.UTF_8);
        var updated = client.pushModelContent("tkn", baseUrl + "/api/models/abc", payload);

        assertNotNull(updated);
        var req = requests.get(0);
        assertEquals("PUT", req.method());
        assertEquals("/api/models/abc/content", req.path());
        assertTrue(req.query().contains("format=archimate"));
        assertEquals("application/octet-stream", header(req, "Content-Type"));
        assertEquals(5, req.body().length);
    }

    @Test
    void pushModelContentReturnsNullWhenResponseHasNoMetadata() throws Exception {
        onPath("/api/models/abc/content", 200, "text/plain", "ok");

        var result = client.pushModelContent("tkn", baseUrl + "/api/models/abc", new byte[] {1});

        assertNull(result);
    }

    // exportModel (multipart) ---------------------------------------------

    @Test
    void exportModelSendsMultipartWithBothParts() throws Exception {
        var responseJson = """
                {"id":"u","name":"U","_links":{"self":{"href":"%s/api/models/u"}}}
                """.formatted(baseUrl);
        var callCount = new AtomicInteger();
        server.createContext("/api/models",
                exchange -> exportHandler(exchange, callCount, responseJson));

        var content = "archi-bytes".getBytes(StandardCharsets.UTF_8);
        var created = client.exportModel(baseUrl, "tkn", "proj-1", "file.archimate", content);

        assertEquals("u", created.id());
        assertEquals(1, callCount.get());
        var req = requests.get(0);
        assertEquals("POST", req.method());
        var ct = header(req, "Content-Type");
        assertTrue(ct != null && ct.startsWith("multipart/form-data; boundary=----ArchiteezyBoundary"), ct);
        var body = new String(req.body(), StandardCharsets.UTF_8);
        assertTrue(body.contains("name=\"entity\""), body);
        assertTrue(body.contains("\"projectId\":\"proj-1\""), body);
        assertTrue(body.contains("name=\"content\"; filename=\"file.archimate\""), body);
        assertTrue(body.contains("archi-bytes"), body);
    }

    private void exportHandler(HttpExchange exchange, AtomicInteger callCount, String responseJson)
            throws IOException {
        callCount.incrementAndGet();
        respond(exchange, 201, "application/hal+json", responseJson.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void exportModelFollowsLocationHeaderWhenBodyIsEmpty() throws Exception {
        var metadataJson = """
                {"id":"u","name":"U","_links":{"self":{"href":"%s/api/models/u"}}}
                """.formatted(baseUrl);

        // POST to /api/models returns 201 + Location header and empty body.
        // Client then follows Location with a GET to /api/models/u.
        server.createContext("/api/models/u",
                exchange -> respond(exchange, 200, "application/hal+json",
                        metadataJson.getBytes(StandardCharsets.UTF_8)));
        server.createContext("/api/models", exchange -> {
            recordRequest(exchange);
            exchange.getResponseHeaders().add("Location", baseUrl + "/api/models/u");
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });

        var created = client.exportModel(baseUrl, "tkn", "p", "f.archimate",
                "x".getBytes(StandardCharsets.UTF_8));

        assertEquals("u", created.id());
        assertEquals(2, requests.size());
        assertEquals("POST", requests.get(0).method());
        assertEquals("GET", requests.get(1).method());
        assertEquals("/api/models/u", requests.get(1).path());
    }

    @Test
    void exportModelThrowsWhenNeitherBodyNorLocationReturned() {
        server.createContext("/api/models", exchange -> {
            recordRequest(exchange);
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });

        var ex = assertThrows(ApiException.class,
                () -> client.exportModel(baseUrl, "tkn", "p", "f.archimate", new byte[] {0}));
        assertTrue(ex.getMessage().contains("model metadata"), ex.getMessage());
    }

    @Test
    void exportModelThrowsOnErrorStatus() {
        onPath("/api/models", 500, "text/plain", "boom");
        var ex = assertThrows(ApiException.class,
                () -> client.exportModel(baseUrl, "tkn", "p", "f.archimate", new byte[] {1}));
        assertEquals(500, ex.getStatusCode());
        assertTrue(ex.isServerError());
    }

    @Test
    void pushModelContentThrowsOnErrorStatus() {
        onPath("/api/models/abc/content", 403, "text/plain", "nope");
        var ex = assertThrows(ApiException.class,
                () -> client.pushModelContent("tkn", baseUrl + "/api/models/abc", new byte[] {1}));
        assertEquals(403, ex.getStatusCode());
    }

    private record RecordedRequest(String method, String path, String query,
            Map<String, String> headers, byte[] body) {
    }

}

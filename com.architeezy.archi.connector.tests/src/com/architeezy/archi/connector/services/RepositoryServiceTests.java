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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jface.preference.PreferenceStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.architeezy.archi.connector.api.ArchiteezyClient;
import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.auth.MemorySecurePreferences;
import com.architeezy.archi.connector.auth.OAuthManager;
import com.architeezy.archi.connector.auth.ProfileRegistry;
import com.architeezy.archi.connector.auth.ProfileStatus;
import com.architeezy.archi.connector.auth.TokenStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class RepositoryServiceTests {

    private HttpServer server;

    private String baseUrl;

    private ArchiteezyClient client;

    private AuthService authService;

    private TokenStore tokens;

    private ProfileRegistry registry;

    private RepositoryService service;

    private ConnectionProfile profile;

    private List<HttpExchange> captured;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        captured = new ArrayList<>();

        client = new ArchiteezyClient();
        var securePrefs = new MemorySecurePreferences();
        tokens = new TokenStore(() -> securePrefs);
        registry = new ProfileRegistry(new PreferenceStore(), tokens);
        registry.removeProfile("Architeezy");
        authService = new AuthService(new OAuthManager(), tokens, registry);
        service = new RepositoryService(client, authService);

        profile = new ConnectionProfile("p", baseUrl, "cid");
        registry.addProfile(profile, baseUrl + "/auth", baseUrl + "/token");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // --- listModels ----------------------------------------------------

    @Test
    void listModelsDelegatesWithTokenWhenConnected() throws Exception {
        profile.setStatus(ProfileStatus.CONNECTED);
        tokens.saveTokens(baseUrl, "tok-xyz", "R", Instant.now().getEpochSecond() + 3600);

        var authHeader = new AtomicReference<String>();
        server.createContext("/api/models", ex -> {
            captured.add(ex);
            authHeader.set(ex.getRequestHeaders().getFirst("Authorization"));
            var body = "{\"_embedded\":{\"models\":[]},\"totalPages\":1,\"totalElements\":0,\"number\":0}";
            respond(ex, 200, body);
        });

        var result = service.listModels(profile, 0, 10);

        assertNotNull(result);
        assertEquals(0, result.items().size());
        assertEquals("Bearer tok-xyz", authHeader.get());
    }

    @Test
    void listModelsSendsNoTokenWhenDisconnected() throws Exception {
        profile.setStatus(ProfileStatus.DISCONNECTED);

        var authHeader = new AtomicReference<String>();
        server.createContext("/api/models", ex -> {
            authHeader.set(ex.getRequestHeaders().getFirst("Authorization"));
            respond(ex, 200,
                    "{\"_embedded\":{\"models\":[]},\"totalPages\":1,\"totalElements\":0,\"number\":0}");
        });

        service.listModels(profile, 0, 10);

        org.junit.jupiter.api.Assertions.assertNull(authHeader.get());
    }

    // --- listProjects --------------------------------------------------

    @Test
    void listProjectsRequiresValidToken() throws Exception {
        profile.setStatus(ProfileStatus.CONNECTED);
        tokens.saveTokens(baseUrl, "tok-projects", "R", Instant.now().getEpochSecond() + 3600);

        var seen = new AtomicReference<String>();
        server.createContext("/api/projects", ex -> {
            seen.set(ex.getRequestHeaders().getFirst("Authorization"));
            respond(ex, 200, "{\"_embedded\":{\"projects\":[]}}");
        });

        var projects = service.listProjects(profile);
        assertTrue(projects.isEmpty());
        assertEquals("Bearer tok-projects", seen.get());
    }

    // --- deleteModel ---------------------------------------------------

    @Test
    void deleteModelSendsDeleteWithAuth() throws Exception {
        profile.setStatus(ProfileStatus.CONNECTED);
        tokens.saveTokens(baseUrl, "tok-del", "R", Instant.now().getEpochSecond() + 3600);

        var method = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        server.createContext("/api/models/42", ex -> {
            method.set(ex.getRequestMethod());
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            ex.sendResponseHeaders(204, -1);
            ex.close();
        });

        var remote = new RemoteModel("42", "Name", "Desc", "author", "2026-01-01",
                baseUrl + "/api/models/42", baseUrl + "/api/models/42/content");

        service.deleteModel(profile, remote);

        assertEquals("DELETE", method.get());
        assertEquals("Bearer tok-del", auth.get());
    }

    @Test
    void deleteModelPropagatesApiErrors() {
        profile.setStatus(ProfileStatus.CONNECTED);
        tokens.saveTokens(baseUrl, "tok-del", "R", Instant.now().getEpochSecond() + 3600);

        server.createContext("/api/models/99", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });

        var remote = new RemoteModel("99", "n", "d", "a", "t",
                baseUrl + "/api/models/99", baseUrl + "/api/models/99/content");

        assertThrows(Exception.class, () -> service.deleteModel(profile, remote));
    }

    // --- helper --------------------------------------------------------

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

}

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jface.preference.PreferenceStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.api.ArchiteezyClient;
import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.auth.MemorySecurePreferences;
import com.architeezy.archi.connector.auth.OAuthManager;
import com.architeezy.archi.connector.auth.ProfileRegistry;
import com.architeezy.archi.connector.auth.ProfileStatus;
import com.architeezy.archi.connector.auth.TokenStore;
import com.architeezy.archi.connector.io.TrackedModelStore;
import com.architeezy.archi.connector.model.ConnectorProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class UpdateCheckServiceCheckModelTests {

    @TempDir
    Path stateDir;

    private HttpServer server;

    private String baseUrl;

    private UpdateCheckService service;

    private IArchimateModel model;

    private String modelId;

    private String modelUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        final var securePrefs = new MemorySecurePreferences();
        final var tokens = new TokenStore(() -> securePrefs);
        final var registry = new ProfileRegistry(new PreferenceStore(), tokens);
        registry.removeProfile("Architeezy");
        var profile = new ConnectionProfile("p", baseUrl, "cid");
        profile.setStatus(ProfileStatus.CONNECTED);
        registry.addProfile(profile, baseUrl + "/auth", baseUrl + "/token");
        tokens.saveTokens(baseUrl, "tok", "R", Instant.now().getEpochSecond() + 3600);

        service = new UpdateCheckService(new ArchiteezyClient(),
                new AuthService(new OAuthManager(), tokens, registry),
                registry,
                new TrackedModelStore(() -> stateDir),
                null);

        modelId = UUID.randomUUID().toString();
        modelUrl = baseUrl + "/api/models/" + modelId;
        model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setDefaults();
        ConnectorProperties.setProperty(model, ConnectorProperties.KEY_URL, modelUrl);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void untrackedModelReturnsFalse() {
        var untracked = IArchimateFactory.eINSTANCE.createArchimateModel();
        untracked.setDefaults();
        assertFalse(service.checkModel(untracked));
    }

    @Test
    void newerRemoteRecordsPendingUpdateAndReturnsTrueOnce() {
        stubModelEndpoint("2026-02-01T00:00:00Z");
        // local last-seen date is older
        var trackedStore = new TrackedModelStore(() -> stateDir);
        trackedStore.setLastModified(modelId, "2025-01-01T00:00:00Z");

        assertTrue(service.checkModel(model), "first observation should flip state");
        assertTrue(service.hasUpdate(model));
        assertNotNull(service.getAvailableUpdate(model));

        // Second invocation: same remote, marker already present -> not a state change.
        assertFalse(service.checkModel(model));
        assertTrue(service.hasUpdate(model));
    }

    @Test
    void stableRemoteLeavesNoPendingUpdate() {
        var date = "2026-02-01T00:00:00Z";
        stubModelEndpoint(date);

        var trackedStore = new TrackedModelStore(() -> stateDir);
        trackedStore.setLastModified(modelId, date);

        assertFalse(service.checkModel(model));
        assertFalse(service.hasUpdate(model));
        assertNull(service.getAvailableUpdate(model));
    }

    @Test
    void networkErrorReturnsFalseAndKeepsState() {
        server.createContext("/api/models/" + modelId, ex -> {
            ex.sendResponseHeaders(500, -1);
            ex.close();
        });

        assertFalse(service.checkModel(model));
        assertFalse(service.hasUpdate(model));
    }

    @Test
    void clearUpdateFiresListenerWhenMarkerWasPresent() {
        stubModelEndpoint("2026-02-01T00:00:00Z");
        assertTrue(service.checkModel(model));
        assertTrue(service.hasUpdate(model));

        var count = new int[1];
        service.addListener(() -> count[0]++);

        service.clearUpdate(model);
        assertFalse(service.hasUpdate(model));
        assertEquals(1, count[0]);
    }

    // --- helpers -------------------------------------------------------

    private void stubModelEndpoint(String lastModified) {
        var path = "/api/models/" + modelId;
        var contentUrl = baseUrl + path + "/content";
        var body = "{\"id\":\"" + modelId + "\",\"name\":\"N\",\"description\":\"d\","
                + "\"creator\":{\"id\":\"u1\",\"name\":\"a\"},"
                + "\"lastModificationDateTime\":\"" + lastModified + "\","
                + "\"_links\":{\"self\":{\"href\":\"" + baseUrl + path + "\"},"
                + "\"content\":[{\"title\":\"ArchiMate\",\"href\":\"" + contentUrl + "\"}]}}";
        var sink = new AtomicReference<HttpExchange>();
        server.createContext(path, ex -> {
            sink.set(ex);
            respond(ex, 200, body);
        });
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

}

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import org.eclipse.jface.preference.PreferenceStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.archimatetool.model.IArchimateFactory;
import com.architeezy.archi.connector.api.ArchiteezyClient;
import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.auth.MemorySecurePreferences;
import com.architeezy.archi.connector.auth.OAuthManager;
import com.architeezy.archi.connector.auth.ProfileRegistry;
import com.architeezy.archi.connector.auth.ProfileStatus;
import com.architeezy.archi.connector.auth.TokenStore;
import com.architeezy.archi.connector.io.ModelSerializer;
import com.architeezy.archi.connector.io.SnapshotStore;
import com.architeezy.archi.connector.io.SnapshotSupport;
import com.architeezy.archi.connector.io.TrackedModelStore;
import com.architeezy.archi.connector.model.ConnectorProperties;
import com.architeezy.archi.connector.model.FakeEditorModelManager;
import com.sun.net.httpserver.HttpServer;

class ModelImportServiceTests {

    @TempDir
    Path workDir;

    @TempDir
    Path stateDir;

    private HttpServer server;

    private String baseUrl;

    private ModelImportService service;

    private FakeEditorModelManager editorManager;

    private TrackedModelStore trackedModels;

    private SnapshotStore snapshotStore;

    private byte[] remoteBytes;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        var sample = IArchimateFactory.eINSTANCE.createArchimateModel();
        sample.setName("Remote Sample");
        sample.setDefaults();
        var serializer = new ModelSerializer();
        remoteBytes = serializer.serialize(sample);

        server.createContext("/content", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.sendResponseHeaders(200, remoteBytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(remoteBytes);
            }
            exchange.close();
        });

        Supplier<Path> stateSupplier = () -> stateDir;
        snapshotStore = new SnapshotStore(stateSupplier);
        trackedModels = new TrackedModelStore(stateSupplier);
        editorManager = new FakeEditorModelManager();
        var disconnectedPrefs = new MemorySecurePreferences();
        var disconnectedTokens = new TokenStore(() -> disconnectedPrefs);
        var disconnectedRegistry = new ProfileRegistry(new PreferenceStore(), disconnectedTokens);
        disconnectedRegistry.removeProfile("Architeezy");
        var authService = new AuthService(
                new OAuthManager(), disconnectedTokens, disconnectedRegistry);
        service = new ModelImportService(
                new ArchiteezyClient(),
                authService,
                serializer,
                trackedModels,
                new SnapshotSupport(serializer, snapshotStore),
                editorManager,
                DirectUiSynchronizer.INSTANCE);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private RemoteModel remote() {
        return new RemoteModel(
                "m1",
                "Sample",
                null,
                null,
                "2026-01-01T00:00:00Z",
                baseUrl + "/models/m1",
                baseUrl + "/content");
    }

    private ConnectionProfile disconnectedProfile() {
        var p = new ConnectionProfile("local", baseUrl, "cid");
        p.setStatus(ProfileStatus.DISCONNECTED);
        return p;
    }

    private ConnectionProfile connectedProfile() {
        var p = new ConnectionProfile("local", baseUrl, "cid");
        p.setStatus(ProfileStatus.CONNECTED);
        // AuthService reads a pre-seeded token from the store; the refresh path
        // is not exercised (the token isn't near expiry).
        return p;
    }

    @Test
    void disconnectedImportOpensSavesAndLeavesTargetOnDisk() throws Exception {
        var target = new File(workDir.toFile(), "imported.archimate");

        var model = service.importModel(disconnectedProfile(), remote(), target, null);

        assertNotNull(model);
        assertTrue(target.isFile(), "target file must remain on success");
        assertTrue(editorManager.getModels().contains(model), "model must be opened in the editor");
        assertFalse(editorManager.isModelDirty(model), "model must be clean after save");
        // DISCONNECTED: no url property, no snapshot, no tracked entry.
        assertEquals(null, ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL));
        assertFalse(snapshotStore.hasSnapshot(ConnectorProperties.extractModelId(remote().selfUrl())));
        assertEquals(null, trackedModels.getLastModified(ConnectorProperties.extractModelId(remote().selfUrl())));
    }

    @Test
    void openErrorDeletesTargetAndPropagates() {
        var target = new File(workDir.toFile(), "should-be-cleaned.archimate");
        editorManager.openError = new RuntimeException("open failed");

        var thrown = assertThrows(Exception.class,
                () -> service.importModel(disconnectedProfile(), remote(), target, null));
        assertEquals("open failed", thrown.getMessage());

        assertFalse(target.exists(), "target file must be cleaned up on open failure");
        assertTrue(editorManager.getModels().isEmpty(), "failed open must not register the model");
    }

    @Test
    void saveErrorDeletesTargetAndPropagates() {
        var target = new File(workDir.toFile(), "save-fail.archimate");
        editorManager.saveError = new IOException("save failed");

        var thrown = assertThrows(Exception.class,
                () -> service.importModel(disconnectedProfile(), remote(), target, null));
        assertEquals("save failed", thrown.getMessage());

        assertFalse(target.exists(), "target file must be cleaned up on save failure");
        assertEquals(1, editorManager.getModels().size(),
                "model was opened before save failed");
    }

    @Test
    void connectedImportSetsUrlTracksAndWritesSnapshot() throws Exception {
        // Pre-seed a valid token so AuthService.getValidAccessToken returns without refresh.
        var profile = connectedProfile();
        var connectedPrefs = new MemorySecurePreferences();
        var tokens = new TokenStore(() -> connectedPrefs);
        var registry = new ProfileRegistry(new PreferenceStore(), tokens);
        registry.removeProfile("Architeezy");
        registry.addProfile(profile, baseUrl + "/auth", baseUrl + "/token");
        tokens.saveTokens(profile.getServerUrl(), "AT", "RT",
                java.time.Instant.now().getEpochSecond() + 3600);
        Supplier<Path> stateSupplier = () -> stateDir;
        var snapshots = new SnapshotStore(stateSupplier);
        var tracked = new TrackedModelStore(stateSupplier);
        var serializer = new ModelSerializer();
        var connected = new ModelImportService(
                new ArchiteezyClient(),
                new AuthService(new OAuthManager(), tokens, registry),
                serializer,
                tracked,
                new SnapshotSupport(serializer, snapshots),
                editorManager,
                DirectUiSynchronizer.INSTANCE);

        var target = new File(workDir.toFile(), "connected.archimate");

        var model = connected.importModel(profile, remote(), target, null);

        var modelId = ConnectorProperties.extractModelId(remote().selfUrl());
        assertEquals(remote().selfUrl(), ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL));
        assertEquals("2026-01-01T00:00:00Z", tracked.getLastModified(modelId));
        assertTrue(snapshots.hasSnapshot(modelId), "snapshot should be written after configure");
        assertTrue(Files.exists(target.toPath()));

        // saveModel should have persisted the post-open KEY_URL mutation back to disk,
        // otherwise re-opening the file would not round-trip the connector metadata.
        var reloaded = serializer.deserialize(Files.readAllBytes(target.toPath()),
                new File(workDir.toFile(), "reloaded.archimate"));
        assertEquals(remote().selfUrl(),
                ConnectorProperties.getProperty(reloaded, ConnectorProperties.KEY_URL),
                "the URL property set after openModel must survive saveModel to disk");
    }

}

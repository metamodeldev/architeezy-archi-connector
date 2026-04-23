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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.merge.IMerger;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.gef.commands.CommandStack;
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
import com.architeezy.archi.connector.io.ModelSerializer;
import com.architeezy.archi.connector.io.SnapshotStore;
import com.architeezy.archi.connector.io.TrackedModelStore;
import com.architeezy.archi.connector.model.ConnectorProperties;
import com.architeezy.archi.connector.model.FakeEditorModelManager;
import com.architeezy.archi.connector.model.PullOutcome;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class ModelSyncServiceTests {

    private static final String MODEL_ID = "m1";

    @TempDir
    Path workDir;

    @TempDir
    Path stateDir;

    private HttpServer server;

    private String baseUrl;

    private String modelUrl;

    private ModelSerializer serializer;

    private SnapshotStore snapshotStore;

    private TrackedModelStore trackedModelStore;

    private FakeEditorModelManager editorManager;

    private NullResolver resolver;

    private byte[] remoteBytes;

    private AtomicReference<byte[]> uploadedBytes;

    private String remoteLastModified;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        modelUrl = baseUrl + "/api/models/" + MODEL_ID;
        remoteLastModified = "2026-04-01T10:00:00Z";
        uploadedBytes = new AtomicReference<>();

        serializer = new ModelSerializer();
        Supplier<Path> stateSupplier = () -> stateDir;
        snapshotStore = new SnapshotStore(stateSupplier);
        trackedModelStore = new TrackedModelStore(stateSupplier);
        editorManager = new FakeEditorModelManager();
        resolver = new NullResolver();

        server.createContext("/api/models/" + MODEL_ID, this::handleModelEndpoint);
        server.createContext("/content/" + MODEL_ID, this::handleContent);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleModelEndpoint(HttpExchange exchange) throws IOException {
        var method = exchange.getRequestMethod();
        if ("PUT".equals(method)) {
            // {modelUrl}/content?format=archimate
            uploadedBytes.set(exchange.getRequestBody().readAllBytes());
            remoteLastModified = "2026-04-01T11:00:00Z"; // server-assigned bump
            var body = modelJson().getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
            exchange.close();
            return;
        }
        var body = modelJson().getBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var os = exchange.getResponseBody()) {
            os.write(body);
        }
        exchange.close();
    }

    private void handleContent(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/xml");
        exchange.sendResponseHeaders(200, remoteBytes.length);
        try (var os = exchange.getResponseBody()) {
            os.write(remoteBytes);
        }
        exchange.close();
    }

    private String modelJson() {
        return "{"
                + "\"id\":\"" + MODEL_ID + "\","
                + "\"name\":\"Sample\","
                + "\"lastModificationDateTime\":\"" + remoteLastModified + "\","
                + "\"_links\":{"
                + "\"self\":{\"href\":\"" + modelUrl + "\"},"
                + "\"content\":["
                + "{\"title\":\"ArchiMate\",\"href\":\"" + baseUrl + "/content/" + MODEL_ID + "\"}"
                + "]}}";
    }

    private IArchimateModel newModel(String name) {
        var model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setName(name);
        model.setDefaults();
        model.setFile(workDir.resolve(MODEL_ID + ".archimate").toFile());
        // Archi's production EditorModelManager attaches a CommandStack when
        // the model is opened; ModelImporter.doImport needs one. The fake
        // editor manager doesn't do that, so we attach one manually here.
        model.setAdapter(CommandStack.class, new CommandStack());
        ConnectorProperties.setProperty(model, ConnectorProperties.KEY_URL, modelUrl);
        editorManager.register(model);
        return model;
    }

    private ModelSyncService disconnectedService() {
        var prefs = new MemorySecurePreferences();
        var tokens = new TokenStore(() -> prefs);
        var registry = new ProfileRegistry(new PreferenceStore(), tokens);
        registry.removeProfile("Architeezy");
        var authService = new AuthService(new OAuthManager(), tokens, registry);
        var mergeService = new MergeService(serializer, resolver);
        var localChangeService = new LocalChangeService(snapshotStore, serializer, editorManager);
        var updateCheckService = new UpdateCheckService(new ArchiteezyClient(), authService, registry,
                trackedModelStore, editorManager);
        return new ModelSyncService(new ArchiteezyClient(), authService, registry, snapshotStore,
                serializer, trackedModelStore, mergeService, localChangeService, updateCheckService,
                editorManager, DirectUiSynchronizer.INSTANCE);
    }

    private ModelSyncService connectedService() {
        var prefs = new MemorySecurePreferences();
        var tokens = new TokenStore(() -> prefs);
        var registry = new ProfileRegistry(new PreferenceStore(), tokens);
        registry.removeProfile("Architeezy");
        var profile = new ConnectionProfile("local", baseUrl, "cid");
        profile.setStatus(ProfileStatus.CONNECTED);
        registry.addProfile(profile, baseUrl + "/auth", baseUrl + "/token");
        tokens.saveTokens(baseUrl, "AT", "RT", java.time.Instant.now().getEpochSecond() + 3600);
        var authService = new AuthService(new OAuthManager(), tokens, registry);
        var mergeService = new MergeService(serializer, resolver);
        var localChangeService = new LocalChangeService(snapshotStore, serializer, editorManager);
        var updateCheckService = new UpdateCheckService(new ArchiteezyClient(), authService, registry,
                trackedModelStore, editorManager);
        return new ModelSyncService(new ArchiteezyClient(), authService, registry, snapshotStore,
                serializer, trackedModelStore, mergeService, localChangeService, updateCheckService,
                editorManager, DirectUiSynchronizer.INSTANCE);
    }

    // ------------------------------------------------------------------
    // pullModel

    @Test
    void pullThrowsWhenModelNotTracked() {
        var model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setDefaults();

        var service = disconnectedService();
        assertThrows(IllegalStateException.class,
                () -> service.pullModel(model, null));
    }

    @Test
    void pullReturnsUpToDateWhenLocalAndRemoteMatchBase() throws Exception {
        var model = newModel("Sample");
        var bytes = serializer.serialize(model);
        snapshotStore.saveSnapshot(MODEL_ID, bytes);
        remoteBytes = bytes;

        var outcome = disconnectedService().pullModel(model, null).outcome();

        assertEquals(PullOutcome.UP_TO_DATE, outcome);
        assertArrayEquals(bytes, snapshotStore.loadSnapshot(MODEL_ID),
                "snapshot must be unchanged when nothing moved");
    }

    @Test
    void pullReturnsAppliedWhenNoSnapshotAndRemoteDiffers() throws Exception {
        // No snapshot -> scenario = SIMPLE_PULL.
        var remote = IArchimateFactory.eINSTANCE.createArchimateModel();
        remote.setName("Server-side renamed");
        remote.setDefaults();
        remoteBytes = serializer.serialize(remote);

        var model = newModel("Sample");
        var outcome = disconnectedService().pullModel(model, null).outcome();

        assertEquals(PullOutcome.APPLIED, outcome);
        assertEquals("Server-side renamed", model.getName(),
                "importer must propagate the remote name onto the live model");
        assertTrue(snapshotStore.hasSnapshot(MODEL_ID),
                "snapshot must be saved after a successful pull");
        assertEquals(remoteLastModified, trackedModelStore.getLastModified(MODEL_ID));
    }

    @Test
    void pullReturnsRemoteUnchangedWhenLocalDivergedButRemoteIsBase() throws Exception {
        var model = newModel("Base name");
        var baseBytes = serializer.serialize(model);
        snapshotStore.saveSnapshot(MODEL_ID, baseBytes);
        remoteBytes = baseBytes; // remote == base

        model.setName("Locally renamed"); // local != base

        var outcome = disconnectedService().pullModel(model, null).outcome();

        assertEquals(PullOutcome.REMOTE_UNCHANGED, outcome);
        assertEquals("Locally renamed", model.getName(),
                "local changes must not be clobbered when remote did not move");
    }

    @Test
    void pullReturnsAppliedForDivergedDisjointChanges() throws Exception {
        var model = newModel("Shared");
        var baseBytes = serializer.serialize(model);
        snapshotStore.saveSnapshot(MODEL_ID, baseBytes);

        // Remote adds an element in the Business folder.
        var remote = serializer.deserializeInMemory(baseBytes);
        var remoteActor = IArchimateFactory.eINSTANCE.createBusinessActor();
        remoteActor.setName("RemoteActor");
        folderByName(remote, "Business").getElements().add(remoteActor);
        remoteBytes = serializer.serialize(remote);

        // Local adds a different element in the Application folder.
        var localApp = IArchimateFactory.eINSTANCE.createApplicationComponent();
        localApp.setName("LocalComponent");
        folderByName(model, "Application").getElements().add(localApp);

        var outcome = disconnectedService().pullModel(model, null).outcome();

        assertEquals(PullOutcome.APPLIED, outcome);
        assertTrue(containsElementNamed(model, "Business", "RemoteActor"),
                "remote element must be merged into the live model");
        assertTrue(containsElementNamed(model, "Application", "LocalComponent"),
                "local element must be preserved");
    }

    // ------------------------------------------------------------------
    // pushModel

    @Test
    void pushThrowsWhenModelNotTracked() {
        var model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setDefaults();

        var service = connectedService();
        assertThrows(IllegalStateException.class,
                () -> service.pushModel(model, null));
    }

    @Test
    void pushThrowsWhenNoConnectedProfile() {
        var model = newModel("Sample");

        var service = disconnectedService();
        var ex = assertThrows(IllegalStateException.class,
                () -> service.pushModel(model, null));
        assertTrue(ex.getMessage().contains("Not authenticated"), ex.getMessage());
    }

    @Test
    void pushUploadsLocalWhenRemoteIsBase() throws Exception {
        var model = newModel("Base");
        var baseBytes = serializer.serialize(model);
        snapshotStore.saveSnapshot(MODEL_ID, baseBytes);
        remoteBytes = baseBytes; // remote unchanged since base

        model.setName("Pushed from local"); // local diverged from base

        connectedService().pushModel(model, null);

        assertNotNull(uploadedBytes.get(), "server must have received the PUT body");
        var uploadedModel = serializer.deserializeInMemory(uploadedBytes.get());
        assertEquals("Pushed from local", uploadedModel.getName());
        assertEquals(remoteLastModified, trackedModelStore.getLastModified(MODEL_ID));
    }

    // ------------------------------------------------------------------

    private static com.archimatetool.model.IFolder folderByName(IArchimateModel model, String name) {
        for (var folder : model.getFolders()) {
            if (name.equals(folder.getName())) {
                return folder;
            }
        }
        throw new IllegalStateException("no folder named " + name);
    }

    private static boolean containsElementNamed(IArchimateModel model, String folderName, String elementName) {
        for (var el : folderByName(model, folderName).getElements()) {
            if (el instanceof com.archimatetool.model.INameable n && elementName.equals(n.getName())) {
                return true;
            }
        }
        return false;
    }

    private static final class NullResolver implements IConflictResolver {
        byte[] cannedResponse;

        @Override
        public byte[] resolve(Comparison comparison, Resource localResource, IMerger.Registry registry) {
            return cannedResponse;
        }
    }

}

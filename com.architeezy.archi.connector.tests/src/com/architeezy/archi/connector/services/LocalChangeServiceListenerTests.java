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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.io.ModelSerializer;
import com.architeezy.archi.connector.io.SnapshotStore;
import com.architeezy.archi.connector.model.ConnectorProperties;
import com.architeezy.archi.connector.model.FakeEditorModelManager;

class LocalChangeServiceListenerTests {

    @TempDir
    Path stateDir;

    private LocalChangeService service;

    private SnapshotStore snapshotStore;

    private FakeEditorModelManager editor;

    private IArchimateModel model;

    private String modelUrl;

    @BeforeEach
    void setUp() {
        snapshotStore = new SnapshotStore(() -> stateDir);
        editor = new FakeEditorModelManager();
        service = new LocalChangeService(snapshotStore, new ModelSerializer(), editor);

        var modelId = "mdl-" + UUID.randomUUID();
        modelUrl = "http://localhost/api/models/" + modelId;
        model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setDefaults();
        ConnectorProperties.setProperty(model, ConnectorProperties.KEY_URL, modelUrl);
    }

    @Test
    void startRegistersListenerAndStopRemovesIt() {
        service.start();

        var count = new AtomicInteger();
        service.addListener(count::incrementAndGet);
        editor.fire(IEditorModelManager.COMMAND_STACK_CHANGED, null);
        assertEquals(1, count.get());

        service.stop();
        editor.fire(IEditorModelManager.COMMAND_STACK_CHANGED, null);
        assertEquals(1, count.get());
    }

    @Test
    void nullModelHasNoLocalChanges() {
        assertFalse(service.hasLocalChanges(null));
    }

    @Test
    void dirtyModelAlwaysHasLocalChanges() {
        editor.markDirty(model);
        assertTrue(service.hasLocalChanges(model));
    }

    @Test
    void cleanUntrackedModelHasNoLocalChanges() {
        var untracked = IArchimateFactory.eINSTANCE.createArchimateModel();
        untracked.setDefaults();
        assertFalse(service.hasLocalChanges(untracked));
    }

    @Test
    void clearLocalChangesWithoutUrlIsNoop() {
        var untracked = IArchimateFactory.eINSTANCE.createArchimateModel();
        untracked.setDefaults();
        var count = new AtomicInteger();
        service.addListener(count::incrementAndGet);

        service.clearLocalChanges(untracked);

        assertEquals(0, count.get());
    }

    @Test
    void clearLocalChangesFiresListenerWhenMarkerWasSet() throws IOException {
        snapshotStore.saveSnapshot(ConnectorProperties.extractModelId(modelUrl),
                "<different/>".getBytes());
        assertTrue(service.recheckModel(model), "expected marker to be added");
        assertTrue(service.hasLocalChanges(model));

        var count = new AtomicInteger();
        service.addListener(count::incrementAndGet);

        service.clearLocalChanges(model);

        assertEquals(1, count.get());
        assertFalse(service.hasLocalChanges(model));
    }

    @Test
    void clearLocalChangesSkipsListenerWhenNoMarkerPresent() {
        var count = new AtomicInteger();
        service.addListener(count::incrementAndGet);

        service.clearLocalChanges(model);

        assertEquals(0, count.get());
    }

    @Test
    void removeListenerStopsNotifications() {
        service.start();
        var count = new AtomicInteger();
        Runnable listener = count::incrementAndGet;
        service.addListener(listener);
        service.removeListener(listener);

        editor.fire(IEditorModelManager.COMMAND_STACK_CHANGED, null);

        assertEquals(0, count.get());
    }

    @Test
    void listenerExceptionsDoNotBreakOtherListeners() {
        service.start();
        service.addListener(() -> {
            throw new RuntimeException("bad listener");
        });
        var count = new AtomicInteger();
        service.addListener(count::incrementAndGet);

        editor.fire(IEditorModelManager.COMMAND_STACK_CHANGED, null);

        assertEquals(1, count.get());
    }

    @Test
    void modelRemovedEventClearsMarker() throws IOException {
        snapshotStore.saveSnapshot(ConnectorProperties.extractModelId(modelUrl),
                "<different/>".getBytes());
        assertTrue(service.recheckModel(model));
        assertTrue(service.hasLocalChanges(model));

        service.start();
        editor.fire(IEditorModelManager.PROPERTY_MODEL_REMOVED, model);

        assertFalse(service.hasLocalChanges(model));
    }

}

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.io.ModelSerializer;
import com.architeezy.archi.connector.io.SnapshotStore;
import com.architeezy.archi.connector.model.ConnectorProperties;

class LocalChangeServiceRecheckModelTests {

    @TempDir
    Path stateDir;

    private LocalChangeService service;

    private ModelSerializer serializer;

    private SnapshotStore snapshotStore;

    private IArchimateModel model;

    private String modelId;

    private String modelUrl;

    @BeforeEach
    void setUp() {
        serializer = new ModelSerializer();
        snapshotStore = new SnapshotStore(() -> stateDir);
        service = new LocalChangeService(snapshotStore, serializer, null);

        modelId = "mdl-" + UUID.randomUUID();
        modelUrl = "http://localhost:8080/api/models/" + modelId;
        model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setName("Original");
        model.setDefaults();
        ConnectorProperties.setProperty(model, ConnectorProperties.KEY_URL, modelUrl);
    }

    @Test
    void returnsFalseWhenModelHasNoUrl() {
        var untracked = IArchimateFactory.eINSTANCE.createArchimateModel();
        untracked.setDefaults();
        assertFalse(service.recheckModel(untracked));
    }

    @Test
    void returnsFalseWhenSnapshotMissing() {
        assertFalse(service.recheckModel(model));
    }

    @Test
    void returnsFalseWhenSnapshotMatchesModel() throws IOException {
        var bytes = serializer.serialize(model);
        snapshotStore.saveSnapshot(modelId, bytes);
        assertFalse(service.recheckModel(model));
        // Calling again with no mutation keeps the "not-differing" state.
        assertFalse(service.recheckModel(model));
    }

    @Test
    void flipsToTrueOnceWhenLocalDivergesFromSnapshot() throws IOException {
        var bytes = serializer.serialize(model);
        snapshotStore.saveSnapshot(modelId, bytes);

        model.setName("Mutated");
        assertTrue(service.recheckModel(model));
        // Second invocation: diff already recorded, so no state change.
        assertFalse(service.recheckModel(model));
    }

    @Test
    void flipsBackToCleanWhenModelMatchesSnapshotAgain() throws IOException {
        var bytes = serializer.serialize(model);
        snapshotStore.saveSnapshot(modelId, bytes);

        model.setName("Mutated");
        assertTrue(service.recheckModel(model));

        // Re-align the model with the snapshot: state should transition back.
        model.setName("Original");
        assertTrue(service.recheckModel(model));
        assertFalse(service.recheckModel(model));
    }

}

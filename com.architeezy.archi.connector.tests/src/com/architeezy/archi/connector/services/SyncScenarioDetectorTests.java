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
import com.architeezy.archi.connector.model.SyncScenario;

class SyncScenarioDetectorTests {

    @TempDir
    Path stateDir;

    private ModelSerializer serializer;

    private SnapshotStore snapshotStore;

    private SyncScenarioDetector detector;

    private String modelId;

    private IArchimateModel model;

    @BeforeEach
    void setUp() {
        serializer = new ModelSerializer();
        snapshotStore = new SnapshotStore(() -> stateDir);
        detector = new SyncScenarioDetector(serializer, snapshotStore);
        modelId = "sync-" + UUID.randomUUID();
        model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setName("Original");
        model.setDefaults();
    }

    // classify ---------------------------------------------------------

    @Test
    void upToDateWhenBothSidesEqualBase() {
        assertEquals(SyncScenario.UP_TO_DATE, SyncScenarioDetector.classify(true, true));
    }

    @Test
    void simplePullWhenOnlyRemoteChanged() {
        assertEquals(SyncScenario.SIMPLE_PULL, SyncScenarioDetector.classify(true, false));
    }

    @Test
    void simplePushWhenOnlyLocalChanged() {
        assertEquals(SyncScenario.SIMPLE_PUSH, SyncScenarioDetector.classify(false, true));
    }

    @Test
    void divergedWhenBothSidesChanged() {
        assertEquals(SyncScenario.DIVERGED, SyncScenarioDetector.classify(false, false));
    }

    // detect -----------------------------------------------------------

    @Test
    void detectReturnsSimplePullWhenNoSnapshotExists() throws IOException {
        var remote = "<remote/>".getBytes();
        assertEquals(SyncScenario.SIMPLE_PULL, detector.detect(model, modelId, remote));
    }

    @Test
    void detectReturnsUpToDateWhenLocalAndRemoteMatchBase() throws IOException {
        var bytes = serializer.serialize(model);
        snapshotStore.saveSnapshot(modelId, bytes);
        assertEquals(SyncScenario.UP_TO_DATE, detector.detect(model, modelId, bytes));
    }

    @Test
    void detectReturnsSimplePullWhenOnlyRemoteDiffers() throws IOException {
        var bytes = serializer.serialize(model);
        snapshotStore.saveSnapshot(modelId, bytes);
        var remote = "<different/>".getBytes();
        assertEquals(SyncScenario.SIMPLE_PULL, detector.detect(model, modelId, remote));
    }

    @Test
    void detectReturnsSimplePushWhenOnlyLocalDiffers() throws IOException {
        var baseBytes = serializer.serialize(model);
        snapshotStore.saveSnapshot(modelId, baseBytes);
        model.setName("Mutated");
        assertEquals(SyncScenario.SIMPLE_PUSH, detector.detect(model, modelId, baseBytes));
    }

    @Test
    void detectReturnsDivergedWhenBothSidesDiffer() throws IOException {
        var baseBytes = serializer.serialize(model);
        snapshotStore.saveSnapshot(modelId, baseBytes);
        model.setName("Mutated");
        var remote = "<something-else/>".getBytes();
        assertEquals(SyncScenario.DIVERGED, detector.detect(model, modelId, remote));
    }

}

/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.archimatetool.model.IArchimateFactory;

class SnapshotSupportTests {

    @TempDir
    Path stateDir;

    private ModelSerializer serializer;

    private SnapshotStore store;

    private SnapshotSupport support;

    private String modelId;

    @BeforeEach
    void setUp() {
        serializer = new ModelSerializer();
        store = new SnapshotStore(() -> stateDir);
        support = new SnapshotSupport(serializer, store);
        modelId = "test-" + UUID.randomUUID();
    }

    @Test
    void setSubTaskIgnoresNullMonitor() {
        assertDoesNotThrow(() -> SnapshotSupport.setSubTask(null, "anything"));
    }

    @Test
    void setSubTaskForwardsToMonitor() {
        var captured = new AtomicReference<String>();
        var monitor = new NullProgressMonitor() {
            @Override
            public void subTask(String name) {
                captured.set(name);
            }
        };
        SnapshotSupport.setSubTask(monitor, "Working");
        assertEquals("Working", captured.get());
    }

    @Test
    void saveSnapshotAfterConfigureStoresSerializedBytes() throws IOException {
        var model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setName("Configured");
        model.setDefaults();

        support.saveSnapshotAfterConfigure(model, modelId, null);

        assertTrue(store.hasSnapshot(modelId));
        var expected = serializer.serialize(model);
        assertArrayEquals(expected, store.loadSnapshot(modelId));
    }

    @Test
    void saveSnapshotAfterConfigureReportsSubTask() {
        var model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setDefaults();
        var captured = new AtomicReference<String>();
        var monitor = new NullProgressMonitor() {
            @Override
            public void subTask(String name) {
                captured.set(name);
            }
        };

        support.saveSnapshotAfterConfigure(model, modelId, monitor);

        assertEquals("Saving snapshot", captured.get());
    }

}

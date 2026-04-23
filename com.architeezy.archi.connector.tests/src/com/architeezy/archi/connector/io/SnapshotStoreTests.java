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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotStoreTests {

    @TempDir
    Path stateDir;

    private SnapshotStore store;

    private String modelId;

    @BeforeEach
    void setUp() {
        store = new SnapshotStore(() -> stateDir);
        modelId = "test-" + UUID.randomUUID();
    }

    @Test
    void hasSnapshotReturnsFalseBeforeSave() {
        assertFalse(store.hasSnapshot(modelId));
    }

    @Test
    void saveLoadRoundTrip() throws IOException {
        var data = "hello snapshot".getBytes(StandardCharsets.UTF_8);
        store.saveSnapshot(modelId, data);

        assertTrue(store.hasSnapshot(modelId));
        assertArrayEquals(data, store.loadSnapshot(modelId));
    }

    @Test
    void saveOverwritesExisting() throws IOException {
        store.saveSnapshot(modelId, "first".getBytes(StandardCharsets.UTF_8));
        store.saveSnapshot(modelId, "second".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals("second".getBytes(StandardCharsets.UTF_8),
                store.loadSnapshot(modelId));
    }

    @Test
    void loadMissingSnapshotThrows() {
        assertThrows(IOException.class, () -> store.loadSnapshot(modelId));
    }

    @Test
    void deleteRemovesSnapshot() throws IOException {
        store.saveSnapshot(modelId, new byte[] {1, 2, 3});
        store.deleteSnapshot(modelId);
        assertFalse(store.hasSnapshot(modelId));
    }

    @Test
    void deleteOnMissingIsNoop() {
        store.deleteSnapshot(modelId);
        assertFalse(store.hasSnapshot(modelId));
    }

    @Test
    void sanitizesModelIdForFileSystem() throws IOException {
        var dirty = "weird/id:with*chars?";
        store.saveSnapshot(dirty, new byte[] {7, 8, 9});
        assertTrue(store.hasSnapshot(dirty));
        assertArrayEquals(new byte[] {7, 8, 9}, store.loadSnapshot(dirty));
    }

}

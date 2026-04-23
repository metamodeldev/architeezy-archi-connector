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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackedModelStoreTests {

    @TempDir
    Path stateDir;

    private TrackedModelStore store;

    private String modelId;

    @BeforeEach
    void setUp() {
        store = new TrackedModelStore(() -> stateDir);
        modelId = "test-" + UUID.randomUUID();
    }

    @Test
    void missingEntryReturnsNull() {
        assertNull(store.getLastModified(modelId));
    }

    @Test
    void setAndGetRoundTrip() {
        store.setLastModified(modelId, "2026-04-23T10:00:00Z");
        assertEquals("2026-04-23T10:00:00Z", store.getLastModified(modelId));
    }

    @Test
    void setOverwritesExisting() {
        store.setLastModified(modelId, "2026-04-22T00:00:00Z");
        store.setLastModified(modelId, "2026-04-23T00:00:00Z");
        assertEquals("2026-04-23T00:00:00Z", store.getLastModified(modelId));
    }

    @Test
    void setNullRemovesEntry() {
        store.setLastModified(modelId, "2026-04-23T00:00:00Z");
        store.setLastModified(modelId, null);
        assertNull(store.getLastModified(modelId));
    }

    @Test
    void setBlankRemovesEntry() {
        store.setLastModified(modelId, "2026-04-23T00:00:00Z");
        store.setLastModified(modelId, "   ");
        assertNull(store.getLastModified(modelId));
    }

    @Test
    void removeClearsEntry() {
        store.setLastModified(modelId, "2026-04-23T00:00:00Z");
        store.remove(modelId);
        assertNull(store.getLastModified(modelId));
    }

    @Test
    void nullAndBlankModelIdIgnored() {
        store.setLastModified(null, "x");
        store.setLastModified("", "x");
        store.setLastModified("   ", "x");
        assertNull(store.getLastModified(null));
        assertNull(store.getLastModified(""));
    }

    @Test
    void entriesPersistAcrossReads() {
        var otherId = "test-" + UUID.randomUUID();
        store.setLastModified(modelId, "2026-04-23T00:00:00Z");
        store.setLastModified(otherId, "2026-04-24T00:00:00Z");
        assertEquals("2026-04-23T00:00:00Z", store.getLastModified(modelId));
        assertEquals("2026-04-24T00:00:00Z", store.getLastModified(otherId));
    }

}

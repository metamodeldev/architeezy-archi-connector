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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.archimatetool.model.IArchimateFactory;

class ModelSerializerTests {

    private final ModelSerializer serializer = new ModelSerializer();

    private static com.archimatetool.model.IArchimateModel newModel(String name) {
        var model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setName(name);
        model.setDefaults();
        return model;
    }

    @Test
    void serializeProducesArchiXmiBytes() throws IOException {
        var bytes = serializer.serialize(newModel("Sample"));
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        var head = new String(bytes, 0, Math.min(256, bytes.length), StandardCharsets.UTF_8);
        assertTrue(head.contains("archimate:model"),
                "expected Archi XMI root, got: " + head);
        assertTrue(head.contains("Sample"));
    }

    @Test
    void serializeIsDeterministicForEquivalentModels() throws IOException {
        var model = newModel("Same");
        var a = serializer.serialize(model);
        var b = serializer.serialize(model);
        assertArrayEquals(a, b);
    }

    @Test
    void deserializeInMemoryRoundTrip() throws IOException {
        var original = newModel("RoundTrip");
        var bytes = serializer.serialize(original);

        var restored = serializer.deserializeInMemory(bytes);
        assertNotNull(restored);
        assertEquals("RoundTrip", restored.getName());
    }

    @Test
    void deserializeAttachesModelToTargetFile(@TempDir Path dir) throws IOException {
        var bytes = serializer.serialize(newModel("Attached"));
        var target = dir.resolve("m.archimate").toFile();

        var model = serializer.deserialize(bytes, target);
        assertEquals("Attached", model.getName());
        assertEquals(target, model.getFile());
        assertTrue(target.exists());
    }

    @Test
    void deserializeDeletesTargetOnInvalidData(@TempDir Path dir) {
        var target = dir.resolve("broken.archimate").toFile();
        var garbage = "<<< not xmi >>>".getBytes(StandardCharsets.UTF_8);

        assertThrows(IOException.class,
                () -> serializer.deserialize(garbage, target));
        assertFalse(target.exists(), "broken target file must be cleaned up");
    }

    @Test
    void serializeRemovesTempFile() throws IOException {
        var before = listArchimateTemps();
        serializer.serialize(newModel("Cleanup"));
        var after = listArchimateTemps();
        assertTrue(after <= before, "serialize must clean up its temp file");
    }

    private static int listArchimateTemps() throws IOException {
        var tmp = new File(System.getProperty("java.io.tmpdir")).toPath();
        try (var stream = Files.list(tmp)) {
            return (int) stream
                    .filter(p -> p.getFileName().toString().startsWith("archi-connector-"))
                    .filter(p -> p.getFileName().toString().endsWith(".archimate"))
                    .count();
        }
    }

}

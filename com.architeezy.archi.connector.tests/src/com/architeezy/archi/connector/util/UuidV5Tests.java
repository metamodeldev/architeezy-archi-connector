/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Cross-checks {@link UuidV5} against vectors captured from
 * {@code com.fasterxml.uuid.Generators.nameBasedGenerator(ns).generate(name)}
 * (Jackson UUIDs 5.2.0). Identical output is the contract that lets the
 * connector recompute a representation's {@code targetObjectId} locally.
 */
class UuidV5Tests {

    private static final UUID DNS_NS = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    @Test
    void matchesJacksonForArchiDiagramId() {
        assertEquals(
                UUID.fromString("e639cbed-1a15-5173-8a3a-826afaa97b1c"),
                UuidV5.nameUuid(DNS_NS, "id-d4f8a8b8c3d4e7f6a8b3c4d5e6f7a8b9"));
    }

    @Test
    void matchesJacksonForEmptyName() {
        assertEquals(
                UUID.fromString("4ebd0208-8328-5d69-8c44-ec50939c0967"),
                UuidV5.nameUuid(DNS_NS, ""));
    }

    @Test
    void matchesJacksonForUnicodeName() {
        assertEquals(
                UUID.fromString("62cad4e8-0343-5980-8eab-5d3f5c3d5e56"),
                UuidV5.nameUuid(DNS_NS, "Привет, мир — диаграмма №1"));
    }

    @Test
    void matchesJacksonForZeroNamespace() {
        var ns = UUID.fromString("00000000-0000-0000-0000-000000000000");
        assertEquals(
                UUID.fromString("8a45b09c-7b11-53ab-852f-59e2d2977f80"),
                UuidV5.nameUuid(ns, "id-d4f8a8b8c3d4e7f6a8b3c4d5e6f7a8b9"));
        assertEquals(
                UUID.fromString("191333f6-c83e-5b3b-bdb0-bd483ad1bcb7"),
                UuidV5.nameUuid(ns, "hello world"));
    }

    @Test
    void matchesJacksonForArbitraryNamespace() {
        var ns = UUID.fromString("bc9df95d-6bf8-4797-94e4-8587bd88c57b");
        assertEquals(
                UUID.fromString("9861a072-f583-5bcd-93a0-7bd8f827bb43"),
                UuidV5.nameUuid(ns, "id-d4f8a8b8c3d4e7f6a8b3c4d5e6f7a8b9"));
        assertEquals(
                UUID.fromString("847a6852-6f39-5276-9cd7-2972cd0b3346"),
                UuidV5.nameUuid(ns, "hello world"));
    }

    @Test
    void stampsVersion5AndRfc4122Variant() {
        var u = UuidV5.nameUuid(DNS_NS, "anything");
        assertEquals(5, u.version());
        assertEquals(2, u.variant()); // RFC 4122
    }

}

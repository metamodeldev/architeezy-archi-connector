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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

/**
 * Generates RFC 4122 version 5 (SHA-1, name-based) UUIDs.
 */
public final class UuidV5 {

    private static final int UUID_BYTES = 16;

    private UuidV5() {
    }

    /**
     * Computes a UUID v5 from a namespace UUID and a name. Bit-for-bit identical
     * to {@code Generators.nameBasedGenerator(namespace).generate(name)}.
     *
     * @param namespace the namespace UUID (used as the SHA-1 prefix)
     * @param name the name to hash
     * @return a UUID with version 5 and RFC 4122 variant
     * @throws IllegalStateException if SHA-1 is unavailable in this JRE
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    public static UUID nameUuid(UUID namespace, String name) {
        try {
            var md = MessageDigest.getInstance("SHA-1"); //$NON-NLS-1$
            md.update(toBytes(namespace));
            md.update(name.getBytes(StandardCharsets.UTF_8));
            var b = Arrays.copyOf(md.digest(), UUID_BYTES);
            // RFC 4122 4.3: stamp version (5) into the high nibble of byte 6.
            b[6] = (byte) ((b[6] & 0x0F) | 0x50);
            // RFC 4122 4.1.1: stamp variant (10xx) into the top two bits of byte 8.
            b[8] = (byte) ((b[8] & 0x3F) | 0x80);
            return new UUID(
                    ByteBuffer.wrap(b, 0, Long.BYTES).getLong(),
                    ByteBuffer.wrap(b, Long.BYTES, Long.BYTES).getLong());
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 is mandatory in every JRE per the platform spec.
            throw new IllegalStateException(e);
        }
    }

    private static byte[] toBytes(UUID uuid) {
        return ByteBuffer.allocate(UUID_BYTES)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

}

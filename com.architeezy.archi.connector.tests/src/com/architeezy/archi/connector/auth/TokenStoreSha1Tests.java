/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

class TokenStoreSha1Tests {

    @Test
    void returnsKnownSha1ForEmptyString() {
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", TokenStore.sha1(""));
    }

    @Test
    void returnsKnownSha1ForAsciiInput() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", TokenStore.sha1("abc"));
    }

    @Test
    void isDeterministic() {
        assertEquals(TokenStore.sha1("https://srv.example.com"),
                TokenStore.sha1("https://srv.example.com"));
    }

    @Test
    void differentInputsProduceDifferentHashes() {
        assertNotEquals(TokenStore.sha1("https://a"), TokenStore.sha1("https://b"));
    }

    @Test
    void handlesUtf8InputCorrectly() throws Exception {
        var input = "привет";
        var expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-1").digest(input.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, TokenStore.sha1(input));
    }

    @Test
    void outputIsFortyHexCharacters() {
        var hash = TokenStore.sha1("anything");
        assertEquals(40, hash.length());
        assertEquals(hash, hash.toLowerCase());
    }

}

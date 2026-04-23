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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class OAuthManagerStateTests {

    // 16 bytes encoded as unpadded url-safe base64 = ceil(16 * 4 / 3) = 22 chars.
    private static final int EXPECTED_LENGTH = 22;

    @Test
    void hasExpectedLength() {
        assertEquals(EXPECTED_LENGTH, OAuthManager.generateState().length());
    }

    @Test
    void usesUrlSafeBase64Alphabet() {
        var state = OAuthManager.generateState();
        assertTrue(state.matches("[A-Za-z0-9_-]+"), state);
    }

    @Test
    void isNotPaddedWithEquals() {
        var state = OAuthManager.generateState();
        assertTrue(!state.contains("="), state);
    }

    @Test
    void twoCallsProduceDifferentValues() {
        assertNotEquals(OAuthManager.generateState(), OAuthManager.generateState());
    }

    @Test
    void manyCallsAreAllDistinct() {
        var samples = new HashSet<String>();
        IntStream.range(0, 100).forEach(i -> samples.add(OAuthManager.generateState()));
        assertEquals(100, samples.size());
    }

}

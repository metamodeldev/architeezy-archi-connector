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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class OAuthManagerJsonTests {

    // extractJsonString ----------------------------------------------------

    @Test
    void extractJsonStringReturnsSimpleValue() {
        var json = "{\"access_token\":\"abc123\",\"token_type\":\"Bearer\"}";
        assertEquals("abc123", OAuthManager.extractJsonString(json, "access_token"));
        assertEquals("Bearer", OAuthManager.extractJsonString(json, "token_type"));
    }

    @Test
    void extractJsonStringReturnsNullWhenKeyMissing() {
        var json = "{\"a\":\"1\"}";
        assertNull(OAuthManager.extractJsonString(json, "missing"));
    }

    @Test
    void extractJsonStringHandlesWhitespaceBeforeColon() {
        var json = "{\"name\" : \"Alice\"}";
        assertEquals("Alice", OAuthManager.extractJsonString(json, "name"));
    }

    @Test
    void extractJsonStringReturnsEmptyForEmptyValue() {
        var json = "{\"x\":\"\"}";
        assertEquals("", OAuthManager.extractJsonString(json, "x"));
    }

    // extractJsonLong ------------------------------------------------------

    @Test
    void extractJsonLongReturnsValue() {
        var json = "{\"expires_in\":3600}";
        assertEquals(3600L, OAuthManager.extractJsonLong(json, "expires_in", -1L));
    }

    @Test
    void extractJsonLongReturnsDefaultWhenMissing() {
        assertEquals(42L, OAuthManager.extractJsonLong("{}", "expires_in", 42L));
    }

    @Test
    void extractJsonLongReturnsDefaultWhenNonNumeric() {
        var json = "{\"x\":\"abc\"}";
        assertEquals(7L, OAuthManager.extractJsonLong(json, "x", 7L));
    }

    @Test
    void extractJsonLongReadsLargeValues() {
        var json = "{\"totalElements\":1234567890}";
        assertEquals(1_234_567_890L, OAuthManager.extractJsonLong(json, "totalElements", 0L));
    }

}

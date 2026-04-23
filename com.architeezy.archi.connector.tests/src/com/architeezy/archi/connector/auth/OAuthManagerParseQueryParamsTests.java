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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OAuthManagerParseQueryParamsTests {

    @Test
    void returnsEmptyMapWhenNoQueryString() {
        var map = OAuthManager.parseQueryParams("/callback");
        assertTrue(map.isEmpty());
    }

    @Test
    void parsesSinglePair() {
        var map = OAuthManager.parseQueryParams("/callback?code=abc");
        assertEquals(1, map.size());
        assertEquals("abc", map.get("code"));
    }

    @Test
    void parsesMultiplePairs() {
        var map = OAuthManager.parseQueryParams("/callback?code=abc&state=xyz");
        assertEquals("abc", map.get("code"));
        assertEquals("xyz", map.get("state"));
    }

    @Test
    void stripsHttpVersionSuffix() {
        var map = OAuthManager.parseQueryParams("/callback?code=abc&state=xyz HTTP/1.1");
        assertEquals("abc", map.get("code"));
        assertEquals("xyz", map.get("state"));
    }

    @Test
    void urlDecodesValues() {
        var map = OAuthManager.parseQueryParams("/callback?redirect=http%3A%2F%2Fexample.com%2Fx%20y");
        assertEquals("http://example.com/x y", map.get("redirect"));
    }

    @Test
    void skipsEntriesWithoutValue() {
        var map = OAuthManager.parseQueryParams("/callback?code&state=xyz");
        assertNull(map.get("code"));
        assertEquals("xyz", map.get("state"));
    }

    @Test
    void parsesErrorResponse() {
        var map = OAuthManager.parseQueryParams("/callback?error=access_denied&state=s1");
        assertEquals("access_denied", map.get("error"));
        assertEquals("s1", map.get("state"));
    }

}

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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OAuthManagerValidateUrlTests {

    @Test
    void acceptsHttpUrl() {
        assertDoesNotThrow(() -> OAuthManager.validateUrl("http://example.com/auth", "auth"));
    }

    @Test
    void acceptsHttpsUrl() {
        assertDoesNotThrow(() -> OAuthManager.validateUrl("https://example.com/token", "token"));
    }

    @Test
    void rejectsEmptyString() {
        assertThrows(Exception.class, () -> OAuthManager.validateUrl("", "auth"));
    }

    @Test
    void rejectsSchemeLessString() {
        assertThrows(Exception.class, () -> OAuthManager.validateUrl("example.com", "auth"));
    }

    @Test
    void rejectsSchemeWithoutJavaUrlHandler() {
        assertThrows(OAuthException.class,
                () -> OAuthManager.validateUrl("unknownproto://example.com", "auth"));
    }

    @Test
    void errorMessageMentionsFieldName() {
        var ex = assertThrows(OAuthException.class,
                () -> OAuthManager.validateUrl("unknownproto://example.com", "auth endpoint"));
        assertTrue(ex.getMessage().contains("auth endpoint"), ex.getMessage());
    }

}

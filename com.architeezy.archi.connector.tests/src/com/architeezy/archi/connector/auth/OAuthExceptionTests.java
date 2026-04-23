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
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class OAuthExceptionTests {

    @Test
    void messageOnlyConstructor() {
        var e = new OAuthException("failed");
        assertEquals("failed", e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    void messageAndCauseConstructor() {
        var cause = new RuntimeException("x");
        var e = new OAuthException("failed", cause);
        assertEquals("failed", e.getMessage());
        assertSame(cause, e.getCause());
    }

}

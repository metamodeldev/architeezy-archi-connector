/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class ApiExceptionTests {

    @Test
    void statusCodeAndMessageConstructor() {
        var e = new ApiException(404, "missing");
        assertEquals(404, e.getStatusCode());
        assertEquals("missing", e.getMessage());
        assertTrue(e.isNotFound());
        assertFalse(e.isUnauthorized());
        assertFalse(e.isForbidden());
        assertFalse(e.isServerError());
    }

    @Test
    void unauthorizedAndForbiddenClassification() {
        assertTrue(new ApiException(401, "x").isUnauthorized());
        assertTrue(new ApiException(403, "x").isForbidden());
    }

    @Test
    void serverErrorRangeIs5xx() {
        assertTrue(new ApiException(500, "x").isServerError());
        assertTrue(new ApiException(503, "x").isServerError());
        assertFalse(new ApiException(499, "x").isServerError());
    }

    @Test
    void ioConstructorSetsMinusOneStatus() {
        var cause = new IOException("boom");
        var e = new ApiException("net down", cause);
        assertEquals(-1, e.getStatusCode());
        assertEquals("net down", e.getMessage());
        assertSame(cause, e.getCause());
        assertFalse(e.isServerError());
    }

}

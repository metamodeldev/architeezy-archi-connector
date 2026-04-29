/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RemoteProjectTests {

    @Test
    void toStringReturnsNameWhenPresent() {
        assertEquals("Proj", new RemoteProject("p1", "Proj").toString());
    }

    @Test
    void toStringFallsBackToIdWhenNameNull() {
        assertEquals("p1", new RemoteProject("p1", null).toString());
    }

    @Test
    void accessorsExposeFields() {
        var p = new RemoteProject("id", "name");
        assertEquals("id", p.id());
        assertEquals("name", p.name());
    }

    @Test
    void scopeReferenceIsCaptured() {
        var p = new RemoteProject("id", "name", "scope-1", "Scope One");
        assertEquals("scope-1", p.scopeId());
        assertEquals("Scope One", p.scopeName());
    }

}

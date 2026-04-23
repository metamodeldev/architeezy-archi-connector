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

class RemoteModelTests {

    @Test
    void toStringReturnsNameWhenPresent() {
        var m = new RemoteModel("id1", "Foo", null, null, null, null, null);
        assertEquals("Foo", m.toString());
    }

    @Test
    void toStringFallsBackToIdWhenNameNull() {
        var m = new RemoteModel("id1", null, null, null, null, null, null);
        assertEquals("id1", m.toString());
    }

    @Test
    void accessorsExposeAllFields() {
        var m = new RemoteModel("i", "n", "d", "a", "lm", "s", "c");
        assertEquals("i", m.id());
        assertEquals("n", m.name());
        assertEquals("d", m.description());
        assertEquals("a", m.author());
        assertEquals("lm", m.lastModified());
        assertEquals("s", m.selfUrl());
        assertEquals("c", m.contentUrl());
    }

}

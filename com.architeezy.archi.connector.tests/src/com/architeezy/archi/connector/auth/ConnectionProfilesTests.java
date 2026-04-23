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

import java.util.List;

import org.junit.jupiter.api.Test;

class ConnectionProfilesTests {

    private static ConnectionProfile p(String name) {
        return new ConnectionProfile(name, "https://" + name, "cli");
    }

    @Test
    void returnsIndexOfMatchingName() {
        var list = List.of(p("a"), p("b"), p("c"));
        assertEquals(0, ConnectionProfiles.indexByName(list, "a"));
        assertEquals(2, ConnectionProfiles.indexByName(list, "c"));
    }

    @Test
    void returnsMinusOneWhenNotFound() {
        var list = List.of(p("a"), p("b"));
        assertEquals(-1, ConnectionProfiles.indexByName(list, "z"));
    }

    @Test
    void returnsMinusOneForEmptyList() {
        assertEquals(-1, ConnectionProfiles.indexByName(List.of(), "a"));
    }

    @Test
    void matchesAreCaseSensitive() {
        var list = List.of(p("Main"));
        assertEquals(-1, ConnectionProfiles.indexByName(list, "main"));
        assertEquals(0, ConnectionProfiles.indexByName(list, "Main"));
    }

}

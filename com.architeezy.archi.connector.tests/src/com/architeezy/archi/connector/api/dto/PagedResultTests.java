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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PagedResultTests {

    @Test
    void hasMoreReturnsTrueWhenNotLastPage() {
        var page = new PagedResult<>(List.of("a"), 30L, 3, 0);
        assertTrue(page.hasMore());
    }

    @Test
    void hasMoreReturnsFalseOnLastPage() {
        var page = new PagedResult<>(List.of("a"), 30L, 3, 2);
        assertFalse(page.hasMore());
    }

    @Test
    void hasMoreReturnsFalseForSinglePage() {
        var page = new PagedResult<>(List.of("a"), 1L, 1, 0);
        assertFalse(page.hasMore());
    }

    @Test
    void hasMoreReturnsFalseForEmptyPage() {
        var page = new PagedResult<>(List.of(), 0L, 0, 0);
        assertFalse(page.hasMore());
    }

    @Test
    void accessorsExposeConstructorValues() {
        var items = List.of("x", "y");
        var page = new PagedResult<>(items, 42L, 5, 2);
        assertEquals(items, page.items());
        assertEquals(42L, page.totalElements());
        assertEquals(5, page.totalPages());
        assertEquals(2, page.page());
    }

}

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

import java.util.List;

/**
 * A single page of results from a paginated API response.
 *
 * @param <T> the type of each item
 * @param items the items
 * @param totalElements the total elements
 * @param totalPages the total pages
 * @param page the page
 */
public record PagedResult<T>(List<T> items, long totalElements, int totalPages, int page) {

    /**
     * Returns {@code true} if there is at least one more page after this one.
     *
     * @return {@code true} if more pages exist
     */
    public boolean hasMore() {
        return page < totalPages - 1;
    }

}

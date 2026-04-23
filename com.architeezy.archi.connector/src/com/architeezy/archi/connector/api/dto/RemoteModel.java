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

/**
 * Metadata for a model stored on the Architeezy server.
 *
 * @param id the id
 * @param name the name
 * @param description the description
 * @param author the author
 * @param lastModified the last modified
 * @param selfUrl the self URL
 * @param contentUrl the content URL
 */
public record RemoteModel(
        String id,
        String name,
        String description,
        String author,
        String lastModified,
        String selfUrl,
        String contentUrl) {

    @Override
    public String toString() {
        return name != null ? name : id;
    }

}

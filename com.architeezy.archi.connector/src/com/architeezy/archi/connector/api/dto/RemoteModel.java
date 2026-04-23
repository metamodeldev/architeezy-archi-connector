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
 * @param slug model slug used to build browser URLs
 * @param projectSlug owning project slug
 * @param projectVersion owning project version
 * @param scopeSlug owning scope slug
 */
@SuppressWarnings("checkstyle:ParameterNumber")
public record RemoteModel(
        String id,
        String name,
        String description,
        String author,
        String lastModified,
        String selfUrl,
        String contentUrl,
        String slug,
        String projectSlug,
        String projectVersion,
        String scopeSlug) {

    /**
     * Convenience constructor for callers that only care about the core
     * metadata and do not supply the browser-URL slugs.
     *
     * @param id the id
     * @param name the name
     * @param description the description
     * @param author the author
     * @param lastModified the last modified
     * @param selfUrl the self URL
     * @param contentUrl the content URL
     */
    public RemoteModel(String id, String name, String description, String author,
            String lastModified, String selfUrl, String contentUrl) {
        this(id, name, description, author, lastModified, selfUrl, contentUrl, null, null, null, null);
    }

    @Override
    public String toString() {
        return name != null ? name : id;
    }

}

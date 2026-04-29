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
 * A project on the Architeezy server.
 *
 * @param id the project id
 * @param name the project name
 * @param scopeId id of the owning scope, or {@code null} if not provided
 * @param scopeName name of the owning scope, or {@code null} if not provided
 * @param updatable whether the server advertised an {@code _links.update}
 *        relation, meaning the current user may export models into this
 *        project
 */
public record RemoteProject(String id, String name, String scopeId, String scopeName, boolean updatable) {

    /**
     * Convenience constructor for callers (mostly tests) that do not need the
     * owning scope reference.
     *
     * @param id the project id
     * @param name the project name
     */
    public RemoteProject(String id, String name) {
        this(id, name, null, null, true);
    }

    /**
     * Convenience constructor that omits the {@code updatable} flag (defaults
     * to {@code true}). Used by tests and callers that don't carry HAL link
     * metadata.
     *
     * @param id the project id
     * @param name the project name
     * @param scopeId id of the owning scope
     * @param scopeName name of the owning scope
     */
    public RemoteProject(String id, String name, String scopeId, String scopeName) {
        this(id, name, scopeId, scopeName, true);
    }

    @Override
    public String toString() {
        return name != null ? name : id;
    }

}

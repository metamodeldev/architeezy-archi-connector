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
 * Metadata for a single representation (diagram) on the Architeezy server.
 *
 * @param id the representation id
 * @param slug URL-safe slug, used as the trailing browser-URL segment
 * @param isDefault whether this representation is the default for its target
 *        object
 * @param isRoot whether the target object is the model root - the default
 *        root representation is opened without a trailing slug segment
 */
public record RemoteRepresentation(String id, String slug, boolean isDefault, boolean isRoot) {
}

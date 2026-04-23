/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.io;

/**
 * Utility methods for producing safe filesystem names.
 */
public final class FileNames {

    private FileNames() {
    }

    /**
     * Replaces characters that are reserved on common filesystems (Windows in
     * particular) with underscores. Returns a {@code "model"} default when the
     * input is {@code null}.
     *
     * @param name the desired filename, may be {@code null}
     * @return the sanitized name
     */
    public static String sanitize(String name) {
        if (name == null) {
            return "model"; //$NON-NLS-1$
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_"); //$NON-NLS-1$ //$NON-NLS-2$
    }
}

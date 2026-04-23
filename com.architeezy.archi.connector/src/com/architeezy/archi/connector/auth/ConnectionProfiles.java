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

import java.util.List;

/**
 * Utility methods for collections of {@link ConnectionProfile}.
 */
public final class ConnectionProfiles {

    private ConnectionProfiles() {
    }

    /**
     * Returns the index of the first profile whose {@link ConnectionProfile#getName() name}
     * equals {@code name}, or {@code -1} if not found. Matching is case-sensitive.
     *
     * @param profiles the list to search
     * @param name the profile name to look for
     * @return the index or {@code -1}
     */
    public static int indexByName(List<ConnectionProfile> profiles, String name) {
        for (var i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }
}

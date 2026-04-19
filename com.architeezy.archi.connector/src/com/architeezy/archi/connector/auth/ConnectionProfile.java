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

/**
 * Holds the configuration and runtime state for one Architeezy server
 * connection.
 */
public class ConnectionProfile {

    private final String name;

    private final String serverUrl;

    private final String clientId;

    private volatile ProfileStatus status;

    /**
     * Creates a profile with the given configuration.
     *
     * @param name unique profile name.
     * @param serverUrl base URL of the Architeezy server.
     * @param clientId OAuth2 client identifier.
     */
    public ConnectionProfile(String name, String serverUrl, String clientId) {
        this.name = name;
        this.serverUrl = serverUrl;
        this.clientId = clientId;
        this.status = ProfileStatus.DISCONNECTED;
    }

    /**
     * Returns the unique profile name.
     *
     * @return the profile name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the base URL of the Architeezy server.
     *
     * @return the server URL.
     */
    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * Returns the OAuth2 client identifier.
     *
     * @return the client ID.
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Returns the current connection status.
     *
     * @return the current status.
     */
    public ProfileStatus getStatus() {
        return status;
    }

    /**
     * Sets the current connection status.
     *
     * @param status the new status.
     */
    public void setStatus(ProfileStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return name + " (" + serverUrl + ")"; //$NON-NLS-1$ //$NON-NLS-2$
    }

}

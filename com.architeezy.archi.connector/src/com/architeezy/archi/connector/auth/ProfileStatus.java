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

/** Connection state of a {@link ConnectionProfile}. */
public enum ProfileStatus {

    /** No active session; the user has not signed in. */
    DISCONNECTED,

    /** OAuth flow is in progress. */
    CONNECTING,

    /** A valid access token is available. */
    CONNECTED,

    /** The access token has expired and needs to be refreshed. */
    SESSION_EXPIRED

}

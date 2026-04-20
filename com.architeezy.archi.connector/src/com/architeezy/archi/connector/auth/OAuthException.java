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
 * Signals a failure in the OAuth 2.0 authorization flow.
 */
public class OAuthException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with the given message.
     *
     * @param message error description
     */
    public OAuthException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message error description
     * @param cause the underlying exception
     */
    public OAuthException(String message, Throwable cause) {
        super(message, cause);
    }

}

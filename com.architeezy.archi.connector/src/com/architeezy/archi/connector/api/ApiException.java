/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.api;

/**
 * Signals an HTTP or I/O error from the Architeezy REST API.
 */
@SuppressWarnings("checkstyle:MagicNumber")
public class ApiException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    /**
     * Creates an exception with an HTTP status code and message.
     *
     * @param statusCode HTTP status code
     * @param message error message
     */
    public ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * Creates an exception wrapping a lower-level I/O error (status -1).
     *
     * @param message error message
     * @param cause the underlying exception
     */
    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    /**
     * Returns the HTTP status code, or {@code -1} for I/O errors.
     *
     * @return the HTTP status code
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Returns {@code true} if the server returned 401 Unauthorized.
     *
     * @return {@code true} for HTTP 401
     */
    public boolean isUnauthorized() {
        return statusCode == 401;
    }

    /**
     * Returns {@code true} if the server returned 403 Forbidden.
     *
     * @return {@code true} for HTTP 403
     */
    public boolean isForbidden() {
        return statusCode == 403;
    }

    /**
     * Returns {@code true} if the server returned 404 Not Found.
     *
     * @return {@code true} for HTTP 404
     */
    public boolean isNotFound() {
        return statusCode == 404;
    }

    /**
     * Returns {@code true} if the server returned a 5xx error.
     *
     * @return {@code true} for HTTP 500+
     */
    public boolean isServerError() {
        return statusCode >= 500;
    }

}

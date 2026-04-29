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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * HTTP plumbing shared by {@link ArchiteezyClient}: request/response framing,
 * status checks, cancellation, base64 unwrapping, and exception messages.
 *
 * <p>Kept package-private so the public surface of the connector remains the
 * client itself. Extracting this out keeps the client focused on REST
 * endpoints rather than transport mechanics.
 */
final class HttpHelpers {

    static final String AUTHORIZATION = "Authorization"; //$NON-NLS-1$

    static final String BEARER_PREFIX = "Bearer "; //$NON-NLS-1$

    static final String CONTENT_TYPE = "Content-Type"; //$NON-NLS-1$

    static final String ACCEPT = "Accept"; //$NON-NLS-1$

    static final String APPLICATION_JSON = "application/json"; //$NON-NLS-1$

    static final String MSG_REQUEST_FAILED = "Request failed: "; //$NON-NLS-1$

    private static final long CANCEL_POLL_INTERVAL_MS = 100L;

    private static final int HTTP_OK = 200;

    private static final int HTTP_REDIRECTION = 300;

    private HttpHelpers() {
    }

    /**
     * Performs a GET, returning the response body as a string.
     *
     * @param http the HTTP client to send through
     * @param url request URL
     * @param accessToken OAuth2 bearer token, or {@code null} for anonymous
     * @param timeout request timeout
     * @return the response body
     * @throws ApiException on non-2xx status, I/O error, or interruption
     */
    static String get(HttpClient http, String url, String accessToken, Duration timeout) throws ApiException {
        try {
            var builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header(ACCEPT, APPLICATION_JSON)
                    .GET();
            if (accessToken != null && !accessToken.isEmpty()) {
                builder.header(AUTHORIZATION, BEARER_PREFIX + accessToken);
            }
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            checkStatus(response.statusCode(), url);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(MSG_REQUEST_FAILED + describe(e), e);
        } catch (Exception e) {
            throw new ApiException(MSG_REQUEST_FAILED + describe(e), e);
        }
    }

    /**
     * Sends a request synchronously, but observes a cooperative cancellation
     * signal. Falls back to the plain blocking send when {@code cancel} is
     * {@link CancelSignal#NEVER}.
     *
     * @param http the HTTP client to send through
     * @param request the request to send
     * @param handler the body handler
     * @param cancel cooperative cancellation signal
     * @param <T> the body type
     * @return the response
     * @throws IOException on transport error
     * @throws InterruptedException if {@code cancel} fires
     */
    static <T> HttpResponse<T> sendCancelable(HttpClient http, HttpRequest request,
            HttpResponse.BodyHandler<T> handler, CancelSignal cancel) throws IOException, InterruptedException {
        if (cancel == null || cancel == CancelSignal.NEVER) {
            return http.send(request, handler);
        }
        var future = http.sendAsync(request, handler);
        var watcher = new Thread(() -> pollCancel(future, cancel), "ArchiteezyCancelWatcher"); //$NON-NLS-1$
        watcher.setDaemon(true);
        watcher.start();
        try {
            return future.get();
        } catch (CancellationException e) {
            throw new InterruptedException("HTTP transfer cancelled"); //$NON-NLS-1$
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            if (cause instanceof InterruptedException ie) {
                throw ie;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IOException(cause == null ? e.getMessage() : cause.getMessage(), cause);
        } finally {
            watcher.interrupt();
        }
    }

    /**
     * Throws {@link ApiException} when {@code status} is outside the 2xx range.
     *
     * @param status HTTP status code
     * @param url request URL, used in the error message
     * @throws ApiException when status is non-2xx
     */
    static void checkStatus(int status, String url) throws ApiException {
        if (status < HTTP_OK || status >= HTTP_REDIRECTION) {
            throw new ApiException(status, "HTTP " + status + " for " + url); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    /**
     * Builds a non-null one-line description for an exception, falling back to
     * the runtime class name when {@link Throwable#getMessage()} is {@code null}.
     * Avoids surfacing the literal text {@code "Request failed: null"} to the
     * user when an underlying error (e.g. a bare {@link NullPointerException})
     * carries no message of its own.
     *
     * @param t the throwable to summarize
     * @return a non-null description suitable for end-user error text
     */
    static String describe(Throwable t) {
        if (t == null) {
            return "unknown error"; //$NON-NLS-1$
        }
        var msg = t.getMessage();
        if (msg != null && !msg.isBlank()) {
            return msg;
        }
        return t.getClass().getSimpleName();
    }

    /**
     * Unwraps Spring's default {@code byte[]} -> JSON serialization. When the
     * server responds with {@code "<base64>"} we strip the quotes and decode;
     * any other body is returned verbatim so callers stay compatible with
     * servers that send the raw {@code .archimate} XML directly.
     *
     * @param body the raw response body bytes
     * @return decoded bytes, or the input unchanged when no JSON wrapper was detected
     */
    static byte[] decodeBase64IfWrapped(byte[] body) {
        if (body == null || body.length < 2) {
            return body;
        }
        var start = skipLeadingWhitespace(body);
        var end = skipTrailingWhitespace(body, start);
        if (end - start < 2 || body[start] != '"' || body[end - 1] != '"') {
            return body;
        }
        try {
            var b64 = new String(body, start + 1, end - start - 2, StandardCharsets.US_ASCII);
            return Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException ignored) {
            return body;
        }
    }

    private static int skipLeadingWhitespace(byte[] body) {
        var i = 0;
        while (i < body.length && Character.isWhitespace(body[i])) {
            i++;
        }
        return i;
    }

    private static int skipTrailingWhitespace(byte[] body, int start) {
        var i = body.length;
        while (i > start && Character.isWhitespace(body[i - 1])) {
            i--;
        }
        return i;
    }

    private static void pollCancel(CompletableFuture<?> future, CancelSignal cancel) {
        while (!future.isDone()) {
            if (cancel.isCanceled()) {
                future.cancel(true);
                return;
            }
            try {
                Thread.sleep(CANCEL_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

}

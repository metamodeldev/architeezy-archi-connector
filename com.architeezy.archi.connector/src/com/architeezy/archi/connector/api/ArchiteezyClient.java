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
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import com.architeezy.archi.connector.api.dto.PagedResult;
import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.api.dto.RemoteProject;

/**
 * HTTP client for the Architeezy REST API (HAL+JSON).
 *
 * All methods are synchronous and intended to be called from a background Job
 * or IRunnableWithProgress, never from the SWT UI thread.
 */
public class ArchiteezyClient {

    private static final String AUTHORIZATION = "Authorization"; //$NON-NLS-1$

    private static final String BEARER_PREFIX = "Bearer "; //$NON-NLS-1$

    private static final String CONTENT_TYPE = "Content-Type"; //$NON-NLS-1$

    private static final String MSG_REQUEST_FAILED = "Request failed: "; //$NON-NLS-1$

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration TRANSFER_TIMEOUT = Duration.ofSeconds(120);

    private static final long CANCEL_POLL_INTERVAL_MS = 100L;

    /**
     * URL-encoded value of the ArchiMate model content type. The model listing
     * endpoint accepts a {@code contentType} query parameter; pinning it to
     * ArchiMate keeps non-ArchiMate models (e.g. BPMN) out of the import wizard.
     */
    private static final String ARCHIMATE_CONTENT_TYPE_FILTER =
            "&contentType=http%3A%2F%2Fwww.archimatetool.com%2Farchimate%23ArchimateModel"; //$NON-NLS-1$

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    // -----------------------------------------------------------------------
    // Models listing & metadata

    /**
     * Lists models in the remote repository.
     *
     * @param serverUrl base URL of the Architeezy server
     * @param accessToken OAuth2 bearer token
     * @param page zero-based page index
     * @param size page size
     * @return paged list of models
     * @throws ApiException on HTTP or I/O error
     */
    public PagedResult<RemoteModel> listModels(String serverUrl, String accessToken, int page, int size)
            throws ApiException {
        var url = serverUrl + "/api/models?page=" + page + "&size=" + size //$NON-NLS-1$ //$NON-NLS-2$
                + ARCHIMATE_CONTENT_TYPE_FILTER;
        var json = get(url, accessToken);
        return ResponseParser.parseModelPage(json, page);
    }

    /**
     * Lists a page of models using the server's default page size.
     *
     * @param serverUrl base URL of the Architeezy server
     * @param accessToken OAuth2 bearer token
     * @param page zero-based page index
     * @return paged list of models
     * @throws ApiException on HTTP or I/O error
     */
    public PagedResult<RemoteModel> listModels(String serverUrl, String accessToken, int page)
            throws ApiException {
        var url = serverUrl + "/api/models?page=" + page //$NON-NLS-1$
                + ARCHIMATE_CONTENT_TYPE_FILTER;
        var json = get(url, accessToken);
        return ResponseParser.parseModelPage(json, page);
    }

    /**
     * Fetches metadata for a single model.
     *
     * @param serverUrl base URL of the Architeezy server
     * @param accessToken OAuth2 bearer token
     * @param modelId model identifier
     * @return model metadata
     * @throws ApiException on HTTP or I/O error
     */
    public RemoteModel getModel(String serverUrl, String accessToken, String modelId) throws ApiException {
        var url = serverUrl + "/api/models/" + modelId; //$NON-NLS-1$
        var json = get(url, accessToken);
        return ResponseParser.parseModel(json);
    }

    /**
     * Downloads the raw ArchiMate content, aborting the transfer if
     * {@code cancel} fires.
     *
     * @param accessToken OAuth2 bearer token (may be {@code null} for public
     *        content).
     * @param contentUrl direct URL to the model content
     * @param cancel cooperative cancellation signal
     * @return raw content bytes
     * @throws ApiException on HTTP, I/O error or cancellation
     */
    public byte[] getModelContent(String accessToken, String contentUrl, CancelSignal cancel)
            throws ApiException {
        if (contentUrl == null || contentUrl.isBlank()) {
            throw new ApiException("Model has no content URL", null); //$NON-NLS-1$
        }
        try {
            var builder = HttpRequest.newBuilder()
                    .uri(URI.create(contentUrl))
                    .timeout(TRANSFER_TIMEOUT)
                    // The /content endpoint only accepts application/json (other Accept values
                    // get a 406). Spring serializes the byte[] as a JSON-quoted base64 string,
                    // which we unwrap below.
                    .header("Accept", "application/json") //$NON-NLS-1$ //$NON-NLS-2$
                    .GET();
            if (accessToken != null && !accessToken.isEmpty()) {
                builder.header(AUTHORIZATION, BEARER_PREFIX + accessToken);
            }
            var request = builder.build();
            var response = sendCancelable(request, HttpResponse.BodyHandlers.ofByteArray(), cancel);
            checkStatus(response.statusCode(), contentUrl);
            return decodeBase64IfWrapped(response.body());
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
     * Builds a non-null one-line description for an exception, falling back to
     * the runtime class name when {@link Throwable#getMessage()} is {@code null}.
     * Avoids surfacing the literal text {@code "Request failed: null"} to the
     * user when an underlying error (e.g. a bare {@link NullPointerException})
     * carries no message of its own.
     *
     * @param t the throwable to summarize
     * @return a non-null description suitable for end-user error text
     */
    private static String describe(Throwable t) {
        if (t == null) {
            return "unknown error"; //$NON-NLS-1$
        }
        final var msg = t.getMessage();
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
    private static byte[] decodeBase64IfWrapped(byte[] body) {
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

    // -----------------------------------------------------------------------
    // Projects

    /**
     * Lists all projects accessible to the authenticated user.
     *
     * @param serverUrl base URL of the Architeezy server
     * @param accessToken OAuth2 bearer token
     * @return list of all accessible projects
     * @throws ApiException on HTTP or I/O error
     */
    public List<RemoteProject> listProjects(String serverUrl, String accessToken) throws ApiException {
        var url = serverUrl + "/api/projects?size=100"; //$NON-NLS-1$
        var json = get(url, accessToken);
        return ResponseParser.parseProjectList(json);
    }

    /**
     * Lists a page of projects using the server's default page size.
     *
     * @param serverUrl base URL of the Architeezy server
     * @param accessToken OAuth2 bearer token
     * @param page zero-based page index
     * @return paged list of projects
     * @throws ApiException on HTTP or I/O error
     */
    public PagedResult<RemoteProject> listProjectsPage(String serverUrl, String accessToken, int page)
            throws ApiException {
        var url = serverUrl + "/api/projects?page=" + page; //$NON-NLS-1$
        var json = get(url, accessToken);
        return ResponseParser.parseProjectPage(json, page);
    }

    // -----------------------------------------------------------------------
    // Export (multipart POST)

    /**
     * Uploads a model file to the server as a multipart POST.
     *
     * @param serverUrl base URL of the Architeezy server
     * @param accessToken OAuth2 bearer token
     * @param projectId target project identifier
     * @param fileName file name for the uploaded model
     * @param content raw ArchiMate file bytes
     * @return metadata of the newly created remote model
     * @throws ApiException on HTTP or I/O error
     */
    public RemoteModel exportModel(String serverUrl, String accessToken, String projectId, String fileName,
            byte[] content) throws ApiException {
        return exportModel(serverUrl, accessToken, projectId, fileName, content, CancelSignal.NEVER);
    }

    /**
     * Uploads a model file to the server as a multipart POST, aborting the
     * transfer if {@code cancel} fires.
     *
     * @param serverUrl base URL of the Architeezy server
     * @param accessToken OAuth2 bearer token
     * @param projectId target project identifier
     * @param fileName file name for the uploaded model
     * @param content raw ArchiMate file bytes
     * @param cancel cooperative cancellation signal
     * @return metadata of the newly created remote model
     * @throws ApiException on HTTP, I/O error or cancellation
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public RemoteModel exportModel(String serverUrl, String accessToken, String projectId, String fileName,
            byte[] content, CancelSignal cancel) throws ApiException {
        var url = serverUrl + "/api/models"; //$NON-NLS-1$
        var boundary = "----ArchiteezyBoundary" + Long.toHexString(System.currentTimeMillis()); //$NON-NLS-1$
        var body = MultipartBuilder.build(boundary, projectId, fileName, content);
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TRANSFER_TIMEOUT)
                    .header(AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .header(CONTENT_TYPE, "multipart/form-data; boundary=" + boundary) //$NON-NLS-1$
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            var response = sendCancelable(request, HttpResponse.BodyHandlers.ofString(), cancel);
            checkStatus(response.statusCode(), url);
            var responseBody = response.body();
            if (responseBody != null && responseBody.contains("\"_links\"")) { //$NON-NLS-1$
                return ResponseParser.parseModel(responseBody);
            }
            var location = response.headers().firstValue("Location").orElse(null); //$NON-NLS-1$
            if (location != null && !location.isBlank()) {
                return ResponseParser.parseModel(get(location, accessToken));
            }
            throw new ApiException("Export succeeded but response did not include model metadata", null); //$NON-NLS-1$
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(MSG_REQUEST_FAILED + describe(e), e);
        } catch (Exception e) {
            throw new ApiException(MSG_REQUEST_FAILED + describe(e), e);
        }
    }

    // -----------------------------------------------------------------------
    // Update / Delete

    /**
     * Uploads new ArchiMate content for an existing model using the dedicated
     * content endpoint.
     *
     * @param accessToken OAuth2 bearer token
     * @param modelUrl HAL self link URL of the model
     * @param content new raw ArchiMate file bytes
     * @return updated model metadata parsed from the PUT response, or
     *         {@code null} if the server did not return a parseable body
     * @throws ApiException on HTTP or I/O error
     */
    public RemoteModel pushModelContent(String accessToken, String modelUrl, byte[] content) throws ApiException {
        return pushModelContent(accessToken, modelUrl, content, CancelSignal.NEVER);
    }

    /**
     * Uploads new ArchiMate content, aborting the transfer if {@code cancel}
     * fires.
     *
     * @param accessToken OAuth2 bearer token
     * @param modelUrl HAL self link URL of the model
     * @param content new raw ArchiMate file bytes
     * @param cancel cooperative cancellation signal
     * @return updated model metadata parsed from the PUT response, or
     *         {@code null} if the server did not return a parseable body
     * @throws ApiException on HTTP, I/O error or cancellation
     */
    public RemoteModel pushModelContent(String accessToken, String modelUrl, byte[] content, CancelSignal cancel)
            throws ApiException {
        var url = modelUrl + "/content?format=archimate"; //$NON-NLS-1$
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TRANSFER_TIMEOUT)
                    .header(AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .header(CONTENT_TYPE, "application/octet-stream") //$NON-NLS-1$
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                    .build();
            var response = sendCancelable(request, HttpResponse.BodyHandlers.ofString(), cancel);
            checkStatus(response.statusCode(), url);
            var body = response.body();
            if (body != null && body.contains("\"lastModificationDateTime\"")) { //$NON-NLS-1$
                return ResponseParser.parseModel(body);
            }
            return null;
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
     * Deletes a model from the server.
     *
     * @param accessToken OAuth2 bearer token
     * @param modelUrl HAL self link URL of the model to delete
     * @throws ApiException on HTTP or I/O error
     */
    public void deleteModel(String accessToken, String modelUrl) throws ApiException {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(modelUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header(AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .DELETE()
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.discarding());
            checkStatus(response.statusCode(), modelUrl);
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(MSG_REQUEST_FAILED + describe(e), e);
        } catch (Exception e) {
            throw new ApiException(MSG_REQUEST_FAILED + describe(e), e);
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers

    private String get(String url, String accessToken) throws ApiException {
        try {
            var builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json") //$NON-NLS-1$ //$NON-NLS-2$
                    .GET();
            if (accessToken != null && !accessToken.isEmpty()) {
                builder.header(AUTHORIZATION, BEARER_PREFIX + accessToken);
            }
            var request = builder.build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
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

    private <T> HttpResponse<T> sendCancelable(HttpRequest request, HttpResponse.BodyHandler<T> handler,
            CancelSignal cancel) throws IOException, InterruptedException {
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

    private static void pollCancel(java.util.concurrent.CompletableFuture<?> future, CancelSignal cancel) {
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

    @SuppressWarnings("checkstyle:MagicNumber")
    private static void checkStatus(int status, String url) throws ApiException {
        if (status < 200 || status >= 300) {
            throw new ApiException(status, "HTTP " + status + " for " + url); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

}

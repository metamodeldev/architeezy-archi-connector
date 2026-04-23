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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

    private static final String BOUNDARY_SEPARATOR = "--"; //$NON-NLS-1$

    private static final String QUOTE = "\""; //$NON-NLS-1$

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
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
        var url = serverUrl + "/api/models?page=" + page + "&size=" + size; //$NON-NLS-1$ //$NON-NLS-2$
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
     * Downloads the raw ArchiMate content from the given URL.
     *
     * @param accessToken OAuth2 bearer token (may be {@code null} for public
     *        content).
     * @param contentUrl direct URL to the model content
     * @return raw content bytes
     * @throws ApiException on HTTP or I/O error
     */
    public byte[] getModelContent(String accessToken, String contentUrl) throws ApiException {
        try {
            var builder = HttpRequest.newBuilder()
                    .uri(URI.create(contentUrl))
                    .GET();
            if (accessToken != null && !accessToken.isEmpty()) {
                builder.header(AUTHORIZATION, BEARER_PREFIX + accessToken);
            }
            var request = builder.build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            checkStatus(response.statusCode(), contentUrl);
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(MSG_REQUEST_FAILED + e.getMessage(), e);
        } catch (Exception e) {
            throw new ApiException(MSG_REQUEST_FAILED + e.getMessage(), e);
        }
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
        var url = serverUrl + "/api/models"; //$NON-NLS-1$
        var boundary = "----ArchiteezyBoundary" + Long.toHexString(System.currentTimeMillis()); //$NON-NLS-1$
        var body = buildMultipart(boundary, projectId, fileName, content);
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header(AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .header(CONTENT_TYPE, "multipart/form-data; boundary=" + boundary) //$NON-NLS-1$
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
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
            throw new ApiException(MSG_REQUEST_FAILED + e.getMessage(), e);
        } catch (Exception e) {
            throw new ApiException(MSG_REQUEST_FAILED + e.getMessage(), e);
        }
    }

    private static byte[] buildMultipart(String boundary, String projectId, String fileName, byte[] content)
            throws ApiException {
        try {
            var out = new ByteArrayOutputStream();
            var crlf = "\r\n"; //$NON-NLS-1$
            var entityJson = "{\"projectId\":" + jsonString(projectId) + "}"; //$NON-NLS-1$ //$NON-NLS-2$

            // Part 1: entity JSON
            out.write((BOUNDARY_SEPARATOR + boundary + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"entity\"; filename=\"blob\"" + crlf) //$NON-NLS-1$
                    .getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: application/json" + crlf + crlf).getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$
            out.write(entityJson.getBytes(StandardCharsets.UTF_8));
            out.write(crlf.getBytes(StandardCharsets.UTF_8));

            // Part 2: model content
            out.write((BOUNDARY_SEPARATOR + boundary + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"content\"; filename=" + QUOTE + fileName + QUOTE + crlf) //$NON-NLS-1$
                    .getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: application/octet-stream" + crlf + crlf).getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$
            out.write(content);
            out.write(crlf.getBytes(StandardCharsets.UTF_8));

            // Final boundary
            out.write((BOUNDARY_SEPARATOR + boundary + BOUNDARY_SEPARATOR + crlf).getBytes(StandardCharsets.UTF_8));

            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException("Failed to build multipart body", e); //$NON-NLS-1$
        }
    }

    // -----------------------------------------------------------------------
    // Create / Update / Delete

    /**
     * Creates a new model record on the server.
     *
     * @param serverUrl base URL of the Architeezy server
     * @param accessToken OAuth2 bearer token
     * @param name model name
     * @param description model description
     * @return the newly created model metadata
     * @throws ApiException on HTTP or I/O error
     */
    public RemoteModel createModel(String serverUrl, String accessToken, String name, String description)
            throws ApiException {
        var url = serverUrl + "/api/models"; //$NON-NLS-1$
        var body = "{\"name\":" + jsonString(name) //$NON-NLS-1$
                + ",\"description\":" + jsonString(description) + "}"; //$NON-NLS-1$ //$NON-NLS-2$

        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header(AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .header(CONTENT_TYPE, "application/json") //$NON-NLS-1$
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            checkStatus(response.statusCode(), url);
            return ResponseParser.parseModel(response.body());
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(MSG_REQUEST_FAILED + e.getMessage(), e);
        } catch (Exception e) {
            throw new ApiException(MSG_REQUEST_FAILED + e.getMessage(), e);
        }
    }

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
        var url = modelUrl + "/content?format=archimate"; //$NON-NLS-1$
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header(AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .header(CONTENT_TYPE, "application/octet-stream") //$NON-NLS-1$
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
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
            throw new ApiException(MSG_REQUEST_FAILED + e.getMessage(), e);
        } catch (Exception e) {
            throw new ApiException(MSG_REQUEST_FAILED + e.getMessage(), e);
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
                    .header(AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .DELETE()
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.discarding());
            checkStatus(response.statusCode(), modelUrl);
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(MSG_REQUEST_FAILED + e.getMessage(), e);
        } catch (Exception e) {
            throw new ApiException(MSG_REQUEST_FAILED + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers

    private String get(String url, String accessToken) throws ApiException {
        try {
            var builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
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
            throw new ApiException(MSG_REQUEST_FAILED + e.getMessage(), e);
        } catch (Exception e) {
            throw new ApiException(MSG_REQUEST_FAILED + e.getMessage(), e);
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static void checkStatus(int status, String url) throws ApiException {
        if (status < 200 || status >= 300) {
            throw new ApiException(status, "HTTP " + status + " for " + url); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null"; //$NON-NLS-1$
        }
        return QUOTE + value.replace("\\", "\\\\").replace(QUOTE, "\\" + QUOTE) + QUOTE; //$NON-NLS-1$ //$NON-NLS-2$
    }

}

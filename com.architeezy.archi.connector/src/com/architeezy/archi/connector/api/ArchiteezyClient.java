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

import static com.architeezy.archi.connector.api.HttpHelpers.ACCEPT;
import static com.architeezy.archi.connector.api.HttpHelpers.APPLICATION_JSON;
import static com.architeezy.archi.connector.api.HttpHelpers.AUTHORIZATION;
import static com.architeezy.archi.connector.api.HttpHelpers.BEARER_PREFIX;
import static com.architeezy.archi.connector.api.HttpHelpers.CONTENT_TYPE;
import static com.architeezy.archi.connector.api.HttpHelpers.MSG_REQUEST_FAILED;
import static com.architeezy.archi.connector.api.HttpHelpers.checkStatus;
import static com.architeezy.archi.connector.api.HttpHelpers.decodeBase64IfWrapped;
import static com.architeezy.archi.connector.api.HttpHelpers.describe;
import static com.architeezy.archi.connector.api.HttpHelpers.sendCancelable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.architeezy.archi.connector.api.dto.PagedResult;
import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.api.dto.RemoteProject;
import com.architeezy.archi.connector.api.dto.RemoteRepresentation;

/**
 * HTTP client for the Architeezy REST API (HAL+JSON).
 *
 * All methods are synchronous and intended to be called from a background Job
 * or IRunnableWithProgress, never from the SWT UI thread.
 */
public class ArchiteezyClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration TRANSFER_TIMEOUT = Duration.ofSeconds(120);

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
        return ResponseParser.parseModelPage(get(url, accessToken), page);
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
    public PagedResult<RemoteModel> listModels(String serverUrl, String accessToken, int page) throws ApiException {
        var url = serverUrl + "/api/models?page=" + page + ARCHIMATE_CONTENT_TYPE_FILTER; //$NON-NLS-1$
        return ResponseParser.parseModelPage(get(url, accessToken), page);
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
        return ResponseParser.parseModel(get(serverUrl + "/api/models/" + modelId, accessToken)); //$NON-NLS-1$
    }

    /**
     * Finds a representation in the given model by its target-object UUID.
     * The server stores each EObject's id as
     * {@code UUID5(model.id, archimateId)}; the connector recomputes that
     * value via {@link com.architeezy.archi.connector.util.UuidV5}.
     *
     * @param serverUrl base URL of the Architeezy server
     * @param accessToken OAuth2 bearer token
     * @param modelId the model id (UUID)
     * @param targetObjectId the target-object UUID (the EObject id)
     * @return the matching representation, or empty when none found
     * @throws ApiException on HTTP or I/O error
     */
    public Optional<RemoteRepresentation> findRepresentationByTargetObjectId(String serverUrl,
            String accessToken, String modelId, UUID targetObjectId) throws ApiException {
        var url = serverUrl + "/api/representations?model.id=" + modelId //$NON-NLS-1$
                + "&targetObjectId=" + targetObjectId + "&page=0&size=2"; //$NON-NLS-1$ //$NON-NLS-2$
        var items = ResponseParser.parseRepresentationList(get(url, accessToken));
        return items.isEmpty() ? Optional.empty() : Optional.of(items.get(0));
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
    public byte[] getModelContent(String accessToken, String contentUrl, CancelSignal cancel) throws ApiException {
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
                    .header(ACCEPT, APPLICATION_JSON)
                    .GET();
            if (accessToken != null && !accessToken.isEmpty()) {
                builder.header(AUTHORIZATION, BEARER_PREFIX + accessToken);
            }
            var response = sendCancelable(http, builder.build(), HttpResponse.BodyHandlers.ofByteArray(), cancel);
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
        return ResponseParser.parseProjectList(get(serverUrl + "/api/projects?size=100", accessToken)); //$NON-NLS-1$
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
        return ResponseParser.parseProjectPage(
                get(serverUrl + "/api/projects?page=" + page, accessToken), page); //$NON-NLS-1$
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
            var response = sendCancelable(http, request, HttpResponse.BodyHandlers.ofString(), cancel);
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
            var response = sendCancelable(http, request, HttpResponse.BodyHandlers.ofString(), cancel);
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
            checkStatus(http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode(), modelUrl);
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(MSG_REQUEST_FAILED + describe(e), e);
        } catch (Exception e) {
            throw new ApiException(MSG_REQUEST_FAILED + describe(e), e);
        }
    }

    private String get(String url, String accessToken) throws ApiException {
        return HttpHelpers.get(http, url, accessToken, REQUEST_TIMEOUT);
    }

}

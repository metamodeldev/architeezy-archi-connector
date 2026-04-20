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
import java.util.ArrayList;
import java.util.List;

import com.architeezy.archi.connector.auth.OAuthManager;

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
        return parseModelPage(json, page);
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
        return parseModel(json);
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
        return parseProjectList(json);
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
     * @throws ApiException on HTTP or I/O error
     */
    public void exportModel(String serverUrl, String accessToken, String projectId, String fileName, byte[] content)
            throws ApiException {
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
            var response = http.send(request, HttpResponse.BodyHandlers.discarding());
            checkStatus(response.statusCode(), url);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(MSG_REQUEST_FAILED + e.getMessage(), e);
        }
    }

    private static byte[] buildMultipart(String boundary, String projectId, String fileName, byte[] content) {
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
            throw new RuntimeException("Failed to build multipart body", e); //$NON-NLS-1$
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
            return parseModel(response.body());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(MSG_REQUEST_FAILED + e.getMessage(), e);
        }
    }

    /**
     * Replaces the content of an existing model.
     *
     * @param accessToken OAuth2 bearer token
     * @param modelUrl HAL self link URL of the model to update
     * @param content new raw ArchiMate file bytes
     * @throws ApiException on HTTP or I/O error
     */
    public void updateModelContent(String accessToken, String modelUrl, byte[] content) throws ApiException {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(modelUrl))
                    .header(AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .header(CONTENT_TYPE, "application/octet-stream") //$NON-NLS-1$
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.discarding());
            checkStatus(response.statusCode(), modelUrl);
        } catch (ApiException e) {
            throw e;
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

    // -----------------------------------------------------------------------
    // JSON parsing (HAL+JSON format)

    private static PagedResult<RemoteModel> parseModelPage(String json, int requestedPage) {
        var items = new ArrayList<RemoteModel>();

        // Extract _embedded.models array
        var embeddedIdx = json.indexOf("\"_embedded\""); //$NON-NLS-1$
        if (embeddedIdx >= 0) {
            var arrStart = json.indexOf('[', embeddedIdx);
            if (arrStart >= 0) {
                var arrEnd = findMatchingBracket(json, arrStart, '[', ']');
                if (arrEnd > arrStart) {
                    parseModelArray(json.substring(arrStart + 1, arrEnd), items);
                }
            }
        }

        var totalElements = OAuthManager.extractJsonLong(json, "totalElements", items.size()); //$NON-NLS-1$
        var totalPages = (int) OAuthManager.extractJsonLong(json, "totalPages", 1); //$NON-NLS-1$
        var pageNum = (int) OAuthManager.extractJsonLong(json, "number", requestedPage); //$NON-NLS-1$

        return new PagedResult<>(items, totalElements, totalPages, pageNum);
    }

    private static void parseModelArray(String arrayContent, List<RemoteModel> out) {
        var pos = 0;
        while (pos < arrayContent.length()) {
            var objStart = arrayContent.indexOf('{', pos);
            if (objStart < 0) {
                break;
            }
            var objEnd = findMatchingBracket(arrayContent, objStart, '{', '}');
            if (objEnd < 0) {
                break;
            }
            var obj = arrayContent.substring(objStart, objEnd + 1);
            var m = parseModel(obj);
            if (m.contentUrl() != null) {
                out.add(m);
            }
            pos = objEnd + 1;
        }
    }

    static RemoteModel parseModel(String json) {
        var id = OAuthManager.extractJsonString(json, "id");
        var name = OAuthManager.extractJsonString(json, "name");
        var description = OAuthManager.extractJsonString(json, "description");
        var author = OAuthManager.extractJsonString(json, "author");
        var lastModified = OAuthManager.extractJsonString(json, "lastModificationDateTime");

        String selfUrl = null;
        String contentUrl = null;

        var linksIdx = json.indexOf("\"_links\"");
        if (linksIdx >= 0) {
            var linksEnd = findMatchingBracket(json, json.indexOf('{', linksIdx), '{', '}');
            var linksBlock = json.substring(linksIdx, linksEnd + 1);

            // Extract _links.self.href
            var selfIdx = linksBlock.indexOf("\"self\"");
            if (selfIdx >= 0) {
                selfUrl = OAuthManager.extractJsonString(linksBlock.substring(selfIdx), "href");
            }

            // Extract _links.content[] entry with title "ArchiMate"
            var contentIdx = linksBlock.indexOf("\"content\"");
            if (contentIdx >= 0) {
                var arrStart = linksBlock.indexOf('[', contentIdx);
                if (arrStart >= 0) {
                    var arrEnd = findMatchingBracket(linksBlock, arrStart, '[', ']');
                    if (arrEnd > arrStart) {
                        var arrContent = linksBlock.substring(arrStart + 1, arrEnd);
                        contentUrl = extractArchiMateHref(arrContent);
                    }
                }
            }
        }
        return new RemoteModel(id, name, description, author, lastModified, selfUrl, contentUrl);
    }

    private static String extractArchiMateHref(String contentArray) {
        var pos = 0;
        while (pos < contentArray.length()) {
            var objStart = contentArray.indexOf('{', pos);
            if (objStart < 0) {
                break;
            }
            var objEnd = findMatchingBracket(contentArray, objStart, '{', '}');
            if (objEnd < 0) {
                break;
            }
            var obj = contentArray.substring(objStart, objEnd + 1);
            var title = OAuthManager.extractJsonString(obj, "title"); //$NON-NLS-1$
            if ("ArchiMate".equals(title)) { //$NON-NLS-1$
                var href = OAuthManager.extractJsonString(obj, "href"); //$NON-NLS-1$
                if (href != null) {
                    // Strip URI template suffix, e.g. "{&inline}"
                    var templateIdx = href.indexOf('{');
                    return templateIdx >= 0 ? href.substring(0, templateIdx) : href;
                }
            }
            pos = objEnd + 1;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // JSON parsing (projects)

    private static List<RemoteProject> parseProjectList(String json) {
        var projects = new ArrayList<RemoteProject>();

        // Try HAL+JSON: _embedded.projects array
        var embeddedIdx = json.indexOf("\"_embedded\""); //$NON-NLS-1$
        if (embeddedIdx >= 0) {
            var projIdx = json.indexOf("\"projects\"", embeddedIdx); //$NON-NLS-1$
            if (projIdx >= 0) {
                var arrStart = json.indexOf('[', projIdx);
                if (arrStart >= 0) {
                    var arrEnd = findMatchingBracket(json, arrStart, '[', ']');
                    if (arrEnd > arrStart) {
                        parseProjectArray(json.substring(arrStart + 1, arrEnd), projects);
                        return projects;
                    }
                }
            }
        }

        // Fallback: plain JSON array
        var arrStart = json.indexOf('[');
        if (arrStart >= 0) {
            var arrEnd = findMatchingBracket(json, arrStart, '[', ']');
            if (arrEnd > arrStart) {
                parseProjectArray(json.substring(arrStart + 1, arrEnd), projects);
            }
        }
        return projects;
    }

    private static void parseProjectArray(String arrayContent, List<RemoteProject> out) {
        var pos = 0;
        while (pos < arrayContent.length()) {
            var objStart = arrayContent.indexOf('{', pos);
            if (objStart < 0) {
                break;
            }
            var objEnd = findMatchingBracket(arrayContent, objStart, '{', '}');
            if (objEnd < 0) {
                break;
            }
            var obj = arrayContent.substring(objStart, objEnd + 1);
            var id = OAuthManager.extractJsonString(obj, "id"); //$NON-NLS-1$
            var name = OAuthManager.extractJsonString(obj, "name"); //$NON-NLS-1$
            if (id != null) {
                out.add(new RemoteProject(id, name));
            }
            pos = objEnd + 1;
        }
    }

    /**
     * Finds the closing bracket matching the opening bracket at {@code start}.
     *
     * @param s the string to search
     * @param start index of the opening bracket
     * @param open the opening bracket character
     * @param close the closing bracket character
     * @return index of the matching closing bracket, or {@code -1} if not found
     */
    private static int findMatchingBracket(String s, int start, char open, char close) {
        var depth = 0;
        var inString = false;
        for (int i = start; i < s.length(); i++) {
            var c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                if (c == open) {
                    depth++;
                }
                if (c == close) {
                    depth--;
                }
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null"; //$NON-NLS-1$
        }
        return QUOTE + value.replace("\\", "\\\\").replace(QUOTE, "\\" + QUOTE) + QUOTE; //$NON-NLS-1$ //$NON-NLS-2$
    }

}

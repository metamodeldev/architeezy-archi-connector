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

import static com.architeezy.archi.connector.api.JsonObjects.extractTopLevelObject;
import static com.architeezy.archi.connector.api.JsonObjects.extractTopLevelString;
import static com.architeezy.archi.connector.api.JsonObjects.extractTopLevelValue;
import static com.architeezy.archi.connector.api.JsonObjects.findMatchingBracket;

import java.util.ArrayList;
import java.util.List;

import com.architeezy.archi.connector.api.dto.PagedResult;
import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.api.dto.RemoteProject;
import com.architeezy.archi.connector.auth.OAuthManager;

/**
 * Parses HAL+JSON responses returned by the Architeezy REST API.
 */
final class ResponseParser {

    private ResponseParser() {
    }

    /**
     * Parses a HAL+JSON page of models.
     *
     * @param json          the response body
     * @param requestedPage the page index requested, used as a fallback when the response omits it
     * @return parsed page, never {@code null}
     */
    static PagedResult<RemoteModel> parseModelPage(String json, int requestedPage) {
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
        var objStart = arrayContent.indexOf('{');
        while (objStart >= 0) {
            var objEnd = findMatchingBracket(arrayContent, objStart, '{', '}');
            if (objEnd < 0) {
                return;
            }
            var m = parseModel(arrayContent.substring(objStart, objEnd + 1));
            if (m.contentUrl() != null) {
                out.add(m);
            }
            objStart = arrayContent.indexOf('{', objEnd + 1);
        }
    }

    /**
     * Parses a single HAL+JSON model object.
     *
     * @param json the JSON object body
     * @return parsed model
     */
    static RemoteModel parseModel(String json) {
        final var id = extractTopLevelString(json, "id"); //$NON-NLS-1$
        final var name = extractTopLevelString(json, "name"); //$NON-NLS-1$
        final var description = extractTopLevelString(json, "description"); //$NON-NLS-1$
        final var lastModified = extractTopLevelString(json, "lastModificationDateTime"); //$NON-NLS-1$
        final var slug = extractTopLevelString(json, "slug"); //$NON-NLS-1$

        String projectSlug = null;
        String projectVersion = null;
        String projectName = null;
        final var projectBlock = extractTopLevelObject(json, "project"); //$NON-NLS-1$
        if (projectBlock != null) {
            projectSlug = OAuthManager.extractJsonString(projectBlock, "slug"); //$NON-NLS-1$
            projectVersion = OAuthManager.extractJsonString(projectBlock, "version"); //$NON-NLS-1$
            projectName = OAuthManager.extractJsonString(projectBlock, "name"); //$NON-NLS-1$
        }

        String scopeSlug = null;
        String scopeName = null;
        final var scopeBlock = extractTopLevelObject(json, "scope"); //$NON-NLS-1$
        if (scopeBlock != null) {
            scopeSlug = OAuthManager.extractJsonString(scopeBlock, "slug"); //$NON-NLS-1$
            scopeName = OAuthManager.extractJsonString(scopeBlock, "name"); //$NON-NLS-1$
        }

        String author = null;
        var creatorBlock = extractTopLevelObject(json, "creator"); //$NON-NLS-1$
        if (creatorBlock != null) {
            author = OAuthManager.extractJsonString(creatorBlock, "name"); //$NON-NLS-1$
        }

        String selfUrl = null;
        String contentUrl = null;
        final var linksBlock = extractTopLevelObject(json, "_links"); //$NON-NLS-1$
        if (linksBlock != null) {
            final var selfIdx = linksBlock.indexOf("\"self\""); //$NON-NLS-1$
            if (selfIdx >= 0) {
                selfUrl = OAuthManager.extractJsonString(linksBlock.substring(selfIdx), "href"); //$NON-NLS-1$
            }

            // _links.content may be an array (multiple formats with titles) or a
            // single object pointing at the model's binary content endpoint.
            final var contentRaw = extractTopLevelValue(linksBlock, "content"); //$NON-NLS-1$
            if (contentRaw != null && !contentRaw.isEmpty()) {
                if (contentRaw.charAt(0) == '[') {
                    contentUrl = extractContentHref(contentRaw.substring(1, contentRaw.length() - 1));
                } else if (contentRaw.charAt(0) == '{') {
                    contentUrl = stripTemplate(OAuthManager.extractJsonString(contentRaw, "href")); //$NON-NLS-1$
                }
            }
        }

        if (contentUrl == null && selfUrl != null) {
            contentUrl = selfUrl + "/content?format=archimate"; //$NON-NLS-1$
        }

        return new RemoteModel(id, name, description, author, lastModified, selfUrl, contentUrl,
                slug, projectSlug, projectVersion, scopeSlug, projectName, scopeName);
    }

    private static String extractContentHref(String contentArray) {
        String firstHref = null;
        var objStart = contentArray.indexOf('{');
        while (objStart >= 0) {
            var objEnd = findMatchingBracket(contentArray, objStart, '{', '}');
            if (objEnd < 0) {
                return firstHref;
            }
            var obj = contentArray.substring(objStart, objEnd + 1);
            var title = OAuthManager.extractJsonString(obj, "title"); //$NON-NLS-1$
            var href = stripTemplate(OAuthManager.extractJsonString(obj, "href")); //$NON-NLS-1$
            if (href != null) {
                if ("ArchiMate".equals(title)) { //$NON-NLS-1$
                    return href;
                }
                if (firstHref == null) {
                    firstHref = href;
                }
            }
            objStart = contentArray.indexOf('{', objEnd + 1);
        }
        return firstHref;
    }

    private static String stripTemplate(String href) {
        if (href == null) {
            return null;
        }
        var templateIdx = href.indexOf('{');
        return templateIdx >= 0 ? href.substring(0, templateIdx) : href;
    }

    /**
     * Parses a HAL+JSON or plain JSON array of projects.
     *
     * @param json the response body
     * @return parsed projects, possibly empty, never {@code null}
     */
    static List<RemoteProject> parseProjectList(String json) {
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

    /**
     * Parses a HAL+JSON page of projects.
     *
     * @param json the response body
     * @param requestedPage the page index requested, used as a fallback when the response omits it
     * @return parsed page, never {@code null}
     */
    static PagedResult<RemoteProject> parseProjectPage(String json, int requestedPage) {
        var items = parseProjectList(json);
        var totalElements = OAuthManager.extractJsonLong(json, "totalElements", items.size()); //$NON-NLS-1$
        var totalPages = (int) OAuthManager.extractJsonLong(json, "totalPages", 1); //$NON-NLS-1$
        var pageNum = (int) OAuthManager.extractJsonLong(json, "number", requestedPage); //$NON-NLS-1$
        return new PagedResult<>(items, totalElements, totalPages, pageNum);
    }

    private static void parseProjectArray(String arrayContent, List<RemoteProject> out) {
        var objStart = arrayContent.indexOf('{');
        while (objStart >= 0) {
            final var objEnd = findMatchingBracket(arrayContent, objStart, '{', '}');
            if (objEnd < 0) {
                return;
            }
            final var obj = arrayContent.substring(objStart, objEnd + 1);
            // Top-level extraction skips nested scope.{id,name} and other embedded
            // references that share field names with the project itself.
            final var id = extractTopLevelString(obj, "id"); //$NON-NLS-1$
            final var name = extractTopLevelString(obj, "name"); //$NON-NLS-1$
            String scopeId = null;
            String scopeName = null;
            final var scopeBlock = extractTopLevelObject(obj, "scope"); //$NON-NLS-1$
            if (scopeBlock != null) {
                scopeId = OAuthManager.extractJsonString(scopeBlock, "id"); //$NON-NLS-1$
                scopeName = OAuthManager.extractJsonString(scopeBlock, "name"); //$NON-NLS-1$
            }
            if (id != null) {
                out.add(new RemoteProject(id, name, scopeId, scopeName));
            }
            objStart = arrayContent.indexOf('{', objEnd + 1);
        }
    }

}

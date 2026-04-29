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

    private static final String KEY_ID = "id"; //$NON-NLS-1$

    private static final String KEY_NAME = "name"; //$NON-NLS-1$

    private static final String KEY_SLUG = "slug"; //$NON-NLS-1$

    private static final String KEY_HREF = "href"; //$NON-NLS-1$

    private static final String KEY_LINKS = "_links"; //$NON-NLS-1$

    private static final String KEY_SCOPE = "scope"; //$NON-NLS-1$

    private static final String KEY_UPDATE = "update"; //$NON-NLS-1$

    private static final String KEY_TOTAL_ELEMENTS = "totalElements"; //$NON-NLS-1$

    private static final String KEY_TOTAL_PAGES = "totalPages"; //$NON-NLS-1$

    private static final String KEY_NUMBER = "number"; //$NON-NLS-1$

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

        return pagedResult(json, items, requestedPage);
    }

    private static <T> PagedResult<T> pagedResult(String json, List<T> items, int requestedPage) {
        var totalElements = OAuthManager.extractJsonLong(json, KEY_TOTAL_ELEMENTS, items.size());
        var totalPages = (int) OAuthManager.extractJsonLong(json, KEY_TOTAL_PAGES, 1);
        var pageNum = (int) OAuthManager.extractJsonLong(json, KEY_NUMBER, requestedPage);
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
        final var id = extractTopLevelString(json, KEY_ID);
        final var name = extractTopLevelString(json, KEY_NAME);
        final var description = extractTopLevelString(json, "description"); //$NON-NLS-1$
        final var lastModified = extractTopLevelString(json, "lastModificationDateTime"); //$NON-NLS-1$
        final var slug = extractTopLevelString(json, KEY_SLUG);

        var project = parseProjectRef(json);
        var scope = parseScopeRef(json);
        var author = parseAuthor(json);
        var links = parseModelLinks(json);

        return new RemoteModel(id, name, description, author, lastModified, links.selfUrl(), links.contentUrl(),
                slug, project.slug(), project.version(), scope.slug(), project.name(), scope.name(), links.updatable());
    }

    private static ProjectRef parseProjectRef(String json) {
        final var block = extractTopLevelObject(json, "project"); //$NON-NLS-1$
        if (block == null) {
            return ProjectRef.EMPTY;
        }
        return new ProjectRef(
                OAuthManager.extractJsonString(block, KEY_SLUG),
                OAuthManager.extractJsonString(block, "version"), //$NON-NLS-1$
                OAuthManager.extractJsonString(block, KEY_NAME));
    }

    private static ScopeRef parseScopeRef(String json) {
        final var block = extractTopLevelObject(json, KEY_SCOPE);
        if (block == null) {
            return ScopeRef.EMPTY;
        }
        return new ScopeRef(
                OAuthManager.extractJsonString(block, KEY_SLUG),
                OAuthManager.extractJsonString(block, KEY_NAME));
    }

    private static String parseAuthor(String json) {
        var creatorBlock = extractTopLevelObject(json, "creator"); //$NON-NLS-1$
        return creatorBlock != null ? OAuthManager.extractJsonString(creatorBlock, KEY_NAME) : null;
    }

    private static ModelLinks parseModelLinks(String json) {
        final var linksBlock = extractTopLevelObject(json, KEY_LINKS);
        if (linksBlock == null) {
            return new ModelLinks(null, null, false);
        }
        var selfUrl = parseSelfHref(linksBlock);
        var contentUrl = parseContentHref(linksBlock);
        if (contentUrl == null && selfUrl != null) {
            contentUrl = selfUrl + "/content?format=archimate"; //$NON-NLS-1$
        }
        var updatable = extractTopLevelValue(linksBlock, KEY_UPDATE) != null;
        return new ModelLinks(selfUrl, contentUrl, updatable);
    }

    private static String parseSelfHref(String linksBlock) {
        final var selfIdx = linksBlock.indexOf("\"self\""); //$NON-NLS-1$
        return selfIdx >= 0 ? OAuthManager.extractJsonString(linksBlock.substring(selfIdx), KEY_HREF) : null;
    }

    // _links.content may be an array (multiple formats with titles) or a single
    // object pointing at the model's binary content endpoint.
    private static String parseContentHref(String linksBlock) {
        final var contentRaw = extractTopLevelValue(linksBlock, "content"); //$NON-NLS-1$
        if (contentRaw == null || contentRaw.isEmpty()) {
            return null;
        }
        if (contentRaw.charAt(0) == '[') {
            return extractContentHref(contentRaw.substring(1, contentRaw.length() - 1));
        }
        if (contentRaw.charAt(0) == '{') {
            return stripTemplate(OAuthManager.extractJsonString(contentRaw, KEY_HREF));
        }
        return null;
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
            var href = stripTemplate(OAuthManager.extractJsonString(obj, KEY_HREF));
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
        return pagedResult(json, parseProjectList(json), requestedPage);
    }

    private static void parseProjectArray(String arrayContent, List<RemoteProject> out) {
        var objStart = arrayContent.indexOf('{');
        while (objStart >= 0) {
            final var objEnd = findMatchingBracket(arrayContent, objStart, '{', '}');
            if (objEnd < 0) {
                return;
            }
            final var obj = arrayContent.substring(objStart, objEnd + 1);
            var project = parseProject(obj);
            if (project != null) {
                out.add(project);
            }
            objStart = arrayContent.indexOf('{', objEnd + 1);
        }
    }

    private static RemoteProject parseProject(String obj) {
        // Top-level extraction skips nested scope.{id,name} and other embedded
        // references that share field names with the project itself.
        final var id = extractTopLevelString(obj, KEY_ID);
        if (id == null) {
            return null;
        }
        final var name = extractTopLevelString(obj, KEY_NAME);
        String scopeId = null;
        String scopeName = null;
        final var scopeBlock = extractTopLevelObject(obj, KEY_SCOPE);
        if (scopeBlock != null) {
            scopeId = OAuthManager.extractJsonString(scopeBlock, KEY_ID);
            scopeName = OAuthManager.extractJsonString(scopeBlock, KEY_NAME);
        }
        boolean updatable = false;
        final var linksBlock = extractTopLevelObject(obj, KEY_LINKS);
        if (linksBlock != null) {
            updatable = extractTopLevelValue(linksBlock, KEY_UPDATE) != null;
        }
        return new RemoteProject(id, name, scopeId, scopeName, updatable);
    }

    private record ProjectRef(String slug, String version, String name) {
        static final ProjectRef EMPTY = new ProjectRef(null, null, null);
    }

    private record ScopeRef(String slug, String name) {
        static final ScopeRef EMPTY = new ScopeRef(null, null);
    }

    private record ModelLinks(String selfUrl, String contentUrl, boolean updatable) {
    }

}

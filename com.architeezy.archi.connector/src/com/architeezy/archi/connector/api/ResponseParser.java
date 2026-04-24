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
        var id = OAuthManager.extractJsonString(json, "id"); //$NON-NLS-1$
        var name = OAuthManager.extractJsonString(json, "name"); //$NON-NLS-1$
        var description = OAuthManager.extractJsonString(json, "description"); //$NON-NLS-1$
        var author = OAuthManager.extractJsonString(json, "author"); //$NON-NLS-1$
        var lastModified = OAuthManager.extractJsonString(json, "lastModificationDateTime"); //$NON-NLS-1$
        var slug = OAuthManager.extractJsonString(json, "slug"); //$NON-NLS-1$
        var projectSlug = OAuthManager.extractJsonString(json, "projectSlug"); //$NON-NLS-1$
        var projectVersion = OAuthManager.extractJsonString(json, "projectVersion"); //$NON-NLS-1$
        var scopeSlug = OAuthManager.extractJsonString(json, "scopeSlug"); //$NON-NLS-1$

        String selfUrl = null;
        String contentUrl = null;

        var linksIdx = json.indexOf("\"_links\""); //$NON-NLS-1$
        if (linksIdx >= 0) {
            var linksEnd = findMatchingBracket(json, json.indexOf('{', linksIdx), '{', '}');
            var linksBlock = json.substring(linksIdx, linksEnd + 1);

            // Extract _links.self.href
            var selfIdx = linksBlock.indexOf("\"self\""); //$NON-NLS-1$
            if (selfIdx >= 0) {
                selfUrl = OAuthManager.extractJsonString(linksBlock.substring(selfIdx), "href"); //$NON-NLS-1$
            }

            // Extract _links.content[] entry with title "ArchiMate"
            var contentIdx = linksBlock.indexOf("\"content\""); //$NON-NLS-1$
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
        return new RemoteModel(id, name, description, author, lastModified, selfUrl, contentUrl,
                slug, projectSlug, projectVersion, scopeSlug);
    }

    private static String extractArchiMateHref(String contentArray) {
        var objStart = contentArray.indexOf('{');
        while (objStart >= 0) {
            var objEnd = findMatchingBracket(contentArray, objStart, '{', '}');
            if (objEnd < 0) {
                return null;
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
            objStart = contentArray.indexOf('{', objEnd + 1);
        }
        return null;
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
            var objEnd = findMatchingBracket(arrayContent, objStart, '{', '}');
            if (objEnd < 0) {
                return;
            }
            var obj = arrayContent.substring(objStart, objEnd + 1);
            var id = OAuthManager.extractJsonString(obj, "id"); //$NON-NLS-1$
            var name = OAuthManager.extractJsonString(obj, "name"); //$NON-NLS-1$
            if (id != null) {
                out.add(new RemoteProject(id, name));
            }
            objStart = arrayContent.indexOf('{', objEnd + 1);
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
            if (isQuoteToggle(s, i, c)) {
                inString = !inString;
            }
            if (!inString) {
                depth += bracketDelta(c, open, close);
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isQuoteToggle(String s, int i, char c) {
        return c == '"' && (i == 0 || s.charAt(i - 1) != '\\');
    }

    private static int bracketDelta(char c, char open, char close) {
        if (c == open) {
            return 1;
        }
        if (c == close) {
            return -1;
        }
        return 0;
    }

}

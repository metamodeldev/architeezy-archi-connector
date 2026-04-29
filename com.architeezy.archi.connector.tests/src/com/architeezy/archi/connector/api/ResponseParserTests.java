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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResponseParserTests {

    private static final String MODEL_WITH_ARCHIMATE_LINK = "{"
            + "\"id\":\"m1\","
            + "\"scope\":{\"id\":\"s1\",\"slug\":\"acme\",\"name\":\"ACME\"},"
            + "\"project\":{\"id\":\"p1\",\"slug\":\"alpha\",\"version\":\"1.0\",\"name\":\"Alpha\"},"
            + "\"slug\":\"first\","
            + "\"name\":\"First\","
            + "\"description\":\"desc\","
            + "\"creator\":{\"id\":\"u1\",\"name\":\"alice\"},"
            + "\"lastModificationDateTime\":\"2026-04-01T10:00:00Z\","
            + "\"_links\":{"
            + "\"self\":{\"href\":\"https://srv/api/models/m1\"},"
            + "\"content\":["
            + "{\"title\":\"HTML\",\"href\":\"https://srv/api/models/m1/html\"},"
            + "{\"title\":\"ArchiMate\",\"href\":\"https://srv/api/models/m1/archimate{&inline}\"}"
            + "]"
            + "}"
            + "}";

    @Test
    void parseModelExtractsAllFields() {
        var m = ResponseParser.parseModel(MODEL_WITH_ARCHIMATE_LINK);
        assertEquals("m1", m.id());
        assertEquals("First", m.name());
        assertEquals("first", m.slug());
        assertEquals("desc", m.description());
        assertEquals("alice", m.author());
        assertEquals("alpha", m.projectSlug());
        assertEquals("1.0", m.projectVersion());
        assertEquals("Alpha", m.projectName());
        assertEquals("acme", m.scopeSlug());
        assertEquals("ACME", m.scopeName());
        assertEquals("2026-04-01T10:00:00Z", m.lastModified());
        assertEquals("https://srv/api/models/m1", m.selfUrl());
        assertEquals("https://srv/api/models/m1/archimate", m.contentUrl());
    }

    @Test
    void parseModelDerivesContentUrlWhenNoContentLink() {
        // When _links.content is absent, fall back to /content?format=archimate
        // on the self URL — every model exposes that endpoint under the new API.
        var json = "{"
                + "\"id\":\"m2\","
                + "\"name\":\"Second\","
                + "\"_links\":{"
                + "\"self\":{\"href\":\"https://srv/api/models/m2\"}"
                + "}"
                + "}";
        var m = ResponseParser.parseModel(json);
        assertEquals("m2", m.id());
        assertEquals("https://srv/api/models/m2/content?format=archimate", m.contentUrl());
    }

    @Test
    void parseModelAcceptsContentLinkAsObject() {
        var json = "{"
                + "\"id\":\"m6\","
                + "\"_links\":{"
                + "\"self\":{\"href\":\"https://srv/api/models/m6\"},"
                + "\"content\":{\"href\":\"https://srv/api/models/m6/content{?format,inline}\"}"
                + "}"
                + "}";
        var m = ResponseParser.parseModel(json);
        assertEquals("https://srv/api/models/m6/content", m.contentUrl());
    }

    @Test
    void parseModelDoesNotMistakeNestedScopeIdForModelId() {
        // Spring Data REST emits nested scope/project objects whose `id` and
        // `name` keys precede the model's own keys in the JSON; the top-level
        // extractor must skip them.
        var json = "{"
                + "\"scope\":{\"id\":\"scope-id\",\"slug\":\"sc\",\"name\":\"Scope name\"},"
                + "\"project\":{\"id\":\"project-id\",\"slug\":\"pr\",\"version\":\"1\",\"name\":\"Project name\"},"
                + "\"id\":\"model-id\","
                + "\"slug\":\"model-slug\","
                + "\"name\":\"Model name\""
                + "}";
        var m = ResponseParser.parseModel(json);
        assertEquals("model-id", m.id());
        assertEquals("Model name", m.name());
        assertEquals("model-slug", m.slug());
        assertEquals("sc", m.scopeSlug());
        assertEquals("pr", m.projectSlug());
        assertEquals("1", m.projectVersion());
    }

    @Test
    void parseModelPageReadsEmbeddedAndMetadata() {
        var json = "{"
                + "\"_embedded\":{\"models\":["
                + MODEL_WITH_ARCHIMATE_LINK
                + "]},"
                + "\"totalElements\":5,"
                + "\"totalPages\":3,"
                + "\"number\":1"
                + "}";
        var page = ResponseParser.parseModelPage(json, 0);
        assertEquals(1, page.items().size());
        assertEquals("m1", page.items().get(0).id());
        assertEquals(5L, page.totalElements());
        assertEquals(3, page.totalPages());
        assertEquals(1, page.page());
        assertTrue(page.hasMore());
    }

    @Test
    void parseModelPageReturnsEmptyWhenNoEmbedded() {
        var page = ResponseParser.parseModelPage("{}", 2);
        assertEquals(0, page.items().size());
        assertEquals(0L, page.totalElements());
        assertEquals(1, page.totalPages());
        assertEquals(2, page.page());
    }

    @Test
    void parseProjectListReadsHalEmbedded() {
        var json = "{\"_embedded\":{\"projects\":["
                + "{\"id\":\"p1\",\"name\":\"Alpha\"},"
                + "{\"id\":\"p2\",\"name\":\"Beta\"}"
                + "]}}";
        var list = ResponseParser.parseProjectList(json);
        assertEquals(2, list.size());
        assertEquals("p1", list.get(0).id());
        assertEquals("Alpha", list.get(0).name());
        assertEquals("p2", list.get(1).id());
    }

    @Test
    void parseProjectListExtractsScopeFromRealisticSpringDataRestPayload() {
        // Realistic shape: project lists from Spring Data REST include the
        // owning scope as a nested object alongside the project's own fields,
        // followed by a _links block.
        var json = "{\"_embedded\":{\"projects\":[{"
                + "\"id\":\"p1\","
                + "\"slug\":\"alpha\","
                + "\"version\":\"1.0\","
                + "\"name\":\"Alpha\","
                + "\"description\":\"d\","
                + "\"confidentiality\":0,"
                + "\"previousVersionId\":null,"
                + "\"creator\":{\"id\":\"u1\",\"name\":\"alice\"},"
                + "\"creationDateTime\":\"2026-01-01T00:00:00Z\","
                + "\"lastModifier\":{\"id\":\"u1\",\"name\":\"alice\"},"
                + "\"lastModificationDateTime\":\"2026-01-02T00:00:00Z\","
                + "\"archiver\":null,"
                + "\"archivationDateTime\":null,"
                + "\"scope\":{\"id\":\"s1\",\"slug\":\"acme\",\"name\":\"ACME\"},"
                + "\"_links\":{\"self\":{\"href\":\"http://localhost/api/projects/p1\"}}"
                + "}]}}";
        var list = ResponseParser.parseProjectList(json);
        assertEquals(1, list.size());
        assertEquals("p1", list.get(0).id());
        assertEquals("Alpha", list.get(0).name());
        assertEquals("s1", list.get(0).scopeId());
        assertEquals("ACME", list.get(0).scopeName());
    }

    @Test
    void parseProjectListIgnoresNestedScopeIdAndName() {
        // Each project now embeds a `scope` reference whose id/name keys
        // appear before the project's own — make sure we skip them and
        // capture them as scopeId / scopeName instead.
        var json = "{\"_embedded\":{\"projects\":["
                + "{\"scope\":{\"id\":\"scope-1\",\"name\":\"Scope\"},\"id\":\"project-1\",\"name\":\"Alpha\"}"
                + "]}}";
        var list = ResponseParser.parseProjectList(json);
        assertEquals(1, list.size());
        assertEquals("project-1", list.get(0).id());
        assertEquals("Alpha", list.get(0).name());
        assertEquals("scope-1", list.get(0).scopeId());
        assertEquals("Scope", list.get(0).scopeName());
    }

    @Test
    void parseProjectListFallsBackToPlainArray() {
        var json = "[{\"id\":\"p1\",\"name\":\"Alpha\"}]";
        var list = ResponseParser.parseProjectList(json);
        assertEquals(1, list.size());
        assertEquals("Alpha", list.get(0).name());
    }

    @Test
    void parseProjectListSkipsObjectsWithoutId() {
        var json = "[{\"name\":\"NoId\"},{\"id\":\"p1\",\"name\":\"Has\"}]";
        var list = ResponseParser.parseProjectList(json);
        assertEquals(1, list.size());
        assertEquals("p1", list.get(0).id());
    }

    @Test
    void parseProjectListReturnsEmptyForEmptyInput() {
        assertNotNull(ResponseParser.parseProjectList("{}"));
        assertEquals(0, ResponseParser.parseProjectList("{}").size());
    }

    // -- findMatchingBracket edge cases (exercised via parse* entry points) --

    @Test
    void parseModelHandlesBracketsInsideStringValues() {
        // The description contains "}" and "]" that must NOT terminate the enclosing
        // JSON object or the _links.content array early.
        var json = "{"
                + "\"id\":\"m3\","
                + "\"name\":\"N\","
                + "\"description\":\"has ] and } inside\","
                + "\"_links\":{"
                + "\"self\":{\"href\":\"https://srv/m3\"},"
                + "\"content\":["
                + "{\"title\":\"ArchiMate\",\"href\":\"https://srv/m3/archimate\"}"
                + "]"
                + "}"
                + "}";
        var m = ResponseParser.parseModel(json);
        assertEquals("m3", m.id());
        assertEquals("has ] and } inside", m.description());
        assertEquals("https://srv/m3", m.selfUrl());
        assertEquals("https://srv/m3/archimate", m.contentUrl());
    }

    @Test
    void parseModelHandlesEscapedQuotesInStringValues() {
        // Escaped quote must not toggle "in-string" - otherwise findMatchingBracket
        // would think we've left the string and close the object early.
        var json = "{"
                + "\"id\":\"m4\","
                + "\"name\":\"with \\\"quotes\\\" inside\","
                + "\"description\":\"d\","
                + "\"_links\":{"
                + "\"self\":{\"href\":\"https://srv/m4\"},"
                + "\"content\":["
                + "{\"title\":\"ArchiMate\",\"href\":\"https://srv/m4/archimate\"}"
                + "]"
                + "}"
                + "}";
        var m = ResponseParser.parseModel(json);
        assertEquals("m4", m.id());
        assertEquals("https://srv/m4", m.selfUrl());
        assertEquals("https://srv/m4/archimate", m.contentUrl());
    }

    @Test
    void parseModelPageSeparatesModelsWithBracketsInStrings() {
        var tricky = "{"
                + "\"id\":\"t1\",\"name\":\"tricky\","
                + "\"description\":\"closing brace } and bracket ] and escaped quote \\\" done\","
                + "\"_links\":{"
                + "\"self\":{\"href\":\"https://srv/t1\"},"
                + "\"content\":["
                + "{\"title\":\"ArchiMate\",\"href\":\"https://srv/t1/archimate\"}"
                + "]"
                + "}"
                + "}";
        var json = "{\"_embedded\":{\"models\":["
                + tricky + "," + MODEL_WITH_ARCHIMATE_LINK
                + "]}}";
        var page = ResponseParser.parseModelPage(json, 0);
        assertEquals(2, page.items().size());
        assertEquals("t1", page.items().get(0).id());
        assertEquals("m1", page.items().get(1).id());
    }

    @Test
    void parseProjectListHandlesBracketsInsideNames() {
        var json = "[{\"id\":\"p1\",\"name\":\"weird } name ]\"},{\"id\":\"p2\",\"name\":\"ok\"}]";
        var list = ResponseParser.parseProjectList(json);
        assertEquals(2, list.size());
        assertEquals("weird } name ]", list.get(0).name());
        assertEquals("p2", list.get(1).id());
    }

    @Test
    void parseModelWithoutLinksReturnsNullUrls() {
        var json = "{\"id\":\"m5\",\"name\":\"no-links\"}";
        var m = ResponseParser.parseModel(json);
        assertEquals("m5", m.id());
        assertNull(m.selfUrl());
        assertNull(m.contentUrl());
    }

}

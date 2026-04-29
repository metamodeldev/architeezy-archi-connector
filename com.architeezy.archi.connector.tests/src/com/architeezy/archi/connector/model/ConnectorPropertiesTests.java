/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;

class ConnectorPropertiesTests {

    private static IArchimateModel newModel() {
        return IArchimateFactory.eINSTANCE.createArchimateModel();
    }

    // getProperty / setProperty / removeProperty ---------------------------

    @Test
    void getPropertyReturnsNullWhenMissing() {
        assertNull(ConnectorProperties.getProperty(newModel(), "foo"));
    }

    @Test
    void setPropertyCreatesEntryWhenAbsent() {
        var model = newModel();
        ConnectorProperties.setProperty(model, "foo", "bar");
        assertEquals("bar", ConnectorProperties.getProperty(model, "foo"));
        assertEquals(1, model.getProperties().size());
    }

    @Test
    void setPropertyUpdatesExistingEntry() {
        var model = newModel();
        ConnectorProperties.setProperty(model, "foo", "one");
        ConnectorProperties.setProperty(model, "foo", "two");
        assertEquals("two", ConnectorProperties.getProperty(model, "foo"));
        assertEquals(1, model.getProperties().size());
    }

    @Test
    void removePropertyDeletesEntry() {
        var model = newModel();
        ConnectorProperties.setProperty(model, "foo", "bar");
        ConnectorProperties.setProperty(model, "baz", "qux");
        ConnectorProperties.removeProperty(model, "foo");
        assertNull(ConnectorProperties.getProperty(model, "foo"));
        assertEquals("qux", ConnectorProperties.getProperty(model, "baz"));
    }

    // isTracked ------------------------------------------------------------

    @Test
    void isTrackedReflectsUrlProperty() {
        var model = newModel();
        assertFalse(ConnectorProperties.isTracked(model));
        ConnectorProperties.setProperty(model, ConnectorProperties.KEY_URL, "https://srv/api/models/123");
        assertTrue(ConnectorProperties.isTracked(model));
    }

    // extractModelId -------------------------------------------------------

    @Test
    void extractModelIdReturnsLastPathSegment() {
        assertEquals("123", ConnectorProperties.extractModelId("https://srv/api/models/123"));
        assertEquals("abc-def", ConnectorProperties.extractModelId("https://srv/api/models/abc-def"));
    }

    @Test
    void extractModelIdHandlesNullAndBlank() {
        assertNull(ConnectorProperties.extractModelId(null));
        assertNull(ConnectorProperties.extractModelId(""));
        assertNull(ConnectorProperties.extractModelId("   "));
    }

    // extractServerUrl -----------------------------------------------------

    @Test
    void extractServerUrlReturnsSchemeHost() {
        assertEquals("https://srv.example.com",
                ConnectorProperties.extractServerUrl("https://srv.example.com/api/models/1"));
    }

    @Test
    void extractServerUrlIncludesExplicitPort() {
        assertEquals("http://localhost:8080",
                ConnectorProperties.extractServerUrl("http://localhost:8080/api/models/1"));
    }

    @Test
    void extractServerUrlOmitsDefaultPort() {
        assertEquals("https://srv",
                ConnectorProperties.extractServerUrl("https://srv/api/models/1"));
    }

    @Test
    void extractServerUrlHandlesNullAndBlank() {
        assertNull(ConnectorProperties.extractServerUrl(null));
        assertNull(ConnectorProperties.extractServerUrl(""));
    }

    // normalizeServerUrl ---------------------------------------------------

    @Test
    void normalizeServerUrlStripsSingleTrailingSlash() {
        assertEquals("http://localhost:8080",
                ConnectorProperties.normalizeServerUrl("http://localhost:8080/"));
    }

    @Test
    void normalizeServerUrlStripsMultipleTrailingSlashes() {
        assertEquals("https://srv.example.com",
                ConnectorProperties.normalizeServerUrl("https://srv.example.com///"));
    }

    @Test
    void normalizeServerUrlLeavesUrlWithoutTrailingSlashUnchanged() {
        assertEquals("http://localhost:8080",
                ConnectorProperties.normalizeServerUrl("http://localhost:8080"));
    }

    @Test
    void normalizeServerUrlPreservesNullAndBlank() {
        assertNull(ConnectorProperties.normalizeServerUrl(null));
        assertEquals("", ConnectorProperties.normalizeServerUrl(""));
        assertEquals("   ", ConnectorProperties.normalizeServerUrl("   "));
    }

}

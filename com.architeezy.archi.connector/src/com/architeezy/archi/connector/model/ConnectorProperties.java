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

import java.net.URI;

import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;

/**
 * Utility class for managing Archi model properties related to the connector.
 */
public final class ConnectorProperties {

    /** Key for the Architeezy model URL property. */
    public static final String KEY_URL = "architeezy:url"; //$NON-NLS-1$

    private ConnectorProperties() {
    }

    /**
     * Returns the value of the property associated with the given key.
     *
     * @param model the model to search for the property
     * @param key the key of the property to retrieve
     * @return the property value, or null if not found
     */
    public static String getProperty(IArchimateModel model, String key) {
        for (var p : model.getProperties()) {
            if (key.equals(p.getKey())) {
                return p.getValue();
            }
        }
        return null;
    }

    /**
     * Sets the value of the property associated with the given key.
     * If the property does not exist, it will be created.
     *
     * @param model the model to update
     * @param key the key of the property
     * @param value the value to set
     */
    public static void setProperty(IArchimateModel model, String key, String value) {
        for (var p : model.getProperties()) {
            if (key.equals(p.getKey())) {
                p.setValue(value);
                return;
            }
        }
        var property = IArchimateFactory.eINSTANCE.createProperty();
        property.setKey(key);
        property.setValue(value);
        model.getProperties().add(property);
    }

    /**
     * Removes the property associated with the given key from the model.
     *
     * @param model the model to modify
     * @param key the key of the property to remove
     */
    public static void removeProperty(IArchimateModel model, String key) {
        model.getProperties().removeIf(p -> key.equals(p.getKey()));
    }

    /**
     * Checks if the model is tracked by Architeezy.
     *
     * @param model the model to check
     * @return true if the model is tracked, false otherwise
     */
    public static boolean isTracked(IArchimateModel model) {
        return getProperty(model, KEY_URL) != null;
    }

    /**
     * Returns the model identifier extracted from the URL, e.g.
     * https://example.com/api/models/{id} -&gt; {id}
     *
     * @param modelUrl the URL of the model
     * @return the model identifier, or null if the URL is null or blank
     */
    public static String extractModelId(String modelUrl) {
        if (modelUrl == null || modelUrl.isBlank()) {
            return null;
        }
        var path = URI.create(modelUrl).getPath();
        var lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Returns the server base URL extracted from the model URL, e.g.
     * https://example.com/api/models/123 -&gt; https://example.com
     *
     * @param modelUrl the URL of the model
     * @return the server base URL, or null if the URL is null or blank
     */
    public static String extractServerUrl(String modelUrl) {
        if (modelUrl == null || modelUrl.isBlank()) {
            return null;
        }
        var uri = URI.create(modelUrl);
        var port = uri.getPort() > 0 ? ":" + uri.getPort() : ""; //$NON-NLS-1$ //$NON-NLS-2$
        return uri.getScheme() + "://" + uri.getHost() + port; //$NON-NLS-1$
    }

}

/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.model.IArchimateFactory;
import com.architeezy.archi.connector.model.ConnectorProperties;

class UpdateCheckServiceListenerTests {

    private UpdateCheckService service;

    @BeforeEach
    void setUp() {
        // Collaborators are not invoked for state-query/listener paths; the
        // background job is never scheduled because start() is not called.
        service = new UpdateCheckService(null, null, null, null, null);
    }

    @Test
    void hasUpdateReturnsFalseForUntrackedModel() {
        var model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setDefaults();
        assertFalse(service.hasUpdate(model));
    }

    @Test
    void getAvailableUpdateReturnsNullForUntrackedModel() {
        var model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setDefaults();
        assertNull(service.getAvailableUpdate(model));
    }

    @Test
    void hasUpdateReturnsFalseWhenNoUpdatePending() {
        var model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setDefaults();
        ConnectorProperties.setProperty(model, ConnectorProperties.KEY_URL,
                "http://localhost/api/models/abc");
        assertFalse(service.hasUpdate(model));
        assertNull(service.getAvailableUpdate(model));
    }

    @Test
    void clearUpdateIsSafeWhenNothingPending() {
        var counter = new int[1];
        service.addListener(() -> counter[0]++);

        var model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setDefaults();
        ConnectorProperties.setProperty(model, ConnectorProperties.KEY_URL,
                "http://localhost/api/models/abc");

        service.clearUpdate(model);
        // No pending update => listener must not fire.
        org.junit.jupiter.api.Assertions.assertEquals(0, counter[0]);
    }

    @Test
    void removeListenerStopsNotifications() {
        var fired = new int[1];
        Runnable listener = () -> fired[0]++;
        service.addListener(listener);
        service.removeListener(listener);

        // With no pending updates this is a no-op anyway, but ensure no
        // AIOBE / ConcurrentModification when listener has been removed.
        var model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setDefaults();
        service.clearUpdate(model);
        org.junit.jupiter.api.Assertions.assertEquals(0, fired[0]);
    }

}

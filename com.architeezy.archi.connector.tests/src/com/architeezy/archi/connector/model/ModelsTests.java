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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;

class ModelsTests {

    private static IArchimateModel newUntrackedModel() {
        return IArchimateFactory.eINSTANCE.createArchimateModel();
    }

    private static IArchimateModel newTrackedModel() {
        var model = newUntrackedModel();
        ConnectorProperties.setProperty(model, ConnectorProperties.KEY_URL,
                "https://srv/api/models/1");
        return model;
    }

    @Test
    void returnsNullForNullCandidates() {
        assertNull(Models.firstUntrackedModel(null));
    }

    @Test
    void returnsNullForEmptyCandidates() {
        assertNull(Models.firstUntrackedModel(List.of()));
    }

    @Test
    void returnsUntrackedModelDirectly() {
        var model = newUntrackedModel();
        assertSame(model, Models.firstUntrackedModel(List.of(model)));
    }

    @Test
    void skipsTrackedModel() {
        var model = newTrackedModel();
        assertNull(Models.firstUntrackedModel(List.of(model)));
    }

    @Test
    void resolvesOwningModelFromArchimateObject() {
        var model = newUntrackedModel();
        var element = IArchimateFactory.eINSTANCE.createBusinessActor();
        model.getDefaultFolderForObject(element).getElements().add(element);
        assertSame(model, Models.firstUntrackedModel(List.of(element)));
    }

    @Test
    void returnsFirstUntrackedAmongCandidates() {
        var tracked = newTrackedModel();
        var untracked = newUntrackedModel();
        assertSame(untracked, Models.firstUntrackedModel(List.of(tracked, untracked)));
    }

    @Test
    void ignoresUnrelatedElements() {
        assertNull(Models.firstUntrackedModel(List.of("some string", 42)));
    }

}

/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.ui.handlers;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.junit.jupiter.api.Test;

import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.model.ConnectorProperties;

class ExportMenuHandlerModelFromSelectionTests {

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
    void returnsNullForEmptySelection() {
        assertNull(ExportMenuHandler.modelFromSelection(StructuredSelection.EMPTY));
    }

    @Test
    void returnsNullForNonStructuredSelection() {
        ISelection notStructured = () -> true;
        assertNull(ExportMenuHandler.modelFromSelection(notStructured));
    }

    @Test
    void returnsUntrackedModelFromSelection() {
        var model = newUntrackedModel();
        assertSame(model, ExportMenuHandler.modelFromSelection(new StructuredSelection(model)));
    }

    @Test
    void skipsTrackedModel() {
        var model = newTrackedModel();
        assertNull(ExportMenuHandler.modelFromSelection(new StructuredSelection(model)));
    }

    @Test
    void resolvesOwningModelFromArchimateObject() {
        var model = newUntrackedModel();
        var element = IArchimateFactory.eINSTANCE.createBusinessActor();
        model.getDefaultFolderForObject(element).getElements().add(element);
        assertSame(model, ExportMenuHandler.modelFromSelection(new StructuredSelection(element)));
    }

    @Test
    void returnsFirstUntrackedModelAmongCandidates() {
        var tracked = newTrackedModel();
        var untracked = newUntrackedModel();
        var sel = new StructuredSelection(new Object[] {tracked, untracked});
        assertSame(untracked, ExportMenuHandler.modelFromSelection(sel));
    }

    @Test
    void ignoresUnrelatedElementsInSelection() {
        var sel = new StructuredSelection(new Object[] {"some string", 42});
        assertNull(ExportMenuHandler.modelFromSelection(sel));
    }

}

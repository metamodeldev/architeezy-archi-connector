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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.archimatetool.editor.model.IEditorModelManager;

class LocalChangeServiceTests {

    @Test
    void recheckTriggersIncludeLoadOpenCreateSaved() {
        assertTrue(LocalChangeService.isRecheckTrigger(IEditorModelManager.PROPERTY_MODEL_LOADED));
        assertTrue(LocalChangeService.isRecheckTrigger(IEditorModelManager.PROPERTY_MODEL_OPENED));
        assertTrue(LocalChangeService.isRecheckTrigger(IEditorModelManager.PROPERTY_MODEL_CREATED));
        assertTrue(LocalChangeService.isRecheckTrigger(IEditorModelManager.PROPERTY_MODEL_SAVED));
    }

    @Test
    void recheckTriggersExcludeRemovedAndCommandStack() {
        assertFalse(LocalChangeService.isRecheckTrigger(IEditorModelManager.PROPERTY_MODEL_REMOVED));
        assertFalse(LocalChangeService.isRecheckTrigger(IEditorModelManager.COMMAND_STACK_CHANGED));
    }

    @Test
    void recheckTriggersHandleNullAndUnknown() {
        assertFalse(LocalChangeService.isRecheckTrigger(null));
        assertFalse(LocalChangeService.isRecheckTrigger("some.other.property"));
        assertFalse(LocalChangeService.isRecheckTrigger(""));
    }

}

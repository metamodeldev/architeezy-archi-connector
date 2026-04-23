/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.ui.dialogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.architeezy.archi.connector.model.diff.Resolution;

class ConflictResolutionDialogHelpersTests {

    @Test
    void column1IsLocalAndColumn2IsRemote() {
        assertEquals(Resolution.LOCAL, ConflictResolutionDialog.resolutionForColumn(1));
        assertEquals(Resolution.REMOTE, ConflictResolutionDialog.resolutionForColumn(2));
    }

    @Test
    void structureAndOutOfRangeColumnsReturnNull() {
        assertNull(ConflictResolutionDialog.resolutionForColumn(0));
        assertNull(ConflictResolutionDialog.resolutionForColumn(3));
        assertNull(ConflictResolutionDialog.resolutionForColumn(-1));
    }

}

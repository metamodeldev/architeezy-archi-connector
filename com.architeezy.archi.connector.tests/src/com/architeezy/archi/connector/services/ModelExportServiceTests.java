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

import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;

class ModelExportServiceTests {

    private static IArchimateModel withName(String name) {
        var model = IArchimateFactory.eINSTANCE.createArchimateModel();
        model.setName(name);
        return model;
    }

    @Test
    void buildExportFileNameUsesModelName() {
        var fileName = ModelExportService.buildExportFileName(withName("Sales"));
        assertTrue(fileName.startsWith("Sales-"), fileName);
        assertTrue(fileName.endsWith(".archimate"), fileName);
    }

    @Test
    void buildExportFileNameFallsBackWhenNameNull() {
        var fileName = ModelExportService.buildExportFileName(withName(null));
        assertTrue(fileName.startsWith("model-"), fileName);
        assertTrue(fileName.endsWith(".archimate"), fileName);
    }

    @Test
    void buildExportFileNameFallsBackWhenNameBlank() {
        var fileName = ModelExportService.buildExportFileName(withName("   "));
        assertTrue(fileName.startsWith("model-"), fileName);
    }

    @Test
    void buildExportFileNameSanitizesIllegalCharacters() {
        var fileName = ModelExportService.buildExportFileName(
                withName("a\\b/c:d*e?f\"g<h>i|j"));
        assertTrue(fileName.startsWith("a_b_c_d_e_f_g_h_i_j-"), fileName);
        assertFalse(fileName.contains("\\"), fileName);
        assertFalse(fileName.contains("/"), fileName);
        assertFalse(fileName.contains(":"), fileName);
        assertFalse(fileName.contains("*"), fileName);
        assertFalse(fileName.contains("?"), fileName);
        assertFalse(fileName.contains("\""), fileName);
        assertFalse(fileName.contains("<"), fileName);
        assertFalse(fileName.contains(">"), fileName);
        assertFalse(fileName.contains("|"), fileName);
    }

    @Test
    void buildExportFileNameHasTimestampBetweenBaseAndExtension() {
        var fileName = ModelExportService.buildExportFileName(withName("X"));
        // Pattern is yyyyMMdd-HHmm, e.g. X-20260423-0941.archimate
        var body = fileName.substring(0, fileName.length() - ".archimate".length());
        // Expect "X-<8 digits>-<4 digits>"
        assertTrue(body.matches("X-\\d{8}-\\d{4}"), fileName);
    }

}

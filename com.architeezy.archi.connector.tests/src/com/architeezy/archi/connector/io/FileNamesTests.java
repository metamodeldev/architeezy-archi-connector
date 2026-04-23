/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FileNamesTests {

    @Test
    void returnsDefaultForNull() {
        assertEquals("model", FileNames.sanitize(null));
    }

    @Test
    void passesThroughPlainName() {
        assertEquals("My Model", FileNames.sanitize("My Model"));
    }

    @Test
    void replacesWindowsReservedCharacters() {
        assertEquals("a_b_c_d_e_f_g_h_i",
                FileNames.sanitize("a\\b/c:d*e?f\"g<h>i"));
        assertEquals("x_y", FileNames.sanitize("x|y"));
    }

    @Test
    void preservesSafePunctuation() {
        assertEquals("v1.2-rc_name", FileNames.sanitize("v1.2-rc_name"));
    }

    @Test
    void returnsEmptyStringUnchanged() {
        assertEquals("", FileNames.sanitize(""));
    }

}

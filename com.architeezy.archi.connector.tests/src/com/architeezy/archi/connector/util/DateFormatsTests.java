/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;

class DateFormatsTests {

    private static final DateTimeFormatter EXPECTED_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    @Test
    void blankAndNullInputsReturnEmDash() {
        assertEquals("—", DateFormats.formatIsoDateTime(null));
        assertEquals("—", DateFormats.formatIsoDateTime(""));
        assertEquals("—", DateFormats.formatIsoDateTime("   "));
    }

    @Test
    void parsesZuluInstant() {
        var iso = "2026-04-19T08:30:00Z";
        var expected = EXPECTED_FMT.format(Instant.parse(iso));
        assertEquals(expected, DateFormats.formatIsoDateTime(iso));
    }

    @Test
    void parsesOffsetDateTime() {
        var iso = "2026-04-19T11:30:00+03:00";
        var expected = EXPECTED_FMT.format(Instant.parse("2026-04-19T08:30:00Z"));
        assertEquals(expected, DateFormats.formatIsoDateTime(iso));
    }

    @Test
    void returnsInputUnchangedOnUnparseableString() {
        assertEquals("not-a-date", DateFormats.formatIsoDateTime("not-a-date"));
    }

}

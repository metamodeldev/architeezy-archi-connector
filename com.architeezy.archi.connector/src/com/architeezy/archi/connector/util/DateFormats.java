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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Utility methods for rendering dates from the Architeezy API.
 */
public final class DateFormats {

    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") //$NON-NLS-1$
            .withZone(ZoneId.systemDefault());

    private DateFormats() {
    }

    /**
     * Formats an ISO-8601 date/time string as {@code yyyy-MM-dd HH:mm} in the
     * system timezone. Accepts both zoned ({@link OffsetDateTime}) and
     * instant ({@link Instant}) representations.
     *
     * @param iso the ISO-8601 string, may be {@code null} or blank
     * @return the formatted string; an em-dash for {@code null}/blank input;
     *         the input unchanged if it cannot be parsed
     */
    public static String formatIsoDateTime(String iso) {
        if (iso == null || iso.isBlank()) {
            return "\u2014"; //$NON-NLS-1$
        }
        try {
            return DISPLAY_FMT.format(OffsetDateTime.parse(iso).toInstant());
        } catch (DateTimeParseException e1) {
            try {
                return DISPLAY_FMT.format(Instant.parse(iso));
            } catch (DateTimeParseException e2) {
                return iso;
            }
        }
    }

}

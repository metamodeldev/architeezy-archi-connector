/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Hand-rolled {@code multipart/form-data} builder used by the model export
 * request. Two parts are produced: an {@code entity} JSON part with the target
 * project id, and a {@code content} part carrying the raw {@code .archimate}
 * file bytes.
 */
final class MultipartBuilder {

    private static final String CRLF = "\r\n"; //$NON-NLS-1$

    private static final String SEP = "--"; //$NON-NLS-1$

    private static final String QUOTE = "\""; //$NON-NLS-1$

    private MultipartBuilder() {
    }

    /**
     * Builds the multipart body bytes.
     *
     * @param boundary the multipart boundary (without leading dashes)
     * @param projectId target project id, embedded in the entity JSON part
     * @param fileName file name presented in the {@code content} part header
     * @param content raw {@code .archimate} file bytes
     * @return the assembled multipart body
     * @throws ApiException if the in-memory write fails (effectively never)
     */
    static byte[] build(String boundary, String projectId, String fileName, byte[] content)
            throws ApiException {
        try {
            final var out = new ByteArrayOutputStream();
            final var entityJson = "{\"projectId\":" + jsonString(projectId) + "}"; //$NON-NLS-1$ //$NON-NLS-2$

            out.write((SEP + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"entity\"; filename=\"blob\"" + CRLF) //$NON-NLS-1$
                    .getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: application/json" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$
            out.write(entityJson.getBytes(StandardCharsets.UTF_8));
            out.write(CRLF.getBytes(StandardCharsets.UTF_8));

            out.write((SEP + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"content\"; filename=" + QUOTE + fileName + QUOTE //$NON-NLS-1$
                    + CRLF).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: application/octet-stream" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$
            out.write(content);
            out.write(CRLF.getBytes(StandardCharsets.UTF_8));

            out.write((SEP + boundary + SEP + CRLF).getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException("Failed to build multipart body", e); //$NON-NLS-1$
        }
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null"; //$NON-NLS-1$
        }
        return QUOTE + value.replace("\\", "\\\\").replace(QUOTE, "\\" + QUOTE) + QUOTE; //$NON-NLS-1$ //$NON-NLS-2$
    }

}

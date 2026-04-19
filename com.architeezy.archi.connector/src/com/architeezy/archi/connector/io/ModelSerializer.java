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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

import org.eclipse.emf.ecore.util.EcoreUtil;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.util.ArchimateResourceFactory;

/**
 * Serializes and deserializes IArchimateModel objects using the native Archi
 * XMI format via ArchimateResourceFactory.
 *
 * All operations use a temporary file to avoid partial writes.
 */
public final class ModelSerializer {

    /** The singleton instance of ModelSerializer. */
    public static final ModelSerializer INSTANCE = new ModelSerializer();

    private ModelSerializer() {
    }

    /**
     * Serializes a model to its native XMI representation.
     * A deep copy is made so the original model is not attached to the temporary
     * Resource.
     *
     * @param model The model to serialize.
     * @return The serialized model content as bytes.
     * @throws IOException If an I/O error occurs.
     */
    public byte[] serialize(IArchimateModel model) throws IOException {
        var tmp = File.createTempFile("archi-connector-", ".archimate"); //$NON-NLS-1$ //$NON-NLS-2$
        try {
            var resource = ArchimateResourceFactory.createNewResource(tmp);
            resource.getContents().add(EcoreUtil.copy(model));
            resource.save(Collections.emptyMap());
            return Files.readAllBytes(tmp.toPath());
        } finally {
            tmp.delete();
        }
    }

    /**
     * Deserializes XMI bytes into an IArchimateModel loaded at {@code targetFile}.
     * The returned model is attached to a Resource backed by {@code targetFile},
     * ready to be opened by IEditorModelManager.
     *
     * @param data The XMI data to deserialize.
     * @param targetFile The file where the model will be loaded.
     * @return The deserialized IArchimateModel.
     * @throws IOException If the data is invalid or an I/O error occurs.
     */
    public IArchimateModel deserialize(byte[] data, File targetFile) throws IOException {
        Files.write(targetFile.toPath(), data);

        var resource = ArchimateResourceFactory.createNewResource(targetFile);
        try {
            resource.load(Collections.emptyMap());
        } catch (Exception e) {
            targetFile.delete();
            throw new IOException("Failed to load model from server data: " + e.getMessage(), e); //$NON-NLS-1$
        }

        if (resource.getContents().isEmpty()) {
            targetFile.delete();
            throw new IOException("Server returned an empty model"); //$NON-NLS-1$
        }

        var model = (IArchimateModel) resource.getContents().get(0);
        model.setFile(targetFile);
        return model;
    }

}

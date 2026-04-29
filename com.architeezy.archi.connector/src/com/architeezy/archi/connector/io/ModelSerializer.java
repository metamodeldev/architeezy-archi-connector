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

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;

import com.archimatetool.editor.model.IArchiveManager;
import com.archimatetool.model.IArchimateModel;

/**
 * Serializes and deserializes IArchimateModel objects using the native Archi
 * archive format via {@link IArchiveManager}, so embedded images survive a
 * round trip.
 *
 * All operations use a temporary file to avoid partial writes.
 */
public final class ModelSerializer {

    /** Creates a new serializer. */
    public ModelSerializer() {
        // No initialization required; kept explicit for clarity.
    }

    /**
     * Serializes a model to its native archive representation, preserving any
     * embedded images carried by its {@link IArchiveManager}.
     *
     * @param model the model to serialize
     * @return the serialized model content as bytes
     * @throws IOException if an I/O error occurs
     */
    public byte[] serialize(IArchimateModel model) throws IOException {
        var tmp = File.createTempFile("archi-connector-", ".archimate"); //$NON-NLS-1$ //$NON-NLS-2$
        try {
            var copy = EcoreUtil.copy(model);
            copy.setFile(tmp);

            var sourceManager = (IArchiveManager) model.getAdapter(IArchiveManager.class);
            var copyManager = (sourceManager != null)
                    ? sourceManager.clone(copy)
                    : IArchiveManager.FACTORY.createArchiveManager(copy);
            copy.setAdapter(IArchiveManager.class, copyManager);

            copyManager.saveModel();
            return Files.readAllBytes(tmp.toPath());
        } finally {
            Files.deleteIfExists(tmp.toPath());
        }
    }

    /**
     * Deserializes archive bytes into a transient IArchimateModel without
     * persisting to any user-visible file. The model is backed by a temporary
     * file scheduled for deletion on JVM exit. Use this when you need the
     * object graph for comparison or import operations, not for user-facing
     * model management.
     *
     * @param data the archive data to deserialize
     * @return the deserialized IArchimateModel
     * @throws IOException if the data is invalid or an I/O error occurs
     */
    public IArchimateModel deserializeInMemory(byte[] data) throws IOException {
        var tmp = File.createTempFile("archi-connector-tmp-", ".archimate"); //$NON-NLS-1$ //$NON-NLS-2$
        tmp.deleteOnExit();
        return deserialize(data, tmp);
    }

    /**
     * Deserializes archive bytes into an IArchimateModel loaded at
     * {@code targetFile}. The returned model is attached to a Resource backed
     * by {@code targetFile}, has a fresh {@link IArchiveManager} adapter, and
     * is ready to be opened by IEditorModelManager or fed to ModelImporter.
     *
     * @param data the archive data to deserialize
     * @param targetFile the file where the model will be loaded
     * @return the deserialized IArchimateModel
     * @throws IOException if the data is invalid or an I/O error occurs
     */
    public IArchimateModel deserialize(byte[] data, File targetFile) throws IOException {
        Files.write(targetFile.toPath(), data);

        Resource resource = IArchiveManager.FACTORY.createResource(targetFile);
        try {
            resource.load(Collections.emptyMap());
        } catch (Exception e) {
            Files.deleteIfExists(targetFile.toPath());
            throw new IOException("Failed to load model from server data: " + e.getMessage(), e); //$NON-NLS-1$
        }

        if (resource.getContents().isEmpty()) {
            Files.deleteIfExists(targetFile.toPath());
            throw new IOException("Server returned an empty model"); //$NON-NLS-1$
        }

        var model = (IArchimateModel) resource.getContents().get(0);
        model.setFile(targetFile);

        var archiveManager = IArchiveManager.FACTORY.createArchiveManager(model);
        model.setAdapter(IArchiveManager.class, archiveManager);
        archiveManager.loadImages();

        return model;
    }

}

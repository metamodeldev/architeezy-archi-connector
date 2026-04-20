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

import org.eclipse.core.runtime.Platform;

import com.architeezy.archi.connector.ConnectorPlugin;

/**
 * Stores base snapshots of synchronized models in the plugin's state location.
 *
 * Snapshots are used as the "base" in 3-way merges and for rollback on failure.
 * Location: Platform.getStateLocation(bundle)/snapshots/{modelId}.archimate
 */
public final class SnapshotStore {

    /** The singleton instance of SnapshotStore. */
    public static final SnapshotStore INSTANCE = new SnapshotStore();

    private SnapshotStore() {
    }

    /**
     * Saves a snapshot of the model content.
     *
     * @param modelId the model identifier
     * @param content the content to save
     * @throws IOException if an I/O error occurs
     */
    public void saveSnapshot(String modelId, byte[] content) throws IOException {
        File file = snapshotFile(modelId);
        file.getParentFile().mkdirs();
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp"); //$NON-NLS-1$
        try {
            Files.write(tmp.toPath(), content);
            Files.move(tmp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            tmp.delete();
            throw new IOException("Failed to save snapshot for " + modelId, e); //$NON-NLS-1$
        }
    }

    /**
     * Loads the snapshot for the given model identifier.
     *
     * @param modelId the model identifier
     * @return the snapshot content
     * @throws IOException if the snapshot is not found or an I/O error occurs
     */
    public byte[] loadSnapshot(String modelId) throws IOException {
        File file = snapshotFile(modelId);
        if (!file.exists()) {
            throw new IOException("No snapshot found for model: " + modelId); //$NON-NLS-1$
        }
        return Files.readAllBytes(file.toPath());
    }

    /**
     * Checks if a snapshot exists for the given model identifier.
     *
     * @param modelId the model identifier
     * @return true if a snapshot exists, false otherwise
     */
    public boolean hasSnapshot(String modelId) {
        return snapshotFile(modelId).exists();
    }

    /**
     * Deletes the snapshot for the given model identifier.
     *
     * @param modelId the model identifier
     */
    public void deleteSnapshot(String modelId) {
        snapshotFile(modelId).delete();
    }

    /**
     * Resolves the snapshot file for the given model identifier.
     *
     * @param modelId the model identifier
     * @return the file object pointing to the snapshot
     */
    private File snapshotFile(String modelId) {
        File stateLocation = Platform.getStateLocation(
                Platform.getBundle(ConnectorPlugin.PLUGIN_ID)).toFile();
        return new File(stateLocation, "snapshots/" + sanitize(modelId) + ".archimate"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Strips characters that are illegal in file names.
     *
     * @param modelId the model identifier to sanitize
     * @return the sanitized model identifier
     */
    private static String sanitize(String modelId) {
        return modelId.replaceAll("[^a-zA-Z0-9_\\-]", "_"); //$NON-NLS-1$ //$NON-NLS-2$
    }

}

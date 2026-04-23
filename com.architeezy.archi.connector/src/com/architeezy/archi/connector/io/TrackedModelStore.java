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
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import org.eclipse.core.runtime.Platform;

import com.architeezy.archi.connector.ConnectorPlugin;

/**
 * Workspace-level metadata for tracked Architeezy models.
 *
 * <p>
 * Stores per-model data (currently just {@code lastModificationDateTime}) that
 * would otherwise be serialized into the {@code .archimate} file. Keeping this
 * out of the model ensures the serialized model bytes match the server's
 * stored content byte-for-byte, so snapshot comparisons stay stable.
 *
 * <p>
 * Persisted as a {@link Properties} file under the plugin's state location.
 * Writes are atomic via a tmp+rename.
 */
public final class TrackedModelStore {

    /** The singleton instance. */
    public static final TrackedModelStore INSTANCE = new TrackedModelStore();

    private static final String FILE_NAME = "tracked-models.properties"; //$NON-NLS-1$

    private static final String KEY_LAST_MODIFIED_SUFFIX = ".lastModificationDateTime"; //$NON-NLS-1$

    private final Object lock = new Object();

    private Properties cache;

    private TrackedModelStore() {
    }

    /**
     * Returns the last-modification timestamp stored for the given model, or
     * {@code null} if none is recorded.
     *
     * @param modelId repository model identifier
     * @return the stored timestamp string, or {@code null}
     */
    public String getLastModified(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return null;
        }
        synchronized (lock) {
            return load().getProperty(modelId + KEY_LAST_MODIFIED_SUFFIX);
        }
    }

    /**
     * Stores the last-modification timestamp for the given model. A
     * {@code null} or blank value removes any existing entry.
     *
     * @param modelId repository model identifier
     * @param timestamp the timestamp string to store, or {@code null} to clear
     */
    public void setLastModified(String modelId, String timestamp) {
        if (modelId == null || modelId.isBlank()) {
            return;
        }
        synchronized (lock) {
            var props = load();
            var key = modelId + KEY_LAST_MODIFIED_SUFFIX;
            if (timestamp == null || timestamp.isBlank()) {
                if (props.remove(key) == null) {
                    return;
                }
            } else {
                var prev = props.setProperty(key, timestamp);
                if (timestamp.equals(prev)) {
                    return;
                }
            }
            save(props);
        }
    }

    /**
     * Removes all entries for the given model.
     *
     * @param modelId repository model identifier
     */
    public void remove(String modelId) {
        setLastModified(modelId, null);
    }

    private Properties load() {
        if (cache != null) {
            return cache;
        }
        var props = new Properties();
        var file = storeFile();
        if (file.exists()) {
            try (var in = Files.newInputStream(file.toPath())) {
                props.load(in);
            } catch (IOException e) {
                ConnectorPlugin.getInstance().getLog()
                        .warn("Failed to load tracked-models store", e); //$NON-NLS-1$
            }
        }
        cache = props;
        return cache;
    }

    private void save(Properties props) {
        var file = storeFile();
        file.getParentFile().mkdirs();
        var tmp = new File(file.getParentFile(), file.getName() + ".tmp"); //$NON-NLS-1$
        try {
            try (var out = Files.newOutputStream(tmp.toPath())) {
                props.store(out, "Architeezy tracked models"); //$NON-NLS-1$
            }
            Files.move(tmp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            tmp.delete();
            ConnectorPlugin.getInstance().getLog()
                    .error("Failed to save tracked-models store", e); //$NON-NLS-1$
        }
    }

    private static File storeFile() {
        var stateLocation = Platform.getStateLocation(
                Platform.getBundle(ConnectorPlugin.PLUGIN_ID)).toFile();
        return new File(stateLocation, FILE_NAME);
    }

}

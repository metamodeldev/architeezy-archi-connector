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

import org.eclipse.core.runtime.IProgressMonitor;

import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.ConnectorPlugin;

/**
 * Shared snapshot-writing helpers used after import, export, pull, and push.
 */
public final class SnapshotSupport {

    private SnapshotSupport() {
    }

    /**
     * Serializes the model WITH connector properties already set and saves the
     * result as the base snapshot. Storing the post-configure bytes ensures that
     * the next comparison (local vs. base) produces equal results when no edits
     * have been made since the last import/pull/publish.
     *
     * @param model the configured model
     * @param modelId the repository model identifier
     * @param monitor progress monitor
     */
    public static void saveSnapshotAfterConfigure(IArchimateModel model, String modelId,
            IProgressMonitor monitor) {
        if (monitor != null) {
            monitor.subTask("Saving snapshot"); //$NON-NLS-1$
        }
        try {
            var bytes = ModelSerializer.INSTANCE.serialize(model);
            SnapshotStore.INSTANCE.saveSnapshot(modelId, bytes);
        } catch (Exception e) {
            ConnectorPlugin.getInstance().getLog().error("Failed to save initial snapshot", e); //$NON-NLS-1$
        }
    }

    /**
     * Updates the progress monitor's subtask label, tolerating a {@code null} monitor.
     *
     * @param monitor progress monitor, may be {@code null}
     * @param task    subtask label
     */
    public static void setSubTask(IProgressMonitor monitor, String task) {
        if (monitor != null) {
            monitor.subTask(task);
        }
    }

}

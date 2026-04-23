/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.model;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.List;

import com.archimatetool.model.IArchimateModel;

/**
 * Narrow adapter over {@link com.archimatetool.editor.model.IEditorModelManager}
 * covering only the operations the connector depends on.
 *
 * Abstracting the singleton lets services be unit-tested with an in-memory
 * fake instead of the workbench-backed editor model manager.
 */
public interface IEditorModelManagerAdapter {

    /**
     * Returns the list of currently open models, or an empty list if the editor
     * has no models loaded.
     *
     * @return the open models
     */
    List<IArchimateModel> getModels();

    /**
     * Returns {@code true} if the given model has unsaved changes.
     *
     * @param model the model to check
     * @return {@code true} when dirty
     */
    boolean isModelDirty(IArchimateModel model);

    /**
     * Opens the given model in the editor.
     *
     * @param model the model to open
     */
    void openModel(IArchimateModel model);

    /**
     * Persists the given model to its file.
     *
     * @param model the model to save
     * @return {@code true} if the save succeeded
     * @throws IOException if writing fails
     */
    boolean saveModel(IArchimateModel model) throws IOException;

    /**
     * Registers a listener for model-lifecycle events (loaded, opened, saved,
     * removed, command-stack changes).
     *
     * @param listener the listener to add
     */
    void addPropertyChangeListener(PropertyChangeListener listener);

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    void removePropertyChangeListener(PropertyChangeListener listener);

}

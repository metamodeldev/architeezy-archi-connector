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

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;

/**
 * Production adapter that delegates to
 * {@link IEditorModelManager#INSTANCE the workbench-backed singleton}.
 */
public final class DefaultEditorModelManager implements IEditorModelManagerAdapter {

    @Override
    public List<IArchimateModel> getModels() {
        return IEditorModelManager.INSTANCE.getModels();
    }

    @Override
    public boolean isModelDirty(IArchimateModel model) {
        return IEditorModelManager.INSTANCE.isModelDirty(model);
    }

    @Override
    public void openModel(IArchimateModel model) {
        IEditorModelManager.INSTANCE.openModel(model);
    }

    @Override
    public boolean saveModel(IArchimateModel model) throws IOException {
        return IEditorModelManager.INSTANCE.saveModel(model);
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        IEditorModelManager.INSTANCE.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        IEditorModelManager.INSTANCE.removePropertyChangeListener(listener);
    }

}

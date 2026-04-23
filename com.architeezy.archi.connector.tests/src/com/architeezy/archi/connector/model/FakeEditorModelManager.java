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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.io.ModelSerializer;

/**
 * Test double for {@link IEditorModelManagerAdapter}. The production
 * implementation delegates to {@code IEditorModelManager.INSTANCE}, a
 * workbench-bound singleton that cannot be exercised in a headless JUnit
 * test. This fake implements the same contract against plain in-memory
 * state, except for {@link #saveModel}, which performs a real write to the
 * file the model was opened from so callers can observe the on-disk
 * effect.
 *
 * The only injection point for failure is {@link #saveError}, which
 * simulates an I/O error without having to fabricate one with a read-only
 * target file (brittle across operating systems).
 */
public final class FakeEditorModelManager implements IEditorModelManagerAdapter {

    /** Throw this exception from the next {@link #saveModel} call. */
    public java.io.IOException saveError;

    /** Throw this exception from the next {@link #openModel} call. */
    public RuntimeException openError;

    private final ModelSerializer serializer = new ModelSerializer();

    private final List<IArchimateModel> models = new ArrayList<>();

    private final Set<IArchimateModel> dirty = new HashSet<>();

    private final List<PropertyChangeListener> listeners = new ArrayList<>();

    /**
     * Registers a model as "open" so that {@link #getModels()} returns it.
     *
     * @param model the model to register
     */
    public void register(IArchimateModel model) {
        models.add(model);
    }

    /**
     * Marks a model as dirty so {@link #isModelDirty(IArchimateModel)} returns {@code true}.
     *
     * @param model the model to mark dirty
     */
    public void markDirty(IArchimateModel model) {
        dirty.add(model);
    }

    /**
     * Dispatches a property-change event to all registered listeners.
     *
     * @param property the event name
     * @param value the new value (model, or arbitrary object)
     */
    public void fire(String property, Object value) {
        var evt = new PropertyChangeEvent(this, property, null, value);
        for (var l : List.copyOf(listeners)) {
            l.propertyChange(evt);
        }
    }

    @Override
    public List<IArchimateModel> getModels() {
        return List.copyOf(models);
    }

    @Override
    public boolean isModelDirty(IArchimateModel model) {
        return dirty.contains(model);
    }

    @Override
    public void openModel(IArchimateModel model) {
        if (openError != null) {
            throw openError;
        }
        if (!models.contains(model)) {
            models.add(model);
        }
    }

    @Override
    public boolean saveModel(IArchimateModel model) throws java.io.IOException {
        if (saveError != null) {
            throw saveError;
        }
        // Real I/O so tests can observe what actually got persisted. Models
        // with no backing file (e.g. listener/dirty-only scenarios) are
        // treated as no-op saves.
        var file = model.getFile();
        if (file != null) {
            Files.write(file.toPath(), serializer.serialize(model));
        }
        dirty.remove(model);
        return true;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        listeners.remove(listener);
    }

}

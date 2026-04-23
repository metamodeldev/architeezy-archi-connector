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

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;

/**
 * Utility methods for resolving {@link IArchimateModel} instances from
 * heterogeneous candidate collections.
 */
public final class Models {

    private Models() {
    }

    /**
     * Returns the first candidate that resolves to a model <em>not</em> tracked
     * by Architeezy (see {@link ConnectorProperties#isTracked(IArchimateModel)}).
     *
     * <p>
     * Candidates are checked in iteration order. A candidate contributes a
     * model if it is itself an {@link IArchimateModel} or an
     * {@link IArchimateModelObject} whose owning model is non-null. All other
     * candidates are ignored.
     *
     * @param candidates the candidate elements; {@code null} is treated as empty
     * @return the first untracked model found, or {@code null} if none
     */
    public static IArchimateModel firstUntrackedModel(Iterable<?> candidates) {
        if (candidates == null) {
            return null;
        }
        for (var element : candidates) {
            IArchimateModel model = null;
            if (element instanceof IArchimateModel m) {
                model = m;
            } else if (element instanceof IArchimateModelObject obj) {
                model = obj.getArchimateModel();
            }
            if (model != null && !ConnectorProperties.isTracked(model)) {
                return model;
            }
        }
        return null;
    }
}

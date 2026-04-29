/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.ui.wizards;

import java.util.List;
import java.util.Map;

import com.architeezy.archi.connector.api.dto.RemoteModel;

/**
 * Grouping nodes used to back the import wizard's
 * {@code scope → project → model} tree.
 */
final class ModelTreeNodes {

    private ModelTreeNodes() {
    }

    /**
     * Scope-level grouping node.
     *
     * @param label display text for the scope row
     * @param projects projects belonging to this scope, keyed by an opaque
     *        identifier so equal-named projects from different versions stay
     *        distinct
     */
    record ScopeNode(String label, Map<String, ProjectNode> projects) {
    }

    /**
     * Project-level grouping node.
     *
     * @param label display text for the project row, including version when known
     * @param models leaf models that live in this project
     */
    record ProjectNode(String label, List<RemoteModel> models) {
    }

}

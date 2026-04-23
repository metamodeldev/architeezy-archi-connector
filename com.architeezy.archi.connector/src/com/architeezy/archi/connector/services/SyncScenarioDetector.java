/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.services;

import java.io.IOException;
import java.util.Arrays;

import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.io.ModelSerializer;
import com.architeezy.archi.connector.io.SnapshotStore;
import com.architeezy.archi.connector.model.SyncScenario;

/**
 * Classifies the pull/push scenario for a tracked model by comparing Local,
 * Base (snapshot), and Remote content. Extracted from {@link ModelSyncService}
 * so the pure-logic classification can be unit-tested without constructing the
 * full sync service.
 */
public final class SyncScenarioDetector {

    private final ModelSerializer serializer;

    private final SnapshotStore snapshotStore;

    /**
     * Creates a detector backed by the given serializer and snapshot store.
     *
     * @param serializer model serializer
     * @param snapshotStore base-snapshot store
     */
    public SyncScenarioDetector(ModelSerializer serializer, SnapshotStore snapshotStore) {
        this.serializer = serializer;
        this.snapshotStore = snapshotStore;
    }

    /**
     * Classifies the scenario for the given model.
     *
     * @param model the locally open model
     * @param modelId the repository model identifier
     * @param remoteContent raw XMI bytes from the server
     * @return the applicable {@link SyncScenario}
     * @throws IOException if the local model cannot be serialized
     */
    public SyncScenario detect(IArchimateModel model, String modelId, byte[] remoteContent)
            throws IOException {
        if (!snapshotStore.hasSnapshot(modelId)) {
            return SyncScenario.SIMPLE_PULL;
        }
        var base = snapshotStore.loadSnapshot(modelId);
        var local = serializer.serialize(model);
        return classify(Arrays.equals(local, base), Arrays.equals(remoteContent, base));
    }

    /**
     * Pure mapping from comparison flags to scenario.
     *
     * @param localEqualsBase whether the serialized local model equals the base snapshot
     * @param remoteEqualsBase whether the remote content equals the base snapshot
     * @return the applicable {@link SyncScenario}
     */
    public static SyncScenario classify(boolean localEqualsBase, boolean remoteEqualsBase) {
        if (localEqualsBase && remoteEqualsBase) {
            return SyncScenario.UP_TO_DATE;
        }
        if (localEqualsBase) {
            return SyncScenario.SIMPLE_PULL;
        }
        if (remoteEqualsBase) {
            return SyncScenario.SIMPLE_PUSH;
        }
        return SyncScenario.DIVERGED;
    }

}

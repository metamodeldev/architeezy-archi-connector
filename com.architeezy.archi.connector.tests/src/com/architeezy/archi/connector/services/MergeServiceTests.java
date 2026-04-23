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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.merge.IMerger;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.INameable;
import com.architeezy.archi.connector.io.ModelSerializer;

class MergeServiceTests {

    private ModelSerializer serializer;

    private MergeService merge;

    private RecordingConflictResolver resolver;

    @BeforeEach
    void setUp() {
        serializer = new ModelSerializer();
        resolver = new RecordingConflictResolver();
        merge = new MergeService(serializer, resolver);
    }

    @Test
    void appliesRemoteChangeWhenLocalIsUnchanged() throws Exception {
        var base = createBaseModel();
        var baseBytes = serializer.serialize(base);

        var remote = (IArchimateModel) EcoreUtil.copy(base);
        remote.setName("Renamed on server");
        var remoteBytes = serializer.serialize(remote);

        var localLive = (IArchimateModel) EcoreUtil.copy(base);

        var merged = merge.computeMergedContent(localLive, baseBytes, remoteBytes);

        assertNotNull(merged);
        var roundTripped = serializer.deserializeInMemory(merged);
        assertEquals("Renamed on server", roundTripped.getName());
    }

    @Test
    void appliesDisjointLocalAndRemoteChanges() throws Exception {
        var base = createBaseModel();
        final var baseBytes = serializer.serialize(base);

        // Remote adds a Business Actor to the existing Business folder.
        var remote = (IArchimateModel) EcoreUtil.copy(base);
        var remoteActor = IArchimateFactory.eINSTANCE.createBusinessActor();
        remoteActor.setName("RemoteActor");
        folderByName(remote, "Business").getElements().add(remoteActor);
        final var remoteBytes = serializer.serialize(remote);

        // Live/local adds a different element in a different folder.
        var localLive = (IArchimateModel) EcoreUtil.copy(base);
        var localApp = IArchimateFactory.eINSTANCE.createApplicationComponent();
        localApp.setName("LocalComponent");
        folderByName(localLive, "Application").getElements().add(localApp);

        var merged = merge.computeMergedContent(localLive, baseBytes, remoteBytes);

        assertNotNull(merged, "disjoint edits produce no real conflicts");
        var result = serializer.deserializeInMemory(merged);

        assertTrue(containsElementNamed(result, "RemoteActor"),
                "remote element must be merged in");
        assertTrue(containsElementNamed(result, "LocalComponent"),
                "local element must be preserved");
    }

    @Test
    void happyPathDoesNotInvokeConflictResolver() throws Exception {
        var base = createBaseModel();
        var baseBytes = serializer.serialize(base);
        var remote = (IArchimateModel) EcoreUtil.copy(base);
        remote.setName("RemoteOnly");
        var remoteBytes = serializer.serialize(remote);

        merge.computeMergedContent((IArchimateModel) EcoreUtil.copy(base), baseBytes, remoteBytes);

        assertFalse(resolver.wasInvoked, "no real conflicts => resolver should not be called");
    }

    @Test
    void realConflictDelegatesToResolver() throws Exception {
        var base = createBaseModel();
        var remote = (IArchimateModel) EcoreUtil.copy(base);
        var localLive = (IArchimateModel) EcoreUtil.copy(base);
        remote.setName("ServerRename");
        localLive.setName("LocalRename");
        resolver.cannedResponse = new byte[] {0x42};

        var merged = merge.computeMergedContent(localLive,
                serializer.serialize(base), serializer.serialize(remote));

        assertTrue(resolver.wasInvoked);
        org.junit.jupiter.api.Assertions.assertArrayEquals(new byte[] {0x42}, merged);
        assertTrue(resolver.lastComparison.getConflicts().stream()
                .anyMatch(c -> c.getKind() == org.eclipse.emf.compare.ConflictKind.REAL));
    }

    @Test
    void cancelledResolverYieldsNullResult() throws Exception {
        var base = createBaseModel();
        var remote = (IArchimateModel) EcoreUtil.copy(base);
        var localLive = (IArchimateModel) EcoreUtil.copy(base);
        remote.setName("ServerRename");
        localLive.setName("LocalRename");
        resolver.cannedResponse = null;

        assertNull(merge.computeMergedContent(localLive,
                serializer.serialize(base), serializer.serialize(remote)));
        assertTrue(resolver.wasInvoked);
    }

    @Test
    void doesNotMutateLiveModelObject() throws Exception {
        var base = createBaseModel();
        var baseBytes = serializer.serialize(base);

        var remote = (IArchimateModel) EcoreUtil.copy(base);
        remote.setName("Server-Renamed");
        var remoteBytes = serializer.serialize(remote);

        var localLive = (IArchimateModel) EcoreUtil.copy(base);
        var originalLocalName = localLive.getName();

        merge.computeMergedContent(localLive, baseBytes, remoteBytes);

        // Merge works on a deep copy; the live object must stay untouched so
        // the UI thread can decide whether to apply the merged bytes.
        assertEquals(originalLocalName, localLive.getName());
        assertFalse(containsElementNamed(localLive, "Server-Renamed"));
    }

    // -----------------------------------------------------------------

    private static IArchimateModel createBaseModel() {
        var m = IArchimateFactory.eINSTANCE.createArchimateModel();
        m.setName("Base");
        m.setDefaults();
        return m;
    }

    private static com.archimatetool.model.IFolder folderByName(IArchimateModel model, String name) {
        for (var f : model.getFolders()) {
            if (name.equals(f.getName())) {
                return f;
            }
        }
        throw new AssertionError("folder not found: " + name);
    }

    private static boolean containsElementNamed(IArchimateModel model, String name) {
        for (var f : model.getFolders()) {
            for (var e : f.getElements()) {
                if (e instanceof INameable n && name.equals(n.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class RecordingConflictResolver implements IConflictResolver {

        boolean wasInvoked;

        byte[] cannedResponse;

        Comparison lastComparison;

        @Override
        public byte[] resolve(Comparison comparison, Resource localResource, IMerger.Registry registry) {
            wasInvoked = true;
            lastComparison = comparison;
            return cannedResponse;
        }

    }

}

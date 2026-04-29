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

import org.eclipse.emf.compare.AttributeChange;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.ConflictKind;
import org.eclipse.emf.compare.DifferenceKind;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.compare.merge.IMerger;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelGroup;
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

    @Test
    void boundsMovedOnBothSidesIsOneConflictNotDeleteAndAdd() throws Exception {
        var base = createBaseModelWithGroup();
        var remote = (IArchimateModel) EcoreUtil.copy(base);
        var remoteBounds = findGroup(remote).getBounds();
        remoteBounds.setX(2000);
        remoteBounds.setY(2000);
        remoteBounds.setWidth(50);
        remoteBounds.setHeight(20);
        var localLive = (IArchimateModel) EcoreUtil.copy(base);
        var localBounds = findGroup(localLive).getBounds();
        localBounds.setX(-2000);
        localBounds.setY(-2000);
        localBounds.setWidth(500);
        localBounds.setHeight(400);

        resolver.cannedResponse = new byte[] {0x01};
        merge.computeMergedContent(localLive, serializer.serialize(base), serializer.serialize(remote));

        assertTrue(resolver.wasInvoked, "diverging bounds edits must surface a real conflict");

        var comparison = resolver.lastComparison;

        // No spurious containment ADD/DELETE diffs on the bounds reference.
        var spuriousBoundsDiffs = comparison.getDifferences().stream()
                .filter(d -> d instanceof ReferenceChange rc
                        && "bounds".equals(rc.getReference().getName())
                        && (rc.getKind() == DifferenceKind.ADD || rc.getKind() == DifferenceKind.DELETE))
                .toList();
        assertTrue(spuriousBoundsDiffs.isEmpty(),
                () -> "bounds change must not be reported as add/delete; got: " + spuriousBoundsDiffs);

        // All real conflicts must be attribute changes on the same Bounds match
        // (i.e. a single matched bounds object with diverging x/y/width/height),
        // not delete/add reference changes on the parent's "bounds" feature.
        var realConflictDiffs = comparison.getConflicts().stream()
                .filter(c -> c.getKind() == ConflictKind.REAL)
                .flatMap(c -> c.getDifferences().stream())
                .toList();
        assertFalse(realConflictDiffs.isEmpty(), "diverging bounds must produce real conflicts");
        assertTrue(realConflictDiffs.stream().allMatch(AttributeChange.class::isInstance),
                () -> "real conflicts must be attribute changes on the bounds, got: " + realConflictDiffs);
        var conflictMatches = realConflictDiffs.stream().map(d -> d.getMatch()).distinct().toList();
        assertEquals(1, conflictMatches.size(),
                () -> "all conflicting diffs must belong to a single bounds match, got: " + conflictMatches);
        assertEquals("Bounds", conflictMatches.get(0).getOrigin().eClass().getName());
    }

    @Test
    void bendpointMovedOnBothSidesIsOneConflictNotDeleteAndAdd() throws Exception {
        var base = createBaseModelWithConnection();
        var remote = (IArchimateModel) EcoreUtil.copy(base);
        var remoteBendpoint = findConnection(remote).getBendpoints().get(1);
        remoteBendpoint.setStartX(2000);
        remoteBendpoint.setStartY(2000);
        remoteBendpoint.setEndX(2100);
        remoteBendpoint.setEndY(2100);
        var localLive = (IArchimateModel) EcoreUtil.copy(base);
        var localBendpoint = findConnection(localLive).getBendpoints().get(1);
        localBendpoint.setStartX(-2000);
        localBendpoint.setStartY(-2000);
        localBendpoint.setEndX(-2100);
        localBendpoint.setEndY(-2100);

        resolver.cannedResponse = new byte[] {0x01};
        merge.computeMergedContent(localLive, serializer.serialize(base), serializer.serialize(remote));

        assertTrue(resolver.wasInvoked, "diverging bendpoint edits must surface a real conflict");

        var comparison = resolver.lastComparison;

        var spuriousBendpointDiffs = comparison.getDifferences().stream()
                .filter(d -> d instanceof ReferenceChange rc
                        && "bendpoints".equals(rc.getReference().getName())
                        && (rc.getKind() == DifferenceKind.ADD || rc.getKind() == DifferenceKind.DELETE))
                .toList();
        assertTrue(spuriousBendpointDiffs.isEmpty(),
                () -> "bendpoint move must not be reported as add/delete; got: " + spuriousBendpointDiffs);

        var realConflictDiffs = comparison.getConflicts().stream()
                .filter(c -> c.getKind() == ConflictKind.REAL)
                .flatMap(c -> c.getDifferences().stream())
                .toList();
        assertFalse(realConflictDiffs.isEmpty(), "diverging bendpoint must produce real conflicts");
        assertTrue(realConflictDiffs.stream().allMatch(AttributeChange.class::isInstance),
                () -> "real conflicts must be attribute changes on the bendpoint, got: " + realConflictDiffs);
        var conflictMatches = realConflictDiffs.stream().map(d -> d.getMatch()).distinct().toList();
        assertEquals(1, conflictMatches.size(),
                () -> "all conflicting diffs must belong to a single bendpoint match, got: " + conflictMatches);
        assertEquals("DiagramModelBendpoint", conflictMatches.get(0).getOrigin().eClass().getName());
    }

    // -----------------------------------------------------------------

    private static IArchimateModel createBaseModelWithConnection() {
        var m = createBaseModel();
        var view = IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
        view.setName("View");
        folderByName(m, "Views").getElements().add(view);
        var groupA = IArchimateFactory.eINSTANCE.createDiagramModelGroup();
        groupA.setName("A");
        groupA.setBounds(IArchimateFactory.eINSTANCE.createBounds(0, 0, 100, 50));
        view.getChildren().add(groupA);
        var groupB = IArchimateFactory.eINSTANCE.createDiagramModelGroup();
        groupB.setName("B");
        groupB.setBounds(IArchimateFactory.eINSTANCE.createBounds(300, 0, 100, 50));
        view.getChildren().add(groupB);
        var connection = IArchimateFactory.eINSTANCE.createDiagramModelConnection();
        connection.connect(groupA, groupB);
        // Three bendpoints so the test targets the middle one (index 1)
        // — proves the index, not just the slot, drives the synthetic ID.
        connection.getBendpoints().add(makeBendpoint(50, 0));
        connection.getBendpoints().add(makeBendpoint(150, 25));
        connection.getBendpoints().add(makeBendpoint(250, 50));
        return m;
    }

    private static com.archimatetool.model.IDiagramModelBendpoint makeBendpoint(int x, int y) {
        var bp = IArchimateFactory.eINSTANCE.createDiagramModelBendpoint();
        bp.setStartX(x);
        bp.setStartY(y);
        bp.setEndX(x);
        bp.setEndY(y);
        return bp;
    }

    private static IDiagramModelConnection findConnection(IArchimateModel model) {
        return model.getFolders().stream()
                .flatMap(f -> f.getElements().stream())
                .filter(IArchimateDiagramModel.class::isInstance)
                .map(IArchimateDiagramModel.class::cast)
                .flatMap(dm -> dm.getChildren().stream())
                .flatMap(c -> c.getSourceConnections().stream())
                .filter(IDiagramModelConnection.class::isInstance)
                .map(IDiagramModelConnection.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("connection not found"));
    }

    // -----------------------------------------------------------------

    private static IArchimateModel createBaseModelWithGroup() {
        var m = createBaseModel();
        var view = IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
        view.setName("View");
        folderByName(m, "Views").getElements().add(view);
        var group = IArchimateFactory.eINSTANCE.createDiagramModelGroup();
        group.setName("Group");
        group.setBounds(IArchimateFactory.eINSTANCE.createBounds(10, 10, 100, 50));
        view.getChildren().add(group);
        return m;
    }

    private static IDiagramModelGroup findGroup(IArchimateModel model) {
        return model.getFolders().stream()
                .flatMap(f -> f.getElements().stream())
                .filter(IArchimateDiagramModel.class::isInstance)
                .map(IArchimateDiagramModel.class::cast)
                .flatMap(dm -> dm.getChildren().stream())
                .filter(IDiagramModelGroup.class::isInstance)
                .map(IDiagramModelGroup.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("group not found"));
    }

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

/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.model.diff;

import java.util.function.Function;

import org.eclipse.emf.compare.match.eobject.IdentifierEObjectMatcher;
import org.eclipse.emf.ecore.EObject;

/**
 * Identifier function for EMF Compare that supplements the default
 * {@link org.eclipse.emf.ecore.util.EcoreUtil#getID(EObject)} lookup with
 * synthetic identifiers derived from an object's structural position inside
 * its identifiable parent.
 *
 * <p>
 * Several Archi model elements do not implement {@code IIdentifier} and have
 * no XMI ID, yet their identity in a 3-way merge is unambiguous because of
 * where they sit in the containment tree:
 * <ul>
 * <li>{@code IBounds} on every {@code IDiagramModelObject} - a single-valued
 * slot, so the slot itself identifies the element.</li>
 * <li>{@code IDiagramModelBendpoint} on every diagram connection - an ordered
 * list, so the index inside that list identifies the element.</li>
 * </ul>
 *
 * <p>
 * Without a synthetic ID, EMF Compare's default matcher falls back to
 * proximity matching on attribute similarity. When both sides of a 3-way
 * merge edit the same logical element heavily, that fallback can match each
 * side to a different candidate, producing a spurious pair of "delete X" +
 * "add X" matches instead of recognising the change as a modification of one
 * and the same object.
 *
 * <p>
 * The synthetic ID returned here mirrors the EMF URI fragment format:
 * <ul>
 * <li>single-valued containment: {@code parentId + "/@" + refName}</li>
 * <li>ordered multi-valued containment: {@code parentId + "/@" + refName + "." + index}</li>
 * </ul>
 * Unordered multi-valued containment is left to the proximity fallback,
 * since indexing is meaningless there.
 *
 * <p>
 * The check is purely metamodel-driven: any future ID-less element placed in
 * a single-valued or ordered multi-valued containment slot of an identifiable
 * parent benefits without code changes.
 */
public final class StructuralIdFunction implements Function<EObject, String> {

    private static final Function<EObject, String> DEFAULT = new IdentifierEObjectMatcher.DefaultIDFunction();

    @Override
    public String apply(EObject input) {
        if (input == null) {
            return null;
        }
        var defaultId = DEFAULT.apply(input);
        if (defaultId != null) {
            return defaultId;
        }
        var ref = input.eContainmentFeature();
        var container = input.eContainer();
        if (ref == null || container == null) {
            return null;
        }
        var parentId = apply(container);
        if (parentId == null) {
            return null;
        }
        var suffix = containmentSuffix(container, ref, input);
        return suffix == null ? null : parentId + suffix;
    }

    private String containmentSuffix(EObject container, org.eclipse.emf.ecore.EReference ref, EObject input) {
        if (!ref.isMany()) {
            return "/@" + ref.getName();
        }
        if (!ref.isOrdered()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        var list = (java.util.List<EObject>) container.eGet(ref);
        var index = list.indexOf(input);
        return index < 0 ? null : "/@" + ref.getName() + "." + index;
    }

}

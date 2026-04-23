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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.emf.compare.CompareFactory;
import org.eclipse.emf.compare.DifferenceKind;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.compare.ResourceAttachmentChange;
import org.junit.jupiter.api.Test;

import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimatePackage;

class DiffFormatterTests {

    // extractLabel ---------------------------------------------------------

    @Test
    void extractLabelReturnsQuestionMarkForNull() {
        assertEquals("?", DiffFormatter.extractLabel(null));
    }

    @Test
    void extractLabelForPropertyWithKey() {
        var prop = IArchimateFactory.eINSTANCE.createProperty();
        prop.setKey("author");
        assertEquals("Property author", DiffFormatter.extractLabel(prop));
    }

    @Test
    void extractLabelForPropertyWithBlankKey() {
        var prop = IArchimateFactory.eINSTANCE.createProperty();
        prop.setKey("   ");
        assertEquals("Property", DiffFormatter.extractLabel(prop));
    }

    @Test
    void extractLabelForPropertyWithoutKey() {
        var prop = IArchimateFactory.eINSTANCE.createProperty();
        assertEquals("Property", DiffFormatter.extractLabel(prop));
    }

    // formatDiffs ----------------------------------------------------------

    @Test
    void formatDiffsEmptyListReturnsEmptyString() {
        assertEquals("", DiffFormatter.formatDiffs(List.of()));
    }

    @Test
    void formatDiffsSkipsContainmentAddDelete() {
        var rcAdd = CompareFactory.eINSTANCE.createReferenceChange();
        rcAdd.setReference(IArchimatePackage.Literals.FOLDER_CONTAINER__FOLDERS);
        rcAdd.setKind(DifferenceKind.ADD);

        var rcDel = CompareFactory.eINSTANCE.createReferenceChange();
        rcDel.setReference(IArchimatePackage.Literals.FOLDER_CONTAINER__FOLDERS);
        rcDel.setKind(DifferenceKind.DELETE);

        assertEquals("", DiffFormatter.formatDiffs(List.of(rcAdd, rcDel)));
    }

    @Test
    void formatDiffsSkipsResourceAttachmentAddDelete() {
        ResourceAttachmentChange rac = CompareFactory.eINSTANCE.createResourceAttachmentChange();
        rac.setKind(DifferenceKind.ADD);
        assertEquals("", DiffFormatter.formatDiffs(List.of(rac)));
    }

    @Test
    void formatDiffsKeepsNonContainmentReferenceChangeAdd() {
        // A non-containment reference of kind ADD must render as "+ <label>".
        // Use a null value so extractLabel returns "?" (no ArchiLabelProvider needed).
        ReferenceChange rc = CompareFactory.eINSTANCE.createReferenceChange();
        rc.setReference(IArchimatePackage.Literals.DIAGRAM_MODEL_ARCHIMATE_OBJECT__ARCHIMATE_ELEMENT);
        rc.setKind(DifferenceKind.ADD);
        assertEquals("+ ?", DiffFormatter.formatDiffs(List.of(rc)));
    }

    @Test
    void formatDiffsKeepsNonContainmentReferenceChangeDelete() {
        ReferenceChange rc = CompareFactory.eINSTANCE.createReferenceChange();
        rc.setReference(IArchimatePackage.Literals.DIAGRAM_MODEL_ARCHIMATE_OBJECT__ARCHIMATE_ELEMENT);
        rc.setKind(DifferenceKind.DELETE);
        assertEquals("- ?", DiffFormatter.formatDiffs(List.of(rc)));
    }

    @Test
    void formatDiffsKeepsNonContainmentReferenceChangeMove() {
        ReferenceChange rc = CompareFactory.eINSTANCE.createReferenceChange();
        rc.setReference(IArchimatePackage.Literals.DIAGRAM_MODEL_ARCHIMATE_OBJECT__ARCHIMATE_ELEMENT);
        rc.setKind(DifferenceKind.MOVE);
        assertTrue(DiffFormatter.formatDiffs(List.of(rc)).endsWith("?"));
    }

    @Test
    void formatAttributeChangeRendersNewValueWhenNoOrigin() {
        var ac = CompareFactory.eINSTANCE.createAttributeChange();
        ac.setAttribute(IArchimatePackage.Literals.NAMEABLE__NAME);
        ac.setValue("NewName");
        var match = CompareFactory.eINSTANCE.createMatch();
        ac.setMatch(match);

        assertEquals("name: NewName", DiffFormatter.formatDiffs(List.of(ac)));
    }

    @Test
    void formatAttributeChangeRendersTransitionWhenOriginDiffers() {
        var ac = CompareFactory.eINSTANCE.createAttributeChange();
        ac.setAttribute(IArchimatePackage.Literals.NAMEABLE__NAME);
        ac.setValue("NewName");
        var origin = IArchimateFactory.eINSTANCE.createArchimateModel();
        origin.setName("OldName");
        var match = CompareFactory.eINSTANCE.createMatch();
        match.setOrigin(origin);
        ac.setMatch(match);

        assertEquals("name: OldName \u2192 NewName", DiffFormatter.formatDiffs(List.of(ac)));
    }

    @Test
    void formatAttributeChangeTreatsNullValueAsLiteralNull() {
        var ac = CompareFactory.eINSTANCE.createAttributeChange();
        ac.setAttribute(IArchimatePackage.Literals.NAMEABLE__NAME);
        // value left unset (null)
        var match = CompareFactory.eINSTANCE.createMatch();
        ac.setMatch(match);

        assertEquals("name: (null)", DiffFormatter.formatDiffs(List.of(ac)));
    }

}

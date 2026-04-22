/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.dialog;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.emf.compare.AttributeChange;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceKind;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.compare.ResourceAttachmentChange;
import org.eclipse.emf.ecore.EObject;

import com.archimatetool.editor.ui.ArchiLabelProvider;
import com.archimatetool.model.IProperty;

/**
 * Utility methods for formatting EMF Compare differences as human-readable strings.
 */
final class DiffFormatter {

    private static final String COLON = ": ";

    private DiffFormatter() {
    }

    static String formatDiffs(List<Diff> diffs) {
        return diffs.stream()
                .map(DiffFormatter::formatDiff)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
    }

    static String extractLabel(EObject element) {
        if (element == null) {
            return "?";
        }
        if (element instanceof IProperty property) {
            var key = property.getKey();
            return "Property" + (key != null && !key.isBlank() ? " " + key : "");
        }
        var label = ArchiLabelProvider.INSTANCE.getLabel(element);
        return label != null && !label.isBlank() ? label : element.eClass().getName();
    }

    private static String formatDiff(Diff diff) {
        // Containment ADD/DELETE and resource attachment ADD/DELETE are shown on the child node itself
        if (diff instanceof ReferenceChange rc
                && rc.getReference().isContainment()
                && (rc.getKind() == DifferenceKind.ADD || rc.getKind() == DifferenceKind.DELETE)) {
            return "";
        }
        if (diff instanceof ResourceAttachmentChange
                && (diff.getKind() == DifferenceKind.ADD || diff.getKind() == DifferenceKind.DELETE)) {
            return "";
        }
        if (diff instanceof AttributeChange ac) {
            return formatAttributeChange(ac);
        }
        if (diff instanceof ReferenceChange rc) {
            return formatReferenceChange(rc);
        }
        return diff.getKind().getName();
    }

    private static String formatAttributeChange(AttributeChange ac) {
        var attrName = ac.getAttribute().getName();
        var newValue = ac.getValue();
        var newStr = newValue != null ? newValue.toString() : "(null)";
        var origin = ac.getMatch().getOrigin();
        if (origin != null) {
            var oldValue = origin.eGet(ac.getAttribute());
            if (oldValue != null && !oldValue.toString().equals(newStr)) {
                return attrName + COLON + oldValue + " \u2192 " + newStr;
            }
        }
        return attrName + COLON + newStr;
    }

    private static String formatReferenceChange(ReferenceChange rc) {
        var name = extractLabel(rc.getValue());
        return switch (rc.getKind()) {
        case ADD -> "+ " + name;
        case DELETE -> "- " + name;
        case MOVE -> "\u21c6 " + name;
        default -> rc.getReference().getName() + COLON + name;
        };
    }

}

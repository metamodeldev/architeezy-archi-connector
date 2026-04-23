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

import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.merge.IMerger;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;

import com.architeezy.archi.connector.io.ModelSerializer;
import com.architeezy.archi.connector.ui.dialogs.ConflictResolutionDialog;

/**
 * Production {@link IConflictResolver} that shows
 * {@link ConflictResolutionDialog} on the UI thread.
 */
public final class DialogConflictResolver implements IConflictResolver {

    private final ModelSerializer serializer;

    /**
     * Creates a resolver that serializes the merged model with the given serializer.
     *
     * @param serializer model serializer used to produce the merged-content bytes
     */
    public DialogConflictResolver(ModelSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public byte[] resolve(Comparison comparison, Resource localResource, IMerger.Registry registry)
            throws Exception {
        byte[][] mergedBytes = { null };
        Exception[] runError = { null };

        Display.getDefault().syncExec(
                () -> runDialog(comparison, localResource, registry, mergedBytes, runError));

        if (runError[0] != null) {
            throw runError[0];
        }
        return mergedBytes[0];
    }

    private void runDialog(Comparison comparison, Resource localResource, IMerger.Registry registry,
            byte[][] mergedBytes, Exception[] runError) {
        var dialog = new ConflictResolutionDialog(
                Display.getDefault().getActiveShell(), comparison, localResource, registry, serializer);
        if (dialog.open() != Window.OK) {
            return;
        }
        if (dialog.getMergeError() != null) {
            runError[0] = dialog.getMergeError();
        } else {
            mergedBytes[0] = dialog.getMergedContent();
        }
    }

}

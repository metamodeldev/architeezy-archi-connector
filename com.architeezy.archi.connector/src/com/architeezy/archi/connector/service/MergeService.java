/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.service;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.Messages;

/**
 * Handles the DIVERGED scenario where both the local model and the remote model
 * have changed since their last known common base snapshot.
 *
 * <p>
 * Asks the user whether to overwrite local changes with the remote version.
 * Proper 3-way EMF DiffMerge UI integration is planned for a future release
 * once {@code org.eclipse.emf.diffmerge.ui} and its transitive dependencies
 * (including {@code org.eclipse.emf.edit.ui} and {@code org.eclipse.compare})
 * are available in the Archi runtime.
 *
 * <p>
 * Must be called from a background thread; this method switches to the UI
 * thread internally to show the dialog.
 *
 * @return {@code true} if the user chose to apply the remote version
 */
public final class MergeService {

    /** The singleton instance of MergeService. */
    public static final MergeService INSTANCE = new MergeService();

    private MergeService() {
    }

    /**
     * Asks the user how to resolve a diverged state where both local and remote
     * models have changed since the last known base.
     *
     * @param model the local model with conflicting changes
     * @return {@code true} if the user chose to apply the remote version,
     *         {@code false} if the user chose to keep the local version
     */
    public boolean askUserToApplyRemote(IArchimateModel model) {
        var result = new boolean[1];
        Display.getDefault().syncExec(() -> {
            var shell = Display.getDefault().getActiveShell();
            result[0] = MessageDialog.openQuestion(shell,
                Messages.MergeService_conflictTitle,
                Messages.MergeService_conflictMessage);
        });
        return result[0];
    }

}

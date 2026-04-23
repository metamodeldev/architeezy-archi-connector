/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;

import com.architeezy.archi.connector.navigator.ModelTreeDecorator;

/**
 * Called by Eclipse after the workbench is initialized. Installs the model
 * tree decorator and eagerly instantiates the pull/push command handlers so
 * they can register their listeners on {@code UpdateCheckService} and
 * {@code LocalChangeService} before those services start firing changes.
 */
public class EarlyStartup implements IStartup {

    private static final String[] EAGER_COMMAND_IDS = {
            "com.architeezy.archi.connector.command.pullModel", //$NON-NLS-1$
            "com.architeezy.archi.connector.command.pushModel", //$NON-NLS-1$
    };

    @Override
    public void earlyStartup() {
        Display.getDefault().asyncExec(() -> {
            ModelTreeDecorator.INSTANCE.install();
            instantiateHandlers();
        });
    }

    private static void instantiateHandlers() {
        if (!PlatformUI.isWorkbenchRunning()) {
            return;
        }
        var service = PlatformUI.getWorkbench().getService(ICommandService.class);
        if (service == null) {
            return;
        }
        for (var id : EAGER_COMMAND_IDS) {
            try {
                // Touching isEnabled forces the framework to resolve the
                // declarative <handler> class and instantiate it, so its
                // constructor can attach service listeners before any state
                // changes occur.
                service.getCommand(id).isEnabled();
            } catch (Exception e) {
                ConnectorPlugin.getInstance().getLog().warn("Failed to eagerly instantiate handler for " + id, e); //$NON-NLS-1$
            }
        }
    }

}

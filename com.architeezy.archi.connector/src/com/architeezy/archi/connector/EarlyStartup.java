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

import com.architeezy.archi.connector.navigator.ModelTreeDecorator;

/**
 * Called by Eclipse after the workbench is fully initialized on a background
 * thread. Dispatches the decorator installation to the UI thread.
 */
public class EarlyStartup implements IStartup {

    @Override
    public void earlyStartup() {
        Display.getDefault().asyncExec(ModelTreeDecorator.INSTANCE::install);
    }

}

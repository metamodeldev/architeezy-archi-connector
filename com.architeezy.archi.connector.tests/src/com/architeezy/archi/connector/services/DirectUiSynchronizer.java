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

import java.util.concurrent.Callable;

/**
 * Test {@link UiSynchronizer} that runs the task on the calling thread.
 */
final class DirectUiSynchronizer implements UiSynchronizer {

    static final DirectUiSynchronizer INSTANCE = new DirectUiSynchronizer();

    private DirectUiSynchronizer() {
    }

    @Override
    public <T> T syncCall(Callable<T> task) throws Exception {
        return task.call();
    }
}

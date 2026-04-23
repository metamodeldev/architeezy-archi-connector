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
 * Runs a task on the UI thread synchronously and returns its result, propagating
 * any exception it throws back to the calling (background) thread.
 *
 * Production wiring uses {@code Display#syncCall(Callable)}; tests use a direct
 * caller that runs the task on the current thread.
 */
@FunctionalInterface
public interface UiSynchronizer {

    /**
     * Executes the given task on the UI thread and returns its result.
     *
     * @param <T> result type
     * @param task the task to execute
     * @return the task's result
     * @throws Exception anything the task threw
     */
    @SuppressWarnings("java:S112")
    <T> T syncCall(Callable<T> task) throws Exception;
}

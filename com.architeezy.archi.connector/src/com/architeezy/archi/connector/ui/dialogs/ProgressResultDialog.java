/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.ui.dialogs;

import java.text.MessageFormat;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;

/**
 * Modal dialog that first shows a progress indicator with a Cancel button
 * and, once the work finishes, morphs in place into a result message (success,
 * error or cancelled) with an OK button. Using a single shell avoids the
 * flicker of closing a progress dialog and opening a separate message dialog.
 */
@SuppressWarnings("checkstyle:MagicNumber")
public class ProgressResultDialog extends Dialog {

    private final String title;

    private final String initialTask;

    private final String errorFormat;

    private final Task task;

    private final DialogMonitor monitor = new DialogMonitor();

    private StackLayout stackLayout;

    private Composite stack;

    private Composite resultPanel;

    private Label subTaskLabel;

    private Label resultIcon;

    private Label resultMessage;

    private Button cancelButton;

    private Button okButton;

    private volatile boolean finished;

    private ResultKind resultKind;

    private Throwable error;

    private boolean silent;

    /**
     * Creates the dialog.
     *
     * @param parent parent shell
     * @param title shell title
     * @param initialTask initial progress label shown before the worker reports any
     *        sub-task
     * @param errorFormat {@link MessageFormat} pattern with one parameter for the
     *        error message
     * @param task background worker
     */
    public ProgressResultDialog(Shell parent, String title, String initialTask,
            String errorFormat, Task task) {
        super(parent);
        this.title = title;
        this.initialTask = initialTask;
        this.errorFormat = errorFormat;
        this.task = task;
    }

    /**
     * Returns the kind of result ultimately shown.
     *
     * @return the result kind, or {@code null} if the dialog was not opened
     */
    public ResultKind getResultKind() {
        return resultKind;
    }

    /**
     * Returns the throwable caught from the worker.
     *
     * @return the worker exception, or {@code null} if the worker succeeded
     */
    public Throwable getError() {
        return error;
    }

    /**
     * Indicates whether the worker returned {@link Outcome#silent()}.
     *
     * @return {@code true} if the dialog closed without showing a result page
     */
    public boolean wasSilent() {
        return silent;
    }

    @Override
    public int open() {
        create();
        startWorker();
        return super.open();
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText(title);
    }

    @Override
    protected Point getInitialSize() {
        return new Point(440, 200);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        var container = (Composite) super.createDialogArea(parent);
        stack = new Composite(container, SWT.NONE);
        var gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        gd.widthHint = 380;
        stack.setLayoutData(gd);
        stackLayout = new StackLayout();
        stack.setLayout(stackLayout);

        var progressPanel = new Composite(stack, SWT.NONE);
        progressPanel.setLayout(new GridLayout(1, false));
        subTaskLabel = new Label(progressPanel, SWT.WRAP);
        subTaskLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        subTaskLabel.setText(initialTask != null ? initialTask : ""); //$NON-NLS-1$
        var progressBar = new ProgressBar(progressPanel, SWT.INDETERMINATE | SWT.HORIZONTAL);
        progressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        resultPanel = new Composite(stack, SWT.NONE);
        resultPanel.setLayout(new GridLayout(2, false));
        resultIcon = new Label(resultPanel, SWT.NONE);
        resultIcon.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
        resultMessage = new Label(resultPanel, SWT.WRAP);
        resultMessage.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        stackLayout.topControl = progressPanel;
        return container;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        cancelButton = createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, true);
        okButton = createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, false);
        var gd = (GridData) okButton.getLayoutData();
        gd.exclude = true;
        okButton.setVisible(false);
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == IDialogConstants.CANCEL_ID) {
            if (finished) {
                setReturnCode(CANCEL);
                close();
                return;
            }
            monitor.setCanceled(true);
            cancelButton.setEnabled(false);
            return;
        }
        if (buttonId == IDialogConstants.OK_ID) {
            setReturnCode(OK);
            close();
            return;
        }
        super.buttonPressed(buttonId);
    }

    private void startWorker() {
        var thread = new Thread(this::runWorker, "ProgressResultDialog-Worker"); //$NON-NLS-1$
        thread.setDaemon(true);
        thread.start();
    }

    private void runWorker() {
        try {
            var outcome = task.run(monitor);
            Display.getDefault().asyncExec(() -> finish(outcome, null));
        } catch (Exception ex) {
            Display.getDefault().asyncExec(() -> finish(null, ex));
        }
    }

    private void finish(Outcome outcome, Throwable ex) {
        finished = true;
        var shell = getShell();
        if (shell == null || shell.isDisposed()) {
            return;
        }
        if (monitor.isCanceled()) {
            // User asked to cancel: close without nagging them about it.
            resultKind = ResultKind.CANCELLED;
            setReturnCode(CANCEL);
            close();
            return;
        }
        if (ex != null) {
            error = ex;
            Platform.getLog(ProgressResultDialog.class).error("Operation failed", ex); //$NON-NLS-1$
            var raw = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            showResult(ResultKind.ERROR, MessageFormat.format(errorFormat, raw));
            return;
        }
        if (outcome != null && outcome.isSilent()) {
            silent = true;
            resultKind = ResultKind.SUCCESS;
            setReturnCode(OK);
            close();
            return;
        }
        showResult(ResultKind.SUCCESS, outcome != null ? outcome.getMessage() : ""); //$NON-NLS-1$
    }

    private void showResult(ResultKind kind, String message) {
        resultKind = kind;
        var iconId = SWT.ICON_INFORMATION;
        if (kind == ResultKind.ERROR) {
            iconId = SWT.ICON_ERROR;
        } else if (kind == ResultKind.CANCELLED) {
            iconId = SWT.ICON_WARNING;
        }
        resultIcon.setImage(getShell().getDisplay().getSystemImage(iconId));
        resultMessage.setText(message != null ? message : ""); //$NON-NLS-1$
        stackLayout.topControl = resultPanel;
        stack.layout(true, true);

        ((GridData) cancelButton.getLayoutData()).exclude = true;
        cancelButton.setVisible(false);
        ((GridData) okButton.getLayoutData()).exclude = false;
        okButton.setVisible(true);
        okButton.getParent().layout();
        getShell().setDefaultButton(okButton);
    }

    /** Worker executed on a background thread. */
    @FunctionalInterface
    public interface Task {

        /**
         * Runs the background operation.
         *
         * @param monitor progress monitor; sub-task text is shown in the dialog
         * @return the outcome to display, or {@link Outcome#silent()} to close
         *         the dialog without showing a result page
         * @throws Exception on failure; displayed as an error result
         */
        @SuppressWarnings("java:S112")
        Outcome run(IProgressMonitor monitor) throws Exception;

    }

    /** What the dialog ended up displaying. */
    public enum ResultKind {
        /** Worker completed normally and a success message is shown. */
        SUCCESS,
        /** Worker threw an exception and an error message is shown. */
        ERROR,
        /** User cancelled the operation and a cancelled message is shown. */
        CANCELLED
    }

    /** Successful outcome returned by a {@link Task}. */
    public static final class Outcome {

        private final boolean silent;

        private final String message;

        private Outcome(boolean silent, String message) {
            this.silent = silent;
            this.message = message;
        }

        /**
         * Returns an outcome that shows {@code message} as the success result.
         *
         * @param message text to display in the success panel
         * @return a new success outcome
         */
        public static Outcome success(String message) {
            return new Outcome(false, message);
        }

        /**
         * Returns an outcome that closes the dialog without showing a result page.
         * Useful when another dialog is opened immediately afterwards.
         *
         * @return a silent outcome
         */
        public static Outcome silent() {
            return new Outcome(true, null);
        }

        boolean isSilent() {
            return silent;
        }

        String getMessage() {
            return message;
        }

    }

    private final class DialogMonitor implements IProgressMonitor {

        private volatile boolean canceled;

        @Override
        public void beginTask(String name, int totalWork) {
            updateLabel(name);
        }

        @Override
        public void done() {
            // finish is driven by the worker thread returning, not by the monitor
        }

        @Override
        public void internalWorked(double work) {
            // no-op: the dialog uses an indeterminate progress bar
        }

        @Override
        public boolean isCanceled() {
            return canceled;
        }

        @Override
        public void setCanceled(boolean value) {
            canceled = value;
        }

        @Override
        public void setTaskName(String name) {
            updateLabel(name);
        }

        @Override
        public void subTask(String name) {
            updateLabel(name);
        }

        @Override
        public void worked(int work) {
            // no-op: the dialog uses an indeterminate progress bar
        }

        private void updateLabel(String text) {
            if (text == null || text.isBlank()) {
                return;
            }
            Display.getDefault().asyncExec(() -> {
                if (subTaskLabel != null && !subTaskLabel.isDisposed()) {
                    subTaskLabel.setText(text);
                    subTaskLabel.getParent().layout();
                }
            });
        }

    }

}

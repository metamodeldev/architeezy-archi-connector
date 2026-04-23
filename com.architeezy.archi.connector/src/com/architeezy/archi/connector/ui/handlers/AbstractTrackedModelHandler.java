/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.ui.handlers;

import java.beans.PropertyChangeListener;
import java.text.MessageFormat;
import java.util.function.Predicate;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.HandlerEvent;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.auth.OAuthException;
import com.architeezy.archi.connector.auth.ProfileRegistry;
import com.architeezy.archi.connector.auth.ProfileStatus;
import com.architeezy.archi.connector.model.ConnectorProperties;
import com.architeezy.archi.connector.model.TrackedModels;
import com.architeezy.archi.connector.services.AuthService;

/**
 * Base class for toolbar handlers whose enablement depends on whether any
 * active/selected tracked Archimate model satisfies a condition. Subclasses
 * implement {@link #isEnabledForModel} and subscribe their own domain
 * services (e.g. {@code UpdateCheckService}) via {@link #refreshEnabled}.
 *
 * <p>
 * The handler listens for workbench selection, part, and window changes, and
 * for Archi model lifecycle events. Whenever something may affect enablement,
 * it fires {@link HandlerEvent} and also posts the e4
 * {@link UIEvents#REQUEST_ENABLEMENT_UPDATE_TOPIC} event - the latter is what
 * actually causes {@code HandledContributionItem} toolbar items to re-query
 * their handler's enabled state. Without posting that event the SWT tool
 * item stays in its cached state even when the underlying Command's
 * enablement has been updated.
 */
public abstract class AbstractTrackedModelHandler extends AbstractHandler {

    private final ISelectionListener selectionListener = (IWorkbenchPart part, ISelection sel) -> refreshEnabled();

    private final IPartListener2 partListener = new PartListener();

    private final PropertyChangeListener modelManagerListener = evt -> {
        var p = evt.getPropertyName();
        if (IEditorModelManager.PROPERTY_MODEL_OPENED.equals(p)
                || IEditorModelManager.PROPERTY_MODEL_LOADED.equals(p)
                || IEditorModelManager.PROPERTY_MODEL_REMOVED.equals(p)
                || IEditorModelManager.PROPERTY_MODEL_SAVED.equals(p)
                || IEditorModelManager.PROPERTY_MODEL_CREATED.equals(p)) {
            refreshEnabled();
        }
    };

    private final IWindowListener windowListener = new WindowListener();

    /**
     * Creates a new handler and subscribes to workbench and model lifecycle events.
     */
    protected AbstractTrackedModelHandler() {
        IEditorModelManager.INSTANCE.addPropertyChangeListener(modelManagerListener);
        var display = Display.getDefault();
        if (display != null) {
            display.asyncExec(this::hookWorkbench);
        }
    }

    /**
     * Tests whether the handler should be enabled for the given tracked model.
     *
     * @param model the tracked model to test
     * @return true if the handler should be enabled for this model
     */
    protected abstract boolean isEnabledForModel(IArchimateModel model);

    @Override
    public final boolean isEnabled() {
        return TrackedModels.anyMatches(this::isEnabledForModel);
    }

    /**
     * Returns the model the handler should act on in {@code execute()}. Picks
     * the first candidate satisfying {@code filter}, or the first tracked
     * candidate as a fallback.
     *
     * @param filter the predicate the target model should satisfy
     * @return a matching tracked model, a fallback candidate, or null
     */
    protected final IArchimateModel getTargetModel(Predicate<IArchimateModel> filter) {
        return TrackedModels.find(filter);
    }

    /**
     * Ensures a connected profile exists for the given model's server before
     * running {@code continuation}. If no profile is configured, shows an error
     * and aborts. If a profile exists but is not connected, kicks off the OAuth
     * sign-in flow in a user Job and runs {@code continuation} on the UI thread
     * only if sign-in succeeds.
     *
     * @param model the tracked model whose server URL drives profile lookup
     * @param shell parent shell for dialogs
     * @param title dialog title used for error messages
     * @param continuation runs on the UI thread once authentication is ready
     */
    protected final void runAuthenticated(IArchimateModel model, Shell shell, String title,
            Runnable continuation) {
        var modelUrl = ConnectorProperties.getProperty(model, ConnectorProperties.KEY_URL);
        if (modelUrl == null) {
            // Model isn't tracked; let the continuation decide how to fail.
            continuation.run();
            return;
        }
        var serverUrl = ConnectorProperties.extractServerUrl(modelUrl);
        var profile = ProfileRegistry.INSTANCE.findProfileForServer(serverUrl);
        if (profile == null) {
            MessageDialog.openError(shell, title,
                    MessageFormat.format(Messages.AuthPrompt_noProfile, serverUrl));
            return;
        }
        if (profile.getStatus() == ProfileStatus.CONNECTED) {
            continuation.run();
            return;
        }
        scheduleSignIn(profile, shell, title, continuation);
    }

    private static void scheduleSignIn(ConnectionProfile profile, Shell shell, String title,
            Runnable continuation) {
        var job = new Job(NLS.bind(Messages.ProfilePage_signingIn, profile.getName())) {

            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    AuthService.INSTANCE.login(profile);
                } catch (OAuthException e) {
                    Display.getDefault().asyncExec(() -> MessageDialog.openError(shell, title,
                            MessageFormat.format(Messages.AuthPrompt_signInFailed, e.getMessage())));
                    return Status.OK_STATUS;
                }
                if (profile.getStatus() == ProfileStatus.CONNECTED) {
                    Display.getDefault().asyncExec(continuation);
                }
                return Status.OK_STATUS;
            }

        };
        job.setUser(true);
        job.schedule();
    }

    /**
     * Forces the command framework to re-query {@link #isEnabled()} on the UI
     * thread and the e4 toolbar renderer to re-render the item. Subclasses
     * should call this from their own service listeners.
     */
    protected final void refreshEnabled() {
        var display = Display.getDefault();
        if (display != null && !display.isDisposed()) {
            display.asyncExec(() -> {
                fireHandlerChanged(new HandlerEvent(this, true, false));
                postEnablementUpdateEvent();
            });
        }
    }

    private static void postEnablementUpdateEvent() {
        if (!PlatformUI.isWorkbenchRunning()) {
            return;
        }
        var broker = PlatformUI.getWorkbench().getService(IEventBroker.class);
        if (broker != null) {
            broker.send(UIEvents.REQUEST_ENABLEMENT_UPDATE_TOPIC, UIEvents.ALL_ELEMENT_ID);
        }
    }

    @Override
    public void dispose() {
        IEditorModelManager.INSTANCE.removePropertyChangeListener(modelManagerListener);
        if (PlatformUI.isWorkbenchRunning()) {
            var workbench = PlatformUI.getWorkbench();
            workbench.removeWindowListener(windowListener);
            for (var w : workbench.getWorkbenchWindows()) {
                unhookWindow(w);
            }
        }
        super.dispose();
    }

    private void hookWorkbench() {
        if (!PlatformUI.isWorkbenchRunning()) {
            return;
        }
        var workbench = PlatformUI.getWorkbench();
        workbench.addWindowListener(windowListener);
        for (var w : workbench.getWorkbenchWindows()) {
            hookWindow(w);
        }
        refreshEnabled();
    }

    private void hookWindow(IWorkbenchWindow w) {
        if (w == null) {
            return;
        }
        w.getSelectionService().addSelectionListener(selectionListener);
        w.getPartService().addPartListener(partListener);
    }

    private void unhookWindow(IWorkbenchWindow w) {
        if (w == null) {
            return;
        }
        try {
            w.getSelectionService().removeSelectionListener(selectionListener);
            w.getPartService().removePartListener(partListener);
        } catch (Exception ignored) {
            // Window may already be disposed
        }
    }

    private final class PartListener implements IPartListener2 {

        @Override
        public void partActivated(IWorkbenchPartReference ref) {
            refreshEnabled();
        }

        @Override
        public void partBroughtToTop(IWorkbenchPartReference ref) {
            refreshEnabled();
        }

        @Override
        public void partOpened(IWorkbenchPartReference ref) {
            refreshEnabled();
        }

        @Override
        public void partClosed(IWorkbenchPartReference ref) {
            refreshEnabled();
        }

        @Override
        public void partInputChanged(IWorkbenchPartReference ref) {
            refreshEnabled();
        }

    }

    private final class WindowListener implements IWindowListener {

        @Override
        public void windowOpened(IWorkbenchWindow w) {
            hookWindow(w);
            refreshEnabled();
        }

        @Override
        public void windowClosed(IWorkbenchWindow w) {
            unhookWindow(w);
            refreshEnabled();
        }

        @Override
        public void windowActivated(IWorkbenchWindow w) {
            refreshEnabled();
        }

        @Override
        public void windowDeactivated(IWorkbenchWindow w) {
            // Do nothing
        }

    }

}

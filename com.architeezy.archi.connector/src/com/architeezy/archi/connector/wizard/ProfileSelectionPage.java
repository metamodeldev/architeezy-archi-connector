/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.wizard;

import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.auth.OAuthException;
import com.architeezy.archi.connector.auth.ProfileStatus;
import com.architeezy.archi.connector.service.AuthService;

/**
 * Wizard page for selecting or creating an Architeezy connection profile.
 */
@SuppressWarnings("checkstyle:MagicNumber")
public class ProfileSelectionPage extends WizardPage {

    private Combo profileCombo;

    private Button newButton;

    private Button saveButton;

    private Button deleteButton;

    private Text profileNameText;

    private Text serverUrlText;

    private Text clientIdText;

    private Text authEndpointText;

    private Text tokenEndpointText;

    private Label statusLabel;

    private Button signInOutButton;

    private boolean isNewMode;

    private String editingProfileName;

    private Job signInJob;

    private final boolean requireAuthentication;

    /** Creates the page without requiring authentication before proceeding. */
    public ProfileSelectionPage() {
        this(false);
    }

    /**
     * Creates the page, optionally requiring a successful sign-in.
     *
     * @param requireAuthentication {@code true} to block Next until the user is
     *        signed in
     */
    public ProfileSelectionPage(boolean requireAuthentication) {
        super("profileSelectionPage"); //$NON-NLS-1$
        this.requireAuthentication = requireAuthentication;
        setTitle(Messages.ProfilePage_title);
        setDescription(Messages.ProfilePage_description);
    }

    @Override
    public void createControl(Composite parent) {
        var container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));
        setControl(container);

        createProfileSection(container);
        createDetailsGroup(container);
        createAuthGroup(container);

        refreshProfileCombo();
        updatePageComplete();
    }

    // -----------------------------------------------------------------------

    private void createProfileSection(Composite parent) {
        var row = new Composite(parent, SWT.NONE);
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        row.setLayout(new GridLayout(5, false));

        var lbl = new Label(row, SWT.NONE);
        lbl.setText(Messages.ProfilePage_profileLabel);

        profileCombo = new Combo(row, SWT.READ_ONLY | SWT.DROP_DOWN);
        profileCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        profileCombo.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> onProfileSelected()));

        var sharedImages = PlatformUI.getWorkbench().getSharedImages();

        newButton = new Button(row, SWT.PUSH);
        newButton.setImage(sharedImages.getImage(ISharedImages.IMG_OBJ_ADD));
        newButton.setToolTipText(Messages.ProfilePage_newTooltip);
        newButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> newProfile()));

        saveButton = new Button(row, SWT.PUSH);
        saveButton.setImage(sharedImages.getImage(ISharedImages.IMG_ETOOL_SAVE_EDIT));
        saveButton.setToolTipText(Messages.ProfilePage_saveTooltip);
        saveButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> saveProfile()));

        deleteButton = new Button(row, SWT.PUSH);
        deleteButton.setImage(sharedImages.getImage(ISharedImages.IMG_ETOOL_DELETE));
        deleteButton.setToolTipText(Messages.ProfilePage_deleteTooltip);
        deleteButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> deleteProfile()));
    }

    private void createDetailsGroup(Composite parent) {
        var grp = new Group(parent, SWT.NONE);
        grp.setText(Messages.ProfilePage_detailsGroup);
        grp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        grp.setLayout(new GridLayout(2, false));

        profileNameText = editableField(grp, Messages.ProfilePage_nameLabel);
        serverUrlText = editableField(grp, Messages.ProfilePage_serverUrlLabel);
        clientIdText = editableField(grp, Messages.ProfilePage_clientIdLabel);
        authEndpointText = editableField(grp, Messages.ProfilePage_authEndpointLabel);
        tokenEndpointText = editableField(grp, Messages.ProfilePage_tokenEndpointLabel);
    }

    private Text editableField(Composite parent, String label) {
        var l = new Label(parent, SWT.NONE);
        l.setText(label);
        l.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        var t = new Text(parent, SWT.BORDER);
        t.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return t;
    }

    private void createAuthGroup(Composite parent) {
        var grp = new Group(parent, SWT.NONE);
        grp.setText(Messages.ProfilePage_authGroup);
        grp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        grp.setLayout(new GridLayout(2, false));

        statusLabel = new Label(grp, SWT.NONE);
        statusLabel.setText(Messages.ProfilePage_statusNotAuthenticated);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        signInOutButton = new Button(grp, SWT.PUSH);
        signInOutButton.setText(Messages.ProfilePage_signIn);
        signInOutButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> toggleSignIn()));
    }

    // -----------------------------------------------------------------------
    // Profile management

    private void newProfile() {
        isNewMode = true;
        editingProfileName = null;
        profileCombo.deselectAll();
        profileNameText.setText(""); //$NON-NLS-1$
        serverUrlText.setText(""); //$NON-NLS-1$
        clientIdText.setText(""); //$NON-NLS-1$
        authEndpointText.setText(""); //$NON-NLS-1$
        tokenEndpointText.setText(""); //$NON-NLS-1$
        setErrorMessage(null);
        profileNameText.setFocus();
        deleteButton.setEnabled(false);
        updateAuthArea();
        updatePageComplete();
    }

    private void saveProfile() {
        var name = profileNameText.getText().trim();
        var serverUrl = serverUrlText.getText().trim();
        var clientId = clientIdText.getText().trim();
        var authEp = authEndpointText.getText().trim();
        var tokenEp = tokenEndpointText.getText().trim();

        if (Stream.of(name, serverUrl, clientId, authEp, tokenEp).anyMatch(String::isEmpty)) {
            setErrorMessage(Messages.ProfilePage_allFieldsRequired);
            return;
        }
        setErrorMessage(null);

        ConnectionProfile profile = new ConnectionProfile(name, serverUrl, clientId);
        if (isNewMode) {
            AuthService.INSTANCE.addProfile(profile, authEp, tokenEp);
        } else {
            String originalName = editingProfileName != null ? editingProfileName : name;
            AuthService.INSTANCE.updateProfile(originalName, profile, authEp, tokenEp);
        }
        isNewMode = false;
        editingProfileName = name;
        refreshProfileCombo();
    }

    private void deleteProfile() {
        var profile = getSelectedProfile();
        if (profile == null) {
            return;
        }
        if (!MessageDialog.openConfirm(getShell(), Messages.ProfilePage_deleteTitle,
                NLS.bind(Messages.ProfilePage_deleteConfirm, profile.getName()))) {
            return;
        }
        AuthService.INSTANCE.removeProfile(profile.getName());
        isNewMode = false;
        editingProfileName = null;
        refreshProfileCombo();
    }

    private void onProfileSelected() {
        isNewMode = false;
        setErrorMessage(null);
        int idx = profileCombo.getSelectionIndex();
        List<ConnectionProfile> profiles = AuthService.INSTANCE.getProfiles();
        if (idx >= 0 && idx < profiles.size()) {
            AuthService.INSTANCE.setActiveProfile(profiles.get(idx).getName());
            editingProfileName = profiles.get(idx).getName();
        }
        refreshDetails();
        updateAuthArea();
        updatePageComplete();
    }

    // -----------------------------------------------------------------------
    // Sign in / out

    private void toggleSignIn() {
        var profile = getSelectedProfile();
        if (profile == null) {
            return;
        }

        if (profile.getStatus() == ProfileStatus.CONNECTED) {
            AuthService.INSTANCE.logout(profile);
            updateAuthArea();
            updatePageComplete();
        } else if (signInJob != null) {
            AuthService.INSTANCE.cancelLogin(profile);
            // UI will be updated by the job's asyncExec when it finishes
        } else {
            setErrorMessage(null);
            signInJob = new Job(NLS.bind(Messages.ProfilePage_signingIn, profile.getName())) {

                @Override
                protected IStatus run(IProgressMonitor monitor) {
                    OAuthException error = null;
                    try {
                        AuthService.INSTANCE.login(profile);
                    } catch (OAuthException ex) {
                        error = ex;
                    }
                    final OAuthException finalError = error;
                    Display.getDefault().asyncExec(() -> onSignInCompleted(finalError));
                    return Status.OK_STATUS;
                }

            };
            signInJob.setUser(false);
            signInJob.schedule();
            updateAuthArea();
        }
    }

    private void onSignInCompleted(OAuthException error) {
        if (getControl().isDisposed()) {
            return;
        }
        signInJob = null;
        if (error != null) {
            setErrorMessage(NLS.bind(Messages.ProfilePage_signInFailed, error.getMessage()));
        } else {
            setErrorMessage(null);
        }
        updateAuthArea();
        updatePageComplete();
    }

    // -----------------------------------------------------------------------
    // Refresh helpers

    private void refreshProfileCombo() {
        var profiles = AuthService.INSTANCE.getProfiles();
        var names = profiles.stream().map(ConnectionProfile::getName).toArray(String[]::new);
        profileCombo.setItems(names);

        if (!isNewMode) {
            var active = AuthService.INSTANCE.getActiveProfile();
            if (editingProfileName != null) {
                for (var i = 0; i < profiles.size(); i++) {
                    if (profiles.get(i).getName().equals(editingProfileName)) {
                        profileCombo.select(i);
                        AuthService.INSTANCE.setActiveProfile(editingProfileName);
                        break;
                    }
                }
            } else if (active != null) {
                for (var i = 0; i < profiles.size(); i++) {
                    if (profiles.get(i).getName().equals(active.getName())) {
                        profileCombo.select(i);
                        editingProfileName = active.getName();
                        break;
                    }
                }
            }
        }

        var hasSelection = !isNewMode && profileCombo.getSelectionIndex() >= 0;
        deleteButton.setEnabled(hasSelection);

        refreshDetails();
        updateAuthArea();
        updatePageComplete();
    }

    private void refreshDetails() {
        if (isNewMode) {
            return;
        }
        var profile = getSelectedProfile();
        if (profile == null) {
            profileNameText.setText(""); //$NON-NLS-1$
            serverUrlText.setText(""); //$NON-NLS-1$
            clientIdText.setText(""); //$NON-NLS-1$
            authEndpointText.setText(""); //$NON-NLS-1$
            tokenEndpointText.setText(""); //$NON-NLS-1$
        } else {
            profileNameText.setText(nvl(profile.getName()));
            serverUrlText.setText(nvl(profile.getServerUrl()));
            clientIdText.setText(nvl(profile.getClientId()));
            authEndpointText.setText(nvl(AuthService.INSTANCE.getAuthEndpoint(profile.getName())));
            tokenEndpointText.setText(nvl(AuthService.INSTANCE.getTokenEndpoint(profile.getName())));
        }
    }

    private void updateAuthArea() {
        var profile = isNewMode ? null : getSelectedProfile();
        if (profile == null) {
            statusLabel.setText(Messages.ProfilePage_statusNoProfile);
            signInOutButton.setText(Messages.ProfilePage_signIn);
            signInOutButton.setEnabled(false);
            return;
        }
        signInOutButton.setEnabled(true);
        if (signInJob != null) {
            statusLabel.setText(Messages.ProfilePage_statusSigningIn);
            signInOutButton.setText(Messages.ProfilePage_cancel);
        } else if (profile.getStatus() == ProfileStatus.CONNECTED) {
            statusLabel.setText(Messages.ProfilePage_statusAuthenticated);
            signInOutButton.setText(Messages.ProfilePage_signOut);
        } else if (profile.getStatus() == ProfileStatus.SESSION_EXPIRED) {
            statusLabel.setText(Messages.ProfilePage_statusSessionExpired);
            signInOutButton.setText(Messages.ProfilePage_signIn);
        } else {
            statusLabel.setText(Messages.ProfilePage_statusNotAuthenticated);
            signInOutButton.setText(Messages.ProfilePage_signIn);
        }
        signInOutButton.getParent().layout();
    }

    private void updatePageComplete() {
        var profile = isNewMode ? null : getSelectedProfile();
        if (profile == null) {
            setPageComplete(false);
            setMessage(getDescription());
            return;
        }
        var connected = profile.getStatus() == ProfileStatus.CONNECTED;
        if (!connected) {
            setPageComplete(!requireAuthentication);
            setMessage(Messages.WizardMessages_notSignedIn, requireAuthentication ? ERROR : WARNING);
        } else {
            setPageComplete(true);
            setMessage(getDescription());
        }
    }

    // -----------------------------------------------------------------------
    // Public API

    /**
     * Returns the profile currently selected in the combo, or the active profile.
     *
     * @return the selected connection profile, or {@code null} if none exists
     */
    public ConnectionProfile getSelectedProfile() {
        var idx = profileCombo == null ? -1 : profileCombo.getSelectionIndex();
        var profiles = AuthService.INSTANCE.getProfiles();
        if (idx >= 0 && idx < profiles.size()) {
            return profiles.get(idx);
        }
        return AuthService.INSTANCE.getActiveProfile();
    }

    // -----------------------------------------------------------------------

    private static String nvl(String s) {
        return s != null ? s : ""; //$NON-NLS-1$
    }

}

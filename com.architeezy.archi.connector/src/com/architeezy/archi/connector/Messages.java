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

import org.eclipse.osgi.util.NLS;

/**
 * NLS message bundle for the Architeezy connector plugin.
 */
@SuppressWarnings({ "checkstyle:JavadocVariable", "checkstyle:StaticVariableName", "checkstyle:VisibilityModifier",
        "java:S3008" })
public final class Messages extends NLS {

    // ProfileSelectionPage
    public static String ProfilePage_title;

    public static String ProfilePage_description;

    public static String ProfilePage_profileLabel;

    public static String ProfilePage_newTooltip;

    public static String ProfilePage_saveTooltip;

    public static String ProfilePage_deleteTooltip;

    public static String ProfilePage_detailsGroup;

    public static String ProfilePage_nameLabel;

    public static String ProfilePage_serverUrlLabel;

    public static String ProfilePage_clientIdLabel;

    public static String ProfilePage_authEndpointLabel;

    public static String ProfilePage_tokenEndpointLabel;

    public static String ProfilePage_authGroup;

    public static String ProfilePage_statusNotAuthenticated;

    public static String ProfilePage_signIn;

    public static String ProfilePage_allFieldsRequired;

    public static String ProfilePage_deleteTitle;

    public static String ProfilePage_deleteConfirm;

    public static String ProfilePage_signingIn;

    public static String ProfilePage_signInFailed;

    public static String ProfilePage_statusNoProfile;

    public static String ProfilePage_statusSigningIn;

    public static String ProfilePage_statusAuthenticated;

    public static String ProfilePage_statusSessionExpired;

    public static String ProfilePage_cancel;

    public static String ProfilePage_signOut;

    // ModelSelectionPage
    public static String ModelPage_title;

    public static String ModelPage_description;

    public static String ModelPage_searchPlaceholder;

    public static String ModelPage_columnName;

    public static String ModelPage_columnLastModified;

    public static String ModelPage_saveAs;

    public static String ModelPage_browse;

    public static String ModelPage_noProfile;

    public static String ModelPage_loading;

    public static String ModelPage_loadingJob;

    public static String ModelPage_loadError;

    public static String ModelPage_saveDialogTitle;

    public static String ModelPage_overwriteWarning;

    // ProjectSelectionPage
    public static String ProjectPage_title;

    public static String ProjectPage_description;

    public static String ProjectPage_searchPlaceholder;

    public static String ProjectPage_noProfile;

    public static String ProjectPage_loading;

    public static String ProjectPage_loadingJob;

    public static String ProjectPage_loadError;

    public static String ProjectPage_unknownScope;

    public static String ModelPage_unknownScope;

    public static String ModelPage_unknownProject;

    // ImportWizard
    public static String ImportWizard_title;

    public static String ImportWizard_importing;

    public static String ImportWizard_importFailed;

    public static String ImportWizard_importFailedMessage;

    public static String ImportWizard_success;

    public static String ImportWizard_successTitle;

    // ExportWizard
    public static String ExportWizard_title;

    public static String ExportWizard_exporting;

    public static String ExportWizard_exportFailed;

    public static String ExportWizard_exportFailedMessage;

    public static String ExportWizard_success;

    public static String ExportWizard_successTitle;

    // ProgressResultDialog / ResultWizardPage shared labels
    public static String ProgressDialog_cancelled;

    public static String ProgressDialog_cancelledTitle;

    public static String ProgressDialog_close;

    // ExportMenuHandler
    public static String ExportHandler_title;

    public static String ExportHandler_noModel;

    // WizardMessages
    public static String WizardMessages_notSignedIn;

    // ModelTreeDecorator
    public static String Decorator_updateTooltip;

    public static String Decorator_localChangesTooltip;

    public static String Decorator_localAndRemoteTooltip;

    // ConflictResolutionDialog
    public static String ConflictDialog_title;

    public static String ConflictDialog_description;

    public static String ConflictDialog_colStructure;

    public static String ConflictDialog_colLocal;

    public static String ConflictDialog_colRemote;

    public static String ConflictDialog_acceptAllLocal;

    public static String ConflictDialog_acceptAllRemote;

    public static String ConflictDialog_showAllChanges;

    public static String ConflictDialog_changeAdded;

    public static String ConflictDialog_changeDeleted;

    // OpenInBrowserHandler
    public static String OpenInBrowserHandler_title;

    public static String OpenInBrowserHandler_jobName;

    public static String OpenInBrowserHandler_failed;

    // PullHandler
    public static String PullHandler_title;

    public static String PullHandler_noModel;

    public static String PullHandler_jobName;

    public static String PullHandler_success;

    public static String PullHandler_failed;

    public static String PullHandler_remoteUnchanged;

    // PushHandler
    public static String PushHandler_title;

    public static String PushHandler_noModel;

    public static String PushHandler_jobName;

    public static String PushHandler_success;

    public static String PushHandler_failed;

    // Authentication prompt (shared by Pull/Push handlers)
    public static String AuthPrompt_noProfile;

    public static String AuthPrompt_signInFailed;

    private static final String BUNDLE_NAME = "com.architeezy.archi.connector.messages"; //$NON-NLS-1$

    static {
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }

}

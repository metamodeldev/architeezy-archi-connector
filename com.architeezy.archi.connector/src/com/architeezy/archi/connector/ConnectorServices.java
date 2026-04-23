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

import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Display;

import com.architeezy.archi.connector.api.ArchiteezyClient;
import com.architeezy.archi.connector.auth.IOAuthManager;
import com.architeezy.archi.connector.auth.OAuthManager;
import com.architeezy.archi.connector.auth.ProfileRegistry;
import com.architeezy.archi.connector.auth.TokenStore;
import com.architeezy.archi.connector.io.ModelSerializer;
import com.architeezy.archi.connector.io.SnapshotStore;
import com.architeezy.archi.connector.io.SnapshotSupport;
import com.architeezy.archi.connector.io.TrackedModelStore;
import com.architeezy.archi.connector.model.DefaultEditorModelManager;
import com.architeezy.archi.connector.model.IEditorModelManagerAdapter;
import com.architeezy.archi.connector.services.AuthService;
import com.architeezy.archi.connector.services.DialogConflictResolver;
import com.architeezy.archi.connector.services.LocalChangeService;
import com.architeezy.archi.connector.services.MergeService;
import com.architeezy.archi.connector.services.ModelExportService;
import com.architeezy.archi.connector.services.ModelImportService;
import com.architeezy.archi.connector.services.ModelSyncService;
import com.architeezy.archi.connector.services.RepositoryService;
import com.architeezy.archi.connector.services.UpdateCheckService;
import com.architeezy.archi.connector.ui.navigator.ModelTreeDecorator;

/**
 * Composition root that instantiates the full service graph with explicit
 * dependency injection.
 *
 * <p>
 * One instance lives inside {@link ConnectorPlugin} for the plugin's lifetime;
 * tests construct individual services directly instead of going through this
 * class.
 */
@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")
public final class ConnectorServices {

    private final ModelSerializer modelSerializer;

    private final ArchiteezyClient apiClient;

    private final IOAuthManager oauthManager;

    private final SnapshotStore snapshotStore;

    private final TrackedModelStore trackedModelStore;

    private final TokenStore tokenStore;

    private final ProfileRegistry profileRegistry;

    private final SnapshotSupport snapshotSupport;

    private final AuthService authService;

    private final RepositoryService repositoryService;

    private final ModelExportService modelExportService;

    private final ModelImportService modelImportService;

    private final MergeService mergeService;

    private final UpdateCheckService updateCheckService;

    private final LocalChangeService localChangeService;

    private final ModelSyncService modelSyncService;

    private final ModelTreeDecorator modelTreeDecorator;

    private final IEditorModelManagerAdapter editorModelManager;

    /**
     * Builds the service graph using the given platform-provided resources.
     *
     * @param stateLocation supplier of the plugin's state-location directory
     * @param preferenceStore plugin preference store
     * @param securePreferences supplier of the secure-preferences root node
     */
    public ConnectorServices(Supplier<Path> stateLocation,
            IPreferenceStore preferenceStore,
            Supplier<ISecurePreferences> securePreferences) {
        this.modelSerializer = new ModelSerializer();
        this.apiClient = new ArchiteezyClient();
        this.oauthManager = new OAuthManager();
        this.snapshotStore = new SnapshotStore(stateLocation);
        this.trackedModelStore = new TrackedModelStore(stateLocation);
        this.tokenStore = new TokenStore(securePreferences);
        this.profileRegistry = new ProfileRegistry(preferenceStore, tokenStore);
        this.snapshotSupport = new SnapshotSupport(modelSerializer, snapshotStore);
        this.editorModelManager = new DefaultEditorModelManager();
        this.authService = new AuthService(oauthManager, tokenStore, profileRegistry);
        this.repositoryService = new RepositoryService(apiClient, authService);
        final Executor uiExecutor = Display.getDefault()::syncExec;
        this.modelExportService = new ModelExportService(apiClient, authService, modelSerializer,
                trackedModelStore, snapshotSupport, editorModelManager, uiExecutor);
        this.modelImportService = new ModelImportService(apiClient, authService, modelSerializer,
                trackedModelStore, snapshotSupport, editorModelManager, uiExecutor);
        this.mergeService = new MergeService(modelSerializer, new DialogConflictResolver(modelSerializer));
        this.updateCheckService = new UpdateCheckService(apiClient, authService, profileRegistry,
                trackedModelStore, editorModelManager);
        this.localChangeService = new LocalChangeService(snapshotStore, modelSerializer, editorModelManager);
        this.modelSyncService = new ModelSyncService(apiClient, authService, profileRegistry, snapshotStore,
                modelSerializer, trackedModelStore, mergeService, localChangeService, updateCheckService,
                editorModelManager, uiExecutor);
        this.modelTreeDecorator = new ModelTreeDecorator(updateCheckService, localChangeService,
                trackedModelStore);
    }

    /**
     * Returns the shared model serializer.
     *
     * @return the model serializer
     */
    public ModelSerializer modelSerializer() {
        return modelSerializer;
    }

    /**
     * Returns the shared HTTP client.
     *
     * @return the HTTP client for the Architeezy REST API
     */
    public ArchiteezyClient apiClient() {
        return apiClient;
    }

    /**
     * Returns the shared OAuth manager.
     *
     * @return the OAuth manager
     */
    public IOAuthManager oauthManager() {
        return oauthManager;
    }

    /**
     * Returns the base-snapshot store.
     *
     * @return the snapshot store
     */
    public SnapshotStore snapshotStore() {
        return snapshotStore;
    }

    /**
     * Returns the tracked-models metadata store.
     *
     * @return the tracked-models store
     */
    public TrackedModelStore trackedModelStore() {
        return trackedModelStore;
    }

    /**
     * Returns the token store.
     *
     * @return the token store
     */
    public TokenStore tokenStore() {
        return tokenStore;
    }

    /**
     * Returns the profile registry.
     *
     * @return the profile registry
     */
    public ProfileRegistry profileRegistry() {
        return profileRegistry;
    }

    /**
     * Returns the snapshot-support helper.
     *
     * @return the snapshot-support helper
     */
    public SnapshotSupport snapshotSupport() {
        return snapshotSupport;
    }

    /**
     * Returns the auth service.
     *
     * @return the auth service
     */
    public AuthService authService() {
        return authService;
    }

    /**
     * Returns the repository service.
     *
     * @return the repository service
     */
    public RepositoryService repositoryService() {
        return repositoryService;
    }

    /**
     * Returns the model-export service.
     *
     * @return the export service
     */
    public ModelExportService modelExportService() {
        return modelExportService;
    }

    /**
     * Returns the model-import service.
     *
     * @return the import service
     */
    public ModelImportService modelImportService() {
        return modelImportService;
    }

    /**
     * Returns the merge service.
     *
     * @return the merge service
     */
    public MergeService mergeService() {
        return mergeService;
    }

    /**
     * Returns the update-check service.
     *
     * @return the update-check service
     */
    public UpdateCheckService updateCheckService() {
        return updateCheckService;
    }

    /**
     * Returns the local-change service.
     *
     * @return the local-change service
     */
    public LocalChangeService localChangeService() {
        return localChangeService;
    }

    /**
     * Returns the model-sync service.
     *
     * @return the model-sync service
     */
    public ModelSyncService modelSyncService() {
        return modelSyncService;
    }

    /**
     * Returns the model-tree decorator.
     *
     * @return the model-tree decorator
     */
    public ModelTreeDecorator modelTreeDecorator() {
        return modelTreeDecorator;
    }

}

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

import java.util.List;

import com.architeezy.archi.connector.api.ArchiteezyClient;
import com.architeezy.archi.connector.api.dto.PagedResult;
import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.api.dto.RemoteProject;
import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.auth.ProfileStatus;

/**
 * Read-only and administrative repository operations: list projects, list
 * models, and delete.
 *
 * Must be called from a background thread (Job / IRunnableWithProgress).
 */
@SuppressWarnings({ "java:S6548", "java:S112" })
public final class RepositoryService {

    /** The singleton instance of RepositoryService. */
    public static final RepositoryService INSTANCE = new RepositoryService();

    /** Client used for communication with the Architeezy server. */
    private final ArchiteezyClient client = new ArchiteezyClient();

    private RepositoryService() {
    }

    /**
     * Lists the models available in the remote repository.
     *
     * @param profile the connection profile to use
     * @param page the page number to retrieve
     * @param size the number of items per page
     * @return a paged result containing the list of remote models
     * @throws Exception if a communication error occurs
     */
    public PagedResult<RemoteModel> listModels(ConnectionProfile profile,
            int page, int size) throws Exception {
        var token = profile.getStatus() == ProfileStatus.CONNECTED
                ? AuthService.INSTANCE.getValidAccessToken(profile)
                : null;
        return client.listModels(profile.getServerUrl(), token, page, size);
    }

    /**
     * Lists the projects available in the remote repository.
     *
     * @param profile the connection profile to use
     * @return list of remote projects
     * @throws Exception if a communication error occurs
     */
    public List<RemoteProject> listProjects(ConnectionProfile profile) throws Exception {
        var token = AuthService.INSTANCE.getValidAccessToken(profile);
        return client.listProjects(profile.getServerUrl(), token);
    }

    /**
     * Deletes the specified remote model from the repository.
     *
     * @param profile the connection profile to use
     * @param remote the remote model to delete
     * @throws Exception if the deletion fails
     */
    public void deleteModel(ConnectionProfile profile, RemoteModel remote) throws Exception {
        var token = AuthService.INSTANCE.getValidAccessToken(profile);
        client.deleteModel(token, remote.selfUrl());
    }

}

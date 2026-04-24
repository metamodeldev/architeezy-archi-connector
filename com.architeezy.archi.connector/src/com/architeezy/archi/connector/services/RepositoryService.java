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

import com.architeezy.archi.connector.api.ApiException;
import com.architeezy.archi.connector.api.ArchiteezyClient;
import com.architeezy.archi.connector.api.dto.PagedResult;
import com.architeezy.archi.connector.api.dto.RemoteModel;
import com.architeezy.archi.connector.api.dto.RemoteProject;
import com.architeezy.archi.connector.auth.ConnectionProfile;
import com.architeezy.archi.connector.auth.OAuthException;
import com.architeezy.archi.connector.auth.ProfileStatus;

/**
 * Read-only and administrative repository operations: list projects, list
 * models, and delete.
 *
 * Must be called from a background thread (Job / IRunnableWithProgress).
 */
public final class RepositoryService {

    private final ArchiteezyClient client;

    private final AuthService authService;

    /**
     * Creates a repository-service view backed by {@code client} and {@code authService}.
     *
     * @param client HTTP client for REST calls
     * @param authService provider of valid OAuth access tokens
     */
    public RepositoryService(ArchiteezyClient client, AuthService authService) {
        this.client = client;
        this.authService = authService;
    }

    /**
     * Lists the models available in the remote repository.
     *
     * @param profile the connection profile to use
     * @param page the page number to retrieve
     * @param size the number of items per page
     * @return a paged result containing the list of remote models
     * @throws OAuthException if the profile has no valid token
     * @throws ApiException if the REST call fails
     */
    public PagedResult<RemoteModel> listModels(ConnectionProfile profile,
            int page, int size) throws OAuthException, ApiException {
        var token = profile.getStatus() == ProfileStatus.CONNECTED
                ? authService.getValidAccessToken(profile)
                : null;
        return client.listModels(profile.getServerUrl(), token, page, size);
    }

    /**
     * Lists a page of models using the server's default page size.
     *
     * @param profile the connection profile to use
     * @param page the zero-based page index
     * @return a paged result containing the list of remote models
     * @throws OAuthException if the profile has no valid token
     * @throws ApiException if the REST call fails
     */
    public PagedResult<RemoteModel> listModels(ConnectionProfile profile, int page)
            throws OAuthException, ApiException {
        var token = profile.getStatus() == ProfileStatus.CONNECTED
                ? authService.getValidAccessToken(profile)
                : null;
        return client.listModels(profile.getServerUrl(), token, page);
    }

    /**
     * Lists the projects available in the remote repository.
     *
     * @param profile the connection profile to use
     * @return list of remote projects
     * @throws OAuthException if the profile has no valid token
     * @throws ApiException if the REST call fails
     */
    public List<RemoteProject> listProjects(ConnectionProfile profile) throws OAuthException, ApiException {
        var token = authService.getValidAccessToken(profile);
        return client.listProjects(profile.getServerUrl(), token);
    }

    /**
     * Lists a page of projects using the server's default page size.
     *
     * @param profile the connection profile to use
     * @param page the zero-based page index
     * @return a paged result containing the list of remote projects
     * @throws OAuthException if the profile has no valid token
     * @throws ApiException if the REST call fails
     */
    public PagedResult<RemoteProject> listProjects(ConnectionProfile profile, int page)
            throws OAuthException, ApiException {
        var token = authService.getValidAccessToken(profile);
        return client.listProjectsPage(profile.getServerUrl(), token, page);
    }

    /**
     * Deletes the specified remote model from the repository.
     *
     * @param profile the connection profile to use
     * @param remote the remote model to delete
     * @throws OAuthException if the profile has no valid token
     * @throws ApiException if the REST call fails
     */
    public void deleteModel(ConnectionProfile profile, RemoteModel remote) throws OAuthException, ApiException {
        var token = authService.getValidAccessToken(profile);
        client.deleteModel(token, remote.selfUrl());
    }

}

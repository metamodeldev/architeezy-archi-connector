/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.auth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.preference.IPreferenceStore;

import com.architeezy.archi.connector.model.ConnectorProperties;

/**
 * Owns the list of connection profiles and persists them via an Eclipse
 * preference store. Also exposes lookup helpers used by other services.
 *
 * Thread-safety: mutating methods are synchronized.
 */
public class ProfileRegistry {

    private static final String PREF_PROFILES = "architeezy.profiles"; //$NON-NLS-1$

    private static final String PREF_ACTIVE_PROFILE = "architeezy.activeProfile"; //$NON-NLS-1$

    private static final String PREF_SERVER_URL = "architeezy.profile.%s.url"; //$NON-NLS-1$

    private static final String PREF_CLIENT_ID = "architeezy.profile.%s.clientId"; //$NON-NLS-1$

    private static final String PREF_AUTH_ENDPOINT = "architeezy.profile.%s.authEndpoint"; //$NON-NLS-1$

    private static final String PREF_TOKEN_ENDPOINT = "architeezy.profile.%s.tokenEndpoint"; //$NON-NLS-1$

    private static final String PREF_DEFAULT_PROFILE_CREATED = "architeezy.defaultProfileCreated"; //$NON-NLS-1$

    private static final String DEFAULT_PROFILE_NAME = "Architeezy"; //$NON-NLS-1$

    private static final String DEFAULT_SERVER_URL = "https://architeezy.com"; //$NON-NLS-1$

    private static final String DEFAULT_CLIENT_ID = "architeezy-api"; //$NON-NLS-1$

    private static final String DEFAULT_AUTH_ENDPOINT = "https://auth.architeezy.com/realms/architeezy/protocol/openid-connect/auth"; //$NON-NLS-1$

    private static final String DEFAULT_TOKEN_ENDPOINT = "https://auth.architeezy.com/realms/architeezy/protocol/openid-connect/token"; //$NON-NLS-1$

    private final IPreferenceStore prefs;

    private final TokenStore tokenStore;

    private final List<ConnectionProfile> profiles = new ArrayList<>();

    private ConnectionProfile activeProfile;

    /**
     * Creates a registry that reads/writes profiles via {@code prefs} and reads
     * token presence via {@code tokenStore}.
     *
     * @param prefs preference store backing the persisted profile list
     * @param tokenStore token store used to determine initial connection status
     */
    public ProfileRegistry(IPreferenceStore prefs, TokenStore tokenStore) {
        this.prefs = prefs;
        this.tokenStore = tokenStore;
        loadProfiles();
    }

    /**
     * Returns an unmodifiable list of all connection profiles.
     *
     * @return the list of profiles
     */
    public synchronized List<ConnectionProfile> getProfiles() {
        return Collections.unmodifiableList(profiles);
    }

    /**
     * Returns the currently active connection profile.
     *
     * @return the active profile, or null if none is set
     */
    public synchronized ConnectionProfile getActiveProfile() {
        return activeProfile;
    }

    /**
     * Sets the active connection profile by name.
     *
     * @param profileName the name of the profile to make active
     */
    public synchronized void setActiveProfile(String profileName) {
        profiles.stream()
                .filter(p -> p.getName().equals(profileName))
                .findFirst()
                .ifPresent(p -> {
                    activeProfile = p;
                    prefs.setValue(PREF_ACTIVE_PROFILE, profileName);
                });
    }

    /**
     * Adds a new connection profile and saves its properties.
     *
     * @param profile the profile to add
     * @param authEndpoint the authorization endpoint
     * @param tokenEndpoint the token endpoint
     */
    public synchronized void addProfile(ConnectionProfile profile, String authEndpoint, String tokenEndpoint) {
        profiles.add(profile);
        saveProfilePrefs(profile, authEndpoint, tokenEndpoint);
        if (activeProfile == null) {
            activeProfile = profile;
            prefs.setValue(PREF_ACTIVE_PROFILE, profile.getName());
        }
        saveProfileList();
    }

    /**
     * Updates an existing connection profile.
     *
     * @param oldName the name of the profile to replace
     * @param newProfile the updated profile
     * @param authEndpoint the new authorization endpoint
     * @param tokenEndpoint the new token endpoint
     */
    public synchronized void updateProfile(String oldName, ConnectionProfile newProfile, String authEndpoint,
            String tokenEndpoint) {
        for (var i = 0; i < profiles.size(); i++) {
            var old = profiles.get(i);
            if (old.getName().equals(oldName)) {
                if (!old.getServerUrl().equals(newProfile.getServerUrl())) {
                    tokenStore.clearTokens(old.getServerUrl());
                }
                if (!old.getName().equals(newProfile.getName())) {
                    removeProfilePrefs(old.getName());
                }
                profiles.set(i, newProfile);
                saveProfilePrefs(newProfile, authEndpoint, tokenEndpoint);
                if (activeProfile != null && activeProfile.getName().equals(oldName)) {
                    activeProfile = newProfile;
                    prefs.setValue(PREF_ACTIVE_PROFILE, newProfile.getName());
                }
                break;
            }
        }
        saveProfileList();
    }

    /**
     * Creates a copy of the named profile under a unique name (e.g. "Foo (copy)",
     * "Foo (copy 2)") and persists it. The copy inherits the source's server URL,
     * client ID, auth and token endpoints, but starts in DISCONNECTED state.
     *
     * @param sourceName name of the profile to duplicate
     * @return the newly created profile, or {@code null} if no profile with
     *         {@code sourceName} exists
     */
    public synchronized ConnectionProfile duplicateProfile(String sourceName) {
        var idx = ConnectionProfiles.indexByName(profiles, sourceName);
        if (idx < 0) {
            return null;
        }
        var src = profiles.get(idx);
        var newName = makeUniqueCopyName(sourceName);
        var copy = new ConnectionProfile(newName, src.getServerUrl(), src.getClientId());
        profiles.add(copy);
        saveProfilePrefs(copy, getAuthEndpoint(sourceName), getTokenEndpoint(sourceName));
        saveProfileList();
        return copy;
    }

    /**
     * Resets the built-in Architeezy profile to factory defaults. If the profile
     * does not exist, it is created. Tokens stored under the previous server URL
     * (if different) are cleared.
     *
     * @return the (re)created default profile
     */
    public synchronized ConnectionProfile resetDefaultProfile() {
        var defaultProfile = new ConnectionProfile(
                DEFAULT_PROFILE_NAME, DEFAULT_SERVER_URL, DEFAULT_CLIENT_ID);
        var idx = ConnectionProfiles.indexByName(profiles, DEFAULT_PROFILE_NAME);
        if (idx >= 0) {
            var existing = profiles.get(idx);
            if (!existing.getServerUrl().equals(defaultProfile.getServerUrl())) {
                tokenStore.clearTokens(existing.getServerUrl());
            }
            profiles.set(idx, defaultProfile);
            if (activeProfile != null && activeProfile.getName().equals(DEFAULT_PROFILE_NAME)) {
                activeProfile = defaultProfile;
            }
        } else {
            profiles.add(defaultProfile);
            if (activeProfile == null) {
                activeProfile = defaultProfile;
                prefs.setValue(PREF_ACTIVE_PROFILE, DEFAULT_PROFILE_NAME);
            }
        }
        saveProfilePrefs(defaultProfile, DEFAULT_AUTH_ENDPOINT, DEFAULT_TOKEN_ENDPOINT);
        saveProfileList();
        prefs.setValue(PREF_DEFAULT_PROFILE_CREATED, true);
        return defaultProfile;
    }

    private String makeUniqueCopyName(String base) {
        var candidate = base + " (copy)"; //$NON-NLS-1$
        if (ConnectionProfiles.indexByName(profiles, candidate) < 0) {
            return candidate;
        }
        var i = 2;
        while (ConnectionProfiles.indexByName(profiles, base + " (copy " + i + ")") >= 0) { //$NON-NLS-1$ //$NON-NLS-2$
            i++;
        }
        return base + " (copy " + i + ")"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Removes a connection profile by name.
     *
     * @param profileName the name of the profile to remove
     */
    public synchronized void removeProfile(String profileName) {
        profiles.removeIf(p -> {
            if (p.getName().equals(profileName)) {
                tokenStore.clearTokens(p.getServerUrl());
                return true;
            }
            return false;
        });
        if (activeProfile != null && activeProfile.getName().equals(profileName)) {
            activeProfile = profiles.isEmpty() ? null : profiles.get(0);
        }
        saveProfileList();
    }

    /**
     * Returns the first profile whose server URL matches the given URL, or
     * {@code null} if none is found. Prefers a connected profile over others.
     * Both the input URL and stored profile URLs are normalized via
     * {@link ConnectorProperties#normalizeServerUrl} so that profiles entered
     * with a trailing slash still match server URLs derived from model self
     * links.
     *
     * @param serverUrl the server URL to match
     * @return a matching profile, or null
     */
    public synchronized ConnectionProfile findProfileForServer(String serverUrl) {
        if (serverUrl == null) {
            return null;
        }
        var normalized = ConnectorProperties.normalizeServerUrl(serverUrl);
        ConnectionProfile fallback = null;
        for (var p : profiles) {
            if (normalized.equals(ConnectorProperties.normalizeServerUrl(p.getServerUrl()))) {
                if (p.getStatus() == ProfileStatus.CONNECTED) {
                    return p;
                }
                if (fallback == null) {
                    fallback = p;
                }
            }
        }
        return fallback;
    }

    /**
     * Returns the authorization endpoint for the given profile.
     *
     * @param profileName the name of the profile
     * @return the authorization endpoint URL
     */
    public synchronized String getAuthEndpoint(String profileName) {
        return prefs.getString(String.format(PREF_AUTH_ENDPOINT, profileName));
    }

    /**
     * Returns the token endpoint for the given profile.
     *
     * @param profileName the name of the profile
     * @return the token endpoint URL
     */
    public synchronized String getTokenEndpoint(String profileName) {
        return prefs.getString(String.format(PREF_TOKEN_ENDPOINT, profileName));
    }

    // -----------------------------------------------------------------------
    // Persistence (profile list in IPreferenceStore, tokens in TokenStore)

    private void loadProfiles() {
        var list = prefs.getString(PREF_PROFILES);
        if (list != null && !list.isBlank()) {
            loadStoredProfiles(list);
        }
        if (profiles.isEmpty() && !prefs.getBoolean(PREF_DEFAULT_PROFILE_CREATED)) {
            createDefaultProfile();
        }
    }

    private void loadStoredProfiles(String list) {
        var activeName = prefs.getString(PREF_ACTIVE_PROFILE);
        for (var rawName : list.split(",")) { //$NON-NLS-1$
            var name = rawName.trim();
            var url = name.isBlank() ? null : prefs.getString(String.format(PREF_SERVER_URL, name));
            if (url != null && !url.isBlank()) {
                var clientId = prefs.getString(String.format(PREF_CLIENT_ID, name));
                var p = new ConnectionProfile(name, url, clientId);
                var hasToken = tokenStore.getAccessToken(url) != null;
                p.setStatus(hasToken ? ProfileStatus.CONNECTED : ProfileStatus.DISCONNECTED);
                profiles.add(p);
                if (name.equals(activeName)) {
                    activeProfile = p;
                }
            }
        }
        if (activeProfile == null && !profiles.isEmpty()) {
            activeProfile = profiles.get(0);
        }
    }

    private void createDefaultProfile() {
        var defaultProfile = new ConnectionProfile(
                DEFAULT_PROFILE_NAME, DEFAULT_SERVER_URL, DEFAULT_CLIENT_ID);
        defaultProfile.setStatus(ProfileStatus.DISCONNECTED);
        profiles.add(defaultProfile);
        activeProfile = defaultProfile;
        saveProfilePrefs(defaultProfile, DEFAULT_AUTH_ENDPOINT, DEFAULT_TOKEN_ENDPOINT);
        saveProfileList();
        prefs.setValue(PREF_ACTIVE_PROFILE, DEFAULT_PROFILE_NAME);
        prefs.setValue(PREF_DEFAULT_PROFILE_CREATED, true);
    }

    private void saveProfileList() {
        var sb = new StringBuilder();
        for (int i = 0; i < profiles.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(profiles.get(i).getName());
        }
        prefs.setValue(PREF_PROFILES, sb.toString());
    }

    private void removeProfilePrefs(String name) {
        prefs.setToDefault(String.format(PREF_SERVER_URL, name));
        prefs.setToDefault(String.format(PREF_CLIENT_ID, name));
        prefs.setToDefault(String.format(PREF_AUTH_ENDPOINT, name));
        prefs.setToDefault(String.format(PREF_TOKEN_ENDPOINT, name));
    }

    private void saveProfilePrefs(ConnectionProfile profile, String authEndpoint, String tokenEndpoint) {
        prefs.setValue(String.format(PREF_SERVER_URL, profile.getName()), profile.getServerUrl());
        prefs.setValue(String.format(PREF_CLIENT_ID, profile.getName()), profile.getClientId());
        prefs.setValue(String.format(PREF_AUTH_ENDPOINT, profile.getName()), authEndpoint);
        prefs.setValue(String.format(PREF_TOKEN_ENDPOINT, profile.getName()), tokenEndpoint);
    }

}

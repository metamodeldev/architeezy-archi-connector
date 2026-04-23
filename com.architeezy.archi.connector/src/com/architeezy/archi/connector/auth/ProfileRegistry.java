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

import com.architeezy.archi.connector.ConnectorPlugin;

/**
 * Owns the list of connection profiles and persists them via the Eclipse
 * preference store. Also exposes lookup helpers used by other services.
 *
 * Thread-safety: mutating methods are synchronized.
 */
@SuppressWarnings("java:S6548")
public final class ProfileRegistry {

    /** The singleton instance. */
    public static final ProfileRegistry INSTANCE = new ProfileRegistry();

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

    private final List<ConnectionProfile> profiles = new ArrayList<>();

    private ConnectionProfile activeProfile;

    private ProfileRegistry() {
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
                    preferenceStore().setValue(PREF_ACTIVE_PROFILE, profileName);
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
            preferenceStore().setValue(PREF_ACTIVE_PROFILE, profile.getName());
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
                    TokenStore.INSTANCE.clearTokens(old.getServerUrl());
                }
                if (!old.getName().equals(newProfile.getName())) {
                    removeProfilePrefs(old.getName());
                }
                profiles.set(i, newProfile);
                saveProfilePrefs(newProfile, authEndpoint, tokenEndpoint);
                if (activeProfile != null && activeProfile.getName().equals(oldName)) {
                    activeProfile = newProfile;
                    preferenceStore().setValue(PREF_ACTIVE_PROFILE, newProfile.getName());
                }
                break;
            }
        }
        saveProfileList();
    }

    /**
     * Removes a connection profile by name.
     *
     * @param profileName the name of the profile to remove
     */
    public synchronized void removeProfile(String profileName) {
        profiles.removeIf(p -> {
            if (p.getName().equals(profileName)) {
                TokenStore.INSTANCE.clearTokens(p.getServerUrl());
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
     *
     * @param serverUrl the server URL to match
     * @return a matching profile, or null
     */
    public synchronized ConnectionProfile findProfileForServer(String serverUrl) {
        if (serverUrl == null) {
            return null;
        }
        ConnectionProfile fallback = null;
        for (var p : profiles) {
            if (serverUrl.equals(p.getServerUrl())) {
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
        return preferenceStore().getString(String.format(PREF_AUTH_ENDPOINT, profileName));
    }

    /**
     * Returns the token endpoint for the given profile.
     *
     * @param profileName the name of the profile
     * @return the token endpoint URL
     */
    public synchronized String getTokenEndpoint(String profileName) {
        return preferenceStore().getString(String.format(PREF_TOKEN_ENDPOINT, profileName));
    }

    // -----------------------------------------------------------------------
    // Persistence (profile list in IPreferenceStore, tokens in ISecurePreferences)

    private void loadProfiles() {
        var prefs = preferenceStore();
        var list = prefs.getString(PREF_PROFILES);
        if (list != null && !list.isBlank()) {
            loadStoredProfiles(prefs, list);
        }
        if (profiles.isEmpty() && !prefs.getBoolean(PREF_DEFAULT_PROFILE_CREATED)) {
            createDefaultProfile(prefs);
        }
    }

    private void loadStoredProfiles(IPreferenceStore prefs, String list) {
        var activeName = prefs.getString(PREF_ACTIVE_PROFILE);
        for (var rawName : list.split(",")) { //$NON-NLS-1$
            var name = rawName.trim();
            var url = name.isBlank() ? null : prefs.getString(String.format(PREF_SERVER_URL, name));
            if (url != null && !url.isBlank()) {
                var clientId = prefs.getString(String.format(PREF_CLIENT_ID, name));
                var p = new ConnectionProfile(name, url, clientId);
                var hasToken = TokenStore.INSTANCE.getAccessToken(url) != null;
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

    private void createDefaultProfile(IPreferenceStore prefs) {
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
        preferenceStore().setValue(PREF_PROFILES, sb.toString());
    }

    private void removeProfilePrefs(String name) {
        var prefs = preferenceStore();
        prefs.setToDefault(String.format(PREF_SERVER_URL, name));
        prefs.setToDefault(String.format(PREF_CLIENT_ID, name));
        prefs.setToDefault(String.format(PREF_AUTH_ENDPOINT, name));
        prefs.setToDefault(String.format(PREF_TOKEN_ENDPOINT, name));
    }

    private void saveProfilePrefs(ConnectionProfile profile, String authEndpoint, String tokenEndpoint) {
        var prefs = preferenceStore();
        prefs.setValue(String.format(PREF_SERVER_URL, profile.getName()), profile.getServerUrl());
        prefs.setValue(String.format(PREF_CLIENT_ID, profile.getName()), profile.getClientId());
        prefs.setValue(String.format(PREF_AUTH_ENDPOINT, profile.getName()), authEndpoint);
        prefs.setValue(String.format(PREF_TOKEN_ENDPOINT, profile.getName()), tokenEndpoint);
    }

    static IPreferenceStore preferenceStore() {
        return ConnectorPlugin.getInstance().getPreferenceStore();
    }

}

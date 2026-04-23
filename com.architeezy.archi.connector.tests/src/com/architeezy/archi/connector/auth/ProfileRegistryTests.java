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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jface.preference.PreferenceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProfileRegistryTests {

    private PreferenceStore prefs;

    private TokenStore tokens;

    private ProfileRegistry registry;

    @BeforeEach
    void setUp() {
        prefs = new PreferenceStore();
        var securePrefs = new MemorySecurePreferences();
        tokens = new TokenStore(() -> securePrefs);
        registry = new ProfileRegistry(prefs, tokens);
    }

    @Test
    void emptyStoreCreatesDefaultProfile() {
        var profiles = registry.getProfiles();
        assertEquals(1, profiles.size());
        assertEquals("Architeezy", profiles.get(0).getName());
        assertSame(profiles.get(0), registry.getActiveProfile());
        assertNotNull(registry.getAuthEndpoint("Architeezy"));
        assertNotNull(registry.getTokenEndpoint("Architeezy"));
    }

    @Test
    void defaultProfileIsNotRecreatedAfterReload() {
        registry.removeProfile("Architeezy");
        // Reload from the same prefs: flag "defaultProfileCreated" should suppress recreation.
        var reloaded = new ProfileRegistry(prefs, tokens);
        assertTrue(reloaded.getProfiles().isEmpty());
    }

    @Test
    void addProfilePersistsToPrefsAndMakesFirstProfileActive() {
        registry.removeProfile("Architeezy");

        var p = new ConnectionProfile("corp", "https://corp.example", "client-x");
        registry.addProfile(p, "https://auth.corp/auth", "https://auth.corp/token");

        assertEquals(1, registry.getProfiles().size());
        assertSame(p, registry.getActiveProfile());
        assertEquals("https://auth.corp/auth", registry.getAuthEndpoint("corp"));
        assertEquals("https://auth.corp/token", registry.getTokenEndpoint("corp"));
    }

    @Test
    void profilesPersistAcrossRegistryInstances() {
        registry.removeProfile("Architeezy");
        registry.addProfile(new ConnectionProfile("a", "https://a", "cid-a"),
                "https://a/auth", "https://a/token");
        registry.addProfile(new ConnectionProfile("b", "https://b", "cid-b"),
                "https://b/auth", "https://b/token");
        registry.setActiveProfile("b");

        var reloaded = new ProfileRegistry(prefs, tokens);
        assertEquals(2, reloaded.getProfiles().size());
        assertEquals("b", reloaded.getActiveProfile().getName());
        assertEquals("https://a/auth", reloaded.getAuthEndpoint("a"));
    }

    @Test
    void setActiveProfileIgnoresUnknownName() {
        var before = registry.getActiveProfile();
        registry.setActiveProfile("does-not-exist");
        assertSame(before, registry.getActiveProfile());
    }

    @Test
    void updateProfileClearsTokensWhenServerUrlChanges() {
        registry.removeProfile("Architeezy");
        registry.addProfile(new ConnectionProfile("corp", "https://old.example", "cid"),
                "https://old/auth", "https://old/token");
        tokens.saveTokens("https://old.example", "A", "R", 42L);

        var updated = new ConnectionProfile("corp", "https://new.example", "cid");
        registry.updateProfile("corp", updated, "https://new/auth", "https://new/token");

        assertEquals("https://new.example", registry.getProfiles().get(0).getServerUrl());
        assertEquals("https://new/auth", registry.getAuthEndpoint("corp"));
        assertNull(tokens.getAccessToken("https://old.example"),
                "tokens for the old server URL must be dropped");
    }

    @Test
    void updateProfileKeepsTokensWhenServerUrlUnchanged() {
        registry.removeProfile("Architeezy");
        registry.addProfile(new ConnectionProfile("corp", "https://corp", "cid"),
                "https://corp/auth", "https://corp/token");
        tokens.saveTokens("https://corp", "A", "R", 42L);

        var updated = new ConnectionProfile("corp-renamed", "https://corp", "cid");
        registry.updateProfile("corp", updated, "https://corp/auth2", "https://corp/token2");

        assertEquals("corp-renamed", registry.getActiveProfile().getName());
        assertEquals("https://corp/auth2", registry.getAuthEndpoint("corp-renamed"));
        assertEquals("A", tokens.getAccessToken("https://corp"),
                "rename alone should not clear tokens");
    }

    @Test
    void removeProfileClearsTokensAndRotatesActive() {
        registry.removeProfile("Architeezy");
        registry.addProfile(new ConnectionProfile("a", "https://a", "cid"),
                "https://a/auth", "https://a/token");
        registry.addProfile(new ConnectionProfile("b", "https://b", "cid"),
                "https://b/auth", "https://b/token");
        registry.setActiveProfile("a");
        tokens.saveTokens("https://a", "A", "R", 42L);

        registry.removeProfile("a");

        assertEquals(1, registry.getProfiles().size());
        assertEquals("b", registry.getActiveProfile().getName());
        assertNull(tokens.getAccessToken("https://a"),
                "tokens for the removed profile must be dropped");
    }

    @Test
    void removeLastProfileLeavesNullActive() {
        registry.removeProfile("Architeezy");
        assertNull(registry.getActiveProfile());
        assertTrue(registry.getProfiles().isEmpty());
    }

    @Test
    void findProfileForServerReturnsNullForUnknown() {
        assertNull(registry.findProfileForServer("https://never-added"));
        assertNull(registry.findProfileForServer(null));
    }

    @Test
    void findProfileForServerPrefersConnectedMatch() {
        registry.removeProfile("Architeezy");
        var disconnected = new ConnectionProfile("x1", "https://same", "cid");
        disconnected.setStatus(ProfileStatus.DISCONNECTED);
        var connected = new ConnectionProfile("x2", "https://same", "cid");
        connected.setStatus(ProfileStatus.CONNECTED);
        registry.addProfile(disconnected, "a", "t");
        registry.addProfile(connected, "a", "t");

        assertSame(connected, registry.findProfileForServer("https://same"));
    }

    @Test
    void findProfileForServerFallsBackToFirstMatchWhenNoneConnected() {
        registry.removeProfile("Architeezy");
        var one = new ConnectionProfile("one", "https://same", "cid");
        var two = new ConnectionProfile("two", "https://same", "cid");
        registry.addProfile(one, "a", "t");
        registry.addProfile(two, "a", "t");

        assertSame(one, registry.findProfileForServer("https://same"));
    }

    @Test
    void getProfilesReturnsUnmodifiableList() {
        var profiles = registry.getProfiles();
        var newProfile = new ConnectionProfile("x", "y", "z");
        assertThrows(UnsupportedOperationException.class,
                () -> profiles.add(newProfile));
    }

}

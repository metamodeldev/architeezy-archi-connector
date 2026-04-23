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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

class OAuthManagerPkceTests {

    // Base64URL alphabet without padding (RFC 4648).
    private static final String BASE64URL = "^[A-Za-z0-9_-]+$";

    @Test
    void codeVerifierUsesBase64UrlWithoutPadding() {
        var verifier = OAuthManager.generateCodeVerifier();
        assertTrue(verifier.matches(BASE64URL),
                "verifier must be Base64URL: " + verifier);
        assertFalse(verifier.contains("="), "verifier must not be padded");
    }

    @Test
    void codeVerifierLengthMatches32RandomBytesEncoded() {
        // 32 bytes -> ceil(32 * 4 / 3) = 43 chars without padding
        assertEquals(43, OAuthManager.generateCodeVerifier().length());
    }

    @Test
    void codeVerifierIsUniquePerCall() {
        var set = new HashSet<String>();
        for (int i = 0; i < 32; i++) {
            set.add(OAuthManager.generateCodeVerifier());
        }
        assertEquals(32, set.size(), "verifier should be unique per call");
    }

    @Test
    void codeChallengeIsSha256OfVerifierInBase64Url() throws Exception {
        var verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"; // RFC 7636 example
        var challenge = OAuthManager.generateCodeChallenge(verifier);

        var md = MessageDigest.getInstance("SHA-256");
        var expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
                md.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        assertEquals(expected, challenge);
        assertTrue(challenge.matches(BASE64URL));
        assertFalse(challenge.contains("="));
    }

    @Test
    void codeChallengeIsDeterministic() {
        var verifier = OAuthManager.generateCodeVerifier();
        assertEquals(OAuthManager.generateCodeChallenge(verifier),
                OAuthManager.generateCodeChallenge(verifier));
    }

    @Test
    void codeChallengeDiffersBetweenVerifiers() {
        var a = OAuthManager.generateCodeChallenge(OAuthManager.generateCodeVerifier());
        var b = OAuthManager.generateCodeChallenge(OAuthManager.generateCodeVerifier());
        assertNotEquals(a, b);
    }

}

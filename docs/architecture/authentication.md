# Authentication Specification: Architeezy Archi Connector

## Overview

The plugin authenticates with the Architeezy server using the **OAuth 2.0 Authorization Code Flow
with PKCE** (Proof Key for Code Exchange, RFC 7636). This flow is designed for public clients — ones
that cannot securely store a client secret. No client secret is stored on the user's machine.

## Connection Profile

Authentication is scoped to a **connection profile**, which stores:

- A display name.
- The server base URL.
- The OAuth client ID registered on that server.
- The authorization endpoint URL.
- The token endpoint URL.

Profiles are persisted in the Eclipse Preference Store and survive application restarts. Tokens for
each profile are stored separately in the secure token store (see below).

## Authorization Code Flow with PKCE

### Step-by-Step

1. **Code Verifier Generation** The plugin generates a cryptographically random 32-byte sequence and
   encodes it as a Base64URL string. This becomes the **code verifier** — a secret known only to the
   plugin instance that initiated the login.

2. **Code Challenge Derivation** The plugin computes the SHA-256 hash of the code verifier and
   encodes the result as a Base64URL string. This is the **code challenge**, which is safe to
   transmit publicly.

3. **State Parameter** A random **state** value is generated for CSRF protection. It will be
   validated when the callback is received.

4. **Local Callback Server** The plugin opens a temporary HTTP server bound to `127.0.0.1` on a
   randomly chosen available port. This server's address (`http://127.0.0.1:{port}/callback`) is
   used as the OAuth redirect URI.

5. **Browser Launch** The plugin opens the system browser to the authorization URL with the
   following query parameters:
   - `response_type=code`
   - `client_id` — from the profile
   - `redirect_uri` — the local callback address
   - `code_challenge` — the derived challenge
   - `code_challenge_method=S256`
   - `state` — the random CSRF token

6. **User Authenticates** The user logs in via the browser. The authorization server redirects the
   browser back to `http://127.0.0.1:{port}/callback?code=...&state=...`.

7. **Callback Reception and Validation** The local HTTP server receives the redirect. It validates
   that the returned `state` matches the generated value. If validation passes, it extracts the
   authorization `code`.

8. **Token Exchange** The plugin sends a `POST` request to the token endpoint with:
   - `grant_type=authorization_code`
   - `code` — the authorization code
   - `redirect_uri` — the same local callback URI used in step 5
   - `client_id`
   - `code_verifier` — the original secret value from step 1

   The server verifies the code verifier against the code challenge it stored, confirming that the
   same party that initiated the flow is completing it.

9. **Token Storage** On success, the plugin stores the received access token, refresh token, and
   expiry time in the secure token store keyed by the server URL.

The local callback server is closed immediately after receiving the redirect (or after a 5-minute
timeout if no callback arrives).

## Token Storage

Tokens are stored in **Eclipse Equinox Secure Preferences**, which integrates with the OS keychain
(Keychain on macOS, Credential Manager on Windows, Secret Service on Linux). Tokens are encrypted at
rest.

Storage keys are derived from the server URL to support multiple profiles pointing to different
servers. The access token, refresh token, and expiry timestamp are stored as separate entries.

## Token Lifecycle and Auto-Refresh

Before each API request, the plugin checks whether the access token will expire within the next 60
seconds. If so, it performs a **silent refresh**:

1. A `POST` is sent to the token endpoint with `grant_type=refresh_token` and the stored refresh
   token.
2. The new access and refresh tokens are stored, replacing the previous values.
3. The request that triggered the refresh is retried with the new access token.

**Concurrency safety**: if multiple threads simultaneously detect that the token needs refreshing
for the same server, only one refresh request is made. All waiting callers receive the new token
once it arrives.

If the refresh fails (e.g., the refresh token was revoked), the profile transitions to **Session
Expired** and the user is prompted to re-authenticate.

## Profile Status State Machine

```plain
                        Login initiated
  DISCONNECTED ──────────────────────────► CONNECTING
       ▲                                        │
       │ Logout or                              │ Browser callback received,
       │ refresh failed                         │ tokens stored
       │                                        ▼
  SESSION_EXPIRED ◄────────────────────── CONNECTED
                    Refresh token invalid
```

| Transition                   | Trigger                                 |
| ---------------------------- | --------------------------------------- |
| Disconnected → Connecting    | User clicks "Sign In"                   |
| Connecting → Connected       | Token exchange succeeds                 |
| Connecting → Disconnected    | User cancels login                      |
| Connected → Disconnected     | User clicks "Sign Out" (tokens cleared) |
| Connected → Session Expired  | Refresh token exchange fails            |
| Session Expired → Connecting | User clicks "Sign In" again             |

## Security Properties

- No client secret is stored or transmitted. The PKCE verifier provides equivalent binding security.
- The state parameter prevents cross-site request forgery attacks against the local callback server.
- The callback server is bound only to `127.0.0.1` (loopback), preventing access from other hosts on
  the network.
- Tokens are encrypted at rest using the platform's secure storage facility.
- The access token is transmitted only over HTTPS.

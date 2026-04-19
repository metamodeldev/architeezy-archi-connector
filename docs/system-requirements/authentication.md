# SR-1: Authentication

## Scenarios

### SR-1.1: Login

The system authenticates a user with the Architeezy platform and establishes a session.

#### Functional Requirements

- [FR-1.1](../functional-requirements.md#fr-1-authentication): Authenticate users with the
  Architeezy platform via OAuth 2.0.

#### User Story

As a user, I want to sign in to the Architeezy platform from within the plugin so that I can access
my repositories and models.

#### Steps

1. Open the plugin panel and select a server profile.
   - The system displays the current connection status for the selected profile.
2. Initiate the sign-in action.
   - The system opens the Architeezy OAuth 2.0 authorization page in the default browser.
3. Complete the authorization in the browser and grant access.
   - The system receives the authorization code, exchanges it for tokens, and stores the session
     securely.
   - The connection status updates to show the authenticated user's identity.

#### Edge Cases

- **Authorization Denied**: If the user declines access in the browser, the plugin displays an
  "Authorization cancelled" notification and retains the previous unauthenticated state.
- **Timeout**: If the authorization flow is not completed within a fixed time window, the system
  cancels the pending request and notifies the user.
- **Server Unreachable**: If the Architeezy server cannot be reached during login, an error
  notification is shown and the system remains in the unauthenticated state.

### SR-1.2: Connection Profiles

The system allows the user to configure and switch between multiple named server connection
profiles.

#### Functional Requirements

- [FR-1.2](../functional-requirements.md#fr-1-authentication): Support multiple server connection
  profiles.

#### User Story

As a user, I want to define separate connection profiles for different Architeezy servers so that I
can switch between environments without reconfiguring credentials each time.

#### Steps

1. Open the connection profile management interface.
   - The system displays the list of existing profiles with their names, server URLs, and connection
     status.
2. Add a new profile by providing a name and server URL.
   - The new profile appears in the list with a "Not connected" status.
3. Select a different profile as the active one.
   - The plugin switches context to the selected profile and reflects its connection state.
4. Delete an existing profile.
   - The profile is removed from the list. If it was the active profile, the system falls back to
     another available profile or shows an empty state.

#### Edge Cases

- **Duplicate URL**: If a profile with the same server URL already exists, the system warns the user
  and requires confirmation to proceed.
- **No Profiles**: If all profiles are deleted, the plugin displays an empty state with a prompt to
  add a new profile.

### SR-1.3: Token Refresh

The system maintains the user session by automatically renewing access tokens before they expire.

#### Functional Requirements

- [FR-1.3](../functional-requirements.md#fr-1-authentication): Maintain and automatically refresh
  user session tokens.

#### User Story

As a user, I want my session to remain active during a long work session so that I am not
unexpectedly signed out while working.

#### Steps

1. Continue working in the plugin while the current access token approaches expiry.
   - The system transparently refreshes the access token using the stored refresh token before the
     current token expires.
   - No interruption to the user's workflow occurs.
2. Attempt any repository or sync operation after an automatic refresh has occurred.
   - The operation proceeds normally using the new access token.

#### Edge Cases

- **Refresh Token Expired**: If the refresh token is no longer valid, the system clears the stored
  session, updates the connection status to "Session expired", and prompts the user to sign in
  again.
- **Refresh Request Fails**: If the token refresh network request fails, the system retries once. On
  a second failure, it treats the session as expired.

### SR-1.4: Connection Status

The system displays the current connection state and the authenticated user's identity within the
plugin interface.

#### Functional Requirements

- [FR-1.4](../functional-requirements.md#fr-1-authentication): Display current connection status and
  authenticated user identity.

#### User Story

As a user, I want to see at a glance whether I am connected to a server and under which account, so
that I can act before connection issues affect my work.

#### Steps

1. Observe the connection status indicator in the plugin panel.
   - The indicator reflects one of the defined states: Connected, Connecting, Disconnected, or
     Session Expired.
   - When connected, the user's display name or username is shown alongside the indicator.
2. Become disconnected due to a network outage.
   - The status indicator updates to "Disconnected" without requiring a manual refresh.
3. Restore the network connection.
   - The system automatically attempts to re-establish the session and updates the status indicator
     accordingly.

#### Edge Cases

- **Unknown State**: If the system cannot determine the connection state, it defaults to
  "Disconnected" and logs the event.

## Business Rules

- **OAuth 2.0 Flow**: The plugin uses the Authorization Code flow with PKCE. Credentials are never
  stored; only short-lived access tokens and refresh tokens are retained in secure plugin storage.
- **Profile Isolation**: Each connection profile maintains its own independent set of tokens and
  session state. Signing out of one profile does not affect others.
- **Active Profile**: Exactly one profile can be active at a time. All repository, synchronization,
  and conflict-resolution operations use the credentials of the active profile.
- **Session Expiry**: A session is considered expired when the refresh token is invalid or absent.
  The system must not attempt any authenticated API calls in this state.
- **Status Definitions**:
  - **Connected**: A valid access token is available and the server is reachable.
  - **Connecting**: A token refresh or initial authentication is in progress.
  - **Disconnected**: The server is unreachable but a valid session may still exist.
  - **Session Expired**: The refresh token is invalid or absent; the user must sign in again.

## UI/UX Functional Details

- **Feedback**: A loading indicator is shown during the login flow and token refresh operations
  exceeding 200ms.
- **Status Visibility**: The connection status indicator and authenticated user identity are
  permanently visible in the plugin panel header.
- **Inline Errors**: Authentication errors are shown as inline notifications within the plugin
  panel, not as modal dialogs.
- **Sign-Out Action**: A sign-out option is accessible from the connection status area and clears
  the session for the active profile only.

## Technical Notes

- **Token Storage**: Access and refresh tokens are stored in the Eclipse secure storage (or
  equivalent platform keychain), never in plain-text files.
- **PKCE**: The code verifier is generated per login attempt and discarded after token exchange.
- **Redirect URI**: A local loopback server (`http://localhost:<dynamic-port>`) is used to capture
  the authorization code without requiring a registered redirect URI for each environment.
- **Proactive Refresh**: Tokens are refreshed when fewer than 60 seconds remain before expiry, or
  immediately upon a 401 response from the server.
- **Concurrency**: If multiple operations trigger a token refresh simultaneously, only one refresh
  request is issued; all pending operations wait for the single result.

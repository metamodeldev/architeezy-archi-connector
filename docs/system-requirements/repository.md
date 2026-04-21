# SR-1: Repository Interaction

## Scenarios

### SR-1.1: Manage Connection Profiles

The system allows the user to create and maintain server connection settings within the import or
export interface.

#### Functional Requirements

- [FR-1.1](../functional-requirements.md#fr-1-repository-interaction): Manage server profiles with
  persistent authentication tokens.

#### User Story

As a user, I want to save server addresses as profiles so that I can easily switch between different
environments.

#### Preconditions

- The Import or Export dialog is open.

#### Steps

1. Access the profile management section within the dialog.
   - Dropdown list of existing profiles and a form with current settings are displayed.
2. Add a new profile by specifying a name, server URL, client ID, and OAuth endpoint URLs.
   - Profile is saved and appears in the selection list.
3. Edit an existing profile's fields and save changes.
   - Profile settings are updated and authentication state is reset to "Disconnected".
4. Delete a profile.
   - Profile is removed from the list and its associated tokens are deleted.
5. Set a profile as the default.
   - Profile is marked for automatic selection upon the next dialog opening.

#### Edge Cases

- **Duplicate Profile Name**: Error message is shown and saving is prevented.
- **Invalid URL**: Validation error is displayed if the URL is malformed or unreachable.

### SR-1.2: Authenticate and Logout

The system manages user sessions via OAuth 2.0 Authorization Code Flow with PKCE.

#### Functional Requirements

- [FR-1.1](../functional-requirements.md#fr-1-repository-interaction): Manage server profiles with
  persistent authentication tokens.

#### User Story

As a user, I want to log in once per server and stay authenticated so that I can perform repository
operations without re-entering credentials.

#### Preconditions

- A connection profile is selected.

#### Steps

1. Initiate the login process for the selected profile.
   - System browser opens to the OAuth authorization URL.
   - Profile status changes to "Connecting" and the button label changes to "Cancel".
2. Complete the authentication in the browser.
   - Connection status in the dialog changes to "Connected".
3. Initiate the logout process.
   - Stored tokens are cleared and status changes to "Disconnected".

#### Edge Cases

- **Authentication Cancelled**: Local callback server closes and the profile returns to
  "Disconnected".
- **Token Expiration**: System attempts an automatic refresh. If failed, profile transitions to
  "Session Expired".

### SR-1.3: Import Model

The system enables the user to browse remote models and download one into the local workspace.

#### Functional Requirements

- [FR-1.2](../functional-requirements.md#fr-1-repository-interaction): Navigate the remote project
  hierarchy.
- [FR-1.3](../functional-requirements.md#fr-1-repository-interaction): Import a model from the
  remote repository.

#### Steps

1. Browse the list of remote models.
   - Paginated, searchable table of available models (name, author, date) is displayed.
2. Select a remote model and choose a local save location.
   - File chooser dialog opens for path selection.
3. Confirm the import action.
   - Model is downloaded and opened in the Archi editor.
   - Model is registered as "tracked" with internal properties for URL and modification date.

#### Edge Cases

- **Already Imported**: User is notified and suggested to use the Pull operation.
- **Empty Projects**: Empty state message is shown in the model list.

### SR-1.4: Export Model

The system allows the user to publish a local model to a chosen project in the remote repository.

#### Functional Requirements

- [FR-1.2](../functional-requirements.md#fr-1-repository-interaction): Navigate the remote project
  hierarchy.
- [FR-1.4](../functional-requirements.md#fr-1-repository-interaction): Export a local model to the
  remote repository.

#### User Story

As a user, I want to upload a new local model to the server so that it can be shared with the team.

#### Preconditions

- A connection profile is active and the session is authenticated. Authentication is mandatory for
  Export.
- A local model is currently open in the Archi editor.

#### Steps

1. Initiate the Export action for the current model.
   - "Next" button remains disabled until the profile status is "Connected".
2. Browse and select a destination project on the server.
   - Searchable list of remote projects is displayed.
3. Confirm the export action.
   - Model is serialized, uploaded, and linked to the new remote entry.
   - Local model becomes "tracked".

#### Edge Cases

- **Access Denied**: Permission error is displayed and the model remains untracked.
- **Upload Failure**: Operation is aborted with no partial data committed to the server.

## Business Rules

### Data Integrity and Tracking

- **Atomic Operations**: Import and Export must be atomic; failed transfers must not result in
  partial models or broken tracking links.
- **Model Tracking**: Tracking state is stored directly inside the `.archimate` file (not in sidecar
  files) via two properties: Server URL and Last Modification Date.
- **Snapshot on Sync**: A base snapshot reflecting the exact synced state must be saved immediately
  after every successful Import, Export, or Pull.
- **Automatic Naming**: Exported files are auto-named as `{modelName}-{date}-{time}.archimate` to
  avoid collisions.

### Security and Authentication

- **Authentication Requirements**: Export always requires authentication. Import browsing may be
  public, but downloading model content always requires a session.
- **Secure Storage**: Access and refresh tokens are stored in the OS keychain via Eclipse Equinox
  Secure Preferences.
- **Session Exclusivity**: Only one server profile can be active for a specific operation at a time.
- **Profile States**:
  - **Disconnected**: No valid tokens exist.
  - **Connecting**: Browser flow is active.
  - **Connected**: Valid tokens are ready for use.
  - **Session Expired**: Refresh token failed; re-authentication required.

### Profile Configuration

- **Persistence**: Profiles and tokens must persist across application restarts.
- **Profile Structure**: Profiles must include Name, Server URL, Client ID, Authorization Endpoint,
  and Token Endpoint.
- **Default Profile**: A default production profile is created on the first launch.
- **Reset on Edit**: Changing a profile's URL or endpoints automatically resets its authentication
  state.

## UI/UX

- **Profile Selection**: A dropdown menu for profiles is present in both Import and Export wizards.
  New profiles can be created and saved from within the wizard without leaving the flow.
- **Visual Status**: The authentication status of the selected profile is shown in a dedicated
  section of the wizard page, along with action buttons (Sign In / Sign Out / Cancel).
- **Import Wizard Authentication**: The Import wizard does not require authentication to open; the
  profile selection page allows browsing with an unauthenticated profile. The Next button is always
  enabled. If the subsequent model download fails due to missing authentication, the user is
  prompted at that point.
- **Export Wizard Authentication**: The Export wizard requires authentication before the user can
  proceed to the project selection page. The Next button is disabled until the profile status is
  "Connected".
- **Progress Feedback**: A progress bar and a "Cancel" button are displayed during model upload and
  download operations, which run on a background thread.

## Technical Notes

- **Authentication**: Authentication is implemented via OAuth 2.0 Authorization Code Flow with PKCE.
  No client secret is stored on the user's machine.
- **Token Storage**: Access and refresh tokens are stored in Eclipse Equinox Secure Preferences,
  which use the OS keychain on supported platforms. Tokens are keyed by a hash of the server URL.
- **Communication**: All repository interactions are performed via the Architeezy REST API over
  HTTPS using HAL+JSON as the response format.
- **Tracking Metadata**: The server URL and last modification timestamp are stored as named
  properties directly inside the `.archimate` file. The server model ID is derivable from the last
  path segment of the server URL property.
- **Base Snapshot Location**: Snapshots are stored in the plugin's Eclipse state directory (managed
  by the platform, separate from the workspace). The filename is derived from the model ID. Writes
  are performed atomically via a temporary file and a file-system rename.

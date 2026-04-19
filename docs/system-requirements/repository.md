# SR-2: Repository

## Scenarios

### SR-2.1: Browse Repository

The system displays the remote repository structure, allowing the user to explore the available
model hierarchy.

#### Functional Requirements

- [FR-2.1](../functional-requirements.md#fr-2-repository): Browse the remote repository structure.

#### User Story

As a user, I want to navigate the repository tree so that I can find models organized within folders
or groups.

#### Preconditions

- A connection profile is active and the session is authenticated.

#### Steps

1. Open the repository browser panel.
   - The system fetches and displays the top-level repository structure (folders, groups, or
     projects) from the server.
   - A loading indicator is shown while the data is being retrieved.
2. Expand a folder or group.
   - The system fetches and displays its child items.
3. Collapse a previously expanded folder.
   - The child items are hidden; no server request is made.

#### Edge Cases

- **Empty Repository**: If the repository contains no items accessible to the user, an "Empty
  repository" message is displayed.
- **Fetch Failure**: If the repository structure cannot be loaded, an error notification is shown
  with a retry option. The previously loaded state (if any) is preserved.
- **Permission Boundary**: Items the user lacks access to are not shown; the server enforces access
  control.

### SR-2.2: Search Models

The system filters the repository contents in real-time based on a user-provided query.

#### Functional Requirements

- [FR-2.2](../functional-requirements.md#fr-2-repository): Search for models within the repository.

#### User Story

As a user, I want to search for a model by name so that I can locate it quickly without manually
browsing the full hierarchy.

#### Preconditions

- A connection profile is active and the session is authenticated.

#### Steps

1. Enter a query in the search field of the repository browser.
   - The system sends the query to the server and displays matching models.
   - Results appear within the repository browser, ordered by relevance or name.
2. Clear the search field.
   - The repository browser returns to displaying the full repository structure.

#### Edge Cases

- **No Matches**: If no models match the query, a "No results found" message is displayed.
- **Fetch Failure**: If the search request fails, an error notification is shown with a retry
  option. The search field retains its current value.
- **Empty Query**: Submitting an empty query clears the search results and restores the full
  repository view.

### SR-2.3: View Model Metadata

The system displays descriptive metadata for a selected model without loading its full content.

#### Functional Requirements

- [FR-2.3](../functional-requirements.md#fr-2-repository): View model metadata.

#### User Story

As a user, I want to see information about a model — such as its description, author, and last
modified date — before deciding to import it.

#### Preconditions

- A connection profile is active and the session is authenticated.

#### Steps

1. Select a model in the repository browser.
   - The system displays a metadata panel showing at minimum: model name, description, author, and
     last modified date.
   - A loading indicator is shown while metadata is being retrieved.

#### Edge Cases

- **Partial Metadata**: If some metadata fields are absent from the server response, those fields
  are shown as empty or with a "Not available" placeholder.
- **Fetch Failure**: If metadata cannot be retrieved, an error notification is shown and the
  metadata panel displays a generic error state.

### SR-2.4: Import Model

The system downloads a model from the repository and adds it to the local Archi workspace.

#### Functional Requirements

- [FR-2.4](../functional-requirements.md#fr-2-repository): Import a model from the repository to the
  local workspace.

#### User Story

As a user, I want to import a model from the repository so that I can view and edit it locally in
Archi.

#### Preconditions

- A connection profile is active and the session is authenticated.
- The target model is visible in the repository browser.

#### Steps

1. Select a model in the repository browser and initiate the import action.
   - The system prompts the user to choose a local destination folder if required.
2. Confirm the import.
   - The system downloads the model content from the server and creates the model in the local Archi
     workspace.
   - A progress indicator is shown during the download.
   - Upon completion, the model appears in the local workspace and is registered as a tracked model
     linked to its remote counterpart.

#### Edge Cases

- **Already Imported**: If the model is already present in the local workspace and linked to the
  same remote reference, the system warns the user and asks for confirmation before overwriting.
- **Insufficient Permissions**: If the user lacks read access to the model, an error notification is
  shown and no local file is created.
- **Download Failure**: If the download fails partway through, any partially created local file is
  removed and an error notification is shown with a retry option.
- **Disk Space**: If the local storage is insufficient, the system shows an error and aborts the
  import without leaving partial files.

### SR-2.5: Publish Model

The system uploads a local model to the repository, making it available on the server for the first
time.

#### Functional Requirements

- [FR-2.5](../functional-requirements.md#fr-2-repository): Publish a local model to the repository.

#### User Story

As a user, I want to publish a locally created model to the repository so that my team can access
it.

#### Preconditions

- A connection profile is active and the session is authenticated.
- The model to be published exists in the local Archi workspace and is not yet linked to a remote
  repository entry.

#### Steps

1. Select a local model and initiate the publish action.
   - The system displays a publish dialog prompting for the target location in the repository
     (folder or group) and an optional description.
2. Confirm the publish settings.
   - The system uploads the model to the specified location in the repository.
   - A progress indicator is shown during the upload.
   - Upon completion, the model is linked to its new remote counterpart and tracked for future
     synchronization.

#### Edge Cases

- **Name Conflict**: If a model with the same name already exists in the target location, the system
  warns the user and requires confirmation before proceeding.
- **Insufficient Permissions**: If the user lacks write access to the target location, an error
  notification is shown and the publish is aborted.
- **Upload Failure**: If the upload fails, no partial model is created on the server and an error
  notification is shown with a retry option.

## Business Rules

- **Tracking**: A model is considered **tracked** once it has been imported or published. Tracked
  models have a persistent link between the local workspace entry and the remote repository
  identifier.
- **Untracked Models**: Local models that have never been published, and remote models that have
  never been imported, are untracked. Synchronization operations are only available for tracked
  models.
- **Search Scope**: The search query is sent to the server; local workspace models are not included
  in repository search results.
- **Access Control**: Repository access control is enforced server-side. The plugin does not
  implement or cache permission rules.
- **Import Atomicity**: An import operation is all-or-nothing. A failed import must not leave a
  partial model in the local workspace.

## UI/UX Functional Details

- **Feedback**: A loading indicator is shown for any repository fetch or import/publish operation
  exceeding 200ms.
- **Repository Tree**: The repository browser uses a tree view with lazy-loading of child nodes on
  expansion.
- **Search Placement**: The search field is persistent at the top of the repository browser and
  takes focus immediately when the panel is opened.
- **Action Availability**: Import and publish actions are context-sensitive: import is available
  only for remote, untracked models; publish is available only for local, untracked models.
- **Progress Reporting**: Long-running import and publish operations display a progress bar with a
  cancel option.

## Technical Notes

- **API**: All repository operations use the Architeezy REST API authenticated with the active
  profile's access token.
- **Lazy Loading**: Child nodes in the repository tree are fetched on demand when a parent node is
  expanded for the first time. Subsequent expansions use the cached response for the duration of the
  session.
- **Search Debounce**: Search queries are debounced by 300ms to avoid sending a request on every
  keystroke.
- **Import Format**: The server returns model data in the ArchiMate Exchange Format (AEF). The
  plugin uses the Archi model import API to load it into the workspace.
- **Tracking Storage**: The remote-to-local model link is stored in the plugin's workspace metadata,
  not in the model file itself, to avoid modifying the canonical model format.

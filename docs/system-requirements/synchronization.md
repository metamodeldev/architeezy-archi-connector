# SR-2: Model Synchronization

## Scenarios

### SR-2.1: Monitor for Updates

The system periodically checks the server to identify if the remote version of a tracked model is
newer than the local version.

#### Functional Requirements

- [FR-2.1](../functional-requirements.md#fr-2-model-synchronization): Monitor the server for model
  updates and notify the user of available changes.

#### User Story

As a user, I want to be notified when remote changes are available so that I can pull them before my
local copy becomes too outdated.

#### Preconditions

- A tracked model is open in the workspace.
- A connection profile is active and authenticated.

#### Steps

1. Keep the model open in Archi while connected to the server.
   - The system periodically checks the server version in the background without user interaction.
2. Observe the update indicator in the model tree.
   - If a newer version is available on the server, a down-arrow icon (↓) appears next to the model
     name in the Archi model tree.
   - Hovering over the model name shows a tooltip with the server version date and the date of the
     last local pull.

The update indicator remains visible until the user performs a Pull. It disappears if the server
version is no longer newer than the local tracking timestamp (e.g., after a successful pull by
another process).

#### Edge Cases

- **Offline State**: If the server is unreachable, the system silently skips the check for that
  cycle and retries at the next interval. No error is shown to the user.
- **Session Expired**: If the access token cannot be refreshed, the system skips the check silently.
  The user will encounter the authentication error the next time they initiate an explicit
  operation.

### SR-2.2: Pull Modifications

The system retrieves remote changes and integrates them into the local model to ensure it reflects
the latest server state.

#### Functional Requirements

- [FR-2.2](../functional-requirements.md#fr-2-model-synchronization): Pull remote modifications and
  integrate them into the local model.

#### User Story

As a user, I want to pull changes made by others so that my local model reflects the latest team
progress.

#### Preconditions

- The model is tracked and a newer version exists on the server.
- The session is authenticated.

#### Steps

1. Initiate the Pull operation from the toolbar.
   - A progress dialog appears while the system downloads the latest model content from the server.
2. Observe the outcome once the operation completes.
   - If only the remote version has changed: the model is updated in-place. Open diagram editors and
     undo history are preserved.
   - If only local changes exist: an informational dialog appears, stating that no remote changes
     are available and that local changes can be pushed when ready.
   - If both the local model and the remote version have changed: non-conflicting changes are
     applied automatically. If conflicts are detected, the Conflict Resolution Dialog opens.
3. Verify that the update has been applied.
   - The update indicator (↓) disappears from the model name in the model tree.

#### Non-Destructive Pull

The pull operation updates the live Archi model in-place rather than closing and reopening the file.
This preserves the user's open diagram tabs and undo history. The operation is performed as a single
batch update to maintain model consistency.

#### Edge Cases

- **Unsaved Changes**: If the local model has unsaved modifications, the system prompts the user
  before proceeding. Proceeding will discard the unsaved changes.
- **Integrity Error**: If the downloaded data cannot be parsed, the system aborts the operation and
  leaves the local model unchanged.

### SR-2.3: Push Modifications

The system uploads local changes to the remote repository, replacing the current server version.

#### Functional Requirements

- [FR-2.3](../functional-requirements.md#fr-2-model-synchronization): Push local modifications to
  the remote repository.

#### User Story

As a user, I want to push my local modifications to the server so that they are shared with other
team members.

#### Preconditions

- The model is tracked and has local changes.
- The local base version matches the current server version (no updates have been made on the server
  since the last sync).

#### Steps

1. Initiate the Push operation from the synchronization toolbar.
   - A progress dialog appears while the system verifies the server version and uploads the model
     content.
2. Confirm the push completed successfully.
   - The progress dialog closes without an error message.

#### Edge Cases

- **Version Mismatch**: If the server has a newer version (someone else pushed in the meantime), the
  system blocks the Push and requires the user to Pull first.
- **Network Failure**: If the upload is interrupted, the server is not updated and the local model
  remains in its original state. The user can retry the Push.

## Business Rules

### Sync Scenario Classification

Before performing any merge operation during a Pull, the system classifies the state of the model
into one of four scenarios by comparing the serialized local model, the stored base snapshot, and
the downloaded remote content:

| Scenario    | Local vs. Base | Remote vs. Base | System Action                                          |
| ----------- | -------------- | --------------- | ------------------------------------------------------ |
| Up to Date  | Unchanged      | Unchanged       | Clear the update indicator; no further action.         |
| Simple Pull | Unchanged      | Changed         | Apply remote content directly to the local model.      |
| Simple Push | Changed        | Unchanged       | Inform the user that remote is unchanged; no pull.     |
| Diverged    | Changed        | Changed         | Perform a 3-way merge; open conflict dialog if needed. |

This classification ensures that a Pull never silently overwrites local work.

### Rules

- **Pull Requirement**: A Push operation is only allowed if the user's local model is based on the
  latest version available on the server.
- **Tracking Persistence**: The relationship between a local model and its remote counterpart
  persists across application sessions. The tracking data is stored in the model file itself and
  survives file copies and moves.
- **Merge Priority**: Automatic merging is only applied to non-overlapping changes; overlapping
  changes must be resolved by the user.
- **Snapshot Integrity**: The base snapshot must always be updated atomically alongside the tracking
  metadata after each successful sync. The two must remain consistent.
- **Background Check Scope**: The background update check covers all tracked models that are
  currently open in the workspace. Closed models are not checked.

## UI/UX

- **Status Indicator**: A down-arrow icon (↓) appended to the model name in the Archi model tree
  indicates that a newer version is available on the server. No icon means the model is in sync or
  untracked.
- **Tooltip**: Hovering over a model with the update indicator shows a tooltip with the server
  version date and the locally tracked date.
- **Pull Toolbar Button**: The Pull button in the main toolbar is enabled only when the active model
  has a pending update. It is automatically re-evaluated when the update state changes.
- **Operation Feedback**: A progress dialog with a "Cancel" option is shown for Pull and Push
  operations. Both run on a background thread to avoid blocking the UI.
- **Notification Persistence**: The update indicator remains visible until the user performs a
  successful Pull.

## Technical Notes

- **Sync Scenario Detection**: The system serializes the live local model to bytes and compares
  against the stored base snapshot and downloaded remote bytes using a binary equality check. This
  approach is simple and reliable but is sensitive to any change that affects the XMI serialization,
  including purely structural reorderings.
- **3-Way Merge Engine**: The system uses the Eclipse EMF Compare framework with a three-scope
  comparison (local, remote, origin/base) to compute structural diffs at the model element level.
- **Metadata Storage**: Synchronization metadata is stored as named properties in the `.archimate`
  model file, not in a separate database or hidden file. The server model ID is the last path
  segment of the server URL property.
- **Background Check Interval**: The default background check interval is 5 minutes. This is
  configurable in the plugin preferences.

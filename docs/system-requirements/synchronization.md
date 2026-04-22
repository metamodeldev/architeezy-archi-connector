# SR-2: Model Synchronization

## Scenarios

### SR-2.1: Monitor for Updates

The system periodically checks the server to identify if the remote version of a tracked model is
newer than the local version.

#### Functional Requirements

- [FR-2.1](../functional-requirements.md#fr-2-model-synchronization): Monitor the server for model
  updates and notify the user.

#### Steps

1. Keep a tracked model open in the Archi workspace while connected.
   - Background version checks are performed periodically.
2. Observe the update indicator in the model tree.
   - A down-arrow icon (↓) appears next to the model name if a newer version exists on the server.
3. Hover over the model name in the tree.
   - A tooltip displays the server version date and the date of the last local sync.

#### Edge Cases

- **Offline State**: Background checks are skipped silently; no error is shown to the user.
- **Session Expired**: Version checks are skipped until the user re-authenticates during an explicit
  operation.

### SR-2.2: Pull Modifications

The system retrieves remote changes and integrates them into the local model.

#### Functional Requirements

- [FR-2.2](../functional-requirements.md#fr-2-model-synchronization): Pull remote modifications and
  integrate them.

#### Steps

1. Initiate the Pull operation from the toolbar.
   - Progress dialog appears and the latest model content is downloaded.
2. Review the result of the automatic integration.
   - If only the remote changed: the model is updated in-place; diagram editors and undo history are
     preserved.
   - If both local and remote changed: non-conflicting changes are applied; the Conflict Resolution
     Dialog opens if overlaps are detected.
   - If only local changes exist: an informational dialog confirms that no remote changes are
     available.
3. Verify the model state in the tree.
   - Update indicator (↓) disappears.

#### Edge Cases

- **Unsaved Changes**: A confirmation prompt appears; proceeding discards unsaved local
  modifications.
- **Integrity Error**: Operation is aborted if downloaded data is corrupted; the local model remains
  unchanged.

### SR-2.3: Push Modifications

The system uploads local changes to the remote repository, replacing the current server version.

#### Functional Requirements

- [FR-2.3](../functional-requirements.md#fr-2-model-synchronization): Push local modifications to
  the remote repository.

#### Steps

1. Initiate the Push operation from the toolbar.
   - Progress dialog appears while the system verifies versions and uploads content.
2. Confirm the operation completion.
   - Progress dialog closes and the local model is marked as in-sync with the server.

#### Edge Cases

- **Version Mismatch**: Push is blocked if the server has a newer version; the user is prompted to
  Pull first.
- **Network Failure**: Upload is aborted; the server version remains unchanged and the user can
  retry.

## Business Rules

### Synchronization and Merging

- **Sync Scenario Classification**: Before any Pull, the state is classified by comparing the local
  model, the base snapshot, and the remote content:
  - **Up to Date**: No changes; indicator is cleared.
  - **Simple Pull**: Only remote changed; content is applied directly.
  - **Simple Push**: Only local changed; no pull action is taken.
  - **Diverged**: Both changed; a 3-way merge is initiated.
- **Merge Priority**: Automatic merging applies only to non-overlapping changes. Overlapping changes
  (conflicts) must be resolved manually by the user.
- **Push Pre-condition**: A Push is only permitted if the local model is based on the latest server
  version (no "pending updates").
- **Non-Destructive Integration**: Pull operations update the live model in-place to preserve open
  diagram tabs and the undo history.

### Data Integrity

- **Atomic Metadata Update**: The base snapshot, server URL, and last modification timestamp must be
  updated as a single atomic operation after every successful sync.
- **Snapshot Consistency**: The base snapshot must always reflect the exact state of the model as it
  exists on the server at the time of the last sync.
- **Tracking Persistence**: Synchronization metadata is stored inside the `.archimate` file. The
  tracking survives file moves, renaming, or application restarts.

### Monitoring and UI

- **Background Check Scope**: Only models currently open in the Archi workspace are monitored for
  updates.
- **Check Interval**: Version checks occur every 5 minutes by default (configurable in preferences).
- **Indicator Persistence**: The update indicator (↓) remains visible until a successful Pull is
  completed or the server version is no longer newer.
- **Action Availability**: The Pull and Push buttons are enabled or disabled based on the current
  sync state and authentication status.

## UI/UX

- **Visual Cues**: The down-arrow (↓) icon is the primary indicator for pending remote changes.
- **Tooltips**: Detailed sync information (dates/versions) is provided via tooltips on the model
  tree items.
- **Feedback**: Progress bars and "Cancel" buttons are required for all Pull and Push operations to
  prevent UI freezing.

## Technical Notes

- **Change Detection**: The system uses binary equality checks between the live model, the snapshot,
  and the remote data to classify sync scenarios.
- **Merge Engine**: Structural diffs and 3-way merges are handled by the Eclipse EMF Compare
  framework.
- **Metadata Storage**: All tracking properties are stored as standard Archi named properties. The
  server model ID is derived from the server URL property.
- **Atomic Writes**: Snapshots are saved to the plugin's state directory using a temporary file and
  rename strategy to prevent data loss.

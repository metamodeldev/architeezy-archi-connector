# SR-3: Synchronization

## Scenarios

### SR-3.1: Push Changes

The system uploads local changes of a tracked model to the remote repository.

#### Functional Requirements

- [FR-3.1](../functional-requirements.md#fr-3-synchronization): Push local changes to the
  repository.

#### User Story

As a user, I want to push my local changes to the repository so that my team can see the latest
version of the model.

#### Preconditions

- A connection profile is active and the session is authenticated.
- The model is tracked and has local changes not yet present in the remote repository.

#### Steps

1. Select a tracked model with local changes and initiate the push action.
   - The system calculates the diff between the local model state and the last known remote state.
2. Review the summary of outgoing changes and confirm the push.
   - The system uploads the changes to the remote repository.
   - A progress indicator is shown during the upload.
   - Upon completion, the local model's sync baseline is updated to match the newly pushed state.
   - The synchronization status for the model updates to "Up to date".

#### Edge Cases

- **No Local Changes**: If the local model has no changes relative to the last sync baseline, the
  push action is disabled or shows a "Nothing to push" notification.
- **Remote Has Newer Changes**: If the remote repository contains changes not present locally since
  the last sync, the system blocks the push and prompts the user to pull first.
- **Upload Failure**: If the push fails, the local model is not modified and an error notification
  is shown with a retry option.
- **Session Expired During Push**: If the session expires mid-operation, the push is aborted, the
  local model is restored to its pre-push state, and the user is prompted to sign in again.

### SR-3.2: Pull and Integrate Remote Changes

The system downloads remote changes and integrates them into the local model.

#### Functional Requirements

- [FR-3.2](../functional-requirements.md#fr-3-synchronization): Pull and integrate remote changes
  into the local model.

#### User Story

As a user, I want to pull the latest version of a model from the repository so that I am working
with my team's most recent changes.

#### Preconditions

- A connection profile is active and the session is authenticated.
- The model is tracked and a newer version exists in the remote repository.

#### Steps

1. Select a tracked model and initiate the pull action.
   - The system fetches the latest changes from the remote repository.
   - A progress indicator is shown during the download.
2. Observe the result after the pull completes.
   - If there are no conflicting changes, the remote changes are integrated into the local model
     automatically.
   - The sync baseline is updated to the pulled remote state.
   - The synchronization status updates to "Up to date".
   - If conflicting changes are detected, the system pauses integration and opens the conflict
     resolution interface (see SR-4).

#### Edge Cases

- **No Remote Changes**: If the remote model has no changes since the last sync, the system shows a
  "Already up to date" notification.
- **Download Failure**: If the fetch fails, the local model is not modified and an error
  notification is shown with a retry option.
- **Large Changeset**: For changesets exceeding a defined threshold, the system shows a warning
  about the size of the incoming changes before applying them.

### SR-3.3: Synchronization Status

The system provides a per-model indicator of the current synchronization state relative to the
remote repository.

#### Functional Requirements

- [FR-3.3](../functional-requirements.md#fr-3-synchronization): Display synchronization status for
  each tracked model.

#### User Story

As a user, I want to see at a glance whether each of my tracked models is up to date, ahead, or
behind the remote repository.

#### Steps

1. Open the tracked models list in the plugin panel.
   - Each tracked model displays a synchronization status badge indicating its current state.
2. Make local changes to a tracked model.
   - The model's status badge updates to reflect that it has unpushed local changes.
3. Pull or push changes successfully.
   - The model's status badge updates to "Up to date".

#### Edge Cases

- **Status Unknown**: If the system cannot determine the sync state (e.g., the server is
  unreachable), the status badge shows "Unknown" and a warning icon is displayed.
- **Multiple Models**: The status is tracked and displayed independently for each tracked model.

### SR-3.4: Remote Update Notifications

The system notifies the user when new remote changes are available for a tracked model.

#### Functional Requirements

- [FR-3.4](../functional-requirements.md#fr-3-synchronization): Notify users when remote updates are
  available for a tracked model.

#### User Story

As a user, I want to be notified when a teammate has pushed changes to a model I am working on so
that I can pull them before my local work diverges too far.

#### Preconditions

- A connection profile is active and the session is authenticated.
- At least one tracked model exists in the local workspace.

#### Steps

1. Work in the plugin or Archi while the system polls for remote changes in the background.
   - The system periodically checks whether the remote repository has changes for tracked models.
2. A remote change is detected.
   - The affected model's synchronization status updates to indicate that remote updates are
     available.
   - A non-blocking notification is shown prompting the user to pull the changes.

#### Edge Cases

- **Poll Failure**: If a polling request fails, the system retries after the next interval without
  showing an error. If failures persist beyond a defined threshold, the model's status is set to
  "Unknown".
- **Multiple Updates**: If several tracked models receive remote changes between polls, all affected
  models are notified in a single summary notification.

### SR-3.5: Network Interruption Handling

The system preserves the integrity of the local model when a network interruption occurs during a
synchronization operation.

#### Functional Requirements

- [FR-3.5](../functional-requirements.md#fr-3-synchronization): Preserve local model integrity when
  a network interruption occurs during sync.

#### User Story

As a user, I want my local model to remain intact and consistent if my connection drops during a
push or pull, so that I do not lose work or end up with a corrupted model.

#### Steps

1. Begin a push or pull operation.
   - The system starts the operation and shows a progress indicator.
2. A network interruption occurs mid-operation.
   - The operation is aborted.
   - The local model is rolled back to its pre-operation state.
   - An error notification is shown explaining that the operation was interrupted and the local
     model was preserved.
3. Restore the network connection and retry the operation.
   - The system performs the operation from the beginning using the preserved local state.

#### Edge Cases

- **Interruption During Pull Integration**: If the interruption occurs after the download but during
  local integration, the model is restored to the pre-pull state via rollback.
- **Rollback Failure**: If the rollback itself fails, the system marks the local model as
  potentially inconsistent, prevents further sync operations, and prompts the user to re-import the
  model.

## Business Rules

- **Sync Baseline**: Each tracked model maintains a sync baseline — a snapshot of the model state at
  the time of the last successful push or pull. Push and pull operations compute diffs relative to
  this baseline.
- **Push Prerequisite**: A push is only allowed if the local sync baseline matches the current
  remote HEAD. If the remote has advanced, the user must pull first.
- **Pull Conflict Handoff**: When a pull detects conflicting changes, the integration is paused. The
  model remains in its pre-pull state until the user resolves all conflicts (see SR-4).
- **Status Definitions**:
  - **Up to date**: Local model matches the remote HEAD.
  - **Local changes**: The local model has changes not present in the remote HEAD.
  - **Remote changes available**: The remote HEAD has changes not present in the local model.
  - **Diverged**: Both the local model and the remote HEAD have changes since the last sync
    baseline.
  - **Unknown**: The system cannot determine the sync state.
- **Operation Atomicity**: Push and pull operations are atomic. A failed operation must not leave
  the local model or the sync baseline in a partially modified state.
- **Polling Interval**: The system polls for remote changes at a configurable interval. The default
  interval is 5 minutes.

## UI/UX Functional Details

- **Feedback**: A progress indicator is shown for any push or pull operation exceeding 200ms.
- **Status Badges**: Synchronization status is represented by consistent icons and color coding
  across the tracked models list.
- **Non-Blocking Notifications**: Remote update notifications appear as non-blocking banners and do
  not interrupt the user's current workflow.
- **Cancel Option**: Long-running push and pull operations expose a cancel action. Cancellation
  triggers the same rollback behavior as a network interruption.
- **Retry Affordance**: Error notifications for failed sync operations include a "Retry" action that
  re-initiates the same operation.

## Technical Notes

- **Diff Algorithm**: Diffs are computed at the model object level using unique object identifiers.
  Only changed, added, and deleted objects are included in push payloads.
- **Rollback Mechanism**: Before applying any changes to the local model, the system saves a
  temporary snapshot. On failure, the snapshot is restored and then discarded.
- **Polling**: Remote change polling uses lightweight HEAD or ETag requests to avoid transferring
  full model payloads on each poll cycle.
- **Concurrency**: Only one synchronization operation (push or pull) may be in progress at a time
  per tracked model. Concurrent attempts on the same model are queued.
- **Baseline Storage**: The sync baseline is stored in the plugin's workspace metadata alongside the
  remote-to-local model tracking link.

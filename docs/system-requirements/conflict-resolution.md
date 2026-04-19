# SR-4: Conflict Resolution

## Scenarios

### SR-4.1: Automatic Merge

The system automatically integrates concurrent, non-overlapping changes from both the local model
and the remote repository without user intervention.

#### Functional Requirements

- [FR-4.1](../functional-requirements.md#fr-4-conflict-resolution): Automatically merge concurrent,
  non-overlapping changes based on model object identity.

#### User Story

As a user, I want the system to automatically merge changes that do not conflict so that I only need
to intervene when there is a genuine ambiguity.

#### Preconditions

- A pull operation has been initiated on a tracked model.
- Both the local model and the remote repository have changes since the last sync baseline.

#### Steps

1. The system computes the diff between the local model, the remote model, and the sync baseline.
   - Local-only changes (objects added, modified, or deleted locally but not remotely) are
     identified.
   - Remote-only changes (objects added, modified, or deleted remotely but not locally) are
     identified.
   - Overlapping changes (the same object modified in both local and remote) are identified as
     conflicts.
2. The system applies all non-overlapping changes automatically.
   - Local-only changes are retained in the merged result.
   - Remote-only changes are integrated into the local model.
3. The pull operation completes without user intervention if no conflicts are present.
   - The sync baseline is updated to the merged state.
   - The synchronization status updates to "Up to date".

#### Edge Cases

- **No Changes on Either Side**: If neither side has changes since the baseline, the merge is a
  no-op and the system shows "Already up to date".
- **All Changes Conflict**: If every changed object has a conflict, the system proceeds to the
  conflict resolution interface (SR-4.2) without applying any automatic changes.

### SR-4.2: Conflict Detection

The system detects and presents all conflicting modifications to the user before allowing the merge
to complete.

#### Functional Requirements

- [FR-4.2](../functional-requirements.md#fr-4-conflict-resolution): Detect and surface conflicting
  modifications to the same model objects.

#### User Story

As a user, I want to see a clear list of all conflicts so that I can understand what needs to be
resolved before the merge is finalized.

#### Preconditions

- A pull operation has identified at least one conflicting change during the merge computation.

#### Steps

1. The system pauses the merge and opens the conflict resolution interface.
   - A list of all conflicting model objects is displayed, grouped by object type (element,
     relationship, property).
   - Each entry shows the object identifier and the nature of the conflict (e.g., both sides
     modified the same property, one side deleted while the other modified).
2. Select a conflict entry from the list.
   - The diff view for that object is shown (see SR-4.3).
3. Observe the overall resolution progress.
   - The conflict list indicates which conflicts have been resolved and which remain pending.
   - The "Complete Merge" action is disabled until all conflicts are resolved.

#### Edge Cases

- **Large Conflict Set**: If the number of conflicts exceeds a defined threshold, the system
  displays a warning about the volume of conflicts and offers a "Select all: keep local" or "Select
  all: keep remote" shortcut.

### SR-4.3: Diff View

The system displays a side-by-side comparison of local and remote versions of a conflicting object.

#### Functional Requirements

- [FR-4.3](../functional-requirements.md#fr-4-conflict-resolution): Provide a diff view comparing
  local and remote versions of conflicting objects.

#### User Story

As a user, I want to see exactly what changed on each side for a conflicting object so that I can
make an informed resolution decision.

#### Preconditions

- The conflict resolution interface is open and at least one conflict entry is selected.

#### Steps

1. Select a conflicting object in the conflict list.
   - The diff view displays two panels side-by-side: the local version on the left and the remote
     version on the right.
   - Changed fields or properties are visually highlighted.
   - The sync baseline version is available as an optional reference view.
2. Navigate between conflicting objects using the conflict list or navigation controls within the
   diff view.
   - The diff view updates to show the selected object's versions.

#### Edge Cases

- **Deleted Object**: If one side has deleted the object while the other has modified it, the diff
  view shows the modified version against an empty "deleted" state on the opposite side.
- **Binary or Unsupported Properties**: Properties that cannot be meaningfully diffed (e.g., large
  embedded resources) are displayed as "Modified" without a detailed diff, and the user is prompted
  to choose local or remote.

### SR-4.4: Manual Conflict Resolution

The system allows the user to resolve each conflict by selecting either the local or remote version
of the affected object.

#### Functional Requirements

- [FR-4.4](../functional-requirements.md#fr-4-conflict-resolution): Allow the user to resolve each
  conflict by accepting the local or remote version.

#### User Story

As a user, I want to choose which version to keep for each conflicting object so that the final
merged model reflects deliberate decisions.

#### Preconditions

- The conflict resolution interface is open and at least one conflict is unresolved.

#### Steps

1. Select a conflicting object and review the diff view.
2. Choose to accept the local version or the remote version for the selected object.
   - The conflict entry is marked as resolved with the chosen side indicated.
   - The diff view advances to the next unresolved conflict automatically (if any).
3. Repeat for all remaining conflicts.
   - Once all conflicts are resolved, the "Complete Merge" action becomes available.
4. Confirm the merge completion.
   - The system applies all resolutions: non-overlapping automatic changes and manual resolution
     choices are combined into the final merged model.
   - The sync baseline is updated to the merged state.
   - The conflict resolution interface closes.
   - The synchronization status updates to "Up to date".

#### Edge Cases

- **Change Resolution**: The user may change a previously made resolution before completing the
  merge. The new choice replaces the old one.
- **Abandon Merge**: The user may cancel the conflict resolution at any point. All pending
  resolutions are discarded, and the local model is restored to its pre-pull state.
- **Session Expiry During Resolution**: If the session expires while the conflict resolution
  interface is open, the system saves the resolution progress locally. Upon re-authentication, the
  user can resume from where they left off.

## Business Rules

- **Conflict Definition**: A conflict exists when the same model object (identified by its unique
  ID) has been modified in both the local model and the remote repository since the last sync
  baseline.
- **Deletion Conflict**: If one side deletes an object and the other side modifies it, this is
  treated as a conflict requiring manual resolution.
- **Simultaneous Addition**: If both sides add a new object with the same identifier, this is
  treated as a conflict.
- **Non-Overlapping Changes Are Safe**: Changes to different objects are never in conflict and are
  always merged automatically.
- **Merge Completeness**: The merge is finalized only when every detected conflict has a resolution.
  Partial merges are not applied to the local model.
- **Baseline Update**: The sync baseline is updated to the merged result only after the merge is
  fully completed and applied to the local model.
- **Resolution Scope**: The user may only choose between the local version and the remote version of
  a conflicting object. Custom property-level editing is not part of conflict resolution.

## UI/UX Functional Details

- **Feedback**: A progress indicator is shown when computing diffs or applying the final merge for
  operations exceeding 200ms.
- **Conflict Counter**: The conflict resolution interface displays a counter showing the number of
  resolved conflicts versus the total (e.g., "3 of 7 resolved").
- **Visual Highlighting**: The diff view uses color coding to distinguish additions (green),
  deletions (red), and unchanged content (neutral).
- **Keyboard Navigation**: The conflict list and resolution actions support keyboard navigation
  (Arrows to move between conflicts, keyboard shortcuts to accept local or remote).
- **Abandon Confirmation**: Cancelling an in-progress conflict resolution requires explicit
  confirmation to avoid accidental loss of resolution work.

## Technical Notes

- **Three-Way Merge**: The merge algorithm uses three inputs: the sync baseline (common ancestor),
  the local model, and the remote model. Diffs are computed against the baseline, not directly
  between local and remote.
- **Object Identity**: Model objects are identified by their immutable unique ID (UUID) as defined
  by the ArchiMate Exchange Format. Name or position changes do not affect identity.
- **Resolution Persistence**: In-progress conflict resolutions are stored in the plugin's workspace
  metadata so that they survive an IDE restart or session expiry without losing progress.
- **Merge Application**: The final merge is applied as a single atomic operation to the local model.
  If the application fails, the local model is rolled back to its pre-pull state.
- **Concurrency**: Conflict resolution is a single-user, sequential process per model. Concurrent
  resolution sessions on the same model are not supported.

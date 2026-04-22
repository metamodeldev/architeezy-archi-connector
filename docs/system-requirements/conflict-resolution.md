# SR-3: Conflict Management

## Scenarios

### SR-3.1: Analyze and Detect Model Conflicts

The system performs a structural three-way comparison during a Pull operation to identify
overlapping changes.

#### Functional Requirements

- [FR-3.1](../functional-requirements.md#fr-3-conflict-resolution),
  [FR-3.2](../functional-requirements.md#fr-3-conflict-resolution)

#### Steps

1. Initiate a Pull operation from the toolbar.
   - Progress dialog is displayed; local model, remote content, and the base snapshot are compared.
2. Observe the outcome of the conflict analysis.
   - If no conflicts: Pull completes automatically and the model is updated in-place.
   - If conflicts exist: Conflict Resolution Dialog opens automatically.

#### Edge Cases

- **No Common Ancestor**: If the base snapshot is missing, the operation is aborted with an error
  message to prevent unsafe merging.
- **Identical Changes**: If both sides made the exact same change, the system merges them
  automatically without flagging a conflict.

### SR-3.2: Resolve Conflicts in Pull Dialog

The system provides a visual interface for the user to manually choose between local and remote
versions of conflicting elements.

#### Functional Requirements

- [FR-3.3](../functional-requirements.md#fr-3-conflict-resolution),
  [FR-3.4](../functional-requirements.md#fr-3-conflict-resolution)

#### Steps

1. Review the Conflict Resolution Dialog.
   - Hierarchical tree of conflicting elements and side-by-side columns (Local vs Remote) are
     displayed.
   - Elements with unresolved conflicts are highlighted in red.
2. Select the preferred version for each conflict (or use "Accept All Local/Remote" buttons).
   - Chosen side is marked with a checkmark (✔).
   - "Apply" button becomes enabled once all conflicts have a selection.
3. Click "Apply" to finalize the resolution.
   - Dialog closes and the model is updated with the merged result.

#### Edge Cases

- **Cancellation**: If the dialog is closed without clicking "Apply", the Pull is aborted and the
  local model remains unchanged.
- **Deselection**: Using Ctrl+Click on a selected cell returns the conflict to an unresolved state
  and disables the "Apply" button.

## Business Rules

### Merge Logic and Data Integrity

- **Three-Way Comparison**: Detection is based on three versions: the base snapshot (last successful
  sync), the local copy, and the remote content. Comparison is performed at the element property
  level using unique IDs.
- **Conflict Definition**: A conflict occurs only when the same property (e.g., Name, Documentation,
  X/Y coordinates) of the same object is modified differently on both sides.
- **Atomicity**: The Pull operation is only finalized if the merge is successfully applied.
  Interrupted or cancelled sessions leave the local model in its original state.
- **Common Ancestor Requirement**: A base snapshot must exist to perform a three-way merge. Without
  it, the system cannot safely determine which side has the "newer" change.

### Resolution Rules

- **Mandatory Selection**: The "Apply" action is blocked until every identified conflict in the
  dialog has a chosen resolution (Local or Remote).
- **Automatic Merging**: All non-conflicting changes (local-only or remote-only) are integrated
  automatically regardless of whether the dialog is opened for conflicting ones.
- **Resolution Scope**: Conflicts are resolved per-property. A user can keep the local Name but
  accept the remote Documentation for the same object.

## UI/UX

### Conflict Resolution Dialog

- **Layout**: A three-column grid containing the Model Structure (element tree), Local Change, and
  Remote Change.
- **Change Representation**:
  - **Attributes**: Shown as `oldValue → newValue`.
  - **Structure**: Additions, deletions, and moves are described as "Added", "Deleted", or "Moved".
- **Visibility**: By default, the tree only shows elements with conflicts. A "Show all changes"
  checkbox reveals non-conflicting modifications.
- **Visual Cues**: Red text indicates unresolved conflicts; checkmarks (✔) indicate selected
  resolutions.
- **Bulk Actions**: "Accept All Local" and "Accept All Remote" buttons are available for quick
  resolution of all pending conflicts.

## Technical Notes

- **Comparison Engine**: Powered by the Eclipse EMF Compare framework (three-scope comparison).
- **Transactional Application**: All merged changes are applied to the live Archi model as a single
  batch operation to ensure structural consistency and maintain the undo history.
- **Snapshot Retrieval**: The base snapshot path is resolved using the model ID stored in the
  model's tracking properties.
- **Pseudo-conflicts**: Identical changes on both sides are classified as "pseudo-conflicts" and are
  resolved automatically by the engine.

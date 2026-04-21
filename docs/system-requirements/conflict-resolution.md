# SR-3: Conflict Management

## Scenarios

### SR-3.1: Analyze and Detect Model Conflicts

The system performs a structural three-way comparison during a Pull operation to identify
overlapping changes between the local workspace and the remote repository.

#### Functional Requirements

- [FR-3.1](../functional-requirements.md#fr-3-conflict-resolution): Compare local and remote model
  versions using a three-way merge logic.
- [FR-3.2](../functional-requirements.md#fr-3-conflict-resolution): Identify conflicting changes
  that cannot be merged automatically.

#### User Story

As a user, I want the system to detect when my local edits overlap with remote changes so that I can
prevent data loss during a Pull.

#### Preconditions

- A Pull operation has been initiated.
- Both local and remote modifications exist relative to the common ancestor version.

#### Steps

1. Initiate a Pull operation.
   - A progress dialog appears while the system compares the base snapshot, the local model, and the
     downloaded remote content.
2. Observe the outcome of the conflict analysis.
   - If all changes are non-overlapping or identical on both sides: the Pull completes automatically
     without further user interaction.
   - If conflicting changes are detected: the Conflict Resolution Dialog opens automatically.

#### Edge Cases

- **No Conflicts**: If all changes are non-overlapping or identical, the system completes the Pull
  automatically without opening any dialog.
- **No Common Ancestor**: If no base snapshot exists for the model, the merge cannot be performed
  safely. The system must abort the Pull and inform the user.

### SR-3.2: Resolve Conflicts in Pull Dialog

The system provides a dialog where all detected conflicts are displayed in a hierarchical tree with
side-by-side columns, allowing the user to choose which version to keep for each conflict.

#### Functional Requirements

- [FR-3.3](../functional-requirements.md#fr-3-conflict-resolution): Provide a visual interface to
  review conflicting object properties.
- [FR-3.4](../functional-requirements.md#fr-3-conflict-resolution): Resolve conflicts by manually
  selecting the local or remote version of an object.

#### User Story

As a user, I want to see all conflicting changes in a single dialog so that I can quickly decide
which versions to keep and finish the Pull operation.

#### Preconditions

- A Pull operation is in progress and real conflicts have been detected.

#### Steps

1. Review the Conflict Resolution Dialog that opens automatically.
   - The dialog shows a hierarchical tree of conflicting model elements alongside local and remote
     changes for each item.
   - Elements with unresolved conflicts are highlighted in red in the model structure column.
2. Click the preferred side (local or remote column cell) for each conflicting item.
   - The chosen side is marked with a checkmark (✔) in the selected cell.
   - Alternatively, click "Accept All Local" or "Accept All Remote" to resolve all remaining
     conflicts at once.
   - Once all conflicts are resolved, the Apply button becomes enabled.
3. Click "Apply" to finalize the Pull.
   - The dialog closes and the model is updated with the merged result.

#### Edge Cases

- **Cancellation**: If the user closes the dialog without clicking "Apply", the Pull is aborted and
  the local model remains in its pre-Pull state.
- **Bulk Selection**: "Accept All Local" and "Accept All Remote" update all unresolved conflicts
  simultaneously and enable the Apply button.

## Business Rules

- **Three-Way Comparison**: Conflict detection compares three versions of the model — the base
  snapshot (state at the last successful sync), the local working copy, and the downloaded remote
  content. The comparison operates at the model element level using each element's unique
  identifier.
- **Change Classification**: Each identified difference is classified as a local-only change, a
  remote-only change, or a conflict. Non-overlapping changes and identical changes on both sides are
  merged automatically. Only changes where the same property was modified differently on both sides
  are presented to the user as conflicts.
- **Mandatory Selection**: The "Apply" action remains disabled until a resolution choice (Local or
  Remote) has been made for every identified conflict in the dialog.
- **Scope of Resolution**: Conflicts are defined at the property level (e.g., Name, Documentation,
  Coordinates) of individual model objects.
- **Atomicity**: The Pull is only completed if the merge is finalized. If the process is interrupted
  or cancelled, the local model remains unchanged.

## UI/UX

### Dialog Layout

The Conflict Resolution Dialog has three columns:

| Column          | Content                                                |
| --------------- | ------------------------------------------------------ |
| Model Structure | Hierarchical tree of model elements that have changes. |
| Local Change    | The value or diff from the user's local working copy.  |
| Remote Change   | The value or diff from the server version.             |

By default, the tree shows only elements that have at least one conflict in their subtree. A **"Show
all changes"** checkbox reveals all changed elements, including non-conflicting ones.

### Change Representation

Changes are displayed in the Local and Remote columns as human-readable descriptions:

- **Attribute change**: shown as `attributeName: oldValue → newValue`.
- **Reference added**: shown as `+ elementName`.
- **Reference removed**: shown as `- elementName`.
- **Reference moved**: shown as `⇆ elementName`.
- **Element added or deleted** (structural containment): shown as "Added" or "Deleted" in the
  structure column rather than as a property diff.

### Resolution Interaction

- Clicking a cell in the Local Change column selects the local version for that conflict.
- Clicking a cell in the Remote Change column selects the remote version.
- The chosen side is indicated by a checkmark (✔) prefix in the cell text.
- Ctrl+Click on a selected cell deselects the resolution, returning the conflict to an unresolved
  state.
- Elements with unresolved conflicts are shown in red in the Model Structure column.
- Two bulk action buttons — "Accept All Local" and "Accept All Remote" — resolve all remaining
  conflicts at once.
- The Apply button remains disabled as long as any conflict is unresolved.

## Technical Notes

- **Comparison Engine**: The three-way comparison uses the Eclipse EMF Compare framework. The
  comparison scope is: left = local working copy, right = remote content, origin = base snapshot.
- **Conflict Kind**: Only structural conflicts ("real conflicts" in EMF Compare terminology — where
  both sides changed the same feature of the same object differently) are surfaced in the dialog.
  Pseudo-conflicts (identical changes on both sides) are resolved automatically.
- **Common Ancestor Identification**: The base snapshot stored in the plugin’s state directory is
  loaded as the "origin" reference. Its path is derived from the model ID in the tracking property.
- **Transactional Application**: All integrated changes and resolved conflicts are applied to the
  live Archi model as a single batch operation to maintain structural consistency.
- **Non-Conflicting Changes**: Remote non-conflicting changes are applied automatically regardless
  of whether the user opens the dialog. The dialog handles only the conflicting portion.

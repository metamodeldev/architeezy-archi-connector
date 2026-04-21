# Error Management: Architeezy Archi Connector

## Principles

1. **Never silently corrupt state.** If an operation fails midway, the local model and tracking
   metadata must remain in their pre-operation state.
2. **Background failures are silent; explicit operations are not.** The update check background
   service does not show error dialogs — it skips the check silently. Explicit user-initiated
   operations (Import, Export, Pull, Push) always surface errors.
3. **Errors are user-readable.** Technical messages (HTTP status codes, exception messages) are
   mapped to plain-language descriptions. Stack traces are logged but not shown in the UI.
4. **Authentication errors trigger a re-auth prompt.** A 401 or a failed token refresh should prompt
   the user to re-authenticate rather than showing a generic error.

## Error Categories

### API Errors

Errors returned by the Architeezy server are classified by HTTP status code:

| Status Code | Category     | User-Facing Behaviour                                              |
| ----------- | ------------ | ------------------------------------------------------------------ |
| 401         | Unauthorized | Attempt token refresh; if refresh fails, show re-auth prompt.      |
| 403         | Forbidden    | Show "You do not have permission for this operation."              |
| 404         | Not Found    | Show "The requested model or project was not found on the server." |
| 5xx         | Server Error | Show "The server encountered an error. Please try again later."    |

### Network Errors

Connection failures, timeouts, and DNS resolution errors are caught and presented as connectivity
errors. The underlying cause (e.g., "Connection refused") is included in the dialog for diagnostics.

### Deserialization Errors

If a downloaded model file cannot be parsed as a valid ArchiMate XMI document, the import or pull is
aborted. The local model remains unchanged.

### Merge Errors

If a base snapshot is missing or cannot be loaded for a model that reports as Diverged, the merge is
aborted. The user is informed that a base snapshot is unavailable and that a full re-import may be
needed.

## Error Handling by Operation

### Import

| Error Condition       | Behaviour                                                        |
| --------------------- | ---------------------------------------------------------------- |
| Download fails        | Abort; no local file created; show error dialog.                 |
| File cannot be parsed | Abort; no model opened; show error dialog.                       |
| Snapshot write fails  | Log warning; model is open but merge will not be possible later. |

### Export

| Error Condition          | Behaviour                                              |
| ------------------------ | ------------------------------------------------------ |
| Create model entry fails | Abort; model remains untracked; show error dialog.     |
| Content upload fails     | Abort; model remains untracked; show error dialog.     |
| Server returns 403       | Show permission error in wizard or post-finish dialog. |

### Pull

| Error Condition                | Behaviour                                                        |
| ------------------------------ | ---------------------------------------------------------------- |
| Remote fetch fails             | Abort; local model unchanged; show error dialog.                 |
| Diverged but no base snapshot  | Abort; inform user that re-import is needed.                     |
| Conflict dialog cancelled      | Pull aborted; local model unchanged (no partial state).          |
| In-place model update fails    | Abort; attempt rollback; show error dialog.                      |
| Snapshot write fails post-pull | Log warning; model is updated but future merges may be affected. |

### Push

| Error Condition          | Behaviour                                                      |
| ------------------------ | -------------------------------------------------------------- |
| Server has newer version | Block push; inform user to pull first.                         |
| Upload fails             | Abort; local tracking metadata not updated; show error dialog. |

### Background Update Check

| Error Condition     | Behaviour                                             |
| ------------------- | ----------------------------------------------------- |
| Network unreachable | Skip silently; retry at next interval.                |
| Auth token expired  | Skip silently; defer to next explicit user operation. |

## Error Display Conventions

- **Wizard pages**: validation errors are shown as page messages at the top of the wizard page (not
  in a separate dialog) so the user can correct them without closing the wizard.
- **Post-finish failures**: if an operation launched from a wizard fails after clicking Finish
  (e.g., the network request fails), the error is shown in a modal error dialog.
- **Background job failures**: errors from user-visible background jobs (e.g., Pull, Push) are
  reported in a modal dialog after the job completes.
- **Debug logging**: all caught exceptions are written to the Eclipse error log with full stack
  traces. Users can access this via the Eclipse Error Log view.

## Atomicity and Recovery

All sync operations are designed to be atomic with respect to local state. The sequence is:

1. Download and validate remote content.
2. Compute the merged or new content.
3. Apply to the live model in-place.
4. Save the model file.
5. Write the new base snapshot.

Steps 1–2 are read-only and do not modify local state. If steps 3–5 fail, the plugin does not update
the tracking metadata. This means the state may be partially applied (e.g., model saved but snapshot
not updated), which will be detected on the next sync as an unexpected scenario.

Future versions should consider a two-phase approach to improve recovery guarantees.

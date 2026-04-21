# UI/UX Guidelines

These guidelines govern the design and implementation of all user interface components in the
Architeezy Archi Connector plugin.

## Principles

1. **Native feel**: Use SWT and JFace components exclusively. Do not introduce custom rendering
   frameworks or third-party widget toolkits. The plugin must look and behave like a native part of
   Archi and the Eclipse workbench.
2. **Non-blocking UI**: All network I/O and file operations must run on background threads. The SWT
   UI thread must never block.
3. **Progress visibility**: Any operation that takes more than 500ms must show a progress indicator
   with a "Cancel" option.
4. **Keyboard accessibility**: All interactive elements must be reachable and operable via keyboard.
   Tab order must follow visual reading order.
5. **Informative states**: Every interactive element should communicate its current state clearly —
   enabled, disabled, loading, error — without requiring the user to trigger it to find out.

## Wizards

Wizards are used for multi-step operations that require user input across several screens. The
plugin uses wizards for **Import** and **Export**.

### When to Use a Wizard

Use a wizard (not a dialog) when:

- The operation involves 2 or more sequential configuration steps.
- Each step depends on the result of the previous step (e.g., choose profile → load model list).

### Wizard Structure

Each wizard follows a consistent page order:

1. **Profile Selection Page** — always first. The user selects or configures a connection profile
   and authenticates.
2. **Content Selection Page** — depends on the operation: a model list for Import, a project list
   for Export.

### Profile Selection Page Rules

- Profiles are shown in a dropdown. The previously used profile should be pre-selected.
- Profile fields (name, server URL, client ID, endpoints) are editable inline without leaving the
  wizard.
- New profiles can be created and saved without leaving the page.
- The authentication status of the selected profile is shown clearly with a status label.
- The "Sign In" button initiates the OAuth flow; during the flow, the button changes to "Cancel" to
  allow the user to abort.
- **Import wizard**: authentication is not required to advance to the next page. The Next button is
  always enabled. If model download later fails due to missing auth, surface the error at that
  point.
- **Export wizard**: authentication is mandatory before the user can proceed. The Next button
  remains disabled until the profile status is "Connected".

### Content Selection Page Rules

- Data is loaded on a background thread when the page becomes visible (not when the wizard opens).
  Show a loading spinner or message during the load.
- Provide a text search field that filters the list in real time using case-insensitive substring
  matching.
- Column layout for model lists: Name (widest), Author, Last Modified (date formatted to
  `yyyy-MM-dd HH:mm` in local time).
- The Finish button is disabled until a required selection is made and any required fields are
  filled.

### Wizard Finish Behaviour

- The Finish operation runs in `IRunnableWithProgress` to show a progress bar inside the wizard.
- On success, the wizard closes automatically.
- On failure, show an error message in a modal dialog after the wizard closes. Do not swallow errors
  silently.

## Dialogs

Use modal JFace dialogs for focused, single-step interactions that require a decision before
proceeding.

### Conflict Resolution Dialog

- Size: at least 820×600, resizable.
- Uses a `TreeViewer` with three columns: **Model Structure**, **Local Change**, **Remote Change**.
- The tree is filtered by default to show only conflicting branches. Provide a "Show all changes"
  checkbox to reveal all changed elements.
- Conflicting rows are shown in red in the Model Structure column until resolved.
- Resolution is indicated by a ✔ prefix in the chosen cell.
- The Apply/OK button must be disabled until all conflicts have a resolution choice.
- Provide "Accept All Local" and "Accept All Remote" bulk action buttons.
- Cancelling the dialog must leave the model completely unchanged.

### Confirmation Dialogs

- Use `MessageDialog` for all confirmations (not `JOptionPane` or native `Shell`-based dialogs).
- Destructive actions (e.g., overwriting local unsaved changes) require an explicit confirmation
  with a clear description of what will happen.
- Non-destructive informational messages (e.g., "remote is unchanged, you can push") use an
  information dialog, not a warning.

## Toolbar and Menu Integration

- The **Pull** action is placed in the main Archi toolbar for quick access.
- The **Import** and **Export** actions are placed in the File menu.
- Toolbar buttons use Eclipse's shared image registry for standard icons (e.g., import_wiz).
- Custom icons are used only when no suitable standard icon exists.

### Button Enablement

- The Pull toolbar button is enabled only when the currently active model is a tracked model with a
  pending update available.
- Enablement is re-evaluated automatically whenever the update state changes (background poll
  result) or the active editor changes.

## Model Tree Decorator

The model tree in Archi's Models view is enhanced by a lightweight **decorator** that appends visual
state indicators to model names.

### Update Indicator

- Symbol: `↓` (Unicode down arrow, U+2193) appended to the model's display name.
- Meaning: a newer version of this model is available on the server.
- The symbol is appended as plain text, not as an icon overlay, to maintain compatibility with
  Archi's existing label provider.

### Tooltip

When the user hovers over a model with the update indicator, a tooltip shows:

- "New version available on server"
- Server version date (formatted as `yyyy-MM-dd HH:mm` in local time)
- Date of the last local pull (formatted identically)

Tooltip support must be explicitly enabled on the tree viewer using the platform's tooltip support
API.

### Decorator Lifecycle

The decorator installs itself on Archi's model tree viewer after the workbench is fully initialized.
It uninstalls itself when the plugin is stopped. If the viewer cannot be found at startup (e.g., the
Models view is not open), the decorator retries on the next update state change.

## Progress and Feedback

- Operations taking more than 500ms must run in an Eclipse `Job` or `IRunnableWithProgress` with:
  - A descriptive task name (e.g., "Importing model from Architeezy…")
  - Incremental progress updates where possible
  - A "Cancel" option that stops the operation safely
- Jobs for explicit user operations (Pull, Push, Import, Export) should be visible in the Progress
  view and show a result notification on completion.
- The background update check is a **system job** (not visible in the Progress view) that runs
  silently on a schedule.

## Error Presentation

- **Wizard page errors**: use the wizard page's built-in message area (top of the page) for
  validation errors or auth failures that occur before Finish is clicked. Never use a modal dialog
  for inline page errors.
- **Operation failures**: use `MessageDialog.openError()` for errors that occur during or after an
  operation (e.g., a failed download). Include a concise description and, where helpful, a suggested
  remediation ("Please check your connection and try again.").
- **Non-blocking warnings**: use informational dialogs (`MessageDialog.openInformation()`) for
  advisory messages that don't block the user (e.g., "Remote is unchanged; you can push your local
  changes.").

## Localization

- All user-visible strings must be defined in a messages properties file, not hardcoded in source.
- US English is the default locale.
- Russian (`messages_ru.properties`) is provided as a supported translation.
- Date formatting must use the user's local timezone and locale-appropriate patterns.
- Do not embed format patterns directly in UI code; use the platform's formatting APIs.

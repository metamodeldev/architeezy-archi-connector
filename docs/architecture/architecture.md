# Architecture Overview: Architeezy Archi Connector

## Plugin Structure

The Architeezy Archi Connector is an OSGi bundle that runs inside the Archi desktop application
(which is itself an Eclipse RCP application). It depends on the Archi platform's extension points
and the Eclipse IDE framework but is otherwise self-contained.

The bundle is part of a multi-module Maven build:

| Module             | Role                                                       |
| ------------------ | ---------------------------------------------------------- |
| Main plugin bundle | All production source code, extension point registrations. |
| Eclipse Feature    | Groups the plugin for installation via a P2 update site.   |
| P2 Repository      | The deployable update site artifact.                       |
| Target Platform    | Pins the versions of Archi, Eclipse, and EMF Compare used. |

## Architectural Layers

The plugin is organized into four loosely coupled layers. Dependencies flow strictly downward.

```plain
┌──────────────────────────────────────────────────────────┐
│  UI Layer                                                │
│  Wizards · Dialogs · Decorators · Handlers               │
├──────────────────────────────────────────────────────────┤
│  Service Layer                                           │
│  Authentication · Repository · Merge · Sync Monitor      │
├──────────────────────────────────────────────────────────┤
│  Infrastructure Layer                                    │
│  API Client · Model Serializer · Snapshot Store          │
│  Token Store · Preference Store                          │
├──────────────────────────────────────────────────────────┤
│  Archi / Eclipse Platform                                │
│  EMF · SWT · JFace · Equinox Security · IPreferenceStore │
└──────────────────────────────────────────────────────────┘
```

### UI Layer

Contains all SWT/JFace components: Import and Export wizards, the Conflict Resolution Dialog, the
Model Tree Decorator, and Eclipse command handlers. UI components delegate all business logic to the
Service layer and must not perform network I/O directly.

### Service Layer

Encapsulates all business logic:

- **Authentication Service**: manages connection profile lifecycle, OAuth token acquisition and
  refresh, and profile status transitions.
- **Repository Service**: orchestrates import, export, and pull operations by coordinating the API
  client, serializer, and snapshot store.
- **Merge Service**: computes the three-way merge result and, when conflicts exist, opens the
  Conflict Resolution Dialog.
- **Update Check Service**: runs a background job that polls tracked open models for server-side
  updates and notifies registered listeners.

### Infrastructure Layer

Provides stateless or lightly stateful utilities:

- **API Client**: makes HTTP requests to the Architeezy REST API and parses HAL+JSON responses.
- **Model Serializer**: converts between live Archi EMF model objects and the XMI byte
  representation used for storage and transmission.
- **Snapshot Store**: persists and retrieves base snapshots from the platform's state directory.
- **Token Store**: reads and writes OAuth tokens using Eclipse Equinox Secure Preferences.

## Plugin Initialization

Eclipse calls the plugin's activator when the bundle is first loaded. The activator starts the
Update Check Service. Separately, an `IStartup` implementation is invoked by the Eclipse workbench
after full initialization; it installs the Model Tree Decorator on the UI thread.

## Singleton Services

All services are singletons accessed via a static instance constant. There is no dependency
injection container. This is consistent with the OSGi/Eclipse RCP idiom and keeps the plugin
footprint small.

## Threading Model

- **Background threads (Eclipse Jobs)**: all network I/O and file operations run on background
  Eclipse `Job` instances with `IProgressMonitor` support for cancellation and progress reporting.
- **UI thread (SWT Display)**: all model tree updates, dialog display, and decorator refreshes are
  dispatched to the SWT UI thread.
- **Concurrency guards**: token refresh operations for a given server use a per-server lock to
  prevent duplicate concurrent refresh requests.

## Extension Points Used

| Extension Point           | Purpose                                           |
| ------------------------- | ------------------------------------------------- |
| `org.eclipse.ui.startup`  | Install the tree decorator after workbench start. |
| `org.eclipse.ui.commands` | Register Import, Export, and Pull commands.       |
| `org.eclipse.ui.handlers` | Bind command handlers.                            |
| `org.eclipse.ui.menus`    | Add menu entries and toolbar buttons.             |

## Key Design Decisions

1. **No external JSON library**: HAL+JSON responses are parsed with hand-written string extraction.
   This avoids adding OSGi bundle dependencies and keeps the plugin dependency footprint minimal.

2. **Tracking metadata in the model file**: Server URL and last-modified timestamp are stored as
   ArchiMate model properties inside the `.archimate` file. This makes tracking state portable — it
   travels with the file when copied or shared, without requiring any out-of-band registry.

3. **Snapshot-based merge**: The common ancestor for 3-way merge is a serialized snapshot stored on
   disk, not a server-side version ID. This enables offline detection of local divergence and
   decouples merge correctness from server version schemes.

4. **Non-destructive in-place pull**: Remote content is applied to the live open model using Archi's
   built-in import mechanism rather than closing and reopening the file. This preserves the user's
   open diagram editors and undo history.

5. **EMF Compare for structural diff**: Conflict detection and resolution reuse the Eclipse EMF
   Compare framework, which understands ArchiMate's EMF metamodel and can match elements by UUID
   across versions.

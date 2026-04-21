# Domain Model: Architeezy Archi Connector

## Core Concepts

### Tracked Model

A **tracked model** is a local Archi model (`.archimate` file) that has been linked to a specific
model entry in the Architeezy repository. A model becomes tracked after a successful **Import** or
**Export** operation. Tracking state is identified by the presence of a server URL property in the
model's property list.

An untracked model is a regular local Archi model with no server association.

### Connection Profile

A **connection profile** holds all the settings needed to communicate with a specific Architeezy
server instance. It is not tied to a specific user account — the same profile can be used by
different users who authenticate against the same server.

| Field                  | Description                                                       |
| ---------------------- | ----------------------------------------------------------------- |
| Name                   | A human-readable label for display in the UI.                     |
| Server URL             | The base URL of the Architeezy server.                            |
| Client ID              | The OAuth 2.0 client identifier registered on the server.         |
| Authorization Endpoint | The URL for initiating the OAuth authorization flow.              |
| Token Endpoint         | The URL for exchanging authorization codes and refreshing tokens. |
| Status                 | The current authentication state (see Profile Status below).      |

#### Profile Status

| Value           | Meaning                                                      |
| --------------- | ------------------------------------------------------------ |
| Disconnected    | No valid tokens stored; the user has not authenticated.      |
| Connecting      | The OAuth browser flow is in progress.                       |
| Connected       | Valid tokens exist; API requests may proceed.                |
| Session Expired | The refresh token is invalid; the user must re-authenticate. |

### Remote Model

A **remote model** is the server-side representation of an ArchiMate model stored in the Architeezy
repository. The plugin retrieves this information from the server's API.

| Field         | Description                                                           |
| ------------- | --------------------------------------------------------------------- |
| ID            | Unique server-side identifier.                                        |
| Name          | Display name of the model.                                            |
| Description   | Optional description text.                                            |
| Author        | The user who created or last modified the model.                      |
| Last Modified | ISO 8601 timestamp of the most recent server-side change.             |
| Self URL      | The model's canonical HAL API URL (used for updates, deletion).       |
| Content URL   | The URL from which the binary `.archimate` content can be downloaded. |

### Remote Project

A **remote project** is a top-level organizational container on the Architeezy server. Models are
associated with projects when exported.

| Field | Description                    |
| ----- | ------------------------------ |
| ID    | Unique server-side identifier. |
| Name  | Display name of the project.   |

### Base Snapshot

A **base snapshot** is a serialized copy of a tracked model's content at the moment of the last
successful synchronization (Import, Export, or Pull). It is stored in the plugin's local state
directory as an `.archimate` file.

The base snapshot serves as the **common ancestor** in the 3-way merge algorithm. Without it, the
system cannot distinguish between a change made locally and a change that was already present at the
last sync.

### Sync Scenario

Before performing a pull, the system classifies the current state of a model into one of four **sync
scenarios** by comparing three versions: the live local model, the base snapshot, and the downloaded
remote content.

| Scenario    | Local vs. Base | Remote vs. Base | Meaning                                     |
| ----------- | -------------- | --------------- | ------------------------------------------- |
| Up to Date  | Equal          | Equal           | Nothing has changed; no action needed.      |
| Simple Pull | Equal          | Different       | Only remote changed; apply remote directly. |
| Simple Push | Different      | Equal           | Only local changed; user should push.       |
| Diverged    | Different      | Different       | Both sides changed; 3-way merge required.   |

Equality in this context is determined by comparing the full serialized XMI byte content of each
version. This approach is simple but means that even a non-semantic reordering (e.g., element order
in the XML) will be treated as a change.

### Conflict

A **conflict** occurs during a Diverged merge when the same attribute or relationship of the same
model element was changed differently in the local version and the remote version relative to the
base snapshot.

**Non-conflict** changes include:

- A change made only on one side (local or remote).
- The exact same change made identically on both sides (pseudo-conflict, resolved automatically).

Only **real conflicts** are presented to the user for manual resolution. Non-conflicting remote
changes are always applied automatically.

## Relationships Between Concepts

```plain
ConnectionProfile ──── (1 per server) ──── OAuth Tokens (in Secure Prefs)

Tracked Model ──── has ──── Tracking Metadata (in model file properties)
                                  │
                                  ▼
                            Base Snapshot (in plugin state dir)

Remote Model ──── belongs to ──── Remote Project
     │
     └── identified by ──── Self URL
```

A tracked model's server URL property points to a Remote Model's self URL. The model ID is the last
path segment of that URL.

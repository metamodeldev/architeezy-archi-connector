# Data Persistence: Architeezy Archi Connector

## Overview

The plugin persists four categories of data, each using a different storage mechanism matched to its
security and lifecycle requirements:

| Data Category       | Storage Mechanism            | Scope          |
| ------------------- | ---------------------------- | -------------- |
| Connection profiles | Eclipse Preference Store     | Per workspace  |
| OAuth tokens        | Eclipse Equinox Secure Prefs | Per user/OS    |
| Tracking metadata   | ArchiMate model file         | Per model file |
| Base snapshots      | Plugin state directory       | Per model      |

## 1. Connection Profiles (Preference Store)

Connection profiles are stored in the plugin's Eclipse `IPreferenceStore`, which persists to a
`.prefs` file in the Eclipse workspace metadata directory.

**Profile list**: A comma-separated string listing all known profile names is stored under a single
preference key.

**Per-profile fields**: Each profile's individual fields (server URL, client ID, authorization
endpoint, token endpoint) are stored as separate preference keys using the profile name as a
namespace qualifier.

Profiles are loaded on demand and written back immediately on any create, update, or delete
operation.

**Default profile**: On first startup, if no profiles exist, a default profile pointing to the
production Architeezy server is written automatically.

## 2. OAuth Tokens (Equinox Secure Preferences)

Access tokens and refresh tokens are sensitive credentials and require encrypted storage.

The plugin uses Eclipse Equinox Secure Preferences (`org.eclipse.equinox.security`), which
integrates with the OS keychain where available:

- **macOS**: Keychain Services
- **Windows**: Windows Credential Manager (Data Protection API)
- **Linux**: Secret Service API (GNOME Keyring / KWallet)

**Key derivation**: The storage key for a server's tokens is based on the SHA-1 hash of the server
URL. This ensures that profiles pointing to different servers use separate storage slots, even if
their display names are similar.

**Stored values** per server:

- Access token (encrypted)
- Refresh token (encrypted)
- Token expiry as an epoch-seconds string (not encrypted — not sensitive)

Tokens are cleared when the user logs out from a profile, or when the plugin detects that the
refresh token is no longer valid.

## 3. Tracking Metadata (Model File Properties)

The link between a local Archi model and its remote counterpart is stored as **named properties
directly inside the `.archimate` file** — not in a separate sidecar file, hidden directory, or
database.

Two properties are written into the model's top-level property list:

| Property Key                             | Value                                                     |
| ---------------------------------------- | --------------------------------------------------------- |
| `architeezy:url`                         | The HAL `self` URL of the model on the server             |
| `architeezy:last_modification_date_time` | ISO 8601 datetime of the last successfully synced version |

**Why in the model file?** Embedding tracking state in the model ensures it is always co-located
with the file. If the user copies or moves the `.archimate` file, or shares it with a colleague, the
tracking information travels with it. There is no orphaned registry entry to clean up.

**Model ID derivation**: The remote model's ID is the last path segment of the server URL property.
The server base URL is the scheme, host, and port portion of the same property.

## 4. Base Snapshots (Plugin State Directory)

A **base snapshot** is a full serialized copy of the model as it existed at the moment of the last
successful sync (Import, Export, or Pull). It serves as the common ancestor in 3-way merge
operations.

**Storage location**: The Eclipse platform provides each plugin with a state directory via
`Platform.getStateLocation()`. This directory is managed by Eclipse and is separate from the
workspace. On desktop platforms it typically lives in the user's application data folder.

Snapshots are stored in a `snapshots/` subdirectory of this state location, one file per tracked
model. The filename is a sanitized form of the model ID (characters not safe for filenames are
replaced), with the `.archimate` extension.

**Atomic writes**: Snapshots are written to a temporary file first and then renamed to the final
path using a file-system atomic move. This prevents a partially written snapshot from being used as
a merge base if the write is interrupted.

**Lifecycle**:

- Created: immediately after a successful Import, Export, or Pull.
- Updated: after every successful Pull (including auto-merges with no conflicts).
- Deleted: when the model is untracked or the plugin's state is cleared.

## Data Consistency Invariant

The tracking metadata in the model file and the base snapshot must always represent the same sync
point. After every sync operation, both must be updated atomically (i.e., in the same logical
transaction):

1. Apply the new content to the live model.
2. Write the tracking metadata properties to the model.
3. Save the model file.
4. Write the new base snapshot.

If step 4 fails, the tracking metadata in step 2 will not match any existing snapshot on disk, which
will cause the next merge to fail safely (no snapshot = no 3-way merge possible). The system should
detect this and surface it as an error rather than silently corrupting data.

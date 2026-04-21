# Glossary

This glossary defines the terms used across the Architeezy Archi Connector documentation and
codebase. Terms are listed alphabetically.

## ArchiMate

An open, independent modeling language for Enterprise Architecture, standardized by The Open Group.
Archi uses ArchiMate as its native modeling notation. Models are stored in an XMI-based format with
the `.archimate` file extension.

## Authorization Code Flow with PKCE

An OAuth 2.0 authentication flow designed for public clients (applications that cannot securely
store a client secret). PKCE (Proof Key for Code Exchange) adds a cryptographic binding between the
authorization request and the token exchange, preventing authorization code interception attacks.

## Base Snapshot

A serialized copy of a tracked model stored locally at the time of the last successful
synchronization (Import, Export, or Pull). The base snapshot is the **common ancestor** used in
3-way merge operations. Without it, the system cannot distinguish local changes from the server's
changes.

## Client ID

The identifier assigned to the Archi Connector plugin as a registered OAuth 2.0 client on the
Architeezy authorization server. It is stored per connection profile and does not need to be kept
secret.

## Common Ancestor

The version of a model that both the local user and the remote server were working from before their
diverging edits. In this plugin, the common ancestor is always the base snapshot.

## Conflict

A situation during a 3-way merge where the same attribute or reference of the same model element was
changed differently in the local version and the remote version relative to the common ancestor.
Conflicts require manual resolution and are displayed in the Conflict Resolution Dialog.

Contrast with **pseudo-conflict**: the same change made identically on both sides, which the system
resolves automatically.

## Conflict Resolution Dialog

A modal dialog opened during a Pull when real conflicts are detected. It presents the model
structure in a tree with three columns (Model Structure, Local Change, Remote Change) and allows the
user to choose which side to keep for each conflict. The Pull is only finalized when all conflicts
have been resolved.

## Connection Profile

A named set of configuration settings for connecting to an Architeezy server instance: server URL,
OAuth client ID, authorization endpoint, and token endpoint. One profile may be shared by multiple
users pointing to the same server. Profiles are persisted in the Eclipse Preference Store.

## Content URL

The URL from which the binary `.archimate` content of a remote model can be downloaded. This URL is
provided by the server in the HAL response's `_links.content` section and is not constructed by the
plugin. It identifies the ArchiMate-format content link (as opposed to other possible formats).

## Diverged

A **sync scenario** where both the local model and the remote model have changed relative to the
base snapshot. A 3-way merge is required. If the changes conflict, the Conflict Resolution Dialog is
shown.

## EMF (Eclipse Modeling Framework)

The Java framework used by Archi to represent model objects in memory. All ArchiMate elements,
relationships, and diagrams are EMF objects. The plugin uses EMF APIs to read model properties and
apply changes.

## EMF Compare

An Eclipse framework for computing structural differences between EMF model versions. The plugin
uses EMF Compare to implement the 3-way merge algorithm, identifying which elements were added,
modified, or deleted relative to the common ancestor.

## Export

The operation of uploading a local, previously untracked Archi model to the Architeezy server for
the first time, creating a new remote model entry and associating the project with it. After a
successful export, the model becomes tracked.

Contrast with **Push**, which uploads a new version of a model that is already tracked.

## HAL+JSON (Hypertext Application Language)

A JSON-based API response format that embeds hypermedia links (`_links`) alongside resource data.
The Architeezy API uses HAL so that the client (this plugin) can navigate to related resources
(e.g., model content URL) without hardcoding paths.

## Import

The operation of downloading a remote model from the Architeezy server and opening it as a new local
Archi model. After a successful import, the model becomes tracked.

Contrast with **Pull**, which updates an already-tracked local model with the latest remote changes.

## Model ID

The unique identifier of a remote model on the Architeezy server. It is the last path segment of the
model's self URL (e.g., `abc123` in `https://architeezy.com/api/models/abc123`). The plugin derives
the model ID from the tracking metadata stored in the model's properties.

## Profile Status

The current authentication state of a connection profile. Possible values:

| Value           | Meaning                                               |
| --------------- | ----------------------------------------------------- |
| Disconnected    | No valid tokens stored.                               |
| Connecting      | OAuth browser flow is in progress.                    |
| Connected       | Valid tokens available; API requests can be made.     |
| Session Expired | Refresh token is invalid; re-authentication required. |

## Pull

The operation of fetching the latest version of a tracked model from the server and applying the
changes to the local copy. May involve a 3-way merge if both sides have changed.

## Push

The operation of uploading the local version of a tracked model to the server, replacing the current
server version. Only allowed when the local base version matches the current server version (i.e.,
no pull is needed first).

## Remote Model

The server-side representation of an ArchiMate model stored in the Architeezy repository, as
returned by the API. Contains metadata such as name, author, last modification date, and links to
its content.

## Remote Project

An organizational container on the Architeezy server that groups related models. When exporting a
model, the user selects a target project.

## Self URL

The canonical HAL API URL of a remote model resource (e.g., `https://server/api/models/abc123`).
This URL is used to update the model's content and is stored as a tracking property in the local
model file.

## Simple Pull

A **sync scenario** where the local model is unchanged relative to the base snapshot and the remote
model has changed. The remote content can be applied directly without any merge logic.

## Simple Push

A **sync scenario** where the local model has changed relative to the base snapshot but the remote
model is unchanged. The system informs the user that they can push their changes; no pull is needed.

## Snapshot Store

The component responsible for persisting and loading base snapshots. Snapshots are stored as files
in the plugin's Eclipse state directory (separate from the workspace). Writes are atomic.

## Sync Scenario

The classification of a model's synchronization state before performing a pull. The four scenarios
are: **Up to Date**, **Simple Pull**, **Simple Push**, and **Diverged**.

## Tracked Model

A local Archi model that has been linked to a remote model entry on the Architeezy server. Tracking
state is embedded as named properties inside the `.archimate` file. A tracked model has a base
snapshot stored locally.

## Token Endpoint

The OAuth 2.0 endpoint URL used to exchange an authorization code for tokens, and to refresh an
access token using a refresh token.

## Tracking Metadata

Two named properties stored directly inside a tracked `.archimate` model file:

- The server URL of the corresponding remote model.
- The ISO 8601 modification timestamp of the last successfully synced version.

These properties are used to identify the server, detect updates, and derive the model ID.

## Up to Date

A **sync scenario** where neither the local model nor the remote model has changed relative to the
base snapshot. No action is required.

## Update Indicator

A visual decorator (a down-arrow icon ↓ appended to the model name) shown in the Archi model tree
when a newer version of a tracked model is available on the server. The indicator appears after the
background update check detects a newer remote timestamp.

## XMI

XML Metadata Interchange — the XML-based serialization format used by ArchiMate/Archi for
`.archimate` files. The plugin serializes and deserializes models using Archi's native XMI resource
factory.

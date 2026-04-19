# Functional Requirements: Architeezy Archi Connector

## Overview

This document outlines the high-level functional requirements for the Architeezy Archi Connector
plugin.

## FR-1: Authentication

- FR-1.1: Authenticate users with the Architeezy platform via OAuth 2.0.
- FR-1.2: Support multiple server connection profiles.
- FR-1.3: Maintain and automatically refresh user session tokens.
- FR-1.4: Display current connection status and authenticated user identity.

## FR-2: Repository

- FR-2.1: Browse the remote repository structure.
- FR-2.2: Search for models within the repository.
- FR-2.3: View model metadata.
- FR-2.4: Import a model from the repository to the local workspace.
- FR-2.5: Publish a local model to the repository.

## FR-3: Synchronization

- FR-3.1: Push local changes to the repository.
- FR-3.2: Pull and integrate remote changes into the local model.
- FR-3.3: Display synchronization status for each tracked model.
- FR-3.4: Notify users when remote updates are available for a tracked model.
- FR-3.5: Preserve local model integrity when a network interruption occurs during sync.

## FR-4: Conflict Resolution

- FR-4.1: Automatically merge concurrent, non-overlapping changes based on model object identity.
- FR-4.2: Detect and surface conflicting modifications to the same model objects.
- FR-4.3: Provide a diff view comparing local and remote versions of conflicting objects.
- FR-4.4: Allow the user to resolve each conflict by accepting the local or remote version.

## Out of Scope

- **Web Viewing & Editing:** Accessing or editing models via the Architeezy web portal is a
  server-side feature and is not implemented in the plugin.
- **Model Validation:** Checking models against enterprise standards or integrity rules.
- **Access Control:** Management of user roles and permissions.
- **Version Branching:** Creation and management of model branches.
- **Local Merging:** Merging changes between local files without a server connection.

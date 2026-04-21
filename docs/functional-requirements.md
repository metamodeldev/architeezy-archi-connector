# Functional Requirements: Architeezy Archi Connector

## Overview

This document outlines the high-level functional requirements for the Architeezy Archi Connector
plugin.

## FR-1: Repository Interaction

- FR-1.1: Manage server profiles with persistent authentication tokens.
- FR-1.2: Browse remote projects and models to select targets for import or export.
- FR-1.3: Import a model from the repository to the local environment.
- FR-1.4: Export a local model to the repository.

## FR-2: Model Synchronization

- FR-2.1: Monitor the server for model updates and notify the user of available changes.
- FR-2.2: Pull remote modifications and integrate them into the local model.
- FR-2.3: Push local modifications to the remote repository.

## FR-3: Conflict Resolution

- FR-3.1: Compare local and remote model versions using a three-way merge logic.
- FR-3.2: Identify conflicting changes that cannot be merged automatically.
- FR-3.3: Provide a visual interface to review conflicting object properties.
- FR-3.4: Resolve conflicts by manually selecting the local or remote version of an object.

## Out of Scope

- **Web Viewing & Editing:** Accessing or editing models via the Architeezy web portal is a
  server-side feature and is not implemented in the plugin.
- **Model Validation:** Checking models against enterprise standards or integrity rules.
- **Access Control:** Management of user roles and permissions.
- **Version Branching:** Creation and management of model branches.
- **Local Merging:** Merging changes between local files without a server connection.

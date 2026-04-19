# Vision: Architeezy Archi Connector

## Summary

The Architeezy Archi Connector is an integration plugin for Archi that bridges the gap between local
ArchiMate modeling and cloud-based repository management. It allows architects to maintain the
performance and familiarity of a desktop application while benefiting from centralized storage,
version control, and team collaboration.

By treating `.archimate` files as managed local artifacts and providing a model-aware merge engine,
the Connector ensures that the enterprise repository remains consistent without the friction of
manual file sharing.

## Problems & Solutions

| Problem                                                                                                                                  | Solution                                                                                                                                                        |
| :--------------------------------------------------------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Model Silos:** Collaborative modeling in Archi usually involves emailing files, leading to version fragmentation and data duplication. | **Centralized Repository:** Direct integration with the Architeezy API, allowing users to browse, import, and sync models within the IDE.                       |
| **Merging Conflicts:** Combining changes from multiple architects manually is error-prone and often corrupts the model structure.        | **Semantic 3-Way Merge:** A built-in engine that understands ArchiMate logic, resolving conflicts at the element and attribute level using UUID identification. |
| **Version Ambiguity:** It is often unclear if a local file is up to date or has diverged from the server version.                        | **Status Decorators:** UI indicators in the model tree that show whether a model is In Sync, has local changes, or requires a pull from the server.             |
| **Complexity of Consumption:** Stakeholders often find installing and using specialized modeling tools difficult for simple reviews.     | **Web-Native Access:** Models in the repository are accessible via the Architeezy web portal for viewing and light editing without additional exports.          |

## Target Users & Journeys

### 1. Enterprise Architects (Governance)

- **Need:** To organize the model library and ensure team members contribute to the correct
  versions.
- **Journey:** Defines the repository structure → Manages user access → Monitors the evolution of
  the model through the web-based portal.
- **Value:** Centralized governance and a consolidated view of the architectural landscape.

### 2. Solution Architects (Active Contributors)

- **Need:** To contribute specific diagrams or components to a shared model without overwriting peer
  work.
- **Journey:** Imports a model from the repository → Performs local edits → Runs a **Sync** to pull
  updates → Resolves any conflicts via a dedicated dialog → Pushes changes to the server.
- **Value:** Enables concurrent work on the same model with a clear path to resolution.

### 3. Reviewers & Stakeholders

- **Need:** To access the latest architectural state for decision-making.
- **Journey:** Log into the Architeezy Web Portal → Navigate to the specific model → Review diagrams
  and element properties directly in the browser.
- **Value:** Transparent access to architecture data for non-technical users.

## Competitive Landscape & Differentiators

| Feature             | Architeezy Connector           | coArchi (Git-plugin)          | Enterprise EA Suites          |
| :------------------ | :----------------------------- | :---------------------------- | :---------------------------- |
| **User Experience** | Native Archi integration       | Requires Git proficiency      | Often complex and proprietary |
| **Merge Logic**     | Semantic (Object-based)        | XML / Text-based              | Built-in / Proprietary        |
| **Web Access**      | Integrated Web Viewer & Editor | None (Requires manual export) | Built-in                      |
| **Authentication**  | OAuth2 / PKCE (SSO)            | SSH Keys / Personal Tokens    | LDAP / SSO                    |

## Technical Context

- **Model Fidelity:** Uses the native `.archimate` EMF XMI format to ensure 100% compatibility with
  Archi’s core features.
- **Data Integrity:** Relies on UUID-based identification for elements and relationships to ensure
  consistency during multi-user merges.
- **Security:** Implements standard OAuth2 flows for secure, browser-based authentication, meeting
  corporate security requirements.

## Success Metrics

- **Adoption:** Architects use the plugin as the primary mechanism for sharing and updating models,
  replacing ad-hoc file exchange entirely.
- **Merge Confidence:** The majority of concurrent edits are resolved automatically without
  requiring manual intervention.
- **Onboarding:** A new team member can import and contribute to an existing model within a single
  working session.

## Roadmap

### Phase 1: Connectivity (MVP)

- OAuth2/PKCE authentication flow.
- Repository Browser for searching and selecting remote models.
- Basic Import (Pull) and Publish (Push) functionality.

### Phase 2: Collaboration

- Semantic 3-Way Merge engine implementation.
- Conflict resolution dialog with change visualization.
- Sync status decorators for the model tree.

### Phase 3: Enterprise Features

- Soft-locking indicators for elements or views currently being edited.
- Integration of web-based comments into the Archi desktop interface.

## Conclusion

The Architeezy Archi Connector transforms Archi from a standalone tool into a collaborative
workstation. By automating version management and synchronization, it allows architects to focus on
the design process rather than the technicalities of file management.

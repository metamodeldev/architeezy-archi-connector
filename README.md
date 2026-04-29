# Architeezy Archi Connector

A plugin for [Archi](https://www.archimatetool.com/) that enables team collaboration by connecting
the desktop application to an [Architeezy](https://architeezy.com) server.

## Features

Architeezy offers a simpler alternative to Git-based
[coArchi](https://www.archimatetool.com/plugins/) or the
[Database Plugin](https://github.com/archi-contribs/database-plugin):

### Streamlined Desktop Sync

- **Simple Pull/Push:** No Git knowledge, SSH keys, or staging required. You don't need to create
  repositories—just import/export and stay in sync with two buttons.
- **One-Action Publishing:** Unlike Git, which requires a two-step "commit then push" process,
  Architeezy lets you publish your local changes in a single click.
- **Conflict Resolution:** If changes overlap, a custom UI dialog lets you choose which version to
  keep, avoiding the complexity of Git merge conflicts.
- **Change Alerts:** Get a notification within Archi as soon as a teammate publishes a newer version
  of the model.

### Web Access & Collaboration

- **Web First:** Models are immediately available in a browser for stakeholders who don't have Archi
  installed.
- **Web Editing:** Perform basic model edits directly on the Architeezy website.
- **Deep Linking:** Jump from an element in Archi to its web representation with a single click.

### Enterprise & Integration

- **Access Control:** Manage per-user Read/Edit permissions directly on the server.
- **Corporate Auth:** Sign in using your company account via OAuth 2.0.
- **REST API:** Access model data via [REST API](https://architeezy.com/swagger-ui/index.html) for
  custom integrations and [analysis tools](https://apps.architeezy.com/).

## Documentation

- [Vision](docs/vision.md)
- [Functional requirements](docs/functional-requirements.md)
- [Non-functional requirements](docs/non-functional-requirements.md)
- [System requirements](docs/system-requirements/)
  - [Repository](docs/system-requirements/repository.md)
  - [Synchronization](docs/system-requirements/synchronization.md)
  - [Conflict resolution](docs/system-requirements/conflict-resolution.md)
- [Architecture](docs/architecture/)
  - [Architecture overview](docs/architecture/architecture.md)
  - [API contract](docs/architecture/api-contract.md)
  - [Authentication](docs/architecture/authentication.md)
  - [Domain model](docs/architecture/domain-model.md)
  - [Data persistence](docs/architecture/data-persistence.md)
  - [Error management](docs/architecture/error-management.md)
- [Guidelines](docs/guidelines/)
  - [Coding conventions](docs/guidelines/coding-conventions.md)
  - [Development workflow](docs/guidelines/development-workflow.md)
  - [Documentation guidelines](docs/guidelines/docs-guidelines.md)
  - [UI/UX guidelines](docs/guidelines/ui-ux-guidelines.md)
  - [Glossary](docs/guidelines/glossary.md)

## Installation

1. Download `architeezy-connector-<version>.archiplugin` from the [Releases](../../releases) page.
2. Install it in Archi in one of two ways:
   - Drag the file onto a running Archi window, or
   - Open **Help → Manage Plug-ins... → Install...** and pick the file.
3. Restart Archi when prompted.

## Usage

After installation you will find the following entries in Archi:

| Command         | Where                                    | What it does                                                   |
| --------------- | ---------------------------------------- | -------------------------------------------------------------- |
| Import          | File → Import → Model From Architeezy... | Open a model from the server.                                  |
| Export          | File → Export → Model To Architeezy...   | Publish the current model to the server.                       |
| Pull            | Architeezy toolbar                       | Fetch the latest version of the current model from the server. |
| Push            | Architeezy toolbar                       | Publish your local changes back.                               |
| Open in Browser | Architeezy toolbar                       | Open the current model on the Architeezy website.              |

By default the plugin connects to `https://architeezy.com`. You can change the server in the
connection dialog that opens from the Import/Export wizard.

## Build from sources

You need JDK 21 and a local [Archi](https://www.archimatetool.com/) 5.8+ installation (used as the
target platform).

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw clean verify -Darchi_home=/path/to/Archi
```

The installable `architeezy-connector-<version>.archiplugin` bundle lands under
`com.architeezy.archi.connector.repository/target/`.

`./mvnw verify` also runs the unit tests from `com.architeezy.archi.connector.tests`.

## License

Licensed under the [Eclipse Public License 2.0](LICENSE).

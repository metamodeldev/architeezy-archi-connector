# Documentation Guidelines

This guide defines the standards for project documentation. We adhere to the **Docs-as-Code**
methodology, where documentation is maintained in the repository, written in Markdown, and versioned
alongside the source code.

## General Principles

- **Docs-as-Code**: Documentation lives in the repository and follows the same lifecycle as the
  source code.
- **Single Source of Truth (SSOT)**: Avoid data duplication. Reference `common/` documentation for
  global standards instead of repeating them in application-specific files. Any logic change should
  ideally require an update in only one location.
- **Naming Conventions**: Use `kebab-case` only for all files and folders (e.g.,
  `docs-guidelines.md`).
- **Visualization**: All diagrams (flows, sequences, ER-schemas, state-machines) must be authored in
  **Mermaid.js** syntax.
- **Relative Paths**: Use relative linking for all internal cross-references (e.g.,
  `[API Contract](../common/api-contract.md)`).
- **Atomicity**: Each requirement file must describe exactly one feature or logical module.
- **Traceability**: Every document must be part of a logical chain, linking technical implementation
  back to business requirements.
- **Reference Direction**: More specific documents reference more general ones — never the reverse.
  System requirements reference functional requirements; functional requirements reference common
  standards; `vision.md` references nothing within the project. General documents (`common/`,
  `vision.md`) must not link to application-specific or lower-level documents.

## Repository Structure

```text
docs/
├── common/                        # Global standards shared across all applications
│   ├── api-contract.md
│   ├── architecture.md
│   ├── authentication.md
│   ├── coding-conventions.md
│   ├── docs-guidelines.md
│   ├── domain-model.md
│   ├── error-handling.md
│   ├── glossary.md
│   ├── state-management.md
│   └── ui-ux-guidelines.md
└── <app-name>/                    # Application-specific documentation
    ├── vision.md
    ├── functional-requirements.md
    ├── non-functional-requirements.md
    ├── traceability-matrix.md
    ├── system-requirements/
    │   └── <feature>.md
    └── test-cases/
        └── <feature>/
            ├── tc-{sr-number}.md
            └── tc-{sr-number}.md
```

Application-specific documents reference `common/` via relative paths (e.g.,
`[Coding Conventions](../common/coding-conventions.md)`). Never duplicate content from `common/`
inside application documents — link instead.

## Document Hierarchy and Purpose

### Vision (`vision.md`)

**Purpose**: Defines the product strategy, value proposition, and alignment with the broader
ecosystem ("Why" the product exists).

**Perspective**: Product Owner, Strategic Lead.

**Target Audience**: Project Contributors, Stakeholders, Potential Partners.

**Structure**:

- **Summary**: A concise 1-2 paragraph overview of the product’s intent and core value.
- **Problems & Solutions**: A side-by-side comparison table of pain points and addressing features.
- **Target Users**: A combined section defining primary roles and concrete usage journeys.
- **Market Positioning**: An analysis of the competitive landscape and key differentiators (table).
- **Strategic Context**: The product's role in the ecosystem and its relationship to other services.
- **Success Metrics**: Strategic targets including high-level performance and business adoption
  KPIs.
- **Product Roadmap**: A phased plan showing the current state and future direction.
- **Call to Action**: A direct appeal to stakeholders or users focusing on immediate practical
  value.

**Principles**:

- **Focus on Strategic Intent:** Prioritize the reasoning behind the product over a detailed feature
  list.
- **Professional Tone:** Avoid marketing pathos. Use calm, objective, and business-focused language.
- **Data-Driven:** Use concrete numbers and specific targets (e.g., "10k+ entities").
- **Scannability:** Use tables and bullet points. Avoid long, dense blocks of text.
- **Visual Simplicity:** Use Mermaid flowcharts or tables instead of UI mockups.
- **Consistency:** Ensure terms and strategic targets align with the FR and NFR documents.

### Functional Requirements (`functional-requirements.md`)

**Purpose**: Serves as a high-level inventory of system capabilities ("What" the system does).

**Perspective**: Product Manager, System Analyst.

**Target Audience**: Project Managers, Architects, Lead Developers.

**Structure**:

- **Overview**: A brief statement defining the scope of the functional inventory and its purpose.
- **Categories**: Requirements must be grouped into logical functional domains using hierarchical
  headers (e.g., `## FR-1: Models`).
- **Out of Scope**: A clear list of features intentionally excluded from the current project or
  phase.

**Principles**:

- **Focus on Capability:** Describe what the system enables, not the UI procedure.
- **Consistency:** Use standardized terminology for model components across all documents.
- **Business-Oriented Grouping:** Organize requirements by logical domains rather than technical
  components.
- **Conciseness:** Keep descriptions brief. Detailed specs belong in supplementary documentation.
- **No UI/Technical Details:** Do not include button placements, database schemas, or raw code.

**Formatting**:

- **Requirement ID**: Use FR-X.Y format (e.g., `FR-1.1`).
- **Separation**: Do not bold IDs. Use a colon and a space after the ID (`FR-1.1: Text`).
- **Out of Scope**: Use bold labels with colons (e.g., **Model Authoring:**) without numeric IDs.

### Non-Functional Requirements (`non-functional-requirements.md`)

**Purpose**: Defines the quality attributes, operational constraints, and technical benchmarks ("How
well" the system performs).

**Perspective**: Architect, QA Lead.

**Target Audience**: Developers, DevOps, QA Engineers.

**Structure**:

- **Overview**: A brief statement defining the scope of quality standards and technical behavior.
- **Domains**: Requirements must be organized into six specific categories:
  - **NFR-1: Performance**: Speed, scale, responsiveness, memory, and payload size.
  - **NFR-2: Reliability**: Security, error handling, data protection, and state consistency.
  - **NFR-3: Usability**: Feedback, accessibility, visual standards, and responsive design.
  - **NFR-4: Quality**: Code standards, testing coverage (%), and architecture.
  - **NFR-5: Infrastructure**: No-Build constraints, hosting, browser support, and dependency
    integrity.
  - **NFR-6: Compliance**: Privacy and licensing.

**Principles**:

- **Measurability:** Every requirement must use specific units (s, ms, MB, FPS, %).
- **The 100/200ms Rule:** Focus on UI responsiveness (<100ms) and data feedback (<200ms).
- **Constraints:** Explicitly state technical boundaries like the No-Build Architecture.
- **Verifiable Benchmarks:** Use data-driven thresholds for different scales of data.
- **Objective Tone:** Use verifiable, technical statements instead of vague descriptors like "fast."
- **Consistency:** Section headers and ID patterns must match the functional requirements.

**Formatting**:

- **Requirement ID**: Use NFR-X.Y: format in standard weight (e.g., `NFR-1.1:`).
- **Requirement Title**: Use bold text followed by a period (`**Title.**`).
- **Statement**: A concise sentence containing measurable metrics and behavior.

### Traceability Matrix (`traceability-matrix.md`)

**Purpose**: Provides complete mapping across requirement levels to track implementation progress
and verification status ("What is built").

**Perspective**: Project Manager, QA Lead, Architect.

**Target Audience**: All Project Contributors and Stakeholders.

**Structure**:

- **Overview**: A brief statement defining the document’s role as the single source of truth for
  tracking the current state of implementation and testing.
- **Coverage Summary**: A high-level table showing the counts of FR, SR, TC, and Implemented items
  for each domain to visualize project progress.
- **Requirement Hierarchy**: A detailed mapping of each functional requirement to its linked system
  requirements and test cases.

**Principles**:

- **Verification Tracking:** Act as the authoritative index for what is built, tested, and ready for
  production.
- **Implementation Status:** Use the "Implemented" metric to show the actual verification progress
  against the total number of test cases.
- **Non-Contradiction:** Reference source documents only; do not redefine or override requirements
  within the matrix.
- **Synchronization:** Update the matrix whenever requirements are added, IDs are changed, or new
  test cases are validated.
- **Consistency:** Use domain names and identifiers that match the **Vision** and **Functional
  Requirements** documents.

**Formatting**:

- **Summary Table**: Use concise headers: Requirement Domain, FR, SR, TC, Implemented.
- **Requirement ID**: Use standard formats (FR-X.Y, SR-X.Y, TC-X.Y) in standard weight without
  bolding.
- **Hierarchical Links**: Use Markdown links to the original source files for every requirement and
  test case.
- **Nesting**: Use bulleted lists to represent the relationship between SRs (level 1) and their
  associated TCs (level 2).

### System Requirements (`system-requirements/*.md`)

**Purpose**: Provides detailed behavioral specifications and technical logic for specific features
("How" the system works).

**Perspective**: System Analyst.

**Target Audience**: Implementation (Dev) and QA teams.

**Structure**:

- **Scenarios**: A collection of detailed behavioral cases grouped under `## Scenarios`.
  - **SR Header**: Use `### SR-X.Y: Title` format.
  - **Description**: A brief introductory sentence explaining the scope of the scenario.
  - **Functional Requirements**: A `#### Functional Requirements` subsection listing the linked FR
    IDs with colon and description (e.g.,
    `- [FR-1.1](../functional-requirements.md#fr-1-models): Load models...`). Multiple FRs allowed.
  - **User Story**: A `#### User Story` subsection (As a... I want... So that...).
  - **Preconditions** (Optional): A `#### Preconditions` subsection listing specific system states
    or user data required before the steps begin.
  - **Steps**: A `#### Steps` subsection containing a numbered list of actions.
    - **Outcomes**: Observable system reactions, listed as an indented sub-list directly under each
      step (no "Expected:" label).
  - **Edge Cases**: A `#### Edge Cases` subsection with bulleted entries covering boundary
    conditions and error paths.
- **Business Rules**: A `## Business Rules` section containing deterministic domain logic, isolation
  rules, and constraints.
- **UI/UX**: A `## UI/UX` section covering feedback (loaders), visibility rules, context, and
  keyboard support.
- **Technical Notes**: A `## Technical Notes` section covering implementation-specific mechanisms
  (History API, Storage, Concurrency).

**Principles**:

- **Imperative Mood**: Steps must be written as direct commands (e.g., "Open...", "Select...",
  "Navigate...") without the word "User".
- **Observability**: Every step outcome must be a perceivable change in the interface or system
  state.
- **Technology-Agnostic Language**: Scenarios must be free of technical jargon like `localStorage`,
  `pushState`, or `abort controller`. Use functional equivalents (e.g., "browser storage",
  "navigation history is not extended"). Place exact technical specifications strictly in the
  **Technical Notes** or **Business Rules**.
- **Atomic Scenarios**: Each `SR-X.Y` scenario should represent one distinct functional capability
  or a tightly coupled set of behaviors.
- **Separation of Concerns**:
  - **Steps**: Focus on user intent and visible results.
  - **Business Rules**: Focus on logic and "truth" (e.g., priority, isolation, definitions).
  - **UI/UX**: Focus on interaction quality (debounce, feedback thresholds, accessibility).
  - **Technical Notes**: Focus on engineering artifacts (API types, specific storage keys, race
    condition handling).
- **Error Consistency**: Define a unified approach for common errors (e.g., "Fetch Failure") within
  the scenario's edge cases.
- **Single Source of Truth**: Ensure that business logic (like namespacing or loading priority) is
  documented in the Business Rules, not scattered across scenarios.
- **Traceability**: Every scenario must explicitly list its linked Functional Requirements in the
  `#### Functional Requirements` subsection with clickable links to `functional-requirements.md`.

**Formatting**:

- **Requirement ID**: Use `SR-X.Y` format.
- **Functional Requirements List**: Use Markdown links with colon:
  `- [FR-1.1](../functional-requirements.md#fr-1-models): Description`
- **Outcome Bullets**: Use a single dash `-` for system reactions under numbered steps.
- **Edge Case Labels**: Use bullet points with bold labels for the case title (e.g.,
  `- **Invalid Link**: description`).
- **Sub-headers**: Use `####` for Functional Requirements, User Story, Preconditions, Steps, and
  Edge Cases to maintain a clear hierarchy under the H3 scenario header.
- **Business Rule Bullets**: Use clean, descriptive bullet points for logical constraints.
- **Highlighting**: Use bold text only for emphasis of critical logical concepts (e.g., **clean
  default settings**, **model type**).

### Test cases (`test-cases/<feature>/tc-{sr-number}.md`)

- **Purpose**: Defines discrete, verifiable steps to validate that a feature meets its requirements.
- **Perspective**: QA Engineer / Developer.
- **Target Audience**: QA team, Developers (for manual and automated testing).
- **Structure**:
  - At file level: `**System Requirement**: [SR-X](../../system-requirements/<app>.md#sr-id)`
  - For each test scenario subsection (`##`):
    - `### Preconditions` (heading, not bold)
    - `### Steps` (heading with numbered list; steps followed by indented outcome bullets without
      `Expected:` label)
    - `### Test Data` (optional; heading with table)
- **Principles**:
  - **Verifiability**: Every step must have a clear, objective expected result.
  - **Negative Testing**: Must include scenarios for invalid inputs, errors, and edge cases.
  - **Traceability**: Each test case must use the `TC-[SR-number].[seq]` identifier format (e.g.,
    `TC-2.5.1`) and link back to an `SR`.
  - **File organization**: One file per system requirement, named `tc-{sr-number}.md` (e.g.,
    `tc-2.5.md` for SR-2.5), placed in a subfolder named after the system requirements file (e.g.,
    test cases for `system-requirements/graph.md` go into `test-cases/graph/`). The file title
    follows the pattern `# TC-{sr-number}: <description>`. Multiple test cases for the same SR are
    separate `##` sections within the same file.
  - **1:1 Coverage**: The number of TC files in a feature subfolder must equal the number of
    Acceptance Criteria in the corresponding system requirements file. Each SR identifier maps to
    exactly one TC file — no more, no less. When an SR is added or removed, a TC file must be
    created or deleted accordingly.
  - **Concreteness**: Use specific element names, types, and values from the test data throughout
    the steps and expected results. Avoid generic placeholders such as "any node" or "some element".
  - Do not include technical implementation details of how the test is automated (keep it
    descriptive).

### Common Standards (`common/*.md`)

- **Purpose**: Establishes global conventions and platform-wide architectural patterns.
- **Perspective**: Lead Engineer / Architect.
- **Target Audience**: All technical contributors.
- **Principles**:
  - Define the "Rules of the Game," including coding conventions, API standards, error handling, and
    documentation rules.
  - Serve as the foundation for all application-specific requirements.
  - Do not include application-specific business logic or individual feature requirements.

Each file in `common/` covers a distinct technical domain:

#### `architecture.md`

- **Purpose**: Defines technology choices, structural organization, and core principles that guide
  all implementation decisions.
- **Target Audience**: All technical contributors.
- **Structure**:
  - **Context & Scope**: What is covered and what is explicitly out of scope.
  - **Architectural Drivers**: Quality attributes and constraints.
  - **Technology Selection**: Key technology decisions with rationale and implications.
  - **System Structure**: Application and module organization, folder layout, dependency rules.
  - **Core Architectural Principles**: The fundamental design rules the entire codebase follows.
  - **Data Architecture**: State typology, storage strategy, state management pattern.
  - **Performance Considerations**: Load, interaction, and rendering targets.
  - **Security Principles**: Token handling, injection prevention, transport security.

#### `domain-model.md`

- **Purpose**: Defines the canonical domain model — core entities, data transformation rules, and
  invariants that all code processing domain data must maintain.
- **Target Audience**: Developers, architects.
- **Structure**:
  - **Core Entities**: Domain objects with fields, constraints, and derived properties.
  - **Data Transformation Contract**: Input format, transformation rules, parsing strategy.
  - **Filtering & Selection Semantics**: Selection state definition and resolution algorithm.
  - **State Classification**: Domain state, UI state, shareable state.
  - **Data Integrity Invariants**: Postconditions that must hold after every transformation.
  - **Mutability Policy**: Rules for when data may and may not be mutated, with rationale.

#### `coding-conventions.md`

- **Purpose**: Establishes code quality and consistency rules for HTML, CSS, and JavaScript.
- **Target Audience**: All developers.
- **Structure**:
  - **General Principles**: Formatting, naming conventions, documentation requirements.
  - **Localization**: Rules for UI strings vs. developer logs.
  - **HTML**: Semantic integrity, accessibility, asset loading, data handling.
  - **CSS**: Architectural principles, units and layout.
  - **JavaScript**: Architecture, functional programming, state and error handling, DOM interaction.
  - **Dependencies**: Dependency management rules.
  - **Testing**: Unit and integration testing requirements.

#### `api-contract.md`

- **Purpose**: Defines standards for API communication between frontend clients and backend services
  — conventions for requests, responses, errors, and security.
- **Target Audience**: Backend implementers, frontend developers, QA engineers.
- **Structure**:
  - **Authentication & Authorization**: Supported mechanisms, client responsibilities.
  - **Request Standards**: HTTP methods, headers, query parameters, request bodies.
  - **Response Standards**: Status codes, response envelope format, pagination, error format.
  - **Caching**: Cache strategy and headers.
  - **Rate Limiting**: Limit enforcement and client backoff behavior.
  - **CORS**: Cross-origin configuration requirements.
  - **Data Privacy & Security**: Transport security, PII handling, compliance.
  - **Monitoring & Observability**: Required metrics, request tracing.
  - **Implementation Guidance**: Checklists for backend and frontend teams.
  - **Contract Testing**: Testing responsibilities for both sides.

#### `authentication.md`

- **Purpose**: Defines standards for client-side authentication — token handling, UX patterns, and
  API integration.
- **Target Audience**: Frontend developers, QA engineers.
- **Structure**:
  - **Security Principles**: Token storage, transmission, and lifetime rules.
  - **User Interface**: Anonymous and authenticated UI states.
  - **Authentication Flow**: Supported flows and edge cases.
  - **Token Expiry Handling**: Response to expired or invalidated tokens.
  - **Sign-Out**: Sign-out procedure and state cleanup.
  - **API Integration**: Authenticated request pattern, user profile endpoint.
  - **State Persistence Considerations**: What auth state persists vs. clears on reload.
  - **Business Rules**: Access control policy, credential storage rules.

#### `error-handling.md`

- **Purpose**: Defines consistent approaches to error handling, graceful degradation, and user
  communication.
- **Target Audience**: Frontend developers, QA engineers.
- **Structure**:
  - **Core Principles**: Degradation strategy, message quality, recovery paths, severity model.
  - **Error Categories & Presentation**: Mapping of category → severity → presentation pattern →
    recovery strategy.
  - **Standard Presentation Patterns**: Per-pattern requirements and message guidelines.
  - **Specific Error Scenarios**: Concrete handling rules for each error category.
  - **Network Request Pattern**: Consistent fetch flow with loading indicators and error
    propagation.
  - **Error Logging Standards**: Log levels, prefixing, contextual information.
  - **User-Friendly Message Guidelines**: DO/DON'T examples.
  - **Recovery Flow Patterns**: Retry, fallback with state preservation, corruption recovery.
  - **Testing Error Scenarios**: Required test cases.
  - **Implementation Checklist**: Pre-release verification items.

#### `state-management.md`

- **Purpose**: Governs client-side state management, browser storage usage, and URL-based state
  encoding.
- **Target Audience**: Frontend developers.
- **Structure**:
  - **Core Principles**: Persistence strategy, graceful degradation, URL vs. storage boundary.
  - **Storage Keys & Schemas**: Key naming rules, namespacing, storage access requirements.
  - **Safe Storage Operations**: Reading, writing, deserialization & recovery patterns.
  - **URL State Encoding**: What belongs in the URL, parameter design, decode/apply sequence.
  - **Theme & User Preference Persistence**: Restoration timing, update-on-change flow.
  - **State Persistence Patterns**: Load and save patterns, debouncing, corruption recovery.
  - **Resource & Content Persistence**: Recent items, stale data cleanup.
  - **Multi-Tab Synchronization**: Considerations and trade-offs.
  - **Testing**: Required test scenarios.

#### `ui-ux-guidelines.md`

- **Purpose**: Defines standards for UI components, visual design, interactions, and accessibility.
- **Target Audience**: Frontend developers, QA engineers.
- **Structure**:
  - **Design Principles**: Core UX values all interfaces must follow.
  - **Visual Design Standards**: Color and contrast rules, color palette system, typography, touch
    targets, spacing and layout.
  - **Component Behavior Standards**: Standard behavior for navigation, accordions, controls,
    modals, theme switching, toasts, loading indicators, error overlays.
  - **Accessibility Standards**: Keyboard navigation, screen reader support, focus management.
  - **Animation Standards**: Timing, easing, motion preference compliance.
  - **Testing Requirements**: Accessibility and interaction checklist.

#### `glossary.md`

- **Purpose**: Defines shared terminology used across all documentation to ensure consistent
  language.
- **Target Audience**: All project contributors.
- **Structure**: Alphabetically ordered list of terms with concise definitions. Add a term whenever
  a concept requires consistent interpretation across documents.
  - Do not duplicate definitions from external standards; reference them instead.
  - Keep definitions technology-agnostic where possible.

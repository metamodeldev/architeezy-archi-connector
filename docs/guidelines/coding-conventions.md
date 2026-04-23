# Java Coding Conventions

## 1. General Principles

The primary requirement for code is clarity. Clear code ensures that errors are easier to find, bugs
are harder to commit, and maintenance is sustainable. Code should be written for the human reader
first and the machine second.

## 2. Naming Conventions

### 2.1 English Word Order

Use standard English adjective-noun order. The main noun must be at the end, with modifiers
preceding it.

- **Correct:** `TaskHistoryExportService`.
- **Incorrect:** `ServiceTaskExportHistory`.

### 2.2 No Prefixes or Suffixes

Avoid metadata indicators in names. Let the language and the package structure provide context.

- **Implementations:** Do **not** use the `Impl` suffix. Use descriptive names (e.g.,
  `DatabaseModelStorageService`).
- **Data Types:** Do not include types in names (e.g., use `users`, not `userList`).

### 2.3 Abstract Classes

Names of abstract classes **must** start with the word `Abstract` (e.g., `AbstractTaskController`).

### 2.4 No Abbreviations

Words in names **must** be written in full.

- **Forbidden:** `App`, `Doc`, `Db`, `Msg`, `Elem`.
- **Allowed:** `Id` (globally accepted).
- **Exception:** Short-lived lambda parameters (e.g., `it`, `e`) are allowed where context is
  immediate.

### 2.5 Acronym Casing

Treat acronyms as words. Only the first letter is capitalized.

- **Correct:** `JsonParser`, `HttpServer`.
- **Incorrect:** `JSONParser`, `HTTPServer`.
- **Exception:** Canonical identifiers may retain their casing (e.g., `OAuth` in `OAuthManager`).

### 2.6 US English

Use American spelling for all identifiers and documentation (e.g., `Color`, `Localization`).

## 3. Documentation and Localization

### 3.1 Mandatory Javadoc

- All **non-private** classes, methods, and fields **must** have Javadoc documentation.
- `@Override` methods do not require Javadoc if the parent documentation is sufficient.
- All documentation **must** be written in English.

### 3.2 Intent over Comments

Code should be self-documenting. Use comments only to explain **"why"** (non-obvious logic), not
**"what"**.

### 3.3 Localization

- **UI Strings:** All strings visible to the end-user **must** be localized using ResourceBundles.
- **Developer Logs:** Messages intended for developers (exceptions, trace logs) **must not** be
  localized.

## 4. Architectural Principles

### 4.1 Domain-Driven Design (DDD)

Group code first by **Domain** (e.g., `com.project.user`), then by **Layer** (e.g., `services`,
`ui`).

### 4.2 Class Member Order

1. Constants (`static final`)
2. Fields (instance variables)
3. Constructors
4. Getters and Setters
5. Main Logic Methods
6. Resource Cleanup (`dispose`, `close`)

_Ordering Logic:_ Place called methods below the caller. Group related methods (e.g., `add`/`remove`
listeners).

### 4.3 Package Organization

- **Pluralization:** Use plural forms for packages that act as containers for similar peer
  components: `services`, `wizards`, `repositories`, `dialogs`.
- **Separation of Concerns:** Maintain a clear boundary between UI, Business Logic, and Data Access.
  UI components should reside in dedicated sub-packages (e.g., `ui`, `presentation`).
- **Internal Encapsulation:** Use `.internal` sub-packages to house implementation details that
  should not be part of the public API. Prefer package-private visibility for classes within these
  packages.
- **Persistence Boundary:** Logic responsible for I/O and serialization should be separated from the
  core domain model to ensure the model remains pure and testable.

## 5. Logic and Implementation

### 5.1 Early Return Pattern

Handle validation and edge cases at the start. Prohibit large `if` blocks wrapping entire function
bodies.

### 5.2 Error Handling

- **Immediate Exceptions:** Throw exceptions immediately upon failure.
- **No Silent Failures:** Do not return `null` or empty `Optional` to hide unexpected errors.
- **Optional Usage:** Use `Optional` only for values that are legitimately absent in a success
  scenario.

### 5.3 Encapsulation

- **Minimal Public API:** Keep methods `private` or `package-private` by default.
- **Immutable Exposure:** Return immutable collections or defensive copies from getters.
- **Variable Locality:** Declare variables as close to their usage as possible.

### 5.4 Resource Management

- **Try-with-resources:** Mandatory for all I/O streams, database connections, and readers.
- **Explicit Disposal:** Any resources that utilize native OS handles (e.g., graphics, fonts, or
  window handles) **must** be explicitly disposed of when they are no longer needed to prevent
  memory leaks.

## 6. Concurrency and Threading

### 6.1 Non-blocking Operations

Long-running operations, such as network requests or heavy I/O, **must** be executed on background
threads to prevent application freezes.

### 6.2 UI Thread Safety

Interactions with UI components **must** be synchronized with the main UI thread. Use the
appropriate platform-specific utilities to dispatch UI updates from background tasks.

### 6.3 Progress Reporting

Long-running background tasks should provide a mechanism for progress feedback to ensure a
responsive user experience.

## 7. Language Standards

### 7.1 Data Modeling

Use `record` instead of `class` for immutable data carriers (DTOs). This ensures immutability and
eliminates boilerplate.

### 7.2 Enums

Always use `==` for Enum comparison. It is null-safe and checked at compile time.

### 7.3 Switches and Strings

- Use arrow-syntax `switch` expressions for better readability and exhaustiveness.
- Use Text Blocks (`"""..."""`) for multi-line strings, SQL, or JSON templates.

### 7.4 Functional Programming

Keep lambdas to 1-2 lines. Use method references (e.g., `this::process`) for complex logic.

## 8. Logging and Dependencies

### 8.1 Logging

- **No Standard Output:** Prohibit `System.out.println()`.
- **System Logs:** Use a proper logging framework or the host environment's logging service.
- **Log Levels:** Use `ERROR` for failures, `WARNING` for unexpected but non-fatal states, and
  `INFO` for major lifecycle events.

### 8.2 Dependency Management

- **Minimalism:** Do not add third-party libraries for tasks easily achieved with the Java Standard
  Library.
- **Circular Dependencies:** Prohibit circular dependencies between packages and modules. Generic
  packages must never reference specific high-level entities.

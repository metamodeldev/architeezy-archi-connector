# Java Coding Conventions

## 1. General Principles

The primary requirement for code is clarity. Clear code ensures that errors are easier to find, bugs
are harder to commit, and maintenance is sustainable.

## 2. Naming Conventions

### 2.1 English Word Order

Use standard English adjective-noun order. The main noun must be at the end, with modifiers
preceding it.

- _Correct:_ `TaskHistoryExportService` (A service for exporting task history).
- _Incorrect:_ `ServiceTaskExportHistory`.

### 2.2 No Prefixes or Suffixes

Avoid metadata indicators in names.

- **Interfaces:** Do **not** use the `I` prefix (e.g., use `ModelStorageService`, not
  `IModelStorageService`).
- **Implementations:** Do **not** use the `Impl` suffix. Use descriptive names for specific
  implementations (e.g., `DatabaseModelStorageService`).
- **Data Types:** Do not include types in names (e.g., use `users`, not `userList` or `userArr`).

### 2.3 Abstract Classes

Names of abstract classes **must** start with the word `Abstract` (e.g., `AbstractTaskController`).

### 2.4 No Abbreviations

Words in names **must** be written in full.

- _Forbidden:_ `App`, `Doc`, `Db`, `Msg`, `Elem`.
- _Allowed:_ `Id` (globally accepted).
- _Exception:_ Short-lived lambda parameters (e.g., `it`, `e`) are allowed where context is
  immediate.

### 2.5 Acronym Casing

Treat acronyms as words. Only the first letter is capitalized.

- _Correct:_ `JsonParser`, `HttpServer`.
- _Incorrect:_ `JSONParser`, `HTTPServer`.

### 2.6 US English

Use American spelling for all identifiers and documentation (e.g., `Color`, `Localization`,
`Initialize`).

## 3. Documentation and Localization

### 3.1 Mandatory Javadoc

- All **non-private** classes, methods, and fields **must** have Javadoc documentation.
- `@Override` methods do not require Javadoc if the parent documentation is sufficient.
- Documentation **must** be written in English.

### 3.2 Intent over Comments

Code should be self-documenting. Use comments only to explain **"why"** (non-obvious logic or
hacks), not **"what"** (which should be clear from the code itself).

### 3.3 Localization

- **UI Strings:** All strings visible to the end-user **must** be localized using ResourceBundles.
- **Developer Logs:** Internal error messages and exceptions intended for developers **must not** be
  localized and should remain in English.

## 4. Architectural Principles

### 4.1 Domain-Driven Design (DDD)

Group code first by **Domain** (e.g., `com.project.user`, `com.project.task`), then by **Layer**
(e.g., `service`, `controller`, `repository`). Avoid giant flat packages like `com.project.services`
containing all services for the whole app.

### 4.2 Class Member Order

To improve readability, maintain the following order within a class:

1. Constants (`static final`)
2. Fields (instance variables)
3. Constructors
4. Getters and Setters
5. Main Logic Methods
6. Resource Cleanup Methods (e.g., `dispose`, `close`)

**Ordering Logic:**

- If method A calls method B, place B below A.
- Group related methods (e.g., `addListener` should be immediately followed by `removeListener`).
- Within a group, order by visibility: `public`, `protected`, `package`, `private`.

### 4.3 Variable Locality

Declare and initialize variables as close to their first usage as possible. Prefer initialization in
a single expression.

## 5. Logic and Implementation

### 5.1 Early Return Pattern

Handle edge cases and validation at the start of the function. **Prohibit** large `if` blocks
wrapping the entire function body.

- _Structure:_ 1. Validation -> 2. Pre-processing -> 3. Main Action -> 4. Return result.

### 5.2 Error Handling

- **Immediate Exceptions:** If an error occurs, throw an exception immediately.
- **No Nulls/Optionals for Errors:** Do **not** return `null` or an empty `Optional` to indicate a
  failure or "not found" state if that state is unexpected. This prevents `NullPointerException` and
  ensures bugs aren't hidden.
- **Optional Usage:** Use `Optional` only for truly optional values. Prefer naming such methods
  `findSomething()` instead of `getSomething()`.

### 5.3 Functional Programming (Lambdas)

- Keep lambda expressions simple. If logic exceeds 1-2 lines, move it to a named method and use a
  method reference (e.g., `.forEach(this::processItem)`).
- Prefer standard `for` loops over `forEach()` if it makes debugging (stack traces) easier or code
  cleaner.

### 5.4 Enum Comparison

Always use `==` to compare Enums, not `.equals()`. This provides compile-time type safety and is
null-safe.

### 5.5 Encapsulation and State

- **Minimal Public API:** Keep the number of public methods to a minimum. Use `private` or
  `protected` unless external access is strictly necessary.
- **Fields for Persistence:** Use class fields only for the long-term state of an object. Do not use
  fields to pass data between methods; use method arguments instead.
- **Immutable Exposure:** Getters should return immutable collections or clones to prevent external
  mutation of internal state.

## 6. Dependencies and Testing

### 6.1 Circular Dependencies

Prohibit circular dependencies between classes and modules (both explicit via `pom.xml` and implicit
via logic). Generic modules must never reference specific entities.

### 6.2 Library Minimalism

Do not add third-party libraries (e.g., Apache Commons, Guava) for simple tasks that can be
implemented natively in a few lines of Java.

### 6.3 Testing

- **Unit Testing:** All utility functions and business logic must have JUnit tests.
- **Integration Testing:** Critical workflows must be verified via integration tests.

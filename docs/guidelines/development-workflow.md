# Development Workflow

## Adding a Feature or Modifying an Application

1. Update the product's vision in `docs/vision.md` if required. Ensure the changes comply with
   [docs-guidelines.md](docs-guidelines.md).
2. Add a new functional requirement group to `docs/functional-requirements.md` if the feature
   represents a new business capability. Ensure the changes comply with
   [docs-guidelines.md](docs-guidelines.md).
3. Add a functional requirement to the appropriate group in `docs/functional-requirements.md`.
   Ensure the changes comply with [docs-guidelines.md](docs-guidelines.md).
4. Update `docs/non-functional-requirements.md` if new quality standards, performance targets, or
   technical constraints are introduced. Ensure the changes comply with
   [docs-guidelines.md](docs-guidelines.md).
5. Create a corresponding system requirement file in `docs/system-requirements/` if a new functional
   requirement group was added. Ensure the changes comply with
   [docs-guidelines.md](docs-guidelines.md).
6. Add new scenarios to the system requirement file in `docs/system-requirements/` if new functional
   behaviors or edge cases are introduced. Ensure the changes comply with
   [docs-guidelines.md](docs-guidelines.md).
7. Update existing scenarios, business rules, UI/UX sections, or technical notes in
   `docs/system-requirements/` if existing feature behavior is modified. Ensure the changes comply
   with [docs-guidelines.md](docs-guidelines.md).
8. Create a corresponding directory in `docs/test-cases/` if a new system requirement file was
   created.
9. Add new test case files to `docs/test-cases/` or update existing files for every new or modified
   acceptance criteria. Ensure the changes comply with [docs-guidelines.md](docs-guidelines.md).
10. Update automated e2e tests in `src/tests/e2e/` to reflect changes in manual test cases and
    system requirements.
11. Update the `docs/traceability-matrix.md` file by running the
    `scripts/traceability-matrix.py docs/[app-name] src/tests/e2e` script.
12. Update the application code in `src/` to implement the changes. Ensure the changes comply with
    [coding-conventions.md](coding-conventions.md) and [ui-ux-guidelines.md](ui-ux-guidelines.md).
    Ensure all tests pass: `bun run test`.
13. Update unit tests for all modified or new pure functions in `src/tests/unit/`. Ensure all tests
    pass: `bun run test:unit`.
14. Format code using `bun run format` and automatically fix linting issues with `bun run lint:fix`.
15. Fix any remaining linter errors and warnings: `bun run lint`.

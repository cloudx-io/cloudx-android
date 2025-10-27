# Repository Guidelines

## Project Structure & Module Organization
- `sdk/` holds the public CloudX SDK API under `io.cloudx.sdk` and internal logic under `io.cloudx.sdk.internal`.
- `adapter-cloudx/` and `adapter-meta/` implement ad network bridges mirroring the adapter interfaces in `sdk/internal/adapter`.
- `app/` is the demo client that wires the SDK and adapters together; use it when validating end-to-end ad flows.
- Reusable scripts live in `scripts/`; docs and policy text are under `docs/`.

## Build, Test, and Development Commands
- `./gradlew build` compiles and runs unit tests for every module; rely on it before opening a PR.
- `./gradlew :sdk:build` (or `:adapter-cloudx:build`, `:adapter-meta:build`) targets a single module for faster iteration.
- `./gradlew :app:installDebug` deploys the sample app to a connected device or emulator.
- `./gradlew publishToMavenLocal` stages artifacts for integration testing in downstream apps.
- `./gradlew clean` clears the build cache when Gradle metadata becomes stale.

## Coding Style & Naming Conventions
- Kotlin source uses 4-space indentation, explicit visibility, and descriptive, PascalCase class names with camelCase members.
- Keep public API surface in `sdk/` carefully reviewed; add KDoc when introducing new entry points.
- Prefer data classes plus sealed hierarchies for modeling bid or error states already present in `sdk/internal`.
- Run `./gradlew lint` (or module-specific `:sdk:lint`) before landing changes that touch Android resources.

## Testing Guidelines
- Unit tests belong in the matching `src/test/java` package; mirror the production package path.
- Extend `CXTest` for all Kotlin tests so MockK, logging, and coroutine dispatchers are configured.
- Use truth-style assertions (`com.google.truth.Truth.assertThat`) and keep test names descriptive, e.g. `loadsBid_whenAuctionReturnsWinner`.
- Execute `./gradlew test` locally; add targeted module tasks (`:sdk:test`) when iterating on a specific area.

## Commit & Pull Request Guidelines
- Follow the existing history: imperative, present-tense subjects (`Fix adapter timeout handling`), optionally referencing PR numbers.
- Squash commits with a clear summary and mention any follow-up TODOs in the PR description instead of the commit body.
- PRs should include: scope description, linked Jira/GitHub issue, test plan (`./gradlew test`, device runs), and screenshots or logs for UI or integration changes.
- Flag any configuration updates to `local.properties`, `keystore.properties`, or credentials so reviewers can replicate the environment without exposing secrets.

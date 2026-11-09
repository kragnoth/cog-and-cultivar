# Contributing to Cog & Cultivar

Thanks for helping! Read [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) first; it's short.

## Setup

- JDK 21. Gradle is provided by the wrapper.
- `./gradlew build` compiles and runs all checks.
- `./gradlew runClient` starts a dev client, and `./gradlew runServer` starts a dev server.
- `./gradlew runData` regenerates `src/generated`. Commit the result.
- `./gradlew runGameTestServer` runs the dedicated-server tests, as CI does.

## What makes a PR easy to merge

- **One feature per PR**, in its own `content/<feature>` or `compat/<modid>` package, registered with one line in `CultivarModules` (and `CultivarClient.MODULES` if needed).
- **Data over code.** New plants, soils, mutations and influences are datapack entries generated through datagen, not hand-written JSON and not Java branches.
- **Backward compatible data.** New record fields use `optionalFieldOf` with a default. Don't rename JSON keys or reorder enum constants.
- **Server-safe.** No client imports outside `client/`. No config-dependent registration.
- **Cheap on the server.** No new per-block tickers. Use `ServerWorkQueue` for scans.
- **Network changes bump `PROTOCOL_VERSION`.**
- **Tests:** add a game test in `gametest/` for logic that can break silently (breeding odds, influence stacking).
- **Art:** 16×16, following the Excalibur-based style and colour ramps in `docs/design-spec.md` §4. Art derived from Excalibur is not MIT-licensed; see `LICENSE`.
- **Clean room:** don't copy code from AgriCraft or other mods. Implement from the spec.

## Style

- Java 21, 4-space indent, UTF-8, LF (see `.editorconfig`).
- Parchment parameter names, no wildcard imports.
- Javadoc on anything in `api/`.

## CI

Every PR runs: compile + `check` (with the sidedness guard), a datagen staleness check, and dedicated-server game tests. All three must pass.

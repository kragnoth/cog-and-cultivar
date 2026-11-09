## What does this change?

<!-- One feature or fix per PR. Link the spec section (docs/design-spec.md §x) if relevant. -->

## Checklist

- [ ] Feature code is in its own `content/<feature>` or `compat/<modid>` module, registered in `CultivarModules`
- [ ] No client-only imports outside `client/` (`./gradlew check` passes)
- [ ] New data is generated via datagen and `src/generated` is committed (`./gradlew runData`)
- [ ] New record fields are optional with defaults; no renamed JSON keys or reordered enums
- [ ] `CultivarNetwork.PROTOCOL_VERSION` bumped if payloads changed
- [ ] No new per-block tickers; large scans use `ServerWorkQueue`
- [ ] Tested on a dedicated server (CI `runGameTestServer`, plus manual if it's gameplay-visible)
- [ ] Clean-room: no code copied from other mods

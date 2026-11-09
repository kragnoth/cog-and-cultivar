# Architecture

How Cog & Cultivar is put together, and where new code goes. Gameplay design lives in [`design-spec.md`](design-spec.md).

## Layers

```
com.kragnoth.cultivar
├── Cultivar.java            common entry: config, registries, network, datagen, modules
├── api/                     public, stable-ish types (addons depend on these)
│   ├── CultivarRegistries   every registry key + static registry instance
│   ├── genetics/            Stat, AllelePair, Genome (data component)
│   ├── plant/               Plant, Product, Mutation, Weed, Fertilizer (datapack records)
│   ├── soil/                Soil, SoilLevels (humidity/acidity/nutrient scales)
│   ├── condition/           GrowthCondition + type registry (dispatch codec)
│   ├── modifier/            PlantModifier + type registry (dispatch codec)
│   └── influence/           CropInfluence, InfluenceEffects, InfluenceScaling (beehives, sprinklers, heaters)
├── core/                    framework; feature code never goes here
│   ├── module/              CultivarModule interface + CultivarModules index
│   ├── registry/            NeoForge registry wiring
│   ├── config/              server (gameplay) config
│   ├── network/             single versioned payload channel
│   ├── work/                ServerWorkQueue: time-sliced server jobs
│   └── datagen/             datagen entry + DataBootstraps collector
├── content/<feature>/       one package per feature module (core, trellis, irrigation, conservatory...)
├── compat/<modid>/          one package per integration (create, jei, jade...)
├── mixin/                   mixins (client ones under mixin/client)
├── gametest/                dedicated-server tests run in CI
└── client/                  client-only entry, ClientModule index, client config, renderers
```

## Modules

A feature is a `CultivarModule` (common) plus, optionally, a `ClientModule` (client). Each module:

- owns its `DeferredRegister`s (no shared mega-registry file, so PRs don't conflict);
- registers payloads through `registerPayloads` (already versioned);
- contributes datagen through `bootstrapData`;
- is listed once in `core/module/CultivarModules.ALL` (and `client/CultivarClient.MODULES` if it has client code).

Optional integrations implement `shouldLoad()` with `ModList.get().isLoaded(...)` and keep every class reference to the other mod out of the module class itself.

## Data vs code

| Kind | Where | Changes need |
|---|---|---|
| Plants, soils, mutations, weeds, fertilizers, crop influences | Datapack registries (`data/<ns>/cultivar/<registry>/*.json`) | JSON only; modpacks can override |
| Stats, condition types, modifier types, influence scaling types | Static registries | Java; addons register with a `DeferredRegister` |
| Gameplay tuning | `serverconfig/cultivar-server.toml` (per world, synced) | Config |
| Visual preferences | `config/cultivar-client.toml` | Config |

Records gain new fields **only** as `optionalFieldOf(..., default)`, so existing JSON and saved items keep loading. Enum scales are append-only.

## Server safety rules

1. **Client code lives under `client/`.** `./gradlew check` runs `checkSidedness`, which fails the build if any other package imports `net.minecraft.client`, `com.mojang.blaze3d` or `net.neoforged.neoforge.client`.
2. **CI boots a real dedicated server** (`runGameTestServer`) on every PR. A leaked client class crashes it.
3. **Registries are identical on both sides.** Never skip registering a block or item based on config; disable behaviour with server config instead.
4. **One network channel, versioned.** Bump `CultivarNetwork.PROTOCOL_VERSION` on any payload change. Prefer block-entity sync and data components over custom packets.
5. **Server state is never trusted from the client.** Payload handlers validate distance, permissions and that the target block is loaded.

## Performance rules

- Crops grow on `randomTick`. No per-crop block-entity ticker.
- Anything that scans more than a handful of blocks goes through `ServerWorkQueue` (budget set in server config).
- Nearby-source lookups (beehives) use the POI index, not block scans, and are cached on the crop.
- Growth requirements are cached per crop and invalidated by neighbour or soil change.
- Client-only polish (particles, animations) never adds server work.

## Tests

- `gametest/` holds dedicated-server game tests. Use the `cultivar:empty` template or add structure files under `data/cultivar/structure/`.
- Run locally with `./gradlew runGameTestServer`.

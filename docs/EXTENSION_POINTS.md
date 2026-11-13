# Extension points

How features plug into Cog & Cultivar without editing shared classes. Addons can use the `api/` hooks too.

## Layering rules (enforced)

- `api/**` imports only `api/` plus Minecraft, NeoForge, DFU and the JDK. Addons compile against it alone.
- `content/core/**` (shared rules: conditions, genetics, growth math, lookups) never imports a feature module, `client/` or `compat/`.
- `core/**` (infrastructure) reaches `content/core/` at most; only the module list and datagen bootstraps wire features in.
- Feature modules (`content/<feature>`) may use `api/`, `core/`, `content/core/` and, where unavoidable, `content/crop`.
  Prefer an API hook over a direct import of another feature.

## Hooks and recipes

**Trellis-free (decorative) crops.** A `field_crop` block + block entity owning a `PlantCore` with a `Host` whose
`blocksAvailable()` is the plant's height room and `strengthBonus()` is 0. Strength (tolerance, Hardy) and Gain
(yield) come for free from `PlantCore`; no weeds, no breeding, no spread (those live only in the trellis entity).
Implement `PlantHolder` on the block and conditioners, the Sprinkler and Create harvesters work with no further
changes. Planting rules: `PlantCore.response(false)` before placing, the same check the trellis uses.

**Right-click replant / break-harvest.** Right-click: `PlantHolder.harvestInPlace` (harvest, back to the
after-harvest stage). Breaking: `PlantCore.collectBreakDrops` (seed plus a full harvest when ripe, like wheat).
For other mods' right-click-harvest support, give the block a normalised vanilla `age` (0–7) state and the
`minecraft:crops` tag so they recognise it as a crop.

**Farmland wet without water.** Register a `MoistureSource` (e.g. an olla or irrigation channel within 4 blocks):
vanilla farmland keeps its moisture and reads as moist everywhere, including Cultivar humidity.

**New humidity / light sources** (sprinklers, channels, grow lamps): register a `HumidityModifier` or
`LightModifier` from the feature's module; nothing in core changes.

**New per-tick mechanics** (seasons, temperature, pests): implement `CropTickPhase` and call
`CropTickPhases.register(id, priority, phase)` from the feature's module. Work through `ctx.plant()` and it applies to
every holder; use `ctx.trellis()` for trellis-only behaviour. A holder that owns a `PlantCore` (e.g. `field_crop`) runs
the same pipeline by building a `CropTickContext` in its `randomTick`.

**New commands**: override `registerCommands(root)` in the module and add a literal with its own `requires`.

**New plot-style machines**: hold a `PlotTracker<YourBE>` and delegate three methods to it.


# AgriCraft — Whiteroom Breakdown & Steampunk/Dieselpunk Redesign Plan (NeoForge 1.21.1 + Create)

> **Purpose:** a clean-room specification of every AgriCraft feature: what it does, how it works (mechanics and formulas pulled from the code), and how to rebuild it as a new NeoForge 1.21.1 mod, re-themed for a steampunk/dieselpunk world with deep Create integration and Excalibur-based art.
>
> **Whiteroom rule for implementation:** this document describes behaviour, numbers and architecture. It contains no AgriCraft code. Anyone implementing the new mod should work from this spec, not from the original source (AgriCraft is MIT-licensed, but a clean reimplementation avoids carrying over its InfinityLib dependency and its known flaws).

---

## 0. Sources examined

| Source | Version | Role in this doc |
|---|---|---|
| `AgriCraft-master.zip` (your upload) | **1.18.2, v3.1.6**, Forge 40.2.2, InfinityLib | Primary "full-feature" reference: irrigation, greenhouse, grate, villages, animal genes, seed bag enchant |
| GitHub `1.21` branch (commit `b142a77`, Apr 2025) | NeoForge 1.21.1, ~15k LOC | Current port. Shows the datapack-registry/codec architecture. **Large parts were dropped** (see §1) |
| GitHub `1.12.2` branch | Forge 1.12.2 | Older full-feature era: Water Pad, Seed Storage Controller, ComputerCraft Peripheral, hardcore stat mode |
| `NeoForge-1.21.1.zip`, `minecraft_decompiled.zip` | 1.21.1 | API verification (capabilities, attachments, datapack registries, `CropGrowEvent`, `FarmBlock`) |
| GitHub `Creators-of-Create/Create`, branch `mc1.21.1/dev` | **Create 6.0.11**, Ponder 1.0.85, Flywheel 1.0.6 | Verified every Create API touchpoint in §5.7 |
| Modrinth `excal`, **Excalibur V26.3_01** (by Maffhew) | 16×16, 6,051 textures, has `overlay_1.21.1` | Base-texture picks and sampled palettes in §4 |

**Method note:** I traced behaviour through the source rather than running a client. The 1.18 build needs InfinityLib plus a Forge 1.18 toolchain, and you asked not to launch headless clients unless requested. Every number below comes from code or config defaults. Where the code disagrees with its own comments, the doc follows the code and flags the difference.

---

## 1. Feature lineage across versions

| Feature | 1.12.2 | 1.18.2 (3.1.6) | 1.21 port | Notes |
|---|:-:|:-:|:-:|---|
| Crop sticks (wood/iron/obsidian), cross crops | ✅ | ✅ | ✅ | |
| Genome (diploid, 6 stats) | 3 stats (growth/gain/strength) | ✅ 6 stats | ✅ 6 stats | |
| Mutations / cloning | ✅ | ✅ | ✅ | |
| Growth requirements (soil/light/biome/dim/season/fluid/nearby blocks) | partial | ✅ | ✅ | |
| Weeds | ✅ | ✅ | ✅ | |
| Fertilizers (JSON) | basic | ✅ | ✅ (datapack registry) | |
| Plant callbacks/modifiers (thorns, wither, tree, fungus…) | – | ✅ | ✅ | |
| Animal-attractant genes | – | ✅ | ❌ | Dropped |
| Seed Analyzer | ✅ GUI | ✅ in-world camera + GUI | ✅ | |
| Journal | ✅ | ✅ (3D in-hand book) | ✅ | |
| Magnifying glass (+ helmet) | ✅ | ✅ | ✅ | |
| Trowel / Clipper / Rake / Seed Bag | ✅ / ✅ / ✅ / – | ✅ all | ✅ all | |
| Seed-bag enchantment gate | – | ✅ | ❌ | Dropped |
| **Irrigation tank (multiblock)** | ✅ | ✅ | ❌ | **Dropped** |
| **Channels, hollow channels, valves** | ✅ | ✅ | ❌ | **Dropped** |
| **Sprinkler** | ✅ | ✅ | ❌ | **Dropped** |
| **Water Pad** | ✅ | ❌ | ❌ | |
| **Greenhouse + monitor** | – | ✅ (effects not wired up) | ❌ | See §2.17 |
| **Grate** (climbable, vines) | ✅ | ✅ | ❌ | |
| **Seed Storage / Controller** | ✅ | stub class | ❌ | |
| **ComputerCraft peripheral** | ✅ | – | ❌ | |
| Village greenhouses (5 biomes ×2) | – | ✅ | ❌ | |
| Grass-drop loot modifier | ✅ | ✅ | ✅ | |
| Vanilla seed conversion | ✅ | ✅ | ✅ | |
| Resource crops + nuggets | ✅ | ✅ | ✅ (shards/pebble) | |
| Create compat | – | Harvester only | ❌ | |
| IE Cloche compat | – | ✅ | ❌ | |

**Takeaway:** the 1.21 port is the "breeding core" only. The infrastructure layer (water, greenhouses, storage, automation) is missing, and that layer is where a steampunk/Create redesign has the most to offer.

---

## 2. Whiteroom breakdown — per feature

Each entry has three parts: **Does**, **How** and **1.21.1 rebuild**.

### 2.1 Crop Sticks & Cross Crops

**Does:** Plants are planted on *crop sticks* placed on a valid soil, not directly on farmland. Right-clicking existing sticks with another stick makes a **cross crop** (double sticks). An empty cross crop is where breeding happens.

**How:**
- One `crop` block entity covers every state. A blockstate enum `STATE` encodes `{sticks only, sticks+plant, cross sticks, plant only (no sticks)}`. A `VARIANT` property holds the stick material (WOOD / IRON / OBSIDIAN).
- Stick variants carry a **fluid predicate**: wood breaks in lava; iron and obsidian survive any fluid. The block is fluid-loggable.
- Breaking drops 1 stick (2 for cross). If config `weedsDestroyCropSticks` is on and weeds are present, the sticks are lost.
- Planting outside crop sticks is allowed by config (`plantOffCropSticks`, default true).
- The hitbox grows with plant height. Collision with sticks is configurable.

**1.21.1 rebuild:** keep the single-block-entity approach. Use `EnumProperty<CropState>` plus `EnumProperty<StickMaterial>`. Make the stick material a datapack registry (an extensible material list) instead of a Java enum. Fluidlogging should use the vanilla `SimpleWaterloggedBlock` pattern generalised to any fluid via a stored fluid id (the 1.21 port's `SimpleFluidloggedBlock` does this).

---

### 2.2 Seeds & the Genome

**Does:** Every seed carries a genome: a **species gene** plus **6 stat genes**. Each gene is a **diploid pair** (dominant + recessive allele).

**How:**
- Stats (default range 1–10, each with a configurable min, max and hidden flag):

| Stat | Effect | Colour (helix render) |
|---|---|---|
| **Gain** | Harvest rolls: `trials = floor((gain + 3) / 3)`, so gain 1→1, 3→2, 6→3, 9–10→4 | blue |
| **Growth** | Adds to the growth chance (see §2.3) | green |
| **Strength** | Widens environmental tolerance windows (see §2.4); at max strength, season requirements are ignored | red |
| **Vigour** | Breeding power: chance to be chosen as a parent (parents sorted by it) and the chance/direction a stat allele drifts ±1 when inherited (AgriCraft's Fertility + Mutativity) | cyan |

> **Cog & Cultivar uses four stats:** Gain, Growth, Strength and Vigour. Strength also takes over AgriCraft's Resistance (weed suppression) and, at 10, makes a plant **Hardy**: it ignores soil humidity, acidity and nutrients. Strength also sets the chance a Cutting takes.

- **Dominance:** for stat genes, the higher value is dominant.
- **Expressed value** comes from config `Stat calculation logic`: `max` (default), `min` or `mean` of the two alleles.
- Recessive allele colour = dominant colour × 0.6 (used by the DNA helix renderer).
- Seeds are a single dynamic item (`agricraft:seed`) whose model and texture are resolved from the species. 1.18 used a custom model loader; 1.21 uses a BEWLR plus a data component.
- Stored as NBT in 1.18, and as the data component `genome` (Codec + StreamCodec) in 1.21.

**1.21.1 rebuild:** a `DataComponentType<Genome>` with a `record Genome(Holder<Plant> species, Map<Holder<Stat>, AllelePair> stats)`. Make **stats a static registry** (NeoForge `RegistryBuilder`, synced) so addons can add genes. Seed item rendering: resolve the model per species using `ItemOverrides` or a custom `BakedModel` wrapper keyed on the component. Avoid a BEWLR: it is slower and breaks some GUIs.

---

### 2.3 Growth Tick

**Does:** Each random tick (or sprinkler-forced tick), the crop decides between weeds, breeding and plant growth.

**How (exact order):**
1. Fire the Pre event; it can be cancelled.
2. **Weed roll** (§2.6). If it succeeds, run the weed tick and stop.
3. Otherwise, if the block is a **cross crop**, run the cross-breed tick (§2.5).
4. Otherwise, evaluate the **growth response** from the requirements:
   - `KILL_IT_WITH_FIRE` (lava) → plant removed instantly
   - `LETHAL` → **regress one growth stage**
   - `FERTILE` → growth roll
   - `INFERTILE` → nothing
5. **Growth roll:** `P = soilGrowthModifier × (plant.growthChance + growth × plant.growthBonus × globalMultiplier)`. If it succeeds, advance the stage (subject to a vertical-space check for tall plants) and fire `CropGrowEvent.Pre/Post` for vanilla compat.

Example (wheat-like, `0.75 / 0.025`): growth 1 → 77.5 %/tick; growth 10 → 100 %.

**1.21.1 rebuild:** use `randomTick` on the block (cheap), not a block-entity ticker. Cache the growth response (§2.4). Fire `net.neoforged.neoforge.event.level.block.CropGrowEvent` so other mods' growth accelerators interoperate.

---

### 2.4 Growth Requirements & Soils

**Does:** Plants have environmental needs. A crop grows only when every condition is FERTILE. The Strength stat makes plants hardier.

**How — soils:** a soil is a datapack entry mapping blocks/tags to three discrete properties plus a growth modifier:

| Property | Ordered scale |
|---|---|
| Humidity | ARID · DRY · DAMP · **WET** (default) · WATERY · FLOODED |
| Acidity | HIGHLY_ACIDIC · ACIDIC · **SLIGHTLY_ACIDIC** · NEUTRAL · SLIGHTLY_ALKALINE · ALKALINE · HIGHLY_ALKALINE |
| Nutrients | NONE · VERY_LOW · LOW · MEDIUM · **HIGH** · VERY_HIGH |

Vanilla soil defaults (1.21 datagen): farmland WET/SLIGHTLY_ACIDIC/HIGH ×1.0; sand ARID/NEUTRAL/LOW ×0.8; clay FLOODED ×1.0; podzol DAMP/VERY_HIGH ×1.1; mycelium DAMP/LOW ×0.8; soul sand ARID/HIGH ×0.8; soul soil ARID/VERY_HIGH ×1.0; nylium ARID/LOW ×0.75 (crimson is HIGHLY_ACIDIC, warped is HIGHLY_ALKALINE); gravel DRY/SLIGHTLY_ALKALINE/LOW ×0.5. FD rich soil ×1.8.

> **Cog & Cultivar humidity levers** (`Hydration`, per check, no stored state): water beside the soil +1 step, a Bell Cloche on the trellis +1, a lit heat source beside or under the soil −1 (`#cultivar:soil_heaters`: fuelled Blaze Burner, lit campfire, magma block). Steps add up and clamp at ARID / FLOODED; with no water or heat, unwatered farmland reads −1. Coarse dirt ships as an overworld DRY / NEUTRAL / LOW soil.

**How — condition evaluation (strength-scaled tolerance):**
- **Soil property:** a plant states `(value, type ∈ {EQUAL, EQUAL_OR_HIGHER, EQUAL_OR_LOWER}, toleranceFactor f)`. Accepted index window = `[value − ⌊f·strength⌋, value + ⌊f·strength⌋]`, clipped by the type (HIGHER drops the upper bound, LOWER drops the lower).
  *Example:* humidity WET, EQUAL, f = 0.15. At strength 1 the window is exactly WET; at strength 7 (⌊1.05⌋ = 1) it is DAMP–WATERY.
- **Light:** `[minLight − ⌊f·str⌋, maxLight + ⌊f·str⌋]`.
- **Biome / Dimension:** whitelist or blacklist; ignored once `strength ≥ ignoreFromStrength`.
- **Season:** listed seasons only (Serene Seasons via plugin); ignored at max strength.
- **Fluid at the crop's position:** listed fluids → FERTILE. Lava → KILL. Any other fluid → LETHAL. No fluid is FERTILE only when the plant lists no fluids (aquatic plants need water).
- **Nearby blocks:** "N matching blocks within box (min..max offsets)", optionally with NBT matching on block entities.
- Conditions are cached per crop (`RequirementCache`) and invalidated on neighbour, light or soil change.

**1.21.1 rebuild:** keep the datapack registries for `plant`, `soil`, `weed`, `mutation` and `fertilizer` (the 1.21 port already does this well). Make growth conditions a **static registry of condition types**, each with a `MapCodec`, so pack makers can add conditions such as "requires steam pressure" or "requires heat ≥ KINDLED" (see §3).

---

### 2.5 Cross-breeding, Cloning & Mutation

**Does:** An empty cross crop next to mature crops can spawn a clone (one parent) or a hybrid/mutant (two parents).

**How:**
1. **Candidate parents:** the 4 horizontal neighbours that are valid, **mature**, (optionally) fertile, sorted by fertility descending, each passing `rand(max) < fertility` (fertility / 10 chance).
2. **0 candidates:** nothing.
   **1 candidate → clone:** only if the plant is cloneable and `rand < spreadChance`. The genome is copied, or mutated if the `cloneMutations` config is on.
   **2 or more → combine** the top 2 by fertility.
3. **Combining, per gene:** pick one random allele from each parent.
   - **Species gene:** look up all mutations whose parents match the allele pair. Each mutation's conditions return `FORCE < PASS < FORBID` (sorted best-first). FORBID entries are dropped, the best tier wins (ties shuffled), then roll `chance`. FORCE skips the roll. The result is `(child, random parent allele)`. On failure the pair is just the two picked alleles.
   - **Stat genes:** each allele independently: if `rand(max) < parentMutativity`, apply a delta; the delta is +1 if `rand(max) < ⌊(max + mutativity) / 2⌋`, else −1.
     *Cog & Cultivar (rebalanced 2026-11-10, see `tools/data/sim_breeding.py`):* each parent joins a cross with chance 0.5 + 0.05 × Vigour (55 % → 100 %). An inherited allele drifts with chance 0.25 + 0.02 × Vigour (27 % → 45 %), upward with chance 0.75 − 0.05 × (allele − 1) + 0.02 × (Vigour − 1), clamped 20–90 %: low stats climb easily, the last steps to 10 are the hard ones. AgriCraft's linear v/max made fresh seeds cross ~1 % of the time and Vigour-10 lines climb every generation; this flattens both ends. On one frame a fresh pair crosses about every 7 min, all stats reach 5 in ~13 h and 10 in ~24 h (4 frames: ~6 h / ~11 h).
4. **Fertilizer on a cross crop** can trigger a cross tick (config).
5. The engine is swappable (selector, cloner and combiner are lambdas), which is good for an API.

**1.12 "hardcore" stat mode (worth reviving as an option):** child stats come from neighbour count. With 1 parent: 3/4 decrement, 1/4 none. With 2: 2/4 decrement, 1/4 none, 1/4 increment. With 3: 1/4, 2/4, 1/4. With 4: 1/4, 1/4, 2/4 increment. This rewards dense breeding setups.

**Mutation chains (1.21 data):** vanilla flowers and crops form a tree; resource crops start from **Ferranium (melon × pumpkin)** and branch to Aurigold, Lapender, Redstodendron, Cuprosia, Niccissus, Platiolus, Osmonium, Plombean, Petinia, Jaslumine, Quartzanthemum, Nitor Wart, Amathyllis, Diamahlia, Emeryllis, Carbonation and Nethereed.

**Cog & Cultivar vanilla tree (our own design, not AgriCraft's):** each child mixes its parents, and each mutation fires only when the cross frame recreates the child's habitat (conditions use tolerance 0, so stats never widen them). Hard-to-reach crops sit deepest, so breeding is a long alternative to exploring, not a shortcut. A parent pair may fork into two children when their habitats are mutually exclusive. Start: Wheat, Potato.

| Child | Parents | Habitat |
|---|---|---|
| Beetroot | Wheat × Potato | damp soil or wetter |
| Carrot / Brown Mushroom | Potato × Beetroot | light ≥ 10 + high nutrients / twilight (light 9–11) |
| Sugar Cane / Bamboo | Wheat × Beetroot | flooded (mud) / wet soil in shade (light ≤ 11) |
| Pumpkin | Carrot × Beetroot | very high nutrients (podzol) |
| Melon | Pumpkin × Beetroot | light ≥ 13, wet or wetter |
| Cactus | Sugar Cane × Pumpkin | arid (sand) |
| Red Mushroom | Brown Mushroom × Beetroot | twilight (light 9–11) |
| Cocoa | Bamboo × Pumpkin | light ≤ 11, wet or wetter |
| Sweet Berries | Beetroot × Cactus | acidic (podzol) |
| Glow Berries | Sweet Berries × Red Mushroom | light ≤ 7 |
| Nether Wart | Red Mushroom × Cactus | dry + slightly alkaline (soul sand) |
| Torchflower | Glow Berries × Pumpkin | torch-lit (light 10–12), 10 % |
| Pitcher Plant | Torchflower × Sugar Cane | flooded, 10 % |

**Farmer's Delight family (optional, loads only with each mod):** Onion = Carrot × Potato; Cabbage = Beetroot × Onion (damp, light 9–14); Tomato = Sweet Berries × Potato (nightshades, light ≥ 13, 2 tall); Rice = Wheat × Sugar Cane (flooded, 2 tall); Asparagus = Onion × Bamboo (wet); Sweet Potato = Potato × Sugar Cane (light ≥ 12); Chili Pepper = Tomato × Cactus (light ≥ 14); Peanut = Potato × Cocoa (light ≥ 12, damp); Cranberries = Sweet Berries × Sugar Cane (flooded bog); Cotton = Wheat × Cactus (light ≥ 13); Bell Pepper = Tomato × Pumpkin (light ≥ 12); Pale Bell Pepper = Bell Pepper × Cotton; Dark Bell Pepper = Bell Pepper × Sweet Berries (acidic: anthocyanins); Coffee = Cocoa × Sweet Berries (shade, wet); Bullet Pepper = Nether Wart × Tomato and Powdery Cane = Bamboo × Nether Wart (soul sand). Rich Soil / Rich Soil Farmland are very-high-nutrient soils. FD knives clip crops (tag `cultivar:clippers`).

Parents that can't grow in the child's habitat (e.g. potatoes in the dark) are bred for strength or moved in mature with the Transplanting Spade. Crop visuals for crops without a plain crop texture are block models under `models/crop/` that crop vanilla textures by UV, so any resource pack re-skins them.


---

### 2.6 Weeds

**Does:** Untended crops (and empty sticks) grow weeds that can regress or kill plants and spread.

**How:**
- **Weed roll:** if the crop has a plant, the weed wins when `rand(max) ≥ ⌊(max + resistance) / 2⌋` (resistance 1 → 50 %, resistance 10 → 0 %). Empty sticks: 50 %.
- No weeds yet → each registered weed rolls its `spawnChance`; the first success is planted.
- Growing weeds advance with `soilModifier × weed.growthChance(stage)`.
- **Mature weeds:** if lethal (config on), roll again to regress the plant one stage. If aggressive (config on), try to infect each neighbour (each neighbour rolls its own resistance).
- **Rake** removes weeds. The wooden rake knocks the weed back **one growth stage** per use (it removes the weed only when it is already at stage 0); the iron rake removes it outright. Raking drops weed products (seeds, grass; config `rakingDropsItems`).

---


> **Blight variants ✅:** a weed JSON may carry a `habitat` (growth conditions, tested on the trellis like plant requirements, so water/cloche/heat shifts count); a new blight is picked from those whose habitat fits. Spreading ignores habitat. A mature blight cleared with a hoe rolls its `rake_products` (cleanup material, sometimes useful); an immature one drops nothing.
>
> | Blight | Habitat | Behaviour | Drops (mature) |
> |---|---|---|---|
> | Rust-rot | anywhere | lethal | sticks, rare iron nugget |
> | Bog-rot | watery or flooded | spreads | clay, slime ball |
> | Dry-rot | dry or arid | lethal | dead bush, bone meal |
> | Ash Blight | slightly alkaline or more | slows | charcoal, rare gunpowder |
> | Sour Moss | acidic or more | spreads | moss carpet, rare glow berries |
> | Mildew | light ≤ 7, damp or wetter | spreads | string, brown mushroom |
> | Sun-scald | light ≥ 14, damp or drier | lethal | dead bush, rare glowstone dust |
> | Leach-creep | low nutrients or less | slow, slows | bone meal, flint |
> | Glut Canker | very high nutrients | lethal | sugar, rare honeycomb |
>
> Art: `tools/art/gen_blight_textures.py` kitbashes each blight from its own Excalibur plant (seagrass, dry grass, crimson roots, berry bush, fern, dead bush, hanging roots, crimson fungus) with its own palette and detail (slime beads, rot tips, embers, pustules, mould fuzz, char, pale tips, galls).

### 2.7 Harvest & Drops

- Right-click a mature crop: run `trials` (from Gain) × `plant.products` rolls. The plant returns to `growthStageAfterHarvest` (perennial behaviour).
- Each product has `(item/tag, min, max, chance)`.
- Seeds drop on break if the stage allows (`onlyMatureSeedDrops` config). There is also a seed-drop chance plus bonus.
- **Clipper:** right-click → `clipProducts` (for example a flower item instead of dye) without killing the plant. 1.12 produced a "clipping" item that could be grafted.
- **Experience** modifier: drops XP per Gain point.

---

### 2.8 Fertilizers

**Does:** Items that tick crops (like bonemeal) with per-plant whitelists and blacklists and possible negative effects.

**How:** datapack entries containing item variants, `potency` (ticks per use), `triggerMutation`, `triggerWeeds`, and an effect block listing plants it helps or harms. Harmful entries can reduce growth or kill the plant. Particles mark positive or negative outcomes. Vanilla bonemeal is routed through this system.

---

### 2.9 Plant Modifiers (callbacks)

Applied per plant through `modifiers: [{id, args}]`:

| Modifier | Behaviour |
|---|---|
| `brightness` | Plant emits light (sea pickle) |
| `burn` | Sets entities on fire on contact |
| `bushy` | Slows entities like a berry bush |
| `experience` | XP on harvest (×gain) |
| `fungus` | Mature plant + bonemeal → grows the huge fungus |
| `poison` | Poisons on contact |
| `redstone` | Emits redstone when mature |
| `summon` | Spawns an entity on harvest or break (chance) |
| `thorns` | Damages on contact (cactus) |
| `tree` | Bonemeal at maturity → grows a tree or huge mushroom |
| `wither` | Wither effect on contact |

**Rebuild:** a static registry of `PlantModifierType` with `MapCodec` args (the 1.21 port's `ModPlantModifiers` already follows this pattern).

---

### 2.10 Animal-Attractant Genes (1.18 only)

**Does:** Extra boolean genes (`cow`, `pig`, `sheep`, `chicken`, `rabbit`, `panda`, `turtle`) are present on plants those animals eat. A mob AI goal makes animals path to mature crops with the gene, eat them (the crop regresses), and enter love mode. This is auto-breeding.

**How:** a gene with boolean alleles. The default plant carries `true`. Mutation tends to flip it to `false` (95 % at mutativity 1, 50 % at 10) and never back. An `EatCropGoal` is injected via capability on entity join, with cooldown and target scanning.

**Rebuild:** an attachment plus `EntityJoinLevelEvent` goal injection. This fits a "livestock feeder" industrial angle well (§3.6).

---

### 2.11 Trowel
Picks up a **living plant with its genome and growth stage** from crop sticks and stores it on the trowel. Right-click empty sticks to transplant it. Lets you move a plant without resetting growth. 1.21 stores this in the `trowel_data` component.

### 2.12 Seed Bag
- Holds **one species** (up to 64 by default, configurable 8–256).
- **Must be enchanted** at an enchanting table to activate (1.18: custom enchant; cost fixed by config, default 10 levels).
- Sorters: default plus one per stat. Scroll while sneaking to change the sorter. A "shake" animation plays (60 ticks, ±30°).
- Off-hand: insert or extract seeds. Main hand: plant the best or worst seed by sorter onto sticks or soil.

### 2.13 Magnifying Glass (& Helmet)
- Hold and use it to look at crops, soil or seeds in-world. An overlay renders the **DNA double-helix** (dominant left, recessive right, coloured per gene, rotatable to the selected gene) and soil property icons.
- A crafting recipe merges it into any helmet (`magnifying` component). TOP/Jade data can be gated behind it (config).

### 2.14 Journal
- Records **researched plants**. Seeds are added by inserting them into the analyzer with the journal inside.
- Pages: Front → Introduction → Growth Requirements guide → Genetics guide → Mutations guide → one page per plant (icon, description, soils, light, seasons, products, clip products, mutations in/out).
- In 1.18 it renders as a **3D open book in hand** with page-flip animation and camera snap (configurable), or as a GUI.
- **Progressive JEI:** mutations stay hidden in JEI until researched (config).

### 2.15 Seed Analyzer
- A block with seed and journal slots. Inserting a seed reveals its stats and genome (1.12 had "unanalyzed" seeds that hid stats; 1.18+ shows them always). If a journal is inside, the seed is recorded.
- **1.18 "in-world" mode:** sneaking with the block in view snaps the camera to look through the angled lens (animated camera, 22.5° tilt), showing a rotating seed model and helix overlay. A GUI is also available.
- Automation: sided item handler. The journal cannot be extracted by automation.
- **1.12 extra:** a ComputerCraft peripheral block placed next to the analyzer exposed `getStats`, `getPlant`, `isMature`, `getNeededSoil`, `getBrightnessRange`, `analyze` and similar to Lua.

### 2.16 Irrigation (1.12 / 1.18 — missing in 1.21)

**Components:**
| Block | Behaviour |
|---|---|
| **Tank** | 8000 mB per block (config 1k–40k). Placing tank blocks in a cuboid **auto-forms a multiblock** whose capacity is the sum of all blocks. One origin block entity holds the fluid; other blocks proxy to it. Fills from **rain** at 5 mB/t (config). Can spawn a water source block when broken while sufficiently full. Exposes a water-only fluid handler. |
| **Channel** | 500 mB. Connects to tanks, channels and sprinklers. |
| **Hollow channel** | Aqueduct-style raised variant. |
| **Valve** | An item applied to a channel. Right-click toggles open or closed with an animated wheel and a `valve.ogg` sound. A closed valve blocks transfer. |
| **Sprinkler** | Hangs under a channel. Pulls water into a buffer. Consumes 10 mB/s (config). Rotates (one turn per 2 s) with particles. |
| **Custom wood** | Tanks and channels take the texture of the planks used to craft them (dynamic texture). |
| **Water pad (1.12)** | A dug-out soil block that holds water and hydrates nearby farmland. |

**How the water simulation works:**
- Each component stores contents as a **height**: `level = minLevel + contents × (maxLevel − minLevel) / capacity`, in world Y coordinates.
- Every tick, each component **balances with each neighbour** whose surface is lower: it transfers enough to equalise heights (scaled by surface factor = capacity / height span), limited by a per-tick delta fraction. Water flows downhill by height across mixed tank and channel sizes.
- Client sync happens only when the level changes by more than one render interval.
- **Nether (`ultraWarm`):** components lose 1 % per tick with steam particles and fizz sounds.
- **Sprinkler pass:** works a 7×7 area (radius 3), one column per tick, scanning up to 5 blocks down. Air is skipped. Plants get a **random tick with 20 % chance** (not on the lowest layer). Farmland is set to moisture 7. A full pass is limited by `sprinkleInterval` (40 t).

**Assessment:** charming but expensive. It is per-block-entity tick balancing with no network graph, which is why it scales badly and was a likely reason it was dropped in the port.

### 2.17 Greenhouse (1.18 only)

**Does (intended):** an enclosed glass-roofed room boosts growth and ignores seasons. A wall-mounted **Greenhouse Monitor** shows status with an animated thermometer and hygrometer.

**How (actual):**
- Placing a monitor on a wall, facing into air, starts a **flood-fill** of air blocks from that point (up to 1024 blocks, config). Every boundary must be solid. The fill runs on the next server tick via a task queue to avoid deadlocks.
- It counts **ceiling blocks** and **glass-type ceiling blocks**. The state is COMPLETE when glass fraction ≥ config, GAPS if any boundary is open, INSUFFICIENT_GLASS otherwise, and REMOVED when destroyed.
- Interior air is **replaced with a custom `greenhouse_air` block**, so later block changes can be detected cheaply, per chunk, and persisted in a level capability.
- **Finding:** `greenHouseGrowthModifier` and `greenHouseIgnoresSeasons` are defined in config but **never read anywhere**. The structure detection is complete; the gameplay effect was never implemented.
- **Village structures:** "greenhouse" and "irrigated greenhouse" pieces for desert, plains, savanna, snowy and taiga villages. A structure processor spawns biome-appropriate crops and forms the greenhouse; a mossify processor ages plains and taiga variants.

### 2.18 Grate
A thin lattice block with three offset positions (near, mid, far) on any axis. Vines can grow on either side. Climbable (config). Purely decorative and functional scaffolding.

### 2.19 Seed Storage (1.12)
Wooden chests that each **lock to one species** and store unlimited analyzed seeds, listed by stats. A **Seed Storage Controller** networks adjacent chests into one GUI. 1.18 kept only an empty `BlockSeedCatalogue` stub (planned but never built). `EntityAgriVillager` is a similar empty stub, so an "agri villager" was also planned and never built.

### 2.20 World & Misc
- **Grass-drop loot modifier:** breaking grass can drop AgriCraft seeds (weighted entries). It can optionally *reset* vanilla seed drops.
- **Vanilla seed conversion:** planting a vanilla seed (or inserting it into the analyzer) converts it to an AgriCraft seed with default stats. Can be restricted to the analyzer only.
- **Resource crops & nuggets/shards:** coal pebble, copper nugget, diamond / emerald / quartz shards, netherite sliver, amathyllis petal, plus tag-driven nuggets for modded metals.
- **Debugger item:** modes for core info, soil check, growth tick, light diff, fill/drain irrigation, greenhouse info.
- **Compost value** for seeds (config).
- **Compat:** JEI/EMI (produce, mutation, requirement, clipping categories), Jade/TOP/WTHIT, Serene Seasons, Botania (mana-consuming plants, horn harvest), Mystical Agriculture (plant override), IE Cloche (genome-aware recipe: strength and growth affect cloche speed and output), Industrial Foregoing, Cyclic, Straw Golem, Botany Pots, **Create (Mechanical Harvester respects AgriCraft crops)**.

---

## 3. The redesign — steampunk/dieselpunk + Create

### 3.0 Identity

Working names (placeholders; pick one):
- **Cog & Cultivar** (`cultivar`) — the default used below
- *Brassroot Botanics* (`brassroot`)
- *Steam & Seedling* (`steamseed`)

**Tone:** Victorian horticultural science meeting a coal-and-diesel agro-industry. Herbaria, apothecary labels and punch-card genetics sit next to riveted pipework and soot-stained glasshouses. In a Steamlands-style pack it can lean darker: blighted soil, smog-tolerant strains, plants bred to survive a poisoned world.

**Design pillars:**
1. **Breeding stays the core.** Keep the genome and mutation depth; they are AgriCraft's best idea.
2. **Infrastructure is Create-native.** Water, heat, pressure and harvesting run on Create fluids, kinetics and contraptions. No parallel pipe system.
3. **Information is diegetic.** Gauges, punch cards, ledgers and goggles replace floating tooltips.
4. **Mechanised does not mean trivial.** Automation should need breeding work first: high-stat seeds unlock efficient machines.
5. **Cheap on the server.** Network-graph water, cached requirements, random-tick growth, and greenhouse scans spread across ticks.

---

### 3.1 Feature mapping

| AgriCraft | Cog & Cultivar | Create hook |
|---|---|---|
| Crop sticks | **Trellis Frames** (wood → iron → brass → blackened steel) | Brass tier crafted via Create; contraption-safe |
| Cross crop | **Grafting Frame** (double trellis with brass clamp) | Deployer can place and graft |
| Seed + genome | **Cultivar seed** with genome; stats shown as a **punched Assay Card** | — |
| Seed analyzer | **Assaying Engine** (kinetic, needs RPM) | KineticBlockEntity, goggle info |
| Journal | **Botanical Ledger** (leather, brass corners, tabbed) | Display Link source |
| Magnifying glass | **Botanist's Loupe**; **Loupe Goggles** merge with Create Engineer's Goggles | `IHaveGoggleInformation` |
| Trowel | **Transplanting Spade** | Deployer-usable |
| Clipper | **Pruning Shears** | Deployer-usable; Mechanical Saw optional |
| Rake | **Hand Tiller**, plus a contraption **Weed Rake** | MovementBehaviour |
| Seed bag | **Seed Satchel** (leather plus brass dial selector) | — |
| Tank multiblock | **Cistern** (or simply accept any Create fluid tank) | `Capabilities.FluidHandler.BLOCK` |
| Channel / valve | **Sluice Troughs + Sluice Gates** (low-tech tier) | Gates driven by redstone or by a kinetic handwheel |
| Sprinkler | **Rotary Sprinkler** (kinetic + pump pressure) and **Mist Nozzle** (steam) | Shaft-driven head; pipes feed it |
| Water pad | **Drip Line** (perforated pipe through soil) | Create pipe connection |
| Greenhouse | **Conservatory** (glass + iron frame, boiler-heated) | Boiler/steam, Blaze Burner heat |
| Greenhouse monitor | **Climate Gauge** (thermometer, hygrometer, barometer dials) | Display Link source, comparator output |
| Fertilizer items | Solid fertilizers **plus liquid fertigation fluids** | Mixing / spout / pipes |
| Weeds | **Blight** (rust-rot, soot-mould, nettle-wire) | — |
| Seed storage | **Seed Vault** (card-catalogue cabinets) | Item handler; works with Create logistics/stock |
| CC peripheral | **Display Link sources** + optional CC:Tweaked peripheral | `DisplaySource` |
| Resource crops | **Ore-blooms** dropping crushed ore | Washing / crushing chain |
| Animal genes | **Fodder genes** + **Livestock Trough** | — |
| Village greenhouses | **Derelict Conservatories** (ruined, worldgen) | — |

---

### 3.2 Crops & Trellises

- **Trellis tiers:**
  - **Timber:** burns, breaks in lava, cheap.
  - **Iron:** any fluid.
  - **Brass:** iron plus a +1 strength-equivalent tolerance buffer. Crafted with Create brass sheets.
  - **Blackened Steel:** brass plus immunity to blight spread *into* the crop. Late game.
  - Datapack-driven materials let pack makers add more.
- **Art direction from Joe's references:** the iron, brass and steel tiers follow **wrought-iron garden trellises**: a slim arched top, vertical bars with collar rings, and C/S-scroll flourishes at the crown and base. At 16 px that means a two-pixel arch, two side rails and one scroll motif per face. Timber stays a simple lashed lattice. Planted crops grow *through* the bars, so the plant model renders inside the frame, not on top of it.
- **Tall crops (Joe's request):** plants get an optional `height` (1–4 blocks). Clicking the top face of a trellis with another trellis auto-extends it upward, like Create's directional placement. The base trellis owns the genome and all logic; upper segments are lightweight extensions that render the plant's upper sections and forward interactions to the base. A tall plant can't pass a growth stage that needs a segment that isn't there. Sugarcane and cactus become `height: 3` plants.
- **Wall Trellis (optional, Phase 6):** a directional, flat-mounted trellis for climbers (vines, hops, Farmer's Delight tomatoes as compat). It uses the same stacking rules, and the plant renders as a single plane against the wall.
- **Bell Cloche (new, early game):** a glass bell jar placed over a single planted trellis. Kitbash it from Excalibur `glass` with a brass knob. It acts as a one-crop greenhouse: season immunity, ×1.1 growth, +1 humidity step, and it blocks weed *spawning* (existing weeds still grow). It doesn't stack with a Conservatory. It is a crop-influence source with radius 0, so no new systems are needed. A cloche can be lifted off with an empty hand to harvest, or harvested in place by a Deployer.
- **Shade Cloche / Lamp Cloche (shipped):** Bell Cloche variants (`cloche_type` = bell/shade/lamp on the trellis) that also override the light their crop *reads* (`CropLight`): shade caps it at `cloche.shadeMaxLight` (3), lamp raises it to `cloche.lampMinLight` (14). World light is untouched (the lamp's glass and bulb are emissive in the model only), so a dark-loving parent can stand beside a bright frame or a lamp-lit parent: real light only changes by 1 per block, which made dark/bright crosses a building puzzle. Crafted from a Bell Cloche plus tinted glass and an iron sheet (shade) or an electron tube and glowstone dust (lamp). Goggles show "Light: 3 (Shade Cloche; 15 outside)". `check_crosses.py` stays strict (no cloches), so every cross is still breedable without them.
- **Grafting Frame** replaces the cross crop. Visual: two trellises clamped with a brass bracket; a small pressure dial shows "graft progress". Deployers holding a trellis can create one, which allows automated breeding grids.
- **Regression stays** for hostile conditions. Add a **wilt visual** (desaturated overlay tint) so players can see why growth stopped.
- **Improvement:** expose the growth response to a **comparator** (0 = infertile, 1–14 = growth %, 15 = mature) for redstone-driven automation.

### 3.3 Genome & Stats (kept, with additions)

Keep the 6 stats and the diploid model. Re-skin the presentation:
- ~~**Assay Card**~~ *(dropped: assaying reveals the seed itself)*. Originally a punch card whose hole pattern encodes the genome. The card texture is generated per stat (a column of 10 holes per stat, punched up to the value). It is an analysis record, separate from the seed.
- Optional **7th stat, "Hardiness"** (pack-toggle): tolerance to pollution and blight, for Steamlands-style wasteland play. Inert unless a pollution provider is installed. Registered through the static stat registry like any addon gene.
- Config option for **classic vs hardcore** stat inheritance (§2.5).
- **Fix:** make the mutativity math match its intended 25/50/25 design, or document the real curve in-game (the Ledger's genetics page).

### 3.4 Assaying Engine (Seed Analyzer)

- **Block:** a kinetic machine. A brass housing with a spinning drum, a magnifying lens on an arm, and a card punch on the side.
- **Function:** insert a seed and give it RPM. Assaying takes `T = base / (|RPM| / 32)` ticks, clamped. Output is the seed back, assayed. If a Ledger is in the side slot, the species is recorded. *(The Assay Card output was dropped.)*
- **Stress impact:** low (2–4 SU/RPM). It is an early Create tutorial machine.
- **Goggle info:** current species, progress, stress.
- **Visual:** the drum spins with the shaft (a Create partial model rotated in the renderer), the lens arm bobs, and the card punch clacks (sound) when done.
- **Keep the 1.18 in-world inspect mode as optional polish:** sneak-look through the lens to see the genome helix rendered as **brass gears on a twin spindle** instead of DNA (dominant spindle left, recessive right, gear tooth count = allele value).

### 3.5 Irrigation, re-done on Create fluids

**Principle:** do not ship a parallel water network. Every water consumer exposes `IFluidHandler` via `RegisterCapabilitiesEvent` and accepts Create pipes. Add only the pieces Create lacks.

| Block | Behaviour |
|---|---|
| **Cistern** | Optional rain-catcher tank with a funnel top (fills at a configurable mB/t while raining and sky-exposed). Multiblock forming like a Create fluid tank, or simply a single-block rain collector that feeds pipes. Keep the 1.18 nether evaporation rule (1 % per tick plus steam particles) as flavour. |
| **Sluice Trough** (low-tech) | Open wooden or iron channels for pre-Create play. Use a **graph solver**: when channels form, compute one network with a shared virtual level. Store the network in a `SavedData`, not per block entity, and recompute only on topology change. Rendering interpolates the shared level. This removes the O(blocks) per-tick balancing. |
| **Sluice Gate** | Valve equivalent. Toggled by hand-wheel, redstone, or a shaft (1 rotation = open). Animated wheel; reuse the valve sound concept. |
| **Rotary Sprinkler** | Mounts under a pipe or trough. **Water** sets the range (needs a supply); **RPM** sets the sweep rate. Radius: 3 at under 64 RPM, 4 at 64–127, 5 at 128+. Same column-scan algorithm as 1.18 (skip air, random-tick plants at a configurable chance, moisten farmland), but scan **one column per tick in a spiral** and only within loaded chunks. Head spins with the shaft. |
| **Mist Nozzle** | Accepts **steam** (Create boiler output via a pipe tap, or a mod steam fluid) and raises the **humidity class** of soils in range by 1–2 steps. Gives humidity a gameplay lever instead of a fixed soil property. |
| **Drip Line** | Perforated pipe segment laid in soil. Hydrates farmland and conditions humidity in a line. Cheap per block and pipe-connectable. |

**Fertigation:** fluid fertilizers (§3.8) can be mixed into water. Sprinklers and drip lines apply their effect at a reduced per-crop potency. This automates fertilizer without deployer spam.

### 3.6 Soil Conditioning (improvement over AgriCraft)

In AgriCraft, soil properties are fixed per block. Here they are **mutable per position** (shipped):
- A **chunk attachment** (`cultivar:soil_amendments`, serialised with the chunk) maps `BlockPos → Amendment {acidity, nutrients, humidity}` steps. Sparse: only conditioned soil has an entry, and only chunks with entries carry the attachment.
- **Effective soil = base soil (datapack) + environment (water, heat, cloche: `Hydration`) + amendment, clamped to the scale.** Growth checks, goggles, Jade and JEI all read the same values.
- **Conditioners** are a datapack registry (`cultivar/soil_conditioner/<id>.json`: `items`, `acidity`, `nutrients`, `humidity` steps, each -3..3). Shipped:
  - **Agricultural Lime** (+1 alkaline): Millstone on calcite (2) or Create limestone (1 + 50%).
  - **Sulfur Dust** (+1 acidic): Millstone on gunpowder (2); ties into the Brimstone Puff.
  - **Compost** (+1 nutrients): Mixer with dirt, bone meal and rotten flesh (3), or dirt, two seed meal and leaves (2); hand-craft fallback (1).
- Used on the soil block or on any segment of the trellis standing on it (a `RightClickBlock` handler, so deployers work). Each property's total is capped at `soil.conditionerMaxSteps` (server config, default 2, 0 disables); opposite conditioners cancel. A use that changes nothing is refused and costs nothing.
- The amendment belongs to the soil: it survives trellis break/replant and is cleared when the soil block is broken (player break, explosion) or a Planter Bed's fill changes.
- **Visual indicator:** a dusting quad on the floor of the trellis (one layer per amended property: white lime, yellow sulfur, dark compost crumbs with leaf bits; also lean, wet and dry for datapack conditioners; denser at 2+ steps), visible through a Bell Cloche; a faint coloured dust puff off any amended soil within two chunks (so bare soil shows it too); an "Amended: ..." line in Jade (soil block and crop) and goggles.
- Sync: `soil_amendments` payload per chunk on `ChunkWatchEvent.Sent` and on change; the client copy is keyed by dimension and chunk and is safe to read from meshing threads.
- Not done (yet): decay toward base over time, harvests consuming nutrients, and blight residue as an acidifier. `check_crosses.py` stays strict (no conditioners), so every cross remains breedable without them.

This turns the strength-tolerance system from "find the right block" into an engineering problem, which suits Create.

### 3.7 Conservatory (Greenhouse, finished)

- **Detection:** reuse the flood-fill model with fixes:
  - Run the scan **incrementally across ticks** with a budget (for example 256 blocks per tick).
  - Do **not** replace air with a custom block. Instead store a `LongOpenHashSet` of interior positions per conservatory in `SavedData`, indexed by chunk section, and listen to block-change events (`BlockEvent.EntityPlaceEvent`/`BreakEvent`, plus a neighbour-update hook on frame blocks) to re-validate locally. Replacing air breaks other mods and structure placement.
  - Boundary rules: walls are any full block; the roof must be at least X % glass (config, default 60 %). Glass-tagged blocks count, including Create framed glass.
- **Effects (the part AgriCraft never wired):**
  - Growth multiplier (default ×1.25).
  - **Season immunity**.
  - **Temperature:** a new climate dimension. Heat sources inside raise the temperature class: Create **Blaze Burner** (KINDLED/SEETHING), **boiler-fed radiators** (steam pipe), and furnaces/campfires (weak). Plants may gain `temperature` requirements (COLD · COOL · TEMPERATE · WARM · HOT), which lets players grow tropical, nether or desert strains in a snow biome.
  - Humidity bonus when misted.
- **Climate Gauge** (monitor): a brass panel with three needle dials (temperature, humidity, glass integrity). It is a **Display Link source** (outputs state, temperature and humidity) and gives a comparator output by state. Needles animate in the renderer with lerped angles.
- **Worldgen (optional, Phase 6, last):** "Derelict Conservatories" — broken iron-frame glasshouses with cracked glass (state GAPS), overgrown blight, and loot containing a Ledger, starter cultivars and a broken Assaying Engine.

### 3.8 Fertilizers & Chemistry

| Product | Recipe idea (Create) | Effect |
|---|---|---|
| **Bone Slurry** (fluid) | Mixing: bone meal + water | Basic growth, potency 1 |
| **Ammonia Tonic** (fluid) | Heated mixing: rotten flesh + blaze cake trace | +Nutrients, potency 2 |
| **Lime Wash** | Crushing calcite → washing | +Alkalinity delta |
| **Sulfur Dressing** | Haunting/mixing from coal/ash | +Acidity delta, kills soft blight |
| **Mutagen Draught** (fluid) | Superheated mixing with ore-bloom extract | Triggers a cross tick; temporarily +mutativity on the child (consumable risk: chance to −1 a random stat) |
| **Blightbane** | Mixing + haunting | Clears blight, raises resistance-equivalent for N ticks |

Solids are still usable by hand or deployer. Fluids work via a **Spout** onto crops (spouting behaviour), sprinklers and drip lines.

### 3.9 Blight (Weeds, re-themed)

- **Variants:** **Rust-rot** (metal-loving; spreads along iron trellises unless they are blackened steel), **Soot-mould** (thrives in polluted or smoky areas; spawns near smokestacks and active boilers), **Nettle-wire** (aggressive, damages players like thorns).
- Same roll math as §2.6. Add an **environmental spawn weight**: soot-mould weight scales with nearby smoke sources (campfires, boilers, Create steam engines within N blocks). This is optional and pack-toggleable.
- **Removal:** Hand Tiller, the **contraption Weed Rake** (a MovementBehaviour that clears blight when moving over it), or Blightbane.
- Raked blight drops **Blight Fibre**, useful as fuel or for crafting (wick, rope) so it has value.

### 3.10 Harvest & Automation

- **Mechanical Harvester compat (required):** the harvester must call the crop's harvest method, so products go to contraption storage, the plant returns to its after-harvest stage, and the **genome is preserved**. 1.18 did this by registering a subclassed harvester behaviour. **That no longer works in Create 6:** the behaviour is bound in `AllBlocks` via `MovementBehaviour.REGISTRY`, `SimpleRegistry.register` throws if the block already has a value, and providers are only consulted for keys with no value. Use a **small mixin** on `HarvesterMovementBehaviour#visitNewPosition`: when the visited block is our crop, harvest through our API and cancel; otherwise fall through to Create's logic. Its stock `isValidCrop` only recognises `CropBlock`, berry bushes and similar, so without the mixin our crops are ignored or broken. Do not extend `CropBlock` just to satisfy it, because the harvester would then reset the age property and drop the genome.
- **Deployer interactions:** trellis placement, grafting, Transplanting Spade, Pruning Shears, Seed Satchel planting, fertilizer application. Use vanilla `useOn` paths so deployer fake players work.
- **Seed sorting:** register Create **item attributes** — "species is X", "stat ≥ N", "has been assayed" — so funnels, filters and the stock system can sort seeds by genetics. This is the biggest automation quality-of-life gain.
- **Comparator / Display Link** outputs on crops, Assaying Engine, Seed Vault and Climate Gauge.
- **Optional processing-recipe integration:** "Seed Extraction" — milling any crop product returns seeds of default stats (replaces the grass-drop grind).

### 3.11 Ore-blooms (Resource Crops)

> **Cog & Cultivar (slimmed):** ore-blooms are plain plant JSON. They drop `create:crushed_raw_<metal>` directly at a low chance, so they feed Create's washing and ore-doubling chain with no new items. Mutations follow the same habitat logic as the rest of the tree (e.g. a copper bloom needs flooded soil, as copper oxidises in water). Art uses pack-driven `cultivar:crop/` models with metal-coloured textures. Pack makers add their own metals the same way. Ore Petals and Brassleaf gating are dropped (there is no brass trellis tier).
>
> **Shipped tree** (`data/cultivar/cultivar/{plant,mutation}/`; every pair is unique against the crop tree). Low-value blooms come first and are the stepping stones; every ore and gem sits at least four crosses from vanilla (enforced by `OreBloomTests`).
>
> | Bloom | Depth | Parents | Habitat | Drop (per roll) |
> |---|---|---|---|---|
> | Bonecap | 1 | Wheat × Red Mushroom | twilight (light 9–11) | bone meal 1–2 |
> | Inkcap | 1 | Brown Mushroom × Sugar Cane | flooded | ink sac |
> | Madder Root | 1 | Beetroot × Red Mushroom | slightly acidic | red dye 1–2 |
> | Woad | 1 | Carrot × Sweet Berries | light ≥ 12 | blue dye 1–2 |
> | Clayreed | 1 | Carrot × Sugar Cane | flooded | clay ball 1–2 |
> | Silkweed | 1 | Wheat × Sweet Berries | light ≥ 12 | string 1–2 |
> | Glowcap | 1 | Glow Berries × Brown Mushroom | light ≤ 5 | glowstone dust 1–2 |
> | Coalroot | 2 | Inkcap × Bonecap | light ≤ 7 | coal |
> | Flintwort | 2 | Bonecap × Cactus | arid | flint |
> | Slimecap | 2 | Inkcap × Clayreed | flooded | slime ball |
> | Brimstone Puff | 3 | Coalroot × Nether Wart | arid | gunpowder |
> | Rustwort | 3 | Coalroot × Flintwort | flooded | iron nuggets 1–3 |
> | Shardbloom | 3 | Glowcap × Flintwort | light ≤ 7 | amethyst shard |
> | Ferrous Bloom | 4 | Rustwort × Coalroot | light ≤ 7 | crushed raw iron |
> | Redstone Fern | 4 | Brimstone Puff × Madder Root | light ≤ 7 | redstone 1–2 |
> | Quartz Lily | 4 | Shardbloom × Brimstone Puff | dry, slightly alkaline | quartz |
> | Verdigris Vine | 5 | Ferrous Bloom × Slimecap | flooded | crushed raw copper |
> | Lazuli Bell | 5 | Redstone Fern × Woad | twilight (light 9–11) | lapis 1–2 |
> | Brassleaf | 6 | Verdigris Vine × Ferrous Bloom | light ≤ 7 | crushed raw zinc |
> | Aurum Thistle | 7 | Brassleaf × Silkweed | arid | crushed raw gold |
> | Emerald Fern | 8 | Aurum Thistle × Woad | light ≥ 13 | emerald (6%) |
> | Diamond Briar | 9 | Emerald Fern × Quartz Lily | arid (heat-dried) | diamond (3%) |
>
> **Breedable by design:** parents and the frame share light (adjacent blocks differ by at most 1), so every cross has a light level both parents and the child tolerate. Crops grow at light ≥ 9 and mushrooms at ≤ 12 (vanilla's own rules), which leaves a twilight band (9–11) for crop × mushroom crosses. `tools/data/check_crosses.py` verifies every shipped cross in CI.
>
> Stepping-stone blooms grow at 0.5–0.6; ore and gem blooms slower (0.3 + 0.02 × Growth), so a maxed crop gives about 9 crushed iron, 14 redstone, 2 emeralds or 1 diamond an hour and a fresh one under 1 iron. Blooms and mostly want dim (light ≤ 10), damp, medium-nutrient soil. Exceptions: Verdigris wants watery soil, Aurum (poor, dry soil) and Emerald want bright light, Quartz shares Nether Wart's soil, and Diamond Briar grows only in scorched (dry or arid) ground at any light. They have no seed item: seeds come from the Seed Satchel only. Art: each bloom is kitbashed from its own Excalibur plant by `tools/art/gen_bloom_textures.py`; flowers keep their foliage and take the product's colours on the petals, other plants are recoloured whole and show the product as nuggets or gems when ripe. Young stages are green buds.

The original AgriCraft-derived notes below are kept for reference.


- Rename and re-skin the resource line: **Ferrous Bloom** (iron), **Verdigris Vine** (copper), **Brassleaf** (zinc — new, Create-specific), **Aurum Thistle** (gold), **Plumbago** (lead), **Lazuli Bell** (lapis), **Redstone Fern**, **Quartz Lily**, **Coalroot**, **Diamond Briar**, **Emerald Fern**, **Netherite Rose**.
- **Output:** instead of nuggets, drop **Ore Petals** that **wash** (fan + water) into `create:crushed_raw_<metal>` at low yield, so ore-blooms slot into the existing Create ore chain. Note for implementation: Create's crushed tier has no per-material tag (only the umbrella `create:crushed_raw_materials`), so resolve outputs by direct item id with a config map and fall back to nuggets when the item is missing.
- Mutation tree root: keep **Ferrous Bloom = melon × pumpkin** (Ferranium's role). Add a Create-specific branch: **Brassleaf = Verdigris Vine × Ferrous Bloom**, required for brass trellises, which ties breeding progress to the tech tree.

### 3.12 Ledger, Loupe, Satchel, Spade, Shears

- **Botanical Ledger:** keep all journal pages; restyle as a ledger with tab dividers. Add a **"Breeding Notebook"** page that logs observed mutations (only shown once triggered) — progressive discovery like AgriCraft's progressive JEI. Display Link source: "species researched x / y".
- **Botanist's Loupe:** hand item showing a HUD readout beside the crosshair: species, growth, stat bars in stat colours, soil, active influences, blight. **Create's Engineer's Goggles show the same readout natively** (the crop block entity implements Create's goggle interface), so no merge recipe is needed. Jade stays a compact one-liner. A gear-spindle genome display is a possible later polish.
- **Seed Satchel:** no enchant gate (the 1.18 enchant requirement was friction for little gain); instead the satchel needs a **brass dial upgrade** to enable sorting. Same capacity and sorter logic; the sorter dial is shown on the item model via an item property.
- **Transplanting Spade / Pruning Shears:** the same function as trowel and clipper.

### 3.13 Seed Vault (storage, rebuilt) — dropped

> **Dropped:** seeds stack per genome, Create item vaults and the stock system store them, and the Phase 3 filter attributes (species, stat ≥ N, assayed) do the sorting.


- **Card-Catalogue Cabinet** blocks, each with 8 drawers. Each drawer locks to a species and stores unlimited seeds keyed by genome (counts per unique genome).
- Adjacent cabinets merge into one vault. A **Vault Desk** controller provides the GUI: filter by species, sort by stat, show assay data.
- Exposes `IItemHandler` (paged virtual slots) so Create funnels, stock links and packagers can pull "best seed of species X".

### 3.14 Livestock Trough (Animal genes, re-framed) — dropped

> **Dropped:** a second gene system plus a block, to automate animal breeding that Create deployers already handle.


- Keep fodder genes (cow, pig, sheep, chicken, rabbit…) on crops.
- A **Livestock Trough** accepts harvested fodder and, if the crop's genome carries that animal's gene, breeds nearby animals on a cooldown. This is more controllable than mobs wandering into fields, and it is Create-feedable.
- The mobs-eat-fields AI from 1.18 stays as an optional config.

### 3.15 Apiculture — Bees as Pollinators

**Goal:** a beehive near a breeding field should be *worth placing* (a clear early-game boost) without becoming mandatory or making high-tier breeding trivial.

**Mechanism:** bees are the first user of the generic **crop influence** system (`api/influence`). A `cultivar:crop_influence` datapack entry targets `minecraft:beehive` and `minecraft:bee_nest`. Crops find hives through the vanilla **POI index** (no block scanning), cache the result, and refresh it slowly (on the order of once per minute per crop, spread across random ticks).

**Range:** radius 5, height ±2, so an 11×11 field around the hive. That covers a typical breeding grid without blanketing a whole farm.

**Strength scales with the colony, read from the hive's honey level** (a blockstate, so it's free to read):
`scale = (1 + honey_level) / 6`, giving 0.17 for an empty hive and 1.0 for a full one.
- Bees only raise honey by foraging flowers, so a working colony is required for the full effect. Plants tagged `cultivar:bee_forage` (flowering cultivars) count as forage.
- **Trade-off:** harvesting honey or combs resets the honey level and temporarily weakens pollination. Players choose between honey and breeding pressure.

**Effects at full strength (scale 1.0):**

| Effect | Value | What it means in practice |
|---|---|---|
| `fertility_bonus` | **+1** | Pollination. Parent-selection chance per parent goes from fertility/10 to (fertility+1)/10 |
| `mutation_multiplier` | **×1.25** | A 25 % mutation becomes 31.25 %; a 50 % one becomes 62.5 % |
| `mutativity_bonus` | **+1** | Offspring stat drift as if Vigour were one higher: drift chance +2 % and the upward bias +2 %. Stats still have to be *bred* up; bees never add stat points directly |
| `weed_multiplier` | **×0.75** | At resistance 1, the weed share of ticks drops from 50 % to 37.5 % |
| `growth_multiplier` | ×1.0 | No growth speed-up, to avoid overlapping with sprinklers |

**Why these numbers are noticeable but not overpowered:**
- **Early game (fertility 3):** a two-parent cross needs both parents to pass their fertility roll: 0.3 × 0.3 = 9 % per tick without bees, 0.4 × 0.4 = 16 % with them (×1.8). Add the ×1.25 mutation multiplier and mutation events come about **2.2× as often**. That is very visible in the first hour of breeding.
- **Late game (fertility 10):** the fertility bonus is capped out and only ×1.25 mutation remains, a **1.25×** edge. Useful, not mandatory.
- **Stacking is bounded:** several hives don't stack (the strongest hive in range applies). Combining with other influence sources is capped by server config: mutation ×1.5 maximum, weed multiplier 0.5 minimum. Pack makers can tune all of it in the datapack entry.

**Feedback:** a pollen particle drifts over influenced crops (client-config toggleable); the Botanist's Loupe and goggles list active influences ("Pollinated: Beehive 83 %").

**Steampunk upgrade (dropped 2026-11-10): Brass Apiary.** Kept for reference only; the vanilla beehive influence covers it. A Create-powered hive. Its strength scales with RPM instead of honey (`cultivar:kinetic_speed` scaling type), it has radius 7, and it outputs honey as a fluid through pipes, so automated farms don't have to trade honey against pollination. Its effects are the same as a full beehive's: the upgrade buys reliability and reach, not more power.

**Vanilla note:** vanilla bees' crop-growing behaviour only targets vanilla crop blocks, so it won't touch Cultivar crops. The influence system replaces it on purpose.

---

## 4. Graphics & Audio Direction — Excalibur base

> **Shipping (2026-11-12):** the default textures are now built from vanilla sources by the same scripts (`tools/art/build_art.py`: stand-in sprites, palettes lifted for vanilla's brightness, brass leading redrawn on glass). The Excalibur-kitbashed set described below ships as the built-in resource pack "Cog & Cultivar: Excalibur Style" (`resourcepacks/excalibur_style`, off by default).

You have the author's permission to use the Excalibur pack as a base. These rules keep new assets consistent with it and with Create.

**Source:** Excalibur V26.3_01 (Maffhew), pulled from Modrinth (`excal`). All picks below are confirmed present at 16×16 in `assets/minecraft/textures/`. The pack's public licence is CC BY-NC-ND 3.0, so keep the author's written permission on file and credit "Excalibur by Maffhew" in the mod description and credits.

**What the pack actually looks like (from sampling):** it is **much darker and lower-contrast than vanilla**. Iron runs `#312d2b → #6d655d` and dark oak `#2a2620 → #504538`. Metals are warm and desaturated; the only bright accents are gold and copper. New blocks must sit in that value range or they will glow next to Excalibur terrain.

**Principles:**
1. **Resolution 16×16.** Blockbench models with 16-px UVs; no HD-only detail.
2. **Kitbash, don't redraw.** Start from the Excalibur sprites in the table below, recolour with the ramps, and keep the pack's dark outlines and bevelled plank edges.
3. **One shared brass ramp.** Create's brass (`#7a4c35 → #f8d375`) is warmer-orange and brighter at the top than Excalibur gold (`#845d21 → #f4e791`). The blended ramp keeps Excalibur's value structure with the hue pulled halfway toward Create, so it sits next to both Excalibur gold and Create brass casings:

| Ramp (dark → light) | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|
| Excalibur gold (sampled) | `#845d21` | `#ab7c2c` | `#c49839` | `#d6ba53` | `#dfca5d` | `#f4e791` |
| Create brass_block (sampled) | `#7a4c35` | `#b17f4f` | `#cea05a` | `#d7aa5e` | `#e4b763` | `#f8d375` |
| **C&C brass (use this)** | `#845125` | `#ab7531` | `#c4943d` | `#d6b055` | `#dfbe5e` | `#f4df8d` |
| **C&C blackened steel** (Excalibur anvil, +blue) | `#202022` | `#252629` | `#2f2f32` | `#363639` | `#3f3e42` | `#484748` |
| Excalibur iron (sampled) | `#312d2b` | `#3d3834` | `#443f3b` | `#4e4843` | `#5a534d` | `#6d655d` |
| Excalibur copper (sampled) | `#734235` | `#98553f` | `#a35c43` | `#bc704f` | `#c37553` | `#dd8b63` |
| Excalibur oxidised copper (sampled) | `#404c43` | `#4f6458` | `#536b5e` | `#5a7c6b` | `#608371` | `#7ba28b` |
| Excalibur dark oak (sampled) | `#2a2620` | `#322b26` | `#38302a` | `#3d362f` | `#443d34` | `#504538` |

   Swatch image: `palette_swatches.png` (sent with this doc). Use ramp steps 5–6 sparingly (rivet and edge highlights only), as Excalibur does.
4. **Dieselpunk accents:** blackened steel for late-tier parts, soot gradients on the top faces of heat sources, and Excalibur's own copper oxidation stages for pipework.
5. **Plants:** match Excalibur's crop sprites (sample from its `wheat_stage*`, `sweet_berry_bush_stage*` and flower textures). Ore-blooms get 2–3 metal-coloured highlight pixels on the petals and a wire-wrapped stem. Blight is grey-brown with rust-orange speckle, kitbashed from `dead_bush` and `vine`.
6. **Animated parts are Create partial models** (shafts, cogs, sprinkler heads, assay drum, gauge needles) rendered through Create's kinetic renderer, so they animate like Create and can use Flywheel instancing.
7. **Connected textures:** Excalibur's CTM (glass, planks, iron block and more under `optifine/ctm/`) is OptiFine-format and won't apply to our blocks on NeoForge. Give the Conservatory glass and frames Create's own CT system (`CTSpriteShifter` + `ConnectedTextureBehaviour`), using Excalibur's `glass.png` border style for the connected sheet.

**Asset list (first pass, with concrete Excalibur bases):**
| Asset | Excalibur base sprite(s) | Notes |
|---|---|---|
| Trellis — timber | `block/spruce_planks`, `block/scaffolding_side` | 3 thin posts, lashing detail |
| Trellis — iron / brass / steel | `block/iron_bars` recoloured to each ramp | Brass and steel tiers get corner brackets |
| Grafting Frame | trellis + `block/anvil` metal for the clamp | 2×2 px pressure-dial needle |
| Assaying Engine | `block/smithing_table_front`, `block/cauldron_side`, `item/spyglass` (lens) | Drum and lens arm are partials |
| Cistern | `block/barrel_side`, `block/barrel_top`, `block/cauldron_side` | Funnel top, rain-level overlay |
| Sluice Trough | `block/dark_oak_planks`, `block/iron_trapdoor` (banding) | |
| Sluice Gate wheel | `block/iron_trapdoor`, brass ramp | Hand-wheel partial (1.18 valve concept) |
| Rotary Sprinkler | `block/hopper_outside` body, brass ramp head | Head partial; small water-arc particles |
| Mist Nozzle / Drip Line | `block/copper_grate`, `block/exposed_copper` | Steam particles |
| Conservatory frame | `block/iron_block` recoloured to blackened steel | CT via Create |
| Conservatory glass | `block/glass`, `block/glass_pane_top` | CT sheet built from Excalibur's border |
| Climate Gauge | `item/clock_00` and `item/compass_00` dial faces on a brass plate | 3 needle partials |
| Card-Catalogue Cabinet | `block/chiseled_bookshelf_occupied`, `block/bookshelf` woodwork | Brass label holders per drawer |
| Botanical Ledger | `item/writable_book`, `item/book` | Brass corners, tab dividers |
| Botanist's Loupe | `item/spyglass` | Shorten the barrel, add a lens ring |
| Seed Satchel | `item/bundle`, `item/leather` | Brass dial overlay as an item property |
| Transplanting Spade | `item/iron_shovel` | Narrower blade |
| Pruning Shears | `item/shears` | Brass pivot |
| Hand Tiller | `item/iron_hoe` | |
| Assay Card | `item/paper` | Hole columns per stat as an overlay layer |
| Seeds | `item/wheat_seeds` as the shared base, tinted per species | |
| Blight ×3 stages ×3 types | `block/dead_bush`, `block/vine` | |
| Conditioned soil overlays | `block/farmland`, `block/farmland_moist` | Tint or overlay for humidity, acidity and nutrients |

**Audio:** the valve wheel squeal (as 1.18's `valve.ogg`), card-punch clack, assay drum whirr (looped, pitch scales with RPM), sprinkler hiss, steam mist puff, gauge needle tick, blight crunch.

---

## 5. 1.21.1 Technical Architecture

### 5.1 Registries
| Kind | Mechanism |
|---|---|
| `plant`, `soil`, `weed`, `mutation`, `fertilizer`, `trellis_material` | **Datapack registries** (`DataPackRegistryEvent.NewRegistry`, synced codec) |
| `stat`, `gene`, `growth_condition_type`, `plant_modifier_type`, `soil_modifier_source` | **Static synced registries** (`RegistryBuilder`) |
| Blocks, items, BEs, menus, data components, attachments, recipe types, particles, sounds | `DeferredRegister` |

### 5.2 Data
- **Item data components:** `genome`, `assay` (card data), `satchel_contents`, `spade_plant`, `ledger_data`, `loupe` (unit).
- **Attachments:** chunk-level `soil_conditioning` (sparse map); level-level `conservatories` (or `SavedData`); entity-level fodder AI flag.
- **Crop block entity:** holds `Holder<Plant>`, the genome, growth stage index, weed id and stage. Sync with `getUpdateTag` / `ClientboundBlockEntityDataPacket` only on change; set `ModelData` for the baked plant model.

### 5.3 Rendering
- **Plants:** a custom baked model that builds quads per plant and stage. Shapes: `#` (hash), `X` (cross), `+` (plus), `rhombus`, as the 1.18 quad generator did, chosen per plant in JSON. Texture lookup per stage. This avoids a BER per crop (important for big farms).
- **Kinetic and animated parts:** BERs extending Create's kinetic renderer, using partial models.
- **Overlays:** the loupe overlay goes through `RegisterGuiLayersEvent`; genome spindle rendering in world space uses `RenderLevelStageEvent`.

### 5.4 Capabilities
- `Capabilities.FluidHandler.BLOCK` on the Cistern, sprinkler, mist nozzle, drip line and trough.
- `Capabilities.ItemHandler.BLOCK` on the Assaying Engine (sided) and Seed Vault.

### 5.5 Networking
`CustomPacketPayload` records with `StreamCodec`: satchel sorter scroll, ledger page flip, loupe inspect request, gauge state sync (or rely on BE sync).

### 5.6 Performance budget
- Crops: `randomTick` only. Requirements cached and invalidated by neighbour change. **No per-crop server tick.**
- Water: network graph (troughs) or Create pipes (no custom tick).
- Sprinklers: 1 column per tick, chunk-loaded check, sleep when the buffer is empty.
- Conservatory: incremental scan budget; revalidate only touched sections.
- Soil conditioning: lazy decay, computed on read.

### 5.7 Create API touchpoints (verified against Create 6.0.11, `mc1.21.1/dev`)

Dependencies: Create `6.0.11`, Ponder `1.0.85` (`net.createmod.ponder`), Flywheel `1.0.6` (range `[1.0.0,2.0)`).

| Need | Create 6 API | Notes |
|---|---|---|
| Our own contraption actors (Weed Rake) | `com.simibubi.create.api.behaviour.movement.MovementBehaviour` + `MovementBehaviour.REGISTRY.register(block, behaviour)` (or `.onRegister(movementBehaviour(...))` with Registrate) | `SimpleRegistry` — one value per block, throws on duplicate |
| Harvester support for our crops | Mixin into `content.contraptions.actors.harvester.HarvesterMovementBehaviour#visitNewPosition` | Can't re-register (see §3.10) |
| Kinetic machines | `content.kinetics.base.KineticBlockEntity` (+ `KineticBlockEntityRenderer`) | |
| Stress impact | `api.stress.BlockStressValues.IMPACTS.register(block, () -> su)` | Also `CAPACITIES`, `RPM` for generators |
| Goggle tooltips | `api.equipment.goggles.IHaveGoggleInformation#addToGoggleTooltip(List<Component>, boolean)` | |
| Fertigation by Spout onto crops | `api.behaviour.spouting.BlockSpoutingBehaviour` — `BY_BLOCK` / `BY_BLOCK_ENTITY` registries; implement `fillBlock(level, pos, spout, fluid, simulate)` returning mB used | |
| Display Link outputs | `api.behaviour.display.DisplaySource` — registered into the static registry `CreateRegistries.DISPLAY_SOURCE`, bound to blocks via `DisplaySource.BY_BLOCK` (`SimpleRegistry.Multi`) | Multiple sources per block allowed |
| Genome filters | `content.logistics.item.filter.attribute.ItemAttributeType` registered under `CreateRegistries.ITEM_ATTRIBUTE_TYPE`; each creates `ItemAttribute`s with `appliesTo(stack, level)` | Package is `content.…`, not `api.…` — expect minor churn between Create versions |
| Conservatory heat | `api.boiler.BoilerHeater.findHeat(level, pos, state)` + `BoilerHeater.REGISTRY` | Reuse it: any block that heats a Create boiler (Blaze Burner, modded heaters) automatically heats a Conservatory. Also `BlazeBurnerBlock.getHeatLevelOf(state)` → `HeatLevel {NONE, SMOULDERING, FADING, KINDLED, SEETHING}` |
| Fan processing (ore-petal washing) | `CreateRegistries.FAN_PROCESSING_TYPE`; plain `create:splashing` recipe JSON is enough | |
| Connected glass/frames | `foundation.block.connected.CTSpriteShifter`, `ConnectedTextureBehaviour`, `CTModel`, `GlassPaneCTBehaviour` | Internal `foundation` package. Use it because Excalibur's own connected textures are OptiFine-format and won't apply to our blocks on NeoForge |
| Recipes | JSON types `create:mixing`, `create:milling`, `create:crushing`, `create:splashing`, `create:haunting`, `create:filling`, `create:emptying`, `create:deploying`, `create:item_application`, `create:sequenced_assembly` | Heat requirement on basin recipes: `"heat_requirement": "heated"/"superheated"` |
| Ponder scenes | Separate Ponder library (`net.createmod.ponder`), plugin-based registration | |

---

## 6. Roadmap

| Phase | Scope | Exit test |
|---|---|---|
| **0 — Foundation** ✅ | Module system, registries (static + datapack), genome component, crop-influence API, server config, versioned network, ServerWorkQueue, datagen, sidedness guard, dedicated-server CI tests | CI green on a dedicated server |
| **1 — Core** | Trellis, crop BE, Bell Cloche, datapack plant/soil/mutation content, growth tick, breeding, weeds/blight, harvest, **influence lookup + beehive pollination (§3.15)** | Breed a vanilla mutation chain; stats inherit as specified; a full hive measurably raises mutation rate |
| **2 — Tools & info** (Loupe and Tillers later removed: goggles and hoes cover them) | Spade, Shears, Tiller, Satchel, Loupe (+ goggle merge), Ledger, Assaying Engine (kinetic) (Assay Card dropped) | Full manual breeding loop with diegetic info |
| **2.5 — Tall crops** ✅ | Plant `height`, auto-stacking trellis segments, sugarcane/cactus/bamboo data (Wall Trellis deferred to Phase 6 content) | A 3-high sugarcane grows on a stacked trellis |
| **3 — Create farming** ✅ | Harvester override, deployer actions, item attributes (stat filters), comparator/Display Link, **cull loop: Millstone seeds → Seed Meal → fertilizer** | Fully automated plant–harvest–sort loop |
| **4 — Water** ✅ | One block: the **Sprinkler** (1000 mB water tank, runs only while it has water, 1 mB per N ticks; adds random ticks + occasional BonemealEvent pulses to growables in a 9×9 area, moistens farmland; fed by any NeoForge fluid-capability pipe: Create, Pipez, Modern Dynamics). Cisterns/drip lines dropped; troughs return as the Irrigation Channel in Phase C | Sprinkler field fed by Create pipes |
| **5 — Climate** ❌ dropped | Temperature added no gameplay; soils, the Sprinkler and the Bell Cloche already cover climate control. Left to addons via the API (see ARCHITECTURE.md "Extending from an addon") | — |
| **Planter Bed** ✅ | End-stage breeding machine. Self-contained, pipe-fed (soil, ranked seed stock, fertilizer, trellis + cloche supply, 4000 mB water, 9 outputs); touching beds form one plot (shared capabilities, balanced water, plot-wide goggles + comparator). Places fed trellises/cloches itself; redstone strips them back. Breeder beds (grafting frame above) bank newborn children as seeds and re-arm; producer beds plant the best seed, hold mature crops for neighbouring breeders, harvest, and upgrade to better seeds of the same species. Ranking = weighted stats (config) | A self-improving plot fed only by pipes |
| **Trial Bed** ✅ | Research plot (brass casing): touching Trial Beds grow and cross their stocked seeds for real with every limiter removed (ideal soil host: no requirements, weeds or mutation habitats). Free trellises in a checkerboard: producers plant random stocked species (spending a seed), hold mature to parent, then rotate; frames breed, and a child of a new species records that mutation in the Ledger. Bonus: every N minutes (config, default 5) a roll at a configurable chance (default 5% for a full 9×9, scaled by plot size) discovers a random untested mutation whose parents are both stocked, records parents + child in the plot's Ledger (recipe appears), and spends one seed of each parent. Inputs: seeds + Ledger; output: the Ledger | A 9×9 fills a Ledger over time |
| **6 — Content** (slimmed) | **Ore-blooms** ✅ (22 blooms incl. 12 low-value stepping stones, data-only, drop `create:crushed_raw_*` and gems), **habitat blight variants** ✅ (9, data-driven `habitat`). Derelict Conservatories ✅ (greenhouse + lean-to ruins, decay processor, loot). Optional: Wall Trellis. Dropped: Brass Apiary, Seed Vault, Livestock Trough/fodder genes, Ore Petals, Brassleaf gating | Pack-ready |
| **Later — Optional compat** | Serene Seasons (`cultivar:season` growth condition; cloche/conservatory season immunity), loaded only when the mod is present | No effect or cost without the mod |
| **7 — Polish** | Excalibur-based asset pass (blooms ✅, blights ✅), sounds ✅ (vanilla/Create events), advancements ✅, Ponder scenes ✅ (spreading, stats, crossbreeding, assaying), JEI ✅ / EMI categories, Jade | Release |

### 6.1 Field farming roadmap (phases A–E, planned 2026-11-13)

The trellis is the breeding half of the mod; these phases build the everyday farming half. Decisions taken:
Cultivar seeds of every species (vanilla ones included) plant as **field crops** that keep the genome; field crops
**respect Strength** like trellis crops; ripe vine blossoms can be taken by **shears, Deployers and Saws**.

**Ground rules for every step** (each step is one PR; maintainability over "works for now"):
- Follow `docs/ARCHITECTURE.md`: one module per feature (`content/<feature>/<Feature>Module` + one line in
  `CultivarModules`), client code only under `client/`, server-safe ticking, no per-tick world scans.
- Reuse, don't fork: growth goes through the existing `GrowthCondition`/`GrowthContext`, `Hydration`,
  `SoilAmendments`, `CropLight` and influence lookup; stats through the genome engine; Create support through
  `HarvesterMovementBehaviourMixin` and the deployer hooks. Where a shared piece needs a change, change it in place
  (with its tests) rather than adding a parallel path.
- Data-driven first: anything a pack maker might tune is a datapack registry, tag or server config, documented in
  `PACKMAKERS.md`.
- Each PR carries its own lang, JEI page, tooltips, recipes, art as a `tools/art/gen_*.py` script run through
  `build_art.py` (both styles), gametests, and spec/parity-audit lines. Bump the network protocol when synced data
  changes. CI green on both Create versions before merge.

**Phase A: Field crops (no trellis)**

| Step | Scope | Done when |
|---|---|---|
| A1 ✅ | Extract the crop core shared by trellis and field crops (genome, stage, growth tick, drops, conditions) out of `CropBlockEntity` behind a small interface, with no behaviour change | Existing gametests pass unchanged |
| A2 ✅ | `cultivar:field_crop` block + BE on the core; plant renderer reuses `CropBakedModel` quads without the frame | A field crop renders and grows by tick |
| A3 ✅ | Seed placement: a Cultivar seed used on a soil the plant accepts places a field crop; otherwise the action bar says why (shared reason text with goggles) | Planting rules match the trellis for every shipped plant (gametest) |
| A4 ✅ (no influences/cloches on field crops yet) | Growth, Strength tolerance, Gain/yield drops, conditioners, cloches (fitted on field crops), Sprinkler, influences | Field and trellis crops grow at equal rates on equal soil (gametest) |
| A5 | No breeding/spread/weeds on field crops; trellis placed over a field crop adopts it | Adopting keeps genome and stage |
| A6 | Create: harvester resets to stage 0, deployer plants and harvests, plough leaves field crops alone; Jade/goggles readout | Contraption harvest loop gametest |

**Phase B: Hand farming**

| Step | Scope | Done when |
|---|---|---|
| B1 | Right-click harvest + replant on field crops, trellis crops and vanilla `CropBlock`s (config `farming.rightClickHarvest`) | Works for all three; respects Fortune-free drops |
| B2 | Scythe (wood/iron/brass): 3×3/3×3/5×5 harvest + replant, durability | Deployer-usable |
| B3 | Dibber: plants a row of 3–5 from satchel/offhand, sneak for one | Uses the A3 placement path |
| B4 | Watering Can (+ brass tier): fill at water, moisten farmland and give random-tick bursts in 3×3/5×5 | Shares the Sprinkler's tick-burst helper |
| B5 | Scarecrow: no trampling or mob crop damage within 8 blocks (tag-driven entity list) | Trample gametest |

**Phase C: Irrigation without water blocks**

| Step | Scope | Done when |
|---|---|---|
| C1 | Irrigation Channel: open trough fluid network (tank per segment, balanced like Planter Bed plots), pipe-compatible | Water flows along a channel line |
| C2 | Channel hydrates farmland within 4 (FarmBlock mixin) and counts as "water beside" in `Hydration` | Farmland stays moist with no water block (gametest) |
| C3 | Archimedes Screw: kinetic, bottom in water, lifts 1 block per segment, stackable, output scales with RPM into channels/pipes | Screw feeds a raised channel field |
| C4 | Mulch: soil conditioner (−1 humidity) that also keeps farmland moist for N days without water | Data entry + farmland timer |

**Phase D: Flowering vines (dye gaps)**

| Step | Scope | Done when |
|---|---|---|
| D1 | Vine block on the plant registry (`growth: vine`): climbs walls/trellises, slow spread up and sideways, flowering stage | One vine climbs and flowers |
| D2 | Blossom harvest by shears, Deployer and Saw; resets to leafy; blossoms craft or mill to dye | Contraption saw harvest gametest |
| D3 | Seven vines: Ivy Bloom (green), Hops (lime), Morning Glory (cyan), Wisteria (purple, hangs), Ash Creeper (gray), Cinnamon Vine (brown), Nightshade (black); art script | Each colour obtainable |
| D4 | Breeding: vines cross on trellises into the mutation tree; `check_crosses.py` covers them | Checker green |

**Phase E: Automation sweep**

| Step | Scope | Done when |
|---|---|---|
| E1 | Audit every crop-like block against harvester, deployer, plough, saw, drill | Gametest per block × contraption |
| E2 | Ponder scenes: field crops, hand tools, channels + screw, vines | Lang check green |
| E3 | Balance pass (`sim_breeding.py` + growth rates) and release notes | Tagged minor version |

---

## 7. Open decisions & sources that would help

**Decisions:**
1. Keep AgriCraft's exact numbers for familiarity, or rebalance (for example, faster default growth since automation adds friction)?
2. Hardcore stat inheritance as the default, or only an option?
3. Temperature and soil conditioning: core features, or a separately toggled "climate module"?
4. Is a Hardiness/pollution stat wanted for Steamlands specifically? Which pollution source should it read from, if any?

**Sources:** Create 6.0.11 and Excalibur V26.3_01 have both been pulled and folded in (§4, §5.7). The only remaining optional source is Serene Seasons for 1.21.1, if season support is kept.

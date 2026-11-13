# Parity audit (2026-11-10)

Every block and item checked against the mod's aims and against each other. The aims, as set during development:

1. **A Create addon first:** Create look (casings, spout/pump shape language, Create textures), Create behaviour (goggles,
   wrench, Ponder, item descriptions, Display Links, comparators, filters, pipes), and automation as the end game.
2. **Purposeful, minimal additions:** one block per job; every step is a choice, not chores; no seed spam.
3. **Extensible by pack makers:** data-driven, with optional mods gated by conditions, shipped tools and documented
   API.
4. **Server-safe and light:** synced state, no per-frame block-entity renderers, cheap ticks, tests on a dedicated
   server.
5. **Excalibur art, pack-driven where possible.**

Legend: ✅ present · ⚠️ partial · ❌ missing · — not applicable

## Cross-cutting standards

| Standard | Status | Notes |
|---|---|---|
| Create-style item descriptions (hold Shift: summary, behaviours, controls) | ❌ | None of our 17 items/blocks have them. Create shows these on every block; players expect them |
| Ponder scenes | ❌ | None. Planned for Phase 7; the multi-step machines (beds, engine, sprinkler) need them most |
| Wrench (rotate / sneak-pick-up) on Create-styled blocks | ⚠️ | Assaying Engine only (kinetic, inherited). Sprinkler, Planter Bed and Trial Bed don't respond to the Create Wrench |
| Goggle readout on machines | ⚠️ | Trellis, Assaying Engine, Planter Bed and Trial Bed have it; the Sprinkler doesn't |
| Comparator output on machines | ⚠️ | Trellis, Assaying Engine and Planter Bed have it; Trial Bed and Sprinkler don't |
| Display Link source on machines | ⚠️ | Trellis and Assaying Engine have one; Planter Bed and Trial Bed don't |
| Consistent redstone meaning | ⚠️ | Planter Bed: redstone = strip + dump. Trial Bed, Sprinkler and Assaying Engine ignore redstone |
| JEI coverage | ⚠️ | Seed subtypes only. No categories for mutations (with habitats), the Assaying Engine, fertilizers, soils, or Trial/Planter Beds; Cutting has no subtype, so JEI shows a blank Cutting |
| Mining tool tags | ⚠️ | Machines are tagged pickaxe; trellises have no tag (timber should be axe, iron pickaxe) |
| Advancements / progression | ✅ | 12 advancements on `cultivar:learned_plant` (#49) |
| Custom sounds | ❌ | Vanilla sounds throughout (Phase 7, optional) |
| Translations | ⚠️ | en_us only (normal for now) |
| Pack-maker tooling | ✅ | `/cultivar export`, scaffold script, conditions, documented API |
| Server safety and tests | ✅ | 33 dedicated-server game tests, CI with and without optional mods, sidedness check |

## Blocks

| Block | Look | Goggles | Jade | Comparator | Display Link | Pipes | Redstone | Wrench | Tests | Needed |
|---|---|---|---|---|---|---|---|---|---|---|
| **Timber / Iron Trellis** | ✅ Excalibur | ✅ | ✅ | ✅ | ✅ crop status | — | — | — | ✅ | Axe/pickaxe tool tags; item descriptions; Ponder (planting, grafting, stacking, cloche) |
| **Assaying Engine** | ✅ brass casing | ✅ | ✅ (capability) | ✅ | ✅ ledger progress | ✅ items | ❌ | ✅ | ✅ | Item description; JEI category; redstone pause (Create convention for processors); Ponder |
| **Sprinkler** | ✅ Spout style | ❌ | ⚠️ tank via capability | ❌ | ❌ | ✅ fluid | ❌ | ❌ | ✅ | Goggles (water, running, area); comparator (tank level); redstone off-switch; wrench rotate/pick-up; item description; Ponder |
| **Planter Bed** | ✅ andesite casing | ✅ plot-wide | ✅ (capability) | ✅ plot-wide | ❌ | ✅ items + fluid | ✅ strip + dump | ❌ | ✅ | Display Link (plot status: best seed, stock, outputs); wrench pick-up keeping contents; item description; Ponder (plot, breeder/producer, dump mode) |
| **Trial Bed** | ✅ brass casing | ✅ plot-wide | ✅ (capability) | ❌ | ❌ | ✅ items | ❌ | ❌ | ✅ | Comparator (Ledger completeness); Display Link (ledger progress, as the Assaying Engine has); redstone pause, or dump to match the Planter Bed; wrench; item description; Ponder |

## Items

| Item | Aim fit | Needed |
|---|---|---|
| **Cultivar Seed** | ✅ genome, assay gating, JEI subtypes, filters, milling, compost | Item description |
| **Cutting** | ✅ clone loop, Strength take roll, millable, assayable (seed-planting bypass fixed in #33) | JEI subtype (hide the blank one); item description; optional: Planter Bed accepts cuttings with the take roll, for clone automation |
| ~~Assay Card~~ | Removed (#35): it was a souvenir nothing used | — |
| **Seed Meal** | ✅ cull loop, fertilizer, compost | Item description; JEI fertilizer info |
| **Bell Cloche** | ✅ cloche influence, +1 humidity step, placed by beds | Item description |
| **Transplanting Spade** | ✅ | Item description; deployer behaviour unverified in game |
| **Pruning Shears** | ✅ cuttings, clip products | Item description; deployer behaviour unverified |
| ~~Timber / Iron Tiller~~ | Removed (#37): hoes clear blight on a trellis; beds have none | — |
| ~~(old)~~ Timber / Iron Tiller | ⚠️ area weeding; tiers differ only in durability and reach | Confirm they still earn their place now that beds have no weeds and hoes clear blight on single trellises. Candidate to drop, or merge into one tool |
| **Seed Satchel** | ✅ seed harvest (Vigour), dial cull, pick-up | Item description; deployer seed harvest unverified |
| **Brass Dial** | ✅ satchel upgrade | Item description |
| ~~Botanist's Loupe~~ | Removed (#37): Create goggles show the crop readout | — |
| ~~(old)~~ Botanist's Loupe | ⚠️ overlaps with Create goggles (both show crop info) | Differentiate: goggles give the summary; the Loupe shows full allele pairs and hidden stats, matching the Assay Card. Or drop it in favour of goggles |
| **Botanical Ledger** | ✅ GUI, Engine/Trial recording, Display Link | Item description; JEI "researched" link |

## Planned content still open

- Phase 6: ~~ore-blooms~~ (done), ~~habitat blight variants~~ (done). Brass Apiary dropped.
- Optional: Wall Trellis.
- Phase 7: Ponder, JEI categories, sounds, final art pass.

## Prioritised change list

1. **Create item descriptions for every block and item.** Low effort, lang-driven, and the biggest visible parity gap.
2. **Machine parity:**
   - Sprinkler: goggles, comparator, redstone off.
   - Trial Bed: comparator, Display Link, redstone.
   - Planter Bed: Display Link.
   - Wrench support on Sprinkler and both beds.
   - One redstone convention across machines: pause, or strip + dump for plots.
3. **Tool tags for trellises** (axe for timber, pickaxe for iron).
4. **Purpose review:** done: Assay Card, Loupe and Tillers removed (#35, #37).
5. **JEI:**
   - Cutting subtype.
   - Categories for mutations (parents, child, habitat), the Assaying Engine, fertilizers and soils.
   - Info pages for beds and the Sprinkler.
6. ~~**Bell Cloche +1 humidity**~~ (done, with heat as the drying lever).
7. ~~**Advancements:**~~ (done) a short progression from first trellis to the first mutation, the Assaying Engine, a Planter Bed plot and a full Ledger.
8. **Phase 6 content:** ~~ore-blooms~~, ~~blight variants~~ (done); Brass Apiary dropped.
9. **Phase 7:** Ponder scenes for every block, sounds, art pass.
10. **In-game verification:** deployer actions with spade, shears, satchel and cutting; Pipez and Modern Dynamics feeding the Sprinkler and beds.

## Progress

- 2026-11-10: items 1–3 done (#36):
  - Create item descriptions on every block and item (Assaying Engine also shows stress).
  - Sprinkler: goggles, comparator, redstone off, wrench.
  - Planter Bed: Display Link (plot status), wrench pick-up.
  - Trial Bed: comparator (Ledger completeness), Display Link (trial status), redstone pause and seed dump, wrench pick-up.
  - Trellis tool tags.

  The Assaying Engine deliberately has no redstone pause: Create's kinetic processors are stopped with a clutch or
  gearshift instead. The Assay Card was removed (#35).
- 2026-11-10: ore-blooms: 10 data-only resource crops dropping Create crushed ores, coal, redstone, lapis, quartz,
  emerald and diamond, bred from the vanilla tree under habitat conditions.
- 2026-11-10: 12 low-value stepping-stone blooms (bone meal, dyes, ink, clay, string, slime, glowstone, coal, flint,
  gunpowder, iron nuggets, amethyst); every ore and gem now sits at least four crosses from vanilla.
- 2026-11-10: per-player knowledge (Ledgers merge with their holder both ways; kept through death), JEI
  Crossbreeding category hidden until known (`genetics.revealAllMutations` to show all), info pages for bred
  plants, Cutting subtype, `/cultivar knowledge` commands. Rest of item 5 done the same day (see below).
- 2026-11-10: Bell Cloche +1 humidity; heat sources (`#cultivar:soil_heaters`) dry soil one step; coarse dirt soil.
- 2026-11-10: every cross breedable on plain trellises at base stats (shared-light rule, twilight band);
  `tools/data/check_crosses.py` runs in CI.
- 2026-11-10: JEI finished: Soils (humidity/acidity/nutrients/growth, shift note), Fertilizers, Assaying (seed →
  assayed seed, optional Ledger), info pages for trellises, Bell Cloche, Ledger, Planter Bed, Trial Bed, Sprinkler.
  The pack-maker `check` warns about crosses that can't be bred on plain trellises.
- 2026-11-10: 9 habitat blights (humidity, acidity, nutrients, light) with mature-only cleanup drops; JEI Blight page.
- 2026-11-10: sounds for crosses (chime + sparkle on a new species), hoe-clearing blight, Assaying Engine finish,
  Sprinkler spray, opening the Ledger; 12 advancements on a `cultivar:learned_plant` trigger.
- 2026-11-10: art pass: per-species seed tint (automatic from the product, `seed_color` override), satchel brass
  buckle, Planter Bed loam and Trial Bed pegged-plot floors (no longer vanilla coarse/rooted dirt), brass-framed
  Assaying Engine window. `tools/art/gen_art_pass.py`.
- 2026-11-10: fixed farmland double-counting water (moist farmland beside water read watery, so wheat never grew in
  the standard layout); vanilla crops' requirements now match vanilla (farmland crops light >= 9 on moist or dry
  farmland; sugar cane, cactus, cocoa, glow berries any light). JEI Growing page with matching soils. Game test
  guards vanilla farmland growth.
- 2026-11-10: balance pass. Parent selection 0.5 + 0.05 x Vigour (was v/10: ~1 % cross rate for fresh seeds);
  drift 27-45 % with an upward bias that fades near 10 (was 100 % up at Vigour 10, a snowball). Blight spawns
  3 % (was 10 %). Ore/gem blooms grow 0.3 + 0.02 x Growth; emerald 6 %, diamond 3 %. tools/data/sim_breeding.py.
- 2026-11-10: Ponder scenes: Spreading crops (frames, clones, cuttings, blight), Improving crop stats (the four
  stats, breeding the same species, keeping the best, parallel frames, bees, Vigour), Crossbreeding (habitat, shared
  light, Ledger/JEI, humidity levers, Trial Bed), Assaying seeds (rotation, input/output, speed, Ledger, goggles).
  Text mirrored to lang by tools/ponder/ponder_lang.py (checked in CI); structures by gen_ponder_schematics.py.
- 2026-11-10: Seed Satchel screen (R-click in the air): every stored genome with count and stats (or "not
  assayed"), Take / Void per row, Void unassayed, and an auto-collect toggle so break-and-replant seeds stop
  filling it. Server-validated `satchel_action` packet; protocol 3.
- 2026-11-10: planted crops remember whether their genome is known. Goggles and Jade (via Create's goggle info)
  show stats only for crops grown from assayed seeds/cuttings/satchel entries, spade-moved known crops, or clones of
  known parents; new cross children read "Stats unknown". Seeds and cuttings from a known crop stay assayed.
  `requireAssay = false` shows everything as before. Protocol 4.
- 2026-11-12: soil conditioners (spec §3.6). Agricultural Lime, Sulfur Dust and Compost (Create milling/mixing)
  shift a soil block's acidity or nutrients a step per use, capped at ±2 (`soil.conditionerMaxSteps`); datapack
  `soil_conditioner` registry (humidity too). Stored per soil position in a chunk attachment, survives replanting,
  cleared with the soil. Shown as a dusting inside the trellis (visible under a cloche), ambient dust puffs, and
  Jade/goggle lines; JEI Growing counts soils conditioners can fix. Protocol 5.
- 2026-11-12: Shade and Lamp Cloches. Bell Cloche variants that cap (3) or raise (14) the light their own crop reads,
  configurable, without changing world light, so dark and bright parents can sit side by side. New trellis state
  `cloche_type` (old saves load as bell); Planter Beds take any cloche. Goggles show the light crops read.
- 2026-11-13: roadmap A1. Plant logic extracted to `PlantCore`; `PlantHolder`/`CropView` API; environment hooks
  (`HumidityModifier`, `LightModifier`, `MoistureSource` + farmland mixin); crop tick steps; shared `PlotTracker`
  for beds; module command hub; `checkModuleBoundaries`. No gameplay change. See `docs/EXTENSION_POINTS.md`.

# Pack-maker guide: adding crops and mutations

Cog & Cultivar is data-driven: every plant, soil, mutation, weed, fertilizer and crop influence is a datapack
JSON file. The mod ships two tools so you don't have to write those by hand from scratch.

## 1. Export what's in your pack

In game, as an operator (works on dedicated servers too):

```
/cultivar export            # 200 loot rolls per crop
/cultivar export 1000       # more rolls = more accurate yield ranges
```

This writes `cultivar_export/` in the server folder (or the game folder in singleplayer):

- `crops.json`: every crop-like block from every mod. For each one it records the seed item and max age, the soils it
  accepts (tested with the block's own placement rule), and yields sampled from its loot table at full growth. Stems
  report their fruit. It also says whether an existing Cultivar plant already covers it. The file includes the
  current Cultivar plants, mutations and soils too.
- `cultivar_scaffold.py`: the scaffold script that matches this mod version (Python 3.9+, no packages needed).

## 2. Scaffold a datapack

```fish
cd cultivar_export
python3 cultivar_scaffold.py scaffold crops.json \
    --assets ~/.minecraft/resourcepacks/MyPack.zip \
    --assets ~/.minecraft/mods \
    --assets ~/.minecraft/versions/1.21.1/1.21.1.jar
```

This writes `cultivar_scaffold/`, a complete datapack containing:

- `data/<mod>/cultivar/plant/<crop>.json` for every crop Cultivar doesn't cover yet:
  - **Stages, seeds and products** come from the export.
  - **Growth requirements** use a preset picked from the soils the crop accepts (farmland, nether, dark, arid or
    wild).
  - **Render textures** are read from the crop's blockstate and models in the `--assets` sources. These are searched
    in the order you pass them, so list resource packs first. Crops whose vanilla model isn't a crop shape are
    flagged; give them a `render.models` entry (see `assets/cultivar/models/crop/` in the mod jar for examples).
- `mutation_plan.csv` with one row per new plant.

Useful flags: `--mod farmersdelight` (only one mod, repeatable), `--include-covered`, `--overwrite-plan`.

## 3. Design the tree

Open `mutation_plan.csv` in any spreadsheet and fill in `parent_a`, `parent_b` and `chance` for each child.
Habitat columns are optional:

| Column | Values |
|---|---|
| `light_min` / `light_max` | 0 to 15 |
| `humidity` | arid, dry, damp, wet, watery, flooded |
| `acidity` | highly_acidic, acidic, slightly_acidic, neutral, slightly_alkaline, alkaline, highly_alkaline |
| `nutrients` | none, very_low, low, medium, high, very_high |
| `comparison` | equal (default), at_least, at_most (applies to humidity/acidity/nutrients) |

The same parent pair may produce different children in different habitats (a "fork"). Add a row per child and
give each its own habitat. For a second route to the same child, add a `variant` column with a short suffix.

Then build the JSON:

```fish
python3 cultivar_scaffold.py mutations cultivar_scaffold/mutation_plan.csv
```

## 4. Check it

```fish
python3 cultivar_scaffold.py check crops.json --pack cultivar_scaffold
```

The check reports:

- mutations that name unknown plants
- crosses that can't be bred on plain trellises at base stats (warning only): the parents and the grafting frame
  sit side by side and share light, so a dark-only and a bright-only parent never work together. Each plant has
  its own soil, which heat, water and a Bell Cloche can shift
- forks where a child has no habitat conditions
- the roots of your tree (plants players must find rather than breed)
- crops that still have no plant

## Optional mods

Generated plant and mutation files for modded crops carry `neoforge:conditions` (`neoforge:mod_loaded`), so one
datapack works whether or not those mods are installed. Cog & Cultivar's own Farmer's Delight data does the same.

## 5. Install

Copy `cultivar_scaffold/` into `<world>/datapacks/` (or a global-datapack mod's folder) and run `/reload`.
Everything is plain JSON, so keep editing it by hand from here. Field reference: `docs/design-spec.md` §2 and the
shipped files under `data/minecraft/cultivar/` in the mod jar.

## Tips

- **Habitat conditions** on mutations use tolerance 0, so a high Strength stat never widens them. Players have to
  build the habitat.
- **Water** beside a soil block makes it one humidity step wetter, a Bell Cloche one more, and a lit heat source
  beside or under it one step drier (add blocks to `#cultivar:soil_heaters`). Steps add up. With no water or heat,
  dry farmland reads one step drier. Mud is flooded; coarse dirt is dry.
- **Resource crops** are ordinary plants: copy one of `data/cultivar/cultivar/plant/ferrous_bloom.json`, point its product
  at your metal's crushed ore (or nugget) and add a mutation. Give it a `chance` below 1 to keep yields modest.
- **Knowledge** belongs to each player and is filled from Botanical Ledgers. JEI's Crossbreeding category only
  shows a cross once the player knows the child and both parents. Op commands, handy for testing or quest rewards:
  `/cultivar knowledge grant <players> all|<plant>`, `/cultivar knowledge revoke <players> all|<plant>`,
  `/cultivar knowledge list <player>`. Set `genetics.revealAllMutations = true` in `cultivar-server.toml` to show
  every cross in JEI and the Ledger.
- **Overriding art:** a resource pack can re-skin any crop by overriding the textures it names, or the
  `cultivar:crop/...` model.

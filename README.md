# Cog & Cultivar

A NeoForge 1.21.1 crop-genetics and breeding mod for steampunk/dieselpunk worlds, with deep Create 6 integration. A clean-room reimplementation inspired by AgriCraft's mechanics.

- Design spec: [`docs/design-spec.md`](docs/design-spec.md)
- Palette: [`docs/palette_swatches.png`](docs/palette_swatches.png)

| | |
|---|---|
| Mod id | `cultivar` |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.219 |
| Create | 6.0.11 (Ponder 1.0.85, Flywheel 1.0.6) |
| Java | 21 |

## For pack makers

Every plant, soil and mutation is datapack JSON. `/cultivar export` dumps every crop in your pack and ships a
scaffold script that drafts plants and mutations for you. See [docs/PACKMAKERS.md](docs/PACKMAKERS.md).

## Art styles

The default textures are kitbashed from vanilla Minecraft. An Excalibur-style set (kitbashed from the Excalibur
resource pack, used with the author's permission) ships inside the jar as a built-in resource pack: turn on
**Cog & Cultivar: Excalibur Style** in Options → Resource Packs, above Excalibur.

Both sets are built by one script from the same designs (`tools/art/gen_*.py`):

```fish
python3 tools/art/build_art.py --excalibur ~/path/to/excalibur/assets/minecraft/textures
```

It defaults to the vanilla 1.21.1 client jar in the Gradle cache (`--vanilla <jar>` to override) and rewrites
`assets/cultivar/textures` (vanilla style) and `resourcepacks/excalibur_style` (only the textures that differ).
Excalibur-derived art (everything under `resourcepacks/excalibur_style`) is not covered by the MIT license.

## Building

```fish
./gradlew build          # jar in build/libs/
./gradlew runClient      # dev client
./gradlew runData        # datagen into src/generated/resources
```

**Author:** Kragnoth

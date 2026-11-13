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

## Vanilla-style resource pack

The built-in art is kitbashed from the Excalibur resource pack. For players without Excalibur,
`tools/art/build_vanilla_pack.py` rebuilds every Excalibur-based texture from vanilla 1.21.1 textures
instead (same designs, palettes lifted for vanilla's brighter look):

```fish
python3 tools/art/build_vanilla_pack.py ~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar
```

It writes `build/cog_and_cultivar_vanilla_style.zip`; drop it in `resourcepacks/` above the mod.

## Building

```fish
./gradlew build          # jar in build/libs/
./gradlew runClient      # dev client
./gradlew runData        # datagen into src/generated/resources
```

**Author:** Kragnoth

# Cog & Cultivar — working notes for Claude

- Mod id `cultivar`, package `com.kragnoth.cultivar`, MIT (code only; Excalibur-derived art under `resourcepacks/excalibur_style` is excluded, see README).
- Targets: MC 1.21.1, NeoForge 21.1.219 (ModDevGradle 2.0.141), Create 6.0.10-281 (CI tests only this; add 6.0.11 to the matrix if it ships for 1.21.1), Ponder 1.0.82, Flywheel 1.0.6, Java 21.
- Design source of truth: `docs/design-spec.md`. Follow its roadmap phases (§6) and Create API table (§5.7).
- Clean-room rule: implement from the spec, never copy AgriCraft code.
- Art: 16x16. Default textures are kitbashed from vanilla; the Excalibur set is a built-in resource pack. Write art as a `tools/art/gen_*.py` script that takes a textures dir, then run `tools/art/build_art.py` (both styles); use the brass/blackened-steel ramps in spec §4.
- Create harvester support goes through `HarvesterMovementBehaviourMixin` (registry override is impossible in Create 6).
- Do not launch the game or a headless client unless Joe explicitly asks for a full compile and load-up.
- Joe uses fish shell on CachyOS: give shell commands in fish syntax.
- Architecture rules: `docs/ARCHITECTURE.md` (module pattern, server-safety, performance). New features = new module + one line in `CultivarModules`.
- Work on feature branches and open PRs; CI must pass (build + sidedness check, datagen staleness, dedicated-server game tests).

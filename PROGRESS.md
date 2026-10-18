Vastlands — Development Progress

Current Status

Phase: World Core complete, ready for Agent 2 (Terrain)
Project: Vastlands
Minecraft: 1.21.8
Loader: NeoForge 21.8.57

The project builds, all tests pass, and the "Vastlands" world type generates a working world from
Y=-256 to Y=511 on a dedicated server and in the client. All four generation systems are
passthrough placeholders, so the terrain is still vanilla-shaped.

Development Roadmap

- [x] Agent 1 — World Core: Create the NeoForge project foundation, configure shared generation infrastructure, and investigate expanded world height.
- [ ] Agent 2 — Terrain: Implement realistic mountains, valleys, plains, and terrain continuity.
- [ ] Agent 3 — Hydrology: Implement large rivers, tributaries, downhill drainage, and waterfalls.
- [ ] Agent 4 — Caves: Implement diverse cave systems and underground formations.
- [ ] Agent 5 — Forests: Implement large forests, giant trees, and tree variations.

Latest Completed Work (Agent 1 — World Core)

- NeoForge 21.8.57 project on ModDevGradle 2.0.148, Gradle 9.2.1 wrapper (distribution checksum
  pinned, wrapper jar matches Gradle's published checksum), Java 21, Parchment mappings, mod id
  `vastlands`, package `com.sincerelyuq.vastlands`.
- World type `vastlands:vastlands` (separate preset; vanilla types unchanged) with overworld
  dimension type and noise settings at Y -256..511 (768 blocks). Nether and End are vanilla.
- Chunk generator `vastlands:noise` (`VastlandsChunkGenerator`, a `NoiseBasedChunkGenerator`).
  Adds a Vastlands line to the F3 screen.
- Per-world `GenerationContext` bound to the world seed through `RandomStateMixin`; seeded,
  position-addressable randomness per system (`GenerationSetup.random(id)`).
- Router nodes `vastlands:terrain`, `vastlands:hydrology`, `vastlands:caves` placed in the
  overworld noise router; interfaces `TerrainSystem`, `HydrologySystem`, `CaveSystem`,
  `ForestSystem` with a documented dependency order; `Passthrough` placeholder for all of them.
- Level-load diagnostics: logs which systems are active and errors if the dimension type and
  noise settings disagree on height.
- 18 JUnit tests, including tests that run on a datapack-loaded NeoForge test server.
- `tools/worldcheck`: scripts that generate a world on the dev server and analyse its region files.

Files and Modules Affected

- Build: `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`,
  `gradle/wrapper/*`, `.gitignore`, `.gitattributes`.
- Metadata: `src/main/templates/META-INF/neoforge.mods.toml`, `src/main/resources/vastlands.mixins.json`.
- Code: `src/main/java/com/sincerelyuq/vastlands/**` (see ARCHITECTURE.md, Repository Layout).
- Data: `src/main/resources/data/vastlands/**`, `src/main/resources/data/minecraft/tags/worldgen/world_preset/normal.json`,
  `src/main/resources/assets/vastlands/lang/en_us.json`.
- Tests: `src/test/java/com/sincerelyuq/vastlands/**`.
- Tools: `tools/worldcheck/{generate,analyze,anvil,rcon}.py`.
- Docs: `ARCHITECTURE.md`, `PROGRESS.md`, `README.md`.

Build and Test Results

- `./gradlew build`: BUILD SUCCESSFUL (JDK 21.0.12, Gradle 9.2.1).
- JUnit: 18 tests, 0 failures, 0 skipped (WorldHeightDataTest 3, GenerationSetupTest 3,
  StageDensityFunctionTest 6, RandomStateBindingTest 3, PresetDataTest 3).
- The tests were checked against deliberate breakage: making `Passthrough` add 1e-9 fails 2 tests;
  changing one coefficient in the Vastlands noise settings fails `PresetDataTest`.

Verification in Game

Dedicated server (`./gradlew runServer`, `level-type=vastlands:vastlands`, seed 20261018,
16×16 chunks around 0,0, all fully generated):
- Log: `Vastlands: generating minecraft:overworld in Y -256..511 (terrain=passthrough hydrology=passthrough caves=passthrough forests=passthrough)`; no errors from the mod.
- `setblock` succeeds at Y 511, 320, 319, -64, -65, -256; fails with "out of this world" at 512 and -257.
- Region files contain sections Y -16..31 (blocks -256..511). Bedrock floor at Y -256 in every
  column, with vanilla's 5-layer gradient (-256..-252).
- Composition: -256..-65 deepslate 94.3%, ores and other 3.0%, bedrock 1.6%, lava 1.0%, air 0.0%;
  256..511 air 100%. Highest block Y 165, surface (WORLD_SURFACE) 21..165.
- Determinism: a second world with the same seed has 99.961% identical blocks, 100% identical
  biomes, and no stone/air differences (32 water/air blocks differ). Vanilla against itself with the
  same seed: 99.908% blocks, 25 water/air/lava differences. All other differences are trees and
  ore blobs on chunk borders: vanilla feature placement depends on chunk generation order.
- Vanilla comparison (same seed, Y -64..319): biomes 100% identical; ground height (ignoring
  vegetation) identical in 97.89% of columns; blocks 95.29% identical. The differences come from
  vanilla features and carvers anchored to the world bottom/top (tuff, gravel, carver caves,
  the vanilla bedrock floor at -64). `PresetDataTest` confirms the router itself matches vanilla exactly.
- A vanilla-preset server run with the mod installed: build limits -64..319, no Vastlands context.

Client (`./gradlew runClient` under Xvfb with Mesa llvmpipe software rendering):
- The create-world screen offers "World Type: Vastlands" after the vanilla types.
- Creating a Vastlands world shows vanilla's "experimental settings" warning; after "Yes" the world
  generates and the F3 screen shows `Vastlands: terrain=passthrough hydrology=…`.
- A world generated on the dedicated server also loads and renders in singleplayer.

Not verified:
- Real GPU rendering, multiplayer with a separate client, generation performance (no measurements).
- Anything about terrain quality: there is no Vastlands terrain yet.

Known Issues

- Caves cut by carvers between Y -248 and -55 are filled with lava (vanilla's fluid picker hardcodes
  lava below -54; private static in `NoiseBasedChunkGenerator`). Measured: per 16-block slice
  below -64, 1 400–34 000 lava blocks and at most 2 600 air. Owner: Caves.
- Vanilla features/carvers anchored with `above_bottom`/`below_top` moved down with the world
  bottom: diamonds now at -256..-176 (vanilla -64..16), lower redstone, tuff, geodes, deep monster
  rooms, lower fossils, gravel, lava springs, cave carvers. Owner: Caves.
- Land only uses -64..~256: the vanilla slides (-64..-40, 240..256) and depth gradient (-64..320)
  are absolute Y values. Owner: Terrain.
- Vanilla's "experimental settings" warning appears when creating a Vastlands world.
- The Vastlands overworld shares vanilla biomes and features; changing them affects vanilla worlds
  unless scoped to Vastlands (see ARCHITECTURE.md, Vanilla Isolation).
- License is "All Rights Reserved" (MDK default). Change `mod_license` in `gradle.properties` if needed.

Architectural Decisions

- Separate world preset instead of modifying `minecraft:overworld`: vanilla worlds stay vanilla.
- Data-driven router with Java hooks: the noise settings JSON stays the integration point; each
  system is a named node (`vastlands:terrain` etc.), so systems can be data, Java, or both.
- Seed access through one small mixin on `RandomState` instead of hashing noises or global state.
  The context is per `RandomState`, created only when the router contains a Vastlands node.
- A Vastlands chunk generator type from the first release, so non-density steps (for example river
  water above sea level) can be added later without changing existing worlds' generator type.
- One place (`GenerationSystems.create`) selects implementations; one-way dependencies
  terrain → hydrology → caves, forests.
- Minimal interfaces: each agent extends its own interface instead of Agent 1 guessing APIs.

Instructions for Agent 2 (Terrain)

1. Read ARCHITECTURE.md ("Generation Pipeline", "System Contracts", "Technical Limitations").
2. Implement `TerrainSystem` in a new package (e.g. `worldgen/terrain`) and construct it in
   `GenerationSystems.create`. Use `setup.random("terrain")` or vanilla noises; keep it thread-safe
   and order-independent.
3. Make terrain use the full height: replace or move the vanilla slides in
   `data/vastlands/worldgen/noise_settings/overworld.json` (bottom -64..-40, top 240..256) and the
   depth gradient (-64..320), or replace the graph under `vastlands:overworld/terrain` entirely.
   Keep a solid floor near -256 and nothing floating near 511.
4. Keep `initial_density_without_jaggedness` (aquifers) and `depth` (biomes) consistent with the new heights.
5. Add a cheap, deterministic 2D height query to `TerrainSystem` for Hydrology, and document it.
6. Return a conservative `outputRange`.
7. Verify with `./gradlew build`, then `python3 tools/worldcheck/generate.py --level-name t1 --fresh --accept-eula`
   and `python3 tools/worldcheck/analyze.py run/t1`; check surface heights, highest block,
   floating blocks, and determinism against a second run with the same seed.
8. Do not fix the deep lava or ore distribution issues; they belong to Caves.

Handoff Instructions

Before starting work, each new agent must:

1. Read ARCHITECTURE.md.
2. Read this file.
3. Inspect the existing project structure and relevant code.
4. Identify the current development phase.
5. Continue from the latest verified implementation.

Before finishing, each agent must update this file with:

- Completed changes
- Files or modules affected
- Build and test results
- Known issues and limitations
- Architectural decisions
- Recommendations for the next agent

Do not mark a feature complete unless it has actually been implemented and verified.

Next Task

Agent 2: Implement the Terrain system: realistic mountain ranges, valleys, plains, plateaus and cliffs
across the full Y=-256..511 range, through `TerrainSystem` and the `vastlands:overworld` noise settings.

Vastlands — Architecture

Project Overview

Vastlands is a realistic world-generation mod for Minecraft Java Edition 1.21.8, using NeoForge.

The goal is to create large-scale, geographically believable landscapes while preserving Minecraft's block-based visual identity.

Core Requirements

- Minecraft version: 1.21.8
- Mod loader: NeoForge (21.8.57)
- Java version: 21
- Target world height: Y=-256 to Y=511 (768 blocks total)
- No unnatural floating islands or disconnected terrain
- Performance-conscious, deterministic world generation
- Modular architecture with clearly separated generation systems

Development Modules

1. World Core — project setup, world height configuration, generation infrastructure, shared interfaces, and integration. (Done: Agent 1.)
2. Terrain — mountain ranges, valleys, plains, plateaus, cliffs, and natural terrain continuity.
3. Hydrology — rivers, tributaries, downhill drainage, waterfalls, and river valleys.
4. Caves — large underground caverns, tunnels, vertical shafts, and diverse cave formations.
5. Forests — large forests, tree diversity, giant trees, and natural vegetation distribution.

Integration Rules

- All generation systems must be deterministic for a given world seed.
- Neighboring chunks must connect without visible seams.
- Modules must use shared generation data where necessary.
- Avoid unnecessary chunk loading during world generation.
- Do not assume that world-height changes are trivial.
- Preserve compatibility with NeoForge 1.21.8.
- Prioritize actual working implementations over visual demonstrations or placeholders.
- Never change vanilla world presets: Vastlands is a separate world type (see "Vanilla isolation").

Development Workflow

Development is performed through separate, sequential Claude Cloud sessions.

Each agent must:

1. Read ARCHITECTURE.md and PROGRESS.md.
2. Inspect the existing code before making changes.
3. Implement only its assigned responsibilities.
4. Run available build checks and tests.
5. Update PROGRESS.md with completed work, remaining issues, and integration notes.
6. Create a pull request without automatically merging into main.

This document may be updated when architectural decisions are verified through implementation.

---

Build and Run

- Toolchain: Gradle 9.2.1 (wrapper, distribution checksum pinned), ModDevGradle 2.0.148,
  NeoForge 21.8.57, Parchment 2025.09.14 mappings on top of official Mojang names, Java 21.
- `./gradlew build` — compiles, runs all JUnit tests, builds `build/libs/vastlands-<version>.jar`.
- `./gradlew test` — tests only. Tests run inside a loaded game (registries bootstrapped, mixins applied).
- `./gradlew runClient` / `./gradlew runServer` — dev runs in `run/` (git-ignored).
  For the server, set `level-type=vastlands\:vastlands` in `run/server.properties`.
- Versions live in `gradle.properties`; mod metadata is generated from `src/main/templates/META-INF/neoforge.mods.toml`.
- The Minecraft sources with NeoForge patches are at `build/moddev/artifacts/neoforge-21.8.57-sources.jar`
  after the first build. Check APIs there, not from memory: 1.21.x changed a lot.

Repository Layout

```
src/main/java/com/sincerelyuq/vastlands/
  Vastlands.java                      @Mod entry point, MOD_ID, LOGGER, id()
  registry/VastlandsRegistries.java   DeferredRegisters: chunk generator, density function types
  mixin/RandomStateMixin.java         binds Vastlands density functions to the world seed
  worldgen/
    WorldHeight.java                  MIN_Y = -256, HEIGHT = 768, MAX_Y = 511
    VastlandsChunkGenerator.java      "vastlands:noise", a NoiseBasedChunkGenerator subclass
    WorldgenDiagnostics.java          level-load checks (height mismatch, missing context)
    context/                          GenerationContext (per-world state), binding visitor, mixin duck interface
    density/StageDensityFunction.java vastlands:terrain / vastlands:hydrology / vastlands:caves router nodes
    system/                           system interfaces, Passthrough placeholder, GenerationSystems wiring
src/main/resources/
  vastlands.mixins.json
  assets/vastlands/lang/en_us.json                         world type name
  data/minecraft/tags/worldgen/world_preset/normal.json    lists the preset in the create-world screen
  data/vastlands/dimension_type/overworld.json             build limits -256..511
  data/vastlands/worldgen/world_preset/vastlands.json      the "Vastlands" world type
  data/vastlands/worldgen/noise_settings/overworld.json    noise range -256..511 + Vastlands stages
  data/vastlands/worldgen/density_function/overworld/      terrain.json, hydrology.json, landscape.json
src/test/java/...                                          JUnit tests (see Testing)
tools/worldcheck/                                          dedicated-server world generation + region analysis
```

World Preset and Height

The Vastlands world type is `vastlands:vastlands`. It is a copy of vanilla's `minecraft:normal`
preset with only the overworld replaced; the Nether and the End are vanilla.

- Overworld dimension type `vastlands:overworld`: vanilla overworld values with
  `min_y = -256`, `height = 768`, `logical_height = 768`.
- Generator `vastlands:noise` with `biome_source = minecraft:multi_noise / preset minecraft:overworld`
  and `settings = vastlands:overworld`.
- Noise settings `vastlands:overworld` were derived from vanilla 1.21.8 `minecraft:overworld`
  with exactly three changes: `noise.min_y = -256`, `noise.height = 768`, and the Vastlands stages in
  `final_density` (next section). Surface rules, aquifers, ore veins, spawn target, sea level 63 and
  all other router outputs are vanilla values.
- Vanilla constraints (DimensionType / NoiseSettings codecs): `min_y` and `height` must be multiples
  of 16, `min_y + height <= 2032`, `logical_height <= height`. The dimension type and the noise
  settings must describe the same range, or terrain is silently clipped; `WorldgenDiagnostics` logs
  an error at level load if they differ, and `WorldHeightDataTest` fails the build if either file
  drifts from `WorldHeight`.
- The tag `#minecraft:normal` (world_preset) adds the type to the create-world screen
  ("World Type: Vastlands"). Dedicated servers use `level-type=vastlands:vastlands`.

What the height extension does and does not do yet: the world builds and stores blocks from -256
to 511, bedrock is at -256, and the noise is evaluated over the full range. The vanilla density
functions still contain absolute Y values (bottom slide -64..-40, top slide 240..256, depth
gradient -64..320), so with passthrough systems land only forms between -64 and about 256:
everything below -64 is solid deepslate (only carvers cut it) and everything above 256 is air.
Using the full height is the Terrain agent's job.

Generation Pipeline

```
minecraft:overworld/sloped_cheese          vanilla base terrain density
        │
vastlands:terrain      ← TerrainSystem     file: density_function/overworld/terrain.json
        │
vastlands:hydrology    ← HydrologySystem   file: density_function/overworld/hydrology.json
        │
minecraft:cache_once                       file: density_function/overworld/landscape.json
        │   replaces all three sloped_cheese references inside vanilla's final_density
vanilla final_density body                 slides, blend, interpolation, noise caves, noodle caves
        │
vastlands:caves        ← CaveSystem        wraps the whole final_density (per block, not interpolated)
        │
NoiseChunk → aquifers → surface rules → carvers → features
                                                    └─ ForestSystem, via GenerationContext.of(level)
```

- Terrain and hydrology stages sit inside `minecraft:interpolated`, so they are evaluated at noise
  cell corners only (every 4×8×4 blocks) and interpolated in between. `cache_once` makes the three
  uses of the landscape share one evaluation per point.
- The caves stage is outside interpolation and runs for every block.
- Forests are not part of the router; forest features reach their system through
  `GenerationContext.of(ServerLevel)`.
- The JSON graph is data and may be restructured by later agents (for example to move the caves
  hook inside interpolation), as long as each stage keeps one clear place in the router.

Seed Binding and GenerationContext

Vanilla gives the world seed to density functions only through hardcoded `instanceof` checks in
`RandomState`'s wiring visitor. `RandomStateMixin` wraps the call
`settings.noiseRouter().mapAll(wiringVisitor)` in `RandomState`'s constructor with a
`ContextBindingVisitor`. When a `StageDensityFunction` is visited, it asks the visitor for the
world's `GenerationContext` (created lazily, once) and binds itself to the matching system.

- One `GenerationContext` per `RandomState`. In the game only `ChunkMap` creates a `RandomState`:
  one per dimension per server start. Dimensions without Vastlands stages (vanilla presets, the
  Nether, the End) get no context and no overhead.
- `GenerationContext.of(RandomState)` / `GenerationContext.of(ServerLevel)` return it as an Optional.
- `GenerationSetup` holds the world seed, noise min Y, height and sea level.
  `setup.random("<system id>")` returns a `PositionalRandomFactory` derived from the seed and the
  id; use `.at(x, y, z)` so results depend only on seed and position.
- An unbound stage (outside a `RandomState`) returns its input, like unwired vanilla noises return 0.
- `VastlandsChunkGenerator` must stay a `NoiseBasedChunkGenerator`: `ChunkMap` only builds the
  `RandomState` from the generator's noise settings for that class.

System Contracts

All interfaces are in `worldgen/system`. `GenerationSystems.create(GenerationSetup)` is the single
place where implementations are chosen. Today every system is `Passthrough.INSTANCE`.

| System | Interface | Kind | May depend on | Owner |
|---|---|---|---|---|
| Terrain | `TerrainSystem` | `DensityStage` | — | Agent 2 |
| Hydrology | `HydrologySystem` | `DensityStage` | Terrain | Agent 3 |
| Caves | `CaveSystem` | `DensityStage` | Terrain, Hydrology | Agent 4 |
| Forests | `ForestSystem` | `treeDensity(x, z)` | Terrain, Hydrology | Agent 5 |

Systems are created in that order and receive only earlier systems. Nothing depends on caves or forests.

Rules for every system:
- Thread-safe: worldgen threads call one shared instance concurrently.
- Deterministic and order-independent: results depend only on the seed and the queried position.
  Never keep a shared mutable `RandomSource`; use `setup.random(id).at(...)`. Any cache must be
  keyed by position and safe under concurrent access, and must not change results.
- `DensityStage.apply(x, y, z, density)`: positive density is solid.
- `DensityStage.outputRange(input)` must be conservative (never narrower than the real output).
  Vanilla skips work based on these bounds; a range that is too narrow silently corrupts terrain.
- Cheap construction: systems are created while `RandomState` is being built; do not touch
  `randomState.router()` or load chunks there.
- The interfaces are minimal by design. The owning agent extends its own interface (for example
  Terrain adds the 2D height query that Hydrology needs) and documents the contract here.

How to implement a system

1. Create a package, e.g. `worldgen/terrain`, with a class implementing the interface.
2. Construct it in `GenerationSystems.create`, passing the setup and upstream systems.
3. Return a meaningful `id()`; it appears in the server log and on the F3 screen.
4. If the router needs to change, edit the JSON under `data/vastlands/worldgen`.
5. Add JUnit tests; `PresetDataTest.passthroughRouterReproducesVanillaDensity` skips itself
   automatically once a density stage is no longer passthrough.
6. Verify in a real world with `tools/worldcheck` (below), not only with unit tests.

Vanilla Isolation

- Vanilla world types are untouched: only `vastlands:*` data is added, plus one entry appended to
  the `#minecraft:normal` world preset tag. A vanilla-preset server run showed the vanilla build
  limits (-64..319) and no Vastlands context.
- Shared vanilla content: the Vastlands overworld uses vanilla biomes, and vanilla biomes reference
  vanilla placed features and carvers. Changing a biome or a `minecraft:` feature (override JSON or
  biome modifier) changes vanilla worlds too. Later agents must scope such changes to Vastlands
  worlds, e.g. with a placement filter that checks for `VastlandsChunkGenerator`
  (`PlacementContext.generator()`), or by using Vastlands-specific biomes.

Testing and Verification

JUnit (`./gradlew test`, 18 tests):
- `WorldHeightDataTest` — data files agree with `WorldHeight` and pass vanilla's codecs.
- `GenerationSetupTest` — seeded randomness is deterministic and separated per system and seed.
- `StageDensityFunctionTest` — passthrough when unbound, delegation and bounds when bound,
  `fillArray`, lazy context creation, binding survives later visitors, codec round trip.
- `RandomStateBindingTest` — the mixin binds stages in a real `RandomState`; vanilla settings get no
  context; a passthrough stage keeps vanilla density exactly.
- `PresetDataTest` — runs on NeoForge's `EphemeralTestServerProvider`, so it reads the data exactly as
  the game loads it: the preset is in `#minecraft:normal`, its overworld uses `vastlands:noise` and
  the -256..511 dimension type, and the Vastlands router reproduces vanilla's router outputs
  bit-for-bit at Y -256..511 while all stages are passthrough.

Real world checks (`tools/worldcheck`, standard-library Python):
- `generate.py --level-name NAME [--level-type ID] [--seed N] --fresh --accept-eula` writes
  `run/server.properties`, starts `./gradlew runServer`, force-loads 16×16 chunks around 0,0, waits
  until they are fully generated and saved, probes the build limits with `setblock` through RCON,
  and stops the server. Running a server means accepting the Minecraft EULA; the flag writes `run/eula.txt`.
- `analyze.py run/NAME [run/OTHER]` reads the region files: section range, bedrock floor, block
  composition per height band, surface heights, and (with two worlds) identical blocks, biomes and
  surface heights in the shared range.
- Vanilla worldgen itself is not bit-for-bit reproducible between runs: features that spill across
  chunk borders (trees, ore blobs) depend on generation order. Compare Vastlands-vs-Vastlands
  differences against a vanilla-vs-vanilla baseline with the same seed.

Technical Limitations (verified)

- Lava below Y=-54: vanilla's global fluid picker (`NoiseBasedChunkGenerator.createFluidPicker`,
  private static) hardcodes lava at Y < -54. In the extended range almost every cave cut by
  carvers between -248 and -55 is filled with lava. Owner: Caves.
- Features and carvers anchored to the world bottom or top (`above_bottom` / `below_top`) move with
  the new limits: diamonds (vanilla -64..16) now generate at -256..-176, lower redstone at
  -256..-224, tuff, geodes, deep monster rooms, lower fossils, gravel, lava springs and the cave
  carvers (min Y -248 instead of -56) shift too. Owner: Caves (underground), within the isolation rule above.
- Land only uses -64..~256 until Terrain replaces the vanilla slides and depth gradient.
- `initial_density_without_jaggedness` (aquifers' preliminary surface) and the `depth` router output
  (biome selection: cave biomes, peaks) are vanilla. If terrain heights change, they must change consistently.
- Creating a Vastlands world shows vanilla's "experimental settings" warning: vanilla registers
  worldgen data from non-vanilla packs with an experimental lifecycle. NeoForge stops it from
  repeating on later loads once confirmed. Not changed.
- Cloud height is vanilla (192); mountains above it will cut through clouds.

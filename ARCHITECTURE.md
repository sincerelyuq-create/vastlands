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
2. Terrain — mountain ranges, valleys, plains, plateaus, cliffs, and natural terrain continuity. (Done: Agent 2.)
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
  mixin/SurfaceRulesContextMixin.java surface rules use the exact terrain surface (Vastlands worlds only)
  worldgen/
    WorldHeight.java                  MIN_Y = -256, HEIGHT = 768, MAX_Y = 511
    VastlandsChunkGenerator.java      "vastlands:noise", a NoiseBasedChunkGenerator subclass
    WorldgenDiagnostics.java          level-load checks (height mismatch, missing context)
    context/                          GenerationContext (per-world state), binding visitor, mixin duck interface
    density/StageDensityFunction.java vastlands:terrain / vastlands:hydrology / vastlands:caves router nodes
    density/TerrainParameterFunction.java  vastlands:terrain_parameter router nodes (climate, depth, initial density)
    system/                           system interfaces, Passthrough placeholder, GenerationSystems wiring
    terrain/                          the terrain: TerrainModel (height field, pure Java), VastlandsTerrain
                                      (TerrainSystem), TerrainColumn, SimplexNoise, ColumnCache
src/main/resources/
  vastlands.mixins.json
  assets/vastlands/lang/en_us.json                         world type name
  data/minecraft/tags/worldgen/world_preset/normal.json    lists the preset in the create-world screen
  data/vastlands/dimension_type/overworld.json             build limits -256..511
  data/vastlands/worldgen/world_preset/vastlands.json      the "Vastlands" world type
  data/vastlands/worldgen/noise_settings/overworld.json    noise range -256..511 + Vastlands stages
  data/vastlands/worldgen/density_function/overworld/      terrain, hydrology, landscape (stages); continents,
                                                           erosion, ridges, depth, initial_density (terrain outputs)
src/test/java/...                                          JUnit tests (see Testing)
src/test/java/.../tools/TerrainPreview.java                ./gradlew terrainPreview: terrain maps without the game
tools/worldcheck/                                          world generation, region analysis, heightmaps, screenshots
docs/terrain/                                              terrain previews, region heightmap and in-game screenshots
```

World Preset and Height

The Vastlands world type is `vastlands:vastlands`. It is a copy of vanilla's `minecraft:normal`
preset with only the overworld replaced; the Nether and the End are vanilla.

- Overworld dimension type `vastlands:overworld`: vanilla overworld values with
  `min_y = -256`, `height = 768`, `logical_height = 768`.
- Generator `vastlands:noise` with `biome_source = minecraft:multi_noise / preset minecraft:overworld`
  and `settings = vastlands:overworld`.
- Noise settings `vastlands:overworld` were derived from vanilla 1.21.8 `minecraft:overworld`.
  Changes: `noise.min_y = -256`, `noise.height = 768`; the Vastlands stages in `final_density`
  (next section); the top slide moved from 240..256 to 488..504; the router outputs `continents`,
  `erosion`, `ridges`, `depth` and `initial_density_without_jaggedness` read the terrain
  (`vastlands:overworld/<name>`, see Terrain). Surface rules, aquifer noises, ore veins, spawn target,
  sea level 63, temperature, vegetation and the bottom slide (-64..-40) are vanilla values.
- Vanilla constraints (DimensionType / NoiseSettings codecs): `min_y` and `height` must be multiples
  of 16, `min_y + height <= 2032`, `logical_height <= height`. The dimension type and the noise
  settings must describe the same range, or terrain is silently clipped; `WorldgenDiagnostics` logs
  an error at level load if they differ, and `WorldHeightDataTest` fails the build if either file
  drifts from `WorldHeight`.
- The tag `#minecraft:normal` (world_preset) adds the type to the create-world screen
  ("World Type: Vastlands"). Dedicated servers use `level-type=vastlands:vastlands`.

How the height is used: the world builds and stores blocks from -256 to 511, bedrock is at -256.
The terrain surface spans about Y -20 (deepest sea floor) to 470 (asymptote of the highest peaks;
the top slide at 488..504 is only a safety net). Below -64 the rock is still solid deepslate cut
only by carvers: the bottom slide stays at vanilla's -64..-40 because opening the deep range lets
vanilla's fluid picker fill every cave below -54 with lava (measured: lava rose from 1.0% to 5.8%
of the -256..-65 band when the slide was moved down). Moving it is the Caves agent's job, together
with the lava fix.

Generation Pipeline

```
0.0 (constant input)                       vanilla's sloped_cheese is no longer evaluated
        │
vastlands:terrain      ← TerrainSystem     file: density_function/overworld/terrain.json
        │                                  adds (surface - y) * 0.125
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
- Router outputs owned by the terrain (`vastlands:terrain_parameter` nodes, files
  `density_function/overworld/{continents,erosion,ridges,depth,initial_density}.json`): the climate
  parameters that select biomes, the biome depth, and the initial density that aquifers and the
  preliminary surface use. The three 2D ones are wrapped in `minecraft:flat_cache` like vanilla's.
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
place where implementations are chosen. Terrain is `VastlandsTerrain`; hydrology, caves and forests
are still `Passthrough.INSTANCE` (which no longer implements `TerrainSystem`).

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

TerrainSystem (contract for later systems; details in Terrain below):
- `TerrainColumn column(x, z)`: exact model values of a block column — `height` (surface),
  `baseHeight` (regional ground level without hills, mountains and plateaus; what valleys cut down
  to), `continentalness` (raw continent field, 0 = coastline), `mountain` (0..1 belt strength),
  `plateau` (0..1), `gullyDepth` (blocks removed by gully erosion: the drainage lines), `ruggedness`,
  `slopeX`/`slopeZ` (gradient of the uneroded terrain smoothed over ~64 blocks; downhill is
  `-slope`) and the three climate parameters. Cached per thread; cheap for nearby repeated queries.
- `double surfaceHeight(x, z)`: the surface as generated (the 4-block grid of `column` heights,
  bilinearly interpolated like the density). Top landscape block = `ceil(surfaceHeight) - 1`.
- `double parameter(TerrainParameter, x, y, z)`: router outputs (see `TerrainParameter` for ranges).
- `GenerationSystems.surfaceHeight(x, z)`: the ground surface after all stages that reshape it, used
  by `SurfaceRulesContextMixin`. Terrain only today; Hydrology must include its channels here.

How to implement a system

1. Create a package, e.g. `worldgen/terrain`, with a class implementing the interface.
2. Construct it in `GenerationSystems.create`, passing the setup and upstream systems.
3. Return a meaningful `id()`; it appears in the server log and on the F3 screen.
4. If the router needs to change, edit the JSON under `data/vastlands/worldgen`.
5. Add JUnit tests. `PresetDataTest.routerKeepsVanillaOutputsTheTerrainDoesNotOwn` checks that
   router outputs nobody owns stay vanilla; adjust its list if a system takes one over.
6. Verify in a real world with `tools/worldcheck` (below), not only with unit tests.

Terrain (Agent 2)

The terrain is a 2D height field turned into density: `density = input + (surface - y) * 0.125`,
clamped to ±128. Every column has exactly one surface, so there is no floating terrain (vanilla's
noise caves still hollow out the rock below). Biomes, aquifers and surface rules are fed from the
same height field, so they agree with the landscape. Code: `worldgen/terrain`. `TerrainModel` is
pure Java (no game classes): it can be rendered with `./gradlew terrainPreview` (see Testing).

Algorithm (`TerrainModel.sample(x, z)`), three layers:
1. Macro shape (`macro`):
   - Continents: 4 octaves of simplex fBm at a 11 000-block scale, domain-warped by 1 800 blocks,
     plus 3 unwarped fine octaves (690 blocks) for coastline detail. Value `c` = 0 is the coastline;
     land covers ~74% of the world. A monotone spline maps `c` to the base height: abyss -30..-5,
     deep sea floor ~14, shelf 48..61, coast 61..64.5, lowlands 70, interior 82..110.
   - Interior: broad swells (±16, 3 500 blocks) and basins (down to -22, 5 000 blocks).
   - Rolling hills: a hilliness mask (3 000 blocks) times rounded fBm bumps up to ~34 blocks (600).
   - Mountain belts: bands around the zero-lines of a warped noise (10 000 blocks; warp 2 200), so
     ranges are long, curving and connected; a second noise switches segments on and off. Across
     the belt a crest profile rises from a gentle foot to a rounded axis (foothills → main crest).
     Height of the crest envelope = 380 × stature (0.35..1, 16 000-block noise: most ranges are
     moderate, a few are major) × crest line (0.3..1 along the range, 2 200 blocks: massifs and
     passes) × peaks (0.6..1.2, 800 blocks: individual summits). Young ("alpine") ranges get extra
     ridged aretes near their crests and stronger erosion; old ranges stay rounded (9 000-block noise).
   - Plateaus: a 9 000-block mask with a rough edge term (bays, spurs, buttes), raising the land by
     40..90 blocks behind escarpments; only well inland and away from mountain belts.
2. Gully erosion (`gullies`): a slope-aligned stripe filter (after Clay John's "eroded terrain
   noise") at cells of 360, 180, 90 and 45 blocks, depth 22% of the cell at full strength. The stripe
   direction follows the gradient of the smoothed macro shape (three samples 32 blocks around the
   column); later octaves bend towards earlier gullies, which makes them branch (dendritic spurs and
   side valleys). Strength rises with slope and relief, is lower on old ranges, and the filter never
   cuts below the regional base or the sea. Its kernel is compact (radius 1.5 cells) and matches the
   4×4 cell window, so it is continuous.
3. Detail (`detail`): plains undulation (±5, 380 blocks), micro relief (±2.5, 140), sea floor
   texture, and crags and ledges on steep mountain and escarpment ground (ridged noise, 70 blocks,
   ±5..12 blocks, sharper on young ranges). Heights above 410 are compressed towards an asymptote at
   470 (`softCap`), so only the highest summits are flattened a little.

Elevation ranges and their frequency (4 seeds × 20 000 random columns over ±200 000 blocks, see
`TerrainModelTest`): sea floor -20..62 on 26% of the world (1.5% below Y 0), lowlands 63..130 on
59%, hills, plateaus and foothills 130..200 on 12%, mountains above 200 on 2.7%, above 300 on 0.5%,
above 400 on 0.1%; highest peaks ~450. Neighbouring columns differ by at most ~10 blocks (canyon
walls, crest cliffs), 0.16 on average, and the height field has no discontinuities.

Climate parameters (biomes come from vanilla's overworld table, `OverworldBiomeBuilder`):
- continentalness: ocean values (< -0.19, deep ocean below -0.455 ≈ 20 blocks deep) exactly where
  the ground is under sea level; the coast band (-0.19..-0.11: beaches, stony shores) on the
  shoreline; inland values rising with `c` (near, mid, far inland).
- erosion: `0.45 - 1.4 × ruggedness` (ruggedness from mountain height, hilliness and slope):
  E0/E1 on mountains → peaks, slopes (jagged/frozen/stony peaks, snowy slopes, grove, meadow,
  badlands in hot climates); E2 on plateaus → plateau biomes; E3/E4 on hills and plains. E5 marks
  some steep hills (windswept), E6 some flat wet lowland at sea level (swamps).
- weirdness: from the height above the regional base relative to the local mountain height
  (crests → peaks slice, slopes → high, plains → low/mid). |w| ≥ 0.06 always: no river biomes until
  Hydrology makes rivers. The sign (variant biomes) comes from its own 1 800-block noise.
- temperature and vegetation (humidity) stay vanilla noises.
- depth: `(surface - y) / 128` like vanilla, so cave biomes sit at vanilla distances below the
  surface. Deeper than 0.95 it is held at 0.95 except in vanilla's deep dark band Y -64..0, where
  the deep dark (and ancient cities) appear under low-erosion terrain as in vanilla. Without the
  hold the deep dark filled all rock more than ~140 blocks under the surface (up to 400 blocks
  thick under mountains) and its sculk features took ~40% of chunk generation time.
Measured on a datapack-loaded test server (`TerrainBiomeTest`, 90 000 surface columns): every
underwater column has an ocean biome, no dry column has one, all columns above Y 300 have
mountain/highland biomes, no river biomes.

Surface rules and aquifers:
- Vanilla applies surface rules (grass, sand, snow, dirt layers) only above a minimum surface
  level interpolated from preliminary heights at 16-block cell corners. Gully valleys are narrower
  than that, so their floors would stay bare stone. `SurfaceRulesContextMixin` replaces the
  interpolated value with `floor(GenerationSystems.surfaceHeight) - 7` in Vastlands worlds (vanilla's
  preliminary surface is 3..11 blocks below the real one, so layer depths stay vanilla-like).
- `initial_density_without_jaggedness` uses the lowest of the 4 grid columns around a point, so the
  preliminary surface never lies above the real surface: aquifers treat open air above ground as
  air, and depressions below sea level fill with water at sea level, as in vanilla.

Determinism, threads, caching: the model is an immutable pure function of (seed, x, z); the seed
is `setup.random("terrain").fromHashOf("height_field").nextLong()`. Noises hash lattice points
(no permutation tables, no repetition). `VastlandsTerrain` keeps a 4 096-entry direct-mapped column
cache per thread (~0.5 MB); a hit returns exactly what the model computes. Climate parameters are
read on the 4-block grid, so biome sampling, density interpolation, aquifers and surface rules share
cached columns.

Performance (measured in this repository's cloud container: 4 vCPU Intel Xeon, 15 GB RAM, Java 21,
dev dedicated server):
- The model costs 10..30 µs per column per thread (benchmarks and `terrainPreview`; it varies with
  the terrain: mountains and plateaus evaluate more layers). A chunk needs about 16..25 grid
  columns, all cached per thread: well under 1 ms per chunk.
- JFR profile of chunk generation in a mountain area (60 s): Vastlands code is 2.4% of the CPU
  samples. The rest is vanilla: surface rules over the deep columns (`SurfaceSystem.buildSurface`,
  36%), features, noise filling (13%), aquifers (4.5%). Before the deep dark band, sculk features
  alone took ~40%.
- End to end (`generate.py`, spawn-ticket generation of 32×32 chunks including features and light):
  2.8 chunks/s for the Vastlands terrain in a mountain area (seed 20261018 around 6750,-1250, mean
  ground Y 261) against 3.1 chunks/s for the passthrough baseline (`main`, same seed and square,
  vanilla-shaped ground at mean Y 70, partly sea). At 0,0 with seed 1 (16×16 chunks): 2.2 chunks/s
  (hills, mean Y 125) against 1.8 (baseline: sea). Identical repeated runs differed by up to 25%
  (363 s and 473 s), so the terrain does not measurably change generation speed. Both are limited
  by 1.21.8's single consecutive "worldgen" executor, which runs surface rules, carvers and features
  one chunk at a time; only noise filling and biome filling use the worker pool.
- Memory: a 4 096-entry column cache (~0.5 MB) per worldgen thread; the model holds only seeds.

Tuning: all constants are in `TerrainModel` (scales in blocks, heights in blocks). Render a seed
with `./gradlew terrainPreview --args="--seed S --center X Z --size 16384 --scale 16 --out build/p"`
(relief map + perspective view + height distribution) before generating worlds. `TerrainModelTest`
guards the distribution (sea share, mountain share, max height, max neighbour step).

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

JUnit (`./gradlew test`, 39 tests):
- `WorldHeightDataTest` — data files agree with `WorldHeight` and pass vanilla's codecs.
- `GenerationSetupTest` — seeded randomness is deterministic and separated per system and seed;
  terrain is implemented, the other systems are passthrough.
- `StageDensityFunctionTest` — passthrough when unbound, delegation and bounds when bound,
  `fillArray`, lazy context creation, binding survives later visitors, codec round trip.
- `TerrainParameterFunctionTest` — unbound value and bounds, binding to the context's terrain, codec.
- `RandomStateBindingTest` — the mixin binds stages in a real `RandomState`; vanilla settings get no
  context; a passthrough stage keeps vanilla density exactly.
- `TerrainModelTest` — same seed same terrain, seeds differ, thread-safe and order-independent,
  continuity (max neighbour step < 12 blocks, mean < 0.4 over 4 seeds), no discontinuities (no
  jump within 0.05 blocks), height distribution
  (sea 15..40%, lowland > 45%, above 200 0.5..8%, above 300 < 1.5% and rarer than above 200,
  max < 470), climate parameters in range, underwater ⇔ ocean continentalness, no river weirdness.
- `VastlandsTerrainTest` — one surface per column, cache equals the model from any thread,
  `surfaceHeight` interpolates the grid, outputs inside their declared ranges, initial density
  below the surface, depth semantics (deep dark band), low worlds cap the terrain below their top.
- `PresetDataTest` (on NeoForge's `EphemeralTestServerProvider`, data loaded exactly as the game
  does) — preset listed, generator and height; router outputs the terrain does not own are
  bit-for-bit vanilla over Y -256..511; terrain-owned outputs equal the terrain; final density solid
  at the bottom, air at the top and above the terrain surface.
- `TerrainBiomeTest` (test server) — surface biomes from vanilla's table agree with the terrain
  (ocean ⇔ under water, mountain biomes above Y 300, no river biomes).

Terrain previews without the game: `./gradlew terrainPreview --args="..."` (`TerrainPreview` in the
test sources): `--seed S --center X Z --size BLOCKS --scale BLOCKS_PER_PIXEL --out PREFIX` writes a
hillshaded relief map and a perspective view and prints the height distribution and the sampling
cost; `--camera X Z YAW ALTITUDE` places the view; `--heights FILE` exports the expected surface
heights of the square (float32) for `heightmap.py --expected`.

Real world checks (`tools/worldcheck`, standard-library Python; screenshots need python-xlib):
- `generate.py --level-name NAME [--level-type ID] [--seed N] [--center X Z] [--radius R] --fresh
  --accept-eula` writes `run/server.properties`, starts `./gradlew runServer`, moves the world spawn
  to the square's centre with `gamerule spawnChunkRadius R` (a ticket: parallel generation like a
  player's view; `/forceload` generates one chunk at a time on the server thread and is not used),
  waits until the 2R × 2R chunks are fully generated and saved (`generation_seconds`,
  `chunks_per_second`), probes the build limits with `setblock`, and stops the server.
  Running a server means accepting the Minecraft EULA; the flag writes `run/eula.txt`.
- `analyze.py run/NAME [run/OTHER]` reads the region files: section range, bedrock floor, block
  composition per height band, surface heights, and (with two worlds) identical blocks, biomes and
  surface heights in the shared range.
- `heightmap.py run/NAME [--png FILE] [--expected FILE] [--compare run/OTHER]` builds the ground
  heightmap from the saved chunk heightmaps: relief PNG, neighbour steps inside chunks vs across
  chunk borders (seams), comparison with the model's expected surface, ground identity of two worlds.
- `screenshot.py run/NAME --out DIR --shot "name:X Y Z YAW PITCH" ...` copies the world to
  `run/saves`, enables commands, starts the dev client under Xvfb with `--quickPlaySingleplayer`,
  and for each shot teleports (spectator), waits for chunks to render and saves an F2 screenshot.
- Vanilla worldgen itself is not bit-for-bit reproducible between runs: features that spill across
  chunk borders (trees, ore blobs) depend on generation order. Compare Vastlands-vs-Vastlands
  differences against a vanilla-vs-vanilla baseline with the same seed.

Technical Limitations (verified)

- Lava below Y=-54: vanilla's global fluid picker (`NoiseBasedChunkGenerator.createFluidPicker`,
  private static) hardcodes lava at Y < -54. In the extended range almost every cave cut by
  carvers between -248 and -55 is filled with lava. This is also why the bottom slide stays at
  -64..-40 (see World Preset and Height). Owner: Caves.
- Features and carvers anchored to the world bottom or top (`above_bottom` / `below_top`) move with
  the new limits: diamonds (vanilla -64..16) now generate at -256..-176, lower redstone at
  -256..-224, tuff, geodes, deep monster rooms, lower fossils, gravel, lava springs and the cave
  carvers (min Y -248 instead of -56) shift too. Owner: Caves (underground), within the isolation rule above.
- No rivers or inland lakes yet: the terrain never selects river biomes, and inland water only
  appears where the ground dips below sea level (aquifers fill it to Y 63). Owner: Hydrology.
- Surface building cost grows with the world depth: vanilla's `SurfaceSystem.buildSurface` tests the
  surface rules for every stone block from the surface down to Y -256 on the single, consecutive
  "worldgen" executor of 1.21.8. See Performance.
- Badlands peaks above Y 256 get vanilla's orange terracotta floor (absolute anchor in the vanilla
  surface rule); high mountains in hot climates are badlands or stony peaks without snow.
- Creating a Vastlands world shows vanilla's "experimental settings" warning: vanilla registers
  worldgen data from non-vanilla packs with an experimental lifecycle. NeoForge stops it from
  repeating on later loads once confirmed. Not changed.
- Cloud height is vanilla (192); mountains above it cut through the cloud layer (3.2% of all
  columns, 4.3% of the land, lie above Y 192). Not changed: a matter of taste, `cloud_height` in the
  dimension type.

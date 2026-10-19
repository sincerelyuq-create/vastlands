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
   2.5. Geography — continent-scale regions, climate, climate-aware biomes, oceans, trenches and frozen seas. (Done: Agent 2.5.)
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
  mixin/ServerLevelMixin.java         weather does not freeze the cracks of ice fields (Vastlands worlds only)
  worldgen/
    WorldHeight.java                  MIN_Y = -256, HEIGHT = 768, MAX_Y = 511
    VastlandsChunkGenerator.java      "vastlands:noise", a NoiseBasedChunkGenerator subclass; trench water and
                                      ice field steps (OceanSurface)
    WorldgenDiagnostics.java          level-load checks (height mismatch, missing context)
    context/                          GenerationContext (per-world state), binding visitor, mixin duck interface
    density/StageDensityFunction.java vastlands:terrain / vastlands:hydrology / vastlands:caves router nodes
    density/TerrainParameterFunction.java  vastlands:terrain_parameter router nodes (climate, depth, initial density)
    system/                           system interfaces, Passthrough placeholder, GenerationSystems wiring
    noise/                            SimplexNoise, NoiseMath (fBm, ridged, smoothstep, smooth max, spline)
    geography/                        macro geography and climate (pure Java): GeographyModel, GeographySample,
                                      GeographySettings, Landform, FrozenSea (ice field fractures)
    terrain/                          the terrain: TerrainModel (height field on the geography, pure Java),
                                      BiomeClimate (biome parameters), VastlandsTerrain (TerrainSystem),
                                      TerrainColumn, ColumnCache
    ocean/OceanSurface.java           block steps of the oceans: trench water, ice fields
src/main/resources/
  vastlands.mixins.json
  assets/vastlands/lang/en_us.json                         world type name
  data/minecraft/tags/worldgen/world_preset/normal.json    lists the preset in the create-world screen
  data/vastlands/dimension_type/overworld.json             build limits -256..511
  data/vastlands/worldgen/world_preset/vastlands.json      the "Vastlands" world type
  data/vastlands/worldgen/noise_settings/overworld.json    noise range -256..511 + Vastlands stages
  data/vastlands/worldgen/density_function/overworld/      terrain, hydrology, landscape (stages); continents,
                                                           erosion, ridges, depth, initial_density, temperature,
                                                           vegetation, bottom_slide (terrain outputs)
src/test/java/...                                          JUnit tests (see Testing)
src/test/java/.../tools/TerrainPreview.java                ./gradlew terrainPreview: terrain, landform, biome, climate
                                                           and ice maps without the game
tools/worldcheck/                                          world generation, region analysis, heightmaps, oceans, screenshots
docs/terrain/, docs/geography/                             previews, region maps and in-game screenshots
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
  `erosion`, `ridges`, `temperature`, `vegetation`, `depth` and `initial_density_without_jaggedness`
  read the terrain (`vastlands:overworld/<name>`, see Terrain and Geography); the bottom slide is
  `vastlands:overworld/bottom_slide` (vanilla's -64..-40, lower only under abyssal trenches). Surface
  rules, aquifer noises, ore veins, spawn target and sea level 63 are vanilla values.
- Vanilla constraints (DimensionType / NoiseSettings codecs): `min_y` and `height` must be multiples
  of 16, `min_y + height <= 2032`, `logical_height <= height`. The dimension type and the noise
  settings must describe the same range, or terrain is silently clipped; `WorldgenDiagnostics` logs
  an error at level load if they differ, and `WorldHeightDataTest` fails the build if either file
  drifts from `WorldHeight`.
- The tag `#minecraft:normal` (world_preset) adds the type to the create-world screen
  ("World Type: Vastlands"). Dedicated servers use `level-type=vastlands:vastlands`.

How the height is used: the world builds and stores blocks from -256 to 511, bedrock is at -256.
The terrain surface spans about Y -30 (abyssal plains; trench floors down to ~-160) to 470
(asymptote of the highest peaks; the top slide at 488..504 is only a safety net). Below -64 the
rock is still solid deepslate cut only by carvers: the bottom slide stays at vanilla's -64..-40
because opening the deep range lets vanilla's fluid picker fill every cave below -54 with lava
(measured: lava rose from 1.0% to 5.8% of the -256..-65 band when the slide was moved down). Only
under trenches does the slide move down, to end 12 blocks below the trench floor (see Geography,
Trenches). Moving it everywhere is the Caves agent's job, together with the lava fix.

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
NoiseChunk → aquifers → [trench water] → surface rules → carvers → [trench water] → features → [ice fields]
                                                                                      └─ ForestSystem, via GenerationContext.of(level)
```

The bracketed steps are `VastlandsChunkGenerator` overrides (`fillFromNoise`, `applyCarvers`,
`applyBiomeDecoration`) calling `ocean/OceanSurface`; they only touch the chunk being generated and
are pure functions of the position, so they do not depend on generation order.

- Terrain and hydrology stages sit inside `minecraft:interpolated`, so they are evaluated at noise
  cell corners only (every 4×8×4 blocks) and interpolated in between. `cache_once` makes the three
  uses of the landscape share one evaluation per point.
- Router outputs owned by the terrain (`vastlands:terrain_parameter` nodes, files
  `density_function/overworld/{continents,erosion,ridges,temperature,vegetation,depth,initial_density,bottom_slide}.json`):
  the five climate parameters that select biomes, the biome depth, the initial density that aquifers
  and the preliminary surface use, and the bottom slide factor inside the final density. The five 2D
  ones are wrapped in `minecraft:flat_cache` like vanilla's.
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
  `-slope`), the five biome climate parameters and `geography` (the column's `GeographySample`:
  regional elevation, landform, provinces, climate; see Geography). Cached per thread; cheap for
  nearby repeated queries.
- `double surfaceHeight(x, z)`: the surface as generated (the 4-block grid of `column` heights,
  bilinearly interpolated like the density). Top landscape block = `ceil(surfaceHeight) - 1`.
- `double parameter(TerrainParameter, x, y, z)`: router outputs (see `TerrainParameter` for ranges).
- `GeographyModel geography()`: the macro geography for any position, uncached (for coarse grids
  such as drainage planning; see Geography, Interfaces for Hydrology).
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
1. Macro shape: since Agent 2.5 computed by `GeographyModel` (see Geography, which also lists what
   changed: ocean regions, provinces, branch ranges, the deep sea floor). Agent 2's parts, with the
   same noises and seeds:
   - Continents: 4 octaves of simplex fBm at a 11 000-block scale, domain-warped by 1 800 blocks,
     plus 3 unwarped fine octaves (690 blocks) for coastline detail. Value `c` = 0 is the coastline.
     A monotone spline maps `c` to the base height: coast 61..64.5, lowlands 70, interior 82..110
     (sea floor: see Geography).
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
   ±5..12 blocks, sharper on young ranges and in dry provinces). Undulation is 40% lower in plains
   provinces and gullies 30% weaker in dry ones. Heights above 410 are compressed towards an
   asymptote at 470 (`softCap`), so only the highest summits are flattened a little.

Elevation ranges and their frequency: Agent 2 measured (4 seeds × 20 000 random columns over
±200 000 blocks) sea floor on 26% of the world, lowlands 63..130 on 59%, mountains above 200 on
2.7%. With the geography (131 072 × 131 072 blocks around 0,0 at 128 blocks per pixel, seed
20261018): sea 36.6% (18.7% of the world below Y 0, 0.04% below Y -50: trenches), lowland 63..100
on 34%, 100..150 on 23%, 150..200 on 4.6%, above 200 on 1.6%, above 300 on 0.25%, above 400 on
0.04%; highest peaks ~452. The guards of `TerrainModelTest` are: sea 25..50%, lowland
63..130 over 38%, above 200 0.5..8%, above 300 under 1.5% and rarer than above 200, max under 470,
min above -200 (trenches). Neighbouring columns differ by at most ~12 blocks (canyon and trench
walls, crest cliffs), and the height field has no discontinuities (same tests as Agent 2's, now also
over the geography's fields).

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
- temperature and vegetation (humidity): from the geography's climate (Agent 2.5, see Geography).
- Since Agent 2.5 these mappings live in `BiomeClimate`; erosion is capped at 0.4 so flat land is
  inside E4 rather than on the E4/E5 boundary, and swamps need moisture > -0.1.
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

Geography (Agent 2.5)

The macro shape of the world and its climate are a separate pure-Java layer,
`worldgen/geography/GeographyModel`: a deterministic function of (seed, x, z) that the terrain model
builds on and later systems query. Four layers, each conditioned on the ones above it:

1. Macrogeography: continents and oceans, provinces, mountain systems, plateaus, basins, sea floor.
2. Climate: temperature and moisture from climate zones, elevation, distance to the sea, rain shadows.
3. Biome selection: `terrain/BiomeClimate` turns landform and climate into vanilla's six biome
   parameters; vanilla's overworld table picks the biome.
4. Local terrain detail: `TerrainModel` (Agent 2): gully erosion, crags, roughness, sea floor texture.

`GeographyModel.sample(x, z)` returns a `GeographySample` (record, documented field by field):
regional `elevation` (the macro surface without gullies and detail), `baseHeight`,
`continentalness`, the province masks `plains`, `hilliness`, `dryness`, `basin`, `plateau`, the
mountain fields `mountain`, `mountainHeight`, `alpine`, the ocean field `trench`, the climate
`temperature`, `seaLevelTemperature`, `moisture`, `rainShadow`, the prevailing wind `windX/windZ`, and
a `Landform` class. All fields are continuous except the class. The terrain caches one sample per
4-block grid column (`TerrainColumn.geography()`).

Layer 1 — macrogeography (`GeographyModel.macro`). Scales at `regionScale` 1:
- Continents and oceans: Agent 2's warped continent fBm (11 000 blocks, 0 = coastline) plus an
  ocean-region term (2-octave fBm at 42 000 blocks, weight 1.3) minus `oceanBias` (0.1). Oceanic
  regions become open oceans with islands, continental regions keep inland seas. Measured over 4
  seeds: 30–44% of the surface is sea; the longest open-water stretch on twelve 60 000-block
  transects per seed is 15 000–25 000 blocks.
- Sea floor (continent spline): coast Y 61 at c = 0; shelf to the shelf break at c = -0.1 (Y 50,
  ~500 blocks wide, mean depth under 15 blocks); continental slope to c = -0.3 (Y 12); rise to
  c = -0.65 (Y -14); abyssal plain Y -22..-30 (85–95 blocks of water). Deep floor (c < -0.35):
  - mid-ocean ridges: zero-lines of a warped 24 000-block noise; a broad rugged rise of up to 55
    blocks with a narrow central rift, varying along its length;
  - seamounts: at most one per 2 600-block cell (probability 0.3), radius 160–440, 25–85 blocks
    high, 30% flat-topped (guyots);
  - ridges and seamounts are compressed so the macro shape stays at least 6 blocks below sea
    level (at least 1 after the ±5 sea floor texture of the terrain detail);
  - trenches (below).
- Trenches: zero-lines of a warped 15 000-block noise, only in deep basins (c < -0.55) and where a
  30 000-block rarity noise exceeds `trenchThreshold` (0.25). Up to 135 blocks below the abyssal
  plain (floors down to Y ~-160), a narrow flat floor and steep walls (about 45° at their steepest
  in the model; in the test world the mean neighbour step was 0.76 blocks).
  0.19% of the sea floor lies below Y -45 (4 seeds); the deepest sampled floor was Y -155..-162.
- Provinces (16 000 blocks): plains provinces (`plains` 0..1, where the province noise is below
  `plainsBias`) reduce hilliness by 85%, interior swells by half, mountain activity by 60% and
  plateaus by 80%: lowland that stays flat or gently rolling for thousands of blocks (mean run of a
  plains province along transects 2 200–3 000 blocks, often much longer). Where the province noise
  is high, the hill mask is raised (hill country).
- Dry provinces: the broad part of the moisture zones (2-octave fBm, 17 000 blocks) gives `dryness`
  0..1. Tablelands are commoner there (plateau threshold lowered by up to 0.14), gullies 30% weaker,
  crags stronger, and the climate drier.
- Mountain systems: Agent 2's belts unchanged (main crest, massifs and passes, individual peaks,
  aretes on young ranges), plus branch ranges: zero-lines of an unwarped 5 000-block noise, only
  within reach of an active belt, height falling with the square of the reach, so side ranges leave
  the main range as broad ridges and die out. They join the main range with a smooth maximum that
  equals max() exactly when either side is 0 (no step where a branch begins). Mountain systems are
  60% weaker inside plains provinces, so they rise at the provinces' margins.
- Plateaus, basins, swells and rolling hills: Agent 2's, modulated by the provinces as above.

Layer 2 — climate (`GeographyModel.sample`). Values on vanilla's biome-parameter scale (-1..1):
- Sea-level temperature: 3-octave fBm at 26 000 blocks × 1.45 + 0.05, plus a 3 200-block local
  term (±0.07). Climate zones (vanilla temperature bands) run ~3 000 blocks on average along
  transects and span roughly 5 000–15 000 blocks.
- Temperature: sea-level temperature − `lapseRate` (0.0022) × (regional elevation − sea level − 12):
  one band per ~150 blocks of height. Foothills keep the regional climate while high ranges carry
  snow and frozen peaks even in hot zones.
- Moisture: 2-octave fBm at 17 000 blocks × 1.15 plus a 2 600-block local term, +0.2 near coasts,
  −0.2 deep inland, minus `rainShadow` (0.5) × shadow.
- Rain shadow: the prevailing wind is mostly westerly and turns over ~70 000 blocks. The crest
  envelope of the mountain systems 500, 1 300 and 2 600 blocks upwind is compared with the local one;
  shadow = smoothstep(25, 200, upwind − ½ local) with weights 1, 0.85, 0.65. Lee sides are measurably
  drier (`GeographyModelTest.climateFollowsTheLandscape`).

Layer 3 — biome selection (`BiomeClimate`, vanilla's `OverworldBiomeBuilder` table):
| Parameter | Source | Effect |
|---|---|---|
| continentalness | landform | ocean values exactly under water (deep ocean below ~20 blocks), coast band at the shoreline, rising inland; polar seas with ice strength > 0.3 stay in the shallow band (frozen ocean: snowfall, freezing) |
| erosion | landform | E0/E1 mountains (peaks, slopes), E2 plateaus (plateau biomes: meadow, savanna plateau, badlands), E3/E4 hills and plains (capped at 0.4: flat land is solidly E4), E5 some steep hill country (windswept), E6 flat wet lowland at sea level (swamps, never in dry climates) |
| weirdness | landform | crests → peaks slice, slopes → high, plains → low/mid; never the river slice; sign from a 1 800-block noise (variant biomes) |
| temperature | climate | `temperature`, with warm + arid land reading hotter (+0.35 × warm × arid: deserts need dryness) and hot + humid land cooler (−0.3 × hot × humid: savanna, forest, jungle instead of desert); oceans use the sea-level temperature |
| humidity | climate | `moisture`; temperate drylands are lifted from vanilla's temperate-arid cell (flower forest) into steppe (plains), except in scattered 1 500-block flower patches |

Resulting rules of thumb: polar zones → snowy plains, snowy taiga, ice spikes, frozen oceans and
snowy beaches; cold dry interiors → plains (steppe) and taiga; temperate → plains, forests, birch and
dark forest by moisture; warm → savanna (dry), forest, jungle (humid); hot and arid → desert, with
badlands on hills, plateaus and the slopes of dry ranges, stony peaks higher up and frozen peaks on
the highest summits. Measured on the test server (`TerrainBiomeTest`): along 8 transects of 40 000
blocks sampled every 64 blocks, the mean biome run is 593 blocks against 166 for vanilla with the
same seed (×3.6); every frozen ocean lies in the polar climate, every desert in a warm one, every
jungle in a humid one. No custom biomes were added: vanilla's table covers every combination the
geography produces (custom variants such as a dedicated polar ice-sheet or cold-desert biome are
possible later with a Vastlands biome source).

Landform classes (`Landform`, from the macro shape): SHELF, CONTINENTAL_SLOPE, ABYSSAL_PLAIN,
SUBMARINE_RIDGE, SEAMOUNT, TRENCH (under water); COAST, PLAINS, HILLS, BASIN, PLATEAU, FOOTHILLS,
MOUNTAINS (mountain relief > 60), HIGH_MOUNTAINS (> 180). Share of all columns over a 131 072-block
square (seed 20261018): sea ~36% (abyssal plain 12.6%, slope 18%, shelf 4%, ridges 1.3%),
plains 30%, hills 10%, foothills 12%, mountains 3.8%, high mountains 0.4%, plateaus 2%, basins 3%.

Trenches in the world:
- The bottom slide (`TerrainParameter.BOTTOM_SLIDE`, `VastlandsTerrain.bottomSlide`) is vanilla's
  gradient (0 at Y -64, 1 at -40) wherever the ground is above Y -28, and moves down with deeper
  floors to end 12 blocks below them, so trench water is open down to the floor and the rock below
  stays solid. `PresetDataTest.trenchesStayOpenBelowTheVanillaSlide` checks the final density.
- Vanilla's global fluid picker makes every open block below Y -54 lava, and the aquifers wall that
  lava off from the sea with a lid of rock and an air gap (seen at Y -46..-54 in the first test
  world), and carvers cut through water and fill their tunnels below -54 with lava. So
  `OceanSurface.fillDeepWater` runs after noise filling and again after carving: in every column
  whose floor is below Y -30, every block from the floor up to Y -30 becomes water (the floor block
  only if it is open). Lava in caves under the floor stays; where a cave mouth opens in the floor, a
  little lava meets the sea (obsidian, magma: 0.4% of the trench columns in the test world).

Frozen seas (`FrozenSea`, `OceanSurface.placeIceFields`, `ServerLevelMixin`):
- Ice strength 0..1 from the sea-level temperature: 0 at -0.45 (the edge of vanilla's frozen band),
  1 at -0.65; a 2 200-block noise roughens the ice edge into tongues and bays.
- Pattern: two domain-warped Voronoi networks (warp 40 blocks at 300 plus 5 at 40, so edges curve
  and jag). Large floes (~170 blocks): their edges are, depending on a 650-block noise that varies
  along them, narrow cracks (2–4 blocks), wide open leads (up to ~30 blocks) or closed pressure
  ridges (packed ice one or two blocks above the sheet). Small cracks (~60-block cells) run partly
  across the floes and end where their 220-block gate closes, so the network branches. Towards the
  warm edge leads widen and floes shrink until only drifting pieces remain (that break-up term only
  ever adds a block or more, so it never draws hairline cracks). A quarter of the large floes are
  thick multi-year ice (packed ice, two blocks deep). Crack widths are distances in warped space;
  they are scaled by the largest local stretch of the warp's Jacobian, so every crack is at least
  ~2 blocks wide in the world and diagonal cracks are connected lines of water, not dashes of
  blocks touching at their corners (an earlier version showed such dashes in game). The Voronoi edge distance is exact
  (`FrozenSeaTest.voronoiEdgesAreExact`), so cracks are continuous across cell and chunk borders.
- In the world: after this chunk's features (so vanilla's `freeze_top_layer` cannot close the
  cracks again), every open-sea column (ground below the surface) with ice strength > 0 gets ice,
  packed ice or water at sea level - 1 by the pattern; only water and ice blocks are touched, so
  vanilla's icebergs stand frozen into the field. `ServerLevelMixin` stops weather from freezing
  water in a crack of a Vastlands ice field (vanilla freezes water at ice edges in cold biomes,
  which would close the cracks within minutes); other worlds and other water are unchanged.
- Model: ice 82–88% of a firm field, the largest connected crack network holds 85–95% of the open
  water, isolated holes under 10 blocks under 0.1% (`FrozenSeaTest`). Test world `ice2` (16 × 16 chunks,
  seed 20261018 around 30208,-19712): the sea surface agrees with the model in 100% of the 12 995
  open-sea columns more than 16 blocks from an iceberg (93.6% overall: icebergs of vanilla's
  frozen-ocean surface rules cover ~17% of that square); no lava or air in the water.

Interfaces for Hydrology (Agent 3). Everything is deterministic, thread-safe, pure in (x, z) and
independent of chunks:
- Coarse planning: `terrain.geography()` returns the `GeographyModel`.
  - `smoothElevation(x, z)`: regional elevation without the sharp parts (individual peaks, aretes,
    ridge texture) — the smooth potential surface rivers should descend; ~3 µs per call.
  - `sample(x, z)`: everything in `GeographySample` (elevation, base height, continentalness,
    provinces, mountain fields, climate, landform); ~10 µs per call.
  - `seaLevelTemperature(x, z)`: cheap; with `FrozenSea.strength` it says where seas (and rivers)
    freeze.
  Sample these on a coarse grid (e.g. 32–64 blocks over a 4 096-block region with a margin) to route
  flow; nothing needs neighbouring chunks.
- Exact surface: `terrain.column(x, z)` (cached per thread; `.geography()` is the same sample) and
  `terrain.surfaceHeight(x, z)` as before.
- Useful fields for drainage: `elevation`/`smoothElevation` (downhill direction), `baseHeight`
  (valley floor level), `continentalness` (0 = coast, < 0 sea; a distance-to-sea proxy:
  `continentality()`), `landform` (where rivers end: SHELF/COAST; lakes: BASIN, which dips below sea
  level and already holds sea-level lakes), `mountain`/`mountainHeight` (sources, gorges),
  `plateau` (waterfalls off escarpments), `moisture` (runoff: river size), `rainShadow`, `dryness`
  (ephemeral rivers in arid provinces), `temperature`/`iceCover()` (frozen rivers).
- The ocean floor is deeper than vanilla's: river mouths reach the shelf (Y 50..61) first; the
  continental slope begins ~500 blocks offshore.
- Biome parameters: rivers need weirdness |w| < 0.05 (Agent 2's note, unchanged); temperature and
  humidity are now terrain-owned router outputs too (`TerrainParameter.TEMPERATURE`, `VEGETATION`).

Configuration (`GeographySettings.DEFAULT`, one record, used by `VastlandsTerrain`):
`regionScale` 1.0 (multiplies every macro wavelength: continents, ocean regions, provinces, mountain
systems, plateau masks, climate zones), `oceanBias` 0.1, `plainsBias` -0.08, `lapseRate` 0.0022,
`rainShadow` 0.5, `trenchThreshold` 0.25. `./gradlew terrainPreview --args="--region-scale 1.5 ..."`
renders other scales. They are code constants, not a config file: a world's terrain must not depend
on a mutable config. Changing any value changes generation (seams against existing chunks).

Performance: see Terrain, Performance (measured with the geography).

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

JUnit (`./gradlew test`, 54 tests):
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
  (sea 25..50%, lowland > 38%, above 200 0.5..8%, above 300 < 1.5% and rarer than above 200,
  max < 470, min > -200), climate parameters in range, underwater ⇔ ocean continentalness, no river
  weirdness.
- `GeographyModelTest` — determinism from any thread; no jumps in elevation, smooth elevation,
  temperature, moisture and masks within 0.05 blocks (4 seeds); region scale along 60 000-block
  transects (plains provinces > 1 500 blocks, climate zones > 2 500, an open sea > 5 000 wide, sea
  25..50%); shelf shallower than 15, abyss 65..110 deep, submarine features at least 5.9 below sea
  level, landform class ⇔ under water; trenches 0.02..2% of the sea floor, deeper than Y -100,
  above -200, and the only floor below Y -45; lapse rate and rain shadow; the terrain uses the
  geography.
- `FrozenSeaTest` — the cover depends only on position and seed; no ice outside the freezing band;
  the Voronoi edge distance equals a brute-force search; a firm field is 80..97% ice with one
  connected crack network and few isolated holes; ice falls towards the warm edge; neighbour
  changes across chunk borders equal those inside chunks.
- `VastlandsTerrainTest` — one surface per column, cache equals the model from any thread,
  `surfaceHeight` interpolates the grid, outputs inside their declared ranges, initial density
  below the surface, depth semantics (deep dark band), low worlds cap the terrain below their top.
- `PresetDataTest` (on NeoForge's `EphemeralTestServerProvider`, data loaded exactly as the game
  does) — preset listed, generator and height; router outputs the terrain does not own (aquifer and
  ore vein noises) are bit-for-bit vanilla over Y -256..511; terrain-owned outputs (including
  temperature and vegetation) equal the terrain; final density solid at the bottom, air at the top
  and above the terrain surface; the bottom slide is vanilla's under ordinary ground and the deepest
  trench found is open water down to its floor, solid below.
- `TerrainBiomeTest` (test server) — surface biomes from vanilla's table agree with the terrain
  (ocean ⇔ under water, mountain biomes above Y 300, no river biomes) and with the climate (frozen
  oceans polar, deserts warm, jungles humid); the mean biome run along transects is more than twice
  vanilla's for the same seed (measured 593 vs 166 blocks).

Terrain previews without the game: `./gradlew terrainPreview --args="..."` (`TerrainPreview` in the
test sources): `--seed S --center X Z --size BLOCKS --scale BLOCKS_PER_PIXEL --out PREFIX` writes a
hillshaded relief map and a perspective view and prints the height distribution and the sampling
cost; `--camera X Z YAW ALTITUDE` places the view; `--heights FILE` exports the expected surface
heights of the square (float32) for `heightmap.py --expected`. Geography: `--maps` with `landform`,
`biome` (vanilla's own table applied to the terrain's climate parameters, so it shows the biomes the
game will pick at the surface), `temperature`, `moisture` (biome bands) and `ice` (1 block per pixel
at the coldest sea of the square, or around `--ice-center X Z` at the square's scale); it also
prints landform, climate band and biome shares, the mean biome run along rows and the highest
peaks; `--find NAME` lists places of a biome or landform; `--cover FILE` exports the expected sea
ice cover for `oceancheck.py --cover`; `--region-scale F` renders other region sizes.

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
- `oceancheck.py run/NAME [--cover FILE] [--expected FILE] [--png FILE]` checks open-sea columns:
  the sea surface (water, ice, packed ice), agreement with the ice model (overall and away from
  icebergs), ice changes across vs inside chunk borders, lava and air in the water column, anything
  but water between the model floor and the surface, the deepest floor; `--png` maps the sea
  surface. Streams the region files chunk by chunk (fine for 32 × 32 chunks).
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
  -64..-40 (see World Preset and Height). Owner: Caves. Exception: the open water of abyssal
  trenches, which `OceanSurface.fillDeepWater` turns back into water after noise filling and after
  carving (see Geography, Trenches); where a cave opens into a trench floor, its lava meets the sea.
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
- Ice fields replace vanilla's sea ice only on open-sea columns at sea level; vanilla's icebergs stay.
  The iceberg feature may carve melt pools into a neighbouring chunk's ice after that chunk was
  decorated, so ice next to some icebergs depends on generation order (as vanilla's trees and ore
  blobs on chunk borders do). Weather does not freeze ice-field cracks (`ServerLevelMixin`), but
  players can still place blocks or water there.
- Cloud height is vanilla (192); mountains above it cut through the cloud layer (3.2% of all
  columns, 4.3% of the land, lie above Y 192). Not changed: a matter of taste, `cloud_height` in the
  dimension type.

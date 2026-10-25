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
3. Hydrology — rivers, tributaries, downhill drainage, waterfalls, and river valleys. (Done: Agent 3.)
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
  mixin/NoiseBasedChunkGeneratorMixin.java  noise filling uses the caves' fluid picker (Vastlands worlds only)
  worldgen/
    WorldHeight.java                  MIN_Y = -256, HEIGHT = 768, MAX_Y = 511
    VastlandsChunkGenerator.java      "vastlands:noise", a NoiseBasedChunkGenerator subclass; trench water and
                                      ice field steps (OceanSurface), river water steps (RiverWater), cave
                                      steps (CaveBlocks), no vanilla carvers, base heights with river water
    WorldgenDiagnostics.java          level-load checks (height mismatch, missing context)
    context/                          GenerationContext (per-world state), binding visitor, mixin duck interface
    density/StageDensityFunction.java vastlands:terrain / vastlands:hydrology / vastlands:caves router nodes
    density/TerrainParameterFunction.java  vastlands:terrain_parameter router nodes (climate, depth, initial density)
    density/HydrologyParameterFunction.java  vastlands:hydrology_parameter router nodes (river weirdness, depth, initial density)
    system/                           system interfaces, Passthrough placeholder, GenerationSystems wiring
    noise/                            SimplexNoise, NoiseMath (fBm, ridged, smoothstep, smooth max, spline)
    geography/                        macro geography and climate (pure Java): GeographyModel, GeographySample,
                                      GeographySettings, Landform, FrozenSea (ice field fractures)
    terrain/                          the terrain: TerrainModel (height field on the geography, pure Java),
                                      BiomeClimate (biome parameters), VastlandsTerrain (TerrainSystem),
                                      TerrainColumn, ColumnCache
    ocean/OceanSurface.java           block steps of the oceans: trench water, ice fields
    hydrology/                        rivers, lakes, falls: HydrologyModel (pure Java: DrainageDomain, BasinRivers,
                                      WaterIndex, LruCache), VastlandsHydrology (HydrologySystem), WaterColumn,
                                      RiverWater (block step)
    caves/                            caves, cave water and lava: CaveModel (pure Java: CaveGeology, FluidDomains,
                                      CaveNetwork, CavePrimitive, Fractures, Caverns, RiverTunnels, CaveColumn,
                                      Noise3), VastlandsCaves (CaveSystem), CaveBlocks (block step), CaveVolume
                                      (model-side fluid checks), CaveSettings, CaveFluid
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
src/test/java/.../tools/HydrologyPreview.java              ./gradlew hydrologyPreview: rivers, lakes and falls over the
                                                           carved relief; expected water export for rivercheck.py
src/test/java/.../tools/CavePreview.java                   ./gradlew cavePreview: cave sections, plans, slices, stats,
                                                           fluid checks, river tunnels, sea caves, bench
tools/worldcheck/                                          world generation, region analysis, heightmaps, oceans, rivers,
                                                           caves (cavecheck.py, blockprobe.py), screenshots
docs/terrain/, docs/geography/, docs/hydrology/            previews, region maps and in-game screenshots
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
  (next section; vanilla's noise caves removed from it); `aquifers_enabled: false` (the caves'
  fluid picker decides every open block, see Caves); the top slide moved from 240..256 to 488..504; the router outputs `continents`,
  `erosion`, `ridges`, `temperature`, `vegetation`, `depth` and `initial_density_without_jaggedness`
  read the terrain (`vastlands:overworld/<name>`, see Terrain and Geography); the bottom slide is
  `vastlands:overworld/bottom_slide` (vanilla's -64..-40, lower only under abyssal trenches). Surface
  rules, aquifer noises (unused since aquifers are disabled), ore veins, spawn target and sea level
  63 are vanilla values.
- Vanilla constraints (DimensionType / NoiseSettings codecs): `min_y` and `height` must be multiples
  of 16, `min_y + height <= 2032`, `logical_height <= height`. The dimension type and the noise
  settings must describe the same range, or terrain is silently clipped; `WorldgenDiagnostics` logs
  an error at level load if they differ, and `WorldHeightDataTest` fails the build if either file
  drifts from `WorldHeight`.
- The tag `#minecraft:normal` (world_preset) adds the type to the create-world screen
  ("World Type: Vastlands"). Dedicated servers use `level-type=vastlands:vastlands`.

How the height is used: the world builds and stores blocks from -256 to 511, bedrock is at -256.
The terrain surface spans about Y -30 (abyssal plains; trench floors down to ~-160) to 470
(asymptote of the highest peaks; the top slide at 488..504 is only a safety net). The caves use the
whole depth down to Y -248 (see Caves): the caves stage wraps the final density outside the
slides, so the bottom slide (vanilla's -64..-40, lower under trenches, see Geography, Trenches) no
longer limits them; it only shapes the landscape density, which is solid there anyway. Vanilla's
lava below Y -54 is gone: the caves' fluid picker puts lava only in deep lava domains.

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
vanilla final_density body                 slides, blend, interpolation (vanilla's noise caves removed)
        │
vastlands:caves        ← CaveSystem        wraps the whole final_density (per block, not interpolated)
        │
NoiseChunk (aquifers off; fluids from CaveSystem.fluidPicker) → [trench water, cave step, river water]
        → surface rules → (no vanilla carvers) [trench water, river water, swallets] → features → [ice fields]
              └─ ForestSystem, via GenerationContext.of(level)
```

The bracketed steps are `VastlandsChunkGenerator` overrides (`fillFromNoise`, `applyCarvers`,
`applyBiomeDecoration`) calling `ocean/OceanSurface`, `caves/CaveBlocks` and `hydrology/RiverWater`;
`applyCarvers` no longer runs vanilla's carvers in Vastlands worlds. The steps only touch the
chunk being generated and are pure functions of the position (and of the hydrology model for the
neighbouring columns), so they do not depend on generation order.

- Terrain and hydrology stages sit inside `minecraft:interpolated`, so they are evaluated at noise
  cell corners only (every 4×8×4 blocks) and interpolated in between. `cache_once` makes the three
  uses of the landscape share one evaluation per point.
- Router outputs owned by the terrain (`vastlands:terrain_parameter` nodes, files
  `density_function/overworld/{continents,erosion,temperature,vegetation,bottom_slide}.json`):
  climate parameters that select biomes and the bottom slide factor inside the final density.
  Since Agent 3, `ridges.json` (weirdness: river biomes), `depth.json` (biome depth from the
  carved surface) and `initial_density.json` (the initial density that aquifers and the
  preliminary surface use: channel floors) are `vastlands:hydrology_parameter` nodes, which pass
  the terrain's value through away from rivers.
  The five 2D ones are wrapped in `minecraft:flat_cache` like vanilla's.
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
place where implementations are chosen. Terrain is `VastlandsTerrain`, hydrology is
`VastlandsHydrology`, caves are `VastlandsCaves`; forests are still `Passthrough.INSTANCE` (which
implements only `CaveSystem` and `ForestSystem` defaults).

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
- `TerrainColumn sample(x, z)` (Agent 3): the exact model column at any point, uncached, for planning
  that should not evict the per-thread cache of the chunk being generated.
- `GenerationSystems.surfaceHeight(x, z)`: the ground surface after all stages that reshape it, used
  by `SurfaceRulesContextMixin`: the hydrology's surface (terrain with channels, valleys, levees).

HydrologySystem (contract for later systems; details in Hydrology below):
- `double surfaceHeight(x, z)`: the generated ground surface with channels, valleys and levees
  (the hydrology's node heights on the 4-block grid, interpolated bilinearly like the density). Top
  landscape block (before caves) = `ceil(surfaceHeight) - 1`, except on steep carved faces, which
  jut out above it in places (rough walls, see Hydrology; never below it).
- `WaterColumn water(x, z)`: the water of a block column: `kind` (DRY, SEA, RIVER, LAKE, ROCK),
  `waterTop` (Y of the top water block), `groundTop` (top solid block under the water: the density's,
  or lower at the foot of a fall; a boulder's top for ROCK), `surface`. `placed()` is true for river and lake water, which
  Vastlands places itself; sea water comes from noise filling (vanilla's aquifers until Agent 4,
  now the caves' fluid picker). Not cached: about a microsecond
  plus one model column query; query once per column per step.
- `double parameter(TerrainParameter, x, y, z)`: router outputs as reshaped by hydrology (weirdness,
  depth, initial density); other parameters are the terrain's.
- `VastlandsHydrology.model()` (`HydrologyModel`) for planning: `falls(x, z)`, `stats(x, z)`,
  `describe(x, z)`, and (Agent 4) `channels(x, z, reach, visitor)`: the river segments within reach
  of a point (ends, water top, half width, bank), read only, for systems that follow rivers.

CaveSystem (contract for later systems; details in Caves below):
- `apply(x, y, z, density)`: the final density with cave space opened (never above the ground
  except at entrances) and gorge roofs restored.
- `double surfaceHeight(x, z, ground)`: the ground once the caves have shaped it (raised only
  under gorge roofs); `GenerationSystems.surfaceHeight` uses it.
- `Aquifer.FluidPicker fluidPicker(vanilla)`: the fluid of every open block in noise filling.
- `VastlandsCaves`: `isCave(x, y, z)`, `caveFluid(x, y, z)`, `isRoof`, `isGrotto`, `describe`; cave
  columns cached per thread.

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
clamped to ±128. Every column has exactly one surface, so there is no floating terrain (the caves
hollow out the rock below, see Caves). Biomes, aquifers and surface rules are fed from the
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
- Memory: a 4 096-entry column cache per worldgen thread (~0.5 MB; ~1.2 MB estimated with the
  geography samples); the model holds only seeds.

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
square (seed 20261018, 128 blocks per pixel): sea 36% (abyssal plain 11.6%, slope 17.6%, shelf
4.1%, ridges 2.9%, seamounts 0.1%, trenches < 0.05%), coast 1.7%, plains 28.4%, hills 9.9%,
foothills 13.6%, mountains 4.3%, high mountains 0.5%, plateaus 2.1%, basins 3.1%.

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
  Since Agent 4 the caves' fluid picker puts sea water into trenches and into the caves of sea
  domains, and carvers no longer run, so this step only re-sets blocks that already are water.

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
  water, isolated holes under 10 blocks under 0.1% (`FrozenSeaTest`). Test world `ice5` (16 × 16
  chunks, seed 20261018 around 30208,-19712): the sea surface agrees with the model in 100% of the
  9 876 open-sea columns more than 16 blocks from an iceberg (93.4% overall: icebergs of vanilla's
  frozen-ocean surface rules cover ~18% of that square); no lava or air in the water.

Interfaces for Hydrology (Agent 3). Everything is deterministic, thread-safe, pure in (x, z) and
independent of chunks:
- Coarse planning: `terrain.geography()` returns the `GeographyModel`.
  - `smoothElevation(x, z)`: regional elevation without the sharp parts (individual peaks, aretes,
    ridge texture) — the smooth potential surface rivers should descend; ~1.5 µs per call.
  - `sample(x, z)`: everything in `GeographySample` (elevation, base height, continentalness,
    provinces, mountain fields, climate, landform); ~3 µs per call.
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

Performance (same machine, pure-Java harness, the 4-block grid of 64 squares of 512 × 512
blocks): a terrain column costs 7.3 µs per thread with the geography against 5.4 µs for Agent 2's
model (+35%; Agent 2 measured all Vastlands code at 2.4% of chunk generation CPU, so about 1% of
generation time). Geography queries: `smoothElevation` 1.5 µs, `sample` 3.2 µs,
`seaLevelTemperature` 0.13 µs. Ice step: skipped after four temperature samples unless a chunk is
near the freezing band. Memory: a cached column now carries its `GeographySample` (estimated from
object sizes: ~1.2 MB per worldgen thread for the 4 096-entry cache).

Hydrology (Agent 3)

Rivers, tributaries, lakes and waterfalls are a separate layer on top of the terrain:
`worldgen/hydrology`. `HydrologyModel` is pure Java (no game classes) like `TerrainModel`: a
deterministic function of (seed, x, z) that can be rendered without the game
(`./gradlew hydrologyPreview`). `VastlandsHydrology` turns it into router density, router
parameters and block water; `RiverWater` places the water.

Layers, coarse to fine:
1. Drainage (`DrainageDomain`), on a grid of 128-block cells, per tile of 128 × 128 cells
   (16 384 blocks) computed with a margin of 128 cells on every side (a square of 384 × 384 cells):
   - elevation: `GeographyModel.smoothElevation` at cell centres; cells below sea level are sea
     (oceans and the sea-level basins that aquifers fill);
   - routing: a priority flood from the sea and the square's border (Barnes et al. 2014) in which
     each cell is processed in the order of its own elevation ("breaching"): water runs down into a
     depression and leaves it over its lowest pass, so every land cell drains to the sea;
   - lakes: a second, filling flood gives each depression's spill level; depressions at least 7
     blocks deep and 2 cells large hold a lake 6 blocks below the spill level, lower in dry
     climates (half the depth times the aridity); very dry lakes are terminal (no outflow);
     shallower depressions are breached by their river;
   - discharge: runoff (0.25..1.2 from the climate's moisture, up to ×1.9 in mountains above Y 90)
     accumulated downstream; lakes lose 15..85% to evaporation, terminal lakes all of it;
   - basins: each land cell belongs to the mouth cell (or terminal lake) it drains to. A tile owns
     the basins whose mouth lies in its core and that stay four cells clear of the computed
     square's border. Measured basins reach at most ~15 000 blocks from their mouth (seed 20261018,
     131 072² blocks), so the 16 384-block margin covers them; a basin that does not fit is owned by
     nobody and has no rivers (none seen in previews).
   Every basin is computed by exactly one tile from pure functions of the seed, so a basin's rivers
   are the same whichever chunk, thread or tile asks first. The raster is dropped after
   construction; a tile keeps its owned river cells (discharge ≥ 24 runoff-weighted cells) and
   lakes.
2. Rivers (`BasinRivers`), per basin, computed on first use:
   - node points: in each river cell the lowest of 3 × 3 candidates of the exact terrain (40 blocks
     apart), so rivers find the valley floors of the gully erosion;
   - reaches (source or confluence to the next confluence, lake or mouth): a valley line through
     the node points (centripetal Catmull-Rom curve); a reach into the sea ends at the centre of
     the first sea cell, and a reach whose bank reaches the sea earlier (the coarse cells miss
     narrow bays) ends there, a little way into the water, instead of running on along the shore
     (it then no longer joins its confluence);
   - meanders on the valley line: a sine-generated curve (the shape of real meanders: the river's
     direction swings by up to ω about the valley line, ω 0.35..1.45 from a 2 600-block noise),
     to first order a lateral offset `A (sin φ + c3 sin 3φ)` and a shift along the valley
     `-B sin 2φ` with `A, B, c3` from Bessel functions of ω. Wavelength 11..16 river widths (at
     least 110..150 blocks), a whole number of half waves per reach (the reach starts and ends on
     its valley line, so confluences, mouths and lake inflows stay put), the river below a
     confluence continuing the phase of the largest river above it, wavelength and strength
     varying along the river (stretches of deep loops and of gentle bends). The amplitude is held
     to the flat ground beside the valley line (probed every 32 blocks: no swing into ground
     rising more than 6 blocks plus 4%, a lake or the sea), to the valley line's curvature, and to
     0 where the valley line itself lies in a lake or the sea. Resampled every 4 blocks;
   - width `3 + 0.8 × discharge^0.6` (about 8 blocks at a source, 25-30 after a few hundred cells,
     50 at a thousand, 90-110 for the largest rivers of a continent), ±10% wider in bends and
     ±8% along the river (noise), depth `1.5 + 0.075 × width` (2..10); sources taper in over 64
     blocks;
   - water levels, from the sources down: the highest level that keeps the banks above the water
     (running minimum of the exact terrain 3 blocks and `4 + 0.2 × half width` blocks beside the
     channel, every 16 blocks, minus 1.5); a river entering a lake meets its surface (an inflow that
     arrives lower lowers the lake; levels are iterated until lake surfaces settle); the river below
     a confluence starts at the lowest incoming level;
   - pools and falls: levels are split into flat pools. Where the level falls gently, a pool ends
     when it has dropped 2 blocks (a 2-block ledge). Where it falls steeply, a pool may drop by up
     to `100 × steepness` blocks (random per pool, at most 56) before the next fall: the pool
     deepens upstream into a gorge whose head is a tall fall, like a waterfall retreating
     upstream. A pool shorter than 64 blocks between two falls joins the pool below (one tall fall
     instead of a staircase, up to 80 blocks). All water tops are `seaTop + 2k`: two water
     surfaces differ by 0 or at least 2 blocks;
   - a tributary that arrives above the main river falls into the main river's valley where its
     level meets the valley wall (a hanging valley); a river that arrives above a lake drops to the
     lake's level 24 blocks plus its half width before the shore, in its own channel;
   - bed: parabolic, depth as above; above each fall the bed rises to one block below the water
     (sill), below it a plunge pool `2 + 0.4 × drop` deep (3..14). Below falls of 6+ blocks the
     pool is round and wider than the river: radius `(r + bank + 2) × 1.05..1.35`, narrowing back
     to the river over `max(8 + 0.5 × drop, 1.5 × radius)` blocks;
   - river lakes: in long gentle pools (steepness < 0.03, river 5+ blocks wide, above sea level)
     the river widens into a lake, an irregular oval of half length 5..9 river widths (60..420
     blocks) and half width 2.5..4.5 widths (20..200), about one per 2 500 blocks of such river,
     where the terrain 6 blocks beyond its shore stands 1.5 blocks above the water (else it
     shrinks, or there is none); deeper towards the middle (2.5 + 0.05 × radius). The pool's water
     is flat, so the lake is a wider stretch of it;
   - valley (`HydrologyModel.profile`): a one-block bank, a floor just above the water (rising 3
     blocks per 100, gentle swells up to 1.2 blocks), flat over the inner half of the floodplain
     (`2 + 0.8 × width`, up to 80 blocks, in gentle terrain only); beyond, the terrain itself,
     its height above the floor scaled by an S-curve that reaches 1 where the valley side has
     climbed it at about the wall slope (0.7 gentle .. 2.0 steep): valley sides keep the
     terrain's shape, lowered and flattened towards the river, and meet the terrain at the
     per-segment cutoff (at most 320 blocks) without a step. The valley shape is smoothed along
     the river over ±48 blocks;
   - fall lips (`BasinRivers.fallSide`): a fall cuts the channel across along its lip, a line
     through the fall bowed upstream in the middle (a horseshoe: 0.2..0.5 × the half width of
     channel and bank at falls of 6+ blocks; ±0.25 at ledges) and ragged (two octaves of value
     noise, 1..5 blocks). Beyond the banks the line curves downstream (parabola), so the valley
     below ends in a rounded head, an amphitheatre below a tall fall and a curved terrace edge
     beside a ledge, not a straight cut across the valley. Segments below a fall do not reach
     upstream of its lip, segments above it not downstream (within 330 blocks along the river,
     and only segments wholly on their side, so a meander swinging back keeps its channel);
   - boulders: 2..6 in the plunge pool of every fall of 6+ blocks (clear of the curtain), 1..4 on
     its lip (they split the curtain into strands), a few along ledges (on and below the step) and
     in steep narrow streams (every 12 blocks with a chance rising with the slope). Radius
     0.8..2.6 blocks, an irregular outline, 1..2 blocks above the water they stand in.
3. Index (`WaterIndex`): the segments and lake cells that can affect a 512-block region, from every
   tile that may own a basin reaching it (the 3 × 3 tiles around), bucketed by 32 blocks.
4. Column (`HydrologyModel.sample`): for each segment within reach, the channel profile at the
   distance from its centre line, jittered by noise of the column (ragged banks: up to 0.8..3
   blocks at the water's edge, 16- and 6-block noise; ragged valley walls: up to 2.5..12 blocks
   beyond the bank, 56- and 20-block noise). The carved height is
   `max(min(terrain, min profile), min(levee, cap))`: carving takes the lowest profile, levees raise
   ground beside a channel above sea level to one block above its water (rare: banks are measured),
   capped by every channel bed so no levee closes another channel. Boulders covering the column
   give its rock top.

Water bodies and the block step (`VastlandsHydrology.water`, `RiverWater`):
- A column's water is the lowest body that holds water there: a river channel always does inside
  its wetted width, a river pool (within its width plus the bank width) or a lake region (its
  coarse lake cells plus a ring of cells) where the ground lies below its water top; the sea where
  the ground lies below sea level (aquifers fill it). A boulder at or above that water (or above
  dry ground) makes the column ROCK. `WaterColumn` gives the ground top (a boulder's top), the
  water top and the kind (DRY, SEA, RIVER, LAKE, ROCK).
- `RiverWater.place` runs after noise filling (so surface rules see the water: channel floors get
  vanilla's underwater dirt and gravel) and again after carving (carvers cut through water and
  banks). For the chunk plus a 3-column margin it reads the water columns from the model, then
  turns water columns beside dry ground more than 2 blocks below their water into rocky rims at
  the water top (a pool ending at a cliff gets a lip of rock instead of a pillar of raised ground
  beside it; two rounds, as a rim at the foot of a fall exposes the pool above; each round takes
  one column of margin so neighbouring chunks agree), fills water from the ground up to the water
  top; seals the bed and every block next to
  neighbouring water that is not solid (cave and carver openings; stone over a hole, never gravel,
  and sand or gravel holding water back over a hole gets a stone block under it, so nothing falls
  at the first block update); raises a dry column up to 2 blocks lower than a neighbouring water
  surface to it (a low levee one block wide; in the model 1.5% of the shore edges around the test
  places are lower than the water, the deeper ones become rims); builds a sill under the upper
  edge of every fall
  (one water block deep); cuts away the bilinear ramp of the density inside a channel at the foot
  of a fall; hangs a curtain in the lower column: a flowing block (amount 7) at the upper water
  top and falling water down to the lower water, through any rock the rough walls put there;
  makes the water churn where a fall lands: soul sand under the pool and a rising bubble column
  (vanilla's, with its particles and sound) up to the surface in every column under a curtain of
  4+ blocks and in 40% of those under 2-block ledges (a bubble column is a water source to the
  fluid rules); and builds boulders from the ground up (andesite, cobblestone and mossy cobblestone, mossier at the
  water: surface rules only repaint plain stone, so they keep these materials).
- Stability: these are the states vanilla computes for those blocks (`FlowingFluid.getNewLiquid`):
  no fluid ticks are scheduled and nothing changes when a chunk loads or a block next to the
  water updates. The 2-block minimum between water surfaces matters: next to a 1-block step, air
  above the lower water with two upper sources beside it turns into a source, and that spreads
  along the whole step and down the river. `RiverWaterTest` checks the ported fluid rules on 3 × 3
  proto-chunks around falls (borders included), `tools/worldcheck/rivercheck.py` on generated
  worlds.
- Every decision depends only on the model for the column and its neighbours, never on the
  chunk's neighbours' blocks, so chunks agree across their borders in any generation order.

Router and integration:
- `vastlands:hydrology` (density): the terrain density with the surface moved to the carved
  height: `density - d(terrain) + d(carved)`, both clamped like the terrain's. Output range ±256
  around the input (conservative). Rough walls: where carving left a steep face (the carved
  heights of a grid column and its four neighbours differ by more than 6 blocks, the hydrology cut
  into the terrain there, and more than 8 blocks above the foot of the face), the surface at a
  height is the higher of the carved surface and the carved surface sampled up to 3.5 blocks
  away (a smooth 3D offset, 13 blocks across and 9 high): cliffs and gorge walls jut out in
  ledges and buttresses that change every few blocks of height instead of rising as one vertical
  outline. Only outward, so no hollow ever opens below the 2D surface (the water, the aquifers
  and the preliminary surface rely on it); the block step clears the rock where a fall's curtain
  hangs.
- `vastlands:hydrology_parameter` (new node type): `ridges` (weirdness) is 0 at grid columns inside
  a channel at least 6 blocks wide, so vanilla's table picks river or frozen river (or swamp,
  mangrove swamp in E6 lowland, and the middle biome in mid/far-inland E0/E1 mountains, as in
  vanilla valleys); `initial_density_without_jaggedness` uses the lowest carved height of the
  noise cell, so aquifers and the preliminary surface never see ground above a channel floor;
  `depth` is measured from the carved surface, so the floors of gorges (up to ~60 blocks below
  the terrain) get surface biomes, not the cave biomes (dripstone, lush caves) their depth below
  the uncarved terrain would select. Other parameters pass the terrain's values through.
- `GenerationSystems.surfaceHeight` is the hydrology's surface (channels included):
  `SurfaceRulesContextMixin` paints channel floors and valley walls.
- `VastlandsChunkGenerator.getBaseHeight/getBaseColumn` include river and lake water, as vanilla's
  include the sea, so structures placed before the chunk exists (village paths and houses) stand on
  the water surface rather than on the channel floor.
- Freezing is vanilla's: rivers and lakes in cold biomes (frozen river, snowy biomes) freeze by
  `freeze_top_layer` and weather. `ServerLevelMixin` only keeps sea ice field cracks open.
- F3: a "Vastlands hydrology" line (water kind and top, ground, nearest river: width, top, distance).

Hydrology in numbers (seed 20261018, `hydrologyPreview`; 4 vCPU container):
- 65 536 × 65 536 blocks around 0,0: rivers and lakes above sea level cover 2.3% of the land; the
  widest rivers are 90..110 blocks (sampled every 256 blocks; plunge pools of the largest rivers
  slightly wider), the tallest falls 72..84 blocks (mountain falls of 84 and 74 blocks at
  32018,2298 and 31975,2351, 76 blocks at 2272,19974). Rendering it computes 42 drainage tiles
  (9.5 s) and 687 basins with rivers (474 000 river segments of 4 blocks, 11.2 s).
- Drainage on a 131 072-block square (prototype, before breaching): basins reach at most 15 100
  blocks from their mouth; filling every depression would have made lakes of 9% of the land.
- Four test places (`HydrologyModelTest`): 54 535 segments, 396 steps, 80 falls of 8+ blocks,
  tallest 50; 182 river mouths and 3 rivers ending in terminal lakes in 2 × 2 tiles around 0,0.

Performance:
- Drainage of a tile: 0.4..0.6 s of CPU (147 456 cells; the smooth elevation, 2.7 µs a sample,
  dominates; samples are cached in shared 64 × 64-cell tiles, so neighbouring tiles reuse two
  thirds of theirs). The first query in a new area computes the 3 × 3 tiles around it in parallel:
  1.3..2.2 s of wall time on this machine, once per 16 384 blocks travelled.
- Rivers of a basin: 16 ms on average (bank samples of the exact terrain every 16 blocks; room
  for meanders probed every 32 blocks).
- Per chunk, once the area's drainage is known (`hydrologyPreview --bench`, one thread): the node
  columns of the density 26..66 µs; the water columns of the two block steps 0.22 ms per chunk
  around a mountain river, 0.88 ms along a broad lowland river (`sampleWater` only looks at
  segments within reach of their water), almost nothing for chunks with no river or lake within
  reach (`mayHoldWater`). Rough walls add a few node lookups and two 3D value noises per cell
  corner on steep carved faces only.
- End to end (dedicated server, `generate.py`, 40 × 40 chunks around -200,2700, a
  broad river with tributaries; runs alternated with the branch before hydrology): 13.81 and
  13.81 chunks/s with hydrology, 13.73 and 13.78 without, so no measurable difference (vanilla's
  worldgen pipeline dominates). The smaller test worlds of Agent 3 (16 × 16 to 24 × 24 chunks,
  start-up included) generated at 6.3..9.4 chunks/s.
- Memory: up to 32 tiles (owned river cells, lakes, and the rivers of the basins used: about 105
  bytes per segment, ~1 MB per tile), 256 region indexes, 400 elevation tiles (6.4 MB), a
  4 096-entry node cache per worldgen thread.

Limitations (details and the full list: PROGRESS.md, Known Issues):
- Structures (villages) can stand across rivers and fill parts of their channels.
- Ground lower than the water beside 1.5% of the shore edges (model, test places): low levees of
  1..2 blocks or rocky rims in the block step.
- River valleys follow the 128-block drainage cells (meanders swing about them); no streams below the river
  threshold (vanilla springs only); no dry river beds in arid regions.
- River biomes only in channels 6+ blocks wide; lakes keep the surrounding biome.
- A basin reaching more than ~16 000 blocks beyond its tile would have no rivers (none seen).
- Any change of a hydrology constant changes generation (seams at rivers in existing worlds).

Tuning: drainage constants in `DrainageDomain` (cell size, tile and margin, river threshold, lake
depth, size and freeboard, runoff), river shape in `BasinRivers` (`widthFor`, depth, freeboard,
meanders in `meander` and `room`, the sea cut in `shape`, pools and falls in `levels`, river
lakes in `riverLakes`, lips, sills, plunge pools and boulders in `emit`, `floodplain`,
`wallSlope`, lake entry, cutoff), meander angles in
`HydrologyModel.meanderAngle`, bank and wall jitter in `HydrologyModel.sample`, the cross-section
in `HydrologyModel.profile`, rough walls in `VastlandsHydrology` (`ROUGHNESS`, `roughSurface`),
rims and foam in `RiverWater`, the river biome width in `BasinRivers.emit`.
Render with `hydrologyPreview` before generating worlds; `HydrologyModelTest` guards the properties.

Caves (Agent 4)

Underground space, the water and lava in it, and where it meets rivers and the sea:
`worldgen/caves`. `CaveModel` is pure Java like the terrain and hydrology models: a deterministic
function of (seed, x, y, z) that can be rendered without the game (`./gradlew cavePreview`).
`VastlandsCaves` (the `CaveSystem`) turns it into router density and the fluid of every open block;
`CaveBlocks` is a small block step. Vanilla's noise caves (cheese, spaghetti, noodles, pillars,
entrances) are gone from the Vastlands router and vanilla's carvers are not run in Vastlands worlds:
both ignore water levels, would cut river beds and open flooded caves into dry ones.

Integration:
- Density: `vastlands:caves` wraps the whole final density (per block, outside interpolation). It
  only lowers the density where the model carves cave space (to at most -0.45, 0.2 per block of
  signed distance, so walls are exact to the block), and raises it to 0.3 where the caves restore
  rock over a river gorge (roofs, below). Output range: the input's, widened to [-0.45, 0.3].
- Fluids: the Vastlands noise settings set `aquifers_enabled: false`, so noise filling uses
  `Aquifer.createDisabled(picker)`, which asks the fluid picker for every open block.
  `NoiseBasedChunkGeneratorMixin` wraps the `Supplier.get()` of vanilla's global picker in
  `createNoiseChunk` and `iterateNoiseColumn`: for a `RandomState` with a Vastlands context it
  returns `CaveSystem.fluidPicker(vanilla)`. `VastlandsCaves.fluidPicker` puts vanilla's sea above
  the ground (water up to Y 62, as the disabled aquifer's sea level would) and, below the ground,
  the fluid of the cave's domain up to its level, air above it. This replaces vanilla's lava below
  Y -54 and vanilla's aquifers in Vastlands worlds (other worlds keep vanilla's picker); base column
  queries (`iterateNoiseColumn`, structure placement) see the same fluids.
- Surface: `CaveSystem.surfaceHeight(x, z, ground)` is the ground raised where a roof restores a
  gorge, else `ground`; `GenerationSystems.surfaceHeight` (surface rules) uses it.
- Block step `CaveBlocks` (in `VastlandsChunkGenerator`): after noise filling, before the first
  river water pass, spillway notches are marked for post-processing and the rock around volcanic
  lava is lined; after the second river water pass (`applyCarvers`, which no longer runs carvers)
  the water of swallets is placed. Both read only the chunk's own blocks and the model.
- F3: a "Vastlands caves" line (rock, domains above and below the aquitard, geology fields).

Layers, coarse to fine (`CaveModel`):
1. Geology (`CaveGeology`), continuous 0..1 fields from the geography plus own noises: `karst`
   (a 5 200-block lithology field favoured on tablelands and hills and by wet climates, absent from
   crystalline range cores: dense multi-level networks, wide low tubes, chambers, dolines, canyon
   passages), `fracture` (mountain systems, young ranges most, and escarpments: chasms, tall
   rifts, shafts), `volcanic` (a 9 000-block hotspot field plus young ranges: higher deep lava,
   basalt), `abundance` (plains provinces quiet, a 2 400-block field leaves regions almost solid),
   `coast` (1 at the coastline, 0 beyond ~600 blocks), `wetness` (climate).
2. Fluid domains (`FluidDomains`): cells of a warped 2D Voronoi diagram in two zones. The upper
   zone (surface down to the aquitard, a cave-free rock band about 8 blocks thick around Y -66 with
   ±1.5 blocks of noise) has cells of ~320 blocks: sea domains (site at sea or below Y 67) are
   flooded to sea level (Y 62); inland domains hold a water table 8..38 blocks below their regional
   base level (15..85% of them, by wetness and karst) or are dry. The deep zone (aquitard to
   bedrock) has cells of ~448 blocks: lava with its top at Y -236..-150 (32% + 45% × volcanic of
   the domains; volcanic ones higher), ancient flooded caves with a level at Y -226..-110
   (16% × (1 - volcanic)), dry otherwise; under the open ocean 75% are flooded to sea level.
   In a domain every open block below its level is fluid and every one above it air: lakes and
   flooded passages always stand level, sea caves keep air pockets above Y 62.
3. Cave pieces:
   - Network (`CaveNetwork`): a jittered lattice of 64-block cells; every node exists on every
     level: seven upper levels at fixed offsets from the regional base level (-60 .. +180: plains
     get one to three, mountains up to seven stacked under their peaks), a coastal level around
     Y 57 near coasts (sea caves half under water) and three deep levels around Y -104, -152 and
     -200. Level heights undulate over ~320 blocks; each level's development follows its own
     1 300-block field. Tunnels join active neighbours (four lattice neighbours and one diagonal
     per quad: no crossings) as quadratic curves with a vertical bump and varying radius; karst
     gets wide low phreatic tubes, fractured rock tall rifts, some tunnels flat sediment floors,
     some upper ones a canyon slot cut into the floor. Junctions may be chambers (radius 8..29,
     deep 10..36, 8% of them halls ×1.8 long and low; elongated and lobed, walls in uneven terraces,
     ceilings hanging in breakdown lobes, rubble floors); nodes may
     hold a shaft to the next level below. The highest level of a node may open to the surface:
     an adit into a hillside or cliff (slope ≥ 0.28, downhill, 12% with a very large mouth), a sea
     cave (coastal nodes: rising seaward about 1 in 4 to meet the shore or sea floor, half or wholly
     under water), a doline in karst, or a blue hole (a flooded funnel in the sea floor).
   - Fractures (`Fractures`): joint sets along a strike that turns over ~7 000 blocks, cracks ~75
     blocks apart opened by a gate noise in stretches of tens to hundreds of blocks; lens-shaped
     sections up to ~4 blocks either side and ~120 blocks tall in mountains; a rare ravine set
     reaching the surface (V-shaped); a second set in the deep zone.
   - Enormous caverns (`Caverns`): at most one per 640-block cell (`giantCaverns` 0.32 × geology:
     about a third of the cells), mostly deep (centres Y -200..-130): radius 45..110, 20..52 high,
     a flattened dome over a rubble floor, its ceiling hanging in breakdown lobes, lobed outline,
     walls stepping back in uneven terraces (6-block bands whose edges wander up and down and
     whose step changes around the wall), pillars. Their fluid is the domain's: an underground
     lake, a lava lake or dry.
4. Column (`CaveColumn`, built once per column and cached per thread): the ground (the lowest
   ground within 4 blocks), both domains, the pieces whose bounds contain the column (up to 48),
   and the heights that must stay rock (up to 24 intervals):
   - cover: 6 blocks under the ground (2 under the sea floor in sea domains), except entrances;
   - dams: within 2.5..5 blocks of a Voronoi edge between two domains whose fluids differ at some
     height, rock over exactly those heights up to 2..3 blocks above the higher level, so flooded
     and dry caves, or water and lava, never touch and no water stands against air;
   - spillways: between two domains of the same fluid at levels 3+ blocks apart, windows of 3
     blocks every 20 along the edge, 30% of them open (`spillways`): the dam's crest drops below
     the higher level there and `CaveBlocks` marks the higher fluid in the notch for
     post-processing, so on the first tick it spills over and runs down into the lower domain as
     an underground fall (fed for ever by the sources of the higher domain, which vanilla never
     drains);
   - the aquitard band, closed where the zones above and below it differ;
   - clearance: wherever the cave fluid at a height differs from the fluid outside (the sea up to
     Y 62, river or lake water up to its top, air), the rock within 4 blocks of the lowest ground
     around (7 under rivers and lakes) stays solid: no dry cave under the sea floor, no flooded
     cave opening onto dry land, no cave under a river bed.
5. Block (`CaveModel.carve`): the minimum of the pieces' signed distances, chasms and caverns,
   plus wall roughness (3D noise whose wavelength grows with its amplitude, so walls bulge at the
   scale of the passage instead of shedding detached pockets, and a fine octave), chockstones
   wedged in chasms, then the solid intervals.

River connections (`RiverTunnels`; the hydrology is read only, through the `Rivers` interface on
`HydrologyModel.channels` and `falls`). The caves add rock only above the carved ground and never
below the top of any water near it, and always leave a vault over the water:
- Natural tunnels: where a narrow river (half width under ~20) runs in a gorge 24..40+ blocks
  deep and a 700-block gate noise along the river is open (`riverTunnels` 0.5), the gorge gets its
  rock back up to a share of the terrain before carving: the gorge narrows into a slot and closes
  into an arched tunnel; skylights where the rock would be thinner than 7 blocks.
- Natural bridges: about 1% of river segments in valleys 13+ blocks deep (no fall or lake within
  40 blocks): a rock fin 18..36 blocks thick from wall to wall, its crest rounded a little below
  the rim, ragged steep ends, a deck of 6+ blocks over an arched vault that flares towards both
  portals.
- Swallets (0.4% of segments, where the cave domain under the river is flooded 14+ blocks below
  it and no dam crosses): a shaft (radius 2.2) in the bank beside the channel, joined to the river
  by a notch one block wide at the water level. `CaveBlocks.afterRivers` fills the notch with a
  source and hangs a curtain in the shaft (a flowing block, amount 7, at the river's water top and
  falling water down to the cave water): the river pours into the flooded caves for ever, without
  draining, in the states vanilla computes for those blocks.
- Grottoes behind falls of 19+ blocks (70% × `riverTunnels` / 0.5 of them, where the cave domain
  is dry there and no dam is within 12 blocks): an elliptical room 6..12 blocks deep cut into the
  cliff under the lip, floor one block above the plunge pool, ceiling up to 9 blocks above it
  (15+ blocks below the upper water), its mouth 1.3× wider running through the rim to 3 blocks
  past the first pool column; from inside the curtain is a wall of falling water. The cliff face
  is found from the hydrology's water columns; the grotto keeps rock under and beside every
  column of curtain water (`RiverTunnels.underWater`).

Ocean connections: sea domains flood the caves near and under the sea to Y 62 (sea-connected
networks, flooded coastal karst, marine caverns where a cavern lies in such a domain), the coastal
level puts passages at the waterline, sea-cave adits open them into cliffs and the shore (often
several per network), and blue holes into the shelf floor (cover there is 2 blocks, so long
passages run just under the floor without opening except at their mouths). Measured in the model
(`cavePreview --sea`, 4 096² around 0,0): ~15 km of coastline, 65 sea-cave adits and 24 blue holes
with mouths at the sea, 84 of them open to the sea (5.5 per km), 0 unstable fluid blocks.

Lava: only in deep domains (below the aquitard; tops at most Y -150), lava falls only over
spillways between lava domains. Water and lava domains are separated by dams, so they never touch.
Around volcanic lava (`volcanic` > 0.25) `CaveBlocks` lines the stone touching lava with magma,
blackstone and basalt and the walls up to 8 blocks above it with basalt and smooth basalt;
elsewhere a little magma at the shore. Surface rules only repaint stone, so these stay.

Determinism and threads: everything is a pure function of the seed and the position. Nodes,
cells (64 blocks), domains, grottoes and swallets are cached per thread in direct-mapped caches
keyed by their exact coordinates (a hit returns what a miss would compute); columns in a 32 × 32
window per thread. Nothing reads chunks, so neighbouring chunks agree in any generation order
(`CaveModelTest.sameSeedSameCavesFromAnyThreadAndOrder`, seam checks of `cavecheck.py`).

Contract for later systems (`CaveSystem`, `VastlandsCaves`):
- `surfaceHeight(x, z, ground)`, `fluidPicker(vanilla)` (above).
- `isCave(x, y, z)`, `caveFluid(x, y, z)` (AIR, WATER, LAVA), `isRoof`, `isGrotto`,
  `fluidCode(x, y, z)`: what noise filling puts in a block; `describe` for F3. Columns are cached
  per thread: cheap for nearby queries.
- `entrances(...)`, `swalletsNear`, `grottoesNear`, `roofStrength`, `swalletPart`: for previews
  and tests.
- For forests: the ground keeps at least 6 blocks of rock over cave space except at entrances
  (adits, dolines, sea caves), ravines and the portals of river tunnels, so tree features only
  meet caves there.

Performance (this container, 4 vCPU, `cavePreview --bench`, one thread, 64 chunks around 0,0,
warm caches): cave columns 3.6 ms, density of all 768 × 256 blocks 3.1 ms, fluid picker 1.1 ms per
chunk (cold: 6.8, 5.9, 1.7 ms). The cost is per column (pieces gathered once) plus a few float
operations per piece per block; the density returns early for blocks already open (sky, sea) and
outside the height range of the column's pieces. End to end on a dedicated server (40 × 40 chunks
around -200,2700, alternated with `main`): 10.1 chunks/s with caves, 10.7 without (vanilla noise
caves, aquifers and carvers): about 6% slower.

Tuning: `CaveSettings.DEFAULT` (abundance, chambers, giant caverns, fractures, entrances, sea
caves, river tunnels, spillways, lava); level offsets, lattice and entrance rules in
`CaveNetwork`; domain sizes and fluid shares in `FluidDomains`; cover, clearance and dams in
`CaveModel`; bridges, swallets and grottoes in `RiverTunnels`. Any change changes generation
(seams against existing chunks). Render with `cavePreview` before generating worlds;
`CaveModelTest` guards the properties.

Limitations (details: PROGRESS.md, Known Issues): most network tunnels are short (64-block
lattice edges; long systems are chains of them); spillway falls are rare; vanilla features still
place lava and water springs and underground lava lakes (a few can meet cave water deep down);
ores and other features anchored to the world bottom keep vanilla's shifted ranges.

Vanilla Isolation

- Vanilla world types are untouched: only `vastlands:*` data is added, plus one entry appended to
  the `#minecraft:normal` world preset tag. A vanilla-preset server run showed the vanilla build
  limits (-64..319) and no Vastlands context.
- Shared vanilla content: the Vastlands overworld uses vanilla biomes, and vanilla biomes reference
  vanilla placed features and carvers. Changing a biome or a `minecraft:` feature (override JSON or
  biome modifier) changes vanilla worlds too. Later agents must scope such changes to Vastlands
  worlds, e.g. with a placement filter that checks for `VastlandsChunkGenerator`
  (`PlacementContext.generator()`), or by using Vastlands-specific biomes.
- Caves (Agent 4): carvers are skipped only by `VastlandsChunkGenerator` when its caves are
  `VastlandsCaves`; `NoiseBasedChunkGeneratorMixin` changes the fluid picker only for a
  `RandomState` with a Vastlands context (vanilla worlds, the Nether and the End keep vanilla's).
  The biomes' carver lists and features are untouched.

Testing and Verification

JUnit (`./gradlew test`, 69 tests):
- `WorldHeightDataTest` — data files agree with `WorldHeight` and pass vanilla's codecs.
- `GenerationSetupTest` — seeded randomness is deterministic and separated per system and seed;
  terrain, hydrology and caves are implemented, forests are passthrough.
- `StageDensityFunctionTest` — passthrough when unbound, delegation and bounds when bound,
  `fillArray`, lazy context creation, binding survives later visitors, codec round trip.
- `TerrainParameterFunctionTest` — unbound value and bounds, binding to the context's terrain, codec.
- `RandomStateBindingTest` — the mixin binds stages in a real `RandomState`; vanilla settings get no
  context; a passthrough stage keeps vanilla density exactly; the caves stage applies the bound
  cave system to the vanilla density.
- `TerrainModelTest` — same seed same terrain, seeds differ, thread-safe and order-independent,
  continuity (max neighbour step < 12 blocks, mean < 0.4 over 4 seeds), no discontinuities (no
  jump within 0.05 blocks), height distribution
  (sea 25..50%, lowland > 38%, above 200 0.5..8%, above 300 < 1.5% and rarer than above 200,
  max < 470, min > -200), climate parameters in range, underwater ⇔ ocean continentalness, the
  terrain itself never selects the river slice (weirdness).
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
  temperature and vegetation) equal the terrain, weirdness, depth and the initial density the hydrology's;
  final density solid at the bottom, air at the top and above the generated surface (channels
  included); the bottom slide is vanilla's under ordinary ground and the deepest trench found is
  open water down to its floor, solid below.
- `TerrainBiomeTest` (test server) — surface biomes from vanilla's table agree with the terrain
  (ocean ⇔ under water, mountain biomes above Y 300, river biomes only in hydrology channels:
  measured 548 of 90 000 samples) and with the climate (frozen oceans polar, deserts warm, jungles
  humid); the mean biome run along transects is more than twice vanilla's for the same seed
  (measured 539 vs 166 blocks; 593 before rivers).
- `HydrologyModelTest` — the same water from two models whatever order and thread computes tiles
  and regions; water never rises along a reach or across a confluence, every water top is
  `seaTop + 2k` (rivers and lakes); every river cell drains into a river, the sea, a terminal lake
  or ends in a lake; widest river 50..110 blocks (plunge pools aside), falls of 8+ blocks exist, the tallest at least
  30; at four places (a plateau fall, broad lowland rivers, lakes), no two water surfaces 1 block
  apart and ground below the water beside under 2% of the shore edges (measured 1.5%); off the rivers the terrain
  is unchanged (reshaped on 6.6% of random grid columns, never raised); `mayHoldWater` never says no
  for a box with river or lake water and skips at least a quarter of the boxes around the test
  places; level quantization.
- `RiverWaterTest` (test server) — 3 × 3 proto-chunks at three places and up to two falls near
  each (7 areas) are filled with the model's ground and sea-level water, get their river water,
  have bed and banks cut by a carver-like tunnel and get it again; no water block of the middle chunk (borders included) changes
  under vanilla's fluid rules (ported from `FlowingFluid`), every source above sea level is river or
  lake water of the model and all flowing water is a fall curtain.
- `CaveModelTest` — two cave systems of the same seed agree on 20 000 blocks (cave, roof, fluid)
  queried in a shuffled order from parallel threads; at seven places (land, sea, a lava region, a
  coast, a natural bridge, a swallet, a natural tunnel with a grotto) 96 × 96 columns over the
  full height hold no fluid that vanilla's rules would move (`CaveVolume.check`: water or lava
  beside or above cave air, water touching lava, sea or river water touching dry caves; designed
  spillway notches aside); no cave space or roof within the clearance under river and lake water
  (swallets aside) at three river places; the network entrances with their mouth at the sea in a
  1 024-block square of coast and shelf (sea-cave adits and blue holes, at least 10) hold their
  fluids and at least 80% open into the sea; no cave lava above Y -54; cave share of the rock 1.2..8% with over a fifth of the columns
  cave-free, chambers with few large ones, adits, enormous caverns in under half of the 640-block
  cells; the natural bridge and swallet near the origin's region exist and the swallet's shaft
  reaches the cave water.

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

Cave previews: `./gradlew cavePreview --args="--seed S --center X Z --size BLOCKS ..."`
(`CavePreview`): `--section` (default) writes sections along x and z through the centre (one pixel
per block, Y -256 at the bottom: rock shaded by depth, cave air black, cave water blue, lava orange,
sea and river water outside); `--plan N` a plan coloured by the height of the highest cave block;
`--slices Y,Y` top-down slices; `--stats` cave, water and lava shares per height band; `--check N`
the fluid check of `CaveModelTest` for an N × N box (also counts cave water open to the sea);
`--tunnels STEP` lists natural tunnels, bridges, swallets and grottoes of the square; `--roofs` maps
them (roofed river columns, vaults, skylights) at one block per pixel; `--sea` lists
every network entrance with its mouth at the sea, checks each block by block and gives sea caves
per km of coastline; `--probe X Y Z` prints the domains, solids and blocks around a point;
`--order X Y Z` the same block from fresh systems after different query histories; `--bench`
times the cave stage per chunk as noise filling calls it.

River previews: `./gradlew hydrologyPreview --args="--seed S --center X Z --size BLOCKS --scale
BLOCKS_PER_PIXEL --out PREFIX"` (`HydrologyPreview`) renders the carved relief with rivers and lakes
(blue), frozen water (pale: snowy or frozen biomes by vanilla's table), the sea and falls of 8+
blocks (white), and prints the drainage time, the tallest falls and the widest rivers of the square
and the work done (tiles, basins, segments). `--probe X Z` prints the columns and segments around a
point; `--water FILE` exports the expected water of a square for `rivercheck.py --expected`.

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
- `rivercheck.py run/NAME [--expected FILE] [--png FILE]` checks the water above sea level of a
  generated world: source, lip, falling and other flowing water; blocks that vanilla's fluid tick
  would change (rules of `FlowingFluid`), at rivers and lakes vs elsewhere (vanilla springs and cave
  water), inside chunks vs on chunk borders; with `--expected` (`./gradlew hydrologyPreview
  --args="--center CX CZ --size N --water FILE"` for the world's square: centre = `block_origin` +
  N/2) the agreement of water tops and ground with the model; river biome cells at the surface.
- `cavecheck.py run/NAME [--png FILE] [--section x=N|z=N]` (needs numpy; PNGs need Pillow) reads
  every block of a world: the composition of the underground (below each column's highest solid
  block) per 32-block band (cave air, water, lava), water and lava sources that vanilla's fluid
  tick would change (the rules of `FlowingFluid`, ported) in caves and at the surface, inside chunks
  vs on chunk borders, water touching lava, flowing water and lava (falls, springs, spillways) and
  the flowing blocks a tick would change, lava above Y -54, open/solid changes between neighbours
  across vs inside chunk borders (seams), columns open to the sky, cave mouths (underground air
  beside open air) and flooded openings (underground water beside open water); plans and sections.
- `blockprobe.py run/NAME X Y Z [--radius R] [--height H]` prints the blocks around a position
  layer by layer, fluids with their level (W0 source, w7 flowing, w8 falling).
- `screenshot.py run/NAME --out DIR --shot "name:X Y Z YAW PITCH" ...` copies the world to
  `run/saves`, enables commands, starts the dev client under Xvfb with `--quickPlaySingleplayer`,
  and for each shot teleports (spectator), waits for chunks to render and saves an F2 screenshot.
- Vanilla worldgen itself is not bit-for-bit reproducible between runs: features that spill across
  chunk borders (trees, ore blobs) depend on generation order. Compare Vastlands-vs-Vastlands
  differences against a vanilla-vs-vanilla baseline with the same seed.

Technical Limitations (verified)

- Lava below Y=-54 (fixed by Agent 4): vanilla's global fluid picker
  (`NoiseBasedChunkGenerator.createFluidPicker`, private static) hardcodes lava at Y < -54. In
  Vastlands worlds aquifers are disabled and `NoiseBasedChunkGeneratorMixin` replaces the picker
  with the caves' (see Caves): lava only in deep lava domains, sea water in trenches and caves under
  the sea. `OceanSurface.fillDeepWater` still runs as a safety net. Vanilla's lava features
  (`spring_lava`, underground lava lakes) still place small amounts of lava at any depth.
- Features and carvers anchored to the world bottom or top (`above_bottom` / `below_top`) move with
  the new limits: diamonds (vanilla -64..16) now generate at -256..-176, lower redstone at
  -256..-224, tuff, geodes, deep monster rooms, lower fossils, gravel and lava springs shift too
  (vanilla's cave carvers are no longer run in Vastlands worlds). Not changed by Agent 4: ores and
  underground features are biome features shared with vanilla worlds (isolation rule above).
- Hydrology: see Hydrology, Limitations.
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

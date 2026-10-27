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
4. Caves — large underground caverns, tunnels, vertical shafts, and diverse cave formations. (Done: Agent 4; overhauled by Agent 4.5: passage widths, density, galleries, megacaves, cave biomes.)
5. Forests — large forests, tree diversity, giant trees, and natural vegetation distribution. (Done: Agent 5.)
6. Natural wonders — natural formations at the surface and in the caves, moss, mushrooms, deadwood. (Done: Agent 6.)
7. Integration — Sincerelyuq's Biomes and Sincerelyuq's Overhaul as optional parts of one world. (Done: Agent 7.)

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
  registry/VastlandsRegistries.java   DeferredRegisters: chunk generator, density function types, biome source,
                                      the forest feature and its placement filter, the formations feature
  mixin/RandomStateMixin.java         binds Vastlands density functions to the world seed, registers the climate sampler
  mixin/SurfaceRulesContextMixin.java surface rules use the exact terrain surface (Vastlands worlds only)
  mixin/ServerLevelMixin.java         weather does not freeze the cracks of ice fields (Vastlands worlds only)
  mixin/NoiseBasedChunkGeneratorMixin.java  noise filling uses the caves' fluid picker (Vastlands worlds only)
  mixin/PlacedFeatureMixin.java       vanilla tree features skip Vastlands forests (Vastlands worlds only); PlacedFeatureAccessor
  mixin/ClimateRTreeMixin.java        exact climate searches during the forests' biome lookups (ExactClimateSearch)
  mixin/ChunkGeneratorMixin.java      site checks of other mods' structure starts (Vastlands worlds only, Agent 7)
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
    caves/                            caves, cave water and lava, cave biome slots: CaveModel (pure Java:
                                      CaveGeology, FluidDomains, CaveNetwork, Galleries, Megacaves, CavePrimitive,
                                      Fractures, RiverTunnels, CaveColumn, CaveBiomes, KeyedCache, Noise3),
                                      VastlandsCaves (CaveSystem), CaveBlocks (block step), CaveVolume (model-side
                                      fluid checks), CaveSettings, CaveFluid, CaveBiome, MegacaveInfo
    biome/VastlandsBiomeSource.java   "vastlands:caves" biome source: surface delegate + cave biome slots,
                                      regional surface biomes and the luminous list (Agent 7)
    biome/RegionalBiomes.java, DuneField.java  regions of other mods' surface biomes; the dune sea's dunes
    compat/                           Agent 7: ModBlock, CompatBlocks (other mods' blocks by id), IntegrationSurface
                                      (surface rules), IntegrationFeatures (placement filters), AddBiomesModifier
                                      (structure modifier), StructureSites (site checks)
    biome/GrassColors.java, BiomeColors.java  the client's grass colours on the server; how well moss blends in
    forest/                           forests: ForestModel (pure Java: ForestType, TreeForm, Wood, TreeSpec, TreeShapes,
                                      TreeShape, TreeBlocks, ForestSample, ForestSettings, ForestCache, TreeRandom),
                                      VastlandsForests (ForestSystem), ForestFeature and ForestBlocks (the block step),
                                      ForestBiomes, VanillaTrees, ExactClimateSearch
    nature/                           natural formations: NatureModel (pure Java: FormationKind, FormationSpec,
                                      FormationShapes, FormationShape, FormationBlocks, Rock, SurfaceStyle,
                                      CaveProfile, NatureSettings, NatureCache, NatureRandom), VastlandsNature
                                      (NatureSystem), NatureFeature and NatureBlocks (the block step), NatureBiomes,
                                      DuneSand (the dunes' block step)
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
  data/vastlands/tags/worldgen/biome/cave/                 cave biome slots: lush, dripstone, deep_dark, crystal,
                                                           volcanic, other (optional entries for other mods)
  data/vastlands/tags/worldgen/biome/forest/               forest communities (temperate .. dry), transparent, all
  data/vastlands/tags/worldgen/placed_feature/forest/      displaced: vanilla features that give way to the forests;
                                                           buried: underground lava lakes kept from under them
  data/vastlands/worldgen/{configured,placed}_feature/     forest.json: the vastlands:forest feature
  data/vastlands/neoforge/biome_modifier/forest.json       adds it to the vegetal decoration of #vastlands:forest/all
  data/vastlands/tags/worldgen/biome/nature/               surface/<style>, cave/<profile>, all
  data/vastlands/worldgen/{configured,placed}_feature/     surface_formations.json, cave_formations.json
  data/vastlands/neoforge/biome_modifier/nature.json       adds both to the local modifications of #vastlands:nature/all
  data/vastlands/tags/worldgen/biome/region/               Agent 7: regional biomes (painted_range .. dusk_wood, keep)
  data/vastlands/tags/worldgen/biome/overhaul/,            Agent 7: Overhaul structures extended to the new biomes, and
    tags/worldgen/structure/overhaul/, neoforge/             the vastlands:add_biomes modifiers that do it
    structure_modifier/overhaul_*.json
  data/vastlands/tags/worldgen/structure/site/             Agent 7: site checks (level_ground, dry_ground, sealed,
                                                           apart_from_sanctum)
  data/vastlands/tags/worldgen/placed_feature/integration/ Agent 7: other mods' features replaced, guarded, level_ground
src/test/java/...                                          JUnit tests (see Testing)
src/test/java/.../tools/TerrainPreview.java                ./gradlew terrainPreview: terrain, landform, biome, climate
                                                           and ice maps without the game
src/test/java/.../tools/HydrologyPreview.java              ./gradlew hydrologyPreview: rivers, lakes and falls over the
                                                           carved relief; expected water export for rivercheck.py
src/test/java/.../tools/CavePreview.java                   ./gradlew cavePreview: cave sections, plans, slices, stats,
                                                           fluid checks, river tunnels, sea caves, places, bench
src/test/java/.../tools/CaveSurvey.java                    ./gradlew caveSurvey: passage widths, cave share by depth,
                                                           wide spaces, connectivity, water bodies, cave biomes
src/test/java/.../tools/ForestPreview.java                 ./gradlew forestPreview: tree gallery, isometric views, aerial
                                                           maps, forest statistics, places, order check, bench
src/test/java/.../tools/NaturePreview.java                 ./gradlew naturePreview: formation gallery, isometric views of
                                                           the surface and of caves, statistics, find, bench, order check
tools/worldcheck/                                          world generation, region analysis, heightmaps, oceans, rivers,
                                                           caves (cavecheck.py, blockprobe.py), biomes (biomecheck.py),
                                                           forests (forestcheck.py), formations (naturecheck.py),
                                                           screenshots
docs/terrain/, docs/geography/, docs/hydrology/,           previews, region maps and in-game screenshots
docs/caves/, docs/forests/, docs/nature/
```

World Preset and Height

The Vastlands world type is `vastlands:vastlands`. It is a copy of vanilla's `minecraft:normal`
preset with only the overworld replaced; the Nether and the End are vanilla.

- Overworld dimension type `vastlands:overworld`: vanilla overworld values with
  `min_y = -256`, `height = 768`, `logical_height = 768`.
- Generator `vastlands:noise` with `biome_source = vastlands:caves` (since Agent 4.5: `surface` is
  `minecraft:multi_noise / preset minecraft:overworld`, the cave slots are the tags
  `#vastlands:cave/*`; see Caves) and `settings = vastlands:overworld`. Worlds created before keep the
  biome source saved in their `level.dat`.
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
biomes: vastlands:caves ← surface delegate (multi_noise) + VastlandsCaves.caveBiome below the ground
NoiseChunk (aquifers off; fluids from CaveSystem.fluidPicker) → [trench water, cave step, river water]
        → surface rules → (no vanilla carvers) [trench water, river water, swallets] → features → [ice fields]
                           vastlands:surface_formations, cave_formations ┘ │ └ vastlands:forest (end of vegetal
                           (local modifications: NatureSystem)              │   decoration: ForestSystem via
                                                  vanilla tree features give way   GenerationContext.of(level))
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
- Forests are not part of the router: the feature `vastlands:forest` reaches its system through
  `GenerationContext.of(ServerLevel)` and builds each chunk's part of every tree that reaches it
  (see Forests). The natural formations (`vastlands:formations`) work the same way (see Nature).
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
`VastlandsHydrology`, caves are `VastlandsCaves`, natural formations are `VastlandsNature`, forests
are `VastlandsForests` (`Passthrough` remains for tests).

| System | Interface | Kind | May depend on | Owner |
|---|---|---|---|---|
| Terrain | `TerrainSystem` | `DensityStage` | — | Agent 2 |
| Hydrology | `HydrologySystem` | `DensityStage` | Terrain | Agent 3 |
| Caves | `CaveSystem` | `DensityStage` | Terrain, Hydrology | Agent 4, 4.5 |
| Nature | `NatureSystem` | feature `vastlands:formations` | Terrain, Hydrology, Caves | Agent 6 |
| Forests | `ForestSystem` | feature `vastlands:forest` | Terrain, Hydrology, Caves, Nature | Agent 5, 6 |

Systems are created in that order and receive only earlier systems. The forests depend on the
natural formations only to keep trees off them; nothing depends on the forests.

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
  columns cached per thread. Since Agent 4.5: `caveBiome(x, y, z)` (cave biome slot) and
  `megacaves(...)` (`MegacaveInfo` for natural wonders).
- Biomes: `VastlandsBiomeSource` (`vastlands:caves`) finds the caves through
  `GenerationContext.of(Climate.Sampler)`.

ForestSystem (contract for later systems; details in Forests below):
- `double treeDensity(x, z)`: the share of the tree lattice holding a tree (0..1).
- `ForestSample forestAt(x, z)`: the community, its share of the neighbourhood, density, stand age,
  clearing, riparian, treeline, exposure, slope, temperature, moisture, old growth.
- `trees(minX, minZ, maxX, maxZ, consumer)`: every tree, shrub and fallen log whose blocks can reach
  a box (`TreeSpec`: root, form, wood, height, crown, community), in a fixed order; their blocks
  through `VastlandsForests.model().shape(spec)` (`TreeShape`).
- `VastlandsForests.bind(owner, biomes)` before use (the feature binds the world's biome source);
  `replacesVanillaTrees(category, x, z)`, `describe` for F3.

NatureSystem (contract for later systems; details in Nature below):
- `formations(minX, minZ, maxX, maxZ, surface, consumer)`: every surface or cave formation whose
  blocks can reach a box (`FormationSpec`: kind, root, height, radius, span, rock, flags), in a
  fixed order; their blocks through `VastlandsNature.model().shape(spec, model().world())`.
- `clearOfFormations(x, z, trunk, crown)`: false where a surface formation keeps a tree (or
  anything else) away.
- `VastlandsNature.bind(owner, biomes)` before use (`NatureBiomes.nature(level, generator)` binds
  the world's biome source); `describe` for F3.

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

Caves (Agent 4, overhauled by Agent 4.5)

Underground space, the water and lava in it, the cave biomes, and where the caves meet rivers and
the sea: `worldgen/caves` and `worldgen/biome`. `CaveModel` is pure Java like the terrain and
hydrology models: a deterministic function of (seed, x, y, z) that can be rendered and measured
without the game (`./gradlew cavePreview`, `./gradlew caveSurvey`). `VastlandsCaves` (the
`CaveSystem`) turns it into router density, the fluid of every open block and the cave biome slot
of every block; `CaveBlocks` is a small block step; `VastlandsBiomeSource` turns cave slots into
biomes. Vanilla's noise caves (cheese, spaghetti, noodles, pillars, entrances) are gone from the
Vastlands router and vanilla's carvers are not run in Vastlands worlds: both ignore water levels,
would cut river beds and open flooded caves into dry ones.

Why Agent 4.5 rewrote the geometry (measured with `caveSurvey` on Agent 4's code, 10 squares of
768 blocks, 3 seeds; see PROGRESS.md for the tables): network tunnels had a radius of
(2.5 + 2.2 karst + 0.8 fracture) × 0.7..1.3, so the volume-weighted median width was 7..10
blocks and above Y -60 no passage was wider than ~50; there was no passage class above that (the
64-block lattice was the only network); "enormous caverns" were 45..110 in radius, almost all in
the deep zone below the aquitard, and on horizontal slices no space anywhere reached 120 blocks;
plains and quiet regions left 1% of the rock open; and cave biomes came from vanilla's table on
the depth parameter Agent 2 holds at 0.95 below ~120 blocks, so below that every cave had the
surface biome (dark forest at Y -150), dripstone appeared only where continentalness ≥ 0.8 (then
almost everywhere: 88% of cave blocks in one square), the deep dark only in vanilla's Y -64..0
band under low erosion.

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
- Biomes (Agent 4.5): the overworld's biome source is `vastlands:caves` (`VastlandsBiomeSource`,
  registered in `VastlandsRegistries.BIOME_SOURCES`): `surface` is the delegate (vanilla's
  `multi_noise` overworld preset on the terrain's climate parameters, as before) and six slots
  (`lush`, `dripstone`, `deep_dark`, `crystal`, `volcanic`, `other`) are biome lists, in the preset
  the tags `#vastlands:cave/<slot>` (`data/vastlands/tags/worldgen/biome/cave/`). Within 16 blocks
  of the ground the delegate decides as before; below, `VastlandsCaves.caveBiome(x, y, z)` gives
  the slot; a slot with biomes returns one of them (several share a slot by 512-block regions), an
  empty slot falls back (crystal to dripstone, the others to plain rock), and plain rock returns the
  delegate's biome at the surface above (vanilla's own underground choice is no longer used). The cave system is found through the
  climate sampler: `RandomStateMixin` registers each Vastlands `RandomState`'s sampler with its
  context (`GenerationContext.bind`, a weak identity map); `GenerationContext.of(Climate.Sampler)`
  looks it up. A sampler without a context (another world using the source) gets the delegate.
  `possibleBiomes` is the delegate's plus every slot's (feature sorting and `/locate` see them).
  The slots are resolved on first use (tags are bound after dimensions are decoded) and logged:
  `Vastlands cave biomes: lush [minecraft:lush_caves], ...`.
- Other mods' cave biomes: optional tag entries (`{"id": "...", "required": false}`) are present
  when the mod is installed and skipped otherwise, so there is no dependency either way.
  `crystal.json` and `volcanic.json` list the amethyst and smoldering caves of Sincerelyuq's Biomes
  under several likely ids (`sincerelyuqs_biomes:amethyst_caves` and variants; NOT verified
  against the mod's jar, which was not available): the one id to correct if the mod uses another.
  Vastlands adds no amethyst biome of its own (Sincerelyuq's Biomes has one). Any mod or datapack
  can add biomes to `#vastlands:cave/other` (or any slot).
- Surface: `CaveSystem.surfaceHeight(x, z, ground)` is the ground raised where a roof restores a
  gorge, else `ground`; `GenerationSystems.surfaceHeight` (surface rules) uses it.
- Block step `CaveBlocks` (in `VastlandsChunkGenerator`): after noise filling, before the first
  river water pass, spillway notches are marked for post-processing and the rock around volcanic
  lava is lined; after the second river water pass (`applyCarvers`, which no longer runs carvers)
  the water of swallets is placed. Both read only the chunk's own blocks and the model.
- F3: a "Vastlands caves" line (rock, domains above and below the aquitard, geology fields) and a
  "Vastlands cave biome" line (the slot of the block).

Layers, coarse to fine (`CaveModel`):
1. Geology (`CaveGeology`), continuous fields from the geography plus own noises: `karst` (a
   5 200-block lithology field favoured on tablelands and hills and by wet climates, absent from
   crystalline range cores), `fracture` (mountain systems, young ranges most, and escarpments),
   `volcanic` (a 9 000-block hotspot field plus young ranges), and (Agent 4.5) the cave regime of
   the region: `density` (0..~2: (0.55 + 0.55 karst + 0.25 fracture) × (1 - 0.15 plains) × a
   2 600-block regime field from 0.25 (nearly solid regions) to 1.5 (labyrinths), its contrast set
   by `regionVariation`), `scale` (0.55..1.85: a 3 400-block field of regions of large passages
   against narrow ones, larger in karst) and `vertical` (0..1: fractured rock plus a 2 000-block
   field); `coast` (1 at the coastline, 0 beyond ~600 blocks), `wetness` (climate).
2. Fluid domains (`FluidDomains`): cells of a warped 2D Voronoi diagram in two zones. The upper
   zone (surface down to the aquitard, a cave-free rock band about 8 blocks thick around Y -66 with
   ±1.5 blocks of noise) has cells of ~320 blocks: sea domains (site at sea or below Y 67) are
   flooded to sea level (Y 62); inland domains hold a water table 8..38 blocks below their regional
   base level ((15..85% + karst) × `water` of them, by wetness) or are dry. The deep zone (aquitard
   to bedrock) has cells of ~448 blocks: lava with its top at Y -236..-150 ((32% + 45% × volcanic)
   × `lava`), ancient flooded caves with a level at Y -226..-110 (20% × (1 - volcanic) × `water`),
   dry otherwise; under the open ocean 75% are flooded to sea level. Megacaves (below) are domains
   of their own over their footprint (1.15 × their outline): `FluidDomains.locate` returns the
   megacave's domain inside it and reports the footprint's edge as a domain edge (dams, spillways)
   on both sides; `voronoi(x, z, deep)` is the plain Voronoi domain. In a domain every open block
   below its level is fluid and every one above it air: lakes and flooded passages always stand
   level, sea caves keep air pockets above Y 62.
3. Cave pieces (signed-distance primitives, `CavePrimitive`: capsule segments with elliptical
   sections, canyon slots, flat floors and funnels; lobed, terraced chambers):
   - Network (`CaveNetwork`): a jittered lattice of 64-block cells; every node exists on every
     level: nine upper levels at fixed offsets from the regional base level (-116 .. +180: plains
     get two to four above the aquitard, mountains up to nine stacked under their peaks), a coastal
     level around Y 57 near coasts (sea caves half under water) and three deep levels around Y -104,
     -152 and -200. Level heights undulate over ~320 blocks; each level's development follows its
     own 1 300-block field. A node is active on a level with probability (0.12 + 0.75 √density ×
     (0.45 + 0.55 development)) × `frequency`; tunnels join active neighbours (four lattice
     neighbours and one diagonal per quad: no crossings) with probability 0.22 + 0.5 density ×
     `density`, as quadratic curves with a vertical bump. Radius (Agent 4.5): log-normal,
     base × scale × `passageWidth` × e^(0.4 g) with g ~ N(0, 1) (a sum of three uniforms), base
     3.8 + 2.0 karst + 0.7 fracture (deep: 4.4 + 2.0 volcanic + 1.2 karst), one in eight a squeeze
     at 0.45 ×, clamped 1.6..16: most tunnels 6..16 blocks wide, a tail to ~30. Karst gets wide low
     phreatic tubes, fractured rock tall rifts (height over width 0.6..1.25 × 0.85..1.15, wide ones
     ×0.85), large ones flat breakdown floors (55%), some upper ones a canyon slot. Ramps (new): a
     steep passage from a node's level down to the next active level of a neighbour, 10..80 blocks
     lower (16% × `vertical` × (0.35 + vertical) of edge-levels). Junctions of two or more tunnels
     may be chambers (radius (10..36) × scale, deep (12..42) × scale, 10% halls ×1.8, at most 58),
     junctions of three or more are otherwise widened into a room 1.45 × the widest tunnel + 3..6
     (half of them). Nodes may hold a shaft or pit (radius 1.8..9, log-normal; 12% + 15% karst
     flaring to 1.8..3 × at the top) to the next level below, a quarter past it. The highest level
     of a node may open to the surface: an adit (radius 3..6.5, 15% 9..18), a sea cave mouth
     (3.5..7.5, 30% 8..17), a doline (3..8) or a blue hole.
   - Galleries (`Galleries`, new): trunk passages on their own jittered lattice of 288-block cells
     and two levels: upper, floor 18..48 below the regional base level; deep, floor Y -200..-130.
     Node share (0.25 + 0.35 density) × `frequency` × `largePassages` × a 1 700-block development
     field; links 0.35 + 0.25 density. Size classes: large passages (radius 12..30: 25..60 wide,
     73%), major corridors (30..55: 60..110, 24%) and colossal ones (55..85, 3% × `largePassages`),
     × √scale × `passageWidth`. A gallery is a Catmull-Rom spline through two meander points
     (±22% of the edge), nine segments, its radius set at each control point (0.55..1.5 ×, and a
     constriction to 0.3..0.45 × at one control point in a third of them), so it alternates between
     narrow connections and vast spaces. Phreatic galleries are wide and low (height over width
     0.42..0.7) with flat floors (floor cut 0.35..0.65); in fractured rock some upper ones are
     canyons (radius 0.4 ×, 1.6..2.9 times as tall as wide). The floor is shared at a node, so
     galleries meeting there join floor to floor; a gallery narrows wherever its vault would come
     within 12 blocks of the rough ground across its width. Junctions of three or more galleries
     are vaulted halls (1.35 × the widest + 6). Underground rivers: where the upper Voronoi domain
     at a node is wet (not the sea) and its water table within reach, 60% of nodes set their floor
     2..6 blocks under the water; a gallery between two such nodes is a river (a still-water channel
     under an open vault, `CavePrimitive.river`) and every dam it crosses is notched across its
     width, so it falls over to the next level (an underground waterfall). Upper gallery nodes on
     steep ground (slope ≥ 0.3, 22% × `entrances`) open through a mouth of radius 8..22.
   - Fractures (`Fractures`): joint sets along a strike that turns over ~7 000 blocks, cracks ~75
     blocks apart opened by a gate noise (more of them where `vertical` is high); lens-shaped
     sections up to ~6 blocks either side (Agent 4: 4) and ~120 blocks tall in mountains; a rare
     ravine set reaching the surface (V-shaped); a second set in the deep zone. Since Agent 4.5
     each joint frame is anchored at a corner of a 2 400-block grid (its own strike and line
     pattern; the four frames around a column blended by distance weights, so sets fade into each
     other and cross where they meet): Agent 4 rotated the stretched noise about the world's
     origin, which shifted it by |r| × the strike's turn per block, so far from the origin
     (r ≳ 5 000) the cracks broke up into clouds of single-block holes.
   - Megacaves (`Megacaves`, new; replaces Agent 4's `Caverns`): at most one per 1 024-block cell
     in each zone (the deep grid offset by 512, so megacaves stack in places), chance (75% upper,
     55% deep) × `megacaves` × (0.35 + 0.95 × a 4 200-block cluster field) × (0.55 + 0.35 density).
     Radius (70 + 280 u^1.8) × `megacaveScale` (median ~150, p90 ~300; long axis up to 2 × that
     with elongation 0.1..0.4), limited so the bounds stay within 0.47 of the distance to every
     neighbouring cell's centre (no overlaps; each column needs the 3 × 3 cells around it). Kinds:
     hall, lake (basin and islands), canyon (elongation 0.6..0.75, up to 220 tall, V floor,
     bridges), abyss (radius 50..120, 110..300 tall, sheer walls for the lower half closing into a
     bell-shaped vault, shelf, bridges), complex (a ragged shelf with holes halfway up: two floors;
     partitions) and pillars (a forest of columns, stubs and pendants), chosen from the room above
     and below and the rock. Height = a share of the radius (halls 0.32..0.52, canyons 1.0, abysses
     2.5) within the room between the aquitard (+18) or Y -236 and the rough ground at the centre
     (−24) or the aquitard (−26); then, ring by ring (0.35, 0.7, 0.95 of the outline, 12
     directions each), the vault's dome profile there (plus 20% for cupolas) must stay under that
     ring's lowest rough ground (−24): tall vaults under peaks, low ones where valleys cross the rim
     (abysses up to ~175 and complexes ~135 blocks tall under mountains in the measured seeds). Per
     column (`Megacaves.Section`): a lobed, elongated outline (q = 1 at the wall) whose walls step
     back in uneven 6-block terraces and recede by up to a fifth at heights picked by a 3D noise
     (~80 blocks along the wall, ~40 in height: alcoves and overhangs; only inwards, so the cave
     stays inside its fluid domain's footprint); a floor (superellipse of power 2.6..4: flat, with
     talus rising to the walls; hills of 0.15 h, a basin and islands in lakes, soft 5-block
     benches, rubble and boulders) and a vault (a dome of power 1.7..2.4 over the floor, exponent
     0.55..0.9: its sides slope in; abysses a bell: walls sheer to 0.45..0.6 h, then a round dome;
     hanging lobes, ribs and cupolas ±0.2 h); slope-corrected distances so the wall noise acts
     across steep walls too; pillars on a jittered grid (R/5, 24..60; 80% of sites in pillar
     caverns, 25% in halls, 20% in lakes; 68% full columns flared at both ends, 16% stalagmite
     stubs, 16% pendants); partition walls along a warped sub-cell Voronoi (0.55 R) with gaps, 35..100% of the
     height; shelves; natural bridges (decks 4..7 thick, thicker towards the walls). Extras: 3..5
     connector passages (radius 4..10, out to 65..130 blocks past the wall, at the floor and
     halfway up: several entrances and exits, openings high on the walls) and, for 30% ×
     `entrances` of upper megacaves within ~110 blocks of the surface, a cenote (a collapse shaft
     of radius 7..22 to the sky). Its fluid domain: the sea under the sea; else the water table of
     the Voronoi domain at its centre when that lies inside it; else a lake of its own (LAKE 90%,
     ABYSS 60%, others 22% + 35% wetness, × `water`) up to 4..34% of the height (6%: 85%, a
     submerged cavern); deep volcanic ones lava lakes (top ≤ Y -100); dry otherwise.
4. Column (`CaveColumn`, built once per column and cached per thread): the ground (the lowest
   ground within 4 blocks), both domains, the pieces that can reach the column (up to 64: bounds
   and the horizontal distance to their axis; indexed by 16-block slabs), both megacave sections,
   and the heights that must stay rock (up to 24 intervals):
   - cover: 6 blocks under the ground (2 under the sea floor in sea domains), except entrances;
   - dams: within 2.5..5 blocks of a domain edge (Voronoi or megacave footprint) between two
     domains whose fluids differ at some height, rock over exactly those heights up to 2..3 blocks
     above the higher level, so flooded and dry caves, or water and lava, never touch and no water
     stands against air;
   - spillways: between two domains of the same fluid at levels 3+ blocks apart, windows of 3
     blocks every 20 along the edge, 30% of them open (`spillways`), and every column of an
     underground river gallery: the dam's crest drops below the higher level there and
     `CaveBlocks` marks the higher fluid in the notch for post-processing, so on the first tick it
     spills over and runs down into the lower domain as an underground fall;
   - the aquitard band, closed where the zones above and below it differ;
   - clearance: wherever the cave fluid at a height differs from the fluid outside (the sea up to
     Y 62, river or lake water up to its top, air), the rock within 4 blocks of the lowest ground
     around (7 under rivers and lakes) stays solid: no dry cave under the sea floor, no flooded
     cave opening onto dry land, no cave under a river bed.
5. Block (`CaveModel.carve`): the minimum of the pieces' signed distances, chasms and megacave
   sections, plus wall roughness (3D noise whose wavelength grows with its amplitude, so walls
   bulge at the scale of the passage; megacaves 2.8 + 0.008 R), chockstones wedged in chasms, then
   the solid intervals. Blocks deeper inside a cave than any wall noise reaches skip the noise.

Cave biomes (`CaveBiomes`, `CaveBiome`, new): provinces of a warped 2D Voronoi diagram per layer:
shallow (16 to ~90 ±15 blocks under the ground, cells ~420), middle (down to the aquitard, ~520)
and deep (below it, ~600). `caveBiomes` (0.62) of the provinces hold a cave biome, chosen from the
climate and rock at the province's site: shallow: lush caves (land, temperature above about -0.3,
weight 0.25 + 2 wetness), dripstone (0.35 + 0.4 karst + 0.2 fracture), crystal, other; middle:
dripstone, lush caves (wetter), the deep dark (land, (0.12 + 0.5 mountain) × (1 - volcanic)),
crystal, volcanic; deep: dripstone, the deep dark, crystal, volcanic (0.12 + 1.4 volcanic). The deep
dark of provinces keeps to Y -150..0 (it holds Y -27 for ancient cities; its sculk features cost
time in proportion to its volume). Megacaves carry a theme over their whole volume (upper: lush if
wet and mild, dripstone, crystal; deep: volcanic for lava ones, the deep dark, dripstone, crystal),
else their province's. The slot lookup needs no cave column (ground, aquitard, megacave footprint,
province), so random biome queries (structure checks, `/locate`) stay cheap.

River connections (`RiverTunnels`; the hydrology is read only, through the `Rivers` interface on
`HydrologyModel.channels` and `falls`). Unchanged by Agent 4.5. The caves add rock only above the
carved ground and never below the top of any water near it, and always leave a vault over the
water:
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
networks, flooded coastal karst, megacaves in such a domain are part of the sea: submerged
caverns under the shelf), the coastal level puts passages at the waterline, sea-cave adits (30%
now large: radius 8..17) open them into cliffs and the shore, and blue holes into the shelf floor
(cover there is 2 blocks, so long passages run just under the floor without opening except at
their mouths). Measured in the model (`CaveModelTest.seaCavesAndDeepLava`, 1 024-block square of
coast and shelf): 31 sea caves with a mouth at the sea (13 blue holes), all 31 open to the sea and
stable (Agent 4: 23, 8, 23).

Lava: only in deep domains and deep megacaves (below the aquitard; tops at most Y -100), lava
falls only over spillways between lava domains. Water and lava domains are separated by dams, so
they never touch. Around volcanic lava (`volcanic` > 0.25) `CaveBlocks` lines the stone touching
lava with magma, blackstone and basalt and the walls up to 8 blocks above it with basalt and smooth
basalt; elsewhere a little magma at the shore. Surface rules only repaint stone, so these stay.

Determinism and threads: everything is a pure function of the seed and the position. Nodes,
cells, megacaves, provinces and domains are cached per thread, keyed by their exact coordinates;
lattice caches are `KeyedCache` (open addressing over four slots: a direct-mapped cache let two
megacaves sharing a slot rebuild each other thousands of times); columns in a 32 × 32 window per
thread. A hit returns what a miss would compute. Nothing reads chunks, so neighbouring chunks agree
in any generation order (`CaveModelTest.sameSeedSameCavesFromAnyThreadAndOrder`, seam checks of
`cavecheck.py`). Planning far from the column (megacave room, gallery cover) uses the terrain's
uncached `sample` (`CaveNetwork.Ground.estimate`), not the hydrology's surface, which would compute
the drainage of distant regions; columns still keep their cover under the exact ground.

Contract for later systems (`CaveSystem`, `VastlandsCaves`):
- `surfaceHeight(x, z, ground)`, `fluidPicker(vanilla)` (above).
- `isCave(x, y, z)`, `caveFluid(x, y, z)` (AIR, WATER, LAVA), `isRoof`, `isGrotto`,
  `fluidCode(x, y, z)`: what noise filling puts in a block; `describe` for F3. Columns are cached
  per thread: cheap for nearby queries.
- `caveBiome(x, y, z)` (Agent 4.5): the `CaveBiome` slot of a block (SURFACE, NONE, LUSH, DRIPSTONE,
  DEEP_DARK, CRYSTAL, VOLCANIC, OTHER); cheap anywhere.
- `megacaves(minX, minZ, maxX, maxZ)` (Agent 4.5): `MegacaveInfo` records (zone, kind, centre,
  half length and width, axis angle, floor and vault height at the centre, fluid and its top,
  pillars, bridges, shelf, cenote) for natural wonders.
- `entrances(...)`, `swalletsNear`, `grottoesNear`, `roofStrength`, `swalletPart`,
  `riverGalleries(...)`, `census(...)`: for previews and tests.
- For forests: the ground keeps at least 6 blocks of rock over cave space except at entrances
  (adits, gallery mouths, dolines, cenotes, sea caves), ravines and the portals of river tunnels,
  so tree features only meet caves there.

Spaces for natural wonders (Agent 6): megacaves are the stage. Tall vaults (halls up to ~150
blocks under high ground, abysses 110..300, canyons up to 220) for giant stalactites and pillars;
broad floors (halls, lakes, the lower floor of complexes; floors are flat in the middle with benches)
for underground forests; lake megacaves with basins 0.25 h deep and islands, and submerged ones,
for underwater formations; shelves and natural bridges already shaped; pillar caverns; the deep
dark megacaves of the deep zone for sculk formations. `MegacaveInfo` gives all of them per area;
`isCave` and `caveFluid` give the exact space.

Performance (this container, 4 vCPU, `cavePreview --bench`, one thread, warm caches, 64 chunks
along z = 0 from x 1 536 and from x -1 280; Agent 4's code in a worktree alternated with the
overhaul): columns 2.5 / 3.5 ms (Agent 4: 4.3 / 4.5), density of all 768 × 256 blocks 8.1 / 4.5 ms
(3.2 / 5.7), fluid picker 1.7 / 1.7 ms (1.4 / 1.2) per chunk: about 3 ms more per chunk near 0,0
(lowland and sea, where Agent 4's caves were sparse) and less in the mountains, for three to four
times the cave volume. What keeps it there: `KeyedCache` instead of direct-mapped caches, rough
terrain for far planning, pieces pruned by their horizontal reach and indexed by 16-block slabs,
chamber and megacave terraces cached per 5..6-block band, no wall noise deep inside caves, cave
biome slots without cave columns. End to end (dedicated server, 24 × 24 chunks, two squares,
alternated with Agent 4's code): 9.4..11.2 chunks/s against Agent 4's 9.35..9.38: not slower.

Tuning: `CaveSettings.DEFAULT` (frequency, density, passageWidth, largePassages, chambers,
megacaves, megacaveScale, vertical, regionVariation, water, spillways, lava, entrances, seaCaves,
riverTunnels, caveBiomes; all 1 except spillways 0.3, riverTunnels 0.5, caveBiomes 0.62); level
offsets, lattice and entrance rules in `CaveNetwork`; gallery classes in `Galleries`; megacave
kinds and sizes in `Megacaves`; domain sizes and fluid shares in `FluidDomains`; cover, clearance
and dams in `CaveModel`; province weights in `CaveBiomes`; slot biomes in the tags. Any change
changes generation (seams against existing chunks). Measure with `caveSurvey`, render with
`cavePreview` before generating worlds; `CaveModelTest` and `CaveBiomeTest` guard the properties.

Limitations (details: PROGRESS.md, Known Issues): network tunnels still follow the 64-block
lattice (galleries hide it at large scale); megacaves under lowlands are wide and low (the upper
zone there is ~100 blocks thick); underground rivers are still water standing in level stretches
with falls between them, not flowing water; spillway falls only where dams cross; vanilla features
still place lava and water springs and underground lava lakes; ores and other features anchored
to the world bottom keep vanilla's shifted ranges; no river rises from a cave (resurgence) and
none is rerouted underground (that would change Agent 3's drainage).

Forests (Agent 5)

Trees, shrubs, deadwood and the forest floor of the surface: `worldgen/forest`. `ForestModel` is
pure Java like the other models: a deterministic function of (seed, x, z) on the terrain, rivers,
caves and surface biomes, rendered and measured without the game (`./gradlew forestPreview`).
`VastlandsForests` (the `ForestSystem`) binds it to the world; the feature `vastlands:forest`
(`ForestFeature`, `ForestBlocks`) builds each chunk's part of every tree that reaches it; vanilla's
tree features give way where Vastlands grows the trees (`VanillaTrees`, `PlacedFeatureMixin`).

Integration:
- Biomes decide who grows the trees. Each forest community is a biome tag
  `#vastlands:forest/<community>` (`data/vastlands/tags/worldgen/biome/forest/`):

  | Community | Biomes (tag) | Trees |
  |---|---|---|
  | temperate | forest | oak, birch stands, spruce in the cold, a little dark oak in wet climates, rare ancient oaks |
  | flower | flower forest | small oaks and birches, many shrubs, bees |
  | birch | birch forest | birch, oak patches |
  | old_birch | old growth birch forest | tall birches, snags |
  | dark | dark forest | dark oaks (big ones in old stands), oaks, birches, huge mushrooms |
  | boreal | taiga, snowy taiga | spruce, pine patches, birch (riverbanks, young stands), rare giant spruces |
  | old_spruce, old_pine | old growth spruce / pine taiga | giant spruces or pines over spruce and pine, snags, moss |
  | grassland | plains, sunflower plains, snowy plains, meadow | groves and lone open-grown oaks, birches, rare ancient oaks; spruce in snowy plains |
  | savanna | savanna, savanna plateau, windswept savanna | acacias, oaks by the water, shrubs |
  | jungle, sparse_jungle | jungle, sparse jungle | tall jungle trees, giant ones, oaks with vines, a dense shrub layer |
  | swamp | swamp | swamp oaks hung with vines, in shallow water too |
  | highland | windswept hills, gravelly hills, windswept forest | spruce, pine, oaks where mild, krummholz in the cold |
  | grove | grove | spruce, pine, krummholz |
  | alpine | snowy slopes | krummholz, a few stunted spruces |
  | dry | wooded badlands | small oaks and shrubs |

  `transparent` holds the river biomes (riverbanks take the woods around them); `all` is the
  union. A biome in none of them (deserts, badlands, peaks, beaches, oceans, mushroom fields,
  mangrove swamps, bamboo jungles, cherry groves, pale gardens, and every biome of another mod
  unless a tag lists it) is left to its own features: Vastlands neither removes its trees nor
  roots any there. Other mods' biomes join a community by an optional tag entry (a datapack),
  without code and without a dependency.
- The feature: `data/vastlands/worldgen/{configured,placed}_feature/forest.json` and the NeoForge
  biome modifier `data/vastlands/neoforge/biome_modifier/forest.json` add `vastlands:forest` to the
  vegetal decoration of `#vastlands:forest/all`. Vanilla decorates a chunk with the features of every
  biome within one chunk of it, so the feature runs in every chunk a tree can reach (a tree's
  blocks stay within 16 blocks of its root, `TreeShape.REACH`). It runs after the biomes' own
  vegetation (flowers, grass, pumpkins, berries stay vanilla) and before `freeze_top_layer`, so
  snow lands on the crowns. In other worlds it does nothing (`VastlandsChunkGenerator` check).
- Vanilla's trees: the placed features in `#vastlands:forest/displaced`
  (`data/vastlands/tags/worldgen/placed_feature/forest/`: the 21 tree features of the managed
  biomes, and `lake_lava_surface`, whose lava would set the dense forests alight) get one more
  placement filter in Vastlands worlds (`vastlands:outside_forests`, appended by
  `PlacedFeatureMixin` at `placeWithBiomeCheck`, placed through the original's context so its biome
  filter still checks the original). The filter drops positions whose biome (the level's biome, the
  one vanilla's biome filter reads) belongs to a forest community; everywhere else those features
  place exactly as before with the same random sequence. Other worlds are untouched; other mods'
  features are touched only if the tag lists them. Vanilla's underground lava lakes
  (`#vastlands:forest/buried`) get the same filter judged at the ground above them while they start
  less than 24 blocks under it: such a lake carves four layers of air over its lava, opens out of
  a hillside and sets a dense forest on fire (seen in a screenshot session in taiga); deeper ones
  are untouched.
- Exact biome lookups (`ExactClimateSearch`, `ClimateRTreeMixin`): vanilla's climate index starts
  each search from the thread's last result and keeps it on an exact tie, so a column on a parameter
  boundary gets whichever biome the thread found last. The forests ask for the same biomes from
  several chunks and threads, so inside their lookups the index searches from scratch and leaves the
  thread's last result alone. Found when two generations of one square differed by whole trees.
- F3: a "Vastlands forest" line (community and its share, density, age, clearing, riparian,
  treeline, exposure, slope).

Layers (`ForestModel`):
1. Communities on a 16-block grid: each node's biome category (at the ground, `ForestBiomes`);
   blended over the nodes within 2.5 nodes (weights (1 - d²/6.25)², river biomes left out), then
   bilinear: composition and density change over ~40 blocks across biome borders, and forests thin
   towards open land, the sea and unmanaged biomes. The grid points also carry the terrain model's
   height and climate (one uncached `TerrainSystem.sample` per point and thread: temperature,
   sea-level temperature, moisture, prevailing wind), read bilinearly by every point of the model.
   Each node has the terrain's position (TPI: the ground minus the mean of 6 grid points ~32 and 6
   ~75 blocks around) and the sea among those points.
2. The forest at a point (`evaluate`): capacity = Σ weight × community capacity × moisture response
   (closed forests 0.72 + 0.4 m, open land 0.55 + 0.9 m); groves on open land (2-octave noise at 190
   blocks, smoothstep 0.36..0.7, up to 0.45); regional cover 0.55..1.05 (1 700 blocks); stand age
   (950 blocks, shifted by the communities' maturity): young stands denser with small trees, old
   growth with giants, snags, fallen logs, moss and gaps; clearings: openings (115 blocks,
   smoothstep 0.48..0.62), meadows (480 blocks), gaps of fallen giants (30 blocks, old growth only);
   the treeline (below); exposed crests thinner and shorter in the cold (TPI 8..30), sheltered
   valleys denser (TPI -6..-24), the coast thinner within ~70 blocks of the sea; riparian woods within
   18 blocks of a river or lake edge (`HydrologyModel.channels`; +30% in forests, +60% on open
   land: gallery forests along rivers through plains and savannas), wet meadows on some floodplains
   (260-block noise); small-scale variation (19 blocks). Noise fields are domain-warped (38 blocks at
   420, 7 at 90) so their shapes meander.
3. The treeline: the geography clamps its temperature at -1, so the trees use an unclamped
   temperature: the sea-level temperature cooled by the lapse rate (0.0022 per block) for the local
   ground above the sea, but in polar lowlands at least -0.7 up to 40 blocks above the sea (polar
   taiga stays wooded). Trees thin from -0.78 to none at -1.15 (±0.1 by a 320-block noise, so the
   line runs in bands and tongues), grow shorter from -0.5 down and turn to krummholz on cold
   exposed ground. Mountain biomes add their own steps: meadows (open), groves, snowy slopes
   (stunted trees), peaks (none).
4. Trees: a jittered lattice of 4-block cells, one candidate each, standing with probability
   density × 0.92; its community is drawn from the blended weights, its wood from the community's
   composition (with 95-block patches per wood: birch stands in oak woods, spruce stands in mixed
   forest, pine patches in taiga), its form and size from the age, the edge (low density or a small
   share of the community: smaller trees, more shrubs, so edges step down instead of standing as a
   wall), the climate and the ground. A candidate yields to any neighbour within two cells with a
   higher priority (its form's spacing plus a random fraction) closer than the mean of their
   spacings (bush 1.5 .. ancient oak 8, `TreeForm`): giants keep room around them, shrubs fill gaps,
   trees never touch. The yield is not recursive (a candidate's priority and site test depend only
   on its own cell), so the answer for any cell is the same from any chunk. A 3-block lattice holds
   shrubs (edges, clearings' margins, the jungle floor; clear of trunks), a 16-block lattice fallen
   logs (old growth, level ground between trunks).
5. The world under a tree (`checkSite`; the ground is the hydrology's surface: the caves only raise
   it under their roofs over gorges, above river water where no tree stands): its biome managed (or
   a river biome on dry ground); every trunk column dry (swamp oaks also in sea-level water at most
   one block deep) and within a block of the others; no water beside the trunk at or above its ground (no trunks on eroded banks);
   local slope over 4 blocks at most 0.45 for giants, 0.55 for dark oaks, mushrooms and logs, 0.95
   for trees, 1.25 for shrubs (cliffs stay bare, steep slopes carry shrubs); no cave space at the
   ground and 1, 3 and 6 blocks below any trunk column (cave mouths, dolines, cenotes, ravines), and
   for crowns of radius 4.5+ none at the lip 3 blocks around; no waterfall within the crown plus 8
   blocks (curtains and plunge pools stay clear, `HydrologyModel.falls` of every 512-block region
   in reach).

Tree shapes (`TreeShapes`, `TreeShape`; vanilla logs, wood and leaves only): broadleaf (oak, birch:
a leaning, gently curving trunk forking into 2..7 branches spread by the golden angle and bent
upward, each ending in a lumpy leaf cluster, side shoots and a fill in big crowns, crowns skewed
to one side), ancient oak (a 2 × 2 trunk with flared roots of bark blocks, 4..6 heavy limbs
bending at an elbow, sub-branches, low spreading limbs, a dome, rooted and coarse dirt around),
birch (layers of leaves on the upper trunk, twigs), spruce (whorls of needles with radial branch
logs inside), pine (a long bare trunk under a rounded crown), giant spruce and pine (2 × 2,
podzol and moss around), krummholz (a low mat stretched downwind, from the geography's prevailing
wind), dark oak (2 × 2, limbs under a broad flat canopy of three layers), tall and giant jungle
trees (vines, cocoa, branches up the trunk), acacia (forked leaning trunks under flat
umbrellas), swamp oak (a wide flat crown hung with vines), shrubs, snags (dead trunks), fallen
logs (with stumps and moss), huge brown and red mushrooms; since Agent 6 also stumps, uprooted
trees, debris, broken and rotten logs and procedural mushrooms up to giant ones (see Nature). Broadleaves, birches,
swamp oaks and tall jungle trees within 7 blocks of a river or lake lean out over the water, other
trees a little at random. Leaves carry the distance vanilla
computes (breadth-first from the tree's own logs through leaves); leaves more than 6 steps from a
log are never built, vines and cocoa without support dropped, bee nests on the east, south or west
of the trunk facing south with room in front, like vanilla's. A shape is a pure function of its
`TreeSpec` (its seed comes from the cell), built once per thread and cached. Each form has a reach
(`TreeForm.reach`: bush 5, birch 8, spruce 9, broadleaf 13, ancient oak 16 ..., two or three blocks
above the largest radius measured over ~170 000 trees); the builder drops blocks beyond it, and
`trees` reports a tree for a box only if its root lies within that reach, so a chunk builds the
shapes of the trees that can reach it, not of every tree within 16 blocks.

The block step (`ForestBlocks`, in each chunk's decoration):
- Each tree, shrub and log reaching the chunk (`model.trees` for the chunk: roots within their
  form's reach of it), in a fixed order; its shape's blocks inside the chunk: logs into air, plants, snow and leaves; leaves
  and decorations into air, plants and snow; leaves and logs win over mushroom caps; never into
  water (swamp trees: logs and waterlogged leaves in shallow water); double plants lose both
  halves; melons, pumpkins and bamboo give way to logs (a bamboo stalk cut by a log loses the part
  above it). Trunks whose ground is lower than the model's grow down onto solid ground (at most 5
  blocks); grass under a trunk becomes dirt; a trunk column or flared root that would come to rest
  on water is left out. Podzol, rooted dirt, coarse dirt and moss around giant trunks on the top
  soil. Bee nests get two or three bees (vanilla's block entity). Blocks are written into the
  chunk without neighbour updates, like vanilla's tree features; the light step runs afterwards.
- Structures (`StructureGuard`): a tree is left out whole if its footprint overlaps a piece of a
  surface structure (step `SURFACE_STRUCTURES`: villages, outposts, temples, huts, igloos, ruined
  portals, shipwrecks, mansions, other mods' surface structures) starting within 7 chunks of the
  tree's own chunk. Every chunk the tree reaches sees the same structures (they are within the 8
  chunks a decorating region can read). Only x and z count: vanilla moves some pieces up or down
  when it places them (buried treasure, scattered features, shipwrecks), so their heights depend on
  whether their chunk is decorated yet. Underground structures do not keep forests off the ground
  above them.
- The forest floor of the chunk's columns, by community, canopy (leaves above the column counted
  from the shapes) and moisture: leaf litter under broadleaf canopies (more in shade, in patches),
  ferns and large ferns (taiga, humid shade, jungle), grass and tall grass (more in the open and at
  edges), bushes at edges, mushrooms in deep shade, moss blocks and carpets on old wet ground
  (since Agent 6 in small clumps and only where moss suits the grass colour, see Nature), dry
  grass in hot savannas; under a closed canopy the floor stays sparse; in snowy climates only a few
  ferns. Everything checked with vanilla's `canSurvive`.

Determinism and chunk safety: candidates depend only on their cell; acceptance only on the
candidates around; the world is read only through pure model queries (terrain, hydrology, caves,
biomes with exact searches) and, in the block step, the chunk's own blocks. Each chunk writes only
its own blocks, so trees cross chunk borders whole without cross-chunk writes and in any order.
Caches are per thread, keyed by exact grid coordinates, open-addressed (`ForestCache`, like the
caves' `KeyedCache`): nodes, blended weights, candidates, falls per 512-block region, shapes. A hit
returns what a miss would compute; nothing grows with the explored area.

Contract for later systems (`ForestSystem`, `VastlandsForests`): see System Contracts. `forestAt`
for anything that should follow the forests (wildlife, ruins in clearings, a forest-floor feature);
`trees(...)` and `model().shape(spec)` for the exact blocks of every tree (also before the chunk is
decorated, for any chunk); `ForestModel.checkSite` reasons (`siteProblem`) explain why a cell has
no tree. Cave openings, waterfalls and water are kept clear by the model, so features placed into
those (Agent 6) do not need to remove trees.

Performance (this container, 4 vCPU; JFR profiles of the dedicated server; details: PROGRESS.md,
Agent 5): the feature costs 1.8 (savanna) .. 3.9 ms per decorated chunk in temperate, boreal, dark
and mountain forests and 6.1 ms in the jungle, ~3% of the worldgen workers' time, shared by the
site checks, the shapes, the model and the block writes. End to end (`generate.py --poll 2`, same
squares alternated with `main`): 0..8% slower where forests are dense, within the run-to-run spread
elsewhere. What keeps it there: the 16-block grid carries the terrain model's height and climate
(one uncached query per point instead of a dozen interpolated terrain queries per node, which
also evicted the terrain's column cache), the model reads the hydrology's surface (not the caves'
column) and the caves only for trunk columns that pass the other checks, per-form reaches (a chunk
builds only the shapes that can reach it), the crown noise skipped outside each blob's largest
radius. Features run on the consecutive worldgen executor, so the feature's cost adds directly.

Tuning: `ForestSettings.DEFAULT` (density, clearings, riparian, age, treeSize, shrubs, deadwood,
understory; all 1 except age 0); community capacities, grove shares and maturity in `ForestType`;
spacings and reaches in `TreeForm`; compositions in `ForestModel.Choices`; shapes in `TreeShapes`; the floor in
`ForestBlocks.Understory`; which biome is which community in the tags. Any change changes
generation (seams against existing chunks). Render with `forestPreview` (`--gallery`, `--view`,
`--map`, `--stats`) before generating worlds; `ForestModelTest` and `ForestGameTest` guard the
properties, `forestcheck.py` checks generated worlds.

Limitations (details: PROGRESS.md, Known Issues): biomes outside the communities keep vanilla's
trees (bamboo jungles, cherry groves, mangroves, pale gardens), and a crown near such a border can
overhang into them; underground structures do not keep trees off the ground above; a leaf's path
to its log can be cut where the terrain or another tree's log takes a block of the crown (stored
distance stays valid until a neighbour update, then up to a few leaves decay: 0..3 per 576 chunks
measured inside the squares); Sincerelyuq's Biomes and other mods' surface biomes are not in any
community until a tag lists them.

Nature (Agent 6)

Natural formations of the surface and the caves: `worldgen/nature`. `NatureModel` is pure Java
like the other models: a deterministic function of (seed, position) on the terrain, rivers, caves
and biomes, rendered and measured without the game (`./gradlew naturePreview`). `VastlandsNature`
(the `NatureSystem`) binds it to the world; the feature `vastlands:formations` (`NatureFeature`,
`NatureBlocks`) builds each chunk's part of every formation that reaches it. Agent 6 also changed
the forests (moss, deadwood, mushrooms; below) and added the server-side grass colours
(`worldgen/biome/GrassColors`, `BiomeColors`).

Integration:
- Two configured features of type `vastlands:formations` (`data/vastlands/worldgen/{configured,placed}_feature/
  surface_formations.json`, `cave_formations.json`, config `{layer: surface|caves, density: 0..1}`),
  added to every biome of `#vastlands:nature/all` (`#minecraft:is_overworld` plus the cave slot
  tags, optional entries) by two NeoForge biome modifiers: `nature.json` puts the surface
  formations at step `local_modifications` (after the terrain, rivers and caves, before
  structures' blocks and vegetation; the forests come later in `vegetal_decoration` and grow
  around them); `nature_caves.json` puts the cave formations at the end of
  `underground_decoration`, after vanilla's dripstone clusters, pointed dripstone and sculk
  patches and before the lush caves' vegetation. (Found in game: run before them, a dripstone
  cluster's pools replaced the feet of stalagmites and left the rest floating.) In other worlds
  the feature does nothing (`VastlandsChunkGenerator` check). Vanilla decorates a
  chunk with the features of every biome within one chunk of it, so the feature runs in every
  chunk a formation can reach (`FormationKind.reach` ≤ 32 blocks, the largest a wonder's).
- Styles from biome tags (`data/vastlands/tags/worldgen/biome/nature/`): `surface/<style>` for
  `SurfaceStyle` (temperate, dark, boreal, old_growth, snowy, mountain, wetland, jungle, savanna,
  desert, badlands, beach, stony_shore, river, ocean, mushroom, none) and `cave/<profile>` for
  `CaveProfile` (plain, dripstone, lush, deep_dark, crystal, volcanic, other, none). A surface
  biome in no tag is judged by vanilla's convention tags and its climate (oceans, rivers,
  beaches, badlands, mountains and hills; cold → snowy, hot and dry → desert, hot and wet →
  jungle, forests and taigas → temperate or boreal, else temperate), so other mods' biomes get
  fitting formations without a list. Underground: plain rock under a surface biome is `plain`; a
  cave biome in no profile tag takes its cave slot's profile (only the slot's own biomes: an empty
  slot falls back to plain rock), `other` in the `other` slot. `none` keeps a biome bare. Other
  mods (Sincerelyuq's Biomes' amethyst caves → `crystal`, smoldering caves → `volcanic`, through
  the cave slot tags already) join by optional tag entries, without code.
- Exact biome lookups (`ExactClimateSearch`, like the forests'): every chunk and thread gets the
  same biome for a formation.
- F3: a "Vastlands nature" line (the formations rooted within 8 blocks: kind and root).

Layers (`NatureModel`): jittered lattices, one candidate column per cell, of four scales per family:
surface 12 / 40 / 160 / 768 blocks (small, medium, large, wonders), caves 6 / 16 / 40 / 96 (small,
medium, large, giant). A surface candidate stands with a chance by layer and style (`surfaceChance`:
e.g. small 0.08 temperate, 0.22 mountain, 0.35 stony shore; wonders 0.14 in mountains, deserts,
badlands, stony shores, 0.1 in jungle, savanna, temperate land, none elsewhere), scaled for small
and medium ones by a regional rockiness (420 blocks, 0.3..1.7: rocky country and places with
hardly a stone) and by the slope, with a floor of 0.3 on shores. A cave cell passes a gate (0.75 /
0.75 / 0.7 / 0.7 × a regional richness field of 260 blocks, 0.15..1.6, so some caves are rich and
some bare), then holds one formation per open run of its column (every cave level it crosses,
`VastlandsCaves.runs`: floor, ceiling, fluid and its level, the room around, megacave, open to the
sky), chosen from the run's profile, height, room, water or lava and depth. Runs within 8 blocks of
the surface and open to the sky get nothing (thin roofs, entrances). A candidate yields to a larger
formation of the same family whose footprint covers it (caves: only when their heights meet), not
recursively (larger ones never yield), so the answer for a cell is the same from every chunk.
Measured (seed 20261018, `NatureModelTest`, 36 km² of surface in 1 km squares over 24 × 24 km,
2.4 km² of caves): surface 19 432 small, 431 medium, 73 large, 2 wonders; caves 13 370 small,
1 673 medium, 45 large, 0 giant (giants need megacave-sized runs).

What stands where (`small`, `medium`, `large`, `wonder`, `caveSmall` ... `caveGiant`):
- Surface, small: boulders (radius 0.7..2.4, a large erratic now and then), rock clusters; at the
  water's edge (within 5 blocks of the shore, `shore`): driftwood (logs of the local wood lying
  along the shore) and shore stones. Never in water deeper than 3 at the edge, never on rivers'
  beds.
- Medium: talus fans on steep mountain and boreal slopes, hoodoos and spires (badlands, deserts,
  steep mountains), balanced rocks (dry land, savanna), tors on crests and hilltops (kopjes on the
  savanna), islets in lakes and the shallow sea near coasts, sea stacks off rocky coasts. River
  channels are kept clear.
- Large: natural arches (dry land, mountains, coasts: `archFits` checks both feet and the ground
  under the span), tall spires and sea stacks, big tors, balanced rocks.
- Wonders: great arches, great spires, stone forests (limestone pinnacles, only on karst ground in
  temperate, savanna and jungle land).
- Rock by place (`surfaceRock`): sandstone in deserts, terracotta (vanilla's bands) in badlands,
  basalt (basalt, smooth basalt, blackstone, tuff) in volcanic geology, limestone (stone, diorite,
  calcite, andesite) on karst, pale rock (diorite, stone, calcite, andesite) in the cold, granite
  (stone with granite patches, andesite, diorite) in warm fractured hills and savannas, shore rock
  (stone, cobblestone, andesite, granite) at the water, else stone (andesite, cobblestone, tuff
  low down). Moss only on
  rock in damp woods where it suits the grass (`surfaceFlags`: wooded styles, moisture, the
  biome's moss blend), snow on cold rock, nothing on dry rock.
- Caves, by profile: dripstone (groups of pointed dripstone on floor and ceiling, stalagmites and
  stalactites of dripstone blocks with pointed tips, columns, spike fields, giant dripstones up to
  ~70 blocks in tall halls), lush (root curtains, moss gardens with azaleas, cave vines and spore
  blossoms, mossy pillars), deep dark (sculk growths, mounds and pillars of deepslate under sculk;
  no sensors, shriekers or catalysts: nothing that calls the Warden), crystal (calcite and
  amethyst clusters), volcanic (basalt columns rising out of lava, magma), plain and other (rubble,
  cave boulders, pendants, rock pillars, collapses; most plain caves stay bare), flooded runs
  (submerged rocks, islets in cave lakes; plants never in water unless waterloggable), giants
  (giant columns, giant dripstones, spike fields) in megacave-sized runs.
- Every surface candidate checks its ground (`clearSurface`, 9 points over its footprint): dry
  ground (unless it belongs at the water), no cave space at the ground, 3 below or 1 above (cave
  mouths, sinkholes, cenotes, ravines stay open), no waterfall within its radius + 10 blocks
  (curtains and plunge pools stay clear).

Shapes (`FormationShapes`, `FormationShape`; vanilla blocks only): signed-distance blobs with
noise (boulders, tors, islets), stacked discs (spires, hoodoos, sea stacks: tapering, leaning,
banded), arches built cell by cell around a half-ellipse centre line, talus fans, pointed
dripstone in vanilla's thickness sequence (base, middle, frustum, tip), stalagmites and
stalactites of dripstone blocks capped with pointed dripstone, columns with a waist, plant
stacks. A shape is a pure function of its `FormationSpec` and the world it reads
(`FormationShapes.World`: the ground (bed under water), the water top, the caves), built once per
thread and cached. Positions are packed relative to the root (127 sideways, 32 767 up or down);
besides blocks a shape lists feet (columns extended down to the ground through open space) and
hangers (up to the ceiling), and snapped stacks (plants, carpets, veins, rubble) that find their
own column's floor or ceiling near their height. Rock is resolved per position from the palette
(smooth patches of each stone; mossy cobblestone on exposed tops of mossy rock, moss blocks on
lush cave rock, sculk on deep dark rock).

The block step (`NatureBlocks`, `NatureFeature`):
- Each formation reaching the chunk (`model.formations` for the chunk, in a fixed order), its
  blocks inside the chunk: solids into air, plants, snow and vanilla's pointed dripstone (what is
  left of it hangs on the new block); into still water only where the formation stands in it
  (`WET`: islets, sea stacks, and every cave formation of a flooded run, so a stalagmite rises out
  of its pool), never into flowing water (falls, springs, spillways); into natural ground only
  where it is sunk into it (`EMBED`: stone, deepslate, dirt, sand, gravel, terracotta ..., never
  anything built); lava only for basalt and magma of volcanic columns. Decorations into air only
  (waterloggable ones into still water). Decorations held by the block above or below them
  (pointed dripstone, cave vines, hanging roots, spore blossoms, carpets, azaleas) are written
  from their support outwards and only where vanilla's `canSurvive` holds: their support is in
  their own column, so the check reads only the chunk's own blocks and does not depend on
  generation order; a tip whose body stayed out (under water, say) is not left hanging from
  nothing. The pointed dripstone of every column touched gets the thickness vanilla computes
  (`calculateDripstoneThickness`), so a stack cut short still ends in a tip. Feet and hangers,
  then the snapped stacks. Blocks are written without neighbour updates, like vanilla's features.
- Structures (`StructureGuard`): a formation is left out whole if its footprint meets a piece of
  any structure (villages, ruins, ancient cities, mineshafts, trial chambers, strongholds, other
  mods' and datapacks' structures) starting within 7 chunks of its root chunk (through that
  chunk's structure references), surface ones by their horizontal extent with a margin of 2, cave
  ones in 3D with a margin of 3. During features a region reads structure references only 1 chunk
  away and starts 8 away (`ChunkPyramid`: FEATURES needs CARVERS at 1, STRUCTURE_STARTS at 8), so a
  formation reaching more than 16 blocks from its root (great arches, stone forests: the root may
  be 2 chunks away) checks every start within 6 chunks of its root instead. Every chunk the
  formation reaches sees the same structures. (Found in game: the first version asked for the
  references of a root chunk 2 chunks away and crashed the server in a badlands square.)
- `density` in the feature config leaves out a share of formations by their identity (the same in
  every chunk).

Forests (Agent 6 changes in `worldgen/forest`):
- Moss: vanilla's moss block is a fixed yellow-green (texture mean #596E2D); the grass around it
  is tinted by the biome. `GrassColors` computes the client's grass colour on the server (the colour
  map is a client resource, so `Biome.getGrassColor` is useless there): vanilla's colour map
  sampled at 9 × 9 points (within 2/255 of the full map, checked against the texture in
  `NatureDataTest`), the dark forest and swamp modifiers, the grass top texture's mean grey (147);
  then the CIELAB contrast between moss and the grass top, lightness weighted 1.5, gives the moss
  blend 1 - smoothstep(7, 24, contrast) (`BiomeColors.mossBlend`, from each biome's own effects
  and climate, so modded biomes are read the same way): forest 0.97, plains 1.0, birch forest
  0.94, meadow 0.8, taiga and old growth spruce 0.12 (bluish grass), jungle 0.24, dark forest,
  swamp, snowy and badlands 0.0. Moss on the forest floor (`ForestBlocks.Understory.floor`) is now
  small clumps (3-block patches inside 23-block colonies) whose chance is the community's moss
  ecology (old growth taiga 1.0, swamp 0.55, boreal 0.45, jungle 0.35, temperate, birch, flower,
  dark 0.3, highland and grove 0.2) × stand age × wetness × canopy × soil (the blend on grass;
  at least 0.85 on podzol and coarse dirt), so moss stays on old damp ground where it matches and
  thins out where it would read as a foreign patch. The dark forest floor gets podzol patches
  (more under closed canopy), coarse dirt specks and mushrooms on podzol instead; swamps mud at the
  water. Moss around old trunks follows the same rule. Lush caves keep all their moss (vanilla's).
- Deadwood: fallen logs (plain, broken in two pieces with a step, rotten with holes, stripped
  wood and mushrooms, giant 2 × 2 logs in old growth), stumps, uprooted trees (a root plate of
  rooted and coarse dirt and mangrove roots with hanging roots), debris; in swamps also in
  shallow water. `TreeForm` STUMP, UPROOTED, DEBRIS; variants in `TreeSpec` style bits.
- Mushrooms: huge brown and red mushrooms are procedural (`TreeShapes.Mushrooms`): curved and
  leaning stems (thin, plus-shaped, 2 × 2 for giants), brown caps flat, umbrella with a skirt,
  wide with a hump, tiered or lopsided and tilted; red caps dome, tall, flattened, bell or lobed;
  smaller huge mushrooms at the foot of giants; faces as vanilla shows them (no face between blocks of the same
  kind, gills hidden), every cap block held face to face (`connect`). Giant mushrooms (`TreeForm.
  GIANT_MUSHROOM`, 10..16 blocks, caps 10..15 across) in old dark forests, clustered by a 70-block
  mushroom patch field. `ForestModelTest` checks faces and connectivity of 480 mushrooms.
- Trees keep clear of formations (`NatureSystem.clearOfFormations`: the trunk outside the
  footprint, a crown away from tall formations).

Determinism and chunk safety: as the forests'. Candidates depend only on their cell; acceptance
only on the candidates around; the world is read only through pure model queries (terrain,
hydrology, caves, biomes with exact searches) and, in the block step, the chunk's own blocks
(feet, hangers and snapped stacks read only their own column). Each chunk writes only its own
blocks. Caches are per thread and keyed by cells (`NatureCache`: candidates, shapes, falls per
512-block region).

Contract for later systems (`NatureSystem`, `VastlandsNature`): `formations(minX, minZ, maxX, maxZ,
surface, consumer)` lists every formation whose blocks can reach a box (`FormationSpec`: kind,
root, height, radius, span, rock, flags); `model().shape(spec, model().world())` its blocks;
`clearOfFormations(x, z, trunk, crown)` for anything that should keep off them (trees use it).
`VastlandsNature.bind(owner, biomes)` before use (the feature binds the world's biome source).

Performance: model and shapes cost about 0.4 ms per chunk at the surface and 0.3..0.4 ms in the caves
(`naturePreview --bench`, warm, one thread); in game the feature takes 0.36..1.0 ms per pass (two
passes per chunk: surface and caves), end to end 0..10% of the generation time per square (see
PROGRESS.md, Agent 6).

Tuning: `NatureSettings.DEFAULT` (surface, caves, large, wonders, mossy; all 1) and the features'
`density`; chances per layer and style in `NatureModel.surfaceChance`, the cave gate `CAVE_MAX`, the
choosers (`small` ... `caveGiant`), rock in `surfaceRock` / `caveRock`; shapes in `FormationShapes`;
palettes in `NatureBlocks.rock`; styles and profiles in the tags; moss in `GrassColors`,
`ForestModel.mossEcology` and `ForestBlocks.Understory.floor`. Any change changes generation (seams
against existing chunks). Render with `naturePreview` (`--gallery`, `--view`, `--cave`, `--stats`,
`--find`, `--bench`, `--order`, `--runs`, `--list`, `--mask`, `--who`) before generating worlds; `NatureModelTest` and
`NatureDataTest` guard the properties, `naturecheck.py` checks generated worlds.

Limitations (details: PROGRESS.md, Known Issues): formations are vanilla blocks at block scale (no
custom models); structures win (a formation meeting one is left out whole, so a village square
never gets a boulder, but neither does the countryside right next to it within the margin);
surface styles come from vanilla biomes (a modded biome gets the style of its convention tags and
climate until a tag lists it); cave profiles of empty slots fall back to plain rock; giant cave
formations need megacave-sized runs and are rare; Radiance lighting and Overhaul structures are not
integrated (no mod present), see PROGRESS.md for the hooks.

Integration (Agent 7): Sincerelyuq's Biomes and Sincerelyuq's Overhaul

Both mods are optional: nothing in Vastlands depends on them at compile time or at runtime. Every
reference to them is an optional tag entry (`{"id": ..., "required": false}`), a block looked up by
id at runtime (`compat/CompatBlocks`, absent blocks give nothing) or a check of a registry key, so
a world without either mod generates bit for bit as before (same slot draws, same noise seeds; the
regional and luminous lists are simply empty). In other world types both mods work as their
authors made them; everything below applies to Vastlands worlds only.

Who owns what:

- Sincerelyuq's Biomes injects its biomes only into stems of vanilla's overworld dimension type;
  the Vastlands overworld (`vastlands:overworld`) is not one, so the mod's own placement never runs
  in Vastlands worlds. Vastlands places all eight of its biomes itself, from its geography,
  climate, hydrology and caves. The `something_biome` and its `something` dimension are untouched.
- The mod's surface rules are not added to the Vastlands overworld either:
  `compat/IntegrationSurface` builds equivalent rules (with fixes for the taller world) and
  `NoiseBasedChunkGeneratorMixin` puts them in front of the Vastlands rules.
- The mod's features that Vastlands builds better from its own models are not placed
  (`#vastlands:integration/replaced`: giant lumen, depths floor, lumen pond, amethyst floors,
  ceilings, spikes and lake, smoldering forge, sand dune, ash vent); the rest are kept, guarded
  against structures (`guard_small/medium/large`) and, for lotus pools, against slopes and water
  (`level_ground`) by placement filters appended by `PlacedFeatureMixin`
  (`compat/IntegrationFeatures`). The mod's tree features in biomes the forests manage are in
  `#vastlands:forest/displaced`.
- Sincerelyuq's Overhaul keeps its structures and their placement; Vastlands extends a chosen few
  to the new biomes (structure modifiers) and drops starts on bad sites (site checks).

Surface biomes: regions (`worldgen/biome/RegionalBiomes`)

The biome source's delegate (vanilla's multi-noise table on Vastlands climate) still decides most
of the land. Five regions take its place where the geography calls for them; each is a continuous
strength field (0..1) from the terrain column's climate parameters, landform and rock, times a
regional selector noise, with a 52-block edge noise so borders wander. The first region (in this
order) whose field passes 0.5 holds the column; rivers, oceans, beaches, mushroom fields and
`#vastlands:region/keep` stay.

| Region | Biomes (tag `#vastlands:region/<name>`) | Where |
|---|---|---|
| `painted_range` | painted strata | mountain ranges (vanilla's peak and slope erosion), warm, not arid, below the snow; some ranges (6500-block selector) |
| `volcanic_waste` | ashen wastes | the volcanic hotspots of the caves' geology, warm and dry, off the high ranges |
| `dune_sea` | bone dunes | the hottest, most arid deserts of dry provinces, flat and far inland (4200-block selector) |
| `wetland` | lotus pools | warm, humid lowland: flat ground near sea level, interior basins, river floodplains |
| `dusk_wood` | twilight thicket | temperate, humid forest country off the mountains (a share of dark forests and their neighbours) |

F3 (`VastlandsBiomeSource.addDebugInfo`) shows each region's field at the player.

Cave biomes

- The slot tags hold the mod's cave biomes as optional entries: amethyst caves in
  `#vastlands:cave/crystal`, the smoldering cave in `#vastlands:cave/volcanic`; they take those
  slots' provinces and megacaves (the crystal slot falls back to dripstone and the volcanic slot to
  plain rock without them, as before).
- Bioluminescent depths: a `luminous` list in the biome source (`#vastlands:cave/luminous`).
  `CaveBiomes` marks half of the lush and dripstone provinces of the middle and deep layers under
  warm, humid climates (and 40% of such megacaves) luminous (bit 0x100 of the province code, drawn
  after the slot so the slots themselves do not move); there the luminous biome takes the slot's
  place. Empty list: nothing changes.
- Each has its own formation profile (`#vastlands:nature/cave/amethyst|smoldering|luminous`,
  checked before the slot profiles, `CaveProfile.FIRST`):
  - amethyst caves: crystal gardens (the mod's amethyst spikes of many lengths on amethyst and
    glowing amethyst anchors, buds), crystal spires (groups of leaning crystals on floor or
    ceiling with a calcite and smooth basalt collar), crystal columns, rare giant crystals in
    megacaves (20..48 blocks), pools of lavender spinel in floor hollows;
  - smoldering cave: magma-veined pillars and outcrops (the `MAGMATIC` rock), vents (blackstone
    cones with magma and the mod's smoldering magma in the crater), geothermal fields in great
    halls; no lava is placed, the mod's eruptive block only at vent tops;
  - bioluminescent depths: lumen mushrooms in five cap forms (dome, umbrella, plate, bell, lobed)
    chosen by the mushroom's proportions, stems of one to four blocks across, leaning and curving,
    gills under the cap and bulbs hanging from them; each mushroom must fit its cave whole (tried
    smaller three times, else left out); young colonies, moss and sprouts, moss and bulbs on low
    ceilings, ponds with sea pickles. Giant lumen mushrooms stay as rare as the mod made them
    (tall caves and megacaves only).
  - Pools (`FormationKind.POOL`) are only the floor cells whose neighbours hold rock at the
    water's level (checked against the cave model), and the block step writes fluid only into
    natural ground, never into air, so a pool cannot spill.

Surface content

- Painted strata, ashen wastes, bone dunes: own surface styles (`#vastlands:nature/surface/strata|ashen|dunes`).
  Strata: banded spires, tors, scree and arches in the strata colours (the `STRATA` rock follows the
  same band function as the surface rule). Ashen: basalt boulders and outcrops, hornitos, and
  volcanic fissures on level, dry ground (a crack 1..3 wide with blackstone lips, magma at its
  bottom, the mod's ash vents on its lips over magma).
- Lotus pools are `wetland`, the twilight thicket `dark` for formations.
- Forests (Agent 5 system): two new woods with the mod's blocks, `DUSK` (twilight thicket: dusk
  trees of every age, hanging dusk leaves, broad elders, rare ancients, snags, huge mushroom
  patches, ferns and mushroom groups on the floor; the mod's own dusk bloom patches stay, guarded)
  and `CHARRED` (ashen wastes: sparse charred snags of several heights and stumps, a dead bush here
  and there). `#vastlands:forest/dusk|charred` name the biomes. Trees avoid underground structure
  pieces that reach the surface.
- Dunes (`biome/DuneField`, `nature/DuneSand`): the bone dunes get transverse dunes across the
  geography's prevailing wind: a long windward slope, a short slip face, sinuous crests that break
  into crescents, spacing of about 45..65 blocks, up to 11 blocks high, ripples on the windward
  slopes. The height is a pure function of the column; it is zero at the region's border, near
  higher-priority regions, in river valleys, within 12 blocks of water (4-block lattice) and of any
  structure piece reaching the ground. It is written after the surface formations in the same
  feature (sand only into air over sand of a bone dunes column), so vegetation and the mod's
  ribcages and skulls (placed at the ground under the dunes through `WORLD_SURFACE_WG`) end up on
  and half buried in the dunes.

Structures (Sincerelyuq's Overhaul)

- Theme extensions: `vastlands:add_biomes` structure modifiers
  (`data/vastlands/neoforge/structure_modifier/overhaul_*.json`) let chosen structures also start in
  the new biomes: camps, half portals and fake treasure in the ashen wastes; chest traps in the bone
  dunes; the firefly bush and old village at the lotus pools; redstone bunches and camps on the
  painted strata; the mushroom ring, spruce well, camps and half portals in the twilight thicket;
  amethyst lakes and fake geodes in the amethyst caves; bones in the smoldering cave; the trial
  fragment and copper bloom in the bioluminescent depths. Lists: `#vastlands:overhaul/<biome>`
  (structures) and the biome tag of the same name. Vastlands worlds only (checked at server start).
- Site checks (`compat/StructureSites`, `ChunkGeneratorMixin`): starts are dropped when the ground
  under the start piece is not level (`#vastlands:site/level_ground`), is water (`dry_ground`), when
  an underground piece would open into flooded or lava-filled cave space (`sealed`), or when a
  structure would stand within 3 chunks of a possible strata sanctum start (`apart_from_sanctum`).
  Pure model queries only, so every generation order agrees. `/locate` does not run these checks
  (it may name a start that was dropped).
- Protection from decorations: formations and forests already step aside for every structure
  piece (Agent 5, 6); the kept Biomes features are guarded; dunes fall away near pieces.
- The Overhaul's expanded Ancient City replaces template pools of vanilla's `minecraft:ancient_city`
  (same pool names and connectors), so it is placed by vanilla's structure at Y -27 in the deep
  dark, which the Vastlands caves keep at that height (Caves). Its pieces are in
  `#vastlands:site/sealed` only through the structures listed there; vanilla's ancient city is not
  site-checked (unchanged placement).


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
- Cave biomes (Agent 4.5): only the Vastlands preset uses the `vastlands:caves` biome source; its
  tags are in the `vastlands` namespace; the sampler registration in `RandomStateMixin` happens only
  for a `RandomState` with a Vastlands context. Vanilla biomes are used as they are (lush caves,
  dripstone caves and the deep dark bring their own features).
- Natural formations (Agent 6): `vastlands:formations` (two placed features) is added to the
  overworld biomes by two NeoForge biome modifiers, so it is in their feature lists in every
  world, but it places nothing unless the generator is a `VastlandsChunkGenerator`. No vanilla
  feature, biome or block is changed; vanilla's cave biome features (dripstone clusters, pointed
  dripstone, sculk patches) run as before, then the cave formations.
- Integration (Agent 7): every reference to Sincerelyuq's Biomes or Overhaul is an optional tag
  entry or a block looked up by id; the biome source's regional and luminous lists, the integration
  surface rules (`NoiseBasedChunkGeneratorMixin`, only with a Vastlands context), the placement
  filters of `#vastlands:integration/*` (`PlacedFeatureMixin`, only for `VastlandsChunkGenerator`),
  the structure modifiers (`vastlands:add_biomes`, only when the server's overworld is a Vastlands
  world) and the site checks (`ChunkGeneratorMixin`, only for `VastlandsChunkGenerator`) change
  nothing in other worlds, and nothing at all without the mods (`IntegrationDataTest`).
- Forests (Agent 5): `vastlands:forest` is added to vanilla biomes by a NeoForge biome modifier, so
  it is in their feature lists in every world, but it places nothing unless the generator is a
  `VastlandsChunkGenerator`. `PlacedFeatureMixin` appends the `vastlands:outside_forests` filter
  only when the generator is a `VastlandsChunkGenerator` and the placed feature is in
  `#vastlands:forest/displaced` or `#vastlands:forest/buried`; otherwise `placeWithBiomeCheck` runs
  unchanged. `ClimateRTreeMixin`
  changes the climate index only inside `ExactClimateSearch.call` (the forests' biome lookups); every
  other search, in any world, keeps vanilla's last-result shortcut.

Testing and Verification

JUnit (`./gradlew test`, 87 tests):
- `WorldHeightDataTest` — data files agree with `WorldHeight` and pass vanilla's codecs.
- `GenerationSetupTest` — seeded randomness is deterministic and separated per system and seed;
  terrain, hydrology, caves and forests are implemented.
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
  queried in a shuffled order from parallel threads; at fifteen places (land, sea, a lava region, a
  coast, a natural bridge, a swallet, a natural tunnel with a grotto; since Agent 4.5 megacaves
  with lakes and cenotes, a flooded one under the coast, a lava megacave, a dry canyon, and
  underground rivers falling over dams) 96 × 96 columns over the full height hold no fluid that
  vanilla's rules would move (`CaveVolume.check`: water or lava beside or above cave air, water
  touching lava, sea or river water touching dry caves; designed spillway notches aside, of which
  there must be more than 20); no cave space or roof within the clearance under river and lake
  water (swallets aside) at three river places; the network entrances with their mouth at the sea
  in a 1 024-block square of coast and shelf (sea-cave adits and blue holes, at least 10) hold
  their fluids and at least 80% open into the sea; no cave lava above Y -54; cave share of the
  rock 4..16% (Agent 4's guard was 1.2..8%, measured 1.56%) with over a tenth of the columns
  cave-free, chambers with a third or fewer of radius 30+, adits, megacaves in both zones in under
  two thirds of the 1 024-block cells, galleries of all three size classes and underground rivers;
  the natural bridge and swallet near the origin's region exist and the swallet's shaft reaches the
  cave water.
- `CaveBiomeTest` (test server, Agent 4.5) — the preset's biome source is `vastlands:caves`, lush
  caves, dripstone caves and the deep dark are among its possible biomes and in their slots, the
  climate sampler leads to its world's context; near the ground every biome equals the delegate's
  (surface biomes unchanged); over 81 000 underground samples every biome is a possible one, lush
  caves 3..30% (none under frozen climates, almost none under the sea), dripstone 8..50%, the deep
  dark 0.3..12% and present at Y -27; cave biome runs along rows 120 blocks deep longer than 200
  blocks.
- `ForestModelTest` (Agent 5; biomes from vanilla's table on the router's parameters, like
  `forestPreview`) — two models of the same seed give every chunk the same trees and shapes when
  queried in rows on one thread and shuffled on parallel threads, at four places (dark forest and
  old growth taiga, a lowland lake, a jungle, a river in taiga); shapes: every leaf within distance 6
  of its own logs with the distance vanilla computes, every block within its form's reach, vines on a solid
  face, cocoa on jungle logs, bee nests with room in front (south); trees on dry ground, never
  over caves or falls, within the slope limits, only in managed biomes (> 3 000 trees, > 50 of them
  riparian); trunk spacing on the main lattice (no two closer than the mean of their spacings),
  giants rare, each community's woods; density by biome (dark forest > 0.4, forest and taiga > 0.35,
  plains < 0.15, desert < 0.03); open land beside water at least twice as wooded as away from it.
- `ForestGameTest` (test server, Agent 5) — the world's real biome source and the mod's mixins: 800
  chunks get the same trees from a fresh system queried in a shuffled order on parallel threads
  (measured 13 327 trees reaching them, 0 chunks differ).
- `ForestModelTest.mushroomsShowVanillaFacesAndHoldTogether` (Agent 6) — 480 huge and giant
  mushrooms of every cap style, lean and size: no face shown between two blocks of the same kind,
  gills hidden, a cap's top shown exactly where nothing of its kind covers it, every stem and cap
  block held face to face by a stem standing on the ground, within reach; heights and cap widths
  vary (measured heights 5..18, cap extents 5..22 with their small neighbours).
- `NatureModelTest` (Agent 6; biomes from vanilla's table and the mod's tag files,
  `PreviewNatureBiomes`) — two models give every chunk the same formations and shapes in rows on
  one thread and shuffled on parallel threads (384 chunks at six places: dark forest by the sea,
  snowy peaks, badlands, a stony shore, a jungle river, dripstone caves); at those places every
  solid block of every formation (2 770 formations, 44 668 blocks) is held face to face, through
  the formation's own blocks or its feet and hangers, by natural rock, and every shape stays within
  its kind's reach; surface formations stand on dry ground (shore kinds aside), never on a cave
  opening, never within radius + 8 of a fall, no islets in rivers; cave formations stand on a
  cave floor with their span ending at the ceiling; small formations outnumber medium ones,
  medium large ones, large wonders, at the surface and in the caves, with 1..36 wonders in 36
  km²; no tree log of the forests grows into a formation (3 081 trees beside 643 formations);
  every kind reaches at most 32 blocks (what a decorating region can read, see the structure guard).
- `NatureDataTest` (test server, Agent 6) — the formations' configured and placed features and
  biome modifiers load; every overworld biome has the surface formations in its local
  modifications and the cave formations last in its underground decoration; vanilla
  biomes get their styles and profiles from the tags; `GrassColors` within 2/255 of vanilla's
  grass colour map (the client texture, read from the test class path) at its samples and 3
  between them, the moss and grass top textures' means as the code assumes; every vanilla
  overworld biome's grass colour from the server equals the preview table's (`PreviewBiomeTraits`);
  moss blends into forest, plains, birch forest, meadow grass and not into dark forest, swamp,
  snowy taiga, badlands or pale garden grass; with the world's real biome source 216 chunks get
  the same formations in any order and on any thread, and the previews' surface styles agree with
  the game's at 1 086 of 1 089 points.

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

Cave survey: `./gradlew caveSurvey --args="--seed S --center X Z --size BLOCKS"` (`CaveSurvey`, Agent
4.5; only public `VastlandsCaves` queries, so it measures any version of the caves): the ground and
landforms of the square, its pieces and megacaves; the cave share of the underground per 64-block
band and per depth below the ground; passage widths as the volume-weighted local thickness (the
diameter of the largest disc inside the cave covering a block, on every horizontal slice: classes
under 4, 4..10, 10..25, 25..60, 60..120, 120+, and the median and percentiles), over the whole
depth and above Y -60; cave heights (open runs of the columns); wide spaces (3D connected
components of blocks at least 24, 60 and 120 blocks wide) with their extents; connectivity (3D
components of all cave space); water bodies by size and their open surface; entrances; the biomes
of 40 000 random cave blocks (`CaveSurveyBiomes`: the cave slot, vanilla's table near the ground).
Blocks outside the square count as rock. `--locate` lists places of each landform; `--megacaves R`
lists the megacaves within R blocks. About 10..25 s for 768 blocks.

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
times the cave stage per chunk as noise filling calls it; `--places R` (Agent 4.5) lists the
megacaves (fluid, cenote), underground river galleries and their spillway notches within R blocks.

Forest previews: `./gradlew forestPreview --args="..."` (`ForestPreview`, Agent 5; biomes from
vanilla's table on the router's parameters, `PreviewForestBiomes`): `--gallery FILE` every tree
form at three sizes (isometric sheets; `--gallery-seed S` other random details); `--view X Z SIZE
FILE` an isometric view of a square of terrain, water and trees; `--map X Z SIZE SCALE FILE` an
aerial map (crowns by wood over hillshaded ground); `--stats X Z SIZE` tree counts by community,
form and wood, heights, spacing, canopy cover, clearings and every trunk checked against water,
caves and slopes; `--find TYPE R`, `--find-river R`, `--find-treeline R` places of a community,
forested riverbanks and treelines; `--trees X Z R` the trees around a point; `--reach X Z SIZE`
the largest radius of each form in a square against its reach; `--bench X Z CHUNKS`
the model and shapes per chunk; `--order X Z CHUNKS` the trees of every chunk from two systems in
two orders; `--explain LAYER I J X Z N` why a lattice cell has or lacks its tree.

Nature previews: `./gradlew naturePreview --args="..."` (`NaturePreview`, Agent 6; biomes from
vanilla's table and the mod's tag files, `PreviewNatureBiomes`): `--gallery FILE` every formation
kind on flat ground or in a box cave (isometric sheets); `--view X Z SIZE FILE` a square of terrain,
water, trees and surface formations, with the formations by kind, rock and biome; `--cave X Y Z SIZE
HEIGHT FILE` the caves of a box with their ceilings cut away and their formations; `--stats X Z SIZE`
formations by kind at the surface and in the caves per km²; `--find KIND N` formations of a kind
near 0,0; `--list X Z SIZE MIN_REACH` the larger formations of a square (places for screenshots);
`--bench X Z CHUNKS` model and shapes per chunk; `--order X Z CHUNKS` the formations of every chunk
from two systems in two orders on parallel threads; `--runs X Z` the caves' open runs against a
scan; `--mask X0 Z0 SIZE FILE` every formation block of a square (to compare generated worlds at
the formations only); `--who X Y Z` the formations with a block at a position.

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
  `chunks_per_second`; `--poll S` sets how often it checks, default 10 s, use 2 for timings),
  probes the build limits with `setblock`, and stops the server.
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
- `forestcheck.py run/NAME [--json FILE] [--png FILE] [--compare run/OTHER]` (Agent 5, needs
  numpy) reads every block of a world: logs, wood and leaves by wood type; leaves' stored distance
  against the distance recomputed by vanilla's rule (leaves that decay now or would after an
  update, also only 16+ blocks inside the square, where every neighbouring chunk is in the world);
  trunk bases on ground, in water or on air, and connected log bodies (26 neighbours) without
  support; trees per chunk, heights, canopy cover; the forest floor and decorations; neighbour
  changes of the canopy across vs inside chunk borders (seams); `--png` a canopy map; `--compare`
  the share of identical tree blocks with another generation of the same square.
- `blockprobe.py run/NAME X Y Z [--radius R] [--height H]` prints the blocks around a position
  layer by layer, fluids with their level (W0 source, w7 flowing, w8 falling), logs by axis (Ty, Tx,
  Tz), wood (Tw) and leaves with their distance (e1..e7, persistent E1..E7).
- `biomecheck.py run/NAME` (Agent 4.5, needs numpy) counts the biomes of the underground per
  64-block band, the biomes holding the cave space (weighted by cave air and water) with their
  height ranges, and the blocks typical of cave biome features (moss, clay, dripleaf, azalea;
  pointed dripstone; sculk; amethyst), so a cave biome whose features never generate shows up.
- `naturecheck.py run/NAME [--compare run/OTHER] [--json FILE]` (Agent 6, needs numpy) reads every
  block: solid blocks not held face to face by the bedrock floor through other solid blocks
  (floating, 16+ blocks inside the square, by block with examples); pointed dripstone without
  support (would break at the first update) or whose free end is not a tip; huge mushroom faces
  shown against the same block; counts of the formations' materials; with `--compare` the same
  square of another world (before and after) and the seams of the added blocks (an added block
  with an added neighbour across chunk borders vs inside chunks).
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

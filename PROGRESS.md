Vastlands — Development Progress

Current Status

Phase: Terrain complete, ready for Agent 3 (Hydrology)
Project: Vastlands
Minecraft: 1.21.8
Loader: NeoForge 21.8.57

The project builds and all 38 tests pass. The "Vastlands" world type generates large-scale,
Vastlands-shaped terrain from the sea floor (Y ~ -20) to mountain peaks (up to Y ~430) in a world
of Y -256..511, with biomes chosen to fit the terrain. Hydrology, caves and forests are still
passthrough placeholders: there are no rivers yet.

Development Roadmap

- [x] Agent 1 — World Core: Create the NeoForge project foundation, configure shared generation infrastructure, and investigate expanded world height.
- [x] Agent 2 — Terrain: Implement realistic mountains, valleys, plains, and terrain continuity.
- [ ] Agent 3 — Hydrology: Implement large rivers, tributaries, downhill drainage, and waterfalls.
- [ ] Agent 4 — Caves: Implement diverse cave systems and underground formations.
- [ ] Agent 5 — Forests: Implement large forests, giant trees, and tree variations.

Latest Completed Work (Agent 2 — Terrain)

- `TerrainModel`: a deterministic 2D height field (pure Java, own seeded simplex noise with analytic
  gradient, no permutation tables). Continents with warped coastlines, shelves and deep sea floor;
  interior swells and basins; rolling hills; long, connected, curving mountain belts with foothills,
  a main crest, massifs and passes along it, individual peaks, alpine aretes on young ranges and
  rounded old ranges; plateaus with dissected escarpments; a multi-scale gully erosion filter that
  cuts dendritic spurs and side valleys into slopes; plains undulation and small detail; heights
  softly capped towards Y 470. Algorithm, scales and ranges: ARCHITECTURE.md, "Terrain".
- `VastlandsTerrain` implements `TerrainSystem`: density `(surface - y) * 0.125` (one surface per
  column, no floating terrain), a per-thread column cache, and the extended interface for later
  systems: `column(x, z)` (height, base height, masks, gully depth, slope, climate),
  `surfaceHeight(x, z)` (exactly as generated) and `parameter(...)` (router outputs).
- Router: `vastlands:terrain` now gets a constant 0 input (vanilla's sloped_cheese is no longer
  computed); new node type `vastlands:terrain_parameter` feeds `continents`, `erosion`, `ridges`,
  `depth` and `initial_density_without_jaggedness` from the terrain. Top slide moved to 488..504.
- Biomes follow the terrain through vanilla's own biome table: oceans exactly under water, beaches
  and stony shores on coasts, peaks/slopes/meadows/groves on mountains, plateau biomes on plateaus,
  swamps on flat wet lowland, no river biomes (left to Hydrology). Biome depth is relative to the
  Vastlands surface, with the deep dark confined to vanilla's Y -64..0 band.
- `SurfaceRulesContextMixin`: surface rules (grass, sand, snow) use the exact surface height in
  Vastlands worlds, so narrow gully floors are not left as bare stone. Vanilla worlds unchanged.
- F3 screen: a "Vastlands terrain" line (surface, base, mountain, plateau, slope, gully depth).
- Tests: 20 new (TerrainModelTest, VastlandsTerrainTest, TerrainParameterFunctionTest,
  TerrainBiomeTest, new PresetDataTest cases); the passthrough-only router test was replaced.
- Tools: `./gradlew terrainPreview` (maps and perspective views of a seed without the game, expected
  heights export); worldcheck: parallel spawn-ticket generation of large squares with timing,
  `heightmap.py` (relief PNG, chunk-border seams, model comparison), `screenshot.py` (in-game
  screenshots under Xvfb), `nbt.py`.

Files and Modules Affected (Agent 2)

- Code: `worldgen/terrain/*` (new: TerrainModel, TerrainColumn, VastlandsTerrain, ColumnCache,
  SimplexNoise), `worldgen/density/TerrainParameterFunction.java` (new),
  `worldgen/system/{TerrainSystem,TerrainParameter,GenerationSystems,Passthrough}.java`,
  `mixin/SurfaceRulesContextMixin.java` (new), `registry/VastlandsRegistries.java`,
  `worldgen/VastlandsChunkGenerator.java` (F3 line).
- Data: `noise_settings/overworld.json` (router outputs, top slide),
  `density_function/overworld/{terrain,continents,erosion,ridges,depth,initial_density}.json`,
  `vastlands.mixins.json`.
- Tests: `src/test/java/.../worldgen/{PresetDataTest,TerrainBiomeTest}.java`,
  `terrain/{TerrainModelTest,VastlandsTerrainTest}.java`, `density/{StageDensityFunctionTest,
  TerrainParameterFunctionTest}.java`, `system/GenerationSetupTest.java`, `tools/TerrainPreview.java`.
- Build: `build.gradle` (`terrainPreview` task).
- Tools: `tools/worldcheck/{generate,analyze,heightmap,screenshot,nbt,rcon}.py`, README.
- Docs: `ARCHITECTURE.md`, `PROGRESS.md`, `README.md`.

@@VERIFICATION@@

Known Issues

- No rivers, streams or inland lakes above sea level yet; long valleys exist (between ranges, gully
  valleys inside ranges, passes) but carry no water. Inland ground below sea level fills with water
  at Y 63 (aquifers), which makes a few sea-level lakes in basins near coasts. Owner: Hydrology.
- Lava below Y -54 (vanilla fluid picker) and the shifted bottom-anchored features (diamonds at
  -256..-176 etc.) are unchanged; the bottom slide stays at -64..-40, so the rock below -64 is solid
  except for carvers. Owner: Caves.
- Chunk generation is slow on this machine, dominated by vanilla code on the single consecutive
  "worldgen" executor of 1.21.8: surface rules tested for every stone block down to Y -256 and
  vanilla features. See Performance.
- The highest massifs are broad and snowy rather than needle-like; aretes only appear on "young"
  ranges. Cliffs exist at escarpments and canyon heads but there are no overhangs (by design: a
  height field, no floating terrain).
- Vanilla's "experimental settings" warning still appears when creating a Vastlands world.
- Badlands peaks above Y 256 get vanilla's orange terracotta floor (absolute anchor in vanilla's
  surface rule).
- Cloud height is vanilla (192): 4.3% of the land lies above it.
- License is "All Rights Reserved" (MDK default). Change `mod_license` in `gradle.properties` if needed.

Architectural Decisions (Agent 2)

- A 2D height field instead of 3D noise: geographically controllable, one surface per column (no
  floating islands), and a cheap exact height query for later systems. Overhangs are left out.
- Biomes are not independent noise any more: the terrain computes vanilla's climate parameters
  (continentalness, erosion, weirdness, depth) from the landscape, and vanilla's biome table picks
  the biomes. Temperature and humidity stay vanilla. This keeps vanilla biomes and their features
  while making them agree with the terrain.
- Terrain runs as Java behind the existing `vastlands:terrain` node, plus a small node type for
  router outputs; the vanilla `final_density` body (caves, slides) is kept.
- The terrain model is pure Java with its own noise, so it can be rendered and tested without the
  game (`terrainPreview`, fast unit tests) and is independent of vanilla noise changes.
- Gully erosion as a local filter (no simulation, no neighbourhood state): deterministic, seam-free
  and order-independent, at the cost of approximate drainage (gullies follow local slopes; they do
  not form a global river network).
- One small mixin (`SurfaceRulesContextMixin`) because vanilla's surface rule condition cannot be
  replaced from data (its classes are package-private); it only acts when the RandomState has a
  Vastlands context.
- The deep dark is confined to vanilla's band (depth held outside it): performance and gameplay.
- Bottom slide left at -64..-40 (see Known Issues), so this stage does not create lava-flooded caves.

Instructions for Agent 3 (Hydrology)

1. Read ARCHITECTURE.md: "Terrain", "System Contracts" (TerrainSystem contract) and
   "Technical Limitations".
2. Inputs available from `TerrainSystem` (constructor argument of your system):
   - `column(x, z).height()` and `surfaceHeight(x, z)`: the terrain surface. `baseHeight()`: the
     regional ground level without relief, which falls towards the coasts (continent spline) except
     in interior basins; a good drainage reference. `slopeX/slopeZ`: smoothed gradient of the
     uneroded terrain (downhill = minus the slope). `gullyDepth()`: the gully filter's cut; its
     maxima are the natural drainage lines of slopes. `mountain()`, `plateau()`, `continentalness()`
     (0 = coastline, < 0 sea).
   - The landscape density you receive in `HydrologySystem.apply` is `(surface - y) * 0.125`
     (clamped ±128); carve channels by lowering it, and keep `outputRange` conservative.
3. Rivers need a global structure that a local height field does not give: build drainage per
   large region (for example a coarse grid flow accumulation per 4 096-block region, deterministic
   from the seed and the region coordinates, with overlap/blending at region borders), or a
   noise-based river network constrained to run downhill on `baseHeight`. Check every channel
   against the terrain height so rivers never climb.
4. River biomes: the terrain never produces |weirdness| < 0.05. Point the router's `ridges` output
   (`density_function/overworld/ridges.json`) at a Hydrology node that reads the terrain value and
   pulls it into -0.05..0.05 along rivers (and `continentalness`/`erosion` if needed).
5. Keep surface rules and aquifers consistent with carved channels: make
   `GenerationSystems.surfaceHeight` include channel depth (it drives `SurfaceRulesContextMixin`),
   and lower `initial_density` (preliminary surface) along channels, or aquifers may treat channel
   air as underground.
6. Water above sea level is not placed by aquifers: add it in `VastlandsChunkGenerator` (e.g. after
   noise filling) or with a feature; see the class comment.
7. Verify with `./gradlew build`, `terrainPreview` and `tools/worldcheck` (`generate.py --center
   --radius`, `heightmap.py --png --expected`, `screenshot.py`).

Agent 1 — World Core (completed earlier)

Completed Work

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

Known Issues at the end of Agent 1 (current list: see above)

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

Agent 3: Implement the Hydrology system: rivers with tributaries that drain downhill to the sea or
to lakes, lakes in basins, waterfalls, through `HydrologySystem` and the router, using the terrain
queries described above.

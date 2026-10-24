Vastlands — Development Progress

Current Status

Phase: Terrain, geography and hydrology complete, ready for Agent 4 (Caves)
Project: Vastlands
Minecraft: 1.21.8
Loader: NeoForge 21.8.57

The project builds and all 63 tests pass. The "Vastlands" world type generates a
continent-scale geography in a world of Y -256..511: open oceans with shelves, continental slopes,
abyssal plains (Y -22..-30), mid-ocean ridges, seamounts and rare trenches (to Y ~-160); continents
with vast plains provinces, hill country, mountain systems with branch ranges (peaks up to Y ~450),
tablelands and basins; climate zones of thousands of blocks with temperature falling with height and
rain shadows behind ranges; vanilla biomes chosen from landform and climate; polar seas covered by
fractured ice fields; and river networks that drain every continent to the sea: meandering
tributaries joining into broad rivers (up to ~100 blocks wide), lakes in basins and on rivers, rocky ledges
and waterfalls (up to 50..80 blocks off escarpments and in mountains) with round plunge pools,
boulders and rough-walled gorges below them, river and frozen river biomes, frozen rivers in polar
climates. Caves and forests are still passthrough placeholders.

Development Roadmap

- [x] Agent 1 — World Core: Create the NeoForge project foundation, configure shared generation infrastructure, and investigate expanded world height.
- [x] Agent 2 — Terrain: Implement realistic mountains, valleys, plains, and terrain continuity.
- [x] Agent 2.5 — Geography: Continent-scale regions, climate, climate-aware biomes, oceans, trenches and frozen seas.
- [x] Agent 3 — Hydrology: Implement large rivers, tributaries, downhill drainage, and waterfalls.
- [ ] Agent 4 — Caves: Implement diverse cave systems and underground formations.
- [ ] Agent 5 — Forests: Implement large forests, giant trees, and tree variations.

Latest Completed Work (Agent 3 — Hydrology)

- `worldgen/hydrology` (new): rivers, tributaries, lakes and waterfalls: a pure-Java model on top
  of the terrain, a hydrology system for the router and a block step for the water. Algorithm,
  constants and contracts: ARCHITECTURE.md, "Hydrology" and "System Contracts".
  - `DrainageDomain`: drainage on 128-block cells per 16 384-block tile (computed with a 16 384-block
    margin): a breaching priority flood from the sea, so every land cell drains to the sea over the
    lowest pass of every depression; a filling flood for spill levels; lakes in depressions deeper
    than 10 blocks, lower and terminal in dry climates; discharge from climate runoff (more in wet
    climates and mountains); basins owned by the tile that holds their mouth, so rivers do not
    depend on which chunk, thread or tile asks first.
  - `BasinRivers`: rivers through the lowest ground of their cells as smooth curves of 4-block
    segments; width from discharge (about 8 blocks at a source, 90..110 for the largest rivers of a
    continent); levels that never rise downstream and keep both banks above the water; flat pools
    with 2-block ledges in gentle terrain and tall falls with a gorge below in steep terrain (falls of
    40..60 blocks off escarpments and in mountains); sills and plunge pools; hanging-valley falls at
    confluences; rivers meet lakes and the sea at their level; valleys with a floodplain and walls,
    smoothed along the river; meanders, ragged fall lips, round plunge pools and boulders (see
    "Natural shapes" below).
  - `VastlandsHydrology` (`HydrologySystem`): carved density, river biomes (weirdness 0 in channels
    6+ blocks wide: river or frozen river), initial density on channel floors, the surface for
    surface rules, the water of every column (`WaterColumn`), a quick "may hold water" check.
  - `RiverWater`: water above sea level placed after noise filling (before surface rules) and again
    after carving, with sealed beds and banks, fall sills, cut fall ramps and stable fall curtains:
    states vanilla's fluid rules leave as they are, no fluid ticks.
- Router: `ridges`, `depth` and `initial_density_without_jaggedness` read the new node type
  `vastlands:hydrology_parameter`.
- `VastlandsChunkGenerator`: the river water steps; base height and base column queries include
  river and lake water, so structures stand on the water surface; an F3 "Vastlands hydrology" line.
- `TerrainSystem.sample(x, z)`: uncached model columns for planning. `HydrologySystem` extended;
  `Passthrough` no longer implements it. `GenerationSystems.surfaceHeight` is the hydrology's.
- Tools: `./gradlew hydrologyPreview` (rivers, lakes, frozen water and falls over the carved relief;
  tallest falls and widest rivers of a square; `--probe`, `--water` export, `--bench`);
  `tools/worldcheck/rivercheck.py` (water above sea level of a generated world: stability under
  vanilla's fluid rules, agreement with the model, river biomes).

Natural Shapes (Agent 3, second pass)

After review of the first in-game screenshots (rivers like canals: straight, even width, parallel
banks; ledges straight across rivers like dams; falls as rectangular sheets in a straight-cut slot,
with stone pillars beside them; dripstone on gorge floors):
- Meanders (`BasinRivers.meander`): sine-generated curves on the valley line, wavelength 11..16
  river widths, a whole number of half waves per reach (confluences, mouths and lake inflows stay
  put), the phase continued through confluences, wavelength and strength varying along the river;
  the amplitude is held to flat ground beside the valley line (no swinging into hills), to its
  curvature, and to 0 near lakes and the sea. Widths vary along the river and are wider in bends.
- Ragged banks (up to 3 blocks) and valley walls (up to 12 blocks), noise of the column; steep
  walls rise in bands (ledges every 8 blocks of height).
- Fall lips: ragged, bowed upstream into a horseshoe at falls of 6+ blocks, curving downstream
  beyond the banks, so the valley below a fall ends in an amphitheatre and a ledge leaves a curved
  terrace edge, not a straight line across the valley. Plunge pools below tall falls are round and
  wider than the river (no more pillars beside the curtain). A pool shorter than 64 blocks between
  two falls joins the pool below: one tall fall instead of a staircase.
- Boulders (`WaterColumn.Kind.ROCK`; andesite, cobblestone, mossy cobblestone): 2..6 in every
  plunge pool, 1..4 on the lip of tall falls (they split the curtain into strands), a few along
  ledges and in steep streams.
- Rough walls (`VastlandsHydrology.apply`): steep carved faces jut out by up to 5 blocks in ledges
  that change every 8 blocks of height; only outward, so the water, aquifers and the preliminary
  surface keep their guarantees; curtains cut through the rock in their way.
- Biome depth from the carved surface (`depth.json` → `vastlands:hydrology_parameter`): gorge
  floors get surface biomes instead of dripstone and lush caves.
- The block step samples only what holds water (`HydrologyModel.sampleWater`): faster than before.

Playtest Fixes (Agent 3, third pass)

After the first playtest (no lakes seen; strange coasts, "walls or flooding"; pillars at falls;
falls static):
- Lakes: basin lakes from depressions 7+ blocks deep and 2+ cells (was 10 and 3); river lakes: in
  long gentle pools the river widens into an irregular oval lake (up to ~840 × 400 blocks, about
  one per 2 500 blocks of gentle river 5+ blocks wide) where the shore stands above the water.
- Valleys follow the terrain: beyond a flat floor just above the water the valley side is the
  terrain itself, lowered and flattened towards the river (S-curve), instead of a uniform plane
  rising 1 block per 20 (contour rings on sand) and a cone of planar walls ending in a vertical
  step at the valley's edge. Gentle swells on the floor; no ledge bands.
- Coasts: a river ends where its bank first reaches the sea, instead of running on along the
  shore behind a strip of land; meanders near coasts and lakes are held off the water on that
  side instead of switched off (no canal-straight lower courses).
- No pillars at falls: where a pool ends at a cliff (dry ground more than 2 blocks below the
  water beside it), the water column becomes a rocky rim at the water top instead of the dry
  column being raised to the water. Lip and pool boulders 1..2 blocks above the water.
- Churning water: soul sand and a rising bubble column (vanilla's particles and sound) under every
  curtain of 4+ blocks and under 40% of the curtain of 2-block ledges.

Files and Modules Affected (Agent 3)

- New: `worldgen/hydrology/{HydrologyModel,DrainageDomain,BasinRivers,WaterIndex,LruCache,
  VastlandsHydrology,WaterColumn,RiverWater}`, `worldgen/density/HydrologyParameterFunction`.
- Changed: `worldgen/system/{HydrologySystem,TerrainSystem,GenerationSystems,Passthrough}`,
  `worldgen/terrain/VastlandsTerrain` (`sample`), `worldgen/VastlandsChunkGenerator`,
  `registry/VastlandsRegistries`.
- Data: `density_function/overworld/{ridges,initial_density,depth}.json`.
- Tests: `hydrology/{HydrologyModelTest,RiverWaterTest}` (new); `PresetDataTest`, `TerrainBiomeTest`,
  `StageDensityFunctionTest`, `GenerationSetupTest` (hydrology is implemented, river biomes in
  channels); `tools/{HydrologyPreview (new),TerrainPreview}`.
- Build: `build.gradle` (`hydrologyPreview` task).
- Tools and docs: `tools/worldcheck/rivercheck.py` (new), `ARCHITECTURE.md` (Hydrology section and
  updates), `PROGRESS.md`, `README.md`, `docs/hydrology/` (previews and in-game images).

Build and Test Results (Agent 3)

- `./gradlew build`: BUILD SUCCESSFUL (JDK 21, Gradle 9.2.1) on a 4 vCPU container.
- JUnit: 63 tests, 0 failures, 0 skipped: WorldHeightDataTest 3, GenerationSetupTest 3,
  StageDensityFunctionTest 6, TerrainParameterFunctionTest 3, RandomStateBindingTest 3,
  TerrainModelTest 8, VastlandsTerrainTest 7, PresetDataTest 6, TerrainBiomeTest 2,
  GeographyModelTest 7, FrozenSeaTest 6, HydrologyModelTest 8 (new), RiverWaterTest 1 (new).
- `HydrologyModelTest` (seed 20261018, four places and the 2 × 2 tiles around 0,0): 54 535 river
  segments, 396 level steps, 80 falls of 8+ blocks (tallest 50), widest river 97 blocks (plunge
  pools and lakes aside); 182 river mouths and 3 rivers ending in terminal lakes; 86 255 river and
  lake columns, 64 of 4 370 shore edges (1.5%) lower than the water in the model (the block step
  raises the shallow ones by 1..2 blocks and turns the water column beside the deeper ones into a
  rim); off the rivers 263 of 4 000 random grid columns are reshaped (valleys), none raised; the
  quick check skips 9 413 of 10 816 boxes of 20 × 20 columns and never misses water.
- `RiverWaterTest` (test server): 7 areas of 3 × 3 proto-chunks, 9 292 water blocks above sea level
  and 1 000 curtain blocks, none changed by the ported fluid rules (bubble columns count as
  sources), also after a carver-like tunnel; every bubble column stands on soul sand in river water.
- Test server: router weirdness, depth and initial density are the hydrology's; surface biomes:
  river biomes in 548 of 90 000 samples, all in hydrology channels; mean biome run 539 blocks
  (vanilla 166, 593 before rivers).

Verification in Game (Agent 3)

Dedicated server (`tools/worldcheck/generate.py`, seed 20261018, all chunks fully generated), checked
with `rivercheck.py --expected` against the model's water for the same square. After the playtest
fixes:
- fall8 — the plateau fall: 0 unstable blocks from the rivers (5 from the hillside spring); water
  tops equal the model in 90.9% of 4 404 columns, ground 97.2%: the differences are the village on
  the river and the rocky rims, which the model's export still counts as water; bubble columns on
  soul sand under the curtain.
- lake3 — 24×24 chunks at -800,3330: the southern part of a river lake ~860 × 610 blocks in jungle:
  64 104 lake and river columns, water tops 99.72%, ground 99.71%, 0 unstable at the water.
- coast2 — 20×20 chunks at 600,1180: a broad river's mouth on a bay: 0 unstable blocks.
Before them (second pass shapes):
- fall6 / fall7 — 16×16 chunks at -5925,14778: a river falling 50 blocks (water top 146 → 96) off a
  plateau escarpment into a round plunge pool (the tallest fall at the test places). 15 579 source
  blocks above sea level, a curtain of 170..188 lip and 1 047..1 091 falling blocks. Unstable water
  at rivers and lakes: 5..6 blocks, all from a vanilla spring on the hillside and a stray source on
  land beside the village flowing down to the river (71..80 more away from the rivers: springs).
  Water tops equal the model in 96.4% of 4 379 river columns, ground in 98.7%, boulders 100% of 69
  columns; the differences lie under a savanna village built across the river.
- plains3 / plains4 — 24×24 chunks at -200,2700: a broad river (about 80 blocks wide) with ragged
  ledges and meandering tributaries, in jungle. 38 943 river columns: water tops 99.98%, ground
  99.7% equal to the model, boulders 96.7% of 60 columns; 0 unstable blocks at the river in plains4
  (1 in plains3: a gravel bed over a cave had fallen; seals over holes are stone now and gravel or
  sand holding water back gets a stone block under it). 2 423 of 9 210 surface biome cells river.
- polar2 — 16×16 chunks at 33300,-21450: a meandering river in snowy plains: 8 090 columns, water
  and ground 100%, frozen over, 0 unstable.
- Seams: no unstable water on chunk borders from the rivers (1 on a border in fall6/fall7: the
  spring); `RiverWaterTest` checks the borders of 3 × 3 proto-chunks around falls.
- First round (straight channels, before the second pass): fall2, plains1, river1, lake1 and polar1
  matched the model in 93.2..100% of their water columns with 0 unstable blocks at rivers and lakes
  (river1: a 90-block jungle river at sea level; lake1: a lake at Y 88 with rivers falling into it).
- Screenshots (`screenshot.py`, dev client under Xvfb): the plateau fall from downstream, its pool
  and gorge, its lip; the broad river and a ledge; the frozen river (`docs/hydrology/`).
- Not verified: real GPU rendering; multiplayer; seeds other than 20261018 in generated worlds
  (`HydrologyModelTest` uses one seed; the drainage statistics below are from previews); long play
  next to a frozen fall (weather freezing the sill, see Known Issues); structures other than one
  village on a river.

Performance (Agent 3)

- Drainage: the first query in a new area computes the 3 × 3 drainage tiles around it (16 384
  blocks each) in parallel: 1.3..2.2 s of wall time on a 4 vCPU container (0.4..0.6 s of CPU per
  tile), once per 16 384 blocks travelled; rivers of a basin 16 ms on average, on first use.
- Per chunk, once the area's drainage is known (`hydrologyPreview --bench`, one thread): router
  node columns 26..66 µs; water columns of the two block steps 0.22 ms (mountain river) to 0.88 ms
  (broad lowland river); chunks with no river or lake within reach skip the step (`mayHoldWater`).
- End to end: the test squares generate at the same rate as with the first-round shapes (fall
  6.27..6.31 chunks/s, before 6.29; plains 9.37..9.38, before 9.4; polar 8.39). First round, 40 × 40
  chunks around -200,2700 alternated with the branch before hydrology: 13.81 and 13.81 chunks/s
  with hydrology, 13.73 and 13.78 without, no measurable difference.
- Fluids: no fluid ticks are scheduled; the placed water is in the states vanilla's fluid rules
  compute, so loading chunks and block updates next to rivers change nothing.
- Memory: up to 32 drainage tiles (~1 MB each with their rivers), 256 region indexes, 400
  elevation tiles (6.4 MB), a 4 096-entry node cache per worldgen thread.

Known Issues

- Villages and other structures can stand across rivers: their foundations fill parts of the
  channel and paths cross on the water (as vanilla villages next to water). Base height queries
  include the water, so pieces stand on its surface rather than on the channel floor.
- Where the ground beside the water lies lower than the water (1.5% of the shore edges in the
  model at the test places), the block step raises it by 1..2 blocks (a low levee) or turns the
  water column into a rocky rim: pools ending at cliffs get a lip of rock (andesite, cobblestone,
  mossy cobblestone) rather than pillars.
- Coasts away from rivers are the terrain's (Agent 2.5): flat lowland 1..4 blocks above the sea in
  places, with small steps at the shore and few beaches. Not changed here.
- River lakes can be large (up to ~840 × 400 blocks) and take the river biome only within the
  river's own width; there are no small ponds or tarns off the rivers.
- Bubble columns push swimmers up under falls, and boats over them bob (vanilla behaviour of
  upward bubble columns).
- Falls in cold biomes: weather can freeze the sill's water; a block update next to the curtain
  then drains it (vanilla behaviour of flowing water without a source). Frozen rivers themselves
  are vanilla's (frozen river biome, freeze_top_layer).
- River valleys follow 128-block drainage cells through their lowest ground; meanders swing about
  them only where the ground beside is flat, so rivers in hills and narrow valleys run in gentle
  bends. Meander loops never cut off (no oxbow lakes); where two loops at different levels pass
  close, the model's levee keeps a narrow bank between them.
- Rough walls (rock jutting out of steep carved faces) exist only in the game's density, not in the
  2D model: previews and `rivercheck.py --expected` see the smooth faces.
- No streams below a discharge of 24 runoff-weighted cells (about 0.4 km² of wet land): small
  brooks and springs are vanilla's (spring features). Dry regions have few rivers and no dry beds.
- River biomes only where a channel is 6+ blocks wide; in mid/far-inland mountains (vanilla erosion
  E0/E1) vanilla's table puts the valley's middle biome instead of a river biome, as in vanilla
  mountain valleys. Lakes keep the surrounding biome.
- A basin that reaches more than ~16 000 blocks beyond its owning tile has no rivers (none seen; the
  largest measured reach from a mouth is 15 100 blocks).
- Ravines and caves near rivers below sea level can hold vanilla water flowing from aquifers
  (seen in river1, on dry land); river water itself is sealed in.
- Changing any hydrology constant changes generation: worlds created before get seams at rivers.
- Unchanged from earlier agents: lava below Y -54 and the bottom slide (Caves), iceberg melt pools,
  deep frozen oceans with rain, corals on trench floors, monument pillars (unverified), badlands
  terracotta bands, noise-shaped climate zones, rarer high ground, experimental-settings warning,
  cloud height 192, `screenshot.py` lost input, license "All Rights Reserved".

Architectural Decisions (Agent 3)

- Drainage on coarse tiles with basin ownership instead of local rules: rivers need a global
  structure (where water comes from, where it goes), and a tile of 128 × 128 cells with a margin
  as wide holds every basin measured. Each basin is computed by exactly one tile, from pure
  functions of the seed, so results never depend on generation order; nothing reads chunks.
- Breaching (not filling) for routing, filling only to find lakes: shallow depressions are cut
  through by their river (a short gorge), deep ones hold lakes; plain filling would have made lakes
  of 9% of the land.
- Water levels from the real terrain beside the channel, as a running minimum: rivers never rise
  and never run above their banks; levees are only a fallback.
- Levels `seaTop + 2k`: two water surfaces are equal or at least 2 blocks apart, which is what keeps
  vanilla's source conversion from flooding a river along a 1-block step. Falls are vertical with a
  curtain in the exact states vanilla computes, so nothing needs a fluid tick.
- The water is a block step (vanilla's aquifers only fill to sea level), placed before surface rules
  so channel floors get vanilla's underwater materials, and again after carvers; every decision
  depends only on the model for the column and its neighbours, so chunks agree across borders.
- River biomes through weirdness only (vanilla's valley slice), so vanilla's own table picks river,
  frozen river, swamp or the valley's middle biome; no custom biomes.
- Base heights include river water, like vanilla's include the sea, so structures do not end up on
  channel floors.

Instructions for Agent 4 (Caves)

1. Read ARCHITECTURE.md: "Hydrology" (the block step and its rules), "System Contracts"
   (HydrologySystem), "Generation Pipeline" and "Technical Limitations".
2. The caves stage wraps the final density, which already contains the hydrology's channels,
   valleys and levees. Keep cave openings out of river and lake beds: `HydrologySystem.water(x, z)`
   says where water lies (`placed()` for river and lake water, `groundTop`, `waterTop`), and
   `mayHoldWater(minX, minZ, maxX, maxZ)` is a cheap check for a box. `RiverWater` seals only the
   block under the water and the blocks beside it at water heights (after noise filling and after
   carvers): a cave that passes under a bed or beside a bank is sealed off, but a cave room right
   under a river shows a one-block "ceiling" of sealed bed.
3. If you add block steps (for example the fix for vanilla's lava below Y -54), run them before the
   second `RiverWater.place` in `VastlandsChunkGenerator.applyCarvers`, or leave columns with
   `water(x, z).placed()` alone above their ground.
4. `GenerationSystems.surfaceHeight` is the hydrology's surface. If caves reshape the surface
   itself (entrances, sinkholes), surface rules should see it: extend it there.
5. Rivers and lakes are all at Y 62 or above. Underground water is vanilla's aquifers; if you add
   underground lakes or rivers, keep them sealed from river beds (water surfaces 1 block apart and
   touching would flood, see Hydrology).
6. Biome depth is measured from the carved surface (`depth.json` is a hydrology parameter). On
   steep carved faces the density juts out above `surfaceHeight` (rough walls, never below it):
   cave entrances cut into gorge walls see that rock. Boulders are `WaterColumn.Kind.ROCK`
   columns (andesite, cobblestone, mossy cobblestone) placed by `RiverWater`, which also turns
   water columns beside deep drops into rock rims and puts soul sand and bubble columns under
   falls: a cave opening under a plunge pool must keep the soul sand (or the bubbles die).
7. Verify with `rivercheck.py` that caves did not open river water (0 unstable blocks from the
   rivers and lakes themselves in the test worlds of Agent 3; at the plateau fall a few blocks of
   a vanilla spring flowing into the river count too) and with `RiverWaterTest`. Seals over holes
   are stone, and sand or gravel that holds water back over a hole gets a stone block under it
   (`RiverWater.support`): keep that if caves change what lies under beds and banks.

Agent 2.5 — Geography (completed earlier)

Completed Work (Agent 2.5)

- `GeographyModel` (new package `worldgen/geography`, pure Java): the macro layer of the terrain,
  extracted from `TerrainModel` with Agent 2's noises and seeds, and extended:
  - Ocean regions (42 000 blocks) on top of the continents: open oceans 15 000–25 000 blocks across
    as well as inland seas; ~36% of the surface is sea (Agent 2: 26%).
  - Sea floor: shelf (~500 blocks, under 15 deep), continental slope and rise, abyssal plains at
    Y -22..-30; mid-ocean ridges with a central rift, seamounts and guyots (always submerged); rare
    trenches along long arcs, down to Y ~-160 (0.19% of the sea floor below Y -45).
  - Provinces (16 000 blocks): vast plains provinces (few hills, low undulation, mountain systems
    weakened) and hill country; dry provinces with more tablelands, rockier, less gullied ranges.
  - Mountain systems: Agent 2's belts plus branch ranges springing from them.
  - Climate: temperature zones (26 000 blocks) with a lapse rate of one band per ~150 blocks;
    moisture zones (17 000 blocks), wetter coasts, drier interiors, rain shadows from the mountain
    crests upwind of a slowly turning prevailing wind.
  - `GeographySample` (one documented record per column) and `Landform` (14 classes).
- `BiomeClimate`: all five surface biome parameters from landform and climate (temperature and
  vegetation are new terrain-owned router outputs). Deserts need warmth and dryness, hot humid land
  gets savanna, forest and jungle, temperate drylands are steppe (plains), polar seas are frozen
  oceans. Vanilla biomes only; biome regions are 3.6× longer than vanilla's along transects.
- Trenches in the world: the bottom slide is a terrain node that follows trench floors
  (`bottom_slide`), and `OceanSurface.fillDeepWater` replaces the lava, aquifer lid and carver lava
  that vanilla puts into water below Y -54 (after noise filling and after carving).
- Frozen seas: `FrozenSea` (two warped Voronoi networks: floes, cracks, leads, pressure ridges,
  thick multi-year floes, a breaking-up margin) laid over polar sea columns after decoration
  (`OceanSurface.placeIceFields`); `ServerLevelMixin` keeps weather from freezing the cracks shut.
- `TerrainSystem.geography()` and `TerrainColumn.geography()`: the API for Hydrology (see
  ARCHITECTURE.md, Geography, Interfaces for Hydrology).
- F3: a "Vastlands geography" line (landform, temperature, sea temperature, moisture, rain shadow,
  dryness, plains, ice).
- `GeographySettings`: region scale, ocean bias, plains bias, lapse rate, rain shadow, trench gate.
- Tools: `terrainPreview` renders `landform`, `biome` (vanilla's table on the terrain's climate),
  `temperature`, `moisture` and `ice` maps, prints landform/climate/biome shares, the mean biome run
  and the highest peaks, finds places (`--find desert`), exports the expected ice cover (`--cover`);
  `tools/worldcheck/oceancheck.py` checks sea surface vs the ice model, ice seams, lava and air in
  the water column and the model floor.

Files and Modules Affected (Agent 2.5)

- New: `worldgen/geography/{GeographyModel,GeographySample,GeographySettings,Landform,FrozenSea}`,
  `worldgen/noise/{SimplexNoise (moved from terrain),NoiseMath}`, `worldgen/terrain/BiomeClimate`,
  `worldgen/ocean/OceanSurface`, `mixin/ServerLevelMixin`.
- Changed: `worldgen/terrain/{TerrainModel,TerrainColumn,VastlandsTerrain}`,
  `worldgen/system/{TerrainSystem,TerrainParameter}`, `worldgen/VastlandsChunkGenerator`,
  `vastlands.mixins.json`.
- Data: `noise_settings/overworld.json` (temperature, vegetation, bottom slide),
  `density_function/overworld/{temperature,vegetation,bottom_slide}.json` (new).
- Tests: `geography/{GeographyModelTest,FrozenSeaTest}` (new), `TerrainModelTest` (sea share,
  lowland share and minimum height ranges for the geography), `PresetDataTest` (temperature and
  vegetation are terrain-owned; trench test), `TerrainBiomeTest` (climate consistency, region size
  against vanilla), `tools/TerrainPreview`.
- Tools and docs: `tools/worldcheck/oceancheck.py` (new), `ARCHITECTURE.md` (Geography section and
  updates), `PROGRESS.md`, `README.md`, `docs/geography/` (previews and in-game images).

Build and Test Results (Agent 2.5)

- `./gradlew build`: BUILD SUCCESSFUL (JDK 21.0.12, Gradle 9.2.1).
- JUnit: 54 tests, 0 failures, 0 skipped: WorldHeightDataTest 3, GenerationSetupTest 3, StageDensityFunctionTest 6,
  TerrainParameterFunctionTest 3, RandomStateBindingTest 3, TerrainModelTest 8, VastlandsTerrainTest 7,
  PresetDataTest 6, TerrainBiomeTest 2, GeographyModelTest 7, FrozenSeaTest 6.
- Geography (4 seeds, ±200 000 blocks): sea 30–44%; longest open sea on 60 000-block transects
  15 600–24 800 blocks; plains provinces run 2 200–3 000 blocks on average; climate zones ~3 000;
  shelf depth under 15, abyssal depth 65–110 (mean); no submarine feature above sea level − 6; trench
  floor 0.19% of the sea floor, deepest Y -155; no field jumps within 1/20 block.
- Ice fields (model): ice 82–88% of a firm field, largest crack network 85–95% of the open water,
  isolated holes under 10 blocks under 0.1%; ice falls monotonically towards the warm edge; neighbour
  changes across chunk borders equal those inside chunks.
- Test server: router temperature/vegetation equal the terrain's values; trench water open from the
  floor (Y -157 at -82240,-191808) to Y -30 in the final density, rock below; surface biomes: every
  underwater column ocean, no dry one, all above Y 300 highland, no rivers; every frozen ocean
  polar, every desert warm, every jungle humid; mean biome run 593 blocks vs 166 for vanilla.

Verification in Game (Agent 2.5)

Dedicated server (`tools/worldcheck/generate.py`, seed 20261018, all chunks fully generated):
- ice5 — 16×16 chunks at 30208,-19712 (frozen ocean, ice strength ~1; final ice model): the sea
  surface (y 62) agrees with the ice model in 100% of the 9 876 open-sea columns more than 16 blocks
  from an iceberg, 93.4% overall (vanilla's frozen-ocean icebergs cover ~18% of the square and are
  left standing); no lava or air in the water. Seams: where no iceberg interferes the world equals
  the model block for block, and the model has no notion of chunks (`FrozenSeaTest`: neighbour
  changes across chunk borders equal those inside chunks over 1 024² blocks). The raw neighbour
  statistic of this small square (0.061 across chunk borders vs 0.052 inside) is dominated by
  icebergs and their skirts; away from icebergs only 511 border pairs remain (0.045 vs 0.028),
  too few to compare. Earlier worlds ice1–ice4 found the problems fixed since: diagonal cracks
  drawn as dashes of single blocks touching at their corners (seen in screenshots), hairline
  break-up cracks.
- Weather and cracks (weak evidence, world ice2 with an earlier pattern): after ~3 minutes of a
  client next to the ice field (random tick speed 3), 1 of ~20 600 surface water blocks had frozen; vanilla's edge freezing would have
  closed crack edges in that time.
- trench5 — 16×16 chunks at -82240,-191808 (trench in a warm ocean): floor down to Y -162; the floor
  equals the model within 1 block in 96.6% of the columns, within 3 in 98.3%; mean neighbour step
  0.756 inside chunks vs 0.765 across borders; the water column from the model floor to the
  surface holds only water, corals and sea plants, plus a sunken geode and a shipwreck; lava touches
  the sea only in cave mouths in the floor (0.4% of the columns). The first test worlds showed the
  problems this fixed: lava in the open water below Y -54, an aquifer lid of rock and air at
  Y -46..-54 over the trench, lava tunnels from carvers through the water.
- coast1 — 32×32 chunks at 4560,3200 (island shore, continental slope, abyssal plain): floor to
  Y -43; ground equals the model within 1 block in 89.2%, within 3 in 94.6% (the rest: carver
  pits, ravines, structures); no lava in the water; 40 of 188 675 open-sea columns (0.02%) are dry
  shoreline dips; neighbour steps 0.408 inside vs 0.396 across chunk borders.
- pm1 — 32×32 chunks at 2992,16400 (hills and foothills of a range, Y 39..188): ground equals the
  model within 1 block in 90.3%, within 3 in 93.5%; steps 0.559 inside vs 0.556 across borders;
  old growth birch, birch and ordinary forest (temperate, humid).
- dry1 — 24×24 chunks at -12512,-10704 (summit of a range in a hot, arid region, Y 215..403):
  within 1 block in 96.9%; steps 0.483 vs 0.488; stony peaks at Y 200–250, frozen peaks above
  (temperature lapse), surrounded by desert and badlands in the previews.
- Generation speed (spawn-ticket generation, this container): 12.6 chunks/s (ice, trench, 16×16),
  16.7 (coast, 32×32), 12.5 (hills, 32×32), 11.3 (mountain summit, 24×24). Agent 2 measured
  2–3 chunks/s in an earlier session; the machine differs, see Performance below for a
  same-machine comparison of the terrain code.
- Screenshots (`tools/worldcheck/screenshot.py`, dev client under Xvfb with Mesa llvmpipe, HUD
  hidden): ice field with cracks, a lead and icebergs, and floes close up (ice5); a warm desert
  shore with the light shelf and dark deep water (coast1); badlands with terracotta bands on the
  flank of a dry range (dry2, 16×16 chunks at -12900,-10400) and its frozen summit (dry1); old
  growth birch forest in the hills (pm1). In `docs/geography/`, with model previews.
- Not verified: the F3 "Vastlands geography" line (HUD hidden in all shots); real GPU rendering;
  multiplayer with a separate client; seeds other than 20261018 in generated worlds (the model
  tests sample 4 seeds); an ocean monument in a deep basin; long play in an ice field.

Performance (Agent 2.5)

Same machine (this container, 4 vCPU), pure-Java harness, 64 squares of 512×512 blocks on the
4-block grid as chunk generation samples them, after warm-up:
- Agent 2's `TerrainModel` (origin/main): 5.4 µs per column per thread; with the geography:
  7.3 µs (+35%: the climate with three upwind rain-shadow samples, provinces, ocean floor features).
  Agent 2's JFR profile put all Vastlands code at 2.4% of chunk generation CPU, so this is about
  1% of generation time. Not re-profiled with JFR.
- Geography queries for Hydrology: `smoothElevation` 1.5 µs, `sample` 3.2 µs,
  `seaLevelTemperature` 0.13 µs per call per thread.
- Block steps: the trench step loops only over columns whose floor is below Y -30 (none on
  land); the ice step returns after four temperature samples unless the chunk is near the freezing
  band, then evaluates the fracture pattern for 256 columns (two 5×5 Voronoi searches each).
- End to end (`generate.py`, spawn-ticket generation including features and light): 8.4–16.7
  chunks/s for the test squares above. No same-machine run of `main` for comparison.
- Memory: each cached `TerrainColumn` now holds a `GeographySample` (estimated from object sizes:
  ~1.2 MB per worldgen thread for the 4 096-entry cache instead of ~0.5 MB).

Known Issues at the end of Agent 2.5 (current list: see Agent 3 above)

- No rivers, streams or inland lakes above sea level yet (Hydrology). Interior basins below sea level
  hold sea-level lakes (aquifers), as before.
- Below Y -54 vanilla's fluid picker still fills caves with lava; only open trench water is fixed.
  Where a cave opens into a trench floor, lava meets the sea (obsidian, magma). Owner: Caves.
- The bottom slide is still vanilla's -64..-40 except under trenches. Owner: Caves.
- Vanilla's frozen-ocean icebergs (surface rules) cover ~17% of polar seas; vanilla's iceberg
  feature can carve melt pools into the ice field of a neighbouring chunk that was decorated
  earlier, which makes the ice next to some icebergs depend on generation order (like vanilla trees
  and ore blobs on chunk borders).
- Deep frozen oceans (ice strength below 0.3) have rain instead of snow (vanilla biome temperature);
  firm polar seas are frozen oceans.
- Warm-ocean corals grow on any sea floor, including trench floors at Y -150 (vanilla features).
- Ocean monuments in deep basins should stand on tall prismarine pillars (vanilla's monument fills
  its base down to the floor); read from the code, not seen in a test world.
- Desert mountains are badlands on their slopes, with the badlands' absolute terracotta bands
  (Agent 2's note about badlands above Y 256 still applies, less often because high ground cools).
- A few shoreline dips below sea level stay dry (0.02% of the open-sea columns in the coast test).
- Climate zones are noise-shaped (no latitude): the coldest and hottest regions are roundish blobs
  of 5 000–15 000 blocks; frozen seas follow them.
- High ground is rarer than with Agent 2's terrain: 1.6% of the world above Y 200 (Agent 2: 2.7%)
  and 0.25% above Y 300 (0.5%), because more of the world is sea and plains provinces weaken
  mountain systems. The highest peaks still reach Y ~450. Tunable: `plainsBias`, `oceanBias`, the
  activity factor in `GeographyModel.macro`.
- Agent 2's documented massif at 6750,-1250 (docs/terrain) is now under the sea: the ocean regions
  moved coastlines. Worlds created before this change get seams at the border to new chunks.
- Unchanged from Agent 2: experimental-settings warning, cloud height 192, `screenshot.py` lost
  input, license "All Rights Reserved".

Architectural Decisions (Agent 2.5)

- Geography as its own pure-Java layer under the terrain (not a fourth unrelated noise): the
  terrain's macro shape is the geography's `elevation`, the climate reads that elevation, and the
  biome parameters read both. Agent 2's noises and seeds are kept, so its mountains are unchanged
  where the ocean regions did not move the coast.
- Vanilla's biome table instead of a custom biome source: biome features, structures, spawns and
  `/locate` stay vanilla; the geography supplies all five surface parameters. Two small couplings
  (dryness → desert, humidity → jungle) and the temperate-steppe rule fix the vanilla table's
  temperature-only deserts and temperate-arid flower forests. No custom biomes yet.
- Ocean features and trenches as part of the height field (no 3D structures), so they are
  continuous, testable without the game, and Hydrology sees them.
- The bottom slide as a terrain parameter: vanilla's band stays everywhere except under trenches, so
  the deep rock (and Agent 4's future lava fix) is unaffected elsewhere.
- Block steps in `VastlandsChunkGenerator` (after noise, after carvers, after decoration) for what
  density cannot express: water in trenches, ice fields. All are pure functions of the column.
- Ice fields after the chunk's own decoration, and a narrow mixin on weather freezing: vanilla's
  freezing would otherwise close every crack.
- Configuration in code (`GeographySettings`), not a config file, so a world's terrain never
  depends on a mutable file; scales are one multiplier for previews and tuning.
- Rejected: parallel ranges along mountain flanks (crest lines at fixed offsets from the belt axis).
  Both variants tried (phase from the belt field, and from its distance) made thin, fingerprint-like
  ridges where the domain warp squeezes a belt; branch ranges give connected systems without it.

Instructions for Agent 3 (Hydrology)

1. Read ARCHITECTURE.md: "Geography" (especially "Interfaces for Hydrology"), "Terrain",
   "System Contracts" and "Technical Limitations".
2. Inputs (the `TerrainSystem` passed to your system):
   - Coarse planning: `terrain.geography()` — `smoothElevation(x, z)` (smooth regional elevation,
     the potential rivers must descend), `sample(x, z)` (`GeographySample`: elevation, baseHeight,
     continentalness, landform, plains/hilliness/dryness/basin/plateau, mountain fields, temperature,
     moisture, rain shadow, wind), `seaLevelTemperature(x, z)`. Pure functions, no chunk access:
     sample them on a coarse grid (32–64 blocks) per region with a margin.
   - Exact surface: `column(x, z)` (with `.geography()`), `surfaceHeight(x, z)`, `slopeX/slopeZ`,
     `gullyDepth()` (natural drainage lines on slopes), as described by Agent 2.
   - Rivers end on the shelf or at the coast (landform SHELF/COAST, continentalness ~0); lakes belong
     in BASIN landforms (which already dip below sea level); waterfalls fit plateau escarpments
     (`plateau` falls from 1 to 0) and steep mountain ground; river size can follow `moisture`
     (runoff) times drainage area; dry provinces (`dryness`) suit ephemeral or fewer rivers; rivers
     freeze where `FrozenSea.strength(temperature)` > 0 (frozen-river biomes need the polar band).
   - The landscape density you receive in `HydrologySystem.apply` is `(surface - y) * 0.125`
     (clamped ±128); carve channels by lowering it, keep `outputRange` conservative.
3. Rivers need a global structure: build drainage per large region (coarse flow accumulation on
   `smoothElevation`, deterministic from the seed and region coordinates, blended at region
   borders), or a network constrained to run downhill. Check every channel against the terrain
   height so rivers never climb.
4. River biomes: the terrain never produces |weirdness| < 0.05. Point the router's `ridges` output
   at a Hydrology node that reads the terrain value and pulls it into -0.05..0.05 along rivers; the
   other four parameters (temperature, vegetation, continentalness, erosion) are terrain-owned too.
5. Keep surface rules and aquifers consistent with carved channels: include channel depth in
   `GenerationSystems.surfaceHeight` (drives `SurfaceRulesContextMixin`) and lower `initial_density`
   along channels.
6. Water above sea level is not placed by aquifers: add it in `VastlandsChunkGenerator` (it already
   has the noise, carver and decoration hooks used by `OceanSurface`) or with a feature.
7. Verify with `./gradlew build`, `terrainPreview` (`--maps relief,landform,biome`) and
   `tools/worldcheck` (`generate.py`, `heightmap.py --expected`, `oceancheck.py`, `screenshot.py`).

Agent 2 — Terrain (completed earlier)

Completed Work (Agent 2)

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
- Tests: 18 → 39 (new: TerrainModelTest 8, VastlandsTerrainTest 7, TerrainParameterFunctionTest 3,
  TerrainBiomeTest 1, PresetDataTest 3; the passthrough-only router test was replaced).
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
- Docs: `ARCHITECTURE.md`, `PROGRESS.md`, `README.md`, `docs/terrain/` (previews, heightmap,
  in-game screenshots).

Build and Test Results (Agent 2)

- `./gradlew build`: BUILD SUCCESSFUL (JDK 21.0.12, Gradle 9.2.1).
- JUnit: 39 tests, 0 failures, 0 skipped: WorldHeightDataTest 3, GenerationSetupTest 3,
  StageDensityFunctionTest 6, TerrainParameterFunctionTest 3, RandomStateBindingTest 3,
  TerrainModelTest 8, VastlandsTerrainTest 7, PresetDataTest 5, TerrainBiomeTest 1.
- TerrainBiomeTest on the test server, 90 000 surface columns: all 21 007 underwater columns have
  ocean biomes, none of 65 962 dry columns has one, all 364 columns above Y 300 have mountain or
  highland biomes, 0 river biomes.
- Continuity (4 seeds): the largest step between neighbouring columns is 10.3 blocks (canyon
  walls), the mean 0.16, and no height jumps within 0.05 blocks. These tests caught three real
  discontinuities during development (gully kernel wider than its cell window, plateau-dependent
  gully strength, the alpine factor switching off at belt edges); all fixed.

Verification in Game (Agent 2)

Dedicated server (`tools/worldcheck/generate.py`, spawn-ticket generation, seed 20261018 unless noted):
- s1 — seed 1, 16×16 chunks at 0,0, final code: bedrock floor at Y -256 in every column with
  vanilla's 5-layer gradient (-256..-252); `setblock` works at 511, 320, 319, -64, -65, -256 and
  fails at 512 and -257. Composition: -256..-65 deepslate 94.1%, lava 1.3% (Agent 1: 1.0%; the
  bottom slide is unchanged), 63..255 air 69.7%, 256..511 air 100%. Ground 83..166 (rolling
  hills); neighbour steps 0.198 inside chunks vs 0.201 across chunk borders.
- c1, c1b, c1r — 32×32 chunks around 6750,-1250, a coastal massif with foothills:
  - Determinism: c1 and c1b (same code, separate server runs) have identical ground in 100.000% of
    262 144 columns.
  - c1r (final code): ground 85..379, mean 261. The generated ground equals the model's surface in
    84.0% of the columns and is within 1 block in 90.6%, within 3 in 95.3%; the rest is lower
    (cave entrances and ravines cut into the surface). No seams: mean neighbour step 0.564 inside
    chunks vs 0.530 across chunk borders.
  - Surface (c1): grass on 88..97% of the ground below Y 200 (with a village in the foothills), a
    snow line at 200..250, snow and packed ice above 250 (frozen/jagged peaks, snowy slopes); on
    steep ground grass 63%, stone 13% — narrow gully floors get grass (surface rule mixin).
- m1 — 32×32 chunks around 300,5000 (an earlier version of the model, without crags/roughness): a
  high massif in a cold zone, ground 98..432; within 1 block of the model in 91.8% of the columns;
  neighbour steps 0.761 inside vs 0.747 across chunk borders; snow 84%, packed ice 10%, stone 1.1%
  of gentle ground.

Client (`tools/worldcheck/screenshot.py`: dev client under Xvfb with Mesa llvmpipe, quick play
into the server-generated worlds, spectator, render distance 16): screenshots of m1 and c1r. The
c1r set (final code) is in `docs/terrain/` together with relief maps (terrain preview and region
heightmaps). The F3 "Vastlands terrain" line was not checked visually (HUD hidden in the shots).

Performance (details: ARCHITECTURE.md, Terrain, Performance):
- Model: 10..30 µs per column per thread; under 1 ms per chunk with the column cache.
- JFR, chunk generation in a mountain area: Vastlands code 2.4% of CPU samples; vanilla surface
  rules 36%, features, noise filling 13%, aquifers 4.5%.
- End to end, 32×32 chunks: 2.65..2.82 chunks/s (Vastlands, mountain massif, mean Y 261; one rerun
  with interference 2.16) vs 3.13 chunks/s for the passthrough baseline on `main` at the same seed
  and square (vanilla-shaped ground, mean Y 70). 16×16 chunks at 0,0, seed 1: 2.18 vs 1.81. Runs
  of identical code differ by up to 25%; generation is bound by 1.21.8's single consecutive
  "worldgen" executor in both cases.

Not verified:
- Real GPU rendering and frame rate, multiplayer with a separate client, Windows/macOS.
- Terrain far from the origin in real worlds: generated squares were at 0,0, 300,5000 and
  6750,-1250; the model tests sample ±200 000 blocks.
- Seeds other than 20261018 and 1 in generated worlds (the model tests sample 4 seeds).
- Interaction with other worldgen mods.

Known Issues at the end of Agent 2 (current list: see above)

- No rivers, streams or inland lakes above sea level yet; long valleys exist (between ranges, gully
  valleys inside ranges, passes) but carry no water. Inland ground below sea level fills with water
  at Y 63 (aquifers), which makes a few sea-level lakes in basins near coasts. Owner: Hydrology.
- Lava below Y -54 (vanilla fluid picker) and the shifted bottom-anchored features (diamonds at
  -256..-176 etc.) are unchanged; the bottom slide stays at -64..-40, so the rock below -64 is solid
  except for carvers. Owner: Caves.
- Chunk generation is slow on this machine, dominated by vanilla code on the single consecutive
  "worldgen" executor of 1.21.8: surface rules tested for every stone block down to Y -256 and
  vanilla features. See Performance.
- In game, the biggest massifs read as broad, dome-like mountains from a distance rather than
  needle peaks, and long uniform slopes still show even block terraces in places (ground roughness
  and crags break them up only partly). Aretes appear only on "young" ranges. Cliffs exist at
  escarpments and canyon heads but there are no overhangs (by design: a height field, no floating
  terrain). Tuning the peak shapes further is possible in `TerrainModel` (envelope profile, peaks,
  aretes, gully depth).
- Gully erosion is a local filter: its valleys follow local slopes and look drainage-like, but they
  do not form one connected river network; Hydrology needs its own routing.
- `screenshot.py` types commands into a laggy software-rendered client; occasionally a command is
  lost and a shot repeats the previous view.
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
- Docs: `ARCHITECTURE.md`, `PROGRESS.md`, `README.md`, `docs/terrain/` (previews, heightmap,
  in-game screenshots).

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

Agent 4: Implement the Cave system: large caverns, tunnels, vertical shafts and cave formations
through `CaveSystem` and the router, including the fix for vanilla's lava below Y -54 and the bottom
slide, keeping river and lake beds sealed (Instructions for Agent 4).

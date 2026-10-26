Vastlands — Development Progress

Current Status

Phase: Terrain, geography, hydrology, caves (overhauled by Agent 4.5) and forests complete, ready for Agent 6 (Natural Wonders)
Project: Vastlands
Minecraft: 1.21.8
Loader: NeoForge 21.8.57

The project builds and all 76 tests pass. The "Vastlands" world type generates a
continent-scale geography in a world of Y -256..511: open oceans with shelves, continental slopes,
abyssal plains (Y -22..-30), mid-ocean ridges, seamounts and rare trenches (to Y ~-160); continents
with vast plains provinces, hill country, mountain systems with branch ranges (peaks up to Y ~450),
tablelands and basins; climate zones of thousands of blocks with temperature falling with height and
rain shadows behind ranges; vanilla biomes chosen from landform and climate; polar seas covered by
fractured ice fields; and river networks that drain every continent to the sea: meandering
tributaries joining into broad rivers (up to ~100 blocks wide), lakes in basins and on rivers, rocky ledges
and waterfalls (up to 50..80 blocks off escarpments and in mountains) with round plunge pools,
boulders and rough-walled gorges below them, river and frozen river biomes, frozen rivers in polar
climates; and an underground world through the whole depth: multi-level networks of tunnels
mostly 6..16 blocks wide shaped by the rock and by regional cave regimes (labyrinths, quiet and
nearly solid regions), ramps, shafts and pits between levels, galleries 25..60 blocks wide, major
corridors 60..110 and colossal ones, megacaves hundreds of blocks across (halls, lake caverns,
canyons, abysses, two-floor complexes, pillar forests) with connectors and cenotes, cave biomes in
provinces (lush caves, dripstone, the deep dark; other mods' cave biomes through biome tags),
entrances in hillsides, cliffs, dolines and the sea floor, level underground lakes, flooded
networks and megacave lakes, underground rivers falling over dams, deep lava lakes with volcanic
rock below the aquitard, natural river tunnels and bridges, swallets, grottoes behind falls, sea
caves and blue holes; and forests that follow the biomes, the climate and the ground: communities
blended across biome borders, regional belts of denser and thinner forest, young and old stands,
clearings and meadows, soft edges, groves on the plains, gallery forests along rivers with trees
leaning over the water, a treeline that thins to krummholz; 18 tree forms from vanilla blocks
(branching oaks and birches, ancient oaks with crowns up to ~28 blocks across, spruces, pines,
giant spruces, pines and jungle trees, dark oaks, acacias, swamp oaks, shrubs, snags, fallen logs,
huge mushrooms) and a forest floor; nothing grows in rivers, lakes, waterfalls or cave openings,
and every tree crosses chunk borders whole in any generation order.

Development Roadmap

- [x] Agent 1 — World Core: Create the NeoForge project foundation, configure shared generation infrastructure, and investigate expanded world height.
- [x] Agent 2 — Terrain: Implement realistic mountains, valleys, plains, and terrain continuity.
- [x] Agent 2.5 — Geography: Continent-scale regions, climate, climate-aware biomes, oceans, trenches and frozen seas.
- [x] Agent 3 — Hydrology: Implement large rivers, tributaries, downhill drainage, and waterfalls.
- [x] Agent 4 — Caves: Implement diverse cave systems and underground formations.
- [x] Agent 4.5 — Underground overhaul: wider passages, density, galleries, megacaves, cave biomes, verticality, underground water.
- [x] Agent 5 — Forests: Implement large forests, giant trees, and tree variations.
- [ ] Agent 6 — Natural Wonders: formations in the megacaves and at the surface (see Instructions for Agent 6).

Latest Completed Work (Agent 5 — Forests)

Before (measured on `main`, same seed and squares, with `tools/worldcheck/forestcheck.py`): the
forest system was a passthrough; every biome kept vanilla's tree features. In a temperate square
by a lake (-1 700,-800) the trees were 5 / 7 / 11 blocks tall (p10 / median / p90, tallest 16,
none of 20+), 26% of the columns under a canopy, vanilla's oak and birch shapes with no regional
structure: the same density and the same few vanilla shapes from one biome border to the
next, trees on riverbanks and lakeshores like anywhere else, forests ending in a hard line at
biome borders.

Work done (code: `worldgen/forest`; algorithm, constants and contracts: ARCHITECTURE.md,
"Forests"):
- `ForestModel` (pure Java, like the terrain, hydrology and cave models): forest communities from
  the surface biomes (biome tags `#vastlands:forest/<community>`: temperate, flower, birch,
  old_birch, dark, boreal, old_spruce, old_pine, grassland, savanna, jungle, sparse_jungle, swamp,
  highland, grove, alpine, dry; river biomes transparent) blended over ~40 blocks on a 16-block
  grid; the density from the communities' capacity and the climate's moisture, regional cover
  (1 700 blocks), groves on open land (190), stand age (950: young dense stands, old growth with
  giants, snags, logs and gaps), clearings (openings 115, meadows 480, gaps of fallen giants 30),
  the topographic position (exposed crests thinner, sheltered valleys denser), the coast,
  riparian woods within 18 blocks of rivers and lakes (gallery forests across open land, wet
  meadows on some floodplains), and an unclamped temperature for the treeline (the geography's is
  clamped at -1): forests thin from -0.78 to nothing at -1.15 in bands and tongues, shorter trees
  and krummholz below that, polar lowland taiga kept.
- Trees on a jittered 4-block lattice with a non-recursive priority yield (giants keep room,
  shrubs fill gaps, trunks never touch), a 3-block lattice of shrubs and a 16-block one of fallen
  logs; species patches of ~95 blocks inside communities (birch stands in oak woods, spruce in
  mixed forest, pine patches in taiga); size, form and shrubs from age and edge (forest edges
  step down instead of standing like a wall).
- Site rules: dry trunk columns on level ground (slope limits by form), no trunk on eroded banks,
  no cave space under a trunk or at the lip of an opening (cave mouths, dolines, cenotes, ravines),
  no tree within its crown plus 8 blocks of a waterfall, only in managed biomes.
- `TreeShapes`: 18 forms from vanilla logs, wood and leaves: branching oaks and birches with
  lumpy, skewed crowns, ancient oaks (2 × 2 flared trunks, elbowed limbs, crowns up to ~28 blocks
  across), slender and old birches, whorled spruces, pines, giant spruces and pines, wind-shaped
  krummholz, dark oaks with layered canopies, tall and giant jungle trees with vines and cocoa,
  acacias, swamp oaks, shrubs, snags, fallen logs with stumps and moss, huge mushrooms. Riverside
  trees lean out over the water. Leaves carry vanilla's distance (never decay), bee nests and
  vines like vanilla's.
- `ForestBlocks` (feature `vastlands:forest`, in the vegetal decoration of the managed biomes):
  each chunk builds its own blocks of every tree reaching it (trees cross chunk borders whole, no
  cross-chunk writes, any order); trunks grow down onto the real ground, flared roots and trunk
  extensions never rest on water, melons, pumpkins and bamboo give way to trunks; podzol, coarse
  and rooted dirt and moss around giants; whole trees left out where they would overlap a surface
  structure; a forest floor by community, canopy and moisture (leaf litter, ferns, grass, bushes,
  mushrooms, moss, dry grass).
- Vanilla isolation: vanilla's tree features (and surface lava lakes) skip the managed biomes in
  Vastlands worlds only (`#vastlands:forest/displaced`, `PlacedFeatureMixin`); underground lava
  lakes skip the 24 blocks under them (`#vastlands:forest/buried`: one opened out of a hillside
  in taiga and set the forest on fire in a screenshot session).
- Exact biome lookups (`ExactClimateSearch`, `ClimateRTreeMixin`): vanilla's climate index keeps
  the thread's last result on an exact tie, so the same column could get two biomes depending on
  what the thread looked up before; found when two generations of a square differed by whole
  trees.
- Tools: `./gradlew forestPreview` (gallery, isometric views, aerial maps, statistics, places,
  reach, order check, explain, bench), `tools/worldcheck/forestcheck.py` (leaf distances, trunk
  support, floating logs, trees per chunk, heights, canopy, floor, seams, comparison of two
  generations), `generate.py --poll`, F3 line "Vastlands forest".

Profiling and fixes on the way (JFR on the dedicated server, river square at -2 368,768):
- The first in-game version cost 10.7 ms per decorated chunk on this host. Half of it was the
  community grid: 12 interpolated terrain queries per node for the topographic position (each up
  to 4 terrain columns, evicting the terrain's per-thread column cache). Now the grid points carry
  the terrain model's height and climate (one uncached query per point), read bilinearly: 7.2 ms.
- Then half of the rest was `land.surface`, which built a full cave column for every query to
  find the caves' roofs over gorges, where no tree stands anyway: the hydrology's surface: 5.3 ms.
- Then a third was building the shapes of trees that could not reach the chunk (every tree
  within 16 blocks): per-form reaches (measured over ~170 000 trees, clipped in the builder) and
  an exact early exit in the crown noise: 3.8 ms. The feature is now ~3% of the worker threads'
  samples; site checks, shapes, model and block writes share it.

Files and Modules Affected (Agent 5)

- New `worldgen/forest/`: `ForestModel`, `ForestType`, `TreeForm`, `Wood`, `TreeSpec`,
  `TreeShape`, `TreeShapes`, `TreeBlocks`, `TreeRandom`, `ForestSample`, `ForestSettings`,
  `ForestCache`, `VastlandsForests`, `ForestBiomes`, `ForestFeature`, `ForestBlocks`,
  `VanillaTrees`, `ExactClimateSearch`.
- New mixins: `PlacedFeatureMixin`, `PlacedFeatureAccessor`, `ClimateRTreeMixin`
  (`vastlands.mixins.json`).
- Changed: `system/ForestSystem` (contract: `treeDensity`, `forestAt`, `trees`),
  `system/GenerationSystems` (`VastlandsForests`), `registry/VastlandsRegistries` (feature
  `vastlands:forest`, placement modifier `vastlands:outside_forests`), `VastlandsChunkGenerator`
  (F3 line).
- Data: `data/vastlands/tags/worldgen/biome/forest/*.json` (communities, transparent, all),
  `data/vastlands/tags/worldgen/placed_feature/forest/{displaced,buried}.json`,
  `data/vastlands/worldgen/{configured,placed}_feature/forest.json`,
  `data/vastlands/neoforge/biome_modifier/forest.json`.
- Tests and tools: `worldgen/forest/ForestModelTest`, `worldgen/ForestGameTest`,
  `GenerationSetupTest` (all four systems implemented), `tools/ForestPreview`,
  `tools/PreviewForestBiomes`, `tools/IsoRenderer`, `build.gradle` (`forestPreview`),
  `tools/worldcheck/forestcheck.py` (new), `generate.py` (`--poll`), `blockprobe.py` (logs, wood
  and leaves in the legend).
- Docs: ARCHITECTURE.md (Forests, contracts, layout, isolation, testing), README.md,
  `docs/forests/` (screenshots and previews).

Build and Test Results (Agent 5)

`./gradlew build`: BUILD SUCCESSFUL; 76 tests (Agent 4.5's 70, `ForestModelTest` 5, `ForestGameTest`
1), 0 failures, 0 skipped (20 minutes here, with a dev client running beside it). Measured by the
forest tests (seed 20261018):
- Determinism: `ForestModelTest` two models, rows on one thread vs shuffled on parallel threads, at
  four places: every chunk identical; `ForestGameTest` (the world's real biome source and the
  mixins): 800 chunks, 13 327 trees reaching them, 0 chunks differ.
- Shapes: 720 shapes of every form with random parameters, 194 876 leaves, every one at the
  distance vanilla would compute and within its form's reach; vines held, cocoa on jungle logs,
  room in front of bee nests.
- Sites: 9 944 trees at four places (916 within 18 blocks of rivers and lakes) on dry, level
  enough ground, no cave under a trunk, no fall within the crown, only in managed biomes.
- Spacing: 5 401 standing trees of the main lattice, no pair closer than the mean of their
  spacings (closest 2.83 blocks), 488 of 22+ blocks; woods by community: temperate oak 646, birch
  265, spruce 589; dark forest dark oak 852, oak 96, birch 29, huge mushrooms 54; boreal spruce
  1 761, birch 255; old growth spruce 776; jungle 1 358 jungle trees, 53 oaks; swamp 100 oaks.
- Density by surface biome (mean over a grid): dark forest 0.62, jungle 0.61, birch forest 0.60,
  taiga 0.56, snowy taiga 0.55, forest 0.52, old growth birch 0.51, old growth pine taiga 0.46,
  river 0.38 (river biomes take the woods around them), sparse jungle 0.33, old growth spruce
  taiga 0.31, flower forest 0.15, sunflower plains 0.14, meadow 0.10, savanna 0.08, plains 0.06,
  bamboo jungle 0.02 (vanilla's), desert 0.00; open land beside rivers and lakes 0.49 against 0.05
  away from them.

Verification in Game (Agent 5)

Dedicated dev server (`tools/worldcheck/generate.py --poll 2`, seed 20261018, 24 × 24 chunks fully
generated per square, final code), checked with `forestcheck.py` (every block of the square):

| Square (world) | Centre | Forests there | Generation s (chunks/s) | Feature ms per chunk | Trees per chunk | Height p10/50/90/max | Trees 20+ | Canopy cover |
|---|---|---|---|---|---|---|---|---|
| t1 | -1 700,-800 | oak, birch and spruce woods by a lake | 60.6 (9.50) | 3.17 | 2.97 | 9 / 14 / 19 / 29 | 162 | 34% |
| j1 | 783,1 892 | jungle on both banks of a river, bamboo jungle around | 54.7 (10.53) | 6.07 | 4.82 | 7 / 24 / 32 / 37 | 1 777 | 53% |
| b1 | -4 142,3 009 | birch forest, oak, dark forest | 54.5 (10.57) | 3.89 | 9.05 | 10 / 12 / 17 / 22 | 47 | 74% |
| r1 | -2 368,768 | taiga with a meandering river | 58.5 (9.85) | 3.58 | 5.85 | 10 / 15 / 20 / 31 | 350 | 63% |
| m1 | -4 320,-992 | snowy spruce forest on high ground (Y ~230), lakes | 64.6 (8.92) | 2.45 | 5.36 | 6 / 9 / 12 / 16 | 0 | 53% |
| s1 | 11 096,3 943 | savanna: acacias, oaks, shrubs | 53.8 (10.71) | 1.78 | 1.91 | 2 / 7 / 9 / 11 | 0 | 20% |
| g1 | -5 403,1 586 | snowy taiga and groves with rivers (Y ~180) | 58.6 (9.83) | 2.75 | 4.78 | 8 / 12 / 15 / 20 | 6 | 49% |
| d1 | 288,32 | old growth spruce taiga, dark forest, a coast | 50.3 (11.45) | 3.57 | 2.57 | 7 / 15 / 28 / 38 | 448 | 41% |
| c1 | 716,448 | dark forest and swamp around a cenote | 52.2 (11.03) | 3.93 | 4.46 | 8 / 10 / 12 / 19 | 0 | 72% |

"Trees" are trunk bases counted in the world (shrubs and logs included), heights from the base to
the highest block of the tree; "feature" is `ForestFeature`'s own timing per decorated chunk.
No exception in any server log.

- Leaves: every Vastlands leaf is stored at the distance vanilla would compute; leaves at
  distance 7 (decaying now): 0 in six squares; the 2 in the birch square and 1 in the cenote
  square are azalea leaves (vanilla's azalea trees over lush caves), the 558 in the jungle square
  are vanilla's trees in the bamboo jungle (599 on `main`). Leaves whose path to a log is cut (they
  would decay after a neighbour update), 16+ blocks inside the squares: 0 in four squares, 1..3 in
  four (crowns whose low leaves meet a mossy boulder or the ground), 401 in the jungle square (the
  bamboo jungle again: 392 on `main`).
- Trunks: no trunk stands in water in any square (an ancient oak's flared roots over a
  lake and a jungle giant's on a riverbank did before the fix below); connected log bodies without
  support 16+ blocks inside the squares: 0 in seven squares, 2 in the jungle square (vanilla's
  trees in the bamboo jungle, 4 on `main`), 1 in the cenote square (a single huge-mushroom cap
  block left inside an oak's crown: leaves win over caps).
- Seams: the canopy changes as often across chunk borders as inside chunks in every square
  (share of tree blocks with a tree block beside them inside chunks / across
  chunk borders along x: t1 0.661 / 0.660, j1 0.567 / 0.583, b1 0.598 / 0.597, r1 0.624 / 0.625,
  m1 0.580 / 0.580, s1 0.521 / 0.511, g1 0.595 / 0.601, d1 0.687 / 0.681, c1 0.665 / 0.670; the
  same along z).
- Determinism in game: the lake square generated twice with the final code: 230 309 tree blocks 16+
  blocks inside the square, 100% identical (`forestcheck.py --compare`); `ForestGameTest`: 800
  chunks queried in a shuffled order on parallel threads, 0 differ; `forestPreview --order`: 576
  chunks, 0 differ.
- Water and caves: `rivercheck.py` on the river square and the temperate lake square: no
  unstable water source; the flowing water a fluid tick would change is the same as on `main` in
  the same square (79 there, 80 with forests in the lake square: the 3..5 differing blocks are a
  spring's lip, not next to any tree block). `cavecheck.py` on the cenote square: 0 unstable water
  sources in caves, 1 at the surface on a chunk border (vanilla spring), as before.
- Before and after with identical seeds (`main` in a worktree, same squares, `forestcheck.py`):
  

  | Square | `main` (vanilla trees): trees per chunk, height p10/50/90/max, 20+, cover | Vastlands forests |
  |---|---|---|
  | t1 lake woods | 2.94, 5 / 7 / 11 / 16, 0, 26% (oak 6 811, birch 1 969, spruce 495 upright logs) | 2.97, 9 / 14 / 19 / 29, 162, 34% (oak 7 215, birch 2 888, spruce 7 576) |
  | r1 taiga river | 5.63, 7 / 9 / 11 / 16, 0, 49% | 5.85, 10 / 15 / 20 / 31, 350, 63% |
  | d1 old growth taiga, dark forest | 2.20, 8 / 10 / 23 / 30, 213, 31% | 2.57, 7 / 15 / 28 / 38, 448, 41% |
  | j1 jungle river | 5.92, 5 / 12 / 26 / 33, 790, 55% | 4.82, 7 / 24 / 32 / 37, 1 777, 53% |
  | m1 snowy mountain spruce | 5.03, 8 / 9 / 11 / 14, 0, 45% | 5.36, 6 / 9 / 12 / 16, 0, 53% |

  The floor changes with the trees: in the lake square leaf litter 36 454 → 15 156 (vanilla's
  litter patches carpet the ground; the Vastlands floor puts it under broadleaf canopies only),
  short grass 2 173 → 7 922, tall grass 0 → 2 160, moss carpets 0 → 6 342, ferns 58 → 2 843,
  bushes 126 → 1 104, mushrooms 6 → 153.
- Screenshots (dev client under Xvfb with Mesa software rendering, `screenshot.py`):
  eleven views in `docs/forests/` (list and descriptions in its README):
  a taiga river meander from above (no fire: the earlier session with the burning slope is what
  found the lava lakes), the same river from over the water with oaks leaning across, a lake
  shore of mixed woods, a dark forest between beach dunes, old growth spruce taiga ending at the
  beach, a cenote in a dark forest with the trees clear of its rim, jungle on a riverbank, a
  savanna, a snow line in a spruce forest, a birch and oak canopy, a snow-laden mountain spruce
  forest; previews from the model (every form, isometric views, aerial maps). Looked at one by
  one: no trunk in water, no tree over the cenote or in the rivers, no cut crown visible (the
  seam numbers above are the real check: from these distances a cut would be hard to see). Weak
  points seen: forest edges along beaches still read as a line of trees from a
  distance, the savanna's acacias look alike, dark forest canopies are flat from above (as
  vanilla's).
- Found and fixed in game during this pass: trunk bases replaced by bamboo stalks in jungles (the
  upper trunk floated; bamboo now gives way and the stalk above a log goes too); ancient oaks'
  flared roots and trunk extensions resting on a lake's water (now left out over water); a forest
  fire from an underground lava lake opened on a slope (`#vastlands:forest/buried`).

Performance (Agent 5)

This container (4 vCPU Xeon @ 2.8 GHz; it was moved to a slower host during the session, so only
numbers from the same host are compared), `generate.py --poll 2`, 24 × 24 chunks:
- The feature (`ForestFeature` timing): 1.8 ms per decorated chunk in the savanna, 2.5..3.9 in
  the temperate, boreal, dark and mountain squares, 6.1 in the jungle (11 trees reaching a chunk,
  giants of ~900 blocks). On the river square the profiling steps took it from 10.7 to 7.2, 5.3
  and 3.6 ms (see "Profiling and fixes" above); the shapes, the site checks, the model and the
  block writes now share it, and it is ~3% of the worldgen workers' JFR samples.
- End to end, same squares alternated with `main` (worktree of `190bfb5`) on the same host: the
  lake square 60.6, 54.6 and 50.1 s against 54.4, 48.0 and 52.2 s; the jungle square 54.7 against
  56.3 s; the old growth taiga square 50.3 against 50.2 s; the river square 56.4, 58.8 and 58.5
  against 54.2 s. So 0..8% slower where forests are dense, within the run-to-run spread (±10%) in
  most single runs. Features run on the consecutive worldgen executor, so the feature's cost adds
  to the total directly; vanilla's tree features no longer run in the managed biomes.
- Memory: per worldgen thread the forest caches (grid points, nodes, weights, candidates, falls,
  shapes; open addressing, fixed sizes) hold about 10 MB; nothing grows with the explored area.
- Tests: `ForestModelTest` runs in ~105 s (it was 187 s before the profiling fixes).

Known Issues

- Forests (Agent 5):
  - Biomes outside the communities keep vanilla's trees: bamboo jungles, cherry groves, mangrove
    swamps, pale gardens, mushroom fields, and every modded biome until a tag lists it. Crowns of
    Vastlands trees near such a border can overhang into it. The floating log bodies and decaying
    leaves `forestcheck.py` finds in the jungle square are vanilla's trees in a bamboo jungle (the
    same counts on `main`).
  - A leaf's path to its log can be cut where the terrain or another tree's log takes a block of a
    crown: the stored distance stays valid until a neighbour update, then a few leaves decay
    (0..3 recomputed-to-decay leaves per 24 × 24 chunks inside the squares).
  - Underground structures (mineshafts, ancient cities, trial chambers) do not keep trees off the
    ground above them; surface structures are avoided only by their horizontal footprint (a tree
    stays out even where a structure lies well below its crown).
  - No trees on natural bridges and over natural tunnels (the forests read the hydrology's
    surface, which is the river there).
  - Vanilla lava springs (rare near the surface in a 768-block world) can still set a forest on
    fire; underground lava lakes deeper than 24 blocks stay vanilla.
  - Sincerelyuq's Biomes: not available here (no jar or source; see below), so none of its
    biomes is in a community. With the mod installed its biomes keep their own trees; to let
    Vastlands grow trees there, add the mod's real biome ids to a community tag (a datapack,
    optional entries). Not tested with the mod. Sincerelyuq's Overhaul (a datapack) and Radiance
    were not available either: structures are avoided through vanilla's structure starts and the
    trees use standard block states and vanilla's light step, but neither was tested.
  - Changing any forest constant, tag or shape changes generation (seams against existing
    chunks). Worlds made before Agent 5 get the new forests only in new chunks.
  - Not verified: real GPU rendering (screenshots are from software rendering), multiplayer, long
    play (leaf decay after many ticks, fire spread), other mods' biomes in the community tags.
- Underground (unchanged from Agent 4.5): see "Known Issues at the end of Agent 4.5" below.

Architectural Decisions (Agent 5)

- A pure model with a thin block step, like the other systems: everything is a function of the
  seed and the position, measured and rendered without the game; the block step only writes each
  chunk's own blocks of every tree that reaches it. No cross-chunk writes, no generation-order
  dependence, no global caches; per-thread caches keyed by exact grid coordinates.
- Biomes as the switch, by tags: the forests manage only the biomes in a community tag, and
  vanilla's trees step aside exactly there (one filter appended to the tagged placed features), so
  other mods' biomes and features stay untouched unless a datapack opts them in.
- Trees as specs and shapes: a candidate's tree is a small record (`TreeSpec`) whose shape is a
  pure function, so any chunk can build any tree's blocks, tests can check every shape, and
  previews show exactly what the game builds.
- Per-form reach instead of one global radius: the query looks 16 blocks around a chunk, but a
  chunk only builds the shapes of trees that can reach it (the builder enforces each reach, so the
  bound is exact by construction).
- Exact biome searches only inside the forests' lookups (a mixin active within
  `ExactClimateSearch.call`): vanilla keeps its shortcut everywhere else.

Instructions for Agent 6 (Natural Wonders)

1. Agent 4.5's instructions below still hold (megacaves, `MegacaveInfo`, `isCave`, `caveFluid`;
   formations as features scoped to Vastlands worlds; 0 unstable cave water).
2. The surface around cave mouths and cenotes is already clear of trunks (no cave space under a
   trunk, no big crown at the lip); waterfalls and their plunge pools are kept free within the
   crown plus 8 blocks. A wonder at the surface (a cenote's rim, a gorge, a giant tree) can read
   `ForestSystem.forestAt(x, z)` for the community, density, clearing and age, and
   `trees(minX, minZ, maxX, maxZ, consumer)` with `VastlandsForests.model().shape(spec)` for the
   exact blocks of every tree, before or after the chunk is decorated.
3. Good places for vegetation in the underground: lush-cave megacaves and the floors of halls
   and lake caverns under moist climates (underground forests of azalea, moss and glow berries),
   hanging roots and vines under cenotes opening into a forest, ferns and moss at cave mouths in
   humid forests. Place them as features scoped to Vastlands worlds; to keep trees out of a
   wonder's footprint at the surface, extend `ForestModel.siteProblem` (a new reason) rather than
   removing blocks after the forest step.
4. Verify with `forestcheck.py` (leaves, trunk support, seams) and `rivercheck.py` /
   `cavecheck.py` that nothing a wonder adds breaks the forests or the water.


Agent 4.5 — Underground Overhaul (completed earlier)

Completed Work (Agent 4.5)

Root causes of Agent 4's problems (found by reading the code and measuring it with the new
`caveSurvey`, baseline in a worktree of `main`):
- Narrow passages: network tunnels had a radius of (2.5 + 2.2 karst + 0.8 fracture) × 0.7..1.3, one
  in seven halved, on a single 64-block lattice: the volume-weighted median width was 7..10 blocks
  in every square measured, and no passage class existed above it. Wide spaces came only from
  junction chambers (radius 8..29, mostly near 8) and deep "enormous caverns".
- Too few caves: thin tunnels × presence 0.3 + 0.62 abundance × link 0.26 + 0.5 abundance, with
  plains provinces (× 0.55) and a quiet field (× 0.25..1.1) on top: 1.1..3.5% of the rock open,
  0.7% under plains; 24% of plains columns had any cave at all.
- No megacaves: "enormous caverns" were radius 45..110 (u², mostly ~50), 20..52 high, 70% of them
  centred at Y -200..-130 below the aquitard, the upper ones capped at radius 80; on horizontal
  slices no space reached 120 blocks anywhere (0 in all 10 squares).
- Biome diversity: the overworld kept `minecraft:multi_noise`; underground biomes came from
  vanilla's table on the depth parameter, which Agent 2 holds at 0.95 below ~120 blocks (to stop
  the deep dark filling everything). Dripstone needs continentalness ≥ 0.8, so far inland it took
  88..99% of all cave blocks at every depth; elsewhere none; lush caves only where humidity ≥ 0.7;
  deep dark only in vanilla's Y -64..0 band under low erosion (≤ 0.8% of cave blocks); everything
  else below ~120 blocks had the surface biome (dark forest at Y -150, river and beach biomes deep
  underground).
- Repetition: one tunnel recipe on one lattice, 7 levels relative to the base level (plains: one
  to three, none between Y -52 and +10), and caverns that were all flattened domes.
- Chasms broke up far from the origin: the joint sets rotated a stretched noise about the world's
  origin by a slowly turning strike, which shifts it by |r| × the turn per block (~8 blocks per
  block at r ≈ 17 000), so beyond a few thousand blocks the cracks became clouds of isolated
  single-block holes (found in a slice at -12 750,-11 030).
- Performance trap found on the way: the per-thread caches were direct-mapped; two keys sharing a
  slot rebuild each other on every query (harmless for cheap network cells, fatal for anything
  expensive: an early megacave version was rebuilt 3 072 times on one thread).

Work done (code: `worldgen/caves`, `worldgen/biome`; algorithm, constants and contracts:
ARCHITECTURE.md, "Caves"):
- `CaveSettings` (rewritten): frequency, density, passageWidth, largePassages, chambers,
  megacaves, megacaveScale, vertical, regionVariation, water, spillways, lava, entrances, seaCaves,
  riverTunnels, caveBiomes (Agent 4's abundance, giantCaverns and fractures are folded into them).
- `CaveGeology`: regional cave regimes: `density` (labyrinths 1.5, quiet regions 0.25), `scale`
  (regions of large and of narrow passages), `vertical`.
- `CaveNetwork`: log-normal tunnel radii (most tunnels 6..16 wide, a tail to ~30, one in eight a
  squeeze), presence and links from the density, nine upper levels (two new ones at -116 and -88
  below the base level), ramps between levels, pits and longer shafts, widened junction rooms, more
  and larger entrances (adits up to radius 18, sea-cave mouths up to 17, dolines up to 8).
- `Galleries` (new): a second lattice (288 blocks) of trunk passages in two levels: large passages
  25..60 wide, major corridors 60..110, colossal ones, canyons in fractured rock, meanders and
  constrictions, vaulted junction halls, very large mouths, and underground rivers that fall over
  every dam they cross.
- `Megacaves` (new, replaces `Caverns`): six kinds hundreds of blocks across in two stacked zones,
  vaults checked ring by ring against the ground (tall under peaks, low where valleys cross the
  rim: abysses ~175 and complexes ~135 blocks tall under mountains), abysses with a bell-shaped
  vault, their own fluid domain (lakes with islands, submerged caverns, lava lakes, dry), pillars,
  partitions, shelves (two floors), natural bridges, terraced walls with alcoves and overhangs,
  connectors and cenotes.
- `FluidDomains`: megacave domains with dams and spillways on their footprint; `water` setting.
- `Fractures`: joint frames anchored on a 2 400-block grid and blended (fixes the broken cracks far
  from the origin), wider chasms (up to ~12 blocks), more open joints where `vertical` is high.
- `CaveColumn`/`CaveModel`/`CavePrimitive`: up to 64 pieces pruned by their horizontal reach and
  indexed by 16-block slabs, terrace caches, no wall noise deep inside caves, river notches.
- `KeyedCache` (new): open-addressing per-thread cache for lattice cells, nodes, megacaves and
  provinces.
- Cave biomes: `CaveBiomes` and `CaveBiome` (new): provinces in three layers, megacave themes, the
  deep dark kept to Y -150..0 and out of flooded rock, lush caves checked against the column's own
  climate; `VastlandsBiomeSource` (new, `vastlands:caves`): the delegate's surface biomes and the
  slots' biomes below, slots from the biome tags
  `#vastlands:cave/{lush,dripstone,deep_dark,crystal,volcanic,other}`; Sincerelyuq's Biomes as
  optional tag entries (ids unverified, see Known Issues).
- `GenerationContext`, `RandomStateMixin`, `NoiseChunkMixin` (new): climate samplers (the
  `RandomState`'s and every chunk's cached one) lead the biome source to its world's caves.
- API for later systems: `VastlandsCaves.caveBiome`, `megacaves` (`MegacaveInfo`),
  `riverGalleries`, `census`.
- Tools: `./gradlew caveSurvey` (new, `CaveSurvey`, `CaveSurveyBiomes`), `cavePreview --places`,
  `tools/worldcheck/biomecheck.py` (new).

Files and Modules Affected (Agent 4.5)

- New: `worldgen/caves/{Galleries,Megacaves,MegacaveInfo,CaveBiomes,CaveBiome,KeyedCache}`,
  `worldgen/biome/VastlandsBiomeSource`, `mixin/NoiseChunkMixin`,
  `data/vastlands/tags/worldgen/biome/cave/*.json` (6 tags).
- Changed: `worldgen/caves/{CaveSettings,CaveGeology,CaveNetwork,CavePrimitive,CaveColumn,CaveModel,
  FluidDomains,Fractures,VastlandsCaves}`, `worldgen/context/GenerationContext`,
  `mixin/RandomStateMixin`, `registry/VastlandsRegistries` (biome source type),
  `vastlands.mixins.json`, `world_preset/vastlands.json` (biome source).
- Removed: `worldgen/caves/Caverns` (replaced by `Megacaves`).
- Tests: `caves/CaveModelTest` (new places, thresholds, galleries and megacaves),
  `CaveBiomeTest` (new); tools `CaveSurvey`, `CaveSurveyBiomes` (new), `CavePreview` (`--places`).
- Build: `build.gradle` (`caveSurvey` task). Tools and docs: `tools/worldcheck/biomecheck.py`
  (new), ARCHITECTURE.md (Caves rewritten, layout, preset, pipeline, contracts, isolation, testing),
  PROGRESS.md, README.md, `docs/caves/` (new previews).

Build and Test Results (Agent 4.5)

- `./gradlew build`: success, 70 tests pass (69 before + `CaveBiomeTest`): WorldHeightDataTest 3,
  GenerationSetupTest 3, StageDensityFunctionTest 6, TerrainParameterFunctionTest 3,
  RandomStateBindingTest 3, PresetDataTest 6, TerrainBiomeTest 2, CaveBiomeTest 1, TerrainModelTest 8,
  VastlandsTerrainTest 7, GeographyModelTest 7, FrozenSeaTest 6, HydrologyModelTest 8,
  RiverWaterTest 1, CaveModelTest 6.
- `CaveModelTest` (seed 20261018): same blocks from any thread and order; 0 unstable fluid blocks
  at 15 places (Agent 4's seven, megacaves with lakes and cenotes at -2314,-2353, 716,448 and
  -1794,1648, a flooded one at -464,-1541, a lava one at -236,-1832, a dry canyon at -1343,-680,
  river galleries at -704,224 and -500,330), 97 designed spillway blocks among them; river and lake
  beds intact; 31 sea caves with a mouth at the sea (13 blue holes), all 31 open to the sea and
  stable; 0 cave lava above Y -54; cave share 6.20% of the rock between Y -240 and the ground over
  4 000 random columns of a 32 768-block square (Agent 4: 1.56%), 1 867 of them without caves
  (Agent 4: 2 747); 256 network cells: 4 669 tunnel pieces, 371 chambers (81 of radius 30+);
  megacaves in 20 × 20 cells of 1 024 blocks: 180 upper, 173 deep; galleries of all classes and
  river galleries over four 3 072-block squares.
- `CaveBiomeTest` (test server): the preset resolves `vastlands:caves`; without other mods the slots
  are `lush [minecraft:lush_caves], dripstone [minecraft:dripstone_caves], deep_dark
  [minecraft:deep_dark]`, crystal falls back to dripstone, volcanic and other to plain rock; a
  chunk's cached sampler gives the same biomes as the `RandomState`'s; surface biomes unchanged
  (0 of 6 561 differ); over 81 643 underground samples of 48 000² blocks: lush caves
  5.6%, dripstone (with crystal) 40.4%, the deep dark 2.1%, present at Y -27 in 232 of 5 372 land
  columns (4.3%: ancient cities can start there); no lush caves under frozen climates; mean cave
  biome run along rows 120 blocks deep ~400 blocks.
- `TerrainBiomeTest`, `PresetDataTest`: unchanged results through the new biome source (mean biome
  run 539 blocks against vanilla's 166; ocean biomes exactly under water; 548 river samples, all in
  channels).

Before and after (`./gradlew caveSurvey --args="--seed S --center X Z --size 768"`, every block of
768 × 768 columns from Y -251 to the ground; widths are the volume-weighted local thickness on
horizontal slices; "spaces" are 3D connected components of blocks at least that wide; Agent 4's
code from a worktree of `main`, same seeds and squares):

| Square (seed: centre) | ground | cave share | median / p90 width | volume ≥25 wide (all, above Y -60) | ≥60 wide | spaces 60+ / 120+ wide | open cave water surface | entrance columns |
|---|---|---|---|---|---|---|---|---|
| 1: -768,-768 | plains, Y 100 | 0.7% → 2.0% | 7 / 30 → 9 / 49 | 14%, 4% → 26%, 18% | 0% → 5% | 0 / 0 → 1 / 0 | 1025 → 17502 | 0 → 0 |
| 1: 0,0 | foothills, Y 138 | 2.2% → 11.8% | 7 / 27 → 28 / 97 | 12%, 9% → 54%, 50% | 0% → 26% | 0 / 0 → 22 / 1 | 26887 → 141930 | 337 → 4611 |
| 20261018: -11520,-10752 | high mountains, Y 274 | 2.6% → 10.5% | 7 / 40 → 17 / 56 | 21%, 12% → 38%, 38% | 2% → 9% | 2 / 0 → 18 / 0 | 11008 → 68165 | 5534 → 16135 |
| 20261018: -2304,0 | foothills, Y 151 | 3.5% → 20.9% | 7 / 36 → 27 / 77 | 18%, 10% → 54%, 50% | 1% → 20% | 1 / 0 → 42 / 1 | 16929 → 171241 | 7059 → 20324 |
| 20261018: -4608,-3072 | plateau, Y 146 | 2.9% → 5.0% | 9 / 51 → 14 / 47 | 25%, 14% → 31%, 26% | 8% → 4% | 5 / 0 → 5 / 0 | 9428 → 23013 | 4765 → 11705 |
| 20261018: 0,0 | foothills, Y 93 | 2.9% → 4.8% | 9 / 51 → 13 / 67 | 28%, 12% → 35%, 24% | 8% → 14% | 3 / 0 → 7 / 0 | 26858 → 48948 | 5147 → 11433 |
| 20261018: 0,1536 | plains, Y 96 | 1.1% → 3.2% | 7 / 23 → 11 / 147 | 9%, 10% → 38%, 9% | 0% → 31% | 0 / 0 → 1 / 1 | 895 → 4270 | 0 → 0 |
| 20261018: 1536,-768 | shelf, Y 59 | 2.1% → 6.6% | 10 / 48 → 19 / 59 | 28%, 17% → 42%, 36% | 7% → 10% | 2 / 0 → 9 / 0 | 5923 → 20313 | 9358 → 19075 |
| 42: -1536,-1536 | foothills, Y 109 | 1.4% → 4.4% | 7 / 31 → 9 / 73 | 15%, 14% → 29%, 20% | 0% → 13% | 0 / 0 → 4 / 0 | 4786 → 32042 | 3918 → 12120 |
| 42: 1536,-1536 | hills, Y 84 | 1.7% → 4.5% | 7 / 28 → 10 / 39 | 13%, 5% → 25%, 25% | 1% → 1% | 0 / 0 → 2 / 0 | 10271 → 33038 | 1037 → 3645 |
| mean of the 10 | | 2.1% → 7.4% | 7.7 / 36.5 → 15.7 / 71.1 | 18%, 11% → 37%, 30% | 2.7% → 13.4% | | | |

Cave biomes of 40 000 random cave blocks per square (Agent 4: vanilla's table on the held depth;
now the slots, no other mods installed, so crystal is dripstone and volcanic and other are plain
rock under the surface biome):
- Agent 4: dripstone 99% (1: -768,-768), 88% (1: 0,0), 98% (plateau), 63% and 52% (mountains),
  0..5% elsewhere; lush caves 0..19%; deep dark at most 0.8%; the rest the surface biome at any
  depth (dark forest 74% at 0,1536, ocean 59% under the shelf).
- Agent 4.5: every square holds two to four cave biomes: dripstone (with crystal) 9..60%, lush
  caves up to 17% where the climate is moist and mild (none under the high cold mountains), the deep
  dark 0..8% (8.4% under the high mountains at -11520,-10752), plain rock 25..73%.

Verification in Game (Agent 4.5)

Dedicated dev server (`tools/worldcheck/generate.py`, all chunks fully generated, 24 × 24 chunks
each), checked with `cavecheck.py` (every block from Y -256 up), the new `biomecheck.py` and
`blockprobe.py`; the same squares generated with Agent 4's code from a worktree of `main`:
- mega1 — seed 20261018, 716,448 (lowland, an upper pillar megacave 236 × 152 with a lake at Y -21
  and a cenote): cave space 3.81% of the underground (Agent 4: 1.76%), 14.3% at Y -32..-1;
  457 464 water and 58 962 lava sources in caves, 0 that vanilla's fluid tick would change, water
  never touches lava; open/solid changes across chunk borders 0.0092 / 0.0081 against 0.0083 /
  0.0082 inside chunks (no seams); the cenote is a shaft open from the sky down to the megacave's
  lake (`blockprobe.py` at 716,448: air from Y 99 to -11, source water at -21); 2 589 columns of
  cave mouths (Agent 4: 2 625 in the first run of this square). Biomes of the cave space: lush
  caves 41.2% (Y -56..59: the megacave and its province; Agent 4: 10.7%), dripstone 14.6%, the
  rest the surface biome (dark forest, swamp); 182 790 blocks of lush features (moss, clay,
  dripleaf, azalea, vines) and 36 398 of dripstone. 1 unstable water source at the surface
  (864 62 367, sea-level water at a shore flowing into a dip of the terrain; no cave there) and 91
  flowing cave blocks a tick would change (vanilla water springs, e.g. 673 -210 370).
- s1m — seed 1, 0,0 (foothills and mountains): cave space 8.33% (Agent 4: 2.46%; 24.5% at
  Y -160..-129 where a deep megacave and galleries run); 1 973 552 water and 1 792 lava sources in
  caves, 2 unstable water sources (24 -24 186 and -190 5 -160: amethyst geodes cut into cave water;
  Agent 4's code in the same square: 31, in an ancient city), water never touches lava, no seams
  (0.0098 / 0.0082 across borders, 0.0101 / 0.0083 inside). A first run of this square, before two
  fixes, had 57 unstable sources in an ancient city standing in a flooded domain (the deep dark is
  now kept out of flooded rock) and the cave biomes of vanilla's table in every chunk (chunk biome
  filling uses a cached climate sampler: fixed by `NoiseChunkMixin`, now covered by
  `CaveBiomeTest`). Biomes of the cave space now: dripstone 42.0% (Agent 4: 79.1%), the deep dark
  0.2% at Y -72..3 (Agent 4: 12.3% at Y -64..3), lush caves 0.3% (cold mountains: the column's own
  climate excludes them), the rest the surface biome; feature blocks: dripstone 216 846, sculk
  2 394, lush 35 277.
- Both worlds were generated again with the final code (after the fracture frames, the ring
  vault, the abysses' bell vaults and the wall recesses): 11.21 and 11.14 chunks/s, no exception
  in either log.
- The log of every world shows `Vastlands cave biomes: lush [minecraft:lush_caves], dripstone
  [minecraft:dripstone_caves], deep_dark [minecraft:deep_dark], crystal [minecraft:dripstone_caves],
  volcanic [], other []` and no exception. The server never loaded neighbouring chunks for the caves
  (everything is a function of the model).
- Not verified in game: Sincerelyuq's Biomes installed (not available); screenshots of the new caves
  (none taken this session: the dev client under Xvfb was not started); multiplayer; long play.

Performance (Agent 4.5)

- Per chunk (`cavePreview --bench`, one thread, warm caches, Agent 4's code alternated in a
  worktree): near 0,0 (64 chunks from x 1 536, lowland and sea) columns 2.5 ms (Agent 4: 4.3),
  density 8.1 ms (3.2), fluid picker 1.7 ms (1.4); in mountains (from x -2 816) 3.5 (4.5), 4.5
  (5.7), 1.7 (1.2). About 3 ms more per chunk where Agent 4's caves were sparse, less in the
  mountains, for 3.5 times the cave volume on average.
- End to end (dedicated server, `generate.py`, 24 × 24 chunks): seed 1 at 0,0 (mountains) Agent 4's
  code 9.35 chunks/s, the overhaul 11.18, 11.16 and 11.18 chunks/s; seed 20261018 at 716,448:
  Agent 4's code 9.37 and 9.38, the overhaul 11.14, 11.18, 9.38 and 11.18 chunks/s (runs vary by up
  to ~20%). The overhaul is not slower end to end: 1.21.8 generates surface rules
  and features on one consecutive executor, the caves stage runs in the noise workers, and the
  cache fix and slab index save more than the larger caves cost.
- Fixes on the way: `KeyedCache` instead of direct-mapped caches; terrain `sample` instead of the
  hydrology's surface for far planning (an early version computed the drainage of distant regions
  for every megacave and was 20 times slower); pieces pruned by horizontal reach; slab index;
  terrace caches; the biome slot without cave columns (the first version built a cave column per
  biome query, which made `TerrainBiomeTest`'s 90 000 random queries crawl).
- Memory: per worldgen thread the caches above (a few MB), nothing grows with the explored area.

Known Issues at the end of Agent 4.5 (current list: see Agent 5 above)

- Sincerelyuq's Biomes: its jar and source were not available here (Modrinth lists no such
  project; the `sincerelyuq-s-Projects` repository is empty), so the biome ids in
  `data/vastlands/tags/worldgen/biome/cave/crystal.json` and `volcanic.json`
  (`sincerelyuqs_biomes:amethyst_caves`, `...:smoldering_cave` and spelling variants) are guesses
  written as optional entries. With the mod installed, check the line `Vastlands cave biomes: ...`
  in the log: if `crystal` still shows dripstone, replace the ids with the mod's real ones. Not
  tested with the mod. MCreator mods also inject their surface rules only into dimensions of the
  vanilla overworld type, so the mod's own floor and wall blocks may be missing in Vastlands worlds
  (its features come with the biome); not verified.
- Structures (ancient cities, mineshafts, trial chambers) and some vanilla features (amethyst
  geodes, dripstone clusters, springs) still ignore the cave fluids: where one cuts flooded rock,
  cave water flows into it (57 sources in an ancient city in a first run at seed 1, 0,0; after the
  deep dark was kept out of flooded rock, 3 sources in geodes and a dripstone cluster). Vanilla
  features place lava lakes and lava above Y -54 as before.
- Underground rivers are still water: level stretches under an open vault with falls over the
  dams between domains, not flowing water; the falls are where a river gallery crosses a dam.
- No river rises from a cave (resurgence) and none is rerouted underground: that would change
  Agent 3's drainage. Rivers enter the caves through swallets only.
- The network still follows the 64-block lattice (a plan view shows its polygons); galleries and
  megacaves hide it at large scale.
- Megacaves under lowlands are wide and low (60..80 blocks): the upper zone there is only ~100
  blocks thick above the aquitard; tall ones (abysses, canyons) need high ground or the deep zone.
  The vault of a megacave under a deep river valley can be flattened by the 6-block cover.
- Wet upper domains flood every cave below their water table (Agent 4's hydrogeology), so under
  wet mountains whole cave levels are under water.
- Changing any cave constant changes generation (seams against existing chunks); worlds created
  before Agent 4.5 keep `minecraft:multi_noise` in their saved settings and get the new caves
  without the cave biomes. Changing the slot tags changes which biome a province gets.
- Not verified: real GPU rendering and screenshots of the new caves (no screenshots were taken in
  this session); multiplayer; long play (spillway falls after many ticks).
- Unchanged from Agent 4: natural bridges with low vaults over wide valleys, one-block swallet
  notches, river biomes over natural tunnels, uneven sea-cave density along coasts.
- Unchanged from earlier agents: see "Known Issues at the end of Agent 4" below.

Architectural Decisions (Agent 4.5)

- Kept Agent 4's architecture (an analytic model of signed-distance pieces, per-column solids,
  fluid domains, no carvers, no aquifers) and its safety rules unchanged; every new piece goes
  through the same column (cover, dams, clearance), so the new caves inherit the fluid guarantees.
- Multi-scale instead of scaling: a second lattice for galleries and a 1 024-block grid for
  megacaves rather than bigger radii on the 64-block lattice, which would have merged into
  Swiss cheese.
- Megacaves as their own fluid domains (a footprint edge is a domain edge): one lake level per
  cavern instead of Voronoi dams cutting through it.
- A biome source of our own instead of router tricks: the router's climate parameters cannot carry
  other mods' biomes, and structures and `/locate` query the biome source directly, so only a
  biome source keeps chunk biomes and structure checks consistent. Slots are biome tags, so other
  mods and datapacks join without code and without a dependency.
- The cave system is found from the climate sampler through weak identity maps (the `RandomState`'s
  sampler and router, and each chunk's cached sampler), not from a global: several worlds and
  test servers can run side by side.

Instructions for Agent 5 (Forests)

1. The ground still keeps 6 blocks of rock over caves except at entrances; new openings are gallery
   mouths (radius 8..22) and megacave cenotes (radius 7..22, round shafts from the sky down into a
   megacave): skip tree placement over them (`VastlandsCaves.isCave` below the root, or
   `megacaves(...)` for cenote centres).
2. Biomes underground are now cave biomes; above 16 blocks under the ground they are the surface
   biomes as before, so forest placement by surface biome is unchanged.

Instructions for Agent 6 (Natural Wonders), from Agent 4.5 (still valid; see Agent 5's additions above)

1. Read ARCHITECTURE.md, Caves: "Spaces for natural wonders" and the contract. `megacaves(minX,
   minZ, maxX, maxZ)` lists every megacave (kind, centre, size, floor and vault heights, fluid and
   its top, pillars, bridges, shelf, cenote); `isCave` and `caveFluid` give the exact space.
2. Tall vaults: halls and pillar caverns (up to ~150 blocks under high ground), canyons (up to 220)
   and abysses (110..300) for giant stalactites and columns; their ceilings already hang in lobes
   and ribs to start from. Broad floors: halls, lakes and the lower floor of complexes. Deep water:
   lake megacaves (basins 0.25 h deep with islands), submerged ones, the sea-flooded ones under the
   shelf. Sculk: the deep dark megacaves of the deep zone and the deep dark provinces at Y -150..0.
3. Place formations as features scoped to Vastlands worlds (placement filter on
   `VastlandsChunkGenerator`), never as changes to the cave model, and keep fluids level: check
   `tools/worldcheck/cavecheck.py` stays at 0 unstable cave water.


Agent 4 — Caves (completed earlier)

Completed Work (Agent 4 — Caves)

- `worldgen/caves` (new): the underground of Vastlands worlds: a pure-Java cave model on top of the
  geography, terrain and hydrology, a cave system for the router and the fluids of noise filling,
  and a small block step. Algorithm, constants and contracts: ARCHITECTURE.md, "Caves" and "System
  Contracts".
  - `CaveGeology`: karst, fractured, volcanic and coastal rock, cave abundance and wetness as
    continuous fields from the geography (Vastlands has no rock types).
  - `FluidDomains`: the hydrogeology. Warped Voronoi domains above (~320 blocks) and below (~448)
    an aquitard band around Y -66; each holds one fluid up to one level: sea domains flooded to
    sea level near and under the sea, inland water tables below the regional base level or dry
    rock, deep lava lakes (tops Y -236..-150, higher in volcanic regions), rare ancient flooded
    caves, the deep caves under the open ocean flooded. Every lake and flooded passage stands
    level and is held by rock.
  - `CaveNetwork`: multi-level networks on a 64-block lattice: seven upper levels relative to the
    regional base level (plains one to three, mountains up to seven), a coastal level at the
    waterline, three deep levels; curved tunnels with varying radius (phreatic tubes in karst,
    rifts in fractured rock, flat sediment floors, canyon slots), chambers and halls at junctions,
    shafts between levels, entrances: adits into hillsides and cliffs (some very large), sea caves,
    dolines, blue holes.
  - `Fractures`: chasms along joint sets, ravines reaching the surface, a deep set.
  - `Caverns`: enormous caverns (radius 45..110, 20..52 high, pillars, rubble floors, ceilings
    with breakdown lobes, walls in uneven terraces) in about a third of the 640-block cells, mostly
    deep; underground lakes or lava lakes by their domain.
  - `CaveModel`/`CaveColumn`: per column the pieces and the heights that stay rock: cover under the
    ground, dams between domains whose fluids differ (with spillway notches between water levels:
    underground falls), the aquitard, clearance wherever cave fluid would meet a different fluid
    outside (sea, river and lake water, open air); per block the signed distance with wall
    roughness and chockstones.
  - `RiverTunnels` (4.1, rivers): natural tunnels (deep gorges roofed back to the terrain before
    carving, slot gorges and arches at their mouths, skylights), natural bridges (rock fins with an
    arched vault), swallets (a river pouring through a notch in its bank down a shaft into the
    flooded caves, without draining), dry grottoes behind tall falls (the curtain as a wall).
  - Sea connections (4.1, oceans): sea-connected domains flooded to Y 62 under and near the sea,
    the coastal level at the waterline (partly submerged caves with air pockets), sea-cave adits
    opening into cliffs, the shore and the sea floor (often several per network), blue holes in the
    shelf.
  - `VastlandsCaves` (`CaveSystem`): density (opens cave space, restores gorge roofs), the fluid
    picker (sea above the ground, the domain's water, lava or air below), the surface under roofs,
    F3 line, queries for previews and tests.
  - `CaveBlocks`: spillway notches marked for post-processing, volcanic lining (magma, blackstone,
    basalt, smooth basalt) around volcanic lava, the swallets' water.
- Router (`noise_settings/overworld.json`): vanilla's noise caves removed from `final_density`
  (`vastlands:caves` wraps the landscape, slides and interpolation); `aquifers_enabled: false`.
- `NoiseBasedChunkGeneratorMixin` (new): noise filling and base column queries of Vastlands worlds
  use `CaveSystem.fluidPicker` instead of vanilla's global picker (lava below Y -54, water below
  sea level). Fixes the lava below Y -54 noted by Agent 1.
- `VastlandsChunkGenerator`: no vanilla carvers in Vastlands worlds; `CaveBlocks` after noise
  filling and after the second river water pass; an F3 "Vastlands caves" line.
- `CaveSystem` extended (`surfaceHeight(x, z, ground)`, `fluidPicker`); `GenerationSystems`
  creates `VastlandsCaves` and its surface height includes gorge roofs.
- `HydrologyModel.channels(x, z, reach, visitor)` (read-only view of the river segments, the
  integration boundary for the caves; no change to the hydrology's behaviour).
- Tools: `./gradlew cavePreview` (sections, plans, slices, stats, fluid checks, river tunnels and
  roofs, sea caves, probes, bench); `tools/worldcheck/cavecheck.py` (underground composition,
  unstable fluids under vanilla's rules in caves and at the surface, seams, lava above Y -54, cave
  mouths and flooded openings, plans and sections of a generated world);
  `tools/worldcheck/blockprobe.py` (blocks around a position with fluid levels).

Files and Modules Affected (Agent 4)

- New: `worldgen/caves/{CaveModel,CaveColumn,CaveGeology,FluidDomains,CaveNetwork,CavePrimitive,
  Fractures,Caverns,RiverTunnels,Noise3,CaveSettings,CaveFluid,VastlandsCaves,CaveBlocks,
  CaveVolume}`, `mixin/NoiseBasedChunkGeneratorMixin`.
- Changed: `worldgen/system/{CaveSystem,GenerationSystems,Passthrough}`,
  `worldgen/VastlandsChunkGenerator`, `worldgen/hydrology/HydrologyModel` (`channels`, added only).
- Data: `noise_settings/overworld.json` (final density without vanilla's noise caves, aquifers
  off), `vastlands.mixins.json`.
- Tests: `caves/CaveModelTest` (new); `GenerationSetupTest` (caves implemented),
  `StageDensityFunctionTest`, `RandomStateBindingTest` (caves stage bound); `tools/CavePreview`
  (new).
- Build: `build.gradle` (`cavePreview` task).
- Tools and docs: `tools/worldcheck/{cavecheck,blockprobe}.py` (new), `ARCHITECTURE.md` (Caves
  section and updates), `PROGRESS.md`, `README.md`, `docs/caves/` (previews).

Build and Test Results (Agent 4)

- `./gradlew build`: BUILD SUCCESSFUL (JDK 21, Gradle 9.2.1) on a 4 vCPU container (7 min 26 s).
- JUnit: 69 tests, 0 failures, 0 skipped: WorldHeightDataTest 3, GenerationSetupTest 3,
  StageDensityFunctionTest 6, TerrainParameterFunctionTest 3, RandomStateBindingTest 3,
  TerrainModelTest 8, VastlandsTerrainTest 7, PresetDataTest 6, TerrainBiomeTest 2,
  GeographyModelTest 7, FrozenSeaTest 6, HydrologyModelTest 8, RiverWaterTest 1, CaveModelTest 6
  (new). The test JVM now gets 2 GB (`build.gradle`): with Gradle's default of 512 MB the cave
  tests' caches left too little for the hydrology tests after them in the same JVM
  (OutOfMemoryError in `HydrologyModelTest`, then the test server of `RiverWaterTest` did not
  start).
- `CaveModelTest` (seed 20261018): two systems agree on 20 000 blocks queried in a shuffled
  parallel order; at seven places 22.7 million blocks (cave air 535 837, water 470 926, lava
  45 592), 0 fluid blocks that vanilla's rules would move (also at the bridge, the swallet and the
  grotto behind the 32-block fall, which had 2 before the last grotto fix); no cave space under
  river and lake water within the clearance; 23 sea caves with a mouth at the sea in the square
  512..1536, -2048..-1024 (8 blue holes), all 23 open to the sea and stable; 0 cave lava above Y -54
  on a 4 096-block grid; cave share 1.56% of the rock between Y -240 and the ground over 4 000 random
  columns of a 32 768-block square, 2 747 of them without caves; 256 network cells: 1 824 tunnel
  pieces, 78 chambers (10 of radius 30+), 12 adits, 3 dolines; enormous caverns in 128 of 400
  640-block cells.
- Model (`cavePreview --sea`, 4 096 blocks around 0,0): ~15.2 km of coastline, 186 entrances of
  which 89 have their mouth at the sea (65 sea-cave adits, 24 blue holes), 84 open to the sea
  (5.5 per km of coastline), 0 unstable fluid blocks at any of them.
- Model (`cavePreview --tunnels 8`, 16 384 blocks around 0,0, ~268 km²): 15 cells of 256 blocks
  with roofed river columns (natural bridges and tunnels), 11 swallets, 3 grottoes behind falls.

Verification in Game (Agent 4)

Dedicated server (`tools/worldcheck/generate.py`, seed 20261018, all chunks fully generated),
checked with `cavecheck.py` (whole world: every block from Y -256 up) and `blockprobe.py`:
- caves3 — 12×12 chunks at 0,0 (hills), final code: cave space 3.68% of the underground blocks:
  the upper levels (Y 64..95 5.4%; Y 32..63 9.0%, 7.2% of it water standing at the local water
  table) and a deep hall (Y -160..-129 14.4%); 98 440 water and 80 lava sources in caves, 0 that
  vanilla's fluid tick would change, inside chunks or on borders; water never touches lava;
  open/solid changes between neighbouring columns the same across chunk borders as inside chunks
  (no seams); the 30 flowing blocks a tick would change are a vanilla water spring at Y -221..-215
  and the 32 lava blocks above Y -54 vanilla lava features. caves1 and caves2 were the same square
  with earlier versions (also 0 unstable); a screenshot of the hall in caves2 showed its terraces
  as even rings and its ceiling as a smooth dome, which led to the uneven terraces and breakdown
  ceilings of chambers and caverns.
- swallet1 — 6×6 chunks at -319,3106: the river pours through its bank notch (source water at
  the river's water top, a lip of amount 7, falling water down the shaft into the flooded caves),
  checked block by block; 0 unstable water.
- bridge1 — 6×6 chunks at -305,75: a natural bridge over a river; 0 unstable water; vanilla lava
  springs touch cave water in 4 blocks deep down (vanilla features, see Known Issues).
- gorge1 / gorge2 — 8×8 chunks at -6560,615: a natural tunnel and a fall of 32 blocks (130 → 98)
  with a dry grotto behind the curtain. gorge2 after the last grotto fix (rock kept under the
  curtain columns): 24 289 water and 100 lava sources in caves, 0 unstable; 479 sources at the
  surface, 0 unstable; 791 cave-mouth blocks in 122 columns, 42 flooded openings; the 7 flowing
  surface blocks a tick would change are the fall's lip beside the sill that weather froze to ice
  (Agent 3's known issue, not caves); the 40 falling blocks in caves at Y -195..-172 are vanilla
  water springs.
- perf-caves2 — 40×40 chunks at -200,2700 (the performance square, a broad river with
  tributaries): `rivercheck.py`: 351 766 sources above sea level, 0 unstable at rivers and lakes
  (the same square on `main`, with vanilla aquifers and carvers: 14 unstable at rivers);
  `cavecheck.py`: cave space 0.80% of 141 million underground blocks (lowland plains: quiet rock),
  120 472 water and 468 114 lava sources in caves, water never touches lava, no seams; 25 water and
  41 lava sources that would flow are vanilla's (springs not yet ticked outside the simulated
  spawn area, and a mineshaft corridor cut beside a cave lava lake at -400,-187,2763); 2 494 lava
  blocks above Y -54 are vanilla's underground lava lakes (closed blobs of lava in the rock).
- coast1 / coast2 — 8×8 chunks at 400,100 (a low coast): 99 024 water sources in caves (the sea
  domain flooded to Y 62), 0 unstable in caves and at the surface; lava only at Y -195..-132.
  This stretch of coast has no sea-cave mouths (the model agrees: 0 cave water blocks open to the
  sea in the square); sea caves were checked in the model (`cavePreview --sea`, CaveModelTest).
- Generation never loaded neighbouring chunks: every decision is a function of the model, and
  seams were checked in every world. swallet1, bridge1, gorge2 and coast2 were generated before the
  last change of chamber and cavern shapes (only shapes changed there, not fluids or connections).
- Screenshots (`screenshot.py --night-vision`, dev client under Xvfb, Mesa software rendering;
  `docs/caves/`): the grotto behind the 32-block fall from inside and the fall from its pool
  (gorge2), the natural bridge from the river (bridge1), the deep hall (caves3), a lava-filled
  chasm and the swallet's shaft (swallet1).
- Not verified: real GPU rendering (screenshots are software-rendered); multiplayer; seeds other than 20261018 in generated worlds; a world square with sea-cave mouths
  generated in game (only in the model); long play (spillway falls ticking, players breaking
  dams).

Performance (Agent 4)

- Per chunk (`cavePreview --bench`, one thread, 64 chunks around 0,0, warm caches): cave columns
  3.6 ms, density of all 768 × 256 blocks 3.1 ms, fluid picker 1.1 ms; first pass over new ground
  (cold caches) 6.8, 5.9 and 1.7 ms.
- End to end (dedicated server, `generate.py`, 40 × 40 chunks around -200,2700, a broad river with
  tributaries, start-up excluded; runs alternated with `main` in a separate worktree): `main`
  10.75 and 10.73 chunks/s, with caves 10.09 and 10.13 chunks/s: about 6% slower. Vanilla's carvers
  and noise caves are gone, the caves stage runs per block outside interpolation and its columns
  read the hydrology for river connections. (Agent 3 measured 13.8 chunks/s on the same square in
  an earlier container; this one is slower for both branches.)
- Memory: per worldgen thread a 32 × 32 column window, 1 024 network cells, 2 048 nodes, domain
  and river caches (a few MB); nothing global grows with the explored area.
- Fluids: no fluid ticks are scheduled by the caves except the spillway notches (a handful of
  blocks per spillway); cave water and lava are in the states vanilla leaves as they are.

Known Issues at the end of Agent 4 (current list: see Agent 4.5 above)

- Network tunnels follow the 64-block lattice: long cave systems are chains of short curved
  tunnels between nodes, not single long passages, and a plan of the caves (`cavePreview --plan`,
  `docs/caves/preview-plan-2048.png`) shows the lattice as a polygonal mesh. Underground the
  curvature, bumps, varying radius and levels hide most of it.
- Natural bridges over wide valleys have a low, flat vault (the opening is a few blocks above the
  water); the bridge's sides take the surface rules' dirt like any cliff.
- Underground falls between water levels exist only where a dam between two domains has an open
  spillway (30% of the notch windows); they are rare, and some only fill a pocket below the notch.
- Vanilla features and structures still run: water and lava springs (`spring_water`,
  `spring_lava`) and underground lava lakes put small amounts of water and lava into the rock and
  caves, also above Y -54 (56 lava blocks in gorge2, 2 494 in the 40 × 40-chunk square), rarely lava
  next to cave water deep down (4 blocks in bridge1); mineshafts can cut corridors beside cave lava
  or water, which then flows into them. Springs, lakes and structures are shared with vanilla
  worlds (scoping them out of Vastlands worlds would need a placement filter, see ARCHITECTURE.md,
  Vanilla Isolation).
- Ores and other features anchored to the world bottom keep vanilla's shifted ranges (diamonds
  at Y -256..-176 etc.; see ARCHITECTURE.md, Technical Limitations).
- River biomes follow the channel even where the caves roof it into a tunnel (the biome of the
  hill over a natural tunnel is a river biome).
- A swallet's notch is one block wide; the ground over it can be a single block of soil.
- No river runs into an underground lake or out of a cave (resurgence): rivers enter the caves
  only through swallets, and the hydrology's rivers are not rerouted underground (that would
  change Agent 3's drainage).
- Sea caves and blue holes are unevenly spread: ~5.5 per km of coastline around 0,0 (model), none
  on some stretches (the low coast at 400,100); where a coastal domain is dry or holds an inland
  water table, rock stays between its caves and the sea.
- Changing any cave constant (`CaveSettings` or a class constant) changes generation: worlds
  created before get seams at caves.
- Unchanged from Agent 3: structures across rivers, low levees and rims, terrain coasts, large
  river lakes, bubble columns under falls, frozen fall sills in cold biomes, drainage-cell
  valleys, smooth faces in previews, no streams below the river threshold, river biome width,
  basin reach.
- Unchanged from earlier agents: iceberg melt pools, deep frozen oceans with rain, corals on trench
  floors, monument pillars (unverified), badlands terracotta bands, noise-shaped climate zones,
  rarer high ground, experimental-settings warning, cloud height 192, `screenshot.py` lost input,
  license "All Rights Reserved".

Architectural Decisions (Agent 4)

- Caves as a pure analytic model (signed-distance pieces on a deterministic graph) instead of
  vanilla's noise caves and carvers: entrances, levels, chambers and connections can be placed
  where the geology and the rivers say, every chunk computes the same pieces, and nothing reads
  neighbouring chunks.
- Fluids from domains, not from vanilla's aquifers: aquifers decide water per block from noise and
  open water walls and floods where caves meet; with aquifers off, the caves' picker gives every
  open block its domain's fluid, so every water and lava body is level, and the only places two
  different fluids could meet are dam lines, which stay rock. One mechanism also replaces vanilla's
  lava below Y -54.
- The fluid picker through a mixin on the `Supplier.get` of vanilla's private global picker (in
  `createNoiseChunk` and `iterateNoiseColumn`), chosen by the `RandomState`'s Vastlands context:
  vanilla worlds and other dimensions keep vanilla's picker.
- No vanilla carvers in Vastlands worlds (skipped in `VastlandsChunkGenerator`): they ignore water
  levels and cut river beds; biome carver lists stay untouched for vanilla worlds.
- The caves stage stays outside interpolation (per block): cave walls are exact to the block and
  the cost is per column plus a few operations per piece; the landscape below stays interpolated.
- River connections read the hydrology (`channels`, `falls`, `water`) and never change it: the
  caves add rock only above the carved ground and never below any water near it; the hydrology's
  block step still seals beds and banks after the caves, and swallet water is placed after it in
  the states vanilla computes. No river is rerouted underground.
- Code constants (`CaveSettings.DEFAULT`), not a config file, like the geography's settings: a
  world's caves must not depend on a mutable file.

Instructions for Agent 5 (Forests), from Agent 4 (still valid; see Agent 4.5's additions above)

1. Read ARCHITECTURE.md: "Caves" (contract, entrances), "System Contracts" (CaveSystem,
   HydrologySystem), "Generation Pipeline", "Vanilla Isolation".
2. `ForestSystem.treeDensity(x, z)` is the only forest hook so far; forest features reach their
   system through `GenerationContext.of(ServerLevel)`. Scope new features to Vastlands worlds
   (placement filter on `VastlandsChunkGenerator`) or use Vastlands-specific biomes.
3. The ground keeps at least 6 blocks of rock over cave space except at entrances (adits, dolines,
   sea caves), ravines, river tunnel portals, natural bridges and swallets. Large trees with deep
   roots should check the block below (or `VastlandsCaves.isCave`) near those, or skip columns
   where `GenerationSystems.surfaceHeight` differs from the hydrology's (roofs).
4. Over natural tunnels and bridges the surface is restored rock (`CaveSystem.surfaceHeight` is
   above the river); vanilla's surface rules paint it, but the biome there is a river biome (see
   Known Issues): forests that follow biomes will skip those hills unless they read the terrain.
5. Do not place features into river or lake water (`HydrologySystem.water(x, z).placed()`) or into
   the swallet notches beside rivers (`VastlandsCaves.swalletPart`).
6. Verify in game with `tools/worldcheck` (`analyze.py`, `rivercheck.py`, `cavecheck.py`): 0
   unstable water at rivers and in caves must stay 0 when trees are added.

Agent 3 — Hydrology (completed earlier)

Completed Work (Agent 3)

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

Known Issues at the end of Agent 3 (current list: see Agent 4 above)

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

Agent 6: Natural wonders (formations in the megacaves and at the surface), scoped to Vastlands
worlds, keeping cave water level and the forests intact (Instructions for Agent 6, from Agent 5
and Agent 4.5).

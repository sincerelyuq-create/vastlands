Vastlands

A realistic world generation mod for Minecraft 1.21.8, powered by NeoForge.

Planned Features

- Massive mountain ranges and natural valleys
- Realistic rivers, tributaries and waterfalls
- Expanded world height from Y=-256 to Y=511
- Vast cave systems with diverse formations
- Expansive forests and giant trees
- Natural terrain without floating islands

Status: Early development. Done: the "Vastlands" world type with the Y=-256..511 world height; the
terrain (continents and seas, plains and rolling hills, long mountain ranges up to Y ~450 with
foothills, passes and eroded valleys, plateaus with escarpments); and the geography on top of it:
open oceans with shelves, abyssal plains, mid-ocean ridges, seamounts and rare deep trenches, vast
plains provinces and hill country, dry regions with tablelands, climate zones thousands of blocks
across with cold mountain tops and rain shadows, vanilla biomes chosen from landform and climate,
and polar seas covered by fractured ice fields; and rivers: networks of tributaries that drain every
continent downhill to the sea, meandering across floodplains, broad rivers up to ~100 blocks wide in
valleys of their own with ragged banks, lakes in basins and where rivers cross flat ground, rocky
ledges and waterfalls (tall ones off escarpments and in mountains, with a horseshoe lip, a round
plunge pool with boulders and churning water, and a rough-walled gorge below), frozen rivers in
polar climates; and caves: an underground world through the whole depth: multi-level cave
networks of tunnels mostly 6..16 blocks wide shaped by the rock (dense labyrinths in karst, chasms,
shafts, pits and ramps in mountains, quieter regions and nearly solid ones), galleries 25..60 blocks
wide, major corridors 60..110 and rare colossal ones meandering between narrow connections and vast
halls, megacaves hundreds of blocks across (vaulted halls with pillars and partitions, lake caverns
with islands, underground canyons with natural bridges, abysses up to 300 blocks deep, two-floor
complexes, pillar forests), several exits each and cenotes open to the sky; cave biomes in
provinces hundreds of blocks across (lush caves under moist climates, dripstone, a rarer deep dark,
plus the cave biomes of other mods through biome tags, such as the amethyst and smoldering caves of
Sincerelyuq's Biomes when it is installed); entrances in hillsides, cliffs, sinkholes and the sea
floor; underground lakes and flooded passages that always stand level, lakes and seas in megacaves,
underground rivers falling over dams between water levels, deep lava lakes below the aquitard with
basalt and magma in volcanic regions; and designed connections to the surface water: rivers flowing
through natural tunnels and under natural bridges, rivers pouring into the caves through swallets,
dry grottoes behind tall waterfalls, sea caves and blue holes open to the sea; and forests: woods
that follow the biomes, the climate and the ground (oak, birch and mixed woods, dark forests,
taiga and old growth giants, jungles, savannas, swamps, groves on the plains), regional belts of
denser and thinner forest, young and old stands, clearings and meadows, soft edges, gallery
forests along rivers with trees leaning over the water, a treeline in the mountains that thins to
wind-shaped krummholz; trees of many shapes from vanilla blocks (branching oaks and birches,
ancient oaks with flared roots and crowns up to ~28 blocks across, whorled spruces, pines, giant
spruces, pines and jungle trees, flat-topped acacias and dark oaks), snags, fallen logs, shrubs
and a forest floor of leaf litter, ferns, mushrooms and moss where it suits the grass; deadwood
(fallen, broken and rotten logs, stumps, uprooted trees) and huge mushrooms of many shapes up to
giants in old dark forests; nothing grows in rivers, lakes, waterfalls or cave openings; and natural
formations: boulders, rock clusters, tors, talus, hoodoos and spires, balanced rocks, natural arches,
islets and sea stacks, driftwood and shore stones, from the local rock (sandstone, banded
terracotta, granite, limestone, basalt) and the biome, with rare great arches, great spires and
stone forests; in the caves pointed dripstone groups, stalagmites, stalactites and columns up to
giant dripstones and spike fields, root curtains and moss gardens in lush caves, sculk mounds and
pillars in the deep dark (no sensors or shriekers), calcite and amethyst, basalt columns over lava,
rubble, pendants and rock pillars, submerged rocks and islets in cave lakes; ordinary caves partly
bare; formations never block a river, a fall, a cave mouth or a structure. See PROGRESS.md.

Using it

- Singleplayer: Create New World → World → World Type: Vastlands.
- Dedicated server: `level-type=vastlands\:vastlands` in `server.properties` before the world is created.
- The default world types are not changed by the mod.
- F3 shows "Vastlands terrain", "Vastlands geography", "Vastlands hydrology", "Vastlands caves",
  "Vastlands cave biome", "Vastlands forest" and "Vastlands nature" lines for the column you stand
  on (surface, landform, climate, ice, water, the nearest river, the rock and cave water domains,
  the cave biome slot, the forest community with its density, age and clearing, and the natural
  formations nearby).
- Other mods' cave biomes: add them to the biome tags `vastlands:cave/lush`, `dripstone`,
  `deep_dark`, `crystal`, `volcanic` or `other` (a datapack, optional entries); see ARCHITECTURE.md,
  Caves.
- Other mods' surface biomes: add them to a forest community tag (`vastlands:forest/temperate`,
  `birch`, `dark`, `boreal`, `jungle` ... see ARCHITECTURE.md, Forests) for Vastlands trees there;
  biomes in no community keep their own trees. For natural formations, add them to a style tag
  (`vastlands:nature/surface/mountain`, `desert`, `badlands`, `temperate` ... or `none` for none) or
  a cave profile tag (`vastlands:nature/cave/dripstone`, `lush`, `deep_dark`, `crystal`,
  `volcanic` ...); untagged biomes are judged by their convention tags and climate.

Building

Requires Java 21. `./gradlew build` produces `build/libs/vastlands-<version>.jar` and runs the tests.
`./gradlew runClient` and `./gradlew runServer` start development instances in `run/`.
`./gradlew terrainPreview --args="--seed 1 --size 16384 --scale 16 --out build/preview"` renders
the terrain of a seed as a relief map and a perspective view without starting the game; add
`--maps relief,landform,biome,temperature,moisture,ice` for geography, biome, climate and ice maps.
`./gradlew hydrologyPreview --args="--seed 1 --size 16384 --scale 8 --out build/rivers"` renders
rivers, lakes and waterfalls over the carved relief and lists the tallest falls and widest rivers.
`./gradlew cavePreview --args="--seed 1 --center 0 0 --size 1024 --out build/caves/s"` draws
sections through the caves of a square (`--plan 2`, `--slices -150,40`, `--stats`, `--sea` and more:
see ARCHITECTURE.md, Testing).
`./gradlew caveSurvey --args="--seed 1 --center 0 0 --size 768"` measures the caves of a square
(passage widths, cave share by depth, wide spaces and megacaves, connectivity, water, cave biomes).
`./gradlew forestPreview --args="--gallery build/forests/g.png --map 288 32 512 1 build/forests/m.png"`
draws every tree form and an aerial map of a forest; `--view`, `--stats` and more: ARCHITECTURE.md,
Testing.
`./gradlew naturePreview --args="--gallery build/nature/g.png --view -12704 -11808 256 build/nature/v.png"`
draws every natural formation and a view of a square with its formations; `--cave`, `--stats` and
more: ARCHITECTURE.md, Testing.
Architecture and contribution notes: ARCHITECTURE.md.

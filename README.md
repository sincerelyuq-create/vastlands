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
polar climates; and caves: multi-level cave networks shaped by the rock (dense winding karst,
chasms and shafts in mountains, quiet plains, sea caves on coasts), chambers, halls and rare
enormous caverns with pillars, entrances in hillsides, cliffs, sinkholes and the sea floor,
underground lakes and flooded passages that always stand level, underground falls between water
levels, deep lava lakes below the aquitard with basalt and magma in volcanic regions (no more lava
in every cave below Y -54), and designed connections to the surface water: rivers flowing through
natural tunnels and under natural bridges, rivers pouring into the caves through swallets, dry
grottoes behind tall waterfalls, sea caves and blue holes open to the sea. Forests are not
implemented yet. See PROGRESS.md.

Using it

- Singleplayer: Create New World → World → World Type: Vastlands.
- Dedicated server: `level-type=vastlands\:vastlands` in `server.properties` before the world is created.
- The default world types are not changed by the mod.
- F3 shows "Vastlands terrain", "Vastlands geography", "Vastlands hydrology" and "Vastlands caves"
  lines for the column you stand on (surface, landform, climate, ice, water, the nearest river, and
  the rock and cave water domains).

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
Architecture and contribution notes: ARCHITECTURE.md.

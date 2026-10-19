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
and polar seas covered by fractured ice fields. Rivers, caves and forests are not implemented yet.
See PROGRESS.md.

Using it

- Singleplayer: Create New World → World → World Type: Vastlands.
- Dedicated server: `level-type=vastlands\:vastlands` in `server.properties` before the world is created.
- The default world types are not changed by the mod.
- F3 shows "Vastlands terrain" and "Vastlands geography" lines for the column you stand on
  (surface, landform, climate, ice).

Building

Requires Java 21. `./gradlew build` produces `build/libs/vastlands-<version>.jar` and runs the tests.
`./gradlew runClient` and `./gradlew runServer` start development instances in `run/`.
`./gradlew terrainPreview --args="--seed 1 --size 16384 --scale 16 --out build/preview"` renders
the terrain of a seed as a relief map and a perspective view without starting the game; add
`--maps relief,landform,biome,temperature,moisture,ice` for geography, biome, climate and ice maps.
Architecture and contribution notes: ARCHITECTURE.md.

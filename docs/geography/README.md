Geography images (Agent 2.5)

Seed 20261018. "Preview" images are rendered from the terrain and geography model with
`./gradlew terrainPreview` (no game; the biome maps apply vanilla's own overworld biome table to the
terrain's climate parameters). "In-game" images are F2 screenshots of the dev client (Mesa software
rendering, HUD hidden) taken with `tools/worldcheck/screenshot.py` in worlds generated on the dev
server with `tools/worldcheck/generate.py`; "surface" maps are read from those worlds' region files
with `tools/worldcheck/oceancheck.py --png`.

| File | What |
|---|---|
| `preview-world-landform-131k.jpg` | 131 072 × 131 072 blocks around 0,0, 128 blocks per pixel: landform classes (blue shades: shelf, slope, abyssal plain, teal ridges; greens: plains, hills; tan: foothills; brown/white: mountains; orange: plateaus) |
| `preview-world-biome-131k.jpg` | the same square: surface biomes (climate zones thousands of blocks across, deserts in warm dry regions, polar seas and coasts) |
| `preview-region-relief-24k.jpg` | 24 576 blocks around 0,0, 24 blocks per pixel: relief and sea depth |
| `preview-region-biome-24k.jpg` | the same square: biomes |
| `preview-plains-to-mountains-view.jpg` | perspective from a plains province towards a mountain system with branch ranges (around 4500,16300) |
| `preview-dry-region-biome-8k.jpg` | 8 192 blocks around -12000,-11000: desert and badlands around a dry range with stony peaks and a snowy summit |
| `preview-dry-region-view.jpg` | the dry range in perspective, seen from the desert |
| `preview-coast-to-basin-4k.jpg` | 4 096 blocks around 5500,3000, 4 blocks per pixel: island shore, continental slope, abyssal plain, the edge of a mid-ocean ridge (landform colours) |
| `preview-trench-relief-8k.jpg` | 8 192 blocks around -82240,-191808: an abyssal trench (deepest floor Y ~-160) in relief |
| `preview-ice-field-1k.jpg` | 1 024 blocks, 1 block per pixel, at the coldest sea of the 65 536-block square: floes, thick floes (darker), cracks and leads (dark), pressure ridges (white) |
| `preview-ice-sea-12k.jpg` | 12 288 blocks at 6 blocks per pixel around 30208,-19712: a frozen sea breaking up towards its warm edge |
| `surface-ice-world.png` | world `ice5` (16 × 16 chunks at 30208,-19712): the generated sea surface, 1 block per pixel (light: ice, mid blue: thick packed-ice floes, dark: water; vanilla's icebergs and their skirts as patches and speckles) |
| `ingame-ice-field.jpg` | world `ice5`: the ice field from above, cracks, a lead and vanilla icebergs frozen in |
| `ingame-ice-floes.jpg` | world `ice5`: floes and cracks closer up |
| `ingame-coast-to-basin.jpg` | world `coast1`: a warm desert shore, the light shelf and the dark deep water beyond |
| `ingame-dry-flank.jpg` | world `dry2`: badlands on the flank of the dry range (terracotta bands) |
| `ingame-dry-range-summit.jpg` | world `dry1`: the summit of the same range above Y 250 (frozen peaks: high ground is cold even in a hot region) |
| `ingame-foothills.jpg` | world `pm1`: old growth birch forest on the hills at the foot of a mountain system |

Hydrology images (Agent 3)

Seed 20261018. "Preview" images are rendered from the terrain and hydrology models with
`./gradlew hydrologyPreview` (no game): hillshaded relief of the carved surface, rivers and lakes
in blue (lakes lighter), frozen rivers and lakes pale (snowy or frozen biomes by vanilla's biome
table), the sea in dark blues, falls of 8 blocks or more white, boulders grey. Previews show the 2D
model: the rough walls of gorges exist only in the game's density. "In-game" images are F2
screenshots of the dev client (Mesa software rendering, HUD hidden) taken with
`tools/worldcheck/screenshot.py` in worlds generated on the dev server with
`tools/worldcheck/generate.py`, after the second pass on natural shapes (meanders, ragged banks and
lips, round plunge pools, boulders, rough walls).

| File | What |
|---|---|
| `preview-world-65k.jpg` | 65 536 blocks around 0,0, 32 blocks per pixel: river networks draining every continent to the sea, lakes in deep basins |
| `preview-region-32k.jpg` | 32 768 blocks around 0,0, 16 blocks per pixel: meandering lowland rivers, tributary networks in the hills |
| `preview-broad-river-3k.jpg` | 3 000 blocks around 0,2400, 3 blocks per pixel: a broad river (~80 blocks) in its valley with ragged walls, meandering tributaries joining it, its mouth in a bay |
| `preview-mountain-valley-1536.jpg` | 1 536 blocks around 24300,-23700, 1 block per pixel: a river leaving the hills in an incised, winding valley, round plunge pools with boulders below its falls |
| `preview-plateau-fall-768.jpg` | 768 blocks around -5925,14778, 1 block per pixel: a river falling 50 blocks off a plateau escarpment into a round plunge pool, a meandering river below |
| `preview-plateau-fall-closeup.jpg` | 192 blocks around -5930,14790, 4 pixels per block: the same fall: the bowed, ragged lip (white), the pool with boulders (grey), the amphitheatre below |
| `preview-frozen-river-3k.jpg` | 3 072 blocks around 32900,-21400, 3 blocks per pixel: a meandering frozen river in snowy plains entering a polar bay |
| `ingame-plateau-fall.jpg` | world `fall6` (16 × 16 chunks at -5925,14778): the 50-block fall from downstream, the gorge and the river below it |
| `ingame-plateau-fall-pool.jpg` | world `fall6`: the plunge pool with mossy boulders, the curtain split by a rock on the lip, ledges on the walls |
| `ingame-plateau-fall-gorge.jpg` | world `fall6`: the fall and its amphitheatre from above the pool; grass on the gorge floor (no cave biomes) |
| `ingame-plateau-fall-lip.jpg` | world `fall6`: the river above the fall running to its lip, a boulder in the stream |
| `ingame-broad-river-weir.jpg` | world `plains3` (24 × 24 chunks at -200,2700): an 80-block river in jungle with a 2-block ledge across it: a wavy line with rocks |
| `ingame-broad-river.jpg` | world `plains3`: the broad river from above, ragged sandy banks |
| `ingame-frozen-river.jpg` | world `polar2` (16 × 16 chunks at 33300,-21450): the frozen river in its terraced valley in snowy plains |

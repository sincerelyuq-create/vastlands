Hydrology images (Agent 3)

Seed 20261018. "Preview" images are rendered from the terrain and hydrology models with
`./gradlew hydrologyPreview` (no game): hillshaded relief of the carved surface, rivers and lakes
in blue (lakes lighter), frozen rivers and lakes pale (snowy or frozen biomes by vanilla's biome
table), the sea in dark blues, falls of 8 blocks or more white. "In-game" images are F2 screenshots
of the dev client (Mesa software rendering, HUD hidden) taken with `tools/worldcheck/screenshot.py`
in worlds generated on the dev server with `tools/worldcheck/generate.py`.

| File | What |
|---|---|
| `preview-world-65k.jpg` | 65 536 blocks around 0,0, 32 blocks per pixel: river networks draining every continent to the sea, lakes in deep basins |
| `preview-region-32k.jpg` | 32 768 blocks around 0,0, 16 blocks per pixel |
| `preview-broad-river-3k.jpg` | 3 000 blocks around 0,2400, 3 blocks per pixel: a broad river (~80 blocks) in its valley, tributaries joining it (small fall marks where they drop into it), its mouth in a bay |
| `preview-mountain-valley-1536.jpg` | 1 536 blocks around 24300,-23700, 1 block per pixel: a river leaving the hills in an incised valley, falls (white) on its steep upper course |
| `preview-plateau-fall-768.jpg` | 768 blocks around -5925,14778, 1 block per pixel: a river falling 50 blocks off a plateau escarpment, the gorge below the fall |
| `preview-frozen-river-3k.jpg` | 3 072 blocks around 32900,-21400, 3 blocks per pixel: a meandering frozen river in snowy plains entering a polar bay |
| `ingame-plateau-fall.jpg` | world `fall2` (16 × 16 chunks at -5925,14778): the 50-block fall from downstream, the gorge and the river below it |
| `ingame-plateau-fall-gorge.jpg` | world `fall2`: the escarpment, the gorge and the river below the fall from the side |
| `ingame-plateau-fall-lip.jpg` | world `fall2`: the river above the fall running to its lip |
| `ingame-broad-river-weir.jpg` | world `plains1` (24 × 24 chunks at -200,2700): an 80-block river in bamboo jungle with a 2-block weir across it |
| `ingame-broad-river.jpg` | world `plains1`: the broad river from above, sandy banks |
| `ingame-frozen-river.jpg` | world `polar1` (16 × 16 chunks at 33300,-21450): the frozen river in its valley in snowy plains |

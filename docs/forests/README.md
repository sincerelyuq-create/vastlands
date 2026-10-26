Forest images (Agent 5)

Seed 20261018. "Preview" images are rendered from the forest model with `./gradlew forestPreview`
(no game; biomes from vanilla's table on the router's climate parameters). "In-game" images are F2
screenshots of the dev client (Mesa software rendering, HUD hidden) taken with
`tools/worldcheck/screenshot.py` in worlds of 24 × 24 chunks generated on the dev server with
`tools/worldcheck/generate.py` (final code). Software rendering draws fancy leaves without
shading variation and fog at 12 chunks; real GPUs look better.

| File | What |
|---|---|
| `preview-gallery-0.png` .. `preview-gallery-3.png` | every tree form, several of them at two or three sizes (`--gallery`): broadleaf oaks, ancient oaks with flared roots, birches, spruces, pines, giant spruces and pines, krummholz, dark oaks, tall and giant jungle trees, acacias, swamp oaks, shrubs, snags, fallen logs, huge mushrooms |
| `preview-view-dark-taiga.jpg` | isometric view of 160 × 160 blocks at 230,0 (`--view`): old growth spruce taiga with giant spruces on podzol, a dark forest edge with oaks, the beach and the sea |
| `preview-view-river.jpg` | 128 × 128 blocks at -2420,760: taiga on both banks of a river, birches and oaks along the water |
| `preview-view-savanna.jpg` | 128 × 128 blocks at 11096,3943: acacias, oaks and shrubs of a savanna |
| `preview-map-dark-taiga.jpg` | aerial map of 768 × 768 blocks around 288,32, one block per pixel (`--map`): crowns by wood over hillshaded ground (spruce grey-green, dark oak deep green, oak green, birch light green, water blue): old growth spruce taiga on podzol (brown gaps) to the west, dark forest to the south-east, a river ending in a lake behind the dunes, the beach and the sea |
| `preview-map-lake.jpg` | the same around -1700,-800: oak and birch woods around a lake with clearings and a shore meadow, mixed with spruce to the south, thin spruce stands on the snowy taiga to the north-west (snow-covered ground white) |
| `ingame-taiga-meander.jpg` | world `r1` (-2368,768): a river meandering through taiga, from 50 blocks above it: a clearing inside the bend, birches and oaks along the banks |
| `ingame-taiga-river.jpg` | world `r1`: the same river from 14 blocks above the water: oaks leaning over it, spruces and birches on the banks, no trunk in the water |
| `ingame-lake-shore.jpg` | world `t1` (-1700,-800): the wooded shore of a lake: oaks, birches and spruces mixed, a narrow beach |
| `ingame-dark-forest-dunes.jpg` | world `d1` (288,32): the edge of a dark forest on the coast, oaks and dark oaks on grass between beach dunes, spruces of the taiga behind |
| `ingame-old-growth-taiga.jpg` | world `d1`: old growth spruce taiga with giant spruces ending at the beach |
| `ingame-cenote-clearing.jpg` | world `c1` (716,448): a cenote of a megacave in a dark forest: the trees keep clear of its rim, huge mushrooms in the canopy |
| `ingame-jungle-river.jpg` | world `j1` (783,1892): tall and giant jungle trees on a riverbank, bamboo jungle (vanilla) on the left |
| `ingame-savanna.jpg` | world `s1` (11096,3943): acacias and shrubs of a savanna |
| `ingame-snow-line-grove.jpg` | world `g1` (-5403,1586): snowy taiga with snow on the spruce crowns giving way to a grassy slope with scattered spruces and oaks, a snowy mountainside above |
| `ingame-birch-oak-canopy.jpg` | world `b1` (-4142,3009): the canopy of a birch and oak forest from 37 blocks above the ground, birch trunks in the gaps |
| `ingame-snowy-mountain-spruce.jpg` | world `m1` (-4320,-992): snow-laden spruce forest on high ground around Y 230 |

What the screenshots show that the numbers do not: forest edges along beaches still read as a
line of trees from a distance (shrubs and smaller trees soften them only at close range); the
savanna's acacias are similar to each other; dark forest canopies look flat from above, as
vanilla's do.

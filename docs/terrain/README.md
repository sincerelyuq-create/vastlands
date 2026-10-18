Terrain images (Agent 2)

Seed 20261018 unless noted. "Preview" images are rendered from the terrain model with
`./gradlew terrainPreview` (no game); "region heightmap" from the saved chunks of a world generated
on the dev server; "in-game" are F2 screenshots of the dev client (Mesa software rendering, render
distance 16, clouds off, HUD hidden) taken with `tools/worldcheck/screenshot.py`.

| File | What |
|---|---|
| `preview-overview-16k.jpg` | 16 384 × 16 384 blocks around 0,0, 16 blocks per pixel: continents, inland seas, mountain belts with snowy cores, plateaus, plains |
| `preview-coastal-massif-3k.jpg` | 3 072 blocks around 6700,-1300, 3 blocks per pixel: a coastal massif with dendritic valleys, foothills and plains |
| `preview-coastal-massif-view.jpg` | the same massif in perspective, seen from the sea in the south |
| `region-heightmap-c1r.jpg` | 512 × 512 blocks (32 × 32 chunks) of world `c1r` at 6480..6991, -1520..-1009: the generated ground, 1 block per pixel |
| `ingame-c1r-massif-from-southwest.jpg` | world `c1r`: the massif from the south-west foothills (camera Y 160) |
| `ingame-c1r-summit-view.jpg` | world `c1r`: near the summit (Y ~379), looking down a snowy ridge |
| `ingame-c1r-mid-slope.jpg` | world `c1r`: the snow line on the upper slopes (Y ~300) |
| `ingame-c1r-foothills.jpg` | world `c1r`: grassy foothill slope with exposed stone, close up |
| `ingame-m1-overview-early-model.jpg` | world `m1` (around 300,5000, a cold region; an earlier version of the model without crags and ground roughness): the massif from the north-east at Y 395 |
| `ingame-m1-valley-early-model.jpg` | world `m1`: from a valley floor (Y ~128) up to the massif |

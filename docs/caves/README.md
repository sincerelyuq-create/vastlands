Cave images (Agent 4, Agent 4.5)

Seed 20261018. "Preview" images are rendered from the cave model with `./gradlew cavePreview` (no
game): sections one pixel per block, Y -256 at the bottom; rock shaded by depth, cave air black,
cave water blue, lava orange, sky light blue, sea and river water outside in blue. "In-game" images
are F2 screenshots of the dev client (Mesa software rendering, HUD hidden, night vision so the caves
are visible) taken with `tools/worldcheck/screenshot.py --night-vision` in worlds generated on the
dev server with `tools/worldcheck/generate.py`.

| File | What |
|---|---|
| `preview-section-x.png` | section along x through 0,0, 1 024 blocks: hills with stacked upper levels, flooded passages at the water table, the aquitard band around Y -66 without caves, deep levels, a deep hall |
| `preview-section-z.png` | the same along z |
| `preview-plan-2048.png` | 2 048 blocks around 0,0, 2 blocks per pixel: every column with cave space, coloured by the height of its highest cave block (deep red through yellow and green to white near the surface; blue water, orange lava), land grey, sea dark blue. From above the network's 64-block lattice shows through: tunnels run node to node in a polygonal mesh (see PROGRESS.md, Known Issues) |
| `ingame-grotto-behind-fall.jpg` | world `gorge2` (8 × 8 chunks at -6560,615): inside the dry grotto behind a 32-block fall: the curtain on both sides of the mouth, the frozen plunge pool with boulders beyond |
| `ingame-fall-with-grotto.jpg` | world `gorge2`: the fall from its pool; the grotto's mouth behind the curtain at its foot |
| `ingame-natural-bridge.jpg` | world `bridge1` (6 × 6 chunks at -305,75): a river flowing under a natural bridge, a rock fin across its valley |
| `ingame-deep-hall.jpg` | world `caves3` (12 × 12 chunks at 0,0): a hall of the deep network around Y -140: rubble floor, walls in uneven terraces, ceiling with breakdown lobes, a vanilla water spring |
| `ingame-lava-chasm.jpg` | world `swallet1` (6 × 6 chunks at -319,3106): a chasm of the deep fracture set, its foot flooded with lava of a deep lava domain at Y -181 |
| `ingame-swallet-shaft.jpg` | world `swallet1`: the swallet's shaft beside a jungle river, seen from above through bamboo: the river pours through a notch in its bank down into the flooded caves |

Agent 4.5 (underground overhaul), rendered from the cave model with `./gradlew cavePreview` as above:

| File | What |
|---|---|
| `preview45-plan-2048.png` | the same 2 048 blocks around 0,0 as `preview-plan-2048.png`, after the overhaul: wider and denser networks, galleries crossing the lattice, megacaves as broad patches, caves under the sea at the right |
| `preview45-abyss-x.png` | seed 1, section along x through an abyss megacave at 4489,-9964 under a mountain (194 × 140 blocks, sheer walls closing into a bell-shaped vault 176 blocks above its floor, a lake at its foot), a river gallery at its water table on the left, deep halls below the aquitard |
| `preview45-complex-z.png` | seed 20261018, section along z through a two-floor complex at -2331,-2367 (412 × 301 blocks, floor Y -12, vault Y 92): a lake on its floor, the shelf, pillars, and the cenote, a collapse shaft from the sky into the vault |
| `preview45-complex-y10.png` | the same complex sliced at Y 10: the shelf with its holes, pillars, partitions, connectors leading out |
| `preview45-pillars-y100.png` | a pillar cavern at -12680,-10780 under high mountains sliced at Y 100: columns, stubs, the network and chasms around it |
| `preview45-lava-z.png` | a deep complex holding a lava lake at -236,-1832 under flooded coastal caves |

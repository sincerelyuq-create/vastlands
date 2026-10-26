Natural formation images (Agent 6)

Seed 20261018. "Preview" images are rendered from the models with `./gradlew naturePreview` and
`./gradlew forestPreview` (no game; biomes from vanilla's table and the mod's tag files). "In-game"
images are F2 screenshots of the dev client (Mesa software rendering, HUD hidden, render distance
8) taken with `tools/worldcheck/screenshot.py` in worlds of 24 × 24 chunks generated on the dev
server with `tools/worldcheck/generate.py`. Software rendering has no smooth lighting variation
and strong fog; real GPUs look better.

| File | What |
|---|---|
| `preview-gallery-0.jpg` .. `preview-gallery-4.jpg` | every formation kind on flat ground or in a box cave (`--gallery`): boulders, rock clusters, talus, outcrops, spires, arches, driftwood, shore stones, islets, balanced rocks, great arch, great spire, stone forest; rubble, cave boulders, stalagmites, stalactites, columns, pointed groups, pendants, rock pillars, collapses, root curtains, moss gardens, mossy pillars, sculk mounds, pillars and growths, crystals, basalt columns, submerged rocks, cave islets, giant dripstone, spike field, giant column |
| `preview-mushrooms-0.jpg`, `-1.jpg` | procedural huge and giant mushrooms (`forestPreview --gallery-kind mushrooms`): brown caps flat, umbrella, wide, tiered and lopsided; red caps dome, tall, flattened, bell and lobed; giants with smaller ones at their foot |
| `preview-deadwood.jpg` | deadwood (`--gallery-kind deadwood`): plain, broken, rotten and giant fallen logs, stumps, uprooted trees with root plates, debris |
| `preview-view-badlands.jpg` | isometric view of 256 × 256 blocks at -12704,-11808: badlands and savanna plateau with spires, outcrops, balanced rocks |
| `preview-view-mountain.jpg` | 256 × 256 blocks at -11520,-10752: frozen peaks with boulders, rock clusters, talus fans and tors in grey stone, limestone and granite patches |
| `preview-view-dark-forest.jpg` | 160 × 160 blocks at 716,448: dark forest around a clearing (the cenote's rim: the preview does not draw caves) with huge brown and red mushrooms and a few small stones |
| `preview-cave-spike-field.jpg` | the caves of a box of 96 × 96 × 40 blocks at -12628,-86,-11739 with their ceilings cut away (`--cave`): dripstone formations on the floor of a dripstone cave |
| `ingame-dark-forest-floor.jpg` | world `c1f` (716,448): the floor under dark oaks: podzol and leaf litter, grass, ferns and mushrooms; no bright moss patches |
| `ingame-dark-forest-canopy.jpg` | the same square from 40 blocks above the canopy: brown and red huge mushroom caps among the dark oaks |
| `ingame-giant-mushroom.jpg` | the same square under the canopy: the stem and cap of a giant red mushroom with a brown one behind dark oak trunks |
| `ingame-great-arch-first.jpg` | world `n1f` (-12704,-11808): the first great arch in banded terracotta, from 40 blocks: it read as a dome with a hole (rib a quarter of the span thick) |
| `ingame-great-arch.jpg`, `ingame-great-arch-side.jpg` | world `n1h`, the same arch with the final shape (a slender rib on flared legs), from the front and at 50° |
| `ingame-badlands.jpg` | world `n1f`: the badlands around the arch from 45 blocks above (the arch, in its first shape, on the horizon) |
| `ingame-dripstone-spike-field.jpg` | world `n1f`, Y -82, night vision: a spike field in a dripstone cave under the badlands: stalagmites and dripstone pillars on the floor, rows of stalactites with pointed tips, vanilla's pointed dripstone among them, water pools |

What the screenshots show that the numbers do not: the arch's banded terracotta follows vanilla's
badlands bands; the dark forest floor is now dark (podzol, litter) instead of bright moss; cave
formations blend with vanilla's dripstone. Weak points seen: the great arch is still a heavy
landmark seen end on (its depth is about its rib's height), the badlands around it are flat with
few hoodoos in this square, and the giant mushroom shot is hidden behind trunks (the canopy
shot shows the caps better).

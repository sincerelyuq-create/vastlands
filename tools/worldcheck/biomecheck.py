"""Counts the biomes of a generated world underground, and the blocks their features place.

Reads every saved chunk of a world made by generate.py and reports:
- the biomes of the underground (4x4x4 biome cells below each column's ground), per 64-block band;
- the biomes that hold the cave space (cells weighted by their cave air and water blocks): what a
  player sees in the caves;
- for each cave biome (lush caves, dripstone caves, deep dark and any other biome found only
  underground) its height range and the number of blocks typical of its features (moss, clay,
  dripleaf, azalea; pointed dripstone, dripstone blocks; sculk, sensors, shriekers, catalysts),
  counted over the whole world, so a biome whose features never generate shows up.

Needs numpy.

    python3 tools/worldcheck/biomecheck.py run/mega1
"""
import argparse
import json
import math
import os
import sys
from collections import Counter, defaultdict

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anvil import read_chunk  # noqa: E402

MIN_Y = -256
FEATURE_BLOCKS = {
    "lush": ("minecraft:moss_block", "minecraft:moss_carpet", "minecraft:clay", "minecraft:big_dripleaf", "minecraft:small_dripleaf",
             "minecraft:azalea", "minecraft:flowering_azalea", "minecraft:cave_vines", "minecraft:cave_vines_plant",
             "minecraft:spore_blossom", "minecraft:rooted_dirt"),
    "dripstone": ("minecraft:pointed_dripstone", "minecraft:dripstone_block"),
    "sculk": ("minecraft:sculk", "minecraft:sculk_vein", "minecraft:sculk_sensor", "minecraft:sculk_shrieker",
              "minecraft:sculk_catalyst"),
    "amethyst": ("minecraft:amethyst_block", "minecraft:budding_amethyst", "minecraft:amethyst_cluster"),
}
AIRS = {"minecraft:air", "minecraft:cave_air"}


def unpack(longs, bits, count):
    """Palette indices packed vanilla's way (no entry spans two longs)."""
    per_long = 64 // bits
    values = np.array([v & 0xFFFFFFFFFFFFFFFF for v in longs], dtype=np.uint64)
    shifts = np.arange(per_long, dtype=np.uint64) * np.uint64(bits)
    mask = np.uint64((1 << bits) - 1)
    out = ((values[:, None] >> shifts[None, :]) & mask).reshape(-1)
    return out[:count].astype(np.int64)


def section_states(section):
    states = section["block_states"]
    palette = [entry["Name"] for entry in states["palette"]]
    if len(palette) == 1 or "data" not in states:
        return palette, np.zeros(4096, dtype=np.int64)
    bits = max(4, math.ceil(math.log2(len(palette))))
    return palette, unpack(states["data"], bits, 4096)


def section_biome_cells(section):
    biomes = section["biomes"]
    palette = biomes["palette"]
    if len(palette) == 1 or "data" not in biomes:
        return palette, np.zeros(64, dtype=np.int64)
    bits = math.ceil(math.log2(len(palette)))
    return palette, unpack(biomes["data"], bits, 64)


def heightmap(nbt, name, min_y, max_y):
    bits = math.ceil(math.log2(max_y - min_y + 2))
    per_long, mask, out = 64 // bits, (1 << bits) - 1, []
    for value in nbt["Heightmaps"][name]:
        value &= 0xFFFFFFFFFFFFFFFF
        for _ in range(per_long):
            if len(out) < 256:
                out.append((value & mask) + min_y - 1)
            value >>= bits
    return np.array(out).reshape(16, 16)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("world")
    args = parser.parse_args()
    with open(os.path.join(args.world, "worldcheck.json")) as f:
        meta = json.load(f)
    r = meta["radius"]
    lo_x, lo_z, hi_x, hi_z = meta.get("chunk_range", [-r, -r, r - 1, r - 1])
    region = os.path.join(args.world, "region")
    under = defaultdict(Counter)
    caves = Counter()
    heights = defaultdict(lambda: [10 ** 6, -10 ** 6])
    features = Counter()
    chunks = 0
    for cx in range(lo_x, hi_x + 1):
        for cz in range(lo_z, hi_z + 1):
            nbt = read_chunk(region, cx, cz)
            if nbt is None or nbt.get("Status") != "minecraft:full":
                continue
            chunks += 1
            ground = heightmap(nbt, "OCEAN_FLOOR", MIN_Y, 511)
            # Biome cells: the lowest ground of the 4x4 columns of each cell.
            cell_ground = ground.reshape(4, 4, 4, 4).min(axis=(1, 3))
            for section in nbt["sections"]:
                if "block_states" not in section or "biomes" not in section:
                    continue
                sy = section["Y"]
                palette, states = section_states(section)
                names = np.array(palette, dtype=object)
                for kind, blocks in FEATURE_BLOCKS.items():
                    for k, name in enumerate(palette):
                        if name in blocks:
                            features[kind] += int(np.count_nonzero(states == k))
                open_ids = [k for k, name in enumerate(palette) if name in AIRS or name == "minecraft:water"]
                open_blocks = np.isin(states, open_ids).reshape(16, 16, 16) if open_ids else np.zeros((16, 16, 16), dtype=bool)
                bpal, bcells = section_biome_cells(section)
                bcells = bcells.reshape(4, 4, 4)
                for qy in range(4):
                    y = sy * 16 + qy * 4
                    for qz in range(4):
                        for qx in range(4):
                            if y + 3 >= cell_ground[qz, qx] - 4:
                                continue
                            biome = bpal[bcells[qy, qz, qx]]
                            band = (y - MIN_Y) // 64
                            under[band][biome] += 1
                            h = heights[biome]
                            h[0] = min(h[0], y)
                            h[1] = max(h[1], y + 3)
                            cave = int(open_blocks[qy * 4:qy * 4 + 4, qz * 4:qz * 4 + 4, qx * 4:qx * 4 + 4].sum())
                            if cave:
                                caves[biome] += cave
    print("%s: %d chunks" % (args.world, chunks))
    total = sum(caves.values())
    print("biomes of the cave space (%d cave blocks):" % total)
    for biome, n in caves.most_common(16):
        print("  %-36s %6.2f%%  Y %d..%d" % (biome, 100.0 * n / max(1, total), heights[biome][0], heights[biome][1]))
    print("underground biome cells by 64-block band:")
    for band in sorted(under, reverse=True):
        cells = under[band]
        n = sum(cells.values())
        top = ", ".join("%s %.0f%%" % (b.replace("minecraft:", ""), 100.0 * c / n) for b, c in cells.most_common(5))
        print("  Y %4d..%4d  %6d cells: %s" % (MIN_Y + 64 * band, MIN_Y + 64 * band + 63, n, top))
    print("feature blocks: " + ", ".join("%s %d" % (k, features[k]) for k in FEATURE_BLOCKS))


if __name__ == "__main__":
    main()

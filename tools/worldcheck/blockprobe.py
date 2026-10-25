"""Prints the blocks around a position of a saved world, layer by layer (top-down).

    python3 tools/worldcheck/blockprobe.py run/caves1 -319 84 3106 --radius 3 --height 4

Each layer shows a (2r+1) x (2r+1) grid, x to the right, z downwards; blocks are abbreviated
(see LEGEND), fluids with their level (W0 source water, w7 flowing amount 7, w8 falling...).
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anvil import _unpack, read_chunk  # noqa: E402
import math  # noqa: E402

LEGEND = {
    "minecraft:air": ".", "minecraft:cave_air": ".", "minecraft:stone": "#", "minecraft:deepslate": "D",
    "minecraft:dirt": "d", "minecraft:grass_block": "g", "minecraft:gravel": "v", "minecraft:sand": "s",
    "minecraft:cobblestone": "c", "minecraft:mossy_cobblestone": "m", "minecraft:andesite": "a",
    "minecraft:basalt": "B", "minecraft:smooth_basalt": "b", "minecraft:blackstone": "K", "minecraft:magma_block": "M",
    "minecraft:soul_sand": "S", "minecraft:bubble_column": "O", "minecraft:bedrock": "X",
}


def block_at(world, x, y, z, cache):
    cx, cz = x >> 4, z >> 4
    if (cx, cz) not in cache:
        cache[(cx, cz)] = read_chunk(os.path.join(world, "region"), cx, cz)
    nbt = cache[(cx, cz)]
    if nbt is None:
        return None
    for s in nbt["sections"]:
        if s["Y"] == y >> 4 and "block_states" in s:
            states = s["block_states"]
            palette = states["palette"]
            if len(palette) == 1 or "data" not in states:
                return palette[0]
            bits = max(4, math.ceil(math.log2(len(palette))))
            index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
            return palette[_unpack(states["data"], bits, index + 1)[index]]
    return None


def short(entry):
    if entry is None:
        return "?"
    name = entry["Name"]
    props = entry.get("Properties", {})
    if name == "minecraft:water":
        level = int(props.get("level", "0"))
        return "W0" if level == 0 else ("w8" if level >= 8 else "w%d" % (8 - level))
    if name == "minecraft:lava":
        level = int(props.get("level", "0"))
        return "L0" if level == 0 else "l%d" % level
    return LEGEND.get(name, name.split(":")[1][:2])


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("world")
    p.add_argument("x", type=int)
    p.add_argument("y", type=int)
    p.add_argument("z", type=int)
    p.add_argument("--radius", type=int, default=3)
    p.add_argument("--height", type=int, default=3, help="layers above and below y")
    args = p.parse_args()
    cache = {}
    for y in range(args.y + args.height, args.y - args.height - 1, -1):
        print("y %d" % y)
        for z in range(args.z - args.radius, args.z + args.radius + 1):
            row = []
            for x in range(args.x - args.radius, args.x + args.radius + 1):
                row.append("%2s" % short(block_at(args.world, x, y, z, cache)))
            print("  z %5d: %s" % (z, " ".join(row)))


if __name__ == "__main__":
    main()

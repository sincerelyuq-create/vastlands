"""Checks the caves of a generated world: volume, fluids, stability, seams, entrances.

Reads every block of a world made by generate.py (Y -256 up to the highest block) and reports:
- the composition of the underground (below each column's highest solid block) per 32-block band:
  solid rock, cave air, water, lava;
- unstable fluids: source water or lava with open air below it or beside it at its own height (vanilla
  flows it), water touching lava, split into cave fluids (below the ground) and surface water,
  inside chunks and on chunk borders. Flowing water and lava (falls, springs) are listed apart;
- lava above Y -54 and the lowest and highest lava;
- seams: how often horizontally neighbouring blocks differ (open vs solid) across chunk borders
  compared with inside chunks; a seam in the caves would raise the border figure;
- entrances: columns where cave air meets open sky (an opening of the caves at the surface);
- with --png, a plan of the caves (each column coloured by the height of its highest cave block, water
  blue, lava orange) and with --section X or Z, a vertical section through the square.

Needs numpy (and Pillow for the images).

    python3 tools/worldcheck/cavecheck.py run/caves1 --png run/caves1-plan.png
"""
import argparse
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anvil import read_chunk  # noqa: E402

MIN_Y = -256
SEA_LEVEL = 63
AIR, SOLID, WATER, WATER_FLOW, LAVA, LAVA_FLOW, PASSABLE, WATERLOGGED, PLANT = range(9)
# Trees and big plants: solid for fluids, but not ground (their shade is not a cave).
PLANT_HINTS = ("leaves", "_log", "_wood", "mushroom_block", "mushroom_stem", "mangrove_roots", "bee_nest", "bamboo",
               "cactus", "sugar_cane", "chorus", "pumpkin", "melon", "azalea", "big_dripleaf", "small_dripleaf",
               "kelp", "cocoa")
AIRS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
WATER_PLANTS = {"minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:kelp", "minecraft:kelp_plant",
                "minecraft:bubble_column"}
# Non-solid blocks fluids can flow into (approximation): plants, snow layers, vines, lichen, dripstone tips...
PASSABLE_HINTS = ("grass", "fern", "flower", "tulip", "bush", "mushroom", "vine", "lichen", "petals", "sapling",
                  "roots", "moss_carpet", "snow", "dandelion", "poppy", "orchid", "allium", "bluet", "daisy",
                  "cornflower", "lily_of_the_valley", "torch", "rail", "pointed_dripstone", "spore_blossom",
                  "cave_vines", "hanging_roots", "glow_berries", "sculk_vein", "leaf_litter", "button",
                  "pressure_plate", "carpet", "sign", "banner", "cobweb", "string", "wildflowers")


def classify(entry):
    name = entry["Name"]
    props = entry.get("Properties", {})
    if name in AIRS:
        return AIR
    if name == "minecraft:water":
        return WATER if props.get("level", "0") == "0" else WATER_FLOW
    if name == "minecraft:lava":
        return LAVA if props.get("level", "0") == "0" else LAVA_FLOW
    if name in WATER_PLANTS or props.get("waterlogged") == "true":
        return WATERLOGGED
    short = name.split(":", 1)[1]
    if short.endswith("_block") or short in ("podzol", "mycelium", "dirt_path", "farmland", "moss_block", "snow_block"):
        return SOLID
    if any(h in short for h in PLANT_HINTS):
        return PLANT
    if any(h in short for h in PASSABLE_HINTS):
        return PASSABLE
    return SOLID


def unpack(longs, bits, count=4096):
    data = np.array([v & 0xFFFFFFFFFFFFFFFF for v in longs], dtype=np.uint64)
    per_long = 64 // bits
    shifts = (np.arange(per_long, dtype=np.uint64) * np.uint64(bits))
    values = (data[:, None] >> shifts[None, :]) & np.uint64((1 << bits) - 1)
    return values.reshape(-1)[:count].astype(np.int32)


def load(path):
    with open(os.path.join(path, "worldcheck.json")) as f:
        meta = json.load(f)
    lo_x, lo_z, hi_x, hi_z = meta["chunk_range"]
    nx = (hi_x - lo_x + 1) * 16
    nz = (hi_z - lo_z + 1) * 16
    chunks = {}
    top = MIN_Y
    for cx in range(lo_x, hi_x + 1):
        for cz in range(lo_z, hi_z + 1):
            nbt = read_chunk(os.path.join(path, "region"), cx, cz)
            if nbt is None or nbt.get("Status") != "minecraft:full":
                raise SystemExit("chunk %d,%d is missing or not fully generated" % (cx, cz))
            chunks[(cx, cz)] = nbt
            for s in nbt["sections"]:
                states = s.get("block_states")
                if states and (len(states["palette"]) > 1 or states["palette"][0]["Name"] not in AIRS):
                    top = max(top, s["Y"] * 16 + 15)
    height = top - MIN_Y + 1
    blocks = np.zeros((height, nz, nx), dtype=np.uint8)
    levels = np.zeros((height, nz, nx), dtype=np.uint8)
    for (cx, cz), nbt in chunks.items():
        ox = (cx - lo_x) * 16
        oz = (cz - lo_z) * 16
        for s in nbt["sections"]:
            states = s.get("block_states")
            y0 = s["Y"] * 16
            if states is None or y0 + 15 < MIN_Y or y0 > top:
                continue
            palette = np.array([classify(e) for e in states["palette"]], dtype=np.uint8)
            water_levels = np.array([int(e.get("Properties", {}).get("level", "0")) if e["Name"] == "minecraft:water" else 0
                                     for e in states["palette"]], dtype=np.uint8)
            if len(palette) == 1 or "data" not in states:
                indices = np.zeros(4096, dtype=np.int32)
            else:
                bits = max(4, math.ceil(math.log2(len(palette))))
                indices = unpack(states["data"], bits)
            ys = slice(y0 - MIN_Y, y0 - MIN_Y + 16)
            blocks[ys, oz:oz + 16, ox:ox + 16] = palette[indices].reshape(16, 16, 16)
            levels[ys, oz:oz + 16, ox:ox + 16] = water_levels[indices].reshape(16, 16, 16)
    return meta, blocks, levels, (lo_x * 16, lo_z * 16)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("world")
    p.add_argument("--png", help="plan of the caves")
    p.add_argument("--section", action="append", default=[], help="vertical section: x=N or z=N (block coordinate)")
    args = p.parse_args()
    meta, b, lv, (x0, z0) = load(args.world)
    height, nz, nx = b.shape
    ys = np.arange(height)[:, None, None] + MIN_Y
    solid = (b == SOLID) | (b == WATERLOGGED)
    # Ground: the highest solid block of each column; everything below it is underground.
    has_solid = solid.any(axis=0)
    top_index = height - 1 - np.argmax(solid[::-1], axis=0)
    ground = np.where(has_solid, top_index + MIN_Y, MIN_Y)
    under = ys < ground[None, :, :]
    open_ = (b == AIR) | (b == PASSABLE) | (b == PLANT) & ~under
    fluid_water = (b == WATER) | (b == WATER_FLOW)
    fluid_lava = (b == LAVA) | (b == LAVA_FLOW)

    print("world %s: %d x %d columns from x %d z %d, Y %d..%d" % (args.world, nx, nz, x0, z0, MIN_Y, MIN_Y + height - 1))
    print("underground composition (below each column's highest solid block):")
    for lo in range(MIN_Y + (height - 1) // 32 * 32, MIN_Y - 1, -32):
        i0, i1 = lo - MIN_Y, min(height, lo - MIN_Y + 32)
        u = under[i0:i1]
        total = int(u.sum())
        if total == 0:
            continue
        air = int((open_[i0:i1] & u).sum())
        water = int((fluid_water[i0:i1] & u).sum())
        lava = int((fluid_lava[i0:i1] & u).sum())
        print("  Y %4d..%4d  cave %6.2f%%  air %6.2f%%  water %6.2f%%  lava %6.2f%%  of %d" % (
            lo, lo + 31, 100.0 * (air + water + lava) / total, 100.0 * air / total, 100.0 * water / total,
            100.0 * lava / total, total))
    u = under
    total = int(u.sum())
    cave = int(((open_ | fluid_water | fluid_lava) & u).sum())
    print("  all: cave %.2f%% of %d underground blocks" % (100.0 * cave / max(1, total), total))

    # Unstable sources: open air below, or beside at the same height (interior blocks only).
    def shifted(mask, dy, dz, dx):
        out = np.zeros_like(mask)
        sy = slice(max(0, -dy), height - max(0, dy))
        ty = slice(max(0, dy), height - max(0, -dy))
        sz = slice(max(0, -dz), nz - max(0, dz))
        tz = slice(max(0, dz), nz - max(0, -dz))
        sx = slice(max(0, -dx), nx - max(0, dx))
        tx = slice(max(0, dx), nx - max(0, -dx))
        out[ty, tz, tx] = mask[sy, sz, sx]
        return out

    interior = np.zeros((height, nz, nx), dtype=bool)
    interior[1:-1, 1:-1, 1:-1] = True
    air_below = shifted(open_, 1, 0, 0)  # value at y comes from y-1
    air_side = shifted(open_, 0, 1, 0) | shifted(open_, 0, -1, 0) | shifted(open_, 0, 0, 1) | shifted(open_, 0, 0, -1)
    lava_near = shifted(fluid_lava, 1, 0, 0) | shifted(fluid_lava, -1, 0, 0) | shifted(fluid_lava, 0, 1, 0) | \
        shifted(fluid_lava, 0, -1, 0) | shifted(fluid_lava, 0, 0, 1) | shifted(fluid_lava, 0, 0, -1)
    border = np.zeros((nz, nx), dtype=bool)
    xs = (np.arange(nx) + x0) & 15
    zs = (np.arange(nz) + z0) & 15
    border[:, (xs == 0) | (xs == 15)] = True
    border[(zs == 0) | (zs == 15), :] = True
    border3 = np.broadcast_to(border[None, :, :], b.shape)
    for name, src in (("water", b == WATER), ("lava", b == LAVA)):
        unstable = src & (air_below | air_side) & interior
        for zone_name, zone in (("in caves", under), ("at the surface", ~under)):
            sel = unstable & zone
            n_in = int((sel & ~border3).sum())
            n_border = int((sel & border3).sum())
            print("unstable %s sources %s: %d inside chunks, %d on chunk borders (of %d sources)" % (
                name, zone_name, n_in, n_border, int((src & zone).sum())))
            if n_in + n_border:
                idx = np.argwhere(sel)[:5]
                print("    e.g. " + ", ".join("%d %d %d" % (x0 + i[2], MIN_Y + i[0], z0 + i[1]) for i in idx))
    touching = fluid_water & lava_near & interior
    print("water touching lava: %d blocks (%d in caves)" % (int(touching.sum()), int((touching & under).sum())))
    print("flowing water %d, flowing lava %d blocks (falls, springs, spillways)" % (int((b == WATER_FLOW).sum()), int((b == LAVA_FLOW).sum())))

    # Flowing water: would vanilla's fluid tick change it? (port of FlowingFluid.getNewLiquid and
    # spreading, as in RiverWaterTest). Lips and curtains of falls and swallets must be stable;
    # vanilla springs placed by features usually are not yet (they still have pending ticks).
    def water_level(k, j, i):
        t = b[k, j, i]
        if t == WATER or t == WATERLOGGED and False:
            return 0
        if t == WATER_FLOW:
            return int(lv[k, j, i])
        return -1

    def solid_at(k, j, i):
        return b[k, j, i] in (SOLID, WATERLOGGED, PLANT)

    changes = {"caves": 0, "surface": 0}
    examples = []
    for k, j, i in np.argwhere((b == WATER_FLOW) & interior):
        level = int(lv[k, j, i])
        sides = [(k, j, i + 1), (k, j, i - 1), (k, j + 1, i), (k, j - 1, i)]
        amount = 0
        sources = 0
        for side in sides:
            l = water_level(*side)
            if l >= 0:
                amount = max(amount, 8 if l == 0 or l >= 8 else 8 - l)
                sources += 1 if l == 0 else 0
        below = (k - 1, j, i)
        above = (k + 1, j, i)
        if sources >= 2 and (solid_at(*below) or water_level(*below) == 0):
            expected = 0
        elif water_level(*above) >= 0:
            expected = 8
        else:
            expected = -1 if amount - 1 <= 0 else 8 - (amount - 1)
        change = expected != level
        if not change and b[below] == AIR:
            change = True
        if not change and not water_level(*below) >= 0:
            amt = 8 if level >= 8 else 8 - level
            change = any(b[s] == AIR for s in sides) and amt - 1 > 0
        if change:
            zone = "caves" if under[k, j, i] else "surface"
            changes[zone] += 1
            if len(examples) < 5:
                examples.append("%d %d %d (level %d)" % (x0 + i, MIN_Y + k, z0 + j, level))
    print("flowing water that a fluid tick would change: %d in caves, %d at the surface%s" % (
        changes["caves"], changes["surface"], (" e.g. " + ", ".join(examples)) if examples else ""))
    lava_y = np.argwhere(fluid_lava.any(axis=(1, 2)))
    if len(lava_y):
        print("lava between Y %d and %d; above Y -54: %d blocks" % (
            MIN_Y + lava_y.min(), MIN_Y + lava_y.max(), int(fluid_lava[-54 - MIN_Y + 1:].sum())))
    else:
        print("no lava")

    # Seams: open/solid changes between horizontal neighbours across chunk borders vs inside.
    filled = (b == SOLID) | (b == WATERLOGGED)
    dx = filled[:, :, 1:] != filled[:, :, :-1]
    cross_x = ((np.arange(nx - 1) + x0 + 1) & 15) == 0
    dz = filled[:, 1:, :] != filled[:, :-1, :]
    cross_z = ((np.arange(nz - 1) + z0 + 1) & 15) == 0
    u_x = under[:, :, 1:] & under[:, :, :-1]
    u_z = under[:, 1:, :] & under[:, :-1, :]
    cx_rate = (dx & u_x)[:, :, cross_x].sum() / max(1, u_x[:, :, cross_x].sum())
    ix_rate = (dx & u_x)[:, :, ~cross_x].sum() / max(1, u_x[:, :, ~cross_x].sum())
    cz_rate = (dz & u_z)[:, cross_z, :].sum() / max(1, u_z[:, cross_z, :].sum())
    iz_rate = (dz & u_z)[:, ~cross_z, :].sum() / max(1, u_z[:, ~cross_z, :].sum())
    print("underground open/solid changes between neighbours: across chunk borders %.4f (x) %.4f (z), inside chunks %.4f (x) %.4f (z)" % (
        cx_rate, cz_rate, ix_rate, iz_rate))

    # Entrances: cave air in the block right below a column's open sky that continues down below ground.
    # A column is an entrance if some cave air block has the sky directly above it through open blocks.
    above_all_open = np.flip(np.cumprod(np.flip(open_ | fluid_water, axis=0), axis=0), axis=0).astype(bool)
    sky_cave = above_all_open & under
    entrances = sky_cave.any(axis=0)
    print("columns where caves open to the sky: %d of %d (%.2f%%)" % (int(entrances.sum()), nx * nz, 100.0 * entrances.mean()))
    # Mouths: underground air beside open air above a neighbouring column's ground (side openings).
    cave_air = open_ & under
    outside_air = open_ & ~under
    mouth = cave_air & (shifted(outside_air, 0, 1, 0) | shifted(outside_air, 0, -1, 0) | shifted(outside_air, 0, 0, 1) | shifted(outside_air, 0, 0, -1)) & interior
    print("cave mouths: %d underground air blocks beside open air, in %d columns" % (int(mouth.sum()), int(mouth.any(axis=0).sum())))

    if args.png:
        from PIL import Image
        img = np.zeros((nz, nx, 3), dtype=np.uint8)
        img[:, :] = (150, 150, 150)
        cave_any = (open_ | fluid_water | fluid_lava) & under
        idx = height - 1 - np.argmax(cave_any[::-1], axis=0)
        has = cave_any.any(axis=0)
        import colorsys
        for j in range(nz):
            for i in range(nx):
                if not has[j, i]:
                    continue
                k = idx[j, i]
                y = k + MIN_Y
                t = max(0.0, min(1.0, (y + 240.0) / 400.0))
                r, g, bl = colorsys.hsv_to_rgb(0.33 * t, 0.85, 0.45 + 0.55 * t)
                c = (int(r * 255), int(g * 255), int(bl * 255))
                if fluid_water[k, j, i]:
                    c = (c[0] // 2 + 30, c[1] // 2 + 60, c[2] // 2 + 127)
                elif fluid_lava[k, j, i]:
                    c = (255, 106, 0)
                img[j, i] = c
        Image.fromarray(img).save(args.png)
        print("wrote " + args.png)
    for spec in args.section:
        from PIL import Image
        axis, value = spec.split("=")
        value = int(value)
        if axis == "x":
            sl = b[:, :, value - x0].copy()
            below = under[:, :, value - x0]
        else:
            sl = b[:, value - z0, :].copy()
            below = under[:, value - z0, :]
        # Cave air (below the ground) black, open sky pale, trees green.
        sl[(sl == AIR) & below] = 9
        colors = np.array([[200, 220, 240], [110, 110, 110], [46, 134, 255], [120, 180, 255], [255, 122, 16],
                           [255, 170, 80], [90, 160, 90], [60, 110, 200], [60, 130, 50], [16, 16, 16]], dtype=np.uint8)
        img = colors[sl[::-1]]
        name = os.path.splitext(args.png or os.path.join(args.world, "caves"))[0] + "-%s%d.png" % (axis, value)
        Image.fromarray(img).save(name)
        print("wrote " + name)


if __name__ == "__main__":
    main()

"""Checks the trees and forest floor of a generated world.

Reads every block from Y 30 up of a world made by generate.py and reports:
- tree blocks by wood: logs (upright and lying), wood (bark) blocks, leaves; mushroom blocks;
  vines, cocoa, bee nests;
- leaves: the stored distance (vanilla's DISTANCE property) and the distance recomputed from the
  logs of the world (6-neighbour steps through leaves, like LeavesBlock.updateDistance). Leaves
  stored or recomputed at 7 decay (persistent leaves aside); a stored distance below the
  recomputed one changes at the first neighbour update;
- trunks: upright logs whose block below is neither log nor wood: standing on ground, standing
  in water, or on air, plants or leaves (most of these are branch logs); and the connected bodies
  of logs (26 neighbours): a body with no log on ground or in water floats. Bodies and leaves
  within 16 blocks of the square's edge are left out (their trees may stand outside it);
- trees per chunk, trunk heights (highest tree block above the base within 2 columns), canopy cover
  (land columns with leaves or logs above the ground), the forest floor (plants, leaf litter, moss);
- seams: how often a tree block (log, leaves) has a tree block beside it, inside chunks against
  across chunk borders (a tree cut at a border would lower the border figure).

    python3 tools/worldcheck/forestcheck.py run/f1 [--json FILE] [--png FILE]
"""
import argparse
import json
import math
import os
import sys
from collections import Counter, deque

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anvil import read_chunk  # noqa: E402

LOW_Y = 30
AIR, GROUND, LOG_Y, LOG_H, WOOD, LEAVES, PLANT, WATER, DECOR, MUSHROOM_BLOCK = range(10)
WOODS = ["oak", "birch", "spruce", "dark_oak", "jungle", "acacia", "mangrove", "cherry", "pale_oak", "azalea", "flowering_azalea",
         "crimson", "warped", "other"]
AIRS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
PLANT_NAMES = ("short_grass", "tall_grass", "fern", "large_fern", "bush", "firefly_bush", "leaf_litter", "moss_carpet", "brown_mushroom",
               "red_mushroom", "short_dry_grass", "tall_dry_grass", "dead_bush", "sweet_berry_bush", "wildflowers", "pink_petals", "snow")


def wood_of(short):
    for i, w in enumerate(WOODS):
        if short.startswith(w + "_") or short.startswith("stripped_" + w + "_"):
            if w == "dark_oak" or not short.startswith("dark_oak"):
                return i
    return len(WOODS) - 1


def classify(entry):
    """(kind, wood, stored leaf distance, persistent, plant name or None)."""
    name = entry["Name"]
    props = entry.get("Properties", {})
    if name in AIRS:
        return AIR, 0, 0, 0, None
    short = name.split(":", 1)[1]
    if name == "minecraft:water" or name == "minecraft:bubble_column":
        return WATER, 0, 0, 0, None
    if short.endswith("_leaves"):
        w = wood_of(short.replace("flowering_azalea", "flowering_azalea"))
        if short.startswith("azalea"):
            w = WOODS.index("azalea")
        if short.startswith("flowering_azalea"):
            w = WOODS.index("flowering_azalea")
        return LEAVES, w, int(props.get("distance", "7")), 1 if props.get("persistent") == "true" else 0, None
    if short.endswith("_log") or short in ("crimson_stem", "warped_stem", "stripped_crimson_stem", "stripped_warped_stem"):
        kind = LOG_Y if props.get("axis", "y") == "y" else LOG_H
        return kind, wood_of(short), 0, 0, None
    if short.endswith("_wood") or short.endswith("_hyphae"):
        return WOOD, wood_of(short), 0, 0, None
    if short in ("mushroom_stem", "brown_mushroom_block", "red_mushroom_block"):
        return MUSHROOM_BLOCK, 0, 0, 0, short
    if short in ("vine", "cocoa", "bee_nest"):
        return DECOR, 0, 0, 0, short
    if short in ("melon", "pumpkin"):
        return PLANT, 0, 0, 0, short
    if short in PLANT_NAMES or any(h in short for h in ("flower", "tulip", "poppy", "dandelion", "orchid", "allium", "bluet", "daisy",
                                                         "cornflower", "lily_of_the_valley", "sapling", "rose_bush", "lilac", "peony",
                                                         "sunflower", "seagrass", "kelp", "lily_pad", "pumpkin", "melon", "sugar_cane",
                                                         "bamboo")):
        return PLANT, 0, 0, 0, short
    return GROUND, 0, 0, 0, short


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
    top = LOW_Y
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
    height = top - LOW_Y + 1
    kind = np.zeros((height, nz, nx), dtype=np.uint8)
    wood = np.zeros((height, nz, nx), dtype=np.uint8)
    dist = np.zeros((height, nz, nx), dtype=np.uint8)
    pers = np.zeros((height, nz, nx), dtype=np.uint8)
    plants = Counter()
    names = {}
    for (cx, cz), nbt in chunks.items():
        ox = (cx - lo_x) * 16
        oz = (cz - lo_z) * 16
        for s in nbt["sections"]:
            states = s.get("block_states")
            y0 = s["Y"] * 16
            if states is None or y0 + 15 < LOW_Y or y0 > top:
                continue
            info = [classify(e) for e in states["palette"]]
            pk = np.array([i[0] for i in info], dtype=np.uint8)
            pw = np.array([i[1] for i in info], dtype=np.uint8)
            pd = np.array([i[2] for i in info], dtype=np.uint8)
            pp = np.array([i[3] for i in info], dtype=np.uint8)
            if len(pk) == 1 or "data" not in states:
                idx = np.zeros(4096, dtype=np.int32)
            else:
                bits = max(4, math.ceil(math.log2(len(pk))))
                idx = unpack(states["data"], bits)
            lo = max(y0, LOW_Y)
            sl = slice(lo - y0, 16)
            dst = slice(lo - LOW_Y, y0 + 16 - LOW_Y)
            for arr, pal in ((kind, pk), (wood, pw), (dist, pd), (pers, pp)):
                arr[dst, oz:oz + 16, ox:ox + 16] = pal[idx].reshape(16, 16, 16)[sl]
            counts = np.bincount(idx.reshape(16, 16, 16)[sl].reshape(-1), minlength=len(info))
            for i, (k, _, _, _, nm) in enumerate(info):
                if nm is not None and k in (PLANT, DECOR, MUSHROOM_BLOCK) and counts[i]:
                    plants[nm] += int(counts[i])
    return meta, kind, wood, dist, pers, plants, (lo_x * 16, lo_z * 16)


def recompute_distance(kind):
    """Leaf distances from the world's logs, through leaves (6 neighbours), capped at 7."""
    d = np.full(kind.shape, 7, dtype=np.uint8)
    logs = (kind == LOG_Y) | (kind == LOG_H) | (kind == WOOD)
    leaves = kind == LEAVES
    d[logs] = 0
    frontier = logs
    for step in range(1, 7):
        grown = np.zeros_like(frontier)
        grown[1:] |= frontier[:-1]
        grown[:-1] |= frontier[1:]
        grown[:, 1:] |= frontier[:, :-1]
        grown[:, :-1] |= frontier[:, 1:]
        grown[:, :, 1:] |= frontier[:, :, :-1]
        grown[:, :, :-1] |= frontier[:, :, 1:]
        new = grown & leaves & (d == 7)
        d[new] = step
        frontier = new
    return d


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("world")
    p.add_argument("--json")
    p.add_argument("--png")
    p.add_argument("--compare", help="another world of the same square: share of identical tree blocks (16+ blocks inside the square)")
    args = p.parse_args()
    meta, kind, wood, dist, pers, plants, (bx, bz) = load(args.world)
    if args.compare:
        _, kind2, wood2, _, _, _, _ = load(args.compare)
        hh = min(kind.shape[0], kind2.shape[0])
        a = np.isin(kind[:hh], [LOG_Y, LOG_H, WOOD, LEAVES, MUSHROOM_BLOCK])
        b = np.isin(kind2[:hh], [LOG_Y, LOG_H, WOOD, LEAVES, MUSHROOM_BLOCK])
        inner2 = np.zeros(a.shape[1:], dtype=bool)
        inner2[16:-16, 16:-16] = True
        a &= inner2[None]
        b &= inner2[None]
        either = a | b
        same = a & b & (kind[:hh] == kind2[:hh]) & (wood[:hh] == wood2[:hh])
        diff = either & ~same
        zs, xs = np.nonzero(diff.any(axis=0))
        print(json.dumps({"tree blocks in either": int(either.sum()), "identical": int(same.sum()),
                          "share identical": round(float(same.sum()) / max(1, either.sum()), 6),
                          "differing columns": int(len(zs)),
                          "differing examples": [[int(bx + x), int(bz + z)] for z, x in list(zip(zs, xs))[:12]]}, indent=2))
        return
    h, nz, nx = kind.shape
    report = {"world": args.world, "columns": nx * nz, "chunks": nx * nz // 256}
    trunk_like = (kind == LOG_Y) | (kind == LOG_H) | (kind == WOOD)
    report["blocks"] = {}
    for w, name in enumerate(WOODS):
        logs = int(((kind == LOG_Y) & (wood == w)).sum())
        lying = int(((kind == LOG_H) & (wood == w)).sum())
        bark = int(((kind == WOOD) & (wood == w)).sum())
        leaves = int(((kind == LEAVES) & (wood == w)).sum())
        if logs + lying + bark + leaves:
            report["blocks"][name] = {"upright logs": logs, "lying logs": lying, "wood": bark, "leaves": leaves}
    leaves = kind == LEAVES
    natural = leaves & (pers == 0)
    true = recompute_distance(kind)
    report["leaves"] = {
        "total": int(leaves.sum()),
        "persistent": int((leaves & (pers == 1)).sum()),
        "stored distance histogram": {str(k): int((natural & (dist == k)).sum()) for k in range(1, 8)},
        "stored 7 (decays)": int((natural & (dist == 7)).sum()),
        "recomputed 7 (decays after an update)": int((natural & (true == 7)).sum()),
        "stored below recomputed": int((natural & (dist < true)).sum()),
        "stored above recomputed": int((natural & (dist > true)).sum()),
    }
    inner = np.zeros((nz, nx), dtype=bool)
    inner[16:nz - 16, 16:nx - 16] = True
    natural_inner = natural & inner[None, :, :]
    report["leaves"]["recomputed 7, 16+ blocks inside the square"] = int((natural_inner & (true == 7)).sum())
    report["leaves"]["stored below recomputed, 16+ blocks inside"] = int((natural_inner & (dist < true)).sum())
    decaying = np.argwhere(natural_inner & ((dist == 7) | (true == 7)))
    report["leaves"]["decaying examples"] = [[int(bx + c[2]), int(c[0] + LOW_Y), int(bz + c[1])] for c in decaying[:8]]

    # Trunk bases: upright logs (or wood) with neither log nor wood below.
    below = np.zeros_like(kind)
    below[1:] = kind[:-1]
    base = ((kind == LOG_Y) | (kind == WOOD)) & ~np.isin(below, [LOG_Y, LOG_H, WOOD])
    base[0] = False
    on_ground = base & (below == GROUND)
    in_water = base & (below == WATER)
    floating = base & np.isin(below, [AIR, PLANT, LEAVES, DECOR])
    ys, zs, xs = np.nonzero(floating)
    border = ((xs % 16 == 0) | (xs % 16 == 15) | (zs % 16 == 0) | (zs % 16 == 15))
    report["trunks"] = {
        "on ground": int(on_ground.sum()),
        "in water": int(in_water.sum()),
        "floating": int(floating.sum()),
        "floating on chunk border columns": int(border.sum()),
        "floating examples": [[int(bx + x), int(y + LOW_Y), int(bz + z)] for y, z, x in list(zip(ys, zs, xs))[:10]],
        "in water examples": [[int(bx + c[2]), int(c[0] + LOW_Y), int(bz + c[1])] for c in np.argwhere(in_water)[:10]],
    }
    # Bodies of logs (26 neighbours) without support.
    solid_tree = trunk_like | (kind == MUSHROOM_BLOCK)
    coords = np.argwhere(solid_tree)
    cells = set(map(tuple, coords.tolist()))
    seen = set()
    bodies = 0
    unsupported = []
    for start in map(tuple, coords.tolist()):
        if start in seen:
            continue
        bodies += 1
        queue = deque([start])
        seen.add(start)
        supported = False
        edge = False
        members = []
        while queue:
            y, z, x = queue.popleft()
            members.append((y, z, x))
            if y > 0 and kind[y - 1, z, x] in (GROUND, WATER):
                supported = True
            if not inner[z, x]:
                edge = True
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        n = (y + dy, z + dz, x + dx)
                        if n in cells and n not in seen:
                            seen.add(n)
                            queue.append(n)
        if not supported and not edge:
            y, z, x = min(members)
            unsupported.append((len(members), int(bx + x), int(y + LOW_Y), int(bz + z)))
    unsupported.sort(reverse=True)
    report["trunks"]["log bodies"] = bodies
    report["trunks"]["floating log bodies (inside)"] = len(unsupported)
    report["trunks"]["floating bodies: size, lowest block"] = unsupported[:12]

    # Trees: upright trunk bases on ground, one per connected base (2x2 trunks count once).
    base_cols = np.zeros((nz, nx), dtype=bool)
    gy, gz, gx = np.nonzero(on_ground & (kind == LOG_Y))
    taken = set()
    tops = []
    any_tree = trunk_like | leaves | (kind == MUSHROOM_BLOCK)
    column_top = np.where(any_tree.any(axis=0), h - 1 - np.argmax(any_tree[::-1], axis=0), -1)
    trees = 0
    for y, z, x in zip(gy, gz, gx):
        if (z, x) in taken or (z - 1, x) in taken or (z, x - 1) in taken or (z - 1, x - 1) in taken:
            taken.add((z, x))
            continue
        taken.add((z, x))
        trees += 1
        z0, z1 = max(0, z - 2), min(nz, z + 3)
        x0, x1 = max(0, x - 2), min(nx, x + 3)
        tops.append(int(column_top[z0:z1, x0:x1].max()) - int(y) + 1)
    tops.sort()
    report["trees"] = {
        "count": trees,
        "per chunk": round(trees / max(1, nx * nz / 256), 2),
        "height p10/p50/p90/max": [tops[len(tops) // 10], tops[len(tops) // 2], tops[len(tops) * 9 // 10], tops[-1]] if tops else [],
        "of 20+ blocks": sum(1 for t in tops if t >= 20),
    }
    # Canopy: land columns with tree blocks above the highest ground block.
    ground = kind == GROUND
    ground_top = np.where(ground.any(axis=0), h - 1 - np.argmax(ground[::-1], axis=0), -1)
    water_col = (kind == WATER).any(axis=0)
    canopy_col = column_top > ground_top
    land = ~water_col | (ground_top >= 0)
    report["canopy"] = {"columns with tree cover": round(float((canopy_col & land).sum()) / max(1, land.sum()), 4)}
    report["floor and decorations"] = dict(plants.most_common(30))

    # Seams: tree blocks with a tree block beside them, inside chunks vs across borders.
    tree = trunk_like | leaves
    def pair(axis):
        a = tree.take(range(0, tree.shape[axis] - 1), axis=axis)
        b = tree.take(range(1, tree.shape[axis]), axis=axis)
        idx = np.arange(tree.shape[axis] - 1)
        border = (idx % 16) == 15
        shape = [1, 1, 1]
        shape[axis] = -1
        border = border.reshape(shape)
        either = a | b
        both = a & b
        inside = float((both & ~border).sum()) / max(1, (either & ~border).sum())
        across = float((both & border).sum()) / max(1, (either & border).sum())
        return round(inside, 4), round(across, 4)
    sx = pair(2)
    sz = pair(1)
    report["seams"] = {"x inside/across": sx, "z inside/across": sz}
    print(json.dumps(report, indent=2))
    if args.json:
        with open(args.json, "w") as f:
            json.dump(report, f, indent=2)
    if args.png:
        from PIL import Image
        img = np.zeros((nz, nx, 3), dtype=np.uint8)
        img[:] = (110, 150, 70)
        img[water_col & ~canopy_col] = (60, 95, 200)
        top_kind = np.zeros((nz, nx), dtype=np.uint8)
        top_wood = np.zeros((nz, nx), dtype=np.uint8)
        for z in range(nz):
            for x in range(nx):
                t = column_top[z, x]
                if t >= 0:
                    top_kind[z, x] = kind[t, z, x]
                    top_wood[z, x] = wood[t, z, x]
        colors = {0: (63, 125, 36), 1: (106, 148, 71), 2: (53, 87, 56), 3: (45, 92, 26), 4: (59, 138, 30), 5: (91, 126, 34)}
        for (zz, xx) in zip(*np.nonzero(canopy_col)):
            c = colors.get(int(top_wood[zz, xx]), (80, 120, 60)) if top_kind[zz, xx] == LEAVES else (107, 84, 52)
            img[zz, xx] = c
        for _, x, y, z in unsupported:
            img[z - bz, x - bx] = (255, 0, 255)
        Image.fromarray(img).save(args.png)
        print("wrote", args.png)


if __name__ == "__main__":
    main()

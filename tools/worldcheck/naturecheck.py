"""Checks the natural formations of a generated world (rocks, deadwood, mushrooms, cave formations).

Reads every block of a world made by generate.py (Y -256 up to the highest block) and reports:
- floating blocks: solid blocks (rock, logs, leaves, mushroom blocks ...; not plants, fluids or
  hanging decorations) not held face to face, through other solid blocks, by the bedrock floor.
  Counted 16+ blocks inside the square (a body at the edge may rest on blocks outside it), by
  block, with examples;
- pointed dripstone: tips that would break at their first update (no sturdy block or dripstone
  of the same direction behind them), and stacks whose free end is not a tip;
- huge mushroom blocks: faces shown against a block of the same kind (vanilla hides those);
- block counts of the formations' materials (dripstone, moss, sculk, calcite, tuff, basalt,
  amethyst, granite, diorite, andesite, mossy cobblestone, mushroom blocks, stripped logs,
  mangrove roots, rooted dirt, hanging roots ...), and with --compare the same square of another
  world (before and after);
- seams: for blocks that differ from the --compare world (what the newer code added), how often
  such a block has another one beside it inside chunks against across chunk borders (a formation
  cut at a border would lower the border figure).

Needs numpy.

    python3 tools/worldcheck/naturecheck.py run/n1 --compare ../vastlands-base/run/base_n1 [--json FILE]
"""
import argparse
import json
import math
import os
import sys
from collections import Counter

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anvil import read_chunk  # noqa: E402

MIN_Y = -256
AIRS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
FLUIDS = {"minecraft:water", "minecraft:lava", "minecraft:bubble_column"}
# Not solid: plants, hanging and attached decorations, thin blocks.
SOFT_HINTS = ("grass", "fern", "flower", "tulip", "bush", "_mushroom", "vine", "lichen", "petals", "sapling", "hanging_roots",
              "moss_carpet", "snow", "dandelion", "poppy", "orchid", "allium", "bluet", "daisy", "cornflower", "lily_of_the_valley",
              "torch", "rail", "pointed_dripstone", "spore_blossom", "cave_vines", "glow_berries", "sculk_vein", "leaf_litter",
              "button", "pressure_plate", "carpet", "sign", "banner", "cobweb", "string", "wildflowers", "seagrass", "kelp",
              "sugar_cane", "bamboo", "cocoa", "amethyst_bud", "amethyst_cluster", "dripleaf", "lily_pad", "sea_pickle", "coral",
              "dead_bush", "cactus", "azalea", "pink_petals", "firefly", "dry_grass", "frogspawn", "candle", "ladder", "lantern",
              "chain", "_head", "skull", "flower_pot", "potted_", "door", "pane", "bars")
# Solid blocks that grow on others (counted as support like any solid block, but listed apart when they float).
MATERIALS = ("dripstone_block", "pointed_dripstone", "moss_block", "moss_carpet", "mossy_cobblestone", "cobblestone", "sculk",
             "sculk_vein", "sculk_sensor", "sculk_shrieker", "sculk_catalyst", "calcite", "tuff", "basalt", "smooth_basalt",
             "amethyst_block", "budding_amethyst", "amethyst_cluster", "granite", "diorite", "andesite", "stone", "deepslate",
             "cobbled_deepslate", "gravel", "clay", "mud", "packed_mud", "coarse_dirt", "rooted_dirt", "podzol", "magma_block",
             "glow_lichen", "hanging_roots", "mangrove_roots", "spore_blossom", "cave_vines", "cave_vines_plant", "azalea",
             "flowering_azalea", "azalea_leaves", "flowering_azalea_leaves", "brown_mushroom_block", "red_mushroom_block",
             "mushroom_stem", "brown_mushroom", "red_mushroom", "sandstone", "terracotta", "packed_ice", "blue_ice")
FACES = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def solid_name(name):
    if name in AIRS or name in FLUIDS:
        return False
    short = name.split(":", 1)[1]
    if short.endswith("_block") or short.endswith("_leaves") or short.endswith("_log") or short.endswith("_wood"):
        return True
    return not any(h in short for h in SOFT_HINTS)


def unpack(longs, bits, count=4096):
    data = np.array([v & 0xFFFFFFFFFFFFFFFF for v in longs], dtype=np.uint64)
    per_long = 64 // bits
    shifts = (np.arange(per_long, dtype=np.uint64) * np.uint64(bits))
    values = (data[:, None] >> shifts[None, :]) & np.uint64((1 << bits) - 1)
    return values.reshape(-1)[:count].astype(np.int32)


class Volume:
    """Every block of a square as ids into a global table of (name, properties) states."""

    def __init__(self, path):
        with open(os.path.join(path, "worldcheck.json")) as f:
            self.meta = json.load(f)
        lo_x, lo_z, hi_x, hi_z = self.meta["chunk_range"]
        self.origin = (lo_x * 16, lo_z * 16)
        self.nx = (hi_x - lo_x + 1) * 16
        self.nz = (hi_z - lo_z + 1) * 16
        self.states = []
        self.index = {}
        chunks = {}
        top = MIN_Y
        for cx in range(lo_x, hi_x + 1):
            for cz in range(lo_z, hi_z + 1):
                nbt = read_chunk(os.path.join(path, "region"), cx, cz)
                if nbt is None or nbt.get("Status") != "minecraft:full":
                    raise SystemExit("chunk %d,%d is missing or not fully generated" % (cx, cz))
                chunks[(cx, cz)] = nbt
                for s in nbt["sections"]:
                    st = s.get("block_states")
                    if st and (len(st["palette"]) > 1 or st["palette"][0]["Name"] not in AIRS):
                        top = max(top, s["Y"] * 16 + 15)
        self.height = top - MIN_Y + 1
        self.ids = np.zeros((self.height, self.nz, self.nx), dtype=np.uint16)
        self.state_id(("minecraft:air", ()))
        for (cx, cz), nbt in chunks.items():
            ox = (cx - lo_x) * 16
            oz = (cz - lo_z) * 16
            for s in nbt["sections"]:
                st = s.get("block_states")
                y0 = s["Y"] * 16
                if st is None or y0 + 15 < MIN_Y or y0 > top:
                    continue
                palette = np.array([self.state_id((e["Name"], tuple(sorted(e.get("Properties", {}).items())))) for e in st["palette"]],
                                   dtype=np.uint16)
                if len(palette) == 1 or "data" not in st:
                    indices = np.zeros(4096, dtype=np.int32)
                else:
                    indices = unpack(st["data"], max(4, math.ceil(math.log2(len(palette)))))
                self.ids[y0 - MIN_Y:y0 - MIN_Y + 16, oz:oz + 16, ox:ox + 16] = palette[indices].reshape(16, 16, 16)

    def state_id(self, state):
        i = self.index.get(state)
        if i is None:
            i = len(self.states)
            self.states.append(state)
            self.index[state] = i
        return i

    def table(self, fn, dtype=bool):
        return np.array([fn(name, dict(props)) for name, props in self.states], dtype=dtype)


def anchored_mask(solid):
    """Solid blocks held face to face through solid blocks by the bottom layer: scans along each
    axis in both directions, repeated until nothing changes."""
    held = np.zeros_like(solid)
    held[0] = solid[0]
    total = -1
    rounds = 0
    while True:
        rounds += 1
        for axis in range(3):
            s = np.moveaxis(solid, axis, 0)
            h = np.moveaxis(held, axis, 0)
            n = s.shape[0]
            for i in range(1, n):
                h[i] |= h[i - 1] & s[i]
            for i in range(n - 2, -1, -1):
                h[i] |= h[i + 1] & s[i]
        now = int(held.sum())
        if now == total:
            return held, rounds
        total = now


def check(path, compare=None):
    v = Volume(path)
    names = [name for name, _ in v.states]
    short = [n.split(":", 1)[1] for n in names]
    solid = v.table(lambda n, p: solid_name(n))[v.ids]
    held, rounds = anchored_mask(solid)
    loose = solid & ~held
    inner = np.zeros_like(loose)
    inner[:, 16:-16, 16:-16] = True
    loose_inner = loose & inner
    by_block = Counter()
    examples = []
    ys, zs, xs = np.nonzero(loose_inner)
    for y, z, x in zip(ys, zs, xs):
        b = short[v.ids[y, z, x]]
        by_block[b] += 1
        if len(examples) < 20:
            examples.append([int(x) + v.origin[0], int(y) + MIN_Y, int(z) + v.origin[1], b])
    report = {"world": path, "blocks": int(v.ids.size), "solid": int(solid.sum()), "support rounds": rounds,
              "floating (16+ inside)": int(loose_inner.sum()), "floating by block": dict(by_block.most_common()),
              "floating examples": examples}

    # Pointed dripstone: attachment and tips.
    pd = v.table(lambda n, p: n == "minecraft:pointed_dripstone")
    down = v.table(lambda n, p: n == "minecraft:pointed_dripstone" and p.get("vertical_direction") == "down")
    tip = v.table(lambda n, p: n == "minecraft:pointed_dripstone" and p.get("thickness") in ("tip", "tip_merge"))
    sturdy = v.table(lambda n, p: solid_name(n) and not n.endswith("leaves"))
    ids = v.ids
    is_pd = pd[ids]
    is_down = down[ids]
    is_tip = tip[ids]
    is_sturdy = sturdy[ids]
    unattached = 0
    endless = 0
    pd_total = int(is_pd.sum())
    for direction in ("down", "up"):
        mask = is_pd & (is_down if direction == "down" else ~is_down)
        same = mask
        if direction == "down":
            behind_ok = np.zeros_like(mask)
            behind_ok[:-1] = is_sturdy[1:] | same[1:]
            ahead_same = np.zeros_like(mask)
            ahead_same[1:] = same[:-1]
        else:
            behind_ok = np.zeros_like(mask)
            behind_ok[1:] = is_sturdy[:-1] | same[:-1]
            ahead_same = np.zeros_like(mask)
            ahead_same[:-1] = same[1:]
        unattached += int((mask & ~behind_ok & inner).sum())
        endless += int((mask & ~ahead_same & ~is_tip & inner).sum())
    report["pointed dripstone"] = {"blocks": pd_total, "unattached (would break)": unattached, "free end not a tip": endless}

    # Huge mushroom faces against the same block.
    wrong = 0
    total = 0
    for kind in ("minecraft:brown_mushroom_block", "minecraft:red_mushroom_block", "minecraft:mushroom_stem"):
        k = v.table(lambda n, p, kind=kind: n == kind)[ids]
        total += int(k.sum())
        for face, (dx, dy, dz) in FACES.items():
            shown = v.table(lambda n, p, kind=kind, face=face: n == kind and p.get(face, "true") == "true")[ids]
            neighbour = np.zeros_like(k)
            src = k[max(0, dy):k.shape[0] + min(0, dy), max(0, dz):k.shape[1] + min(0, dz), max(0, dx):k.shape[2] + min(0, dx)]
            neighbour[max(0, -dy):k.shape[0] + min(0, -dy), max(0, -dz):k.shape[1] + min(0, -dz), max(0, -dx):k.shape[2] + min(0, -dx)] = src
            wrong += int((shown & neighbour & inner).sum())
    report["huge mushroom blocks"] = {"blocks": total, "faces shown against the same block": wrong}

    counts = Counter()
    hist = np.bincount(ids.reshape(-1), minlength=len(names))
    for i, c in enumerate(hist):
        if c and short[i] in MATERIALS:
            counts[short[i]] += int(c)
        if c and short[i].startswith("stripped_"):
            counts["stripped logs"] += int(c)
    report["materials"] = dict(sorted(counts.items()))

    if compare:
        w = Volume(compare)
        if w.meta["chunk_range"] != v.meta["chunk_range"]:
            raise SystemExit("the worlds cover different squares")
        report["compare"] = compare
        other = Counter()
        hist = np.bincount(w.ids.reshape(-1), minlength=len(w.states))
        for i, c in enumerate(hist):
            n = w.states[i][0].split(":", 1)[1]
            if c and n in MATERIALS:
                other[n] += int(c)
            if c and n.startswith("stripped_"):
                other["stripped logs"] += int(c)
        report["materials before"] = dict(sorted(other.items()))
        h = min(v.height, w.height)
        # Block names as shared ids in both worlds (states differ in properties only).
        table = {}
        a_names = np.array([table.setdefault(n, len(table)) for n in names], dtype=np.uint16)
        b_names = np.array([table.setdefault(s[0], len(table)) for s in w.states], dtype=np.uint16)
        # Added solid blocks: solid now, where the other world had another block.
        added = solid[:h] & (a_names[v.ids[:h]] != b_names[w.ids[:h]])
        report["added solid blocks"] = int(added.sum())
        inside = [0, 0]
        border = [0, 0]
        for axis in (2, 1):
            a = np.moveaxis(added, axis, 2)
            pairs = a[:, :, :-1] & a[:, :, 1:]
            starts = a[:, :, :-1]
            at_border = (np.arange(a.shape[2] - 1) % 16) == 15
            border[0] += int(pairs[:, :, at_border].sum())
            border[1] += int(starts[:, :, at_border].sum())
            inside[0] += int(pairs[:, :, ~at_border].sum())
            inside[1] += int(starts[:, :, ~at_border].sum())
        report["seams (added blocks with an added neighbour)"] = {
            "inside chunks": round(inside[0] / max(1, inside[1]), 3), "across chunk borders": round(border[0] / max(1, border[1]), 3)}
    return report


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("world")
    p.add_argument("--compare", help="the same square of another world (before)")
    p.add_argument("--json")
    args = p.parse_args()
    report = check(args.world, args.compare)
    text = json.dumps(report, indent=2)
    print(text)
    if args.json:
        with open(args.json, "w") as f:
            f.write(text)


if __name__ == "__main__":
    main()

"""Checks a generated world for the integration of other mods (Agent 7: Sincerelyuq's Biomes and Overhaul).

Reads every saved chunk of a world made by generate.py and reports:
- surface biomes (the biome cell at each column's ground) and cave biomes (cells weighted by their
  open cave blocks), with shares;
- blocks of other mods (every non-minecraft block) and the formations' vanilla materials, lava;
- fluids that would flow at their first update: source blocks of lava and of other mods' fluids
  (lavender spinel) with air beside or under them, and the same for still water below the ground
  in caves (pools dug into cave floors must not have any);
- structure starts in the square: id, start piece position, surface biome there, and how many of
  their pieces' blocks are buried under sand the dunes heaped (sand above the piece's top inside
  its footprint);
- relief per surface biome: mean and largest height step between neighbouring columns (dunes).

Needs numpy.

    python3 tools/worldcheck/integrationcheck.py run/d_s1 [--json FILE]
"""
import argparse
import json
import os
import sys
from collections import Counter, defaultdict

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anvil import read_chunk, section_blocks, section_biomes  # noqa: E402

MIN_Y = -256
AIRS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
MATERIALS = ("minecraft:magma_block", "minecraft:blackstone", "minecraft:basalt", "minecraft:smooth_basalt", "minecraft:calcite",
             "minecraft:amethyst_block", "minecraft:amethyst_cluster", "minecraft:clay", "minecraft:sea_pickle", "minecraft:tuff",
             "minecraft:netherrack", "minecraft:terracotta", "minecraft:sand", "minecraft:lava", "minecraft:water")


def heights(chunk, name):
    hm = chunk.get("Heightmaps", {}).get(name)
    if hm is None:
        return None
    bits = 10
    out = []
    per = 64 // bits
    for v in hm:
        v &= 0xFFFFFFFFFFFFFFFF
        for _ in range(per):
            out.append(v & 1023)
            v >>= bits
            if len(out) == 256:
                break
    return np.array(out[:256], dtype=np.int32).reshape(16, 16) + MIN_Y - 1  # [z][x]: top block


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("world")
    p.add_argument("--json")
    a = p.parse_args()
    info = json.load(open(os.path.join(a.world, "worldcheck.json")))
    x0, z0, x1, z1 = info["chunk_range"]
    region = os.path.join(a.world, "region")
    size = (x1 - x0 + 1) * 16
    depth = 768
    names = {}
    ids = []

    def code(n):
        c = names.get(n)
        if c is None:
            c = names[n] = len(ids)
            ids.append(n)
        return c

    code("minecraft:air")
    world = np.zeros((depth, size, size), dtype=np.int32)  # [y][z][x]
    surface = np.zeros((size, size), dtype=np.int32)
    surf_biome = np.empty((size, size), dtype=object)
    biome_cells = {}
    starts = []
    for cx in range(x0, x1 + 1):
        for cz in range(z0, z1 + 1):
            ch = read_chunk(region, cx, cz)
            if ch is None:
                continue
            bx, bz = (cx - x0) * 16, (cz - z0) * 16
            for sec in ch.get("sections", []):
                sy = sec["Y"]
                y0 = sy * 16 - MIN_Y
                if y0 < 0 or y0 >= depth or "block_states" not in sec:
                    continue
                blocks = np.array([code(n) for n in section_blocks(sec)], dtype=np.int32).reshape(16, 16, 16)
                world[y0:y0 + 16, bz:bz + 16, bx:bx + 16] = blocks
                if "biomes" in sec:
                    biome_cells[(cx, sy, cz)] = section_biomes(sec)
            h = heights(ch, "OCEAN_FLOOR")
            if h is not None:
                surface[bz:bz + 16, bx:bx + 16] = h
            for sid, s in ch.get("structures", {}).get("starts", {}).items():
                if s.get("id") == "INVALID" or not s.get("Children"):
                    continue
                boxes = [c["BB"] for c in s["Children"]]
                starts.append((s.get("id", sid), boxes))

    def biome_at(x, y, z):  # square-relative x, z; absolute y
        cx, cz = x // 16 + x0, z // 16 + z0
        sy = y >> 4
        cells = biome_cells.get((cx, sy, cz))
        if cells is None:
            return "?"
        return cells[((y & 15) >> 2) * 16 + ((z & 15) >> 2) * 4 + ((x & 15) >> 2)]

    # The probe column of generate.py (glass at the build limits) takes its neighbour's height.
    probe = info.get("probe_column")
    if probe:
        px, pz = probe[0] - x0 * 16, probe[1] - z0 * 16
        if 0 <= px < size - 1 and 0 <= pz < size:
            surface[pz, px] = surface[pz, px + 1]
            world[:, pz, px] = world[:, pz, px + 1]
    for z in range(size):
        for x in range(size):
            surf_biome[z, x] = biome_at(x, int(surface[z, x]), z)

    air = np.isin(world, [names[n] for n in AIRS if n in names])
    report = {"world": a.world, "size": size}
    # Surface and cave biomes.
    report["surface_biomes"] = dict(Counter(surf_biome.ravel()).most_common())
    cave = defaultdict(int)
    below = np.arange(depth)[:, None, None] + MIN_Y < surface[None, :, :] - 8
    open_cave = air & below
    ys, zs, xs = np.nonzero(open_cave[:, ::4, ::4])
    for y, z, x in zip(ys, zs, xs):
        cave[biome_at(x * 4, y + MIN_Y, z * 4)] += 1
    total = sum(cave.values()) or 1
    report["cave_biomes_share"] = {k: round(v / total, 4) for k, v in sorted(cave.items(), key=lambda kv: -kv[1])}
    # Blocks.
    counts = np.bincount(world.ravel(), minlength=len(ids))
    report["mod_blocks"] = {ids[i]: int(counts[i]) for i in range(len(ids)) if not ids[i].startswith("minecraft:") and counts[i]}
    report["materials"] = {m: int(counts[names[m]]) for m in MATERIALS if m in names}
    # Fluids that would flow: sources with air beside or under them (16+ blocks inside the square).
    spill = {}
    for fluid in ("minecraft:lava", "sincerelyuqs_biomes:lavender_spinel", "minecraft:water"):
        if fluid not in names:
            continue
        f = world == names[fluid]
        open_ = np.zeros_like(f)
        open_[1:] |= f[1:] & air[:-1]
        open_[:, :, 1:] |= f[:, :, 1:] & air[:, :, :-1]
        open_[:, :, :-1] |= f[:, :, :-1] & air[:, :, 1:]
        open_[:, 1:, :] |= f[:, 1:, :] & air[:, :-1, :]
        open_[:, :-1, :] |= f[:, :-1, :] & air[:, 1:, :]
        inner = open_[:, 16:-16, 16:-16]
        if fluid == "minecraft:water":
            inner = inner & below[:, 16:-16, 16:-16]
        n = int(inner.sum())
        ex = []
        if n:
            for y, z, x in list(zip(*np.nonzero(inner)))[:5]:
                ex.append([int(x + 16 + x0 * 16), int(y + MIN_Y), int(z + 16 + z0 * 16)])
        spill[fluid] = {"blocks": int(f.sum()), "open": n, "examples": ex}
    report["fluids_open"] = spill
    # Structures.
    sand = names.get("minecraft:sand")
    st = []
    for sid, boxes in starts:
        b0 = boxes[0]
        cxm, czm = (b0[0] + b0[3]) // 2, (b0[2] + b0[5]) // 2
        rx, rz = cxm - x0 * 16, czm - z0 * 16
        biome = surf_biome[rz, rx] if 0 <= rx < size and 0 <= rz < size else "outside"
        buried = 0
        if sand is not None:
            for b in boxes:
                for x in range(max(b[0], x0 * 16), min(b[3], x1 * 16 + 15) + 1):
                    for z in range(max(b[2], z0 * 16), min(b[5], z1 * 16 + 15) + 1):
                        top = b[4] - MIN_Y
                        col = world[top + 1:top + 20, z - z0 * 16, x - x0 * 16]
                        buried += int((col == sand).sum())
        st.append({"id": sid, "start": [cxm, b0[1], czm], "pieces": len(boxes), "surface_biome": biome, "sand_over_pieces": buried})
    report["structures"] = st
    # Relief per surface biome.
    relief = {}
    dx = np.abs(np.diff(surface, axis=1))
    dz = np.abs(np.diff(surface, axis=0))
    for b in set(surf_biome.ravel()):
        m = surf_biome == b
        mx = m[:, 1:] & m[:, :-1]
        mz = m[1:, :] & m[:-1, :]
        steps = np.concatenate([dx[mx], dz[mz]])
        if len(steps):
            relief[b] = {"mean_step": round(float(steps.mean()), 3), "max_step": int(steps.max()),
                         "p99_step": int(np.percentile(steps, 99)), "height_range": [int(surface[m].min()), int(surface[m].max())]}
    report["relief"] = relief
    text = json.dumps(report, indent=1)
    if a.json:
        open(a.json, "w").write(text)
    print(text)


if __name__ == "__main__":
    main()

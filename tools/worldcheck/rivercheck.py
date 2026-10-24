"""Checks the rivers and lakes of a generated world: is the water above sea level stable?

Reads the region files of a world made by generate.py (all of it from Y 56 up) and reports for
the water above sea level (Y >= 63):
- source and flowing water blocks; flowing water split into fall lips (amount 7, not falling),
  falling water, and other flowing water (vanilla springs, anything else);
- unstable water: blocks that vanilla's fluid tick would change if it ran now, computed with the
  rules of FlowingFluid (getNewLiquid, spreading down and to the sides). A source next to (or
  above) air or a replaceable plant leaks; a flowing block whose neighbours give it another state
  changes. Blocks at the edge of the square are skipped (their neighbours are unknown). Reported
  inside chunks and on chunk borders separately: a seam between chunks would leak on the border;
- with --expected (exported by `./gradlew hydrologyPreview --args="... --water FILE"` for the same
  square), how often the generated water top and ground of river and lake columns agree with the
  hydrology model;
- river biome columns (sampled at the 4-block biome grid at Y 63).

    python3 tools/worldcheck/rivercheck.py run/fall1 --expected run/fall1-water.bin --png run/fall1-water.png
"""
import argparse
import json
import math
import os
import struct
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anvil import _unpack, read_chunk, section_biomes  # noqa: E402

SEA_LEVEL = 63
LOW = 56

AIR, SOURCE, FLOWING, SOLID, PLANT, ICE, LAVA, WATERLOGGED = range(8)
AIRS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
# Blocks without collision that flowing water replaces (approximation of canHoldAnyFluid).
PLANTS = {"minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern", "minecraft:dead_bush",
          "minecraft:snow", "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid", "minecraft:allium",
          "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:orange_tulip", "minecraft:white_tulip",
          "minecraft:pink_tulip", "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:lily_of_the_valley",
          "minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush", "minecraft:peony", "minecraft:sweet_berry_bush",
          "minecraft:brown_mushroom", "minecraft:red_mushroom", "minecraft:vine", "minecraft:glow_lichen",
          "minecraft:pink_petals", "minecraft:wildflowers", "minecraft:leaf_litter", "minecraft:bush", "minecraft:firefly_bush",
          "minecraft:short_dry_grass", "minecraft:tall_dry_grass", "minecraft:torchflower", "minecraft:pitcher_plant"}
WATER_PLANTS = {"minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:kelp", "minecraft:kelp_plant", "minecraft:bubble_column"}
ICES = {"minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice", "minecraft:frosted_ice"}


def classify(entry):
    name = entry["Name"]
    props = entry.get("Properties", {})
    if name in AIRS:
        return AIR, 0
    if name == "minecraft:water":
        level = int(props.get("level", "0"))
        return (SOURCE, 0) if level == 0 else (FLOWING, level)
    if name == "minecraft:lava":
        return LAVA, 0
    if name in WATER_PLANTS or props.get("waterlogged") == "true":
        return WATERLOGGED, 0
    if name in ICES:
        return ICE, 0
    if name in PLANTS:
        return PLANT, 0
    return SOLID, 0


def load(path):
    with open(os.path.join(path, "worldcheck.json")) as f:
        meta = json.load(f)
    lo_x, lo_z, hi_x, hi_z = meta["chunk_range"]
    size = (hi_x - lo_x + 1) * 16
    top = LOW
    chunks = {}
    for cx in range(lo_x, hi_x + 1):
        for cz in range(lo_z, hi_z + 1):
            nbt = read_chunk(os.path.join(path, "region"), cx, cz)
            if nbt is None or nbt.get("Status") != "minecraft:full":
                raise SystemExit("chunk %d,%d is missing or not fully generated" % (cx, cz))
            chunks[(cx, cz)] = nbt
            for s in nbt["sections"]:
                if "block_states" in s and len(s["block_states"]["palette"]) > 1 or (
                        "block_states" in s and s["block_states"]["palette"][0]["Name"] not in AIRS):
                    top = max(top, s["Y"] * 16 + 15)
    height = top - LOW + 1
    kind = bytearray(size * size * height)
    level = bytearray(size * size * height)
    biomes = {}
    for (cx, cz), nbt in chunks.items():
        ox = (cx - lo_x) * 16
        oz = (cz - lo_z) * 16
        for s in nbt["sections"]:
            sy = s["Y"]
            if "block_states" not in s or sy * 16 + 15 < LOW or sy * 16 > top:
                continue
            states = s["block_states"]
            palette = [classify(e) for e in states["palette"]]
            if len(palette) == 1 or "data" not in states:
                indices = [0] * 4096
            else:
                bits = max(4, math.ceil(math.log2(len(palette))))
                indices = _unpack(states["data"], bits, 4096)
            for i, p in enumerate(indices):
                y = sy * 16 + (i >> 8)
                if y < LOW or y > top:
                    continue
                k, lv = palette[p]
                x = ox + (i & 15)
                z = oz + ((i >> 4) & 15)
                idx = ((y - LOW) * size + z) * size + x
                kind[idx] = k
                level[idx] = lv
            if "biomes" in s:
                b = section_biomes(s)
                for by in range(4):
                    for bz in range(4):
                        for bx in range(4):
                            biomes[(ox + bx * 4, sy * 16 + by * 4, oz + bz * 4)] = b[(by * 4 + bz) * 4 + bx]
    return meta, size, height, kind, level, biomes


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("world")
    p.add_argument("--expected", help="model water columns (int16 ground top, int16 water top, int8 kind per column)")
    p.add_argument("--png", help="map of the water above sea level and the unstable blocks")
    args = p.parse_args()
    meta, size, height, kind, level, biomes = load(args.world)
    expected = None
    if args.expected:
        with open(args.expected, "rb") as f:
            data = f.read()
        n = size * size
        expected = (struct.unpack("<%dh" % n, data[:2 * n]), struct.unpack("<%dh" % n, data[2 * n:4 * n]), data[4 * n:5 * n])

    def near_model_water(x, z, y):
        """True if a river or lake column of the model within 2 blocks holds water (or a fall's
        curtain) at this height; water in caves under a river bed is not the river's."""
        if expected is None:
            return True
        for dz in range(-2, 3):
            for dx in range(-2, 3):
                xx, zz = x + dx, z + dz
                if 0 <= xx < size and 0 <= zz < size:
                    c = zz * size + xx
                    if expected[2][c] in (2, 3) and expected[0][c] < y <= expected[1][c] + 64:
                        return True
        return False

    def at(x, y, z):
        if y < LOW or y >= LOW + height:
            return (AIR, 0) if y >= LOW + height else (SOLID, 0)
        idx = ((y - LOW) * size + z) * size + x
        return kind[idx], level[idx]

    def amount(k, lv):
        if k in (SOURCE, WATERLOGGED):
            return 8
        if k == FLOWING:
            return 8 if lv >= 8 else 8 - lv
        return 0

    def is_water(k):
        return k in (SOURCE, FLOWING, WATERLOGGED)

    def holds(k):
        return k in (AIR, PLANT)

    counts = Counter()
    unstable = []
    for y in range(SEA_LEVEL, LOW + height):
        for z in range(1, size - 1):
            for x in range(1, size - 1):
                k, lv = at(x, y, z)
                if k not in (SOURCE, FLOWING):
                    continue
                border = (x % 16 in (0, 15)) or (z % 16 in (0, 15))
                below, _ = at(x, y - 1, z)
                sides = [at(x + 1, y, z), at(x - 1, y, z), at(x, y, z + 1), at(x, y, z - 1)]
                if k == SOURCE:
                    counts["source"] += 1
                    if holds(below):
                        counts["source leaks down"] += 1
                        unstable.append((x, z, y, border))
                    elif any(holds(s[0]) for s in sides):
                        counts["source leaks sideways"] += 1
                        unstable.append((x, z, y, border))
                    continue
                if lv >= 8:
                    counts["falling"] += 1
                elif lv == 1:
                    counts["lip"] += 1
                else:
                    counts["other flowing"] += 1
                # getNewLiquid
                best = max([amount(*s) for s in sides if is_water(s[0])] or [0])
                sources = sum(1 for s in sides if s[0] in (SOURCE, WATERLOGGED))
                above, _ = at(x, y + 1, z)
                if sources >= 2 and (below in (SOLID, ICE) or below == SOURCE):
                    new = (SOURCE, 0)
                elif is_water(above):
                    new = (FLOWING, 8)
                else:
                    a = best - 1
                    new = (AIR, 0) if a <= 0 else (FLOWING, 8 - a)
                changes = new != (k, lv)
                if not changes and holds(below):
                    changes = True
                if not changes and not is_water(below) and not holds(below) and any(holds(s[0]) for s in sides):
                    # Not a water hole: it spreads sideways.
                    changes = True
                if changes:
                    counts["flowing would change"] += 1
                    unstable.append((x, z, y, border))
    rivers = [u for u in unstable if near_model_water(u[0], u[1], u[2])]
    inside = sum(1 for u in rivers if not u[3])
    border = len(rivers) - inside
    report = {"water_above_sea": dict(counts), "unstable": len(unstable),
              "unstable_away_from_rivers_and_lakes": len(unstable) - len(rivers),
              "unstable_at_rivers_and_lakes": len(rivers), "of_which_inside_chunks": inside, "of_which_on_chunk_borders": border,
              "unstable_examples": [[meta["block_origin"][0] + u[0], u[2], meta["block_origin"][1] + u[1]] for u in rivers[:16]]}
    # Biome of each 4x4 cell at the surface of its corner column (top of water or ground).
    river_biomes = 0
    surface_cells = 0
    for z in range(0, size, 4):
        for x in range(0, size, 4):
            for y in range(LOW + height - 1, LOW - 1, -1):
                if at(x, y, z)[0] not in (AIR, PLANT):
                    b = biomes.get((x, y & ~3, z))
                    if b is not None:
                        surface_cells += 1
                        river_biomes += b in ("minecraft:river", "minecraft:frozen_river")
                    break
    report["river_biome_surface_cells"] = "%d of %d" % (river_biomes, surface_cells)

    if expected:
        grounds, tops, kinds = expected
        mismatches = []
        differences = Counter()
        placed = top_ok = ground_ok = ground_close = ground_checked = 0
        for z in range(size):
            for x in range(size):
                c = z * size + x
                if kinds[c] not in (2, 3):
                    continue
                placed += 1
                actual_top = None
                actual_ground = None
                for y in range(LOW + height - 1, LOW - 1, -1):
                    k, _ = at(x, y, z)
                    if k in (SOURCE, WATERLOGGED, ICE) and actual_top is None:
                        actual_top = y
                    if actual_top is not None and k in (SOLID, PLANT, LAVA):
                        actual_ground = y
                        break
                    if actual_top is None and k == SOLID and y < tops[c]:
                        break
                top_ok += actual_top == tops[c]
                differences["none" if actual_top is None else str(max(-3, min(3, actual_top - tops[c])))] += 1
                if actual_top != tops[c] and len(mismatches) < 12:
                    mismatches.append([meta["block_origin"][0] + x, meta["block_origin"][1] + z, tops[c], actual_top])
                if grounds[c] >= LOW:
                    ground_checked += 1
                    ground_ok += actual_ground == grounds[c]
                    ground_close += actual_ground is not None and abs(actual_ground - grounds[c]) <= 1
        report["model_river_lake_columns"] = placed
        if placed:
            report["water_top_matches_model"] = "%.2f%%" % (100.0 * top_ok / placed)
            report["ground_matches_model"] = "%.2f%% (within 1: %.2f%%) of %d columns with ground above Y %d" % (
                100.0 * ground_ok / max(1, ground_checked), 100.0 * ground_close / max(1, ground_checked), ground_checked, LOW)
            report["water_top_mismatches (x, z, model, world)"] = mismatches
            report["water_top_world_minus_model"] = dict(differences)

    if args.png:
        import zlib
        rows = []
        mark = {(u[0], u[1]) for u in unstable}
        for z in range(size):
            row = bytearray([0])
            for x in range(size):
                color = (90, 90, 90)
                for y in range(LOW + height - 1, LOW - 1, -1):
                    k, lv = at(x, y, z)
                    if k == AIR or k == PLANT:
                        continue
                    if k == SOURCE:
                        color = (40, 90, 220) if y >= SEA_LEVEL else (20, 40, 110)
                    elif k == FLOWING:
                        color = (230, 240, 255)
                    elif k == ICE:
                        color = (180, 210, 240)
                    else:
                        shade = max(40, min(220, 40 + (y - 50)))
                        color = (shade, int(shade * 0.9), int(shade * 0.7))
                    break
                if (x, z) in mark:
                    color = (255, 0, 0)
                row.extend(color)
            rows.append(bytes(row))
        raw = b"".join(rows)

        def chunk(tag, body):
            return struct.pack(">I", len(body)) + tag + body + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)
        png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)) \
            + chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b"")
        with open(args.png, "wb") as f:
            f.write(png)
        report["png"] = args.png
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()

"""Checks the oceans of a generated world: the water column, the sea floor and polar ice fields.

Reads the region files of a world made by generate.py and reports, for open-sea columns (ground
below the sea surface):
- the sea surface (sea level - 1): water, ice, packed ice, other;
- with --cover (exported by `./gradlew terrainPreview --args="... --cover FILE"` for the same
  square): how often the surface agrees with the ice field model (open water vs ice), overall and
  more than 16 blocks away from icebergs (vanilla's iceberg feature carves melt pools around itself,
  also into neighbouring chunks after their ice field was laid);
- ice field seams: how often neighbouring surface blocks differ across chunk borders vs inside chunks;
- the water column: lava above the sea floor (vanilla's fluid picker below Y -54) and air pockets
  between the floor and the surface (dry ocean floor), which must both be 0; with --expected
  (terrain preview heights), anything but water between the model's floor (+1) and the surface;
- the deepest floor and the surface biomes.
--png writes a top-down map of the sea surface (1 pixel per block).

    python3 tools/worldcheck/oceancheck.py run/ice1 --cover run/ice1-cover.bin --png run/ice1-surface.png
"""
import argparse
import json
import math
import os
import struct
import sys
import zlib
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anvil import read_chunk, section_biomes, section_blocks  # noqa: E402

SEA_LEVEL = 63
SURFACE = SEA_LEVEL - 1
WATERY = {"minecraft:water", "minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice",
          "minecraft:kelp", "minecraft:kelp_plant", "minecraft:seagrass", "minecraft:tall_seagrass",
          "minecraft:bubble_column", "minecraft:sea_pickle"}


# What may stand on the sea surface: air, snow, icebergs (packed and blue ice are watery).
OPEN_ABOVE = {"minecraft:air", "minecraft:snow", "minecraft:snow_block"}


def is_watery(name):
    """Water, ice and what grows in water: none of it is sea floor."""
    return name in WATERY or "coral" in name


def columns_of(path):
    """Yields (x, z, column) for every column of the square, top down; x, z relative to its corner."""
    with open(os.path.join(path, "worldcheck.json")) as f:
        meta = json.load(f)
    lo_x, lo_z, hi_x, hi_z = meta["chunk_range"]
    for cx in range(lo_x, hi_x + 1):
        for cz in range(lo_z, hi_z + 1):
            nbt = read_chunk(os.path.join(path, "region"), cx, cz)
            if nbt is None or nbt.get("Status") != "minecraft:full":
                raise SystemExit("chunk %d,%d is missing or not fully generated" % (cx, cz))
            sections = {s["Y"]: s for s in nbt["sections"] if "block_states" in s}
            blocks = {y: section_blocks(s) for y, s in sections.items()}
            surface_section = SURFACE >> 4
            biomes = section_biomes(sections[surface_section]) if surface_section in sections and "biomes" in sections[surface_section] else []
            for lx in range(16):
                for lz in range(16):
                    column = []
                    for sy in sorted(blocks, reverse=True):
                        data = blocks[sy]
                        for ly in range(15, -1, -1):
                            column.append((sy * 16 + ly, data[(ly * 16 + lz) * 16 + lx]))
                    biome = biomes[(lz >> 2) * 4 + (lx >> 2)] if biomes else None
                    yield cx * 16 + lx - lo_x * 16, cz * 16 + lz - lo_z * 16, column, biome if (lx & 3) == 0 and (lz & 3) == 0 else None


def square_size(path):
    with open(os.path.join(path, "worldcheck.json")) as f:
        lo_x, lo_z, hi_x, hi_z = json.load(f)["chunk_range"]
    return (hi_x - lo_x + 1) * 16


def is_iceberg(column):
    """An iceberg (packed or blue ice, snow block) at least 3 blocks above the sea surface."""
    for y, name in column:
        if y < SEA_LEVEL + 2:
            return False
        if name in ("minecraft:packed_ice", "minecraft:blue_ice", "minecraft:snow_block"):
            return True
    return False


def analyse(column):
    """(surface block, floor Y, lava above floor, air between floor and surface) of an open-sea column, top down;
    (None, None, 0, 0) if something solid covers it (land, caves under land)."""
    surface = None
    floor = None
    lava = 0
    air = 0
    for y, name in column:
        if y > SURFACE and not (is_watery(name) or name in OPEN_ABOVE):
            # Land (or a cave under it), not open sea.
            return None, None, 0, 0
        if y == SURFACE:
            surface = name
        if y < SURFACE and floor is None:
            if is_watery(name) or name == "minecraft:lava":
                lava += name == "minecraft:lava"
                continue
            if name in ("minecraft:air", "minecraft:cave_air"):
                air += 1
                continue
            floor = y
    return surface, floor, lava, air


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("world")
    p.add_argument("--cover", help="expected sea cover classes from the terrain preview (same square)")
    p.add_argument("--png", help="top-down map of the sea surface")
    p.add_argument("--expected", help="surface heights from the terrain preview (same square): checks that the water "
                                      "column is open (only water and water plants) from the expected floor to the surface")
    args = p.parse_args()
    size = square_size(args.world)
    heights = None
    if args.expected:
        with open(args.expected, "rb") as f:
            heights = struct.unpack("<%df" % (size * size), f.read())
    surface = {}
    bergs = set()
    biomes = Counter()
    open_sea = 0
    kinds = Counter()
    lava_columns = 0
    air_columns = 0
    deepest = None
    blocked = Counter()
    blocked_columns = 0
    for x, z, column, biome in columns_of(args.world):
        if biome:
            biomes[biome] += 1
        top, floor, lava, air = analyse(column)
        surface[(x, z)] = top
        if is_iceberg(column):
            bergs.add((x, z))
        if heights is not None:
            top_block = math.ceil(heights[z * size + x]) - 1
            if top_block < SURFACE - 1:
                found = [name for y, name in column if top_block + 1 < y < SURFACE and not is_watery(name)]
                blocked.update(found)
                blocked_columns += bool(found)
        if floor is None or floor >= SURFACE - 1:
            continue
        open_sea += 1
        kinds[top] += 1
        lava_columns += lava > 0
        air_columns += air > 0
        deepest = floor if deepest is None else min(deepest, floor)
    report = {"columns": size * size, "open_sea_columns": open_sea, "sea_surface": dict(kinds.most_common()),
              "columns_with_lava_above_floor": lava_columns, "columns_with_air_in_water": air_columns,
              "deepest_floor": deepest, "surface_biomes": dict(biomes.most_common())}
    if heights is not None:
        report["columns_with_non_water_in_water_column"] = blocked_columns
        report["non_water_blocks_in_water_column"] = dict(blocked.most_common(8))

    def is_open(name):
        return name in ("minecraft:water", "minecraft:bubble_column", "minecraft:kelp", "minecraft:kelp_plant",
                        "minecraft:seagrass", "minecraft:tall_seagrass")

    if args.cover:
        with open(args.cover, "rb") as f:
            expected = f.read()
        near = set()
        for (bx, bz) in bergs:
            for dx in range(-16, 17):
                for dz in range(-16, 17):
                    near.add((bx + dx, bz + dz))
        agree = checked = agree_far = checked_far = 0
        for (x, z), top in surface.items():
            e = expected[z * size + x]
            if e == 0 or top is None or not (is_open(top) or top in ("minecraft:ice", "minecraft:packed_ice")):
                continue
            match = (e == 1) == is_open(top)
            checked += 1
            agree += match
            if (x, z) not in near:
                checked_far += 1
                agree_far += match
        report["cover_checked"] = checked
        report["cover_agreement"] = round(agree / checked, 5) if checked else None
        report["iceberg_columns"] = len(bergs)
        report["cover_checked_away_from_icebergs"] = checked_far
        report["cover_agreement_away_from_icebergs"] = round(agree_far / checked_far, 5) if checked_far else None

    border = [0, 0]
    inner = [0, 0]
    for z in range(size):
        for x in range(size - 1):
            a, b = surface.get((x, z)), surface.get((x + 1, z))
            if a is None or b is None or a not in WATERY or b not in WATERY:
                continue
            target = border if (x + 1) % 16 == 0 else inner
            target[0] += 1
            target[1] += is_open(a) != is_open(b)
    report["surface_changes_across_chunk_borders"] = round(border[1] / border[0], 5) if border[0] else None
    report["surface_changes_inside_chunks"] = round(inner[1] / inner[0], 5) if inner[0] else None
    print(json.dumps(report, indent=2))

    if args.png:
        colors = {"minecraft:water": (30, 50, 80), "minecraft:ice": (180, 210, 240), "minecraft:packed_ice": (140, 170, 220),
                  "minecraft:blue_ice": (90, 140, 230), "minecraft:snow_block": (250, 250, 250)}
        raw = bytearray()
        for z in range(size):
            raw.append(0)
            for x in range(size):
                top = surface.get((x, z))
                raw.extend(colors.get(top, (140, 140, 120) if top not in WATERY else (60, 90, 70)))

        def chunk(kind, data):
            body = kind + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

        with open(args.png, "wb") as f:
            f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
                    + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    main()

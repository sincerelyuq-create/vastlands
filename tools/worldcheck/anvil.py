"""Minimal reader for Minecraft Java region files (.mca) and NBT. Standard library only."""
import math
import os
import struct
import zlib


class _Reader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def take(self, n):
        chunk = self.data[self.pos:self.pos + n]
        self.pos += n
        return chunk

    def unpack(self, fmt):
        size = struct.calcsize(fmt)
        return struct.unpack(fmt, self.take(size))[0]


def _payload(r, tag):
    if tag == 1: return r.unpack(">b")
    if tag == 2: return r.unpack(">h")
    if tag == 3: return r.unpack(">i")
    if tag == 4: return r.unpack(">q")
    if tag == 5: return r.unpack(">f")
    if tag == 6: return r.unpack(">d")
    if tag == 7: return r.take(r.unpack(">i"))
    if tag == 8: return r.take(r.unpack(">H")).decode("utf-8", "replace")
    if tag == 9:
        item = r.unpack(">b")
        return [_payload(r, item) for _ in range(r.unpack(">i"))]
    if tag == 10:
        out = {}
        while True:
            t = r.unpack(">b")
            if t == 0:
                return out
            name = r.take(r.unpack(">H")).decode("utf-8", "replace")
            out[name] = _payload(r, t)
    if tag == 11:
        n = r.unpack(">i")
        return list(struct.unpack(">%di" % n, r.take(4 * n)))
    if tag == 12:
        n = r.unpack(">i")
        return list(struct.unpack(">%dq" % n, r.take(8 * n)))
    raise ValueError("unknown NBT tag %d" % tag)


def parse_nbt(data):
    r = _Reader(data)
    tag = r.unpack(">b")
    r.take(r.unpack(">H"))
    return _payload(r, tag)


def read_chunk(region_dir, cx, cz):
    """Returns the chunk NBT compound, or None if the chunk was never saved."""
    path = os.path.join(region_dir, "r.%d.%d.mca" % (cx >> 5, cz >> 5))
    if not os.path.exists(path):
        return None
    with open(path, "rb") as f:
        header = f.read(4096)
        index = 4 * ((cx & 31) + (cz & 31) * 32)
        offset = int.from_bytes(header[index:index + 3], "big")
        if offset == 0:
            return None
        f.seek(offset * 4096)
        length, compression = struct.unpack(">ib", f.read(5))
        raw = f.read(length - 1)
    if compression == 2:
        return parse_nbt(zlib.decompress(raw))
    if compression == 1:
        import gzip
        return parse_nbt(gzip.decompress(raw))
    raise ValueError("unsupported chunk compression %d" % compression)


def _unpack(longs, bits, count):
    per_long = 64 // bits
    mask = (1 << bits) - 1
    out = []
    for value in longs:
        value &= 0xFFFFFFFFFFFFFFFF
        for _ in range(per_long):
            out.append(value & mask)
            value >>= bits
            if len(out) == count:
                return out
    return out


def section_blocks(section):
    """4096 block names in YZX order (index = y*256 + z*16 + x)."""
    states = section["block_states"]
    palette = [entry["Name"] for entry in states["palette"]]
    if len(palette) == 1 or "data" not in states:
        return [palette[0]] * 4096
    bits = max(4, math.ceil(math.log2(len(palette))))
    return [palette[i] for i in _unpack(states["data"], bits, 4096)]


def section_biomes(section):
    """64 biome ids in YZX order of 4x4x4 cells."""
    biomes = section["biomes"]
    palette = biomes["palette"]
    if len(palette) == 1 or "data" not in biomes:
        return [palette[0]] * 64
    bits = math.ceil(math.log2(len(palette)))
    return [palette[i] for i in _unpack(biomes["data"], bits, 64)]

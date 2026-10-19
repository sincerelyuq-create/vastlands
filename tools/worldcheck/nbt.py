"""Typed NBT reader/writer (round-trips tag types), for editing level.dat. Standard library only."""
import gzip
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)
_SCALARS = {BYTE: ">b", SHORT: ">h", INT: ">i", LONG: ">q", FLOAT: ">f", DOUBLE: ">d"}


class Tag:
    """A tag type and its value. Compounds hold dict[str, Tag]; lists hold (element type, [Tag])."""

    def __init__(self, kind, value):
        self.kind = kind
        self.value = value

    def __getitem__(self, key):
        return self.value[key]

    def __setitem__(self, key, tag):
        self.value[key] = tag


def _read(data, pos, kind):
    if kind in _SCALARS:
        fmt = _SCALARS[kind]
        return Tag(kind, struct.unpack_from(fmt, data, pos)[0]), pos + struct.calcsize(fmt)
    if kind == BYTE_ARRAY:
        n = struct.unpack_from(">i", data, pos)[0]
        return Tag(kind, data[pos + 4:pos + 4 + n]), pos + 4 + n
    if kind == STRING:
        n = struct.unpack_from(">H", data, pos)[0]
        return Tag(kind, data[pos + 2:pos + 2 + n].decode("utf-8")), pos + 2 + n
    if kind == LIST:
        item, n = struct.unpack_from(">bi", data, pos)
        pos += 5
        items = []
        for _ in range(n):
            tag, pos = _read(data, pos, item)
            items.append(tag)
        return Tag(kind, (item, items)), pos
    if kind == COMPOUND:
        out = {}
        while True:
            t = data[pos]
            pos += 1
            if t == END:
                return Tag(kind, out), pos
            n = struct.unpack_from(">H", data, pos)[0]
            name = data[pos + 2:pos + 2 + n].decode("utf-8")
            out[name], pos = _read(data, pos + 2 + n, t)
    if kind in (INT_ARRAY, LONG_ARRAY):
        n = struct.unpack_from(">i", data, pos)[0]
        fmt = ">%d%s" % (n, "i" if kind == INT_ARRAY else "q")
        return Tag(kind, list(struct.unpack_from(fmt, data, pos + 4))), pos + 4 + struct.calcsize(fmt)
    raise ValueError("unknown NBT tag %d" % kind)


def _write(tag, out):
    kind, value = tag.kind, tag.value
    if kind in _SCALARS:
        out += struct.pack(_SCALARS[kind], value)
    elif kind == BYTE_ARRAY:
        out += struct.pack(">i", len(value)) + value
    elif kind == STRING:
        raw = value.encode("utf-8")
        out += struct.pack(">H", len(raw)) + raw
    elif kind == LIST:
        item, items = value
        out += struct.pack(">bi", item, len(items))
        for element in items:
            _write(element, out)
    elif kind == COMPOUND:
        for name, element in value.items():
            raw = name.encode("utf-8")
            out += struct.pack(">bH", element.kind, len(raw)) + raw
            _write(element, out)
        out.append(END)
    elif kind in (INT_ARRAY, LONG_ARRAY):
        out += struct.pack(">i%d%s" % (len(value), "i" if kind == INT_ARRAY else "q"), len(value), *value)
    else:
        raise ValueError("unknown NBT tag %d" % kind)


def load(path):
    """Reads a gzipped NBT file; returns the root compound."""
    with gzip.open(path, "rb") as f:
        data = f.read()
    n = struct.unpack_from(">H", data, 1)[0]
    root, _ = _read(data, 3 + n, data[0])
    return root


def save(path, root):
    out = bytearray([COMPOUND, 0, 0])
    _write(root, out)
    with gzip.open(path, "wb") as f:
        f.write(bytes(out))

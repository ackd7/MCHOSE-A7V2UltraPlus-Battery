"""Generate a simple battery icon as a proper multi-size .ico file (no external deps)."""
import struct, zlib, os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "Assets", "app.ico")
os.makedirs(os.path.dirname(OUT), exist_ok=True)


def make_png(size):
    """Draw a battery glyph with RGBA pixels, return PNG bytes."""
    W = H = size
    scale = size / 64.0
    px = [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]

    def rect(x0, y0, x1, y1, color, rounded=0):
        for y in range(max(0, int(y0)), min(H, int(y1))):
            for x in range(max(0, int(x0)), min(W, int(x1))):
                # rounded corners
                if rounded:
                    r = rounded
                    cx = min(max(x, x0 + r), x1 - r)
                    cy = min(max(y, y0 + r), y1 - r)
                    if (x - cx) ** 2 + (y - cy) ** 2 > r * r:
                        continue
                px[y][x] = color

    # battery body outline (cyan #4CC2FF)
    body_x0, body_y0, body_x1, body_y1 = 8*scale, 20*scale, 50*scale, 44*scale
    border = 3*scale
    cyan = (76, 194, 255, 255)
    green = (124, 231, 120, 255)
    dark = (40, 44, 52, 255)
    # outline: outer rounded rect minus inner
    outer = [[False]*W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            r = 5*scale
            cx = min(max(x, body_x0 + r), body_x1 - r)
            cy = min(max(y, body_y0 + r), body_y1 - r)
            if (cx <= x <= body_x1 and body_y0 <= y <= body_y1) and ((x-cx)**2 + (y-cy)**2 <= r*r):
                outer[y][x] = True
    inner = [[False]*W for _ in range(H)]
    ix0, iy0, ix1, iy1 = body_x0+border, body_y0+border, body_x1-border, body_y1-border
    for y in range(H):
        for x in range(W):
            r = 3*scale
            cx = min(max(x, ix0 + r), ix1 - r)
            cy = min(max(y, iy0 + r), iy1 - r)
            if (ix0 <= x <= ix1 and iy0 <= y <= iy1) and ((x-cx)**2 + (y-cy)**2 <= r*r):
                inner[y][x] = True
    for y in range(H):
        for x in range(W):
            if outer[y][x] and not inner[y][x]:
                px[y][x] = cyan
    # cap
    rect(51*scale, 27*scale, 56*scale, 37*scale, cyan, 2*scale)
    # fill 80%
    fx1 = ix0 + (ix1 - ix0) * 0.8
    rect(ix0, iy0, fx1, iy1, green, 3*scale)

    raw = b"".join(b"\x00" + b"".join(bytes(px[y][x]) for x in range(W)) for y in range(H))

    def chunk(t, d):
        c = t + d
        return struct.pack(">I", len(d)) + c + struct.pack(">I", zlib.crc32(c) & 0xffffffff)

    hdr = struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", hdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


sizes = [16, 24, 32, 48, 64, 128, 256]
pngs = [(s, make_png(s)) for s in sizes]

# ICO header
ico = struct.pack("<HHH", 0, 1, len(pngs))
offset = 6 + 16 * len(pngs)
entries = b""
data = b""
for s, png in pngs:
    w = 0 if s == 256 else s
    h = 0 if s == 256 else s
    entries += struct.pack("<BBBBHHII", w, h, 0, 0, 1, 32, len(png), offset)
    data += png
    offset += len(png)

with open(OUT, "wb") as f:
    f.write(ico + entries + data)

print(f"wrote {OUT} ({os.path.getsize(OUT)} bytes)")

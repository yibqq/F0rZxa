"""Generates src/MajestyMonitor/Assets/app.ico (a stylised cooler fan)."""
import math, sys
from PIL import Image, ImageDraw

def fan(size):
    S = 512
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # rounded square background with vertical gradient
    bg = Image.new("RGBA", (S, S))
    for y in range(S):
        t = y / S
        c = (int(18 + 10 * t), int(24 + 8 * t), int(44 + 20 * t), 255)
        ImageDraw.Draw(bg).line([(0, y), (S, y)], fill=c)
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle([8, 8, S - 8, S - 8], radius=110, fill=255)
    im.paste(bg, (0, 0), mask)
    cx = cy = S / 2
    # outer ring gradient cyan -> violet
    for i in range(360):
        a0, a1 = i, i + 1.5
        t = i / 360
        col = (int(0 + 140 * t), int(220 - 110 * t), 255, 255)
        d.arc([60, 60, S - 60, S - 60], a0 - 90, a1 - 90, fill=col, width=34)
    # blades
    for k in range(7):
        ang = k * 2 * math.pi / 7
        pts = []
        for j in range(24):
            t = j / 23
            r = 60 + 120 * t
            a = ang + 0.9 * t
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        for j in range(23, -1, -1):
            t = j / 23
            r = 60 + 120 * t
            a = ang + 0.9 * t + 0.42 * (1 - t * 0.55)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        d.polygon(pts, fill=(120, 225, 255, 235))
    d.ellipse([cx - 62, cy - 62, cx + 62, cy + 62], fill=(28, 36, 66, 255), outline=(0, 220, 255, 255), width=10)
    return im.resize((size, size), Image.LANCZOS)

out = sys.argv[1]
big = fan(256)
big.save(out, sizes=[(16, 16), (20, 20), (24, 24), (32, 32), (40, 40), (48, 48), (64, 64), (128, 128), (256, 256)])
fan(256).save(out.replace(".ico", ".png"))

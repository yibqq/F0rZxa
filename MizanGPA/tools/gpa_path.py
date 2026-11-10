import sys
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen

def gpa_path(font_file, cx, cy, cap, spacing=0.0):
    """SVG path of "GPA" with cap height `cap`, centered on (cx, cy) in a y-down viewport."""
    f = TTFont(font_file)
    gs = f.getGlyphSet(); cmap = f.getBestCmap(); hmtx = f['hmtx']
    capH = f['OS/2'].sCapHeight
    s = cap / capH
    names = [cmap[ord(c)] for c in "GPA"]
    # Lay out with advances (+ optional extra spacing in viewport units), measure ink bounds.
    xs, x = [], 0.0
    for n in names:
        xs.append(x); x += hmtx[n][0] + spacing / s
    bp = BoundsPen(gs)
    for n, ox in zip(names, xs):
        gs[n].draw(TransformPen(bp, (1, 0, 0, 1, ox, 0)))
    xmin, ymin, xmax, ymax = bp.bounds
    # Center the ink horizontally, and the cap height vertically.
    dx = cx - (xmin + xmax) / 2 * s
    dy = cy + capH / 2 * s
    pen = SVGPathPen(gs, ntos=lambda v: ("%.2f" % v).rstrip('0').rstrip('.'))
    for n, ox in zip(names, xs):
        gs[n].draw(TransformPen(pen, (s, 0, 0, -s, dx + ox * s, dy)))
    width = (xmax - xmin) * s
    return pen.getCommands(), width

if __name__ == "__main__":
    for w in ("800", "900"):
        p, width = gpa_path(f"nunito{w}.ttf", 53.6, 54, 13.2, 0.4)
        print(w, "width", round(width, 2), "len", len(p))

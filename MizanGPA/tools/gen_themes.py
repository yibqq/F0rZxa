"""
Generates every color theme with the same Material 3 recipe so they look like one family:
primary from the seed hue at a shared chroma, secondary in the same hue but muted, tertiary in a
neighbouring hue (+35 degrees) instead of a contrasting one, and near-neutral surfaces tinted
slightly towards the theme hue. Light and dark roles use the standard Material 3 tones.
"""
import re, sys
from materialyoucolor.hct import Hct
from materialyoucolor.palettes.tonal_palette import TonalPalette

res = sys.argv[1]
# name: (seed, primary chroma, tertiary hue shift)
THEMES = {
    "teal":     (0xFF0E8C86, 40, 35),
    "ocean":    (0xFF2563EB, 48, 35),
    "violet":   (0xFF7C3AED, 48, 35),
    "rose":     (0xFFE11D48, 48, 30),
    "sunset":   (0xFFEA580C, 48, 30),
    "forest":   (0xFF16A34A, 40, 35),
    "gold":     (0xFFB8860B, 40, -25),
    "graphite": (0xFF475569, 12, 35),
}
LIGHT = {
    "primary": ("p", 40), "on_primary": ("p", 100), "primary_container": ("p", 90), "on_primary_container": ("p", 10),
    "secondary": ("s", 40), "on_secondary": ("s", 100), "secondary_container": ("s", 90), "on_secondary_container": ("s", 10),
    "tertiary": ("t", 40), "on_tertiary": ("t", 100), "tertiary_container": ("t", 90), "on_tertiary_container": ("t", 10),
    "background": ("n", 98), "on_background": ("n", 10), "surface": ("n", 98), "on_surface": ("n", 10),
    "surface_variant": ("nv", 90), "on_surface_variant": ("nv", 30), "outline": ("nv", 50), "outline_variant": ("nv", 80),
    "surface_container_lowest": ("n", 100), "surface_container_low": ("n", 96), "surface_container": ("n", 94),
    "surface_container_high": ("n", 92), "surface_container_highest": ("n", 90),
    "inverse_surface": ("n", 20), "inverse_on_surface": ("n", 95), "inverse_primary": ("p", 80),
}
DARK = {
    "primary": ("p", 80), "on_primary": ("p", 20), "primary_container": ("p", 30), "on_primary_container": ("p", 90),
    "secondary": ("s", 80), "on_secondary": ("s", 20), "secondary_container": ("s", 30), "on_secondary_container": ("s", 90),
    "tertiary": ("t", 80), "on_tertiary": ("t", 20), "tertiary_container": ("t", 30), "on_tertiary_container": ("t", 90),
    "background": ("n", 6), "on_background": ("n", 90), "surface": ("n", 6), "on_surface": ("n", 90),
    "surface_variant": ("nv", 30), "on_surface_variant": ("nv", 80), "outline": ("nv", 60), "outline_variant": ("nv", 30),
    "surface_container_lowest": ("n", 4), "surface_container_low": ("n", 10), "surface_container": ("n", 12),
    "surface_container_high": ("n", 17), "surface_container_highest": ("n", 22),
    "inverse_surface": ("n", 90), "inverse_on_surface": ("n", 20), "inverse_primary": ("p", 40),
}
# Theme attribute -> role, for the overlays.
ATTRS = [("colorPrimary","primary"),("colorOnPrimary","on_primary"),("colorPrimaryContainer","primary_container"),
 ("colorOnPrimaryContainer","on_primary_container"),("colorSecondary","secondary"),("colorOnSecondary","on_secondary"),
 ("colorSecondaryContainer","secondary_container"),("colorOnSecondaryContainer","on_secondary_container"),
 ("colorTertiary","tertiary"),("colorOnTertiary","on_tertiary"),("colorTertiaryContainer","tertiary_container"),
 ("colorOnTertiaryContainer","on_tertiary_container"),("android:colorBackground","background"),("colorOnBackground","on_background"),
 ("colorSurface","surface"),("colorOnSurface","on_surface"),("colorSurfaceVariant","surface_variant"),
 ("colorOnSurfaceVariant","on_surface_variant"),("colorOutline","outline"),("colorOutlineVariant","outline_variant"),
 ("colorSurfaceContainerLowest","surface_container_lowest"),("colorSurfaceContainerLow","surface_container_low"),
 ("colorSurfaceContainer","surface_container"),("colorSurfaceContainerHigh","surface_container_high"),
 ("colorSurfaceContainerHighest","surface_container_highest"),("colorSurfaceInverse","inverse_surface"),
 ("colorOnSurfaceInverse","inverse_on_surface"),("colorPrimaryInverse","inverse_primary")]

def palettes(seed, chroma, shift):
    hue = Hct.from_int(seed).hue
    return {
        "p": TonalPalette.from_hue_and_chroma(hue, chroma),
        "s": TonalPalette.from_hue_and_chroma(hue, max(6, min(16, chroma / 3))),
        "t": TonalPalette.from_hue_and_chroma((hue + shift) % 360, max(10, chroma * 0.65)),
        "n": TonalPalette.from_hue_and_chroma(hue, min(4, chroma / 3)),
        "nv": TonalPalette.from_hue_and_chroma(hue, min(8, chroma / 2)),
    }

def hexc(argb): return "#%06X" % (argb & 0xFFFFFF)

def scheme(name, dark):
    seed, chroma, shift = THEMES[name]
    pals = palettes(seed, chroma, shift)
    roles = DARK if dark else LIGHT
    return {r: hexc(pals[p].tone(t)) for r, (p, t) in roles.items()}

# 1) Overlay palettes for every theme except the base (teal).
for dark, folder in ((False, "values"), (True, "values-night")):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- Generated: one Material 3 recipe for every theme (see tools/gen_themes.py). -->',
           '<resources>']
    for name in THEMES:
        if name == "teal": continue
        for role, value in scheme(name, dark).items():
            out.append(f'    <color name="theme_{name}_{role}">{value}</color>')
    out.append('</resources>')
    open(f"{res}/{folder}/colors_themes.xml", "w").write("\n".join(out) + "\n")

# 2) The base theme (teal) uses the md_* colors.
MD = {"primary":"md_primary","on_primary":"md_on_primary","primary_container":"md_primary_container",
      "on_primary_container":"md_on_primary_container","secondary":"md_secondary","on_secondary":"md_on_secondary",
      "secondary_container":"md_secondary_container","on_secondary_container":"md_on_secondary_container",
      "tertiary":"md_tertiary","on_tertiary":"md_on_tertiary","tertiary_container":"md_tertiary_container",
      "on_tertiary_container":"md_on_tertiary_container","surface":"md_surface","on_surface":"md_on_surface",
      "surface_variant":"md_surface_variant","on_surface_variant":"md_on_surface_variant","outline":"md_outline",
      "outline_variant":"md_outline_variant","surface_container_lowest":"md_surface_lowest",
      "surface_container_low":"md_surface_low","surface_container":"md_surface_container",
      "surface_container_high":"md_surface_high","surface_container_highest":"md_surface_highest",
      "inverse_surface":"md_inverse_surface","inverse_on_surface":"md_inverse_on_surface","inverse_primary":"md_inverse_primary"}
for dark, folder in ((False, "values"), (True, "values-night")):
    p = f"{res}/{folder}/colors.xml"
    s = open(p).read()
    sch = scheme("teal", dark)
    for role, name in MD.items():
        s, n = re.subn(rf'(<color name="{name}">)#[0-9A-Fa-f]{{6}}(</color>)', rf'\g<1>{sch[role]}\g<2>', s)
        assert n == 1, name
    open(p, "w").write(s)

# 3) Overlays.
t = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
for name in THEMES:
    if name == "teal": continue
    t.append(f'    <style name="ThemeOverlay.Mizan.{name.capitalize()}" parent="">')
    for attr, role in ATTRS:
        t.append(f'        <item name="{attr}">@color/theme_{name}_{role}</item>')
    t.append('    </style>')
t.append('''    <!-- Pure black surfaces for OLED screens; applied on top of any palette in dark mode only. -->
    <style name="ThemeOverlay.Mizan.PureBlack" parent="">
        <item name="android:colorBackground">#000000</item>
        <item name="colorSurface">#000000</item>
        <item name="colorSurfaceContainerLowest">#000000</item>
        <item name="colorSurfaceContainerLow">#0C0C0D</item>
        <item name="colorSurfaceContainer">#121213</item>
        <item name="colorSurfaceContainerHigh">#1A1A1B</item>
        <item name="colorSurfaceContainerHighest">#232324</item>
    </style>''')
t.append('</resources>')
open(f"{res}/values/themes_palettes.xml", "w").write("\n".join(t) + "\n")

for name in THEMES:
    L, D = scheme(name, False), scheme(name, True)
    print(f"{name:9} light P{L['primary']} PC{L['primary_container']} T{L['tertiary']} S{L['surface']}  | dark P{D['primary']} PC{D['primary_container']} T{D['tertiary']}")

"""Draws the Garbaggio launcher icon: an engraved, pencil-hatched rubbish bin on red.

Run from the repository root:  python3 tools/icon/make_icon.py
Writes the mipmap PNGs under app/src/main/res and a 512 px store icon under docs/.
"""
import math
import os
import random

import numpy as np

from PIL import Image, ImageChops, ImageDraw

S = 2048                 # working canvas; coordinates below are in 1024 units
K = S / 1024
RED = (229, 83, 60)
PAPER = (244, 239, 228)
INK = (20, 18, 18)
rng = random.Random(134)


def p(x, y):
    return (x * K, y * K)


def new_layer():
    return Image.new("L", (S, S), 0)


def polygon_mask(points):
    m = new_layer()
    ImageDraw.Draw(m).polygon([p(*pt) for pt in points], fill=255)
    return m


def ellipse_mask(cx, cy, rx, ry):
    m = new_layer()
    ImageDraw.Draw(m).ellipse([p(cx - rx, cy - ry), p(cx + rx, cy + ry)], fill=255)
    return m


def pencil(draw, pts, width, passes=2, jitter=1.2):
    """A slightly wobbly polyline, drawn twice like a sketched pencil line."""
    for _ in range(passes):
        wobbly = [p(x + rng.uniform(-jitter, jitter), y + rng.uniform(-jitter, jitter)) for x, y in pts]
        w = max(1, int(width * K * rng.uniform(0.8, 1.1)))
        draw.line(wobbly, fill=255, width=w, joint="curve")


def outline(draw, pts, width, closed=True):
    pts = list(pts) + ([pts[0]] if closed else [])
    pencil(draw, pts, width)


def hatch(mask, angle, spacing, width, shade=None, jitter=0.6, broken=0.0):
    """Parallel strokes at `angle` degrees, clipped to `mask` (and optional `shade`)."""
    layer = new_layer()
    d = ImageDraw.Draw(layer)
    a = math.radians(angle)
    dx, dy = math.cos(a), math.sin(a)
    nx, ny = -dy, dx
    for off in range(-760, 760, 1):
        if off % spacing:
            continue
        o = off + rng.uniform(-spacing * 0.15, spacing * 0.15)
        cx, cy = 512 + nx * o, 512 + ny * o
        start = rng.uniform(-760, -720)
        pts = []
        t = start
        while t < 760:
            pts.append((cx + dx * t + rng.uniform(-jitter, jitter), cy + dy * t + rng.uniform(-jitter, jitter)))
            t += 40
        if broken and rng.random() < broken:
            cut = rng.randint(1, len(pts) - 2)
            pts = pts[:cut]
        d.line([p(*q) for q in pts], fill=255, width=max(1, int(width * K)))
    layer = ImageChops.multiply(layer, mask)
    if shade is not None:
        layer = ImageChops.multiply(layer, shade)
    return layer


def ramp(x0, x1, y0=0, y1=1024, vertical=False):
    """Soft 0..255 mask that rises from x0 to x1 (or from y0 to y1 when vertical)."""
    lo, hi = (y0, y1) if vertical else (x0, x1)
    coords = np.arange(S) / K
    line = np.clip((coords - lo) / max(1, hi - lo), 0, 1) * 255
    grid = np.tile(line[:, None] if vertical else line[None, :], (1, S) if vertical else (S, 1))
    return Image.fromarray(grid.astype(np.uint8), "L")


def lerp(a, b, t):
    return a + (b - a) * t


# ---------------------------------------------------------------- geometry
# The can is seen from slightly above, so every horizontal edge is the front half of an
# ellipse. Lower ellipses are further below eye level and open up more (bigger sag).
BODY_TOP, BODY_BOT = 418, 742
BODY_TL, BODY_TR = 356, 668
BODY_BL, BODY_BR = 392, 632
SAG_TOP, SAG_BOT = 15, 24
LID_TOP, LID_BOT = 348, 392
RIM_TOP, RIM_BOT = 398, 432


def arc(x0, x1, y, sag, n=48):
    """Front half of an ellipse from x0 to x1, dipping `sag` below y in the middle."""
    return [(lerp(x0, x1, i / n), y + sag * math.sin(math.pi * i / n)) for i in range(n + 1)]


def sag_at(y):
    return lerp(SAG_TOP, SAG_BOT, (y - BODY_TOP) / (BODY_BOT - BODY_TOP))


body_bottom = arc(BODY_BL, BODY_BR, BODY_BOT, SAG_BOT)
body_pts = arc(BODY_TL, BODY_TR, BODY_TOP, SAG_TOP) + body_bottom[::-1]
rim_bottom = arc(348, 676, RIM_BOT, 15)
rim_pts = arc(344, 680, RIM_TOP, 14) + rim_bottom[::-1]
lid_top_back = [(x, LID_TOP - (y - LID_TOP)) for x, y in arc(368, 656, LID_TOP, 11)]
lid_bottom = arc(338, 686, LID_BOT, 13)
lid_pts = lid_top_back + lid_bottom[::-1]
lid_top_front = arc(368, 656, LID_TOP, 11)
handle_outer = [(456, 350), (456, 300), (468, 290), (556, 290), (568, 300), (568, 350)]
handle_inner = [(478, 350), (478, 310), (546, 310), (546, 350)]


def body_x(t, y):
    f = (y - BODY_TOP) / (BODY_BOT - BODY_TOP)
    return lerp(lerp(BODY_TL, BODY_BL, f), lerp(BODY_TR, BODY_BR, f), t)


paper = new_layer()
ink = new_layer()
pd = ImageDraw.Draw(paper)
idraw = ImageDraw.Draw(ink)

# ---------------------------------------------------------------- ground shadow
shadow = ellipse_mask(512, 762, 232, 30)
ink = ImageChops.lighter(ink, hatch(shadow, 0, 7, 3.2, jitter=0.3))
ink = ImageChops.lighter(ink, hatch(ellipse_mask(530, 764, 170, 18), 0, 7, 3.2, jitter=0.3))
idraw = ImageDraw.Draw(ink)

# ---------------------------------------------------------------- paper fills
for shape in (body_pts, rim_pts, handle_outer):
    pd.polygon([p(*q) for q in shape], fill=255)
pd.polygon([p(*q) for q in handle_inner], fill=0)
pd.polygon([p(*q) for q in lid_pts], fill=255)  # the lid shows through the handle's hole

lid = polygon_mask(lid_pts)
handle = ImageChops.subtract(polygon_mask(handle_outer), polygon_mask(handle_inner))
handle = ImageChops.subtract(handle, lid)
rim = ImageChops.subtract(polygon_mask(rim_pts), lid)
body = ImageChops.subtract(ImageChops.subtract(polygon_mask(body_pts), rim), lid)
lid_side = ImageChops.subtract(lid, ellipse_mask(512, LID_TOP, 144, 11))
lid_top = ImageChops.subtract(ellipse_mask(512, LID_TOP, 144, 11), polygon_mask(handle_outer))

# ---------------------------------------------------------------- shading
def darkness(t):
    """How dark the cylinder is at horizontal position t (0 = left edge, 1 = right edge)."""
    highlight = math.exp(-((t - 0.28) / 0.09) ** 2)
    return max(0.0, min(1.0, 0.25 + 0.95 * t ** 1.6 - 0.9 * highlight + 0.35 * (t < 0.08)))


def contour_hatch(x_at, y0, y1, t_min=0.0, t_max=1.0, base=7.0, width=2.4, sag=0.0):
    """Vertical strokes that follow the bin's taper, packed tighter where it is darker."""
    layer = new_layer()
    d = ImageDraw.Draw(layer)
    t = t_min
    while t < t_max:
        dark = darkness(t)
        if dark > 0.06:
            top = y0 + rng.uniform(0, 6) + (1 - dark) * rng.uniform(0, 40)
            bot = y1 - rng.uniform(0, 6) - (1 - dark) * rng.uniform(0, 30)
            pts = []
            y = top
            while y <= bot:
                pts.append(p(x_at(t, y) + rng.uniform(-0.5, 0.5), y + sag * math.sin(math.pi * t)))
                y += 18
            if len(pts) > 1:
                d.line(pts, fill=255, width=max(1, int(width * (0.6 + 0.6 * dark) * K)))
        width_here = x_at(1, (y0 + y1) / 2) - x_at(0, (y0 + y1) / 2)
        t += (base * (1.0 + 2.6 * (1 - dark))) / width_here
    return layer


def arc_hatch(x_at, y0, y1, step, t_from, width, sag=sag_at, t_to=1.0, fade=0.12):
    """Curved cross-lines wrapping around the cylinder, starting at a ragged t_from."""
    layer = new_layer()
    d = ImageDraw.Draw(layer)
    sag_fn = sag if callable(sag) else (lambda _y: sag)
    y = y0
    while y < y1:
        bend = sag_fn(y)
        start = t_from + rng.uniform(-fade, fade)
        pts = []
        t = start
        while t <= t_to:
            pts.append(p(x_at(t, y), y + bend * math.sin(math.pi * t) + rng.uniform(-0.4, 0.4)))
            t += 0.02
        if len(pts) > 1:
            d.line(pts, fill=255, width=max(1, int(width * K)))
        y += step + rng.uniform(-1.2, 1.2)
    return layer


def lid_x(t, y):
    f = (y - LID_TOP) / (LID_BOT - LID_TOP)
    return lerp(lerp(368, 338, f), lerp(656, 686, f), t)


def rim_x(t, y):
    return lerp(346, 678, t)


layers = [
    (contour_hatch(body_x, 430, 770, base=6.5, width=2.6), body),
    (arc_hatch(body_x, 440, 742, 9, 0.62, 2.0), body),
    (arc_hatch(body_x, 440, 742, 9, 0.84, 2.2, fade=0.05), body),
    (arc_hatch(body_x, 424, 474, 5.5, 0.0, 2.2, fade=0.0), body),            # shadow under the rim
    (arc_hatch(body_x, 712, 746, 6, 0.0, 2.0, fade=0.0), body),              # darker base of the can
    (contour_hatch(lambda t, y: lerp(346, 678, t), 396, 450, base=5.0, width=2.2), rim),
    (arc_hatch(lambda t, y: lerp(346, 678, t), 424, 434, 4, 0.0, 2.0, sag=15, fade=0.0), rim),
    (contour_hatch(lid_x, 344, 406, base=4.5, width=2.6), lid_side),
    (arc_hatch(lid_x, 382, 394, 4, 0.0, 2.2, sag=13, fade=0.0), lid_side),   # lid's lower lip
    (arc_hatch(lambda t, y: lerp(368, 656, t), 338, 360, 4.5, 0.62, 1.8, sag=0, fade=0.08), lid_top),
    (contour_hatch(lambda t, y: lerp(456, 568, t), 292, 350, t_min=0.55, base=5.0, width=2.0), handle),
]
for layer, mask in layers:
    ink = ImageChops.lighter(ink, ImageChops.multiply(layer, mask))
idraw = ImageDraw.Draw(ink)

# ---------------------------------------------------------------- ribs
# Evenly spaced around the can, so they bunch together toward the sides.
for k in range(8):
    t = (1 - math.cos(math.pi * (k + 0.5) / 8)) / 2
    y_top = 444 + sag_at(444) * math.sin(math.pi * t)
    y_bot = 716 + sag_at(716) * math.sin(math.pi * t)
    pencil(idraw, [(body_x(t, y_top), y_top), (body_x(t, y_bot), y_bot)], 3.6)
    steps = 20
    for i in range(steps):
        y = lerp(y_top + 4, y_bot - 8, i / steps)
        x = body_x(t, y) + 4
        pencil(idraw, [(x, y), (x + 7, y + 6)], 1.8, passes=1, jitter=0.4)

# ---------------------------------------------------------------- outlines
outline(idraw, [(BODY_TL, BODY_TOP + 20), (BODY_BL, BODY_BOT)] + body_bottom[1:] + [(BODY_TR, BODY_TOP + 20)], 9,
        closed=False)
outline(idraw, rim_pts, 7.5)
outline(idraw, lid_pts, 9)
pencil(idraw, lid_top_front, 6.0)  # front edge of the lid's top face
outline(idraw, handle_outer, 6.5, closed=False)
outline(idraw, handle_inner, 5.5, closed=False)

# ---------------------------------------------------------------- compose
grain = Image.effect_noise((S, S), 18).point(lambda v: max(0, v - 110))
ink_textured = ImageChops.subtract(ink, ImageChops.multiply(grain, ink).point(lambda v: v // 3))

color = Image.composite(Image.new("RGB", (S, S), INK), Image.new("RGB", (S, S), PAPER), ink_textured)
fg = color.convert("RGBA")
fg.putalpha(ImageChops.lighter(paper, ink))

mono = Image.new("RGBA", (S, S), (0, 0, 0, 0))
mono.putalpha(ink)


def rounded_square(size, radius_frac=0.22):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * radius_frac), fill=255)
    return m


def legacy(size):
    """Pre-Android-8 icon: red rounded square with the drawing filling most of it."""
    crop = fg.crop((int(0.19 * S), int(0.2 * S), int(0.81 * S), int(0.82 * S)))
    art = Image.new("RGBA", crop.size, RED + (255,))
    art.alpha_composite(crop)
    art = art.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(art, (0, 0), rounded_square(size))
    return out


def zoom(img, factor=1.12):
    """Enlarges the drawing inside the adaptive icon's safe zone."""
    inset = int(S * (1 - 1 / factor) / 2)
    return img.crop((inset, inset, S - inset, S - inset)).resize((S, S), Image.LANCZOS)


root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
res = os.path.join(root, "app", "src", "main", "res")
densities = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
for name, scale in densities.items():
    d = os.path.join(res, f"mipmap-{name}")
    os.makedirs(d, exist_ok=True)
    layer_px = int(108 * scale)
    zoom(fg).resize((layer_px, layer_px), Image.LANCZOS).save(os.path.join(d, "ic_launcher_foreground.png"), optimize=True)
    zoom(mono).resize((layer_px, layer_px), Image.LANCZOS).save(os.path.join(d, "ic_launcher_monochrome.png"), optimize=True)
    legacy(int(48 * scale)).save(os.path.join(d, "ic_launcher.png"), optimize=True)

docs = os.path.join(root, "docs")
os.makedirs(docs, exist_ok=True)
legacy(512).save(os.path.join(docs, "icon-512.png"), optimize=True)
print("icons written")

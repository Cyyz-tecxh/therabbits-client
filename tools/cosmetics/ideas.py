# Cosmetic idea drafts (23 designs across 7 categories). Coordinates: aura frame (head top y=-8, feet y=24, front = -z).
import math, copy
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import gen, mythics as MY   # mythics adds pscale/hair/crystal/smoke/holo/rune/portal materials
from gen import P

FONT = '/home/claude/fonts/ttf/exo2.ttf'
IMG = {}   # 'img:key' materials: per-face PIL drawers -> (front, other)

def hexc(h): return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

def _face(fn, w, h, face):
    K = 6; W, H = w * K, h * K
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0)); gl = Image.new('RGBA', (W, H), (0, 0, 0, 255))
    fn(ImageDraw.Draw(im), ImageDraw.Draw(gl), W, H, face, im, gl)
    im = np.asarray(im.resize((w, h), Image.LANCZOS)).astype(float); g = np.asarray(gl.resize((w, h), Image.LANCZOS)).astype(float); g[..., 3] = 255
    return im, g

_prev = gen.paint
def paint(mat, face, w, h):
    if mat.startswith('img:'):
        return _face(IMG[mat[4:]], w, h, face)
    return _prev(mat, face, w, h)
gen.paint = paint

def reg(name):
    def deco(fn): IMG[name] = fn; return fn
    return deco

# ---------------------------------------------------------------- textures
@reg('clockface')
def _(d, g, W, H, face, im, gl):
    d.rectangle((0, 0, W, H), fill=(52, 38, 18, 255))
    if face not in ('front', 'back'): d.rectangle((0, 0, W, H), fill=(170, 120, 40, 255)); return
    cx, cy, r = W / 2, H / 2, W * 0.46
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(236, 222, 190, 255), outline=(200, 150, 50, 255), width=int(W * 0.06))
    for k in range(12):
        a = k / 12 * 2 * math.pi
        d.line([(cx + math.cos(a) * r * 0.75, cy + math.sin(a) * r * 0.75), (cx + math.cos(a) * r * 0.88, cy + math.sin(a) * r * 0.88)], fill=(60, 40, 20, 255), width=int(W * 0.03))
    d.line([(cx, cy), (cx, cy - r * 0.6)], fill=(30, 20, 10, 255), width=int(W * 0.04)); d.line([(cx, cy), (cx + r * 0.4, cy + r * 0.1)], fill=(30, 20, 10, 255), width=int(W * 0.04))
    g.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(255, 190, 80), width=int(W * 0.04))

@reg('gear')
def _(d, g, W, H, face, im, gl):
    if face not in ('front', 'back'): d.rectangle((0, 0, W, H), fill=(150, 104, 36, 255)); return
    cx, cy = W / 2, H / 2; R = W * 0.5; r = W * 0.38
    pts = []
    for k in range(48):
        a = k / 48 * 2 * math.pi; rr = R if (k // 3) % 2 == 0 else r
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    d.polygon(pts, fill=(196, 140, 52, 255)); d.ellipse((cx - r * 0.75, cy - r * 0.75, cx + r * 0.75, cy + r * 0.75), fill=(150, 100, 34, 255))
    d.ellipse((cx - r * 0.3, cy - r * 0.3, cx + r * 0.3, cy + r * 0.3), fill=(0, 0, 0, 0))
    for k in range(4):
        a = k / 4 * 2 * math.pi + 0.4
        d.line([(cx + math.cos(a) * r * 0.3, cy + math.sin(a) * r * 0.3), (cx + math.cos(a) * r * 0.75, cy + math.sin(a) * r * 0.75)], fill=(196, 140, 52, 255), width=int(W * 0.08))

@reg('runecircle')
def _(d, g, W, H, face, im, gl):
    if face not in ('top', 'bottom'): return
    cx, cy = W / 2, H / 2; c = (110, 198, 255)
    for dd, col in ((d, c + (255,)), (g, c)):
        for rr, wd in ((0.48, 0.012), (0.42, 0.006), (0.27, 0.008)):
            dd.ellipse((cx - W * rr, cy - H * rr, cx + W * rr, cy + H * rr), outline=col, width=max(2, int(W * wd)))
        for k in range(6):
            a = k / 6 * 2 * math.pi
            pts = [(cx + math.cos(a + j * 2 * math.pi / 3) * W * 0.42, cy + math.sin(a + j * 2 * math.pi / 3) * H * 0.42) for j in range(3)]
        for off in (0, math.pi):
            pts = [(cx + math.cos(off + j * 2 * math.pi / 3 - math.pi / 2) * W * 0.42, cy + math.sin(off + j * 2 * math.pi / 3 - math.pi / 2) * H * 0.42) for j in range(3)]
            dd.polygon(pts, outline=col, width=max(2, int(W * 0.008)))
        f = ImageFont.truetype(FONT, int(W * 0.04))
        for k in range(16):
            a = k / 16 * 2 * math.pi
            x, y = cx + math.cos(a) * W * 0.452, cy + math.sin(a) * H * 0.452
            dd.text((x - W * 0.012, y - H * 0.02), 'ᚠᚢᚦᚨᚱᚲᚷᚹ'[k % 8] if False else 'ⵔⵙⵜⵟⵠⵡⵢⵣ'[k % 8] if False else '+x*o<>^~'[k % 8], font=f, fill=col)
        dd.ellipse((cx - W * 0.05, cy - H * 0.05, cx + W * 0.05, cy + H * 0.05), outline=col, width=max(2, int(W * 0.01)))

@reg('koi')
def _(d, g, W, H, face, im, gl):
    base = (255, 110, 40, 255) if face != 'bottom' else (255, 240, 230, 255)
    d.rectangle((0, 0, W, H), fill=(250, 246, 240, 255))
    for k in range(4):
        x = (k * 0.31 + 0.1) % 1 * W; y = (k * 0.47 + 0.2) % 1 * H
        d.ellipse((x - W * 0.2, y - H * 0.25, x + W * 0.25, y + H * 0.25), fill=base)
    g.rectangle((0, 0, W, H), fill=(120, 70, 50))

@reg('cloud')
def _(d, g, W, H, face, im, gl):
    a = np.linspace(0, 1, H)
    for y in range(H):
        v = int(70 - 30 * a[y]); d.line([(0, y), (W, y)], fill=(v, v, v + 8, 255))

@reg('petal')
def _(d, g, W, H, face, im, gl):
    d.ellipse((0, 0, W - 1, H - 1), fill=(255, 183, 213, 255)); d.ellipse((W * 0.3, H * 0.25, W * 0.7, H * 0.75), fill=(255, 220, 235, 255))
    g.ellipse((0, 0, W - 1, H - 1), fill=(120, 60, 90))

@reg('oni')
def _(d, g, W, H, face, im, gl):
    d.rectangle((0, 0, W, H), fill=(170, 20, 28, 255))
    if face != 'front': return
    for sx in (-1, 1):   # brows, eyes, cheeks
        d.polygon([(W / 2 + sx * W * 0.08, H * 0.28), (W / 2 + sx * W * 0.42, H * 0.18), (W / 2 + sx * W * 0.42, H * 0.26), (W / 2 + sx * W * 0.1, H * 0.34)], fill=(20, 10, 12, 255))
        d.polygon([(W / 2 + sx * W * 0.1, H * 0.38), (W / 2 + sx * W * 0.38, H * 0.33), (W / 2 + sx * W * 0.32, H * 0.46)], fill=(255, 220, 90, 255))
        g.polygon([(W / 2 + sx * W * 0.1, H * 0.38), (W / 2 + sx * W * 0.38, H * 0.33), (W / 2 + sx * W * 0.32, H * 0.46)], fill=(255, 200, 60))
    d.rectangle((W * 0.2, H * 0.66, W * 0.8, H * 0.86), fill=(20, 10, 12, 255))
    for k in range(5):
        d.rectangle((W * (0.24 + k * 0.11), H * 0.66, W * (0.3 + k * 0.11), H * 0.74), fill=(245, 240, 230, 255))
    d.polygon([(W * 0.24, H * 0.86), (W * 0.3, H * 0.98), (W * 0.36, H * 0.86)], fill=(245, 240, 230, 255))
    d.polygon([(W * 0.64, H * 0.86), (W * 0.7, H * 0.98), (W * 0.76, H * 0.86)], fill=(245, 240, 230, 255))
    d.line([(W * 0.5, H * 0.45), (W * 0.44, H * 0.58), (W * 0.56, H * 0.58)], fill=(110, 10, 16, 255), width=int(W * 0.03))

@reg('visor')
def _(d, g, W, H, face, im, gl):
    c = (110, 198, 255)
    d.rectangle((0, 0, W, H), fill=(10, 30, 50, 200))
    if face != 'front': d.rectangle((0, 0, W, H), fill=(30, 30, 36, 255)); return
    for dd, col in ((d, c + (255,)), (g, c)):
        dd.line([(W * 0.05, H * 0.5), (W * 0.95, H * 0.5)], fill=col, width=max(1, H // 14))
        for k in range(6):
            dd.rectangle((W * (0.08 + k * 0.05), H * 0.2, W * (0.1 + k * 0.05), H * (0.2 + 0.1 * (k % 3 + 1))), fill=col)
        dd.ellipse((W * 0.7, H * 0.18, W * 0.86, H * 0.82), outline=col, width=max(1, H // 14))
        dd.line([(W * 0.78, H * 0.1), (W * 0.78, H * 0.9)], fill=col, width=max(1, H // 20))

def _cape(fn):
    def draw(d, g, W, H, face, im, gl):
        if face != 'back' and face != 'front': d.rectangle((0, 0, W, H), fill=(14, 14, 16, 255)); return
        fn(d, g, W, H, im, gl)
    return draw

@reg('lava')
def _(d, g, W, H, face, im, gl):
    if face not in ('back', 'front'): d.rectangle((0, 0, W, H), fill=(20, 16, 14, 255)); return
    rng = np.random.default_rng(3)
    d.rectangle((0, 0, W, H), fill=(26, 20, 18, 255))
    for k in range(9):
        x, y = rng.random() * W, rng.random() * H * 0.2
        pts = [(x, y)]
        for j in range(10):
            x += rng.normal(0, W * 0.06); y += H * 0.1; pts.append((x, y))
        for dd, col, wd in ((d, (255, 120, 20, 255), 0.03), (d, (255, 230, 120, 255), 0.01), (g, (255, 120, 20), 0.035)):
            dd.line(pts, fill=col, width=max(1, int(W * wd)), joint='curve')

@reg('code')
def _(d, g, W, H, face, im, gl):
    if face not in ('back', 'front'): d.rectangle((0, 0, W, H), fill=(10, 12, 14, 255)); return
    d.rectangle((0, 0, W, H), fill=(8, 10, 14, 255))
    f = ImageFont.truetype(FONT, int(W * 0.08)); rng = np.random.default_rng(5)
    cols = 10
    for c in range(cols):
        head = rng.random() * H
        for j in range(16):
            y = head - j * W * 0.085
            if y < 0: y += H
            a = max(0, 1 - j / 14); col = tuple(int(v * a) for v in (110, 198, 255))
            ch = '01ABRTX7'[int(rng.random() * 8)]
            d.text((c * W / cols + W * 0.01, y), ch, font=f, fill=(255, 255, 255, 255) if j == 0 else col + (255,))
            g.text((c * W / cols + W * 0.01, y), ch, font=f, fill=col)

@reg('aurora')
def _(d, g, W, H, face, im, gl):
    if face not in ('back', 'front'): d.rectangle((0, 0, W, H), fill=(8, 10, 22, 255)); return
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    base = np.zeros((H, W, 3)); base[..., 0] = 8; base[..., 1] = 10; base[..., 2] = 26
    for k, (c, ph) in enumerate((((60, 255, 170), 0.0), ((120, 120, 255), 1.7), ((200, 90, 255), 3.1))):
        band = np.exp(-((yy / H - (0.25 + 0.2 * k) - 0.08 * np.sin(xx / W * 6 + ph)) ** 2) / 0.006)
        base += np.array(c)[None, None] * band[..., None] * 0.8
    rng = np.random.default_rng(9)
    for k in range(40):
        x, y = int(rng.random() * W), int(rng.random() * H); base[max(0, y - 1):y + 2, max(0, x - 1):x + 2] = 255
    arr = np.clip(base, 0, 255).astype(np.uint8)
    im.paste(Image.fromarray(arr).convert('RGBA')); gl.paste(Image.fromarray((arr * 0.6).astype(np.uint8)).convert('RGBA'))

@reg('mirror')
def _(d, g, W, H, face, im, gl):
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    arr = np.zeros((H, W, 3)); arr[..., 0] = 150 + 60 * (xx / W); arr[..., 1] = 170 + 50 * (yy / H); arr[..., 2] = 210 + 40 * (1 - xx / W)
    im.paste(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).convert('RGBA'))
    if face not in ('front',): return
    cx, cy = W * 0.62, H * 0.38; rng = np.random.default_rng(2)
    for k in range(9):
        a = k / 9 * 2 * math.pi + rng.random() * 0.4
        d.line([(cx, cy), (cx + math.cos(a) * W, cy + math.sin(a) * W)], fill=(255, 255, 255, 255), width=max(2, W // 80))
        g.line([(cx, cy), (cx + math.cos(a) * W, cy + math.sin(a) * W)], fill=(200, 230, 255), width=max(2, W // 100))
    for k in range(3):
        d.ellipse((cx - W * 0.08 * (k + 1), cy - W * 0.08 * (k + 1), cx + W * 0.08 * (k + 1), cy + W * 0.08 * (k + 1)), outline=(255, 255, 255, 255), width=max(1, W // 120))
    d.rectangle((0, 0, W - 1, H - 1), outline=(60, 64, 80, 255), width=int(W * 0.06))

@reg('footprint')
def _(d, g, W, H, face, im, gl):
    if face != 'top': return
    c = (110, 198, 255)
    for dd, col in ((d, c + (255,)), (g, c)):
        dd.ellipse((W * 0.25, H * 0.35, W * 0.75, H * 0.95), outline=col, width=max(2, W // 14))
        for k in range(4):
            x = W * (0.22 + k * 0.18); dd.ellipse((x - W * 0.07, H * 0.05, x + W * 0.07, H * 0.25), fill=col)

@reg('card')
def _(d, g, W, H, face, im, gl):
    d.rectangle((0, 0, W, H), fill=(10, 8, 12, 255)); c = (110, 198, 255)
    d.rectangle((0, 0, W - 1, H - 1), outline=c + (255,), width=max(2, W // 10)); g.rectangle((0, 0, W - 1, H - 1), outline=c, width=max(2, W // 10))
    cx, cy, r = W / 2, H / 2, W * 0.22
    d.polygon([(cx, cy - r * 1.2), (cx + r * 0.8, cy), (cx, cy + r * 1.2), (cx - r * 0.8, cy)], fill=c + (255,)); g.polygon([(cx, cy - r * 1.2), (cx + r * 0.8, cy), (cx, cy + r * 1.2), (cx - r * 0.8, cy)], fill=c)

# ---------------------------------------------------------------- models
M = {}
def add(key, name, cat, desc, parts): M[key] = dict(name=name, cat=cat, desc=desc, parts=parts)
def ring(prefix, n, R, size, mat, y=0, parent=None, pivot=(0, 0, 0)):
    out = []
    for i in range(n):
        a = i / n * 2 * math.pi
        out.append(P('%s%d' % (prefix, i), (-size[0] / 2, -size[1] / 2, -size[2] / 2), size, mat,
                     pivot=(pivot[0] + math.cos(a) * R, pivot[1] + y, pivot[2] + math.sin(a) * R), rot=(0, -math.degrees(a) - 90, 0), parent=parent))
    return out

# AURAS
ps = []
for i, (a, y, sz) in enumerate(((0.3, 2, 5), (2.2, 10, 3.6), (4.1, 5, 4.2), (5.4, 15, 3.0))):
    ps.append(P('g%d' % i, (-sz / 2, -sz / 2, -0.3), (sz, sz, 0.6), 'img:gear', pivot=(math.cos(a) * 12, y, math.sin(a) * 12), rot=(0, -math.degrees(a) + 90, 30 * i)))
ps.append(P('clock', (-3.2, -3.2, -0.4), (6.4, 6.4, 0.8), 'img:clockface', pivot=(-9, -2, -7), rot=(0, 35, 0)))
ps += ring('ch', 22, 13.5, (1.6, 0.3, 0.3), 'gold', y=8)
add('clockwork', 'Clockwork Orbit', 'AURAS', 'Brass gears + clock orbit · tick faster when you sprint', ps)

ps = [P('rc', (-14, -0.05, -14), (28, 0.1, 28), 'img:runecircle', pivot=(0, 23.8, 0))]
for i in range(10):
    a = i * 2.39; y = 22 - (i * 2.3) % 18
    ps.append(P('rr%d' % i, (-0.7, -0.7, -0.02), (1.4, 1.4, 0.04), 'rune:6EC6FF', pivot=(math.cos(a) * 9, y, math.sin(a) * 9), rot=(0, -math.degrees(a) + 90, 0)))
ps += ring('rp', 24, 12.5, (3.2, 0.25, 0.25), 'neon:6EC6FF', y=23.5)
add('rune_circle', 'Rune Circle', 'AURAS', 'Glowing magic circle at your feet · runes rise off it', ps)

ps = []
for f, (ph, col) in enumerate(((0.0, 'pscale:FF7A2A:B03A10:FFE0B0'), (math.pi, 'pscale:FFFFFF:C8C8C8:E0203C'))):
    for i in range(8):
        s = ph + 0.9 - i * 0.15
        x, z, y = math.sin(s) * 14, math.sin(2 * s) * 10, 6 + 3 * math.cos(s)
        x2, z2 = math.sin(s + 0.05) * 14, math.sin(2 * (s + 0.05)) * 10
        w = [3.0, 3.5, 3.3, 2.8, 2.3, 1.7, 1.2, 0.8][i]
        rot = MY.look((x, y, z), (x2, y, z2))
        ps.append(P('k%d_%d' % (f, i), (-w / 2, -w * 0.45, -1.1), (w, w * 0.9, 2.2), col, pivot=(x, y, z), rot=rot))
    ps.append(P('k%d_tail' % f, (-0.1, -1.4, 0), (0.2, 2.8, 2.6), 'neon:FFE3C2', pivot=(0, 0, 1.0), parent='k%d_7' % f))
    ps.append(P('k%d_finl' % f, (-2.2, -0.1, -0.6), (2.2, 0.2, 1.4), 'neon:FFE3C2', pivot=(-0.9, 0.6, 0), rot=(0, 0, 25), parent='k%d_1' % f))
    ps.append(P('k%d_finr' % f, (0, -0.1, -0.6), (2.2, 0.2, 1.4), 'neon:FFE3C2', pivot=(0.9, 0.6, 0), rot=(0, 0, -25), parent='k%d_1' % f))
    for sx in (-1, 1): ps.append(P('k%d_eye%d' % (f, sx), (-0.12, -0.2, -0.2), (0.24, 0.4, 0.4), 'neon:111111', pivot=(sx * 1.05, -0.3, -0.7), parent='k%d_0' % f))
for i in range(6):
    a = i * 1.1
    ps.append(P('bub%d' % i, (-0.3, -0.3, -0.3), (0.6, 0.6, 0.6), 'neon:BFEFFF', pivot=(math.cos(a) * 10, 2 + i * 2.5, math.sin(a) * 10)))
add('koi', 'Koi Spirits', 'AURAS', 'Two ghost koi swim figure-eights around you', ps)

ps = []
for i, (x, z, r) in enumerate(((0, 0, 3.4), (-3, 0.8, 2.6), (3, -0.5, 2.8), (-1.4, -2, 2.3), (1.6, 2.2, 2.4), (-4.6, -0.6, 1.8), (4.8, 1, 1.7))):
    ps.append(P('cl%d' % i, (-r, -r * 0.7, -r), (2 * r, 1.4 * r, 2 * r), 'img:cloud', pivot=(x, -13.5 + (i % 2) * 0.6, z)))
for i in range(14):
    x, z = math.sin(i * 2.7) * 4.5, math.cos(i * 1.9) * 3
    ps.append(P('rn%d' % i, (-0.06, 0, -0.06), (0.12, 1.4, 0.12), 'neon:9FD8FF', pivot=(x, -10.5 + (i * 0.9) % 4, z)))
bolt = [(0, -10.5), (-0.8, -8.5), (0.4, -8.0), (-0.6, -5.6)]
for i in range(3):
    (x0, y0), (x1, y1) = bolt[i], bolt[i + 1]; L = math.hypot(x1 - x0, y1 - y0)
    ps.append(P('bolt%d' % i, (-0.15, 0, -0.15), (0.3, L, 0.3), 'neon:FFFFFF', pivot=(x0 + 2.5, y0, 1), rot=(0, 0, -math.degrees(math.atan2(x1 - x0, y1 - y0)))))
add('storm_cloud', 'Storm Cloud', 'AURAS', 'Tiny thundercloud over your head · rain + lightning', ps)

ps = []
rng = np.random.default_rng(4)
for i in range(36):
    a = rng.random() * 2 * math.pi; r = 7 + rng.random() * 7; y = -6 + rng.random() * 28
    ps.append(P('pt%d' % i, (-0.6, -0.45, -0.05), (1.2, 0.9, 0.1), 'img:petal', pivot=(math.cos(a) * r, y, math.sin(a) * r), rot=(rng.random() * 180, rng.random() * 180, rng.random() * 180)))
add('petal_storm', 'Petal Storm', 'AURAS', 'Sakura petals swirl around you · burst when you jump', ps)

# WINGS
ps = []
for sx in (-1, 1):
    base = 'hw%d' % sx
    ps.append(P(base, (-0.2, -0.2, -0.2), (0.4, 0.4, 0.4), 'neon:6EC6FF', pivot=(sx * 1.5, 2.5, 2.4), rot=(0, sx * -25, 0)))
    for j, (L, ang) in enumerate(((14, -30), (16, -10), (15, 10), (12, 30), (9, 50))):
        ps.append(P('%s_b%d' % (base, j), (0 if sx > 0 else -L, -0.12, -0.12), (L, 0.24, 0.24), 'neon:6EC6FF', rot=(0, 0, -sx * ang), parent=base))
        ps.append(P('%s_t%d' % (base, j), (-0.25, -0.25, -0.25), (0.5, 0.5, 0.5), 'neon:FFFFFF', pivot=(sx * L, 0, 0), parent='%s_b%d' % (base, j)))
    ps.append(P('%s_m' % base, (0 if sx > 0 else -13, -0.08, -0.08), (13, 0.16, 0.16), 'neon:BFE6FF', pivot=(0, -4.6, 0), rot=(0, 0, -sx * 10), parent=base))
    ps.append(P('%s_pane' % base, (0 if sx > 0 else -13, -6, -0.02), (13, 6, 0.04), 'holo:6EC6FF', rot=(0, 0, -sx * 12), parent=base))
add('hardlight_wings', 'Hard-Light Wings', 'WINGS', 'Wireframe wings that build segment by segment', ps)

ps = []
for sx in (-1, 1):
    n = 'th%d' % sx
    ps += [P(n, (-1.4, -3.5, -1.2), (2.8, 7, 2.4), 'metal:3A3E46', pivot=(sx * 2.4, 4, 3.4)),
           P(n + 'c', (-1.6, -3.7, -1.4), (3.2, 1, 2.8), 'metal:9AA0AA', parent=n),
           P(n + 'r', (-1.65, 0.5, -1.45), (3.3, 0.35, 2.9), 'neon:6EC6FF', parent=n),
           P(n + 'n', (-1.1, 3.5, -0.9), (2.2, 1.2, 1.8), 'metal:22252A', parent=n),
           P(n + 'f', (-0.8, 4.7, -0.6), (1.6, 2.6, 1.2), 'neon:BFE6FF', parent=n),
           P(n + 'f2', (-0.5, 7.3, -0.4), (1.0, 1.6, 0.8), 'neon:6EC6FF', parent=n)]
    for k in range(3): ps.append(P(n + 'p%d' % k, (-0.6, -0.6, -0.6), (1.2, 1.2, 1.2), 'smoke:9AA0AA', pivot=(sx * 2.4 + sx * k * 0.6, 14 + k * 2.2, 4 + k), rot=(0, k * 30, k * 20)))
ps.append(P('strap', (-4.2, 0.4, -2.3), (8.4, 1.2, 4.6), 'metal:22252A'))
add('mecha_thrusters', 'Mecha Thrusters', 'WINGS', 'Jet packs that ignite when you sprint or jump', ps)

ps = []; rng = np.random.default_rng(8)
for sx in (-1, 1):
    for j in range(46):
        u = rng.random(); v = rng.random()
        x = sx * (1.5 + u * 11); y = 3 - u * 6 + v * (10 - u * 6); z = 2.6 + u * 2.5
        mat = ['neon:FF8A1A', 'neon:FFB040', 'neon:FF4A1A', 'neon:FFE070'][j % 4]
        s = 0.4 + (1 - u) * 0.5
        ps.append(P('em%d_%d' % (sx, j), (-s / 2, -s / 2, -s / 2), (s, s, s), mat, pivot=(x, y, z), rot=(j * 17, j * 31, j * 7)))
add('ember_wings', 'Ember Wings', 'WINGS', 'Wings made of floating embers that drift and re-form', ps)

# HATS
ps = []
for i in range(18):
    a = i / 18 * 2 * math.pi
    ps.append(P('hr%d' % i, (-0.5, -0.12, -0.12), (1.0, 0.24, 0.24), 'neon:FFFFFF' if i % 3 == 0 else 'gold', pivot=(math.cos(a) * 3.6, -11.5, math.sin(a) * 3.6), rot=(0, -math.degrees(a) - 90, 0)))
for i in range(6):
    a = i / 6 * 2 * math.pi
    ps.append(P('hs%d' % i, (-0.5, -0.5, -0.02), (1.0, 1.0, 0.04), 'rune:FFD24A', pivot=(math.cos(a) * 3.6, -12.2, math.sin(a) * 3.6), rot=(0, -math.degrees(a) + 90, 0)))
add('halo_ring', 'Halo Ring', 'HATS', 'Spinning ring of gold runes above your head', ps)

ps = [P('oni', (-3.6, -4, -0.5), (7.2, 8, 1.0), 'img:oni', pivot=(-3.6, -4.2, -1.5), rot=(0, 60, 8))]
for sx in (-1, 1): ps.append(P('onih%d' % sx, (-0.4, -2.4, -0.4), (0.8, 2.4, 0.8), 'fab_cream', pivot=(sx * 2.2, -3.4, -0.4), rot=(0, 0, sx * 15), parent='oni'))
ps.append(P('band', (-4.3, -6, -4.3), (8.6, 0.8, 8.6), 'metal:1A1A1E'))
add('oni_mask', 'Oni Mask', 'HATS', 'Demon mask worn on the side of the head · glowing eyes', ps)

ps = [P('kb', (-4.6, -9.0, -4.6), (9.2, 4.2, 9.2), 'metal:16161A'), P('kbt', (-3.8, -9.8, -3.8), (7.6, 0.9, 7.6), 'metal:22222A'),
      P('kbn', (-5.6, -5.0, -5.4), (11.2, 0.6, 10.6), 'metal:16161A'), P('kbg', (-5.7, -5.3, -5.5), (11.4, 0.3, 10.8), 'gold')]
for sx in (-1, 1):
    ps.append(P('kf%d' % sx, (-0.35, -6.5, -0.15), (0.7, 6.5, 0.3), 'gold', pivot=(sx * 1.0, -8.2, -4.7), rot=(0, 0, sx * 32)))
    ps.append(P('ks%d' % sx, (-0.3, -2.8, -2.4), (0.6, 2.8, 4.8), 'metal:16161A', pivot=(sx * 5.0, -5.0, 0.4), rot=(0, 0, sx * -20)))
ps.append(P('kcrest', (-0.7, -0.7, -0.15), (1.4, 1.4, 0.3), 'neon:E0203C', pivot=(0, -9.0, -4.8), rot=(0, 0, 45)))
add('kabuto', 'Samurai Kabuto', 'HATS', 'Black lacquer helmet · gold crest · red jewel', ps)

ps = [P('vz', (-4.4, -6.2, -4.7), (8.8, 2.2, 0.5), 'img:visor'), P('vzs', (-4.6, -6.4, -4.6), (9.2, 2.6, 9.2), 'metal:22252A'),
      P('vzl', (-4.75, -5.5, -1.6), (0.4, 1.1, 1.1), 'neon:6EC6FF'), P('vzr', (4.35, -5.5, -1.6), (0.4, 1.1, 1.1), 'neon:6EC6FF')]
add('neon_visor', 'Neon Visor', 'HATS', 'Holographic visor with a flickering HUD', ps)

# CAPES (shown on the back)
for key, mat, name, desc in (('lava_cape', 'img:lava', 'Lava Flow', 'Molten cracks pulse across black rock'),
                             ('code_cape', 'img:code', 'Digital Rain', 'Falling code in your accent colour'),
                             ('aurora_cape', 'img:aurora', 'Aurora', 'Slow northern-lights gradient + stars')):
    add(key, name, 'CAPES', desc, [P('cape', (-5, 0, 0), (10, 16, 1), mat, pivot=(0, 0.1, 2.15), rot=(10, 0, 0))])

# PETS
ps = []
d0 = MY.PREMIUM['eternal_dragon']
mini = MY.build_premium(d0)
keep = [p for p in mini if p['name'].startswith('d_') and not p['name'].startswith(('d_p', 'd_k', 'd_orb', 'd_halo'))]
mm = MY.MODELS['eternal_dragon']
add('mini_dragon', 'Mini Dragon', 'PETS', 'Pocket Eternal Dragon that flies over your shoulder', keep)
M['mini_dragon']['mini'] = True

ps = [P('bb', (-2.4, -2.2, -3.0), (4.8, 4.2, 6.0), 'neon:E8F4FF', pivot=(9, 20.0, 1)),
      P('bh', (-1.9, -3.8, -1.8), (3.8, 3.6, 3.6), 'neon:F4FAFF', pivot=(0, -1.0, -2.6), parent='bb'),
      P('bt', (-0.9, -0.9, 0), (1.8, 1.8, 1.4), 'neon:FFFFFF', pivot=(0, -1.0, 3.0), parent='bb')]
for sx in (-1, 1):
    ps.append(P('be%d' % sx, (-0.5, -4.4, -0.3), (1.0, 4.4, 0.6), 'neon:F4FAFF', pivot=(sx * 0.9, -3.6, 0.2), rot=(-15, 0, sx * 10), parent='bh'))
    ps.append(P('bei%d' % sx, (-0.25, -3.6, -0.32), (0.5, 3.6, 0.1), 'neon:6EC6FF', pivot=(0, 0, 0), parent='be%d' % sx))
    ps.append(P('beye%d' % sx, (-0.25, -0.35, -0.1), (0.5, 0.7, 0.2), 'neon:1A6AA8', pivot=(sx * 0.9, -2.4, -1.85), parent='bh'))
for i in range(5):
    ps.append(P('bsp%d' % i, (-0.15, -0.15, -0.15), (0.3, 0.3, 0.3), 'neon:6EC6FF', pivot=(9 + math.sin(i) * 3.5, 15 + i * 0.8, 3 + math.cos(i * 2) * 3)))
add('spirit_bunny', 'Spirit Bunny', 'PETS', 'Glowing rabbit that hops after you (TheRabbits!)', ps)

ps = [P('dr', (-1.6, -1.0, -1.6), (3.2, 2.0, 3.2), 'metal:2A2E36', pivot=(-8, -10, 2)),
      P('drl', (-1.0, 0.9, -1.0), (2.0, 0.4, 2.0), 'neon:6EC6FF', parent='dr'),
      P('dre', (-0.5, -0.4, -1.75), (1.0, 0.8, 0.2), 'neon:FFFFFF', parent='dr'),
      P('beam', (-0.15, 1.3, -0.15), (0.3, 8, 0.3), 'neon:6EC6FF', rot=(-20, 0, 0), parent='dr')]
for k in range(4):
    a = k / 4 * 2 * math.pi + math.pi / 4
    ps.append(P('arm%d' % k, (-0.2, -0.2, 0), (0.4, 0.4, 2.0), 'metal:5A606A', rot=(0, -math.degrees(a) - 90, 0), parent='dr'))
    ps.append(P('rot%d' % k, (-1.3, -0.08, -0.25), (2.6, 0.16, 0.5), 'metal:9AA0AA', pivot=(math.cos(a) * 2.4, -0.6, math.sin(a) * 2.4), rot=(0, k * 40, 0), parent='dr'))
add('drone', 'Drone Buddy', 'PETS', 'Hovering scout drone with a scanning light', ps)

# TRAILS
ps = []
for i in range(5):
    sx = 1 if i % 2 else -1
    ps.append(P('fp%d' % i, (-1.3, -0.04, -2.2), (2.6, 0.08, 4.4), 'img:footprint', pivot=(sx * 2.0, 23.9, 4 + i * 4.2)))
add('footsteps', 'Footstep Glyphs', 'TRAILS', 'Every step leaves a glowing print that fades', ps)
M['footsteps']['fade'] = 'fp'

ps = []
for i in range(14):
    y = -6 + i * 2.1; L = 6 + (i * 5 % 9)
    ps.append(P('sl%d' % i, (-0.1, -0.1, 0), (0.2, 0.2, L), 'neon:FFFFFF' if i % 3 == 0 else 'neon:6EC6FF', pivot=(math.sin(i * 2.3) * 5.5, y, 3.5 + (i % 4))))
add('speed_lines', 'Speed Lines', 'TRAILS', 'Anime streaks behind you when you sprint', ps)

ps = []
for i in range(7):
    ps.append(P('ct%d' % i, (-1.5, -2.1, -0.08), (3.0, 4.2, 0.16), 'img:card', pivot=(math.sin(i * 1.7) * 4, 6 + i * 2.6, 5 + i * 3.2), rot=(30 * i, 45 + 50 * i, 20 * i)))
add('card_trail', 'Card Trail', 'TRAILS', 'Cards flutter down behind you (pairs with Card Master)', ps)

# SHIELDS (held in the right hand)
for key, mat, rim, name, desc in (('dragon_shield', 'pscale:22D07F:04261B:E9B949', 'gold', 'Dragon Scale', 'Eternal Dragon scales · gold rim'),
                                  ('mirror_shield', 'img:mirror', 'metal:3A3E4A', 'Mirror Shard', 'Cracked mirror that catches the light')):
    ps = [P('sh', (-5, -7, -0.6), (10, 14, 1.2), mat, pivot=(-7.5, 9, -4.5), rot=(0, -25, 0))]
    for n, frm, size in (('t', (-5.4, -7.4, -0.8), (10.8, 0.8, 1.6)), ('b', (-5.4, 6.6, -0.8), (10.8, 0.8, 1.6)),
                         ('l', (-5.4, -6.6, -0.8), (0.8, 13.2, 1.6)), ('r', (4.6, -6.6, -0.8), (0.8, 13.2, 1.6))):
        ps.append(P('rim' + n, frm, size, rim, parent='sh'))
    if key == 'dragon_shield':
        ps.append(P('boss', (-1.4, -1.4, -0.3), (2.8, 2.8, 0.6), 'gold', pivot=(0, 0, -0.8), rot=(0, 0, 45), parent='sh'))
        ps.append(P('bosse', (-0.6, -0.6, -0.3), (1.2, 1.2, 0.6), 'neon:FFFFFF', pivot=(0, 0, -1.1), rot=(0, 0, 45), parent='sh'))
    add(key, name, 'SHIELDS', desc, ps)

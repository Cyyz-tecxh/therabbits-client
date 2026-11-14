# TheRabbits cosmetics generator: models (MC pixel units, part-local), HD textures, atlas + JSON export.
import json, math, os
import numpy as np
from PIL import Image, ImageFilter

S = 8  # texels per model unit (atlas is declared in units, PNG is S times bigger)
rng = np.random.default_rng(7)

# ---------------------------------------------------------------- materials
def noise(h, w, sx=1.0, sy=1.0, octaves=3):
    out = np.zeros((h, w))
    amp = 1.0
    for o in range(octaves):
        gh, gw = max(2, int(h / (S * 2 * sy) * 2 ** o) + 2), max(2, int(w / (S * 2 * sx) * 2 ** o) + 2)
        g = rng.random((gh, gw))
        im = Image.fromarray((g * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        out += (np.asarray(im) / 255.0 - 0.5) * amp
        amp *= 0.5
    return out

def fine(h, w, k=1.0):
    return (rng.random((h, w)) - 0.5) * k

def bevel(img, face, strength=0.18, width=None):
    h, w, _ = img.shape
    bw = width or max(2, S // 3)
    yy, xx = np.mgrid[0:h, 0:w]
    m = np.zeros((h, w))
    m += np.where(yy < bw, strength, 0)          # lit top edge
    m -= np.where(yy >= h - bw, strength, 0)     # shaded bottom edge
    m += np.where(xx < bw, strength * 0.4, 0)
    m -= np.where(xx >= w - bw, strength * 0.6, 0)
    img[..., :3] = np.clip(img[..., :3] * (1 + m[..., None]), 0, 255)
    return img

def vgrad(h, w, top, bottom):
    t = np.linspace(0, 1, h)[:, None]
    return top + (bottom - top) * t * np.ones((1, w))

def paint(mat, face, w, h):
    """returns (rgba, glow_rgba_or_None) arrays of shape (h,w,4)"""
    img = np.zeros((h, w, 4)); img[..., 3] = 255
    glow = None
    def base(c):
        img[..., 0], img[..., 1], img[..., 2] = c
    if mat in ('felt', 'felt_dark'):
        c = np.array((48, 47, 52) if mat == 'felt' else (34, 33, 37), float)
        n = noise(h, w, 0.6, 0.6) * 0.18 + fine(h, w, 0.12)
        g = vgrad(h, w, 1.08, 0.86) if face not in ('top', 'bottom') else 1.0
        img[..., :3] = np.clip(c[None, None] * (1 + n)[..., None] * (g if np.ndim(g) == 0 else g[..., None]), 0, 255)
        bevel(img, face, 0.12)
    elif mat == 'satin':
        c = np.array((150, 18, 26), float)
        xx = np.linspace(0, 1, w)[None, :]
        yy = np.linspace(0, 1, h)[:, None]
        sheen = 0.75 + 0.5 * np.exp(-((yy - 0.35) ** 2) / 0.02) + 0.08 * np.sin(xx * 40)
        img[..., :3] = np.clip(c[None, None] * sheen[..., None] * (1 + fine(h, w, 0.05))[..., None], 0, 255)
        bevel(img, face, 0.2)
    elif mat in ('obsidian', 'gunmetal'):
        c = np.array((26, 24, 30) if mat == 'obsidian' else (62, 64, 70), float)
        brushed = noise(h, w, 0.15, 2.5, 4) * 0.35 + fine(h, w, 0.08)
        spec = 0.35 * np.exp(-((np.linspace(0, 1, h)[:, None] - 0.25) ** 2) / 0.015) * np.ones((1, w))
        img[..., :3] = np.clip(c[None, None] * (1 + brushed)[..., None] + spec[..., None] * 120, 0, 255)
        bevel(img, face, 0.45, max(2, S // 2))
    elif mat == 'silver':
        c = np.array((178, 182, 190), float)
        t = np.linspace(0, 1, h)[:, None] * np.ones((1, w))
        band = 0.75 + 0.45 * np.exp(-((t - 0.3) ** 2) / 0.02) - 0.25 * t
        img[..., :3] = np.clip(c[None, None] * (band + noise(h, w, 0.2, 2, 2) * 0.12)[..., None], 0, 255)
        bevel(img, face, 0.3)
    elif mat == 'blade':
        c = np.array((205, 210, 218), float)
        xx = np.linspace(-1, 1, w)[None, :] * np.ones((h, 1))
        edge = 1.15 - 0.45 * np.abs(xx)
        t = np.linspace(0, 1, h)[:, None]
        img[..., :3] = np.clip(c[None, None] * (edge * (1.05 - 0.2 * t) + noise(h, w, 0.1, 3, 2) * 0.08)[..., None], 0, 255)
        bevel(img, face, 0.25, max(1, S // 4))
    elif mat == 'leather':
        c = np.array((30, 28, 29), float)
        n = noise(h, w, 0.25, 0.25, 4) * 0.3 + fine(h, w, 0.15)
        img[..., :3] = np.clip(c[None, None] * (1 + n)[..., None], 0, 255)
        if face not in ('top', 'bottom') and h > 2 * S:   # stitching near top/bottom edges
            for y in (int(S * 0.6), h - int(S * 0.6) - 1):
                for x in range(1, w - 1, max(3, S // 2)):
                    img[y:y + 1, x:x + max(1, S // 4), :3] = (120, 118, 112)
        bevel(img, face, 0.25)
    elif mat in ('fur', 'fur_tip'):
        c = np.array((238, 236, 232) if mat == 'fur' else (250, 250, 250), float)
        strands = noise(h, w, 0.05, 1.6, 4) * 0.22 + fine(h, w, 0.08)
        g = vgrad(h, w, 1.0, 0.9)
        img[..., :3] = np.clip(c[None, None] * (1 + strands)[..., None] * g[..., None], 0, 255)
        bevel(img, face, 0.08)
    elif mat == 'pink':
        c = np.array((236, 160, 178), float)
        xx = np.linspace(-1, 1, w)[None, :]
        cen = 1.05 - 0.25 * xx ** 2
        img[..., :3] = np.clip(c[None, None] * (cen * vgrad(h, w, 0.92, 1.05) + fine(h, w, 0.05))[..., None], 0, 255)
    elif mat in ('gem', 'glow'):
        if mat == 'gem':
            yy, xx = np.mgrid[0:h, 0:w]
            cx, cy = (w - 1) / 2, (h - 1) / 2
            r = np.sqrt(((xx - cx) / max(1, w / 2)) ** 2 + ((yy - cy) / max(1, h / 2)) ** 2)
            facet = (np.abs(xx - cx) / max(1, w / 2) + np.abs(yy - cy) / max(1, h / 2)) < 0.9
            v = np.clip(1.25 - r * 0.8, 0.35, 1.3) * np.where(facet, 1.0, 0.7)
            img[..., 0] = np.clip(255 * v, 0, 255); img[..., 1] = np.clip(40 * v * v, 0, 255); img[..., 2] = np.clip(52 * v * v, 0, 255)
            hl = (np.abs(xx - cx * 0.6) < max(1, w * 0.12)) & (np.abs(yy - cy * 0.6) < max(1, h * 0.12))
            img[hl, :3] = (255, 215, 215)
        else:
            base((235, 30, 42))
            img[..., :3] *= vgrad(h, w, 1.1, 0.8)[..., None]
        glow = img.copy()
    elif mat in ('onyx', 'pearl'):
        c = np.array((20, 20, 23) if mat == 'onyx' else (244, 244, 248), float)
        t = np.linspace(0, 1, h)[:, None] * np.ones((1, w))
        shade = (1.0 + noise(h, w, 0.5, 0.5, 2) * 0.08) if mat == 'onyx' else (1.03 - 0.08 * t + noise(h, w, 0.5, 0.5, 2) * 0.03)
        img[..., :3] = np.clip(c[None, None] * shade[..., None], 0, 255)
        if mat == 'onyx':  # thin glossy highlight line just under the top edge
            r0 = max(1, S // 8); r1 = r0 + max(1, S // 6)
            if h > r1 + 2: img[r0:r1, :, :3] += 46
            img[..., :3] += (np.linspace(14, 0, h)[:, None] * np.ones((1, w)))[..., None]
        bevel(img, face, 0.5 if mat == 'onyx' else 0.1, max(1, S // 4))
    elif mat == 'diamond':
        yy, xx = np.mgrid[0:h, 0:w]
        cx, cy = (w - 1) / 2, (h - 1) / 2
        dx, dy = (xx - cx) / max(1, w / 2), (yy - cy) / max(1, h / 2)
        v = np.where(dx * dy > 0, 0.62, 0.95) * np.where(np.abs(dx) + np.abs(dy) < 0.5, 1.12, 0.85)
        v = v - 0.12 * dy
        img[..., :3] = np.clip(np.array((236, 238, 246))[None, None] * v[..., None], 0, 255)
        img[(np.abs(dx + 0.35) < 0.18) & (np.abs(dy + 0.35) < 0.18), :3] = 255
        glow = img.copy(); glow[..., :3] *= 0.55
    elif mat == 'skin':
        base((214, 168, 134)); img[..., :3] *= (1 + fine(h, w, 0.04))[..., None]
    elif mat == 'face':
        base((214, 168, 134)); img[..., :3] *= (1 + fine(h, w, 0.04))[..., None]
        if face == 'front':
            u = w / 8
            for ex, inner in ((1, 2), (5, 5)):
                img[int(4 * u):int(5 * u), int(ex * u):int((ex + 2) * u), :3] = (245, 245, 245)
                img[int(4 * u):int(5 * u), int(inner * u):int((inner + 1) * u), :3] = (52, 40, 34)
            img[int(6.4 * u):int(6.8 * u), int(3 * u):int(5 * u), :3] = (170, 112, 98)
        if face in ('top', 'back') or face in ('left', 'right'):
            hair = np.array((20, 20, 23), float)
            n = noise(h, w, 0.2, 0.6) * 0.3
            if face == 'top' or face == 'back':
                img[..., :3] = hair * (1 + n)[..., None]
            else:
                img[:int(h * 0.3), :, :3] = (hair * (1 + n)[..., None])[:int(h * 0.3)]
        if face == 'front':
            hair = np.array((20, 20, 23), float)
            u = w / 8
            img[:int(h * 0.3), :, :3] = hair
            for bx, bh in ((0, 4.6), (1, 3.4), (2, 4.2), (3, 3.0), (4, 3.9), (5, 3.2), (6, 4.4), (7, 4.8)):
                img[:int(bh * u), int(bx * u):int((bx + 1) * u), :3] = hair * (1.0 + 0.12 * ((bx % 2) - 0.5))
    elif mat == 'hoodie':
        c = np.array((24, 24, 27), float)
        img[..., :3] = np.clip(c[None, None] * (1 + noise(h, w, 0.4, 0.4) * 0.25 + fine(h, w, 0.1))[..., None], 0, 255)
        if face == 'front' and w > 6 * S:
            for x in (int(w * 0.42), int(w * 0.56)):
                img[0:int(h * 0.35), x:x + max(1, S // 3), :3] = (230, 230, 230)
            img[int(h * 0.55):int(h * 0.85), int(w * 0.18):int(w * 0.82), :3] *= 0.75
    elif mat == 'sleeve':
        c = np.array((24, 24, 27), float)
        img[..., :3] = np.clip(c[None, None] * (1 + noise(h, w, 0.4, 0.4) * 0.25 + fine(h, w, 0.1))[..., None], 0, 255)
        img[int(h * 0.85):, :, :3] = (214, 168, 134)
    elif mat == 'denim':
        c = np.array((52, 62, 88), float)
        img[..., :3] = np.clip(c[None, None] * (1 + noise(h, w, 0.2, 1.0) * 0.25 + fine(h, w, 0.18))[..., None], 0, 255)
        img[int(h * 0.88):, :, :3] = (235, 235, 235)
    else:
        raise ValueError(mat)
    return img, glow

# ---------------------------------------------------------------- models
def P(name, frm, size, mat, pivot=(0, 0, 0), rot=(0, 0, 0), parent=None):
    return dict(name=name, frm=list(frm), size=list(size), mat=mat, pivot=list(pivot), rot=list(rot), parent=parent)

def box_c(name, cx, y0, cz, w, h, d, mat, **k):
    return P(name, (cx - w / 2, y0, cz - d / 2), (w, h, d), mat, **k)

MODELS = {}

# Black Fedora — curved brim, pinched crown, red satin band, silver rabbit pin, red feather
f = []
f.append(box_c('brim', 0, -8.55, 0, 12.5, 0.5, 12.5, 'felt_dark'))
f.append(P('brim_f', (-6.25, -0.25, -2.0), (12.5, 0.5, 2.0), 'felt_dark', pivot=(0, -8.3, -6.25), rot=(12, 0, 0)))
f.append(P('brim_b', (-6.25, -0.25, 0.0), (12.5, 0.5, 2.0), 'felt_dark', pivot=(0, -8.3, 6.25), rot=(18, 0, 0)))
f.append(P('brim_l', (-2.0, -0.25, -6.25), (2.0, 0.5, 12.5), 'felt_dark', pivot=(-6.25, -8.3, 0), rot=(0, 0, 16)))
f.append(P('brim_r', (0.0, -0.25, -6.25), (2.0, 0.5, 12.5), 'felt_dark', pivot=(6.25, -8.3, 0), rot=(0, 0, -16)))
f.append(box_c('crown_lo', 0, -13.2, 0.2, 8.75, 4.7, 8.75, 'felt'))
f.append(box_c('crown_hi', 0, -14.6, 0.3, 8.0, 1.4, 8.0, 'felt'))
f.append(box_c('cap_l', -2.15, -15.1, 0.4, 3.5, 0.5, 7.25, 'felt'))
f.append(box_c('cap_r', 2.15, -15.1, 0.4, 3.5, 0.5, 7.25, 'felt'))
f.append(P('pinch_l', (-0.75, -1.6, -0.25), (1.5, 3.2, 0.5), 'felt_dark', pivot=(-2.6, -12.4, -4.1), rot=(0, 18, 0)))
f.append(P('pinch_r', (-0.75, -1.6, -0.25), (1.5, 3.2, 0.5), 'felt_dark', pivot=(2.6, -12.4, -4.1), rot=(0, -18, 0)))
f.append(box_c('band', 0, -10.2, 0.2, 9.0, 1.7, 9.0, 'satin'))
f.append(box_c('pin', -4.65, -9.85, -1.5, 0.375, 1.5, 1.25, 'silver'))
f.append(box_c('pin_ear1', -4.65, -11.0, -1.85, 0.375, 1.25, 0.375, 'silver'))
f.append(box_c('pin_ear2', -4.65, -11.0, -1.15, 0.375, 1.25, 0.375, 'silver'))
f.append(P('feather', (-0.125, -4.0, -0.6), (0.25, 4.0, 1.25), 'glow', pivot=(-4.6, -9.2, 2.2), rot=(-25, 0, -14)))
f.append(P('feather_tip', (-0.125, -1.5, -0.4), (0.25, 1.5, 0.8), 'glow', pivot=(0, -4.0, 0), rot=(-20, 0, 0), parent='feather'))
MODELS['fedora'] = dict(attach='head', parts=f)

# Spiked Crown — beveled obsidian ring, 8 tapered spikes leaning outward, faceted glowing gems
c = []
c.append(box_c('ring', 0, -10.4, 0, 9.0, 2.6, 9.0, 'obsidian'))
c.append(box_c('ring_lip', 0, -8.4, 0, 9.5, 0.6, 9.5, 'gunmetal'))
c.append(box_c('ring_glow', 0, -9.25, 0, 9.12, 0.3, 9.12, 'glow'))
spikes = [(-3.6, -3.6, 1.0), (3.6, -3.6, 1.0), (-3.6, 3.6, 1.0), (3.6, 3.6, 1.0),
          (0, -4.5, 0.85), (0, 4.5, 0.85), (-4.5, 0, 0.85), (4.5, 0, 0.85)]
for i, (sx, sz, s) in enumerate(spikes):
    out = (np.sign(sx), np.sign(sz))
    pv = (sx, -10.4, sz)
    rx = -10 * out[1] if sz else 0  # lean outward
    rz = 10 * out[0] if sx else 0
    n = f's{i}'
    c.append(P(n, (-0.9 * s, -3.0 * s, -0.9 * s), (1.8 * s, 3.0 * s, 1.8 * s), 'obsidian', pivot=pv, rot=(rx, 0, -rz)))
    c.append(P(n + 'b', (-0.6 * s, -2.4 * s, -0.6 * s), (1.2 * s, 2.4 * s, 1.2 * s), 'obsidian', pivot=(0, -3.0 * s, 0), parent=n))
    c.append(P(n + 'c', (-0.3 * s, -1.6 * s, -0.3 * s), (0.6 * s, 1.6 * s, 0.6 * s), 'gunmetal', pivot=(0, -2.4 * s, 0), parent=n + 'b'))
    # gem on the outward face of the spike base
    gx, gz = (0.0, -0.95 * s) if abs(sz) >= abs(sx) and sz < 0 else (0.0, 0.95 * s) if abs(sz) >= abs(sx) else ((-0.95 * s, 0.0) if sx < 0 else (0.95 * s, 0.0))
    if abs(sx) == abs(sz):  # corner spikes: gem on front/back face
        gx, gz = 0.0, (-0.95 * s if sz < 0 else 0.95 * s)
    gw, gd = (1.0 * s, 0.25) if gx == 0 else (0.25, 1.0 * s)
    c.append(P(n + 'g', (gx - gw / 2, -2.3 * s, gz - gd / 2), (gw, 1.5 * s, gd), 'gem', parent=n))
for (gx, gz, gw, gd) in [(0, -4.6, 2.0, 0.3), (0, 4.6, 2.0, 0.3), (-4.6, 0, 0.3, 2.0), (4.6, 0, 0.3, 2.0)]:
    c.append(box_c('rg%d%d' % (gx, gz), gx, -10.0, gz, gw, 1.25, gd, 'gem'))
MODELS['crown'] = dict(attach='head', parts=c)


# Royal Crown (black & white) — glossy onyx band with pearl trims, stepped peaks, diamond gems, pearl-tipped corner spikes
r = []
r.append(box_c('band', 0, -9.8, 0, 9.2, 2.2, 9.2, 'onyx'))
r.append(box_c('lip', 0, -7.85, 0, 9.7, 0.45, 9.7, 'onyx'))
r.append(box_c('stripe', 0, -9.0, 0, 9.5, 0.65, 9.5, 'pearl'))
r.append(box_c('rim', 0, -10.35, 0, 9.5, 0.55, 9.5, 'pearl'))
def peak(tag, big, rot_y):
    s = 1.0 if big else 0.78
    parts = []
    piv = (0, -10.35, 0)
    parts.append(P(tag, (-0.01, -0.01, -0.01), (0.02, 0.02, 0.02), 'onyx', pivot=piv, rot=(0, rot_y, 0)))
    y = 0.0
    for i, (ww, bw) in enumerate(((7.2, 5.6), (5.4, 3.8), (3.6, 2.0), (2.0, 0.0))):
        hh = 0.85 * s
        parts.append(P(tag + 'k%d' % i, (-ww * s / 2, y - hh, -4.95), (ww * s, hh, 0.7), 'onyx', parent=tag))
        parts.append(P(tag + 'w%d' % i, (-ww * s / 2, y - hh, -5.2), (ww * s, hh, 0.25), 'pearl', parent=tag))
        if bw:
            parts.append(P(tag + 'b%d' % i, (-bw * s / 2, y - hh, -5.4), (bw * s, hh, 0.2), 'onyx', parent=tag))
        y -= hh
    parts.append(P(tag + 'cap', (-0.7 * s, y - 0.7 * s, -5.0), (1.4 * s, 0.7 * s, 0.6), 'pearl', parent=tag))
    g = 1.5 * s
    parts.append(P(tag + 'gem', (-g / 2, -g / 2, -0.3), (g, g, 0.6), 'diamond', pivot=(0, -1.25 * s, -5.55), rot=(0, 0, 45), parent=tag))
    for sx in (-1, 1):  # small pearl studs on the rim between peak and corner
        parts.append(P(tag + 'st%d' % sx, (sx * 2.85 - 0.45, -0.9, -4.95), (0.9, 0.9, 0.6), 'pearl', parent=tag))
    return parts
r += peak('pf', True, 0)
r += peak('pb', False, 180)
r += peak('pl', False, 90)
r += peak('pr', False, -90)
for i, (cx_, cz_) in enumerate(((-4.3, -4.3), (4.3, -4.3), (-4.3, 4.3), (4.3, 4.3))):
    n = 'cs%d' % i
    r.append(P(n, (-0.75, -1.7, -0.75), (1.5, 1.7, 1.5), 'onyx', pivot=(cx_, -10.35, cz_), rot=(-8 * np.sign(cz_), 0, 8 * np.sign(cx_))))
    r.append(P(n + 'b', (-0.55, -1.2, -0.55), (1.1, 1.2, 1.1), 'pearl', pivot=(0, -1.7, 0), parent=n))
    r.append(P(n + 'c', (-0.3, -0.8, -0.3), (0.6, 0.8, 0.6), 'pearl', pivot=(0, -1.2, 0), parent=n + 'b'))
MODELS['royal_crown'] = dict(attach='head', parts=r)

# Rabbit Ears — two-segment furry ears that flop back at the tip, pink inner
e = []
for side, sgn in (('l', -1), ('r', 1)):
    base = (2.2 * sgn, -8.0, 0.6)
    e.append(P('ear_' + side, (-1.2, -5.5, -0.5), (2.4, 5.5, 1.0), 'fur', pivot=base, rot=(-6, -8 * sgn, 12 * sgn)))
    e.append(P('in_' + side, (-0.7, -5.0, -0.6), (1.4, 4.6, 0.2), 'pink', parent='ear_' + side))
    e.append(P('ear2_' + side, (-1.05, -4.0, -0.45), (2.1, 4.0, 0.9), 'fur', pivot=(0, -5.5, 0), rot=(-28, 0, 6 * sgn), parent='ear_' + side))
    e.append(P('in2_' + side, (-0.6, -3.4, -0.55), (1.2, 3.2, 0.2), 'pink', parent='ear2_' + side))
    e.append(P('tip_' + side, (-0.7, -0.8, -0.35), (1.4, 0.8, 0.7), 'fur_tip', pivot=(0, -4.0, 0), rot=(-12, 0, 0), parent='ear2_' + side))
MODELS['ears'] = dict(attach='head', parts=e)

# Claws — stitched leather gauntlet, silver cuff, glowing seam, three curved steel blades (right arm; left mirrored)
k = []
k.append(P('gaunt', (-3.4, 4.5, -2.4), (4.8, 5.9, 4.8), 'leather'))
k.append(P('cuff', (-3.6, 4.2, -2.6), (5.2, 0.9, 5.2), 'silver'))
k.append(P('seam', (-3.45, 6.6, -2.45), (4.9, 0.25, 4.9), 'glow'))
k.append(P('knuck', (-3.0, 8.4, -2.8), (4.0, 1.6, 0.6), 'gunmetal'))
for i, bx in enumerate((-2.35, -1.0, 0.35)):
    n = 'blade%d' % i
    k.append(P(n, (-0.25, 0, -0.45), (0.5, 4.0, 0.9), 'blade', pivot=(bx, 9.2, -2.6), rot=(28, 0, 0)))
    k.append(P(n + 'b', (-0.2, 0, -0.35), (0.4, 3.0, 0.7), 'blade', pivot=(0, 4.0, 0), rot=(18, 0, 0), parent=n))
    k.append(P(n + 'c', (-0.12, 0, -0.2), (0.25, 1.8, 0.4), 'blade', pivot=(0, 3.0, 0), rot=(20, 0, 0), parent=n + 'b'))
MODELS['claws'] = dict(attach='arms', parts=k)

# Preview mannequin (not exported) — part-local boxes + attachments
BODY = dict(
    head=dict(pivot=(0, 0, 0), parts=[P('h', (-4, -8, -4), (8, 8, 8), 'face')]),
    body=dict(pivot=(0, 0, 0), parts=[P('b', (-4, 0, -2), (8, 12, 4), 'hoodie')]),
    right_arm=dict(pivot=(-5, 2, 0), parts=[P('ra', (-3, -2, -2), (4, 12, 4), 'sleeve')]),
    left_arm=dict(pivot=(5, 2, 0), parts=[P('la', (-1, -2, -2), (4, 12, 4), 'sleeve')]),
    right_leg=dict(pivot=(-1.9, 12, 0), parts=[P('rl', (-2, 0, -2), (4, 12, 4), 'denim')]),
    left_leg=dict(pivot=(1.9, 12, 0), parts=[P('ll', (-2, 0, -2), (4, 12, 4), 'denim')]),
)

def mirror(parts):
    out = []
    for p in parts:
        q = json.loads(json.dumps(p))
        q['frm'][0] = -(p['frm'][0] + p['size'][0])
        q['pivot'][0] = -p['pivot'][0]
        q['rot'][1] = -p['rot'][1]; q['rot'][2] = -p['rot'][2]
        out.append(q)
    return out

# ---------------------------------------------------------------- atlas
FACES = ['top', 'bottom', 'right', 'front', 'left', 'back']

def face_regions(u, v, w, h, d):
    return dict(top=(u + d, v, w, d), bottom=(u + d + w, v, w, d), right=(u, v + d, d, h),
                front=(u + d, v + d, w, h), left=(u + d + w, v + d, d, h), back=(u + 2 * d + w, v + d, w, h))

def build_atlas(parts, width=64):
    x = y = row = 0
    for p in parts:
        w, h, d = p['size']
        bw, bh = math.ceil(2 * d + 2 * w + 0.5), math.ceil(d + h + 0.5)
        if x + bw > width:
            x, y, row = 0, y + row, 0
        p['uv'] = [x, y]
        x += bw; row = max(row, bh)
    H = y + row
    H = max(16, 2 ** math.ceil(math.log2(H)))
    tex = np.zeros((H * S, width * S, 4)); glow = np.zeros_like(tex)
    anyglow = False
    for p in parts:
        w, h, d = p['size']
        for face, (fu, fv, fw, fh) in face_regions(*p['uv'], w, h, d).items():
            pw, ph = max(1, round(fw * S)), max(1, round(fh * S))
            img, g = paint(p['mat'], face, pw, ph)
            X, Y = round(fu * S), round(fv * S)
            tex[Y:Y + ph, X:X + pw] = img
            if g is not None:
                glow[Y:Y + ph, X:X + pw] = g; anyglow = True
    return tex.astype(np.uint8), (glow.astype(np.uint8) if anyglow else None), (width, H)

def export(outdir):
    os.makedirs(outdir + '/models', exist_ok=True); os.makedirs(outdir + '/textures', exist_ok=True)
    for key, m in MODELS.items():
        tex, glow, (tw, th) = build_atlas(m['parts'])
        Image.fromarray(tex, 'RGBA').save(f'{outdir}/textures/{key}.png')
        if glow is not None:
            Image.fromarray(glow, 'RGBA').save(f'{outdir}/textures/{key}_glow.png')
        m['tex'] = tex; m['glow'] = glow; m['texsize'] = (tw, th)
        js = dict(attach=m['attach'], texture=[tw, th], glow=glow is not None,
                  parts=[dict(name=p['name'], parent=p['parent'], pivot=p['pivot'], rot=p['rot'],
                              from_=p['frm'], size=p['size'], uv=p['uv']) for p in m['parts']])
        txt = json.dumps(js, indent=1).replace('"from_"', '"from"')
        open(f'{outdir}/models/{key}.json', 'w').write(txt)
    for key, b in BODY.items():
        tex, glow, ts = build_atlas(b['parts'])
        b['tex'] = tex; b['glow'] = None; b['texsize'] = ts

if __name__ == '__main__':
    export('/home/claude/cos/out')
    print('ok')

# TheRabbits cosmetics generator: models (MC pixel units, part-local), HD textures, atlas + JSON export.
import json, math, os
import numpy as np
from PIL import Image, ImageFilter

S = 8  # texels per model unit (atlas is declared in units, PNG is S times bigger)
rng = np.random.default_rng(7)

# ---------------------------------------------------------------- materials
SHIELD_T = 0.3  # honeycomb animation time (0..1); frames are rendered by sweeping this

def honeycomb(base, w, h, t):
    R_ = 1.55 * S
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    x, y = xx - w / 2, yy - h / 2
    q = (np.sqrt(3) / 3 * x - y / 3) / R_; r = (2 / 3 * y) / R_
    cx, cz = q, r; cy = -cx - cz
    rx, ry, rz = np.round(cx), np.round(cy), np.round(cz)
    dx, dy, dz = np.abs(rx - cx), np.abs(ry - cy), np.abs(rz - cz)
    fx = (dx > dy) & (dx > dz); fy = ~fx & (dy > dz)
    rx = np.where(fx, -ry - rz, rx); ry = np.where(fy, -rx - rz, ry); rz = np.where(~fx & ~fy, -rx - ry, rz)
    norm = (np.abs(cx - rx) + np.abs(cy - ry) + np.abs(cz - rz)) / 2      # 0 centre .. 0.5 edge
    hsh = (np.abs(np.sin(rx * 12.9898 + rz * 78.233) * 43758.5453) % 1.0)
    # brightness: a diagonal wave sweeping across + per-cell sparkle
    ph = (t - (rx * 0.09 - rz * 0.06) - hsh * 0.18) % 1.0
    b = np.clip(1 - np.abs(ph - 0.5) * 6.5, 0, 1)
    gold = np.array((255, 186, 50), float); orange = np.array((255, 122, 30), float)
    tone = (hsh > 0.5)[..., None]
    fill = np.where(tone, orange, gold)
    edge = norm > 0.40
    out = base.copy()
    # faint purple grid everywhere, then outlines (b>0.15) and fills (b>0.55)
    out = np.where(edge[..., None], out + np.array((22, 8, 34)), out)
    ol = edge & (b > 0.15)
    out = np.where(ol[..., None], fill * (0.35 + 0.5 * b)[..., None], out)
    fl = (~edge) & (b > 0.55)
    shade = (1.05 - 0.35 * norm / 0.4)[..., None]
    out = np.where(fl[..., None], fill * shade * (0.55 + 0.45 * b)[..., None], out)
    # purple light along top and bottom edges
    v = np.abs(yy / max(1, h - 1) - 0.5) * 2
    out = out + np.array((90, 30, 140)) * (np.clip(v - 0.82, 0, 1) / 0.18)[..., None] ** 2
    glow = np.zeros((h, w, 4))
    lit = ol | fl
    glow[..., :3] = np.where(lit[..., None], fill * (0.25 + 0.45 * b)[..., None], 0)
    glow[..., 3] = np.where(lit, 255, 0)
    return np.clip(out, 0, 255), glow

FAB = {'fab_red': (200, 26, 34), 'fab_green': (96, 200, 58), 'fab_dkgreen': (44, 120, 36), 'fab_black': (30, 30, 34),
       'fab_black2': (20, 20, 23), 'fab_white': (240, 240, 244), 'fab_lav': (176, 146, 236), 'fab_pink': (244, 132, 186),
       'fab_velvet': (140, 16, 30), 'fab_pupil': (16, 16, 18)}
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
    elif mat in FAB:
        c = np.array(FAB[mat], float)
        n = noise(h, w, 0.5, 0.5) * 0.12 + fine(h, w, 0.08)
        g = vgrad(h, w, 1.06, 0.9) if face not in ('top', 'bottom') else np.ones((h, w))
        img[..., :3] = np.clip(c[None, None] * ((1 + n) * g)[..., None], 0, 255)
        bevel(img, face, 0.16, max(1, S // 4))
    elif mat == 'fluff':
        c = np.array((244, 244, 246), float)
        n = noise(h, w, 0.3, 0.3, 2) * 0.07 + fine(h, w, 0.04)
        img[..., :3] = np.clip(c[None, None] * (1.0 + n)[..., None], 0, 255)
        bevel(img, face, 0.12, max(1, S // 3))
    elif mat == 'gold':
        c = np.array((236, 176, 40), float)
        t = np.linspace(0, 1, h)[:, None] * np.ones((1, w))
        sh = 1.08 - 0.3 * t + 0.25 * np.exp(-((t - 0.2) ** 2) / 0.01) + noise(h, w, 0.2, 2, 2) * 0.08
        img[..., :3] = np.clip(c[None, None] * sh[..., None], 0, 255)
        bevel(img, face, 0.3, max(1, S // 4))
    elif mat == 'shield_face':
        c = np.array((14, 10, 20), float)
        yy, xx = np.mgrid[0:h, 0:w].astype(float)
        sh = 1.0 + noise(h, w, 0.4, 0.4, 3) * 0.35
        purple = np.array((52, 22, 84), float) * np.clip(noise(h, w, 0.8, 0.8, 2) + 0.35, 0, 1)[..., None]
        img[..., :3] = np.clip(c[None, None] * sh[..., None] + purple * 0.6, 0, 255)
        if face == 'front':
            img[..., :3], glow = honeycomb(img[..., :3], w, h, SHIELD_T)
        bevel(img, face, 0.2)
    elif mat == 'neon':
        xx = np.linspace(-1, 1, w)[None, :] * np.ones((h, 1)); yy = np.linspace(-1, 1, h)[:, None] * np.ones((1, w))
        core = 1.0 - 0.35 * np.maximum(np.abs(xx), np.abs(yy)) ** 2
        img[..., 0] = 200 * core; img[..., 1] = 96 * core; img[..., 2] = 255 * core
        img[np.maximum(np.abs(xx), np.abs(yy)) < 0.35, :3] = (240, 200, 255)
        glow = img.copy()
    elif mat == 'purple_metal':
        c = np.array((112, 56, 176), float)
        t = np.linspace(0, 1, h)[:, None] * np.ones((1, w))
        sh = 1.1 - 0.35 * t + 0.35 * np.exp(-((t - 0.2) ** 2) / 0.01) + noise(h, w, 0.2, 2, 2) * 0.1
        img[..., :3] = np.clip(c[None, None] * sh[..., None], 0, 255)
        bevel(img, face, 0.4, max(1, S // 4))
    elif mat == 'void':
        c = np.array((18, 12, 28), float)
        xx = np.linspace(-1, 1, w)[None, :] * np.ones((h, 1)); yy = np.linspace(-1, 1, h)[:, None] * np.ones((1, w))
        sh = 1.0 + 1.6 * np.exp(-((xx + 0.3) ** 2 + (yy + 0.4) ** 2) / 0.08)
        img[..., :3] = np.clip(c[None, None] * sh[..., None], 0, 255)
        bevel(img, face, 0.5, max(1, S // 4))
    elif mat.startswith('astro_'):
        part = mat[6:]
        c = np.array((238, 240, 244), float)
        img[..., :3] = np.clip(c[None, None] * (1 + noise(h, w, 0.4, 0.4, 2) * 0.05 + fine(h, w, 0.03))[..., None], 0, 255)
        u = w / 8 if part in ('head', 'body') else w / 4
        v = h / 8 if part == 'head' else h / 12
        if part == 'head' and face == 'front':
            img[int(1.6 * v):int(6.6 * v), int(0.9 * u):int(7.1 * u), :3] = (150, 154, 162)
            vis = np.array((28, 36, 52), float)
            yy, xx = np.mgrid[0:h, 0:w]
            box = (yy >= int(2 * v)) & (yy < int(6.2 * v)) & (xx >= int(1.3 * u)) & (xx < int(6.7 * u))
            grad = vis * (1 + 0.6 * np.clip(1 - yy / h, 0, 1))[..., None]
            img[..., :3] = np.where(box[..., None], grad, img[..., :3])
            refl = box & (xx > yy * 0.6 + w * 0.45) & (xx < yy * 0.6 + w * 0.62)
            img[refl, :3] = (110, 160, 230)
        if part == 'head' and face in ('left', 'right'):
            img[int(3 * v):int(5 * v), int(3 * u):int(5 * u), :3] = (170, 174, 182)
        if part == 'body':
            img[int(7.2 * v):int(8.1 * v), :, :3] = (214, 40, 46)
            if face == 'front':
                img[int(2.5 * v):int(5.5 * v), int(2 * u):int(6 * u), :3] = (178, 182, 190)
                img[int(3.2 * v):int(4.6 * v), int(3 * u):int(5 * u), :3] = (40, 50, 70)
                img[int(3.4 * v):int(3.8 * v), int(3.2 * u):int(3.8 * u), :3] = (90, 200, 255)
                img[int(1.0 * v):int(1.8 * v), int(5.4 * u):int(7 * u), :3] = (40, 90, 200)
            if face == 'back':
                img[int(1 * v):int(6.5 * v), int(1 * u):int(7 * u), :3] = (200, 204, 210)
        if part == 'arm':
            img[int(3.0 * v):int(3.8 * v), :, :3] = (214, 40, 46)
            img[int(9.8 * v):, :, :3] = (54, 60, 74)
        if part == 'leg':
            img[int(3.5 * v):int(5.5 * v), int(0.6 * u):int(3.4 * u), :3] = (196, 200, 208) if face == 'front' else img[int(3.5 * v):int(5.5 * v), int(0.6 * u):int(3.4 * u), :3]
            img[int(9.6 * v):, :, :3] = (60, 66, 80)
        bevel(img, face, 0.12)
    elif mat == 'electric':
        img[..., :3] = 0
        xx = np.arange(w)
        core = (h - 1) / 2 + np.sin(xx / max(1, w) * 18 + rng.random() * 6) * h * 0.18 + (rng.random(w) - 0.5) * h * 0.25
        yy = np.arange(h)[:, None]
        d = np.abs(yy - core[None, :])
        v = np.clip(1.3 - d / max(1.0, h * 0.22), 0, 1.3)
        img[..., 0] = np.clip(60 * v + 140 * np.clip(v - 0.9, 0, 1), 0, 255)
        img[..., 1] = np.clip(170 * v + 80 * np.clip(v - 0.9, 0, 1), 0, 255)
        img[..., 2] = np.clip(255 * v, 0, 255)
        glow = img.copy()
    elif mat == 'spark':
        img[..., :3] = (150, 215, 255)
        glow = img.copy()
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


# ---------------- six colour hats
def side_group(tag, rot_y, pivot=(0, 0, 0)):
    return P(tag, (-0.01, -0.01, -0.01), (0.02, 0.02, 0.02), 'fab_black2', pivot=pivot, rot=(0, rot_y, 0))

# 1. Gold Crown — gold band, velvet cap, battlement spikes, rubies
g = [box_c('band', 0, -10.2, 0, 9.2, 2.2, 9.2, 'gold'), box_c('lip', 0, -8.4, 0, 9.6, 0.5, 9.6, 'gold'),
     box_c('velvet', 0, -10.9, 0, 8.4, 1.6, 8.4, 'fab_velvet')]
for tag, ry in (('n', 0), ('e', 90), ('s', 180), ('w', -90)):
    g.append(side_group(tag, ry, (0, -10.2, 0)))
    for j, (x, hh) in enumerate(((-3.5, 2.2), (-1.75, 1.4), (0, 3.0), (1.75, 1.4), (3.5, 2.2))):
        g.append(P(tag + 'sp%d' % j, (x - 0.55, -hh, -4.6), (1.1, hh, 0.9), 'gold', parent=tag))
        g.append(P(tag + 'tp%d' % j, (x - 0.3, -hh - 0.6, -4.45), (0.6, 0.6, 0.6), 'gold', parent=tag))
    g.append(P(tag + 'rb', (-0.8, 0.5, -4.9), (1.6, 1.4, 0.4), 'gem', parent=tag))
    for x in (-2.6, 2.6):
        g.append(P(tag + 'rs%d' % x, (x - 0.45, 0.75, -4.85), (0.9, 0.9, 0.35), 'gem', parent=tag))
    g.append(P(tag + 'rt', (-0.4, -2.4, -4.75), (0.8, 0.8, 0.3), 'gem', parent=tag))
MODELS['gold_crown'] = dict(attach='head', parts=g)

# 2. Cat Ears Hat — black beanie, pink-lined ears, little white cat face
c2 = [box_c('cap', 0, -9.6, 0, 9.1, 5.0, 9.1, 'fab_black'), box_c('top', 0, -10.1, 0, 8.4, 0.5, 8.4, 'fab_black'),
      box_c('fold', 0, -5.4, 0, 9.4, 1.1, 9.4, 'fab_black2')]
for sx in (-1, 1):
    x = 2.7 * sx
    c2.append(P('ear%d' % sx, (-1.7, -1.6, -0.75), (3.4, 1.6, 1.5), 'fab_black', pivot=(x, -10.0, -1.0), rot=(0, 0, 12 * sx)))
    c2.append(P('ear%db' % sx, (-1.15, -1.5, -0.7), (2.3, 1.5, 1.4), 'fab_black', pivot=(0, -1.6, 0), parent='ear%d' % sx))
    c2.append(P('ear%dc' % sx, (-0.55, -1.2, -0.6), (1.1, 1.2, 1.2), 'fab_black', pivot=(0, -1.5, 0), parent='ear%db' % sx))
    c2.append(P('in%d' % sx, (-1.15, -1.6, -1.0), (2.3, 1.6, 0.3), 'fab_pink', parent='ear%d' % sx))
    c2.append(P('in%db' % sx, (-0.6, -1.4, -0.95), (1.2, 1.4, 0.3), 'fab_pink', parent='ear%db' % sx))
    c2.append(box_c('eye%d' % sx, 1.5 * sx, -8.0, -4.65, 0.9, 0.6, 0.3, 'fab_white'))
for i, (x, y) in enumerate(((-0.9, -6.9), (-0.45, -6.6), (0, -6.9), (0.45, -6.6), (0.9, -6.9))):
    c2.append(box_c('m%d' % i, x, y, -4.65, 0.45, 0.35, 0.3, 'fab_white'))
MODELS['cat_hat'] = dict(attach='head', parts=c2)

# 3. Bucket Hat — black crown, white stripe, silver eyelets, sloped brim
b3 = [box_c('crown', 0, -10.6, 0, 8.9, 3.4, 8.9, 'fab_black'), box_c('top', 0, -11.1, 0, 8.1, 0.5, 8.1, 'fab_black'),
      box_c('stripe', 0, -8.4, 0, 9.15, 1.05, 9.15, 'pearl'), box_c('ring', 0, -7.4, 0, 10.0, 0.45, 10.0, 'fab_black2')]
for tag, ry in (('n', 0), ('e', 90), ('s', 180), ('w', -90)):
    b3.append(side_group('b' + tag, ry, (0, -7.2, 0)))
    b3.append(P('b' + tag + 'sk', (-6.4, 0, -2.3), (12.8, 0.45, 2.3), 'fab_black2', pivot=(0, 0, -5.0), rot=(30, 0, 0), parent='b' + tag))
    for x in (-1.6, 1.6):
        b3.append(P('b' + tag + 'ey%d' % x, (x - 0.25, -2.4, -4.55), (0.5, 0.5, 0.2), 'silver', parent='b' + tag))
MODELS['bucket_hat'] = dict(attach='head', parts=b3)

# 4. Santa Hat — red cone that flops to the side, fluffy trim and pom-pom
s4 = [box_c('trim', 0, -9.6, 0, 9.6, 2.0, 9.6, 'fluff'),
      box_c('t1', -2.6, -9.8, -4.85, 2.0, 0.6, 0.4, 'fluff'), box_c('t2', 2.2, -8.3, -4.9, 2.4, 0.7, 0.4, 'fluff'),
      P('c0', (-4.3, -2.6, -4.3), (8.6, 2.6, 8.6), 'fab_red', pivot=(0, -9.6, 0))]
prev, dims = 'c0', ((7.4, 2.2, 10), (6.0, 2.0, 16), (4.6, 1.9, 24), (3.4, 1.8, 32), (2.4, 1.6, 36))
y = -2.6
for i, (wdt, hh, rz) in enumerate(dims):
    n = 'c%d' % (i + 1)
    s4.append(P(n, (-wdt / 2, -hh, -wdt / 2), (wdt, hh, wdt), 'fab_red', pivot=(0.5 if i else 0.3, y, 0.4), rot=(-8, 0, rz), parent=prev))
    prev, y = n, -hh
s4.append(P('pom', (-1.3, -2.4, -1.3), (2.6, 2.6, 2.6), 'fluff', pivot=(0.3, y, 0), parent=prev))
MODELS['santa_hat'] = dict(attach='head', parts=s4)

# 5. Frog Hat — green dome, big 3D eyes, smile and blush
f5 = [box_c('dome', 0, -9.6, 0, 9.2, 4.8, 9.2, 'fab_green'), box_c('top', 0, -10.2, 0, 8.4, 0.6, 8.4, 'fab_green'),
      box_c('lip', 0, -5.3, 0, 9.6, 0.8, 9.6, 'fab_dkgreen')]
for sx in (-1, 1):
    x = 2.3 * sx
    f5.append(box_c('eye%d' % sx, x, -12.6, -2.4, 3.0, 2.8, 2.8, 'fab_green'))
    f5.append(box_c('wh%d' % sx, x, -12.2, -3.9, 2.2, 2.1, 0.3, 'fab_white'))
    f5.append(box_c('pu%d' % sx, x + 0.25 * sx, -11.7, -4.1, 1.0, 1.2, 0.3, 'fab_pupil'))
    f5.append(box_c('sh%d' % sx, x - 0.35 * sx, -12.0, -4.2, 0.4, 0.4, 0.2, 'fab_white'))
    f5.append(box_c('bl%d' % sx, 3.3 * sx, -7.2, -4.7, 1.0, 0.5, 0.2, 'fab_pink'))
    f5.append(box_c('no%d' % sx, 0.6 * sx, -8.6, -4.7, 0.4, 0.4, 0.2, 'fab_dkgreen'))
f5 += [box_c('mouth', 0, -6.7, -4.7, 4.4, 0.35, 0.2, 'fab_dkgreen'),
       box_c('mo_l', -2.35, -7.0, -4.7, 0.5, 0.45, 0.2, 'fab_dkgreen'), box_c('mo_r', 2.35, -7.0, -4.7, 0.5, 0.45, 0.2, 'fab_dkgreen')]
MODELS['frog_hat'] = dict(attach='head', parts=f5)

# 6. Bunny Hat — fluffy white beanie, lavender brim, tall bunny ears
u6 = [box_c('cap', 0, -9.6, 0, 9.1, 5.0, 9.1, 'fluff'), box_c('top', 0, -10.1, 0, 8.4, 0.5, 8.4, 'fluff'),
      box_c('fold', 0, -5.4, 0, 9.4, 1.1, 9.4, 'fab_lav'), box_c('nose', 0, -6.9, -4.75, 0.8, 0.5, 0.3, 'fab_pink')]
for sx in (-1, 1):
    n = 'ear%d' % sx
    u6.append(P(n, (-1.0, -5.2, -0.55), (2.0, 5.2, 1.1), 'fluff', pivot=(2.0 * sx, -10.0, 0.4), rot=(-4, 0, 9 * sx)))
    u6.append(P(n + 'i', (-0.55, -4.6, -0.7), (1.1, 4.2, 0.25), 'fab_lav', parent=n))
    u6.append(P(n + 'b', (-0.9, -3.4, -0.5), (1.8, 3.4, 1.0), 'fluff', pivot=(0, -5.2, 0), rot=(-10, 0, 4 * sx), parent=n))
    u6.append(P(n + 'bi', (-0.5, -2.9, -0.65), (1.0, 2.6, 0.25), 'fab_lav', parent=n + 'b'))
    u6.append(P(n + 'c', (-0.6, -0.7, -0.4), (1.2, 0.7, 0.8), 'fluff', pivot=(0, -3.4, 0), parent=n + 'b'))
MODELS['bunny_hat'] = dict(attach='head', parts=u6)


# Neon Royal Shield — black/purple plate, gold-orange honeycomb, neon frame, purple+gold corners, glossy core, handle
sd = [P('plate', (-6, -11, -0.75), (12, 22, 1.5), 'shield_face')]
for n, frm, size in (('ft', (-6.5, -11.5, -1.05), (13, 0.9, 2.1)), ('fb', (-6.5, 10.6, -1.05), (13, 0.9, 2.1)),
                     ('fl', (-6.5, -10.6, -1.05), (0.9, 21.2, 2.1)), ('fr', (5.6, -10.6, -1.05), (0.9, 21.2, 2.1))):
    sd.append(P(n, frm, size, 'neon'))
for i, (cx_, cy_) in enumerate(((-6.0, -11.0), (6.0, -11.0), (-6.0, 11.0), (6.0, 11.0))):
    sd.append(box_c('cn%d' % i, cx_, cy_ - 1.05, -0.0, 2.1, 2.1, 2.7, 'purple_metal'))
    sd.append(box_c('cg%d' % i, cx_, cy_ - 0.55, -1.45, 1.1, 1.1, 0.4, 'gold'))
sd.append(P('core', (-2.2, -2.2, -0.35), (4.4, 4.4, 0.7), 'void', pivot=(0, 0, -1.0), rot=(0, 0, 45)))
sd.append(P('core_ring', (-2.7, -2.7, -0.2), (5.4, 5.4, 0.35), 'gold', pivot=(0, 0, -0.8), rot=(0, 0, 45)))
sd.append(P('core_glow', (-0.8, -0.8, -0.25), (1.6, 1.6, 0.5), 'neon', pivot=(0, 0, -1.5), rot=(0, 0, 45)))
for (x, y) in ((0, -6.5), (0, 6.5), (-3.6, 0), (3.6, 0)):
    sd.append(box_c('stud%d%d' % (x, y), x, y - 0.4, -0.95, 0.8, 0.8, 0.4, 'gold'))
sd += [box_c('post_t', 0, -3.6, 1.35, 1.2, 1.2, 1.2, 'gunmetal'), box_c('post_b', 0, 2.4, 1.35, 1.2, 1.2, 1.2, 'gunmetal'),
       box_c('grip', 0, -3.6, 2.3, 1.6, 7.2, 0.9, 'purple_metal')]
MODELS['neon_shield'] = dict(attach='shield', parts=sd)


# Electric Aura - swirling blue energy rings + rising sparks around the body (animated in-game)
au = []
for k, (y, r, n, tilt) in enumerate(((22.5, 9.0, 12, 8), (13.0, 8.0, 11, -10), (3.0, 7.5, 10, 6))):
    ring = 'ring%d' % k
    au.append(P(ring, (-0.01, -0.01, -0.01), (0.02, 0.02, 0.02), 'spark', pivot=(0, y, 0), rot=(tilt, 0, tilt / 2)))
    for i in range(n):
        a = i / n * 2 * math.pi
        jy = math.sin(i * 2.7 + k) * 1.2
        au.append(P('%s_s%d' % (ring, i), (-2.0, -0.35, -0.12), (4.0, 0.7, 0.24), 'electric',
                    pivot=(math.cos(a) * r, jy, math.sin(a) * r), rot=(0, -math.degrees(a) - 90, math.sin(i * 1.9) * 25), parent=ring))
for i in range(16):
    a = (i * 137.5) % 360
    rr = 5.5 + (i * 37 % 50) / 10
    au.append(P('p%d' % i, (-0.2, -0.2, -0.2), (0.4, 0.4, 0.4), 'spark',
                pivot=(math.cos(math.radians(a)) * rr, 24, math.sin(math.radians(a)) * rr)))
MODELS['aura'] = dict(attach='aura', parts=au)

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

ASTRO = dict(
    head=dict(pivot=(0, 0, 0), parts=[P('h', (-4.4, -8.6, -4.4), (8.8, 8.8, 8.8), 'astro_head')]),
    body=dict(pivot=(0, 0, 0), parts=[P('b', (-4.2, 0, -2.2), (8.4, 12, 4.4), 'astro_body'), P('pack', (-3.4, 0.6, 2.2), (6.8, 8.5, 2.6), 'astro_pack')]),
    right_arm=dict(pivot=(-5, 2, 0), parts=[P('ra', (-3.2, -2, -2.2), (4.4, 12, 4.4), 'astro_arm')]),
    left_arm=dict(pivot=(5, 2, 0), parts=[P('la', (-1.2, -2, -2.2), (4.4, 12, 4.4), 'astro_arm')]),
    right_leg=dict(pivot=(-1.9, 12, 0), parts=[P('rl', (-2.2, 0, -2.2), (4.4, 12, 4.4), 'astro_leg')]),
    left_leg=dict(pivot=(1.9, 12, 0), parts=[P('ll', (-2.2, 0, -2.2), (4.4, 12, 4.4), 'astro_leg')]),
)

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
    for key, b in list(BODY.items()) + list(ASTRO.items()):
        tex, glow, ts = build_atlas(b['parts'])
        b['tex'] = tex; b['glow'] = None; b['texsize'] = ts

if __name__ == '__main__':
    export('/home/claude/cos/out')
    print('ok')

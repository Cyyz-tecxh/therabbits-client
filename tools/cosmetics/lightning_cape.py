# Lightning cape: 100 frames x 100 ms = 10 s, five different strikes. Vanilla cape layout (64x32) at 16x.
import numpy as np, os, math
from PIL import Image, ImageDraw, ImageFilter
S, FR, PER = 16, 100, 20             # 20 frames (2 s) per strike
FW, FH = 10 * S, 16 * S              # outer face 160 x 256
UP = 4
OUT = '/home/claude/cos/lightning/lightning_cape'

def smoky(h, w, seed):
    r = np.random.default_rng(seed); out = np.zeros((h, w)); amp = 1; tot = 0
    for o in range(5):
        gh, gw = int(h / 90 * 2 ** o) + 3, int(w / 90 * 2 ** o) + 3
        g = Image.fromarray((r.random((gh, gw)) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        out += np.asarray(g) / 255 * amp; tot += amp; amp *= 0.55
    return out / tot

def base_panel(h, w, seed=1):
    n = smoky(h, w, seed)
    yy, xx = np.mgrid[0:h, 0:w]
    mid = np.exp(-(((xx - w / 2) / (w * 0.45)) ** 2 + ((yy - h / 2) / (h * 0.5)) ** 2))
    col = np.array((3, 7, 11), float)[None, None] * (1 - 0.35 * mid[..., None]) + (n[..., None] - 0.5) * np.array((10, 14, 20))
    return np.clip(col, 0, 255)

def bolt_path(r, kind):
    """points from top to bottom of the face, in UP-scaled pixels"""
    W, H = FW * UP, FH * UP
    pts = []; n = 26
    ph = r.uniform(0, math.pi); amp = r.uniform(0.16, 0.26) * W; cx = W * r.uniform(0.38, 0.62)
    for i in range(n + 1):
        y = H * i / n
        x = cx + amp * math.sin(ph + i / n * math.pi * r.uniform(1.4, 2.0)) + r.normal(0, W * 0.025)
        if kind == 'zig' and i > n * 0.6:   # sharp right-angle zigzags near the bottom
            x = pts[-1][0] + (W * 0.09 if i % 2 else -W * 0.07)
            y = pts[-1][1] + H / n * (0.3 if i % 2 else 1.6)
        pts.append((x, min(y, H)))
    return pts

def branches(r, pts, count, curl=1.0):
    out = []
    for _ in range(count):
        k = int(r.integers(2, len(pts) - 3)); x, y = pts[k]
        ang = r.choice([-1, 1]) * r.uniform(0.5, 1.2) + math.pi / 2
        L = r.uniform(70, 140) * curl
        seg = [(x, y)]
        for j in range(5):
            ang += r.uniform(-0.6, 0.6) + 0.25 * curl * (1 if ang > math.pi / 2 else -1)
            x += math.cos(ang) * L / 5 * (1 if r.random() < .5 else 1); y += math.sin(ang) * L / 5
            seg.append((x, y))
        out.append(seg)
    return out

def draw_bolt(d, pts, width0, width1, color, upto=1.0):
    n = max(2, int(len(pts) * upto))
    for i in range(n - 1):
        w = width0 + (width1 - width0) * i / len(pts)
        d.line((pts[i], pts[i + 1]), fill=color, width=max(1, int(w)))
        d.ellipse((pts[i + 1][0] - w / 2, pts[i + 1][1] - w / 2, pts[i + 1][0] + w / 2, pts[i + 1][1] + w / 2), fill=color)

def strike_frames(seed, kind, top_flash):
    r = np.random.default_rng(seed)
    W, H = FW * UP, FH * UP
    pts = bolt_path(r, kind)
    br = branches(r, pts, 5)
    crack = [[(x + r.normal(0, 6), y + r.normal(0, 4)) for x, y in pts]]
    crack += branches(r, pts, 9, 0.8)
    puffs = []   # cartoon clouds: each a cluster of 3-4 overlapping circles hugging the bottom edge
    for k in range(6):
        cx0 = (k + 0.5) / 6 * W + r.normal(0, 18); base_r = r.uniform(44, 66)
        for j in range(r.integers(3, 5)):
            puffs.append((cx0 + r.normal(0, base_r * 0.7), H - r.uniform(base_r * 0.2, base_r * 1.0), base_r * r.uniform(0.6, 1.0)))
    specks = [(r.uniform(0, W), H - r.uniform(0, 160), r.uniform(4, 9)) for _ in range(60)]
    frames = []
    for f in range(PER):
        core = Image.new('L', (W, H), 0); dc = ImageDraw.Draw(core)
        glowm = Image.new('L', (W, H), 0); dg = ImageDraw.Draw(glowm)
        smoke = Image.new('RGBA', (W, H), (0, 0, 0, 0)); ds = ImageDraw.Draw(smoke)
        bolt_a = 0
        if f == 0 and top_flash:
            draw_bolt(dg, pts[:6], 26, 18, 160)
        elif f in (1, 2, 3):
            upto = {1: 0.55, 2: 1.0, 3: 1.0}[f]
            draw_bolt(dc, pts, 18, 6, 255, upto); draw_bolt(dg, pts, 46, 26, 255, upto)
            for b in br: draw_bolt(dc, b, 9, 4, 255); draw_bolt(dg, b, 24, 12, 220)
        elif f in (4, 5, 6):   # crackling thinner branches, fading
            a = {4: 230, 5: 170, 6: 70}[f]
            for b in crack: draw_bolt(dc, [(x + r.normal(0, 3), y) for x, y in b], 5, 2, a); draw_bolt(dg, b, 16, 8, a)
        # smoke puffs: burst at the bottom, then dissolve into specks
        if 2 <= f <= 7:
            g = {2: 0.6, 3: 0.9, 4: 1.05, 5: 1.1, 6: 0.8, 7: 0.45}[f]       # burst, swell, then shrink away
            for (x, y, rad) in puffs:
                rr = rad * g
                ds.ellipse((x - rr - 6, y - rr - 6, x + rr + 6, y + rr + 6), fill=(110, 196, 255, 255))   # light-blue outline
            for (x, y, rad) in puffs:
                rr = rad * g
                ds.ellipse((x - rr, y - rr, x + rr, y + rr), fill=(236, 248, 255, 255))
                ds.ellipse((x - rr * 0.55, y - rr * 0.75, x + rr * 0.15, y - rr * 0.2), fill=(255, 255, 255, 255))   # highlight
        if 6 <= f <= 11:
            a = int(255 * (1 - (f - 6) / 6))
            for (x, y, s) in specks:
                y2 = y - (f - 6) * 6
                ds.rectangle((x, y2, x + s, y2 + s), fill=(200, 235, 255, a))
        frames.append((core, glowm, smoke))
    return frames

def compose(core, glowm, smoke, bg):
    W, H = FW * UP, FH * UP
    g1 = np.asarray(glowm.filter(ImageFilter.GaussianBlur(14)), float)[..., None] / 255
    g2 = np.asarray(glowm.filter(ImageFilter.GaussianBlur(40)), float)[..., None] / 255
    c = np.asarray(core, float)[..., None] / 255
    big = np.asarray(Image.fromarray(bg.astype(np.uint8)).resize((W, H), Image.BICUBIC), float)
    col = big + g2 * np.array((40, 130, 200)) * 0.9 + g1 * np.array((92, 200, 255)) * 1.1
    col = col * (1 - c) + c * np.array((250, 254, 255))
    sm = np.asarray(smoke, float); a = sm[..., 3:] / 255
    col = col * (1 - a) + sm[..., :3] * a
    img = Image.fromarray(np.clip(col, 0, 255).astype(np.uint8)).resize((FW, FH), Image.LANCZOS)
    return np.asarray(img)

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    bg = base_panel(FH, FW)
    inner = base_panel(16 * S, 10 * S, 5) * 0.8
    strikes = [(11, 's', False), (23, 'zig', True), (37, 's', False), (41, 'zig', False), (53, 's', True)]
    allf = []
    for seed, kind, tf in strikes: allf += strike_frames(seed, kind, tf)
    for i, (core, glowm, smoke) in enumerate(allf):
        face = compose(core, glowm, smoke, bg)
        tex = np.zeros((32 * S, 64 * S, 4), np.uint8)
        blk = np.zeros((17 * S, 22 * S, 3)); blk[:] = (3, 7, 11)       # edges / top / bottom
        tex[0:17 * S, 0:22 * S, :3] = blk; tex[0:17 * S, 0:22 * S, 3] = 255
        tex[S:17 * S, S:11 * S, :3] = face                           # outer face (seen from behind)
        tex[S:17 * S, 12 * S:22 * S, :3] = inner.astype(np.uint8)    # inside
        tex[0:S, 0:S] = 0; tex[0:S, 21 * S:22 * S] = 0
        Image.fromarray(tex, 'RGBA').save(f'{OUT}/{i}.png', optimize=True)
        Image.fromarray(face).save(f'/home/claude/cos/lightning/face_{i:03d}.png')
    print('ok', len(allf))

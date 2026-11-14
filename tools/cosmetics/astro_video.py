# Astronaut promo: white voxel astronaut, electric-blue energy, particles, cinematic camera, slow motion.
import os, sys, math, subprocess, numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import gen, render as R
gen.export('/tmp/astro_out')
R.AMB, R.DIF = 0.5, 0.6
W, H, FPS, SS = 1920, 1080, 30, 1.5
BW, BH = int(W * SS), int(H * SS)
OUT = '/home/claude/astro_frames'; os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(3)

# ---------- energy: helical streams + loose particles + ground ring
STREAMS = [dict(r=8.5 + rng.random() * 3.5, ph=rng.random() * 6.28, sp=0.9 + rng.random() * 0.8, y0=26 - rng.random() * 6,
                y1=-10 + rng.random() * 8, tw=rng.random() * 6.28, L=0.9 + rng.random() * 0.8) for _ in range(9)]
PART = [dict(r=6 + rng.random() * 16, a=rng.random() * 6.28, y=-14 + rng.random() * 40, sp=0.2 + rng.random() * 0.6,
             s=0.6 + rng.random() * 1.6, up=0.5 + rng.random() * 2.0) for _ in range(170)]

def pose(t):
    b = math.sin(t * 1.3)
    return dict(head=(3 * math.sin(t * 0.8), 8 * math.sin(t * 0.5), 0), right_arm=(-6 + 4 * b, 0, 10 + 3 * b),
                left_arm=(-4 - 4 * b, 0, -10 - 3 * b), right_leg=(2, 0, 2), left_leg=(-2, 0, -2))

def body_quads(t, lift):
    q = []
    ps = pose(t)
    for name, b in gen.ASTRO.items():
        piv = np.array(b['pivot'], float) + np.array((0, -lift, 0))
        q += R.quads_for(b['parts'], b['tex'], b['texsize'], None, R.M(piv, ps.get(name, (0, 0, 0))))
    return q

def project(p, yaw, pitch, scale, cx, cy):
    Rm = R.rotZYX(pitch, 0, 0) @ R.rotZYX(0, yaw, 0)
    v = Rm @ np.asarray(p, float)
    return cx + v[0] * scale, cy + v[1] * scale, v[2]

def energy_layer(t, yaw, pitch, scale, cx, cy, zb, lift, intensity):
    img = Image.new('RGB', (BW, BH)); d = ImageDraw.Draw(img)
    def vis(x, y, z):
        xi, yi = int(x), int(y)
        if not (0 <= xi < BW and 0 <= yi < BH): return False
        return z < zb[yi, xi] + 0.5
    for s in STREAMS:
        pts = []
        for k in range(48):
            u = k / 47
            a = s['ph'] + t * s['sp'] * 2.2 + u * 6.28 * s['L']
            y = s['y0'] + (s['y1'] - s['y0']) * u - lift
            r = s['r'] * (1 + 0.15 * math.sin(u * 9 + t * 3 + s['tw']))
            jit = 0.6 * math.sin(u * 37 + t * 11 + s['tw']) + 0.4 * math.sin(u * 83 - t * 7)
            pts.append(project((math.cos(a) * (r + jit), y + jit * 0.6, math.sin(a) * (r + jit)), yaw, pitch, scale, cx, cy))
        head = (t * 0.7 + s['tw']) % 1.0
        for k in range(len(pts) - 1):
            u = k / 47
            fade = max(0.0, 1 - abs(u - head) * 2.4) * intensity
            if fade <= 0.02: continue
            (x0, y0, z0), (x1, y1, z1) = pts[k], pts[k + 1]
            if not vis(x0, y0, z0) and not vis(x1, y1, z1): continue
            c = (int(70 * fade), int(170 * fade), int(255 * fade))
            d.line([(x0, y0), (x1, y1)], fill=c, width=max(2, int(4 * SS * fade)))
            if fade > 0.7: d.line([(x0, y0), (x1, y1)], fill=(int(200 * fade), int(235 * fade), 255), width=max(1, int(1.5 * SS)))
    for p in PART:
        a = p['a'] + t * p['sp']
        y = ((p['y'] - t * p['up'] * 2 + 30) % 44) - 18 - lift
        x, yy, z = project((math.cos(a) * p['r'], y, math.sin(a) * p['r']), yaw, pitch, scale, cx, cy)
        if not vis(x, yy, z): continue
        sz = p['s'] * SS * scale / 22
        tw = (0.5 + 0.5 * math.sin(t * 4 + p['a'] * 7)) * intensity
        d.rectangle([x - sz, yy - sz, x + sz, yy + sz], fill=(int(90 * tw), int(190 * tw), int(255 * tw)))
    # ground ring at the feet
    ring = []
    for k in range(97):
        a = k / 96 * 6.28 + t * 0.6
        rr = 13 + 0.9 * math.sin(k * 1.7 + t * 6) + 0.5 * math.sin(k * 5.3 - t * 9)
        ring.append(project((math.cos(a) * rr, 24.2, math.sin(a) * rr), yaw, pitch, scale, cx, cy))
    for k in range(96):
        (x0, y0, z0), (x1, y1, z1) = ring[k], ring[k + 1]
        if vis(x0, y0, z0) or vis(x1, y1, z1):
            d.line([(x0, y0), (x1, y1)], fill=(int(60 * intensity), int(160 * intensity), int(255 * intensity)), width=int(3 * SS))
    return np.asarray(img).astype(float)

def background(t, yaw):
    yy = np.linspace(0, 1, BH)[:, None]; xx = np.linspace(-1, 1, BW)[None, :]
    glow = np.exp(-((xx) ** 2 / 0.35 + (yy - 0.62) ** 2 / 0.12))
    base = np.dstack([4 + 10 * glow, 8 + 26 * glow, 16 + 60 * glow]) * np.ones((BH, BW, 1))
    # sci-fi panels: vertical light strips that parallax with yaw
    for i in range(14):
        x = ((i * 0.16 - yaw / 300 * 1.5) % 2.24) - 1.12
        px = int((x + 1) / 2 * BW)
        if 0 < px < BW - 8:
            fl = 0.6 + 0.4 * math.sin(t * 2 + i)
            base[int(BH * 0.08):int(BH * 0.62), px:px + int(3 * SS)] += np.array((10, 40, 90)) * fl
    # floor: perspective grid
    horizon = int(BH * 0.64)
    fy = np.arange(horizon, BH)
    for k in range(1, 18):
        y = horizon + int((BH - horizon) * (k / 18) ** 2.2)
        base[y:y + 1] += np.array((6, 22, 48))
    for k in range(-14, 15):
        x0 = BW / 2 + k * BW * 0.012; x1 = BW / 2 + k * BW * 0.16 - yaw * 3
        for y in range(horizon, BH, 2):
            u = (y - horizon) / (BH - horizon)
            x = int(x0 + (x1 - x0) * u)
            if 0 <= x < BW: base[y, x] += np.array((5, 18, 40)) * u
    return base

def frame(t, yaw, pitch, scale, cx, cy, lift, intensity, title=0.0):
    q = body_quads(t, lift)
    col, gl, mask = R.render(q, BW, BH, yaw, pitch, scale, cx, cy)
    zb = R.LAST_ZB
    # blue rim light on the astronaut: brighten edges of the mask
    m = Image.fromarray((mask * 255).astype(np.uint8))
    edge = np.asarray(m.filter(ImageFilter.GaussianBlur(4 * SS))).astype(float) / 255
    rim = np.clip(mask - edge, 0, 1) * 2.4
    col = col * (0.9 + 0.1 * np.array((0.9, 0.95, 1.2))) + rim[..., None] * np.array((60, 140, 255))
    bg = background(t, yaw)
    # reflection on the floor
    refl = np.flipud(np.where(mask[..., None] > 0, col, 0))
    horizon_px = int(cy + (24.2 - lift) * scale * math.cos(math.radians(pitch)))
    shift = 2 * horizon_px - BH
    ref = np.zeros_like(bg)
    if -BH < shift < BH:
        if shift >= 0: ref[shift:] = refl[:BH - shift]
        else: ref[:BH + shift] = refl[-shift:]
    yy = np.arange(BH)[:, None]
    fall = np.clip(1 - (yy - horizon_px) / (BH * 0.25), 0, 1) * (yy > horizon_px)
    bg = bg + ref * 0.22 * fall[..., None]
    en = energy_layer(t, yaw, pitch, scale, cx, cy, zb, lift, intensity)
    img = np.where(mask[..., None] > 0, col, bg)
    e = Image.fromarray(np.clip(en, 0, 255).astype(np.uint8))
    bloom = np.asarray(e.filter(ImageFilter.GaussianBlur(6 * SS))).astype(float) * 1.6 + np.asarray(e.filter(ImageFilter.GaussianBlur(22 * SS))).astype(float) * 1.2
    img = img + en + bloom
    # blue light spill on the suit from the energy
    img = np.where(mask[..., None] > 0, img + np.asarray(e.filter(ImageFilter.GaussianBlur(30 * SS))).astype(float) * 0.9, img)
    # vignette + letterbox
    vy = np.linspace(-1, 1, BH)[:, None]; vx = np.linspace(-1, 1, BW)[None, :]
    img *= (1 - 0.45 * (vx ** 2 * 0.6 + vy ** 2 * 0.8))[..., None]
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((W, H), Image.LANCZOS)
    d = ImageDraw.Draw(out)
    bar = int(H * 0.09)
    d.rectangle([0, 0, W, bar], fill=(0, 0, 0)); d.rectangle([0, H - bar, W, H], fill=(0, 0, 0))
    if title > 0:
        F = ImageFont.truetype('/home/claude/fonts/ttf/orbitron.ttf', 96); F2 = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 34)
        a = int(255 * title)
        layer = Image.new('RGBA', (W, H), (0, 0, 0, 0)); ld = ImageDraw.Draw(layer)
        t1 = 'ASTRONAUT'; t2 = 'BEYOND THE LIMIT'
        ld.text((W / 2 - ld.textlength(t1, font=F) / 2, H * 0.14), t1, font=F, fill=(240, 248, 255, a))
        ld.text((W / 2 - ld.textlength(t2, font=F2) / 2, H * 0.14 + 120), t2, font=F2, fill=(120, 190, 255, a))
        g = layer.filter(ImageFilter.GaussianBlur(10))
        out = out.convert('RGBA'); out.alpha_composite(g); out.alpha_composite(layer); out = out.convert('RGB')
    return out

def ease(u): return u * u * (3 - 2 * u)

# shots: (seconds, function u-> camera)  time t runs at 0.5x for slow motion
SHOTS = [
    (4.0, lambda u: dict(yaw=-60 + 30 * ease(u), pitch=-14 + 6 * ease(u), scale=(15 + 5 * ease(u)) * SS, cy=BH * 0.3, lift=0, inten=0.4 + 0.6 * u)),
    (4.0, lambda u: dict(yaw=-30 + 200 * ease(u), pitch=-6, scale=20 * SS, cy=BH * 0.3, lift=0, inten=1.0)),
    (3.0, lambda u: dict(yaw=-25 + 30 * u, pitch=2, scale=(46 - 6 * u) * SS, cy=BH * 0.5 + 4.3 * (46 - 6 * u) * SS, lift=0, inten=1.0)),
    (4.0, lambda u: dict(yaw=-20 - 10 * u, pitch=-10, scale=(18 - 2 * u) * SS, cy=BH * 0.4, lift=3 * ease(u), inten=1.0 + 0.4 * u, title=min(1, max(0, (u - 0.25) * 2.5)))),
]
if __name__ == '__main__':
    only = int(sys.argv[1]) if len(sys.argv) == 2 else None
    rng_ = (int(sys.argv[1]), int(sys.argv[2])) if len(sys.argv) == 3 else None
    encode_only = len(sys.argv) == 2 and sys.argv[1] == '-1'
    n = 0; T = 0.0
    files = []
    for si, (dur, cam) in enumerate(SHOTS):
        frames = int(dur * FPS)
        for k in range(frames):
            u = k / max(1, frames - 1)
            c = cam(u)
            t = T * 0.5
            fn = f'{OUT}/{n:04d}.jpg'
            if (rng_ and rng_[0] <= n < rng_[1]) or (not rng_ and not encode_only and (only is None or only == n)):
                im = frame(t, c['yaw'], c['pitch'], c['scale'], BW / 2, c['cy'], c['lift'], c['inten'], c.get('title', 0))
                im.save(fn, quality=92)
                if only is not None: print(fn); sys.exit()
            n += 1; T += 1 / FPS
    if rng_: sys.exit()
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(FPS), '-i', f'{OUT}/%04d.jpg', '-vf',
                    'fade=t=in:st=0:d=0.8,fade=t=out:st=14.2:d=0.8', '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '18',
                    '-pix_fmt', 'yuv420p', '/home/claude/astronaut_promo.tmp.mp4'], check=True)
    os.replace('/home/claude/astronaut_promo.tmp.mp4', '/home/claude/astronaut_promo.mp4')
    print('done', n)

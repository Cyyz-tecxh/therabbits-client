# Black Nebula cape: 16-frame animated, vanilla cape layout (64x32) at 16x = 1024x512.
import numpy as np, os
from PIL import Image, ImageFilter
S = 16; FR = 16
rng = np.random.default_rng(42)

def fbm(h, w, base, oct=6, seed=0):
    r = np.random.default_rng(seed); out = np.zeros((h, w)); amp = 1; tot = 0
    for o in range(oct):
        gh, gw = int(h / base * 2 ** o) + 3, int(w / base * 2 ** o) + 3
        g = r.random((gh, gw)).astype(np.float32)
        im = Image.fromarray((g * 255).astype(np.uint8)).resize((w + int(w / gw * 2), h + int(h / gh * 2)), Image.BICUBIC)
        out += np.asarray(im)[:h, :w] / 255.0 * amp; tot += amp; amp *= 0.55
    return out / tot

W, H = 22 * S, 17 * S   # the whole cape block (outer + inner + edges)
pad = 64
big_h, big_w = H + pad * 2, W + pad * 2
n1 = fbm(big_h, big_w, 120, 6, 1); n2 = fbm(big_h, big_w, 60, 5, 2); n3 = fbm(big_h, big_w, 200, 4, 3)
# smoke: ridged + soft clouds
ridge = 1 - np.abs(n2 * 2 - 1)
smoke = np.clip((n1 - 0.42) * 2.4, 0, 1) ** 1.6 * 0.75 + np.clip(ridge - 0.55, 0, 1) * 1.2 * np.clip(n3 - 0.35, 0, 1) * 2
dust = np.clip((fbm(big_h, big_w, 18, 3, 4) - 0.55) * 4, 0, 1) * smoke
# stars
stars = []
for i in range(260):
    stars.append((rng.integers(0, big_w), rng.integers(0, big_h), rng.random() ** 3, rng.random() * 6.28, 0.5 + rng.random()))

frames = []
for f in range(FR):
    t = f / FR
    off = int(round(np.sin(t * 2 * np.pi) * 6)), int(round(np.cos(t * 2 * np.pi) * 4))
    sm = smoke[pad + off[1]:pad + off[1] + H, pad + off[0]:pad + off[0] + W]
    du = dust[pad - off[1]:pad - off[1] + H, pad - off[0]:pad - off[0] + W]
    base = np.array((10, 10, 12), float)
    col = base + sm[..., None] * np.array((118, 120, 130)) + du[..., None] * np.array((70, 72, 80))
    starimg = np.zeros((H, W))
    for (x, y, b, ph, sz) in stars:
        x -= pad; y -= pad
        if not (1 <= x < W - 1 and 1 <= y < H - 1): continue
        tw = 0.45 + 0.55 * (0.5 + 0.5 * np.sin(ph + t * 2 * np.pi * (1 + (ph > 3))))
        v = (0.35 + 0.65 * b) * tw
        starimg[y, x] = max(starimg[y, x], v)
        if b > 0.45:  # brighter stars get a tiny cross
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)): starimg[y + dy, x + dx] = max(starimg[y + dy, x + dx], v * 0.45)
    glow = np.asarray(Image.fromarray((starimg * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2.2))) / 255.0
    col = col + starimg[..., None] * 235 + glow[..., None] * np.array((60, 62, 70))
    # vignette toward the cape edges
    yy, xx = np.mgrid[0:H, 0:W]
    col *= (1 - 0.35 * (np.abs(yy / H - 0.5) * 2) ** 3)[..., None]
    rgb = np.clip(col, 0, 255).astype(np.uint8)
    tex = np.zeros((32 * S, 64 * S, 4), np.uint8)
    tex[0:H, 0:W, :3] = rgb; tex[0:H, 0:W, 3] = 255
    tex[0:S, 0:S] = 0; tex[0:S, 21 * S:22 * S] = 0   # vanilla layout corners are empty
    frames.append(tex)
os.makedirs('/home/claude/cos/nebula/black_nebula', exist_ok=True)
for i, t in enumerate(frames):
    Image.fromarray(t, 'RGBA').save(f'/home/claude/cos/nebula/black_nebula/{i}.png')
# flat preview of the outer face
Image.fromarray(frames[0][S:17 * S, S:11 * S, :3]).resize((320, 512), Image.NEAREST).save('/home/claude/cos/nebula/preview.png')
print('ok')

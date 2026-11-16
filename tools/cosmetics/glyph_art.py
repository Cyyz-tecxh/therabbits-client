# Red "decoding terminal" art: a fixed 13x5 grid of invented pixel glyphs that glitch-swap (shared by the shield).
import numpy as np
from PIL import Image, ImageFilter
GW, GH = 5, 9
def glyph(r):
    g = np.zeros((GH, GW), bool)
    OPTS = [[2], [0, 4], [1, 3], [0], [4], [2, 4]]
    for sx in OPTS[r.choice(len(OPTS), p=[.3, .2, .15, .1, .1, .15])]:
        g[r.integers(0, 3):r.integers(6, 9) + 1, sx] = True
    for _ in range(r.integers(2, 5)):
        y = r.integers(0, GH); a = r.integers(0, 3); g[y, a:r.integers(a + 1, GW) + 1] = True
    for _ in range(r.integers(0, 3)):
        x = r.integers(0, GW); y = r.integers(0, GH - 2); g[y:y + 2, x] = True
        if x + 1 < GW: g[y + 1, x + 1] = True
    for _ in range(r.integers(0, 3)): g[r.integers(0, GH), r.integers(0, GW)] = True
    if r.random() < .3: g[::2, r.integers(0, GW)] = True
    return g

def frames(n, W, H, seed=7, cols=13, rows=5, px=3):
    """n looping frames of the terminal at W x H (RGB uint8) + a glow mask for each."""
    r = np.random.default_rng(seed)
    cells = [[glyph(r) for _ in range(rows)] for _ in range(cols)]
    period = r.integers(1, 3, cols); phase = r.integers(0, 3, cols)
    blur_at = {n // 2}
    yy, xx = np.mgrid[0:H, 0:W]
    edge = np.minimum.reduce([xx, W - 1 - xx, yy, H - 1 - yy]).astype(float)
    base = np.zeros((H, W, 3)); base[:] = (13, 10, 11)
    base += np.exp(-edge / (H * 0.06))[..., None] * np.array([80, 6, 10])
    x0, y0 = W * 0.04, H * 0.07
    cw, ch = (W - 2 * x0) / cols, (H - 2 * y0) / rows
    out = []
    for f in range(n):
        for c in range(cols):
            if (f + phase[c]) % period[c] == 0:
                for rr in range(rows):
                    if r.random() < .85: cells[c][rr] = glyph(r)
        layer = np.zeros((H, W))
        for c in range(cols):
            for rr in range(rows):
                gx = int(x0 + c * cw + (cw - GW * px) / 2); gy = int(y0 + rr * ch + (ch - GH * px) / 2)
                layer[gy:gy + GH * px, gx:gx + GW * px] = np.maximum(layer[gy:gy + GH * px, gx:gx + GW * px], np.kron(cells[c][rr], np.ones((px, px))))
        li = Image.fromarray((layer * 255).astype(np.uint8))
        halo = np.asarray(li.filter(ImageFilter.GaussianBlur(px * 1.1)), float) / 255
        img = base * (1 - layer[..., None]) + layer[..., None] * np.array([255, 42, 42]) + halo[..., None] * np.array([110, 10, 12])
        if f in blur_at:
            img = np.asarray(Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(px)), float) * 0.6
        for _ in range(int(r.integers(8, 16))):
            sx, sy = int(r.integers(2, W - 2)), int(r.integers(2, H - 2))
            img[sy:sy + px // 2 + 1, sx:sx + px // 2 + 1] = (255, 255, 255) if r.random() < .6 else (255, 200, 215)
        L = int(r.integers(W // 8, W // 2)); s0 = int(r.integers(W // 8, W - W // 8 - L // 2)); x = s0
        while x < min(W - 2, s0 + L):
            seg = int(r.integers(W // 70 + 2, W // 14)); img[0:max(1, px), x:min(W - 2, x + seg)] = (245, 245, 245); x += seg + int(r.integers(1, 4))
        out.append((np.clip(img, 0, 255).astype(np.uint8), (layer > 0) | (img.max(-1) > 230)))
    return out

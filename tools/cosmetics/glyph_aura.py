# Glyph Aura: thin white / grey pixel glyphs that flicker in and out around the player (positions re-rolled in Java)
import os, sys, math, json, copy, subprocess, numpy as np
from PIL import Image, ImageDraw, ImageFilter
import gen, render as R, ideas as I
from gen import P
NG, NPARTS = 14, 60
def sprite(k):
    r = np.random.default_rng(100 + k)
    g = np.zeros((8, 5), bool)
    kind = k % 7
    if kind == 0: g[:, 1] = g[:, 3] = True
    elif kind == 1: g[0, :] = True; g[:, 2] = True
    elif kind == 2: g[0, :3] = True; g[:, 0] = True; g[7, :3] = True
    elif kind == 3: g[2:, 2] = True; g[2, 2:5] = True; g[0, 4] = True
    elif kind == 4: g[3, :4] = True; g[5, 1:5] = True
    elif kind == 5: g[0, 1:4] = True; g[1:5, 4] = True; g[5, 1:4] = True; g[6:8, 1] = True
    else:
        y = 0
        for x in range(5): g[y, x] = True; y = min(7, y + int(r.integers(1, 3)))
    if k >= 7: g[int(r.integers(0, 8)), int(r.integers(0, 5))] = True; g = g[:, ::-1] if k % 2 else g
    return g
def make_mat(k):
    col = (245, 245, 245, 255) if k % 3 else (150, 150, 150, 255)
    def f(d, gd, W, H, face, im, gl):
        im.paste((0, 0, 0, 0), (0, 0, W, H))
        K = max(1, W // 8)                      # painter draws at 6x and shrinks; draw K x K blocks per pixel
        if face not in ('front', 'back') or W < 5 * K or H < 8 * K: return
        g = sprite(k)
        if face == 'back': g = g[:, ::-1]
        d = ImageDraw.Draw(im)
        ox, oy = (W // K - 5) // 2 * K, (H // K - 8) // 2 * K
        for y in range(8):
            for x in range(5):
                if g[y, x]: d.rectangle((ox + x * K, oy + y * K, ox + x * K + K - 1, oy + y * K + K - 1), fill=col)
    return f
for k in range(NG): I.IMG['gl%d' % k] = make_mat(k)
parts = []
for i in range(NPARTS):
    w, h = 1, 2          # whole texels: the glyph (5x8 px) sits centred in an 8x16 face
    parts.append(P('g%d' % i, (-w / 2, -h / 2, -0.01), (w, h, 0.02), 'img:gl%d' % (i % NG), pivot=(0, 0, 0)))
SPARK = ["..#..", ".##..", ".#...", "####.", "..#..", ".##..", ".#...", "#...."]
def _spark(d, gd, W, H, face, im, gl):
    im.paste((0, 0, 0, 0), (0, 0, W, H))
    K = max(1, W // 8)
    if face not in ('front', 'back') or W < 5 * K or H < 8 * K: return
    dr = ImageDraw.Draw(im); ox, oy = (W // K - 5) // 2 * K, (H // K - 8) // 2 * K
    for y, row in enumerate(SPARK):
        for x, ch in enumerate(row[::-1] if face == 'back' else row):
            if ch == '#': dr.rectangle((ox + x * K, oy + y * K, ox + x * K + K - 1, oy + y * K + K - 1), fill=(255, 255, 255, 255))
I.IMG['glspark'] = _spark
for i in range(2):
    parts.append(P('k%d' % i, (-0.5, -1, -0.01), (1, 2, 0.02), 'img:glspark', pivot=(0, 0, 0)))
MODEL = dict(attach='aura', parts=parts)

def hash01(a, b):
    v = math.sin(a * 12.9898 + b * 78.233) * 43758.5453
    return v - math.floor(v)
def place(i, t, facing):
    """Same maths as CosmeticsLayer.animateGlyphs: each glyph lives a short while at a random spot, then jumps."""
    rate = 2.5 + hash01(i, 1) * 3.5
    life = t * rate + hash01(i, 2) * 10
    slot = math.floor(life); frac = life - slot
    vis = frac < 0.72 and hash01(i, slot + 3) > 0.12
    a = hash01(i, slot) * 6.2832
    y = -11 + hash01(slot, i + 7) * 36
    near = hash01(i + 5, slot) < 0.25   # some sit right on the body (they look faint there)
    rad = (2.6 if near else 4.6) + hash01(slot + 2, i) * (1.0 if near else 3.6)
    return vis, (math.sin(a) * rad, y, math.cos(a) * rad), facing

def spark(i, t, facing):
    """A single-frame white spark near the character's lower right (as seen by the viewer)."""
    slot = math.floor(t * 30)
    vis = hash01(i + 40, slot) < 0.05
    lx, ly, lz = 5.2 + hash01(slot, i + 50) * 2.4, 13 + hash01(slot + 3, i) * 9, -2.5
    a = math.radians(facing)
    return vis, (lx * math.cos(a) + lz * math.sin(a), ly, -lx * math.sin(a) + lz * math.cos(a))

def frame_parts(t, facing):
    ps = copy.deepcopy(parts)
    for i, p in enumerate(ps):
        if p['name'].startswith('k'):
            vis, pos = spark(i, t, facing); p['pivot'] = list(pos); p['rot'] = [0, facing, 0]
            if not vis: p['size'] = [1e-4] * 3; p['frm'] = [0, 0, 0]
            continue
        vis, pos, fy = place(i, t, facing)
        p['pivot'] = list(pos); p['rot'] = [0, fy, 0]
        if not vis: p['size'] = [1e-4] * 3; p['frm'] = [0, 0, 0]
    return ps

def shot(t, yaw, W=720, H=900, scale=22, cy=0.3):
    SS = 2; gen.S = 8
    q = []
    for name, b in gen.BODY.items():
        q += R.quads_for(b['parts'], b['tex'], b['texsize'], None, R.M(b['pivot'], (0, 0, 0)))
    ATL = getattr(shot, 'atl', None)
    if ATL is None: shot.atl = ATL = gen.build_atlas(parts)
    tex, glow, ts = ATL
    ps = frame_parts(t, -yaw)
    q += R.quads_for(ps, tex, ts, None, np.eye(4))
    col, gl, mask = R.render(q, W * SS, H * SS, yaw, 2, scale * SS, W / 2 * SS, H * cy * SS)
    bg = np.zeros_like(col); bg[:] = (22, 22, 22)
    img = np.where(mask[..., None] > 0, col, bg)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((W, H), Image.LANCZOS)

if __name__ == '__main__':
    R.AMB, R.DIF = 0.62, 0.5
    gen.export('/tmp/sign_body')
    if sys.argv[1] == 'still':
        o = Image.new('RGB', (720 * 3, 900))
        for k, (t, yaw) in enumerate(((1.0, 0), (2.3, 30), (3.7, -160))): o.paste(shot(t, yaw), (k * 720, 0))
        o.save('/home/claude/glyph_aura_design.png')
    elif sys.argv[1] == 'export':
        out = '/home/claude/repo/src/main/resources/assets/therabbits'
        tex, glow, (tw, th) = gen.build_atlas(parts)
        Image.fromarray(tex, 'RGBA').save(f'{out}/textures/cosmetics/glyph_aura.png')
        js = dict(attach='aura', texture=[tw, th], glow=False,
                  parts=[dict(name=p['name'], parent=p['parent'], pivot=p['pivot'], rot=p['rot'], **{'from': p['frm']}, size=p['size'], uv=p['uv']) for p in parts])
        open(f'{out}/cosmetics/glyph_aura.json', 'w').write(json.dumps(js, indent=1)); print('exported', tw, th)
    else:
        os.makedirs('/tmp/gav', exist_ok=True)
        for f in range(int(sys.argv[1]), int(sys.argv[2])):
            shot(f / 30, -30 * math.sin(f / 300 * 2 * math.pi)).save(f'/tmp/gav/{f:04d}.jpg', quality=93)

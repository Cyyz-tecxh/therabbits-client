# NULL//PROTOCOL aura (id glyph_aura): monochrome pixel glyphs that orbit, drift, flicker, sometimes form a ring,
# and break into pixel fragments; tiny white sparks and pixel bursts. Motion maths is mirrored in CosmeticsLayer.
import os, sys, math, json, copy, numpy as np
from PIL import Image, ImageDraw
import gen, render as R, ideas as I
from gen import P
NG, NGLYPH, NFRAG, NSPARK = 24, 90, 24, 4
TONES = [(255, 255, 255, 255), (170, 170, 170, 255), (119, 119, 119, 255)]
def sprite(k):
    r = np.random.default_rng(300 + k)
    g = np.zeros((8, 5), bool)
    kind = k % 12
    if kind == 0: g[:, 1] = g[:, 3] = True                                   # ||
    elif kind == 1: g[0, :] = True; g[:, 2] = True                           # T
    elif kind == 2: g[0, :3] = True; g[:, 0] = True; g[7, :3] = True          # [
    elif kind == 3: g[2:, 2] = True; g[2, 2:5] = True; g[0, 4] = True         # hook
    elif kind == 4: g[3, :4] = True; g[5, 1:5] = True                        # dashes
    elif kind == 5: g[0, 1:4] = True; g[1:5, 4] = True; g[5, 1:4] = True; g[6:8, 1] = True   # curl
    elif kind == 6: g[0, :] = True; g[:, 0] = True; g[4, 2:5] = True; g[4:8, 4] = True       # rune
    elif kind == 7: g[1, 2] = g[3, 0] = g[3, 4] = g[5, 2] = g[7, 2] = True; g[3, 2] = True   # fragmented cross
    elif kind == 8: g[:, 2] = True; g[2, 0:2] = True; g[5, 3:5] = True       # stepped rune
    elif kind == 9: g[0, 0] = g[1, 1] = g[2, 2] = g[3, 3] = g[4, 4] = True; g[4:8, 4] = True; g[7, 0:4] = True
    elif kind == 10: g[1:7, 0] = g[1:7, 4] = True; g[1, 1:4] = True; g[6, 2] = True          # gate
    else:
        y = 0
        for x in range(5): g[y, x] = True; y = min(7, y + int(r.integers(1, 3)))
    if k >= 12: g = g[:, ::-1]; g[int(r.integers(0, 8)), int(r.integers(0, 5))] = True
    return g
FRAGS = [np.array(m, bool) for m in ([[1, 0], [0, 1]], [[1, 1]], [[1], [1]], [[1, 0, 1]], [[0, 1, 0], [1, 0, 1]], [[1]])]
SPARK = ["..#..", ".##..", ".#...", "####.", "..#..", ".##..", ".#...", "#...."]
def pix_mat(mask, col, flip_back=True):
    def f(d, gd, W, H, face, im, gl):
        im.paste((0, 0, 0, 0), (0, 0, W, H))
        K = max(1, W // 8); h, w = mask.shape
        if face not in ('front', 'back') or W < w * K or H < h * K: return
        m = mask[:, ::-1] if (face == 'back' and flip_back) else mask
        dr = ImageDraw.Draw(im); ox, oy = (W // K - w) // 2 * K, (H // K - h) // 2 * K
        for y in range(h):
            for x in range(w):
                if m[y, x]: dr.rectangle((ox + x * K, oy + y * K, ox + x * K + K - 1, oy + y * K + K - 1), fill=col)
    return f
parts = []
for i in range(NGLYPH):
    key = 'np_g%d' % i
    I.IMG[key] = pix_mat(sprite(i % NG), TONES[(i * 7) % 3])
    parts.append(P('g%d' % i, (-0.5, -1, -0.01), (1, 2, 0.02), 'img:' + key))
for j in range(NFRAG):
    key = 'np_q%d' % j
    I.IMG[key] = pix_mat(FRAGS[j % len(FRAGS)], TONES[j % 3])
    parts.append(P('q%d' % j, (-0.5, -0.5, -0.01), (1, 1, 0.02), 'img:' + key))
I.IMG['np_spark'] = pix_mat(np.array([[c == '#' for c in row] for row in SPARK]), TONES[0])
for k in range(NSPARK):
    parts.append(P('k%d' % k, (-0.5, -1, -0.01), (1, 2, 0.02), 'img:np_spark'))
MODEL = dict(attach='aura', parts=parts)

def h(a, b):
    v = math.sin(a * 12.9898 + b * 78.233) * 43758.5453
    return v - math.floor(v)
def smooth(x): x = max(0.0, min(1.0, x)); return x * x * (3 - 2 * x)

def glyph(i, t, density=0.8, spread=1.3):
    """-> (visible, x, y, z, frac)"""
    if h(i, 99) > density: return False, 0, 0, 0, 0
    rate = 0.6 + h(i, 1) * 0.8
    life = t * rate + h(i, 2) * 10
    slot = math.floor(life); frac = life - slot
    vis = frac < 0.85 and h(i, slot + 3) > 0.15 and h(i, math.floor(t * 12) + 5) > 0.06   # flicker
    a0 = h(i, slot) * 6.2832; dirn = 1 if i % 2 else -1
    a = a0 + t * (0.18 + h(i, 4) * 0.14) * dirn                       # gentle orbit
    near = h(i + 5, slot) < 0.22
    rad = (3.0 + h(slot + 2, i) * 1.2) if near else (4.6 + h(slot + 2, i) * 4.0) * spread
    y = -10 + h(slot, i + 7) * 35 + math.sin(t * 0.8 + i) * 0.8 - frac * 1.4   # drift
    x, z = math.sin(a) * rad, math.cos(a) * rad
    ph = t % 9.0                                                       # every 9 s some glyphs form a ring
    k = smooth((ph - 5.6) / 0.6) * (1 - smooth((ph - 7.6) / 0.6))
    if k > 0 and i % 3 == 0:
        ra = i / NGLYPH * 6.2832 * 3 + t * 0.6
        x += (math.sin(ra) * 6.2 * spread - x) * k; z += (math.cos(ra) * 6.2 * spread - z) * k; y += (9 - y) * k
        vis = vis or k > 0.5
    return vis, x, y, z, frac

def frag(j, t, density=0.8, spread=1.3):
    """fragments of glyph j: show just after it vanishes, drifting apart"""
    gi = j * 3 % NGLYPH
    if h(gi, 99) > density: return False, 0, 0, 0
    rate = 0.6 + h(gi, 1) * 0.8
    life = t * rate + h(gi, 2) * 10
    slot = math.floor(life); frac = life - slot
    if not (0.85 <= frac < 0.97) or h(gi, slot + 3) <= 0.15: return False, 0, 0, 0
    _, x, y, z, _ = glyph(gi, (slot + 0.84 - h(gi, 2) * 10) / rate, 1.0, spread)
    e = (frac - 0.85) / 0.12
    ang = j * 2.4
    return True, x + math.cos(ang) * e * 1.6, y - e * 1.2 + math.sin(ang) * e, z + math.sin(ang) * e * 1.6

def spark(k, t, spread=1.3):
    slot = math.floor(t * 30)
    if h(k + 40, slot) > 0.05: return False, 0, 0, 0
    a = h(slot, k + 50) * 6.2832; rad = (4.5 + h(slot + 3, k) * 3) * spread
    return True, math.sin(a) * rad, -6 + h(slot + 7, k) * 28, math.cos(a) * rad

def frame_parts(t, facing, density=0.8, spread=1.3):
    ps = copy.deepcopy(parts)
    for p in ps:
        n = p['name']; idx = int(n[1:])
        if n[0] == 'g': vis, x, y, z, _ = glyph(idx, t, density, spread)
        elif n[0] == 'q': vis, x, y, z = frag(idx, t, density, spread)
        else: vis, x, y, z = spark(idx, t, spread)
        p['pivot'] = [x, y, z]; p['rot'] = [0, facing, 0]
        if not vis: p['size'] = [1e-4] * 3; p['frm'] = [0, 0, 0]
    return ps

def shot(t, yaw, W=720, H=900, scale=20, cy=0.32):
    SS = 2; gen.S = 8
    if not hasattr(shot, 'atl'): shot.atl = gen.build_atlas(parts)
    tex, glow, ts = shot.atl
    q = []
    for name, b in gen.BODY.items():
        q += R.quads_for(b['parts'], b['tex'], b['texsize'], None, R.M(b['pivot'], (0, 0, 0)))
    q += R.quads_for(frame_parts(t, -yaw), tex, ts, None, np.eye(4))
    col, gl, mask = R.render(q, W * SS, H * SS, yaw, 2, scale * SS, W / 2 * SS, H * cy * SS)
    bg = np.zeros_like(col); bg[:] = (22, 22, 22)
    return Image.fromarray(np.clip(np.where(mask[..., None] > 0, col, bg), 0, 255).astype(np.uint8)).resize((W, H), Image.NEAREST)

if __name__ == '__main__':
    R.AMB, R.DIF = 0.62, 0.5
    gen.export('/tmp/sign_body')
    if sys.argv[1] == 'still':
        o = Image.new('RGB', (720 * 3, 900))
        for k, (t, yaw) in enumerate(((2.0, 0), (6.6, 25), (11.3, -160))): o.paste(shot(t, yaw), (k * 720, 0))
        o.save('/home/claude/null_protocol_design.png')
    elif sys.argv[1] == 'export':
        out = '/home/claude/repo/src/main/resources/assets/therabbits'
        gen.S = 8
        tex, glow, (tw, th) = gen.build_atlas(parts)
        Image.fromarray(tex, 'RGBA').save(f'{out}/textures/cosmetics/glyph_aura.png')
        js = dict(attach='aura', texture=[tw, th], glow=False,
                  parts=[dict(name=p['name'], parent=p['parent'], pivot=p['pivot'], rot=p['rot'], **{'from': p['frm']}, size=p['size'], uv=p['uv']) for p in parts])
        open(f'{out}/cosmetics/glyph_aura.json', 'w').write(json.dumps(js, indent=1)); print('exported', tw, th, len(parts))
    else:
        os.makedirs('/tmp/npv', exist_ok=True)
        for f in range(int(sys.argv[1]), int(sys.argv[2])):
            shot(f / 30, -25 * math.sin(f / 300 * 2 * math.pi)).save(f'/tmp/npv/{f:04d}.jpg', quality=93)

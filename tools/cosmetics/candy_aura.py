# Candy Cane aura: striped voxel candy canes (red, green, purple, teal) popping up at random spots around the player.
import os, sys, math, json, copy, numpy as np
from PIL import Image, ImageDraw
import gen, render as R, ideas as I
from gen import P
NCANE = 12
COLS = {'red': (206, 34, 46), 'green': (44, 168, 66), 'purple': (178, 44, 186), 'teal': (34, 168, 196), 'white': (244, 244, 246)}
def solid(rgb):
    def f(d, gd, W, H, face, im, gl):
        sh = {'top': 1.12, 'bottom': 0.7, 'front': 1.0, 'back': 0.92, 'left': 0.84, 'right': 0.88}.get(face, 1)
        c = tuple(int(min(255, v * sh)) for v in rgb)
        d.rectangle((0, 0, W, H), fill=c + (255,))
        e = max(1, W // 8)   # slight voxel bevel: lighter top edge, darker bottom edge
        d.rectangle((0, 0, W, e - 1), fill=tuple(int(min(255, v * 1.12)) for v in c) + (255,))
        d.rectangle((0, H - e, W, H), fill=tuple(int(v * 0.8) for v in c) + (255,))
    return f
for k, v in COLS.items(): I.IMG['cc_' + k] = solid(v)
S = 1.2
# stick: 5 segments upward, then a hook curling over to the right and down (cube centres, in units)
SEG = [(0, -S * j) for j in range(5)] + [(0.3, -6.0), (1.3, -6.6), (2.3, -6.0), (2.6, -4.9), (2.6, -3.8)]   # (centre x, bottom y)
parts = []
for i in range(NCANE):
    col = ['red', 'green', 'purple', 'teal'][i % 4]
    root = 'cc%d' % i
    for j, (x, y) in enumerate(SEG):
        mat = 'img:cc_' + (col if j % 2 == 0 else 'white')
        name = root if j == 0 else '%ss%d' % (root, j)
        parts.append(P(name, (x - S / 2, y - S, -S / 2), (S, S, S), mat, pivot=(0, 0, 0), parent=None if j == 0 else root))
MODEL = dict(attach='aura', parts=parts)
def hash01(a, b):
    v = math.sin(a * 12.9898 + b * 78.233) * 43758.5453
    return v - math.floor(v)
def place(i, t):
    """Same maths as CosmeticsLayer.animateCandy."""
    rate = 1.4 + hash01(i, 11) * 1.6
    life = t * rate + hash01(i, 12) * 10
    slot = math.floor(life); frac = life - slot
    vis = frac < 0.8 and hash01(i, slot + 13) > 0.3
    a = hash01(i, slot + 17) * 6.2832
    rad = 4.8 + hash01(slot + 19, i) * 3.4
    y = -8 + hash01(slot, i + 23) * 26
    yaw = hash01(i + 29, slot) * 360
    return vis, (math.sin(a) * rad, y, math.cos(a) * rad), yaw
def frame_parts(t):
    ps = copy.deepcopy(parts)
    for p in ps:
        if p['parent'] is not None: continue
        i = int(p['name'][2:])
        vis, pos, yaw = place(i, t)
        p['pivot'] = list(pos); p['rot'] = [0, yaw, 0]
        if not vis:
            p['_hide'] = True
    hidden = {p['name'] for p in ps if p.get('_hide')}
    for p in ps:
        if p['name'] in hidden or p['parent'] in hidden: p['size'] = [1e-4] * 3; p['frm'] = [0, 0, 0]
    return ps
def shot(t, yaw, W=600, H=860, scale=20, cy=0.3):
    SS = 2; gen.S = 8
    if not hasattr(shot, 'atl'): shot.atl = gen.build_atlas(parts)
    tex, glow, ts = shot.atl
    q = []
    for name, b in gen.BODY.items():
        q += R.quads_for(b['parts'], b['tex'], b['texsize'], None, R.M(b['pivot'], (0, 0, 0)))
    q += R.quads_for(frame_parts(t), tex, ts, None, np.eye(4))
    col, gl, mask = R.render(q, W * SS, H * SS, yaw, 4, scale * SS, W / 2 * SS, H * cy * SS)
    bg = np.zeros_like(col); bg[:] = (22, 22, 22)
    return Image.fromarray(np.clip(np.where(mask[..., None] > 0, col, bg), 0, 255).astype(np.uint8)).resize((W, H), Image.LANCZOS)
if __name__ == '__main__':
    R.AMB, R.DIF = 0.7, 0.45
    gen.export('/tmp/sign_body')
    if sys.argv[1] == 'still':
        o = Image.new('RGB', (600 * 3, 860))
        for k, (t, yaw) in enumerate(((1.0, -15), (2.6, 25), (4.1, 0))): o.paste(shot(t, yaw), (k * 600, 0))
        o.save('/home/claude/candy_aura_design.png')
    elif sys.argv[1] == 'export':
        out = '/home/claude/repo/src/main/resources/assets/therabbits'
        gen.S = 8
        tex, glow, (tw, th) = gen.build_atlas(parts)
        Image.fromarray(tex, 'RGBA').save(f'{out}/textures/cosmetics/candy_aura.png')
        js = dict(attach='aura', texture=[tw, th], glow=False,
                  parts=[dict(name=p['name'], parent=p['parent'], pivot=p['pivot'], rot=p['rot'], **{'from': p['frm']}, size=p['size'], uv=p['uv']) for p in parts])
        open(f'{out}/cosmetics/candy_aura.json', 'w').write(json.dumps(js, indent=1)); print('exported', tw, th)
    else:
        os.makedirs('/tmp/cav', exist_ok=True)
        for f in range(int(sys.argv[1]), int(sys.argv[2])):
            shot(f / 30, -20 * math.sin(f / 300 * 2 * math.pi)).save(f'/tmp/cav/{f:04d}.jpg', quality=93)

# Splits an animated shield into one static texture (frame, handle) + small per-frame textures of just the face plate,
# so the game loads ~0.1-0.5 MB per frame instead of a full 1024x1024 atlas.
import json, os, sys, numpy as np
from PIL import Image
OUT = '/home/claude/repo/src/main/resources/assets/therabbits'
def split(name, scale=1.0, src_s=16):
    jp = f'{OUT}/cosmetics/{name}.json'; js = json.load(open(jp))
    if 'framePart' in js: print(name, 'already split'); return
    nf = js['frames']; d = f'{OUT}/textures/cosmetics/{name}'
    plate = next(p for p in js['parts'] if p['name'] == 'plate')
    w, h, dd = (int(round(v)) for v in plate['size'])
    u, v = plate['uv']
    bw, bh = 2 * (dd + w), dd + h                       # box-UV footprint in texels
    x0, y0 = u * src_s, v * src_s
    for f in range(nf):
        for suf in ('', '_glow'):
            im = Image.open(f'{d}/{f}{suf}.png').convert('RGBA')
            crop = im.crop((x0, y0, x0 + bw * src_s, y0 + bh * src_s))
            if scale != 1.0: crop = crop.resize((round(bw * src_s * scale), round(bh * src_s * scale)), Image.LANCZOS if not suf else Image.BILINEAR)
            if f == 0:   # static texture: frame 0 with the plate area cleared
                st = np.array(im); st[y0:y0 + bh * src_s, x0:x0 + bw * src_s] = 0
                Image.fromarray(st, 'RGBA').save(f'{OUT}/textures/cosmetics/{name}{suf}.png', optimize=True)
            crop.save(f'{d}/{f}{suf}.png', optimize=True)
    js['framePart'] = dict(name='plate', texture=[bw, bh])
    open(jp, 'w').write(json.dumps(js, indent=1))
    print(name, nf, 'frames ->', bw * src_s * scale, 'x', bh * src_s * scale)
for a in sys.argv[1:]:
    n, _, s = a.partition(':'); split(n, float(s) if s else 1.0)

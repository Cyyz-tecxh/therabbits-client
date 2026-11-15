import json, os, copy, numpy as np
from PIL import Image
import gen, blood_shield as B
out = '/home/claude/repo/src/main/resources/assets/therabbits'
name = 'blood_moon_shield'
os.makedirs(f'{out}/textures/cosmetics/{name}', exist_ok=True)
gen.S = 16
for f in range(16):
    B.T[0] = f / 16; B.sign.T[0] = f / 16
    parts = copy.deepcopy(B.MODEL['parts'])
    tex, glow, (tw, th) = gen.build_atlas(parts, width=64)
    g = glow.copy()
    g[g[..., :3].max(-1) < 70] = 0          # only real light sources glow
    Image.fromarray(tex, 'RGBA').save(f'{out}/textures/cosmetics/{name}/{f}.png', optimize=True)
    Image.fromarray(g, 'RGBA').save(f'{out}/textures/cosmetics/{name}/{f}_glow.png', optimize=True)
js = dict(attach='shield', texture=[tw, th], glow=True, frames=16,
          parts=[dict(name=p['name'], parent=p['parent'], pivot=p['pivot'], rot=p['rot'], **{'from': p['frm']}, size=p['size'], uv=p['uv']) for p in parts])
open(f'{out}/cosmetics/{name}.json', 'w').write(json.dumps(js, indent=1))
print('ok', tw, th, tex.shape)

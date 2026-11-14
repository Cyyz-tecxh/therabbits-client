import json, os, numpy as np
from PIL import Image
import gen
m = gen.MODELS['neon_shield']
out = '/home/claude/repo/src/main/resources/assets/therabbits'
os.makedirs(out + '/textures/cosmetics/neon_shield', exist_ok=True)
for f in range(16):
    gen.rng = np.random.default_rng(11)
    gen.SHIELD_T = f / 16
    tex, glow, (tw, th) = gen.build_atlas(m['parts'])
    Image.fromarray(tex, 'RGBA').save(f'{out}/textures/cosmetics/neon_shield/{f}.png')
    Image.fromarray(glow, 'RGBA').save(f'{out}/textures/cosmetics/neon_shield/{f}_glow.png')
js = dict(attach='shield', texture=[tw, th], glow=True, frames=16,
          parts=[dict(name=p['name'], parent=p['parent'], pivot=p['pivot'], rot=p['rot'], **{'from': p['frm']}, size=p['size'], uv=p['uv']) for p in m['parts']])
open(f'{out}/cosmetics/neon_shield.json', 'w').write(json.dumps(js, indent=1))
print('ok', tw, th)

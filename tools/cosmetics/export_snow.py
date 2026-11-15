import json, numpy as np
from PIL import Image
import gen
out = '/home/claude/repo/src/main/resources/assets/therabbits'
m = gen.MODELS['snow_aura']
tex, glow, (tw, th) = gen.build_atlas(m['parts'])
Image.fromarray(tex, 'RGBA').save(f'{out}/textures/cosmetics/snow_aura.png')
Image.fromarray(glow, 'RGBA').save(f'{out}/textures/cosmetics/snow_aura_glow.png')
js = dict(attach='aura', texture=[tw, th], glow=True,
          parts=[dict(name=p['name'], parent=p['parent'], pivot=p['pivot'], rot=p['rot'], **{'from': p['frm']}, size=p['size'], uv=p['uv']) for p in m['parts']])
open(f'{out}/cosmetics/snow_aura.json', 'w').write(json.dumps(js, indent=1))
print('ok', tw, th, tex.shape, len(m['parts']))

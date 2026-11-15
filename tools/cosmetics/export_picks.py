# Export the picked cosmetics (3 masks, 3 auras, 5 dragon pets, spirit bunny) as client models + textures + icons.
import json, math, os, copy
import numpy as np
from PIL import Image
import gen, picks as PK, mythics as MY, render as R, cards as CA
OUT = '/home/claude/repo/src/main/resources/assets/therabbits'
gen.export('/tmp/exp_body')

def atlas(parts):
    need = max([math.ceil(2 * p['size'][2] + 2 * p['size'][0] + 1) for p in parts] + [64])
    return gen.build_atlas(parts, width=2 ** math.ceil(math.log2(need)))

def write(key, attach, parts):
    tex, glow, (tw, th) = atlas(parts)
    Image.fromarray(tex, 'RGBA').save(f'{OUT}/textures/cosmetics/{key}.png')
    if glow is not None: Image.fromarray(glow, 'RGBA').save(f'{OUT}/textures/cosmetics/{key}_glow.png')
    js = dict(attach=attach, texture=[tw, th], glow=glow is not None,
              parts=[dict(name=p['name'], parent=p['parent'], pivot=[round(v, 4) for v in p['pivot']], rot=[round(v, 4) for v in p['rot']],
                          **{'from': [round(v, 4) for v in p['frm']]}, size=[round(v, 4) for v in p['size']], uv=p['uv']) for p in parts])
    open(f'{OUT}/cosmetics/{key}.json', 'w').write(json.dumps(js))
    print(key, attach, len(parts), 'parts', tex.shape[1], 'x', tex.shape[0], 'px')
    return tex, glow, (tw, th)

MODELS = {}
for k in ('oni_mask', 'kitsune_mask', 'oni_menpo'):
    MODELS[k] = ('head', copy.deepcopy(PK.M[k]['parts']))
MODELS['clockwork_aura'] = ('aura', copy.deepcopy(PK.M['clockwork']['parts']))
MODELS['petal_aura'] = ('aura', copy.deepcopy(PK.M['petal_storm']['parts']))
MODELS['koi_aura'] = ('aura', copy.deepcopy(PK.M['koi']['parts']))
for k in ('eternal_dragon', 'shadow_dragon', 'frost_dragon', 'infernal_dragon', 'celestial_dragon'):
    ps = [p for p in copy.deepcopy(MY.MODELS[k]['parts']) if not p['name'].startswith(('d_p', 'd_k', 'd_orb', 'd_halo'))]
    MODELS['pet_' + k.replace('_dragon', '')] = ('pet', ps)
MODELS['spirit_bunny'] = ('pet', copy.deepcopy(PK.M['spirit_bunny']['parts']))
for deck in CA.DECKS:
    MODELS['cards_' + deck] = ('aura', copy.deepcopy(CA.MODELS['cards_' + deck]['parts']))
for k in ('eternal_dragon', 'shadow_dragon', 'frost_dragon', 'infernal_dragon', 'celestial_dragon'):
    MODELS['dragon_' + k.replace('_dragon', '')] = ('aura', copy.deepcopy(MY.MODELS[k]['parts']))

if __name__ == '__main__':
    import sys
    for key, (attach, parts) in MODELS.items():
        if len(sys.argv) > 1 and not any(key.startswith(a) for a in sys.argv[1:]): continue
        write(key, attach, parts)

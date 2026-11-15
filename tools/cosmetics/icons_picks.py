import copy, numpy as np
from PIL import Image, ImageFilter
import sys
import gen, render as R, picks as PK, mythics as MY, export_picks as EX, cards as CA
R.AMB, R.DIF = 0.7, 0.5
OUT = '/home/claude/repo/src/main/resources/assets/therabbits/textures/gui/cosmetic_icons'
def posed(key, parts):
    if key == 'koi_aura': return PK.pose_koi(2.0, parts)
    if key.startswith(('cards_', 'dragon_')):
        uv = {p['name']: p['uv'] for p in parts}
        out = CA.pose_cards(key, 1.2, cam_yaw=-25) if key.startswith('cards_') else MY.pose_premium(key[7:] + '_dragon', 5.2 if key == 'dragon_eternal' else 2.0)
        out = [p for p in out if p['name'] in uv and not p['name'].startswith(('d_p', 'd_k'))]
        for p in out: p['uv'] = uv[p['name']]
        return out
    if key.startswith('pet_'):
        k = key[4:] + '_dragon'
        uv = {p['name']: p['uv'] for p in parts}
        out = [p for p in MY.pose_premium(k, 4.0, style_override='pet') if p['name'] in uv]
        for p in out: p['uv'] = uv[p['name']]; p.pop('uvsize', None)
        return out
    return parts
VIEW = {'oni_mask': (0, 0), 'kitsune_mask': (0, 0), 'oni_menpo': (0, 0)}
for key, (attach, parts) in EX.MODELS.items():
    if len(sys.argv) > 1 and not key.startswith(tuple(sys.argv[1:])): continue
    tex, glow, ts = EX.atlas(copy.deepcopy(parts))
    ps = copy.deepcopy(parts)
    tex, glow, ts = gen.build_atlas(ps, width=tex.shape[1] // gen.S)
    ps = posed(key, ps)
    if attach == 'head':   # face the mask toward the camera
        for p in ps:
            if p['parent'] is None and not p['name'].endswith('cord'): p['pivot'] = [0, 0, 0]; p['rot'] = [0, 0, 0]
        ps = [p for p in ps if not p['name'].endswith('cord')]
        yaw, pitch = -20, 4
    else:
        yaw, pitch = -25, 12
    q = R.quads_for(ps, tex, ts, glow, np.eye(4))
    S = 3
    col, gl, mask = R.render(q, 800 * S, 800 * S, yaw, pitch, 14 * S, 400 * S, 400 * S)
    g = Image.fromarray(np.clip(gl, 0, 255).astype(np.uint8))
    rgb = np.clip(col + np.asarray(g.filter(ImageFilter.GaussianBlur(2 * S))) * 0.6, 0, 255)
    im = Image.fromarray(np.dstack([rgb.astype(np.uint8), (mask * 255).astype(np.uint8)]), 'RGBA')
    im = im.crop(im.getbbox())
    k = 120 / max(im.size)
    im = im.resize((max(1, int(im.width * k)), max(1, int(im.height * k))), Image.LANCZOS)
    canvas = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    canvas.alpha_composite(im, ((128 - im.width) // 2, (128 - im.height) // 2))
    canvas.save(f'{OUT}/{key}.png'); print(key)

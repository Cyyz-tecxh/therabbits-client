# Straw Hat (id straw_hat): a crown and a wide flat brim, same proportions as the reference model, with an original
# hand-painted straw weave and a ribbon band. Head slot.
import os, sys, json, math, numpy as np
from PIL import Image
import gen, render as R

S = 8                                   # px per texel
TW, TH = 64, 32
OUT = '/home/claude/repo/src/main/resources/assets/therabbits'

CW, CH = 9.2, 3.4                       # crown: 8x3x8 at the reference scale, a touch wider than the head
BW, BT = 16.0, 0.4                      # brim: 14x14 at the reference scale, thin plate
PARTS = [
    dict(name='crown', parent=None, pivot=[0, 0, 0], rot=[0, 0, 0], frm=[-CW / 2, -10.6, -CW / 2], size=[CW, CH, CW], uv=[0, 0]),
    dict(name='brim', parent=None, pivot=[0, 0, 0], rot=[0, 0, 0], frm=[-BW / 2, -7.6, -BW / 2], size=[BW, BT, BW], uv=[0, 13]),
]

STRAW = np.array([228, 190, 96]); DARK = np.array([186, 142, 62]); LIGHT = np.array([244, 214, 138])
RIB = np.array([150, 38, 42]); RIB_D = np.array([110, 26, 30])

def hsh(x, y, k=0):
    v = math.sin(x * 12.9898 + y * 78.233 + k * 37.719) * 43758.5453
    return v - math.floor(v)

def texel(kind, face, x, y, w, h):
    """colour of one texel; x,y are texel coords inside the face"""
    n = hsh(x, y, len(face)) * 0.12 - 0.06
    if kind == 'crown' and face in ('front', 'back', 'left', 'right'):
        if y == h - 2: c = RIB if (x + y) % 3 else RIB_D        # ribbon band at the bottom of the crown
        else: c = DARK if int(y) % 2 == 1 and int(x) % 2 == 0 else STRAW   # woven rows
    elif kind == 'crown':                                          # crown top: weave rings
        r = max(abs(x - w / 2 + 0.5), abs(y - h / 2 + 0.5))
        c = LIGHT if r < 1 else DARK if int(r) % 2 == 1 else STRAW
    elif face in ('top', 'bottom'):                                # brim: concentric weave rings, darker rim
        r = max(abs(x - w / 2 + 0.5), abs(y - h / 2 + 0.5))
        c = DARK if r >= w / 2 - 1 else (DARK if int(r) % 2 == 0 else STRAW) if int(x + y) % 2 == 0 else STRAW
        if face == 'bottom': c = c * 0.78
    else:
        c = DARK                                                   # brim edge
    return np.clip(c * (1 + n), 0, 255)

def atlas():
    img = np.zeros((TH * S, TW * S, 4), np.uint8)
    for p in PARTS:
        w, h, d = p['size']; u, v = p['uv']
        for face, (fu, fv, fw, fh) in gen.face_regions(u, v, w, h, d).items():
            x0, y0, x1, y1 = round(fu * S), round(fv * S), round((fu + fw) * S), round((fv + fh) * S)
            for py in range(y0, max(y1, y0 + 1)):
                for px in range(x0, max(x1, x0 + 1)):
                    tx, ty = int((px - x0) / S), int((py - y0) / S)
                    img[py, px, :3] = texel(p['name'], face, tx, ty, int(math.ceil(fw)), int(math.ceil(fh)))
                    img[py, px, 3] = 255
    return img

def shot(yaw, W=700, H=700, scale=34, pitch=18, tex=None):
    gen.S = S
    q = []
    b = gen.BODY['head']
    q += R.quads_for(b['parts'], b['tex'], b['texsize'], None, R.M(b['pivot'], (0, 0, 0)))
    q += R.quads_for(PARTS, tex, (TW, TH), None, R.M(b['pivot'], (0, 0, 0)))
    col, gl, mask = R.render(q, W * 2, H * 2, yaw, pitch, scale * 2, W, H * 1.25)
    img = np.where(mask[..., None] > 0, col, np.array((22, 23, 26), float))
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((W, H), Image.LANCZOS)

if __name__ == '__main__':
    R.AMB, R.DIF = 0.62, 0.5
    gen.export('/tmp/sign_body')
    tex = atlas()
    if sys.argv[1] == 'still':
        o = Image.new('RGB', (700 * 3, 700))
        for k, yaw in enumerate((25, 150, -60)): o.paste(shot(yaw, tex=tex), (k * 700, 0))
        o.save('/home/claude/straw_hat_design.png')
    else:
        Image.fromarray(tex, 'RGBA').save(f'{OUT}/textures/cosmetics/straw_hat.png')
        js = dict(attach='head', texture=[TW, TH], glow=False,
                  parts=[dict(name=p['name'], parent=None, pivot=p['pivot'], rot=p['rot'], **{'from': p['frm']}, size=p['size'], uv=p['uv']) for p in PARTS])
        open(f'{OUT}/cosmetics/straw_hat.json', 'w').write(json.dumps(js, indent=1))
        # icon: the hat alone, three-quarter view, transparent background
        gen.S = S
        q = R.quads_for(PARTS, tex, (TW, TH), None, R.M((0, 0, 0), (0, 0, 0)))
        col, gl, mask = R.render(q, 512, 512, 35, -22, 26, 256, 330)
        ic = np.concatenate([np.clip(col, 0, 255), (mask[..., None] > 0) * 255.0], 2).astype(np.uint8)
        Image.fromarray(ic, 'RGBA').resize((128, 128), Image.LANCZOS).save(f'{OUT}/textures/gui/cosmetic_icons/straw_hat.png')
        print('exported')

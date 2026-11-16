# Decoding Shield: the red glyph terminal on a shield face (frame matches the Blood Moon Shield shape)
import os, sys, copy, math, subprocess, json, numpy as np
from PIL import Image, ImageDraw, ImageFont
import gen, render as R, ideas as I
import blood_shield as B, glyph_art as GA
NF = 18                      # 18 x 110 ms: the blur pulse comes round about every 2 s
ART = GA.frames(NF, 336, 176)  # 21 x 11 face = 1.9 : 1, like the video
CUR = [0]
def _face(d, gd, W, H, face, im, gl):
    if face != 'front':
        d.rectangle((0, 0, W, H), fill=(12, 9, 10, 255)); return
    rgb, lit = ART[CUR[0] % NF]
    a = Image.fromarray(rgb).resize((W, H), Image.NEAREST).convert('RGBA')
    im.paste(a, (0, 0))
    m = Image.fromarray((lit * 255).astype(np.uint8)).resize((W, H), Image.NEAREST)
    gl.paste(a, (0, 0), m)
def _frame(d, gd, W, H, face, im, gl):
    yy = np.linspace(0, 1, H)[:, None] * np.ones((1, W))
    k = 0.85 + 0.3 * np.exp(-((yy - 0.3) ** 2) / 0.03)
    arr = np.stack([14 * k, 10 * k, 11 * k, np.full_like(k, 255)], -1)
    im.paste(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA'))
    if face == 'front':   # thin blood-red edge line
        line = Image.new('RGBA', (W, H), (0, 0, 0, 0)); ld = ImageDraw.Draw(line)
        if W > H: ld.rectangle((0, H // 2, W, H // 2 + max(0, H // 4 - 1)), fill=(110, 10, 16, 255))
        else: ld.rectangle((W // 2, 0, W // 2 + max(0, W // 4 - 1), H), fill=(110, 10, 16, 255))
        im.alpha_composite(line)
I.IMG['bm_face'] = _face
I.IMG['bm_frame'] = _frame
def set_frame(i): CUR[0] = i; B.T[0] = i / NF
# 1.9:1 panel with a thin near-black border (no crests)
from gen import P, box_c
def build():
    PW, PH, b = 21.0, 11.0, 0.5
    sd = [P('plate', (-PW / 2, -PH / 2, -0.5), (PW, PH, 1.0), 'img:bm_face')]
    for n, frm, size in (('ft', (-PW / 2 - b, -PH / 2 - b, -0.7), (PW + 2 * b, b, 1.4)), ('fb', (-PW / 2 - b, PH / 2, -0.7), (PW + 2 * b, b, 1.4)),
                         ('fl', (-PW / 2 - b, -PH / 2, -0.7), (b, PH, 1.4)), ('fr', (PW / 2, -PH / 2, -0.7), (b, PH, 1.4))):
        sd.append(P(n, frm, size, 'img:bm_frame'))
    sd += [box_c('back', 0, -PH / 2, 0.65, PW, PH, 0.3, 'img:bm_frame'),
           box_c('post_t', 0, -3.0, 1.35, 1.2, 1.2, 1.2, 'gunmetal'), box_c('post_b', 0, 1.8, 1.35, 1.2, 1.2, 1.2, 'gunmetal'),
           box_c('grip', 0, -3.0, 2.3, 1.6, 6.0, 0.9, 'gunmetal')]
    return sd
B.MODEL['parts'] = build()
if __name__ == '__main__':
    gen.export('/tmp/sign_body')
    if sys.argv[1] == 'still':
        F1 = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 40); F2 = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 20)
        o = Image.new('RGB', (1480, 960), (12, 8, 9)); d = ImageDraw.Draw(o)
        set_frame(3); o.paste(B.shot(0.2, 760, 400, 0, 0, 30, 200), (0, 120))
        set_frame(9); o.paste(B.shot(0.45, 760, 400, -35, -8, 28, 200), (0, 520))
        set_frame(15); o.paste(B.shot(0.7, 680, 900, -12, -4, 15, 300, held=True), (780, 120))
        d.text((30, 26), 'Decoding Shield', font=F1, fill=(240, 236, 236))
        d.text((32, 74), 'Matched to your video: 13 x 5 glitching glyphs, 1.9 : 1 panel  ·  front  ·  angled  ·  held', font=F2, fill=(235, 90, 90))
        o.save('/home/claude/decoding_shield_design.png')
    elif sys.argv[1] == 'export':
        out = '/home/claude/repo/src/main/resources/assets/therabbits'; name = 'decoding_shield'
        os.makedirs(f'{out}/textures/cosmetics/{name}', exist_ok=True)
        gen.S = 16
        for f in range(NF):
            set_frame(f)
            parts = copy.deepcopy(B.MODEL['parts'])
            tex, glow, (tw, th) = gen.build_atlas(parts, width=64)
            g = glow.copy(); g[g[..., :3].max(-1) < 70] = 0
            Image.fromarray(tex, 'RGBA').save(f'{out}/textures/cosmetics/{name}/{f}.png', optimize=True)
            Image.fromarray(g, 'RGBA').save(f'{out}/textures/cosmetics/{name}/{f}_glow.png', optimize=True)
        js = dict(attach='shield', texture=[tw, th], glow=True, frames=NF,
                  parts=[dict(name=p['name'], parent=p['parent'], pivot=p['pivot'], rot=p['rot'], **{'from': p['frm']}, size=p['size'], uv=p['uv']) for p in parts])
        open(f'{out}/cosmetics/{name}.json', 'w').write(json.dumps(js, indent=1)); print('exported', tw, th)
    else:
        os.makedirs('/tmp/gsv', exist_ok=True)
        for f in range(int(sys.argv[1]), int(sys.argv[2])):
            set_frame(int(f * 30 / 1000 * 1000 / 110 / 30 * 30 / 30 * (1000 / 110) / (1000 / 110)) if False else int(f * (1000 / 30) / 110) % NF)
            yaw = -25 * math.sin(f / 180 * 2 * math.pi)
            B.shot(f / 180, 720, 900, yaw, -4, 15, 300, held=True).save(f'/tmp/gsv/{f:04d}.jpg', quality=93)

# Blood Moon Shield: the animated blood-moon scene on the face of a shield (original design).
import math, copy, os, sys, subprocess
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import gen, render as R, ideas as I, sign
from gen import P, box_c

# landscape art like the reference: deep red nebula, crimson moon with dark seas, soft (not neon) light
sign.MOON_CY, sign.MOON_R = 0.5, 0.36
sign.GLOW, sign.MARIA, sign.MOON_RGB, sign.VIGNETTE = 0.3, 2.6, (210, 26, 54), 0.55
sign.CRATER, sign.NEB_GAIN, sign.MARIA_D = 0.35, 1.5, 1.5
T = [0.0]

def _face(d, gd, W, H, face, im, gl):
    if face != 'front':
        d.rectangle((0, 0, W, H), fill=(14, 8, 10, 255)); return
    art = sign.blood_moon(T[0]).resize((W, H), Image.LANCZOS).convert('RGBA')
    im.paste(art, (0, 0))
    gl.paste(art.point(lambda v: int(v * 0.8)).convert('RGBA'), (0, 0))
I.IMG['bm_face'] = _face

def _frame(d, gd, W, H, face, im, gl):
    # thin charcoal frame with a soft brushed sheen
    yy = np.linspace(0, 1, H)[:, None] * np.ones((1, W)); xx = np.linspace(0, 1, W)[None, :] * np.ones((H, 1))
    k = 0.85 + 0.35 * np.exp(-((yy - 0.3) ** 2) / 0.03) + 0.08 * np.sin(xx * 9 + yy * 3)
    arr = np.stack([34 * k, 34 * k, 40 * k, np.full_like(k, 255)], -1)
    im.paste(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA'))
I.IMG['bm_frame'] = _frame

def _metal(d, gd, W, H, face, im, gl):
    yy = np.linspace(0, 1, H)[:, None] * np.ones((1, W))
    k = 0.85 + 0.4 * np.exp(-((yy - 0.2) ** 2) / 0.02)
    arr = np.stack([96 * k, 14 * k, 20 * k, np.full_like(k, 255)], -1)
    im.paste(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA'))
I.IMG['bm_crimson'] = _metal

PW, PH = 22.0, 10.5
def build():
    b = 0.75
    sd = [P('plate', (-PW / 2, -PH / 2, -0.5), (PW, PH, 1.0), 'img:bm_face')]
    for n, frm, size in (('ft', (-PW / 2 - b, -PH / 2 - b, -0.75), (PW + 2 * b, b, 1.6)), ('fb', (-PW / 2 - b, PH / 2, -0.75), (PW + 2 * b, b, 1.6)),
                         ('fl', (-PW / 2 - b, -PH / 2, -0.75), (b, PH, 1.6)), ('fr', (PW / 2, -PH / 2, -0.75), (b, PH, 1.6))):
        sd.append(P(n, frm, size, 'img:bm_frame'))
    sd += [box_c('back', 0, -PH / 2, 0.65, PW, PH, 0.3, 'img:bm_frame'),
           box_c('post_t', 0, -3.0, 1.35, 1.2, 1.2, 1.2, 'gunmetal'), box_c('post_b', 0, 1.8, 1.35, 1.2, 1.2, 1.2, 'gunmetal'),
           box_c('grip', 0, -3.0, 2.3, 1.6, 6.0, 0.9, 'gunmetal')]
    return sd
MODEL = dict(attach='shield', parts=build())

def shield_quads(t, base=np.eye(4), S=24):
    T[0] = t; sign.T[0] = t
    parts = copy.deepcopy(MODEL['parts'])
    old = gen.S; gen.S = S
    try:
        tex, glow, ts = gen.build_atlas(parts, width=64)
        return R.quads_for(parts, tex, ts, glow, base)
    finally:
        gen.S = old

def shot(t, Wd, Hd, yaw, pitch, scale, cy, held=False, ss=2):
    q = []
    if held:
        pose = dict(left_arm=(-4, 0, -2), right_arm=(-4, 0, 2))
        for bname, b in gen.BODY.items():
            base = R.M(b['pivot'], pose.get(bname, (0, 0, 0)))
            q += R.quads_for(b['parts'], b['tex'], b['texsize'], None, base)
        q += shield_quads(t, R.M((0, 6.0, -4.4), (0, 0, 0)))
    else:
        q = shield_quads(t)
    Wb, Hb = Wd * ss, Hd * ss
    col, gl, mask = R.render(q, Wb, Hb, yaw, pitch, scale * ss, Wd / 2 * ss, cy * ss)
    yy = np.linspace(0, 1, Hb)[:, None, None]; xx = np.linspace(-1, 1, Wb)[None, :, None]
    bg = (18 - 9 * yy) * (1 - 0.2 * xx ** 2) * np.array((1.15, 0.8, 0.85))[None, None]
    img = np.where(mask[..., None] > 0, col, bg)
    g = Image.fromarray(np.clip(gl, 0, 255).astype(np.uint8))
    img = img + np.asarray(g.filter(ImageFilter.GaussianBlur(10 * ss))).astype(float) * 0.45
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((Wd, Hd), Image.LANCZOS)

if __name__ == '__main__':
    gen.export('/tmp/sign_body')
    if sys.argv[1] == 'still':
        F1 = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 40); F2 = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 20)
        o = Image.new('RGB', (1480, 960), (12, 8, 9)); d = ImageDraw.Draw(o)
        o.paste(shot(0.2, 760, 400, 0, 0, 30, 200), (0, 120))
        o.paste(shot(0.45, 760, 400, -35, -8, 28, 200), (0, 520))
        o.paste(shot(0.7, 680, 900, -12, -4, 15, 300, held=True), (780, 120))
        d.text((30, 26), 'Blood Moon Shield', font=F1, fill=(240, 236, 236))
        d.text((32, 74), 'Matched to your reference  \u00B7  front  \u00B7  angled  \u00B7  held', font=F2, fill=(230, 120, 120))
        o.save('/home/claude/blood_moon_shield_design.png')
    elif sys.argv[1] == 'enc':
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', '30', '-i', '/tmp/bmsv/%04d.jpg', '-f', 'lavfi', '-i', 'anullsrc=r=48000:cl=stereo', '-shortest',
                        '-c:v', 'libx264', '-crf', '17', '-preset', 'veryfast', '-pix_fmt', 'yuv420p', '-profile:v', 'main', '-c:a', 'aac', '-movflags', '+faststart', '/home/claude/bms.tmp.mp4'], check=True)
        os.replace('/home/claude/bms.tmp.mp4', '/home/claude/Blood_Moon_Shield_preview.mp4')
    else:
        os.makedirs('/tmp/bmsv', exist_ok=True); N = 180
        for f in range(int(sys.argv[1]), int(sys.argv[2])):
            t = f / N; yaw = -25 * math.sin(t * 2 * math.pi)
            shot(t, 720, 900, yaw, -4, 15, 300, held=True).save(f'/tmp/bmsv/{f:04d}.jpg', quality=93)

import os, sys, math, copy, subprocess, numpy as np
from PIL import Image, ImageFilter, ImageDraw, ImageFont
import gen, render as R
gen.export('/tmp/snow_out')
R.AMB, R.DIF = 0.55, 0.6
m = gen.MODELS['snow_aura']
W = H = 1080; SS = 2; FPS = 30; N = 210
OUT = '/home/claude/snow_frames'; os.makedirs(OUT, exist_ok=True)
base_parts = m['parts']

def animate(t):
    parts = copy.deepcopy(base_parts)
    by = {p['name']: p for p in parts}
    i = 0
    while 'f%d' % i in by:
        f = by['f%d' % i]; ip = base_parts[[p['name'] for p in base_parts].index('f%d' % i)]
        h = (i * 0.6180339) % 1
        drift = t * (0.10 + h * 0.12) * (1 if i % 2 == 0 else -1)
        x0, y0, z0 = ip['pivot']
        c, s = math.cos(drift), math.sin(drift)
        f['pivot'] = [x0 * c - z0 * s, y0 + math.sin(t * (0.7 + h * 0.6) + i * 1.3) * (0.5 + h * 0.7), x0 * s + z0 * c]
        f['rot'] = [0, ip['rot'][1] + math.degrees(t * (0.4 + h * 0.5)), math.degrees(t * (0.25 + h * 0.3) * (-1 if i % 3 == 0 else 1))]
        life = (t * (0.11 + h * 0.08) + h * 7.3) % 1
        a = life / 0.15 if life < 0.15 else 1 if life < 0.75 else (0.9 - life) / 0.15 if life < 0.9 else 0
        a *= 0.85 + 0.15 * math.sin(t * 9 + i * 2.1)
        sc = (1.0 + h * 0.8) * max(0, a)
        for p in (f, by['f%dx' % i]):
            p['frm'] = [v * sc for v in p['frm']]; p['size'] = [max(1e-4, v * sc) for v in p['size']]
        i += 1
    return parts

def frame(n, t):
    yaw = -25 + 50 * math.sin(n / N * 2 * math.pi)
    q = []
    for name, b in gen.BODY.items():
        base = R.M(b['pivot'], (0, 0, 0))
        q += R.quads_for(b['parts'], b['tex'], b['texsize'], None, base)
    parts = animate(t)
    q += R.quads_for(parts, m['tex'], m['texsize'], m['glow'], np.eye(4))
    col, gl, mask = R.render(q, W * SS, H * SS, yaw, 4, 21 * SS, W / 2 * SS, H * 0.36 * SS)
    yy = np.linspace(0, 1, H * SS)[:, None]; xx = np.linspace(-1, 1, W * SS)[None, :]
    bg = (20 + 10 * np.exp(-(xx ** 2 / 0.3 + (yy - 0.5) ** 2 / 0.25)))[..., None] * np.ones((1, 1, 3)) * np.array((1, 1, 1.12))
    img = np.where(mask[..., None] > 0, col, bg)
    g = Image.fromarray(np.clip(gl, 0, 255).astype(np.uint8))
    img = img + np.asarray(g.filter(ImageFilter.GaussianBlur(5 * SS))).astype(float) * 1.3 + np.asarray(g.filter(ImageFilter.GaussianBlur(1.5 * SS))).astype(float) * 0.5
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((W, H), Image.LANCZOS)
    d = ImageDraw.Draw(im)
    F = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 42); F2 = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 24)
    d.text((W / 2 - d.textlength('Snow Aura', font=F) / 2, 40), 'Snow Aura', font=F, fill=(236, 248, 255))
    t2 = 'THERABBITS CLIENT  ·  AURA'
    d.text((W / 2 - d.textlength(t2, font=F2) / 2, 96), t2, font=F2, fill=(90, 200, 255))
    im.save(f'{OUT}/{n:04d}.jpg', quality=93)

if __name__ == '__main__':
    if len(sys.argv) == 3:
        for n in range(int(sys.argv[1]), int(sys.argv[2])): frame(n, n / FPS)
    else:
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(FPS), '-i', f'{OUT}/%04d.jpg', '-c:v', 'libx264',
                        '-preset', 'veryfast', '-crf', '18', '-pix_fmt', 'yuv420p', '/home/claude/snow_aura.tmp.mp4'], check=True)
        os.replace('/home/claude/snow_aura.tmp.mp4', '/home/claude/snow_aura_preview.mp4')
        print('ok')

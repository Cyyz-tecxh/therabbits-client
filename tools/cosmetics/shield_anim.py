import os, subprocess, numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import gen, render as R
R.AMB, R.DIF = 0.6, 0.55
m = gen.MODELS['neon_shield']
os.makedirs('/home/claude/sh_frames', exist_ok=True)
W, H, N = 1080, 1080, 96
yy = np.linspace(0, 1, H)[:, None]; xx = np.linspace(-1, 1, W)[None, :]
r = 14 + 22 * np.exp(-(xx ** 2 / 0.4 + (yy - 0.5) ** 2 / 0.35))
bg = Image.fromarray(np.clip(np.dstack([r * 1.05, r * 0.72, r * 1.5]), 0, 255).astype(np.uint8)).convert('RGBA')
F = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 46); F2 = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 26)
ss = 2
for f in range(N):
    gen.SHIELD_T = f / N * 2 % 1.0
    tex, glow, ts = gen.build_atlas(m['parts'])
    yaw = -32 + 26 * np.sin(f / N * 2 * np.pi)
    q = R.quads_for(m['parts'], tex, ts, glow, R.M((0, 1.2 * np.sin(f / N * 4 * np.pi), 0), (0, 0, 0)))
    col, gl, mask = R.render(q, W * ss, H * ss, yaw, 10, 28 * ss, W / 2 * ss, H * 0.56 * ss)
    g = Image.fromarray(np.clip(gl, 0, 255).astype(np.uint8))
    rgb = np.clip(col + np.asarray(g.filter(ImageFilter.GaussianBlur(2 * ss))) * 0.5, 0, 255)
    im = Image.fromarray(np.dstack([rgb.astype(np.uint8), (mask * 255).astype(np.uint8)]), 'RGBA').resize((W, H), Image.LANCZOS)
    wide = np.asarray(g.filter(ImageFilter.GaussianBlur(16 * ss)).resize((W, H), Image.LANCZOS)).astype(float) * 1.7
    fr = bg.copy()
    a = im.split()[3].filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(18))
    hl = Image.new('RGBA', im.size, (176, 84, 255, 0)); hl.putalpha(Image.eval(a, lambda v: int(v * 0.55)))
    fr.alpha_composite(hl)
    fr = Image.fromarray(np.clip(np.asarray(fr.convert('RGB')).astype(float) + wide, 0, 255).astype(np.uint8)).convert('RGBA')
    fr.alpha_composite(im)
    d = ImageDraw.Draw(fr)
    d.text((W / 2 - d.textlength('Neon Royal Shield', font=F) / 2, 40), 'Neon Royal Shield', font=F, fill=(246, 240, 255))
    t = 'THERABBITS CLIENT  ·  ANIMATED SHIELD'
    d.text((W / 2 - d.textlength(t, font=F2) / 2, 100), t, font=F2, fill=(176, 84, 255))
    fr.convert('RGB').save(f'/home/claude/sh_frames/{f:03d}.jpg', quality=93)
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', '24', '-i', '/home/claude/sh_frames/%03d.jpg', '-c:v', 'libx264',
                '-preset', 'veryfast', '-crf', '18', '-pix_fmt', 'yuv420p', '/home/claude/neon_shield_animated.tmp.mp4'], check=True)
os.replace('/home/claude/neon_shield_animated.tmp.mp4', '/home/claude/neon_shield_animated.mp4')
print('ok')

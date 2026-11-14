import os, numpy as np
from PIL import Image, ImageFilter
import gen, render as R
R.AMB, R.DIF = 0.62, 0.5
gen.export('/tmp/icons_out')
OUT = '/home/claude/repo/src/main/resources/assets/therabbits/textures/gui/cosmetic_icons'
os.makedirs(OUT, exist_ok=True)
for key, m in gen.MODELS.items():
    parts = m['parts']
    if m['attach'] == 'arms':
        yaw, pitch = -35, 8
    elif m['attach'] in ('shield', 'aura'):
        yaw, pitch = -28, 6
    else:
        yaw, pitch = -30, 22
    q = R.quads_for(parts, m['tex'], m['texsize'], m['glow'], np.eye(4))
    S = 3
    col, gl, mask = R.render(q, 700 * S, 700 * S, yaw, pitch, 18 * S, 350 * S, 350 * S)
    g = Image.fromarray(np.clip(gl, 0, 255).astype(np.uint8))
    rgb = np.clip(col + np.asarray(g.filter(ImageFilter.GaussianBlur(2 * S))) * 0.6, 0, 255)
    im = Image.fromarray(np.dstack([rgb.astype(np.uint8), (mask * 255).astype(np.uint8)]), 'RGBA')
    im = im.crop(im.getbbox())
    k = 120 / max(im.size)
    im = im.resize((max(1, int(im.width * k)), max(1, int(im.height * k))), Image.LANCZOS)
    canvas = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    canvas.alpha_composite(im, ((128 - im.width) // 2, (128 - im.height) // 2))
    canvas.save(f'{OUT}/{key}.png')
    print(key)

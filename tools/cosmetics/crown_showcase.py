import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import gen, render as R
R.AMB, R.DIF = 0.66, 0.46

gen.export('/home/claude/cos/out')
m = gen.MODELS['royal_crown']

def crown_quads(base=np.eye(4)):
    return R.quads_for(m['parts'], m['tex'], m['texsize'], m['glow'], base)

def body_quads(head_rot=(0, 0, 0)):
    q = []
    for bname, b in gen.BODY.items():
        if bname not in ('head', 'body', 'right_arm', 'left_arm'): continue
        base = R.M(b['pivot'], head_rot if bname == 'head' else (0, 0, 0))
        q += R.quads_for(b['parts'], b['tex'], b['texsize'], None, base)
        if bname == 'head':
            q += crown_quads(base)
    return q

def shot(quads, W, H, yaw, pitch, scale, cx, cy, ss=2):
    col, gl, mask = R.render(quads, W * ss, H * ss, yaw, pitch, scale * ss, cx * ss, cy * ss)
    rgb = np.clip(col + np.asarray(Image.fromarray(np.clip(gl, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3 * ss))) * 0.6, 0, 255)
    a = (mask * 255).astype(np.uint8)
    im = Image.fromarray(np.dstack([rgb.astype(np.uint8), a]), 'RGBA')
    return im.resize((W, H), Image.LANCZOS)

def glow_outline(im, radius=10, strength=0.55):
    a = im.split()[3]
    halo = a.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(radius))
    halo = Image.eval(halo, lambda v: int(v * strength))
    g = Image.new('RGBA', im.size, (255, 255, 255, 0)); g.putalpha(halo)
    return Image.alpha_composite(g, im)

def sparkle(d, x, y, s):
    d.rectangle([x - s * 0.18, y - s, x + s * 0.18, y + s], fill=(245, 245, 245))
    d.rectangle([x - s, y - s * 0.18, x + s, y + s * 0.18], fill=(245, 245, 245))

W, H = 1920, 1080
yy = np.linspace(0, 1, H)[:, None]; xx = np.linspace(-1, 1, W)[None, :]
bg = (52 - 26 * ((xx * 0.8) ** 2 + (yy - 0.45) ** 2)) * np.ones((H, W))
canvas = Image.fromarray(np.dstack([bg, bg, bg + 2]).clip(0, 255).astype(np.uint8)).convert('RGBA')

# character close-up (left)
char = shot(body_quads(head_rot=(4, -14, 0)), 760, 960, -24, 10, 36, 380, 640)
canvas.alpha_composite(glow_outline(char, 14, 0.5), (-10, 120))
# hero crown (top right)
hero = shot(crown_quads(), 1000, 560, -26, 24, 58, 500, 940)
canvas.alpha_composite(glow_outline(hero, 12, 0.6), (840, 40))
# front / side / back
labels = [('Front View', 0), ('Side View', -90), ('Back View', 180)]
F = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 34)
T = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 64)
T2 = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 34)
d = ImageDraw.Draw(canvas)
for i, (lab, yaw) in enumerate(labels):
    v = shot(crown_quads(), 360, 260, yaw, 8, 30, 180, 470)
    x = 800 + i * 380
    canvas.alpha_composite(glow_outline(v, 8, 0.5), (x, 640))
    d.text((x + 180 - d.textlength(lab, font=F) / 2, 925), lab, font=F, fill=(232, 232, 236))
d.text((60, 46), 'Royal Crown', font=T, fill=(245, 245, 245))
d.text((62, 124), 'TheRabbits 3D Cosmetic Hat', font=T2, fill=(200, 200, 205))
d.line([(500, 145), (700, 145)], fill=(200, 200, 205), width=3)
for (x, y, s) in ((960, 130, 22), (1800, 520, 16), (1850, 490, 12)):
    sparkle(d, x, y, s)
canvas.convert('RGB').save('/home/claude/royal_crown_showcase.png')

# isolated transparent model
iso = shot(crown_quads(), 1200, 760, -26, 22, 70, 600, 1100, ss=3)
iso = iso.crop(iso.getbbox())
iso.save('/home/claude/royal_crown_isolated.png')
print('ok', iso.size)

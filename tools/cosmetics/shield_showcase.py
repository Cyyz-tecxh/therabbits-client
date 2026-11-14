import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import gen, render as R
R.AMB, R.DIF = 0.6, 0.55
gen.export('/home/claude/cos/out')
m = gen.MODELS['neon_shield']
PURPLE, GOLD = (176, 84, 255), (255, 176, 48)

def sq(base=np.eye(4)):
    return R.quads_for(m['parts'], m['tex'], m['texsize'], m['glow'], base)

def shot(quads, W, H, yaw, pitch, scale, cx, cy, ss=2, bloom=1.0):
    col, gl, mask = R.render(quads, W * ss, H * ss, yaw, pitch, scale * ss, cx * ss, cy * ss)
    g = Image.fromarray(np.clip(gl, 0, 255).astype(np.uint8))
    b = np.asarray(g.filter(ImageFilter.GaussianBlur(2 * ss))).astype(float) * 0.5 * bloom
    rgb = np.clip(col + b, 0, 255)
    im = Image.fromarray(np.dstack([rgb.astype(np.uint8), (mask * 255).astype(np.uint8)]), 'RGBA').resize((W, H), Image.LANCZOS)
    wide = g.filter(ImageFilter.GaussianBlur(14 * ss)).resize((W, H), Image.LANCZOS)
    return im, wide

def place(canvas, im_wide, xy, halo=PURPLE, hs=0.55):
    im, wide = im_wide
    a = im.split()[3].filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(16))
    hl = Image.new('RGBA', im.size, halo + (0,)); hl.putalpha(Image.eval(a, lambda v: int(v * hs)))
    canvas.alpha_composite(hl, xy)
    w = np.asarray(wide).astype(float) * 1.6
    base = np.asarray(canvas.crop((xy[0], xy[1], xy[0] + im.width, xy[1] + im.height)).convert('RGB')).astype(float)
    canvas.paste(Image.fromarray(np.clip(base + w, 0, 255).astype(np.uint8)), xy)
    canvas.alpha_composite(im, xy)

def holder_quads():
    q = []
    pose = dict(left_arm=(-62, 12, 0), right_arm=(14, 0, 6), head=(4, -10, 0))
    for bname, b in gen.BODY.items():
        if bname in ('right_leg', 'left_leg'): continue
        base = R.M(b['pivot'], pose.get(bname, (0, 0, 0)))
        q += R.quads_for(b['parts'], b['tex'], b['texsize'], None, base)
        if bname == 'left_arm':
            q += sq(base @ R.M((-2.5, 9.0, -3.5), (62, -24, 0)))
    return q

W, H = 2400, 1350
yy = np.linspace(0, 1, H)[:, None]; xx = np.linspace(-1, 1, W)[None, :]
r = 16 + 18 * np.exp(-((xx + 0.2) ** 2 / 0.5 + (yy - 0.45) ** 2 / 0.4))
bg = np.dstack([r * 1.05, r * 0.75, r * 1.45])
# faint grid floor lines
grid = ((np.arange(W)[None, :] % 80) < 2) | ((np.arange(H)[:, None] % 80) < 2)
bg = bg + np.where(grid[..., None], np.array((10, 4, 16)) * (yy[..., None] > 0.55), 0)
canvas = Image.fromarray(np.clip(bg, 0, 255).astype(np.uint8)).convert('RGBA')
d = ImageDraw.Draw(canvas)
FT = '/home/claude/fonts/ttf/exo2.ttf'
T, S2, L = (ImageFont.truetype(FT, n) for n in (84, 34, 32))

d.text((80, 60), 'Neon Royal Shield', font=T, fill=(246, 240, 255))
d.text((84, 160), 'THERABBITS 3D COSMETIC  ·  SHIELD', font=S2, fill=PURPLE)

# floating angled hero, with a soft shadow below
place(canvas, shot(sq(), 820, 1000, -32, 12, 34, 410, 500), (120, 230), PURPLE, 0.6)
sh = Image.new('RGBA', (520, 80), (0, 0, 0, 0)); ImageDraw.Draw(sh).ellipse([0, 0, 519, 79], fill=(0, 0, 0, 150))
canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(18)), (270, 1205))
d.text((420, 1250), 'Floating render', font=L, fill=(200, 190, 214))

# character holding the shield
place(canvas, shot(holder_quads(), 860, 1000, -34, 4, 24, 400, 420), (1500, 230), PURPLE, 0.45)

# front / side / back
for i, (lab, yaw) in enumerate((('Front', 0), ('Side', -90), ('Back', 180))):
    x = 930 + i * 220
    place(canvas, shot(sq(), 220, 420, yaw, 0, 15.5, 110, 210), (x, 380), PURPLE if i != 1 else GOLD, 0.45)
    d.text((x + 110 - d.textlength(lab, font=L) / 2, 820), lab, font=L, fill=(214, 206, 228))
d.text((1590, 1250), 'In hand', font=L, fill=(200, 190, 214))
for (x, y, s) in ((960, 250, 18), (2250, 300, 14), (880, 1100, 12)):
    d.rectangle([x - s * 0.18, y - s, x + s * 0.18, y + s], fill=GOLD); d.rectangle([x - s, y - s * 0.18, x + s, y + s * 0.18], fill=GOLD)
canvas.convert('RGB').save('/home/claude/neon_shield_showcase.png')
iso, _ = shot(sq(), 900, 1100, -32, 12, 40, 450, 550, ss=3)
iso.crop(iso.getbbox()).save('/home/claude/neon_shield_isolated.png')
print('ok')

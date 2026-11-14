import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import gen, render as R
R.AMB, R.DIF = 0.6, 0.55
gen.export('/tmp/sd')

def shot(m, W, H, yaw, pitch, scale, ss=2):
    q = R.quads_for(m['parts'], m['tex'], m['texsize'], m['glow'], np.eye(4))
    col, gl, mask = R.render(q, W * ss, H * ss, yaw, pitch, scale * ss, W / 2 * ss, H / 2 * ss)
    g = Image.fromarray(np.clip(gl, 0, 255).astype(np.uint8))
    rgb = np.clip(col + np.asarray(g.filter(ImageFilter.GaussianBlur(2 * ss))).astype(float) * 0.5, 0, 255)
    im = Image.fromarray(np.dstack([rgb.astype(np.uint8), (mask * 255).astype(np.uint8)]), 'RGBA').resize((W, H), Image.LANCZOS)
    wide = g.filter(ImageFilter.GaussianBlur(12 * ss)).resize((W, H), Image.LANCZOS)
    return im, wide

W, H = 2400, 1500
yy = np.linspace(0, 1, H)[:, None]; xx = np.linspace(-1, 1, W)[None, :]
bg = (16 + 12 * np.exp(-(xx ** 2 / 0.6 + (yy - 0.5) ** 2 / 0.5)))[..., None] * np.ones((1, 1, 3)) * np.array((1, 1, 1.15))
sheet = Image.fromarray(np.clip(bg, 0, 255).astype(np.uint8)).convert('RGBA')
d = ImageDraw.Draw(sheet)
F = '/home/claude/fonts/ttf/exo2.ttf'
T, L, S2 = ImageFont.truetype(F, 72), ImageFont.truetype(F, 38), ImageFont.truetype(F, 24)
t = 'SHIELD DRAFTS'
d.text((W / 2 - d.textlength(t, font=T) / 2, 34), t, font=T, fill=(240, 244, 250))
t = 'THERABBITS CLIENT  ·  PICK YOUR FAVOURITES'
d.text((W / 2 - d.textlength(t, font=S2) / 2, 122), t, font=S2, fill=(110, 198, 255))
CW, CH = 740, 610
for i, (key, (name, m, col)) in enumerate(gen.SHIELD_DRAFTS.items()):
    cx, cy = 60 + (i % 3) * (CW + 40), 190 + (i // 3) * (CH + 40)
    card = Image.new('RGBA', (CW + 60, CH + 60), (0, 0, 0, 0)); cd = ImageDraw.Draw(card)
    cd.rounded_rectangle([30, 30, 30 + CW, 30 + CH], 20, outline=col + (255,), width=3)
    sheet.alpha_composite(card.filter(ImageFilter.GaussianBlur(12)), (cx - 30, cy - 30))
    inner = Image.new('RGBA', (CW, CH), (0, 0, 0, 0))
    ImageDraw.Draw(inner).rounded_rectangle([0, 0, CW - 1, CH - 1], 20, fill=(14, 14, 18, 235))
    sheet.alpha_composite(inner, (cx, cy)); sheet.alpha_composite(card, (cx - 30, cy - 30))
    d.text((cx + 26, cy + 20), name, font=L, fill=col)
    for j, (yaw, pitch, sc, w_, h_, ox, oy) in enumerate(((-30, 10, 20, 340, 520, 30, 70), (0, 0, 11, 170, 380, 385, 90), (180, 0, 11, 170, 380, 565, 90))):
        im, wide = shot(m, w_, h_, yaw, pitch, sc)
        base = np.asarray(sheet.crop((cx + ox, cy + oy, cx + ox + w_, cy + oy + h_)).convert('RGB')).astype(float)
        sheet.paste(Image.fromarray(np.clip(base + np.asarray(wide).astype(float) * 1.4, 0, 255).astype(np.uint8)), (cx + ox, cy + oy))
        sheet.alpha_composite(im, (cx + ox, cy + oy))
    for j, lab in enumerate(('Front', 'Back')):
        x0 = cx + 385 + j * 180 + 85
        d.text((x0 - d.textlength(lab, font=S2) / 2, cy + 480), lab, font=S2, fill=(150, 150, 160))
sheet.convert('RGB').save('/home/claude/shield_drafts.png')
print('ok')

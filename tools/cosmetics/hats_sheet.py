import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import gen, render as R
R.AMB, R.DIF = 0.64, 0.48
gen.export('/home/claude/cos/out')

HATS = [('gold_crown', 'Gold Crown', (246, 184, 44)), ('cat_hat', 'Cat Ears', (244, 112, 196)),
        ('bucket_hat', 'Bucket Hat', (226, 226, 236)), ('santa_hat', 'Santa Hat', (236, 52, 56)),
        ('frog_hat', 'Frog Hat', (112, 226, 70)), ('bunny_hat', 'Bunny Hat', (176, 136, 246))]

def hat_q(m, base=np.eye(4)):
    return R.quads_for(m['parts'], m['tex'], m['texsize'], m['glow'], base)

def char_q(m, head_rot=(4, -16, 0)):
    q = []
    for bname, b in gen.BODY.items():
        if bname not in ('head', 'body', 'right_arm', 'left_arm'): continue
        base = R.M(b['pivot'], head_rot if bname == 'head' else (0, 0, 0))
        q += R.quads_for(b['parts'], b['tex'], b['texsize'], None, base)
        if bname == 'head': q += hat_q(m, base)
    return q

def shot(quads, W, H, yaw, pitch, scale, cx, cy, ss=2):
    col, gl, mask = R.render(quads, W * ss, H * ss, yaw, pitch, scale * ss, cx * ss, cy * ss)
    glow = np.asarray(Image.fromarray(np.clip(gl, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3 * ss))).astype(float)
    rgb = np.clip(col + glow * 0.7, 0, 255)
    im = Image.fromarray(np.dstack([rgb.astype(np.uint8), (mask * 255).astype(np.uint8)]), 'RGBA')
    return im.resize((W, H), Image.LANCZOS)

def glow(im, color, radius=10, strength=0.8):
    a = im.split()[3].filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(radius))
    a = Image.eval(a, lambda v: int(v * strength))
    g = Image.new('RGBA', im.size, color + (0,)); g.putalpha(a)
    return Image.alpha_composite(g, im)

def fit(im, box_w, box_h):
    im = im.crop(im.getbbox())
    k = min(box_w / im.width, box_h / im.height)
    return im.resize((int(im.width * k), int(im.height * k)), Image.LANCZOS)

W, H = 2400, 1720
yy = np.linspace(0, 1, H)[:, None]; xx = np.linspace(-1, 1, W)[None, :]
bg = (30 - 14 * (xx ** 2 * 0.7 + (yy - 0.4) ** 2)) * np.ones((H, W))
sheet = Image.fromarray(np.dstack([bg, bg, bg + 3]).clip(0, 255).astype(np.uint8)).convert('RGBA')
d = ImageDraw.Draw(sheet)
FT = '/home/claude/fonts/ttf/exo2.ttf'
title, sub, lab, foot = (ImageFont.truetype(FT, n) for n in (92, 34, 40, 26))

t = 'THERABBITS CLIENT'
d.text((W / 2 - d.textlength(t, font=title) / 2, 34), t, font=title, fill=(242, 242, 246))
t = 'C  O  S  M  E  T  I  C  S'
tw = d.textlength(t, font=sub)
d.text((W / 2 - tw / 2, 150), t, font=sub, fill=(190, 190, 198))
d.line([(W / 2 - tw / 2 - 320, 172), (W / 2 - tw / 2 - 30, 172)], fill=(110, 110, 118), width=2)
d.line([(W / 2 + tw / 2 + 30, 172), (W / 2 + tw / 2 + 320, 172)], fill=(110, 110, 118), width=2)

CW, CH, GX, GY, X0, Y0 = 740, 640, 40, 60, 70, 250
for i, (key, name, col) in enumerate(HATS):
    m = gen.MODELS[key]
    cx, cy = X0 + (i % 3) * (CW + GX), Y0 + (i // 3) * (CH + GY)
    # card with coloured glowing border
    card = Image.new('RGBA', (CW + 80, CH + 80), (0, 0, 0, 0))
    cd = ImageDraw.Draw(card)
    cd.rounded_rectangle([40, 40, 40 + CW, 40 + CH], 22, outline=col + (255,), width=4)
    halo = card.filter(ImageFilter.GaussianBlur(14))
    sheet.alpha_composite(halo, (cx - 40, cy - 40)); sheet.alpha_composite(halo, (cx - 40, cy - 40))
    inner = Image.new('RGBA', (CW, CH), (0, 0, 0, 0))
    ImageDraw.Draw(inner).rounded_rectangle([0, 0, CW - 1, CH - 1], 22, fill=tuple(int(18 + c * 0.05) for c in col) + (235,))
    sheet.alpha_composite(inner, (cx, cy)); sheet.alpha_composite(card, (cx - 40, cy - 40))
    # label pill
    pw = int(d.textlength(name, font=lab)) + 70
    px = cx + 50
    pill = Image.new('RGBA', (pw, 64), (0, 0, 0, 0))
    ImageDraw.Draw(pill).rounded_rectangle([0, 0, pw - 1, 63], 32, fill=(22, 22, 26, 255), outline=col + (255,), width=3)
    sheet.alpha_composite(pill, (px, cy - 32))
    d.text((px + 35, cy - 26), name, font=lab, fill=col)
    # hero hat (top-left), character (right), three views (bottom)
    hero = fit(shot(hat_q(m), 1000, 1000, -28, 22, 34, 500, 760), 420, 300)
    sheet.alpha_composite(glow(hero, col, 12, 0.9), (cx + 30 + (420 - hero.width) // 2, cy + 60 + (300 - hero.height) // 2))
    ch = shot(char_q(m), 520, 700, -24, 6, 26, 260, 480)
    ch = ch.crop((0, 0, 520, 700 - 18))
    chb = ch.getbbox(); ch = ch.crop((chb[0], chb[1], chb[2], ch.height))
    k = min(270 / ch.width, (CH - 50) / ch.height); ch = ch.resize((int(ch.width * k), int(ch.height * k)), Image.LANCZOS)
    sheet.alpha_composite(glow(ch, col, 9, 0.55), (cx + CW - ch.width - 18, cy + CH - ch.height - 4))
    for j, yaw in enumerate((0, -90, 180)):
        v = fit(shot(hat_q(m), 600, 600, yaw, 10, 22, 300, 460), 128, 120)
        sheet.alpha_composite(glow(v, col, 7, 0.7), (cx + 36 + j * 148 + (128 - v.width) // 2, cy + 400 + (120 - v.height)))
        d.text((cx + 36 + j * 148 + 64 - d.textlength(('Front', 'Side', 'Back')[j], font=foot) / 2, cy + 535),
               ('Front', 'Side', 'Back')[j], font=foot, fill=(170, 170, 178))

t = 'CUSTOMIZE   •   EXPRESS   •   STAND OUT'
d.text((W / 2 - d.textlength(t, font=sub) / 2, H - 64), t, font=sub, fill=(150, 150, 158))
sheet.convert('RGB').save('/home/claude/therabbits_hats_sheet.png')
print('ok')

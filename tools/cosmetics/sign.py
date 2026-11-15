# Held Sign cosmetic: a framed display held in front of the chest showing animated art (original Blood Moon scene).
import math, copy, os, sys, subprocess
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import gen, render as R, ideas as I
from gen import P

AW, AH = 640, 300           # art resolution
T = [0.0]                   # current animation time (0..1 loop)
MOON_CY, MOON_R = 0.47, 0.33
GLOW, MARIA, MOON_RGB, VIGNETTE = 1.0, 1.0, (200, 20, 26), 0.0
CRATER, NEB_GAIN, MARIA_D = 1.0, 1.0, 1.0

def _noise(seed, w, h, cells, octaves=5):
    rng = np.random.default_rng(seed); out = np.zeros((h, w))
    for o in range(octaves):
        n = cells * 2 ** o
        g = rng.random((n + 2, int(n * w / h) + 2))
        im = Image.fromarray((g * 255).astype(np.uint8)).resize((w + w // max(1, int(n * w / h)) * 2, h + h // n * 2), Image.BICUBIC)
        out += np.asarray(im, float)[:h, :w] / 255 / 2 ** o
    return out / 1.95

NEB1 = _noise(5, AW * 2, AH, 3)
NEB2 = _noise(9, AW * 2, AH, 5)
MOONTEX = _noise(17, 512, 256, 4, 6)       # wraps around the moon (equirect)
CRATERS = []
_rng = np.random.default_rng(3)
for k in range(46):
    CRATERS.append((_rng.random() * 2 * math.pi, (_rng.random() - 0.5) * 2.6, 0.03 + _rng.random() ** 3 * 0.18))

def blood_moon(t):
    yy, xx = np.mgrid[0:AH, 0:AW].astype(float)
    # nebula drifting slowly to the left (loops: two noise layers scrolled by full widths)
    off1 = int(t * AW) % AW; off2 = int(t * AW * 2) % AW
    n1 = NEB1[:, off1:off1 + AW]; n2 = NEB2[:, off2:off2 + AW]
    neb = np.clip(n1 * 1.25 - 0.35, 0, 1) ** 1.6 + 0.5 * np.clip(n2 - 0.45, 0, 1)
    img = np.zeros((AH, AW, 3))
    img[...] = (6, 2, 6)
    img += np.stack([125 * neb, 10 * neb, 26 * neb], -1) * NEB_GAIN
    img += np.stack([60 * neb ** 3, 30 * neb ** 3, 70 * neb ** 3], -1)       # faint violet in the brightest gas
    # stars
    rng = np.random.default_rng(1)
    for k in range(130):
        x, y = int(rng.random() * AW), int(rng.random() * AH)
        tw = 0.35 + 0.65 * abs(math.sin(t * 2 * math.pi * (1 + k % 3) + k))
        b = (90 + 165 * rng.random()) * tw
        img[y, x] = np.maximum(img[y, x], (b, b * 0.85, b * 0.9))
        if k % 9 == 0 and y + 1 < AH and x + 1 < AW: img[y:y + 2, x:x + 2] = np.maximum(img[y:y + 2, x:x + 2], (b, b * 0.8, b * 0.85))
    # the moon: shaded sphere with slowly turning surface texture
    cx, cy, r = AW * 0.5, AH * MOON_CY, min(AW, AH) * MOON_R
    dx, dy = (xx - cx) / r, (yy - cy) / r
    d2 = dx ** 2 + dy ** 2
    inside = d2 < 1
    dz = np.sqrt(np.clip(1 - d2, 0, 1))
    lon = np.arctan2(dx, dz) + 0.35 * math.sin(t * 2 * math.pi)   # the moon gently turns back and forth
    lat = np.arcsin(np.clip(dy, -1, 1))
    u = ((lon / (2 * math.pi) + 0.5) % 1 * 511).astype(int); v = ((lat / math.pi + 0.5) * 255).clip(0, 255).astype(int)
    surf = MOONTEX[v, u]
    maria = np.clip((surf - 0.5) * 3 * MARIA, 0, 1)
    shade = 0.55 + 0.45 * surf - 0.35 * MARIA_D * maria
    for (clon, clat, cr) in CRATERS:   # craters: darker floor, bright rim on the lit side
        dl = np.angle(np.exp(1j * (lon - clon)))
        dist = np.sqrt((dl * np.cos(lat)) ** 2 + (lat - clat * 0.5) ** 2)
        shade -= 0.35 * CRATER * np.exp(-(dist / cr) ** 4)
        shade += 0.12 * CRATER * np.exp(-((dist - cr) / (cr * 0.25)) ** 2)
    light = np.clip(0.35 + 0.75 * (dz * 0.6 - dx * 0.45 - dy * 0.25), 0.08, 1.2)   # lit from the upper left
    moon = np.stack([MOON_RGB[0] * shade * light, MOON_RGB[1] * shade * light, MOON_RGB[2] * shade * light], -1)
    img = np.where(inside[..., None], moon, img)
    # atmosphere glow + slow pulse
    pulse = 0.85 + 0.15 * math.sin(t * 2 * math.pi)
    rim = np.sqrt(d2)
    glow = np.exp(-np.clip(rim - 1, 0, None) / 0.12) * (rim >= 0.98)
    img += np.stack([255 * glow, 40 * glow, 40 * glow], -1) * 0.42 * pulse * GLOW
    img += np.stack([120, 10, 14], -1)[None, None] * np.exp(-np.clip(rim - 1, 0, None) / 0.6)[..., None] * 0.35 * pulse * GLOW * (rim >= 1)[..., None]
    # floating embers drifting up
    for k in range(26):
        life = (t * (1 + k % 3) + k / 26) % 1
        x = int((0.1 + 0.8 * ((k * 0.37) % 1)) * AW + 12 * math.sin(life * 6 + k))
        y = int(AH * (1.02 - life * 1.1))
        if 0 <= x < AW - 2 and 0 <= y < AH - 2:
            a = math.sin(life * math.pi)
            img[y:y + 2, x:x + 2] = np.maximum(img[y:y + 2, x:x + 2], np.array((255, 90, 80)) * a)
    if VIGNETTE:
        vx = (xx / AW - 0.5) * 2; vy = (yy / AH - 0.5) * 2
        v = np.clip(1 - VIGNETTE * (vx ** 2 * 0.6 + vy ** 2 * 0.8), 0.15, 1)
        img = img * v[..., None]
        corner = np.clip(np.abs(vx) - 0.4, 0, 1) * np.clip(vy, 0, 1) * 0.9 * (1 - inside)
        img += np.stack([14 * corner, 6 * corner, 34 * corner], -1)
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    g = im.filter(ImageFilter.GaussianBlur(5))
    out = np.clip(np.asarray(im, float) + np.asarray(g, float) * 0.4 * GLOW, 0, 255).astype(np.uint8)
    return Image.fromarray(out)

def _screen(d, gd, W, H, face, im, gl):
    if face != 'front':
        d.rectangle((0, 0, W, H), fill=(12, 12, 15, 255)); return
    art = blood_moon(T[0]).resize((W, H), Image.LANCZOS).convert('RGBA')
    im.paste(art, (0, 0))
    # screen glass sheen
    sh = Image.new('L', (W, H), 0); ImageDraw.Draw(sh).polygon([(0, 0), (W * 0.35, 0), (W * 0.15, H), (0, H)], fill=14)
    im.paste(Image.new('RGBA', (W, H), (255, 255, 255, 255)), (0, 0), sh)
    gl.paste(art.point(lambda v: int(v * 0.75)).convert('RGBA'), (0, 0))
I.IMG['sign_screen'] = _screen

def _frame(d, gd, W, H, face, im, gl):
    yy = np.linspace(0, 1, H)[:, None] * np.ones((1, W))
    k = 0.75 + 0.35 * np.exp(-((yy - 0.2) ** 2) / 0.02)
    arr = np.stack([22 * k, 22 * k, 28 * k, np.full_like(k, 1 / 255)], -1) * 255 / 255
    arr[..., 3] = 255
    im.paste(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA'))
I.IMG['sign_frame'] = _frame

# geometry (aura frame: y down, head top -8, chest ~0..12, front = -z). Sign 22 x 11, held at chest height.
SW, SH, SD = 22.0, 11.0, 0.6
def build():
    ps = [P('sign', (-SW / 2, -SH / 2, -SD / 2), (SW, SH, SD), 'img:sign_screen', pivot=(0, 7.0, -7.6))]
    b = 0.7
    for n, frm, size in (('ft', (-SW / 2 - b, -SH / 2 - b, -SD / 2 - 0.3), (SW + 2 * b, b, SD + 0.6)),
                         ('fb', (-SW / 2 - b, SH / 2, -SD / 2 - 0.3), (SW + 2 * b, b, SD + 0.6)),
                         ('fl', (-SW / 2 - b, -SH / 2, -SD / 2 - 0.3), (b, SH, SD + 0.6)),
                         ('fr', (SW / 2, -SH / 2, -SD / 2 - 0.3), (b, SH, SD + 0.6)),
                         ('back', (-SW / 2, -SH / 2, SD / 2), (SW, SH, 0.3))):
        ps.append(P('sign_' + n, frm, size, 'img:sign_frame', parent='sign'))
    return ps
MODEL = dict(attach='aura', parts=build())
POSE = dict(right_arm=(-30, 0, 8), left_arm=(-30, 0, -8))   # both arms forward, holding the sign

def shot(t, Wd, Hd, yaw, pitch, scale, cy, ss=2):
    T[0] = t
    parts = copy.deepcopy(MODEL['parts'])
    old = gen.S; gen.S = 28
    try:
        tex, glow, ts = gen.build_atlas(parts, width=64)
        sq = R.quads_for(parts, tex, ts, glow, np.eye(4))
    finally:
        gen.S = old
    q = []
    for bname, b in gen.BODY.items():
        q += R.quads_for(b['parts'], b['tex'], b['texsize'], None, R.M(b['pivot'], POSE.get(bname, (0, 0, 0))))
    q += sq
    Wb, Hb = Wd * ss, Hd * ss
    col, gl, mask = R.render(q, Wb, Hb, yaw, pitch, scale * ss, Wd / 2 * ss, cy * ss)
    yy = np.linspace(0, 1, Hb)[:, None, None]; xx = np.linspace(-1, 1, Wb)[None, :, None]
    bg = (16 - 8 * yy) * (1 - 0.2 * xx ** 2) * np.ones((1, 1, 3))
    img = np.where(mask[..., None] > 0, col, bg)
    g = Image.fromarray(np.clip(gl, 0, 255).astype(np.uint8))
    img = img + np.asarray(g.filter(ImageFilter.GaussianBlur(10 * ss))).astype(float) * 0.45
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((Wd, Hd), Image.LANCZOS)

if __name__ == '__main__':
    gen.export('/tmp/sign_body')
    if sys.argv[1] == 'still':
        F1 = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 40); F2 = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 20)
        o = Image.new('RGB', (1500, 760), (10, 10, 12)); d = ImageDraw.Draw(o)
        o.paste(shot(0.2, 620, 640, -8, -4, 13, 250), (0, 100))
        o.paste(shot(0.5, 440, 640, -42, -6, 13, 250), (600, 100))
        art = blood_moon(0.3).resize((440, 206), Image.LANCZOS)
        o.paste(art, (1040, 250)); d.rectangle((1036, 246, 1484, 460), outline=(60, 60, 70), width=3)
        d.text((30, 26), 'Held Sign  \u00B7  Blood Moon', font=F1, fill=(240, 240, 245))
        d.text((32, 74), 'A framed screen held in front of you  \u00B7  the art animates', font=F2, fill=(140, 190, 230))
        d.text((1040, 470), 'Screen art (close-up)', font=F2, fill=(150, 150, 160))
        d.text((1040, 500), 'Nebula drifts, the moon slowly turns', font=F2, fill=(110, 110, 120))
        d.text((1040, 526), 'and pulses, stars twinkle, embers rise.', font=F2, fill=(110, 110, 120))
        o.save('/home/claude/held_sign_design.png')
    elif sys.argv[1] == 'enc':
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', '30', '-i', '/tmp/signv/%04d.jpg', '-f', 'lavfi', '-i', 'anullsrc=r=48000:cl=stereo', '-shortest',
                        '-c:v', 'libx264', '-crf', '17', '-preset', 'veryfast', '-pix_fmt', 'yuv420p', '-profile:v', 'main', '-c:a', 'aac', '-movflags', '+faststart', '/home/claude/sign.tmp.mp4'], check=True)
        os.replace('/home/claude/sign.tmp.mp4', '/home/claude/Held_Sign_preview.mp4')
    else:
        os.makedirs('/tmp/signv', exist_ok=True); N = 180
        for f in range(int(sys.argv[1]), int(sys.argv[2])):
            t = f / N; yaw = -8 - 30 * math.sin(t * 2 * math.pi) ** 2
            shot(t, 720, 720, yaw, -4, 14, 290).save(f'/tmp/signv/{f:04d}.jpg', quality=93)

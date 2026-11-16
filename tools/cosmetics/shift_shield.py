# Shapeshift Shield (id shift_shield): a dark panel where one thin white outline slowly morphs between shapes
# (a sword, a vase, a pawn, a flame), with a short red/cyan glitch each time it starts to change. Original design.
import os, sys, copy, math, json, numpy as np
from PIL import Image, ImageDraw, ImageFilter
import gen, render as R, ideas as I
import blood_shield as B
from gen import P, box_c

NF = 60                      # 60 frames x 166 ms = a 10 s loop
FRAME_MS = 166
FW, FH = 336, 176            # plate face: 21 x 11 texels at 16 px
NPT = 240

def catmull(pts, n=12):
    out = []
    m = len(pts)
    for i in range(m):
        p0, p1, p2, p3 = (np.array(pts[(i + k) % m], float) for k in (-1, 0, 1, 2))
        for s in np.linspace(0, 1, n, endpoint=False):
            out.append(0.5 * (2 * p1 + (-p0 + p2) * s + (2 * p0 - 5 * p1 + 4 * p2 - p3) * s * s + (-p0 + 3 * p1 - 3 * p2 + p3) * s ** 3))
    return out

def resample(pts, n=NPT):
    pts = np.array(pts, float); pts = np.vstack([pts, pts[:1]])
    seg = np.linalg.norm(np.diff(pts, axis=0), axis=1); cum = np.concatenate([[0], np.cumsum(seg)])
    t = np.linspace(0, cum[-1], n, endpoint=False)
    res = np.stack([np.interp(t, cum, pts[:, 0]), np.interp(t, cum, pts[:, 1])], 1)
    k = int(np.argmin(res[:, 0] + np.abs(res[:, 1]) * 0.2))     # start at the leftmost point
    res = np.roll(res, -k, axis=0)
    area = np.sum(res[:, 0] * np.roll(res[:, 1], -1) - np.roll(res[:, 0], -1) * res[:, 1])
    if area < 0: res = np.vstack([res[:1], res[1:][::-1]])      # same winding for every shape
    return res

# shapes in panel units: x -1..1, y -0.5..0.5 (y down)
SWORD = [(-0.95, 0), (-0.6, -0.11), (0.12, -0.11), (0.12, -0.3), (0.26, -0.3), (0.26, -0.07), (0.66, -0.07), (0.66, -0.14),
         (0.84, -0.14), (0.84, 0.14), (0.66, 0.14), (0.66, 0.07), (0.26, 0.07), (0.26, 0.3), (0.12, 0.3), (0.12, 0.11), (-0.6, 0.11)]
VASE = catmull([(-0.82, -0.08), (-0.55, -0.12), (-0.35, -0.3), (0.05, -0.34), (0.45, -0.3), (0.72, -0.2), (0.8, 0.0),
                (0.72, 0.22), (0.45, 0.32), (0.05, 0.34), (-0.35, 0.28), (-0.55, 0.12), (-0.82, 0.08)])
PAWN = catmull([(-0.86, 0.0), (-0.78, -0.17), (-0.6, -0.2), (-0.45, -0.1), (-0.2, -0.08), (0.2, -0.16), (0.5, -0.28),
                (0.78, -0.3), (0.84, 0.0), (0.78, 0.3), (0.5, 0.28), (0.2, 0.16), (-0.2, 0.08), (-0.45, 0.1), (-0.6, 0.2), (-0.78, 0.17)])
FLAME = catmull([(-0.86, 0.04), (-0.6, -0.14), (-0.42, -0.06), (-0.22, -0.3), (0.0, -0.12), (0.2, -0.34), (0.42, -0.14),
                 (0.62, -0.3), (0.82, -0.04), (0.74, 0.22), (0.4, 0.3), (0.0, 0.26), (-0.4, 0.22), (-0.7, 0.18)])
SHAPES = [resample(SWORD), resample(VASE), resample(PAWN), resample(FLAME)]
DOTS = [(-0.12, 0.0), (0.42, 0.02), None, (0.1, 0.05)]       # a small dot inside some shapes

def smooth(x): x = min(1.0, max(0.0, x)); return x * x * (3 - 2 * x)

def outline_at(f):
    """-> (points, dot or None, glitch strength 0..1) for frame f of NF"""
    seg = NF // len(SHAPES); i, k = divmod(f % NF, seg)
    a, b = SHAPES[i], SHAPES[(i + 1) % len(SHAPES)]
    hold = 10
    m = smooth((k - hold + 1) / (seg - hold)) if k >= hold else 0.0
    pts = a * (1 - m) + b * m
    dot = DOTS[i] if m < 0.2 else DOTS[(i + 1) % len(SHAPES)] if m > 0.85 else None
    glitch = 1.0 if k == hold else 0.55 if k == hold + 1 else (0.6 if (f * 7) % 23 == 5 else 0.0)
    return pts, dot, glitch

def face_image(f, SS=4):
    W, H = FW * SS, FH * SS
    yy = np.linspace(-1, 1, H)[:, None]; xx = np.linspace(-1, 1, W)[None, :]
    v = 1 - 0.25 * (xx ** 2 * 0.6 + yy ** 2)
    base = np.stack([16 * v, 16 * v, 17 * v], -1)
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8)).convert('RGBA')
    lines = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    pts, dot, g = outline_at(f)
    sc = (W * 0.42, H * 0.84)
    def to_px(p, dx=0.0, dy=0.0): return (W / 2 + p[0] * sc[0] + dx * SS, H / 2 + p[1] * sc[1] + dy * SS)
    lw = 2 * SS
    def draw(col, dx=0.0, jitter=0.0):
        d = ImageDraw.Draw(lines)
        P = [to_px(p, dx + (jitter * math.sin(p[1] * 40 + f) if jitter else 0.0)) for p in pts]
        d.line(P + [P[0]], fill=col, width=lw, joint='curve')
        if dot: x, y = to_px(dot, dx); d.ellipse((x - 2 * SS, y - 2 * SS, x + 2 * SS, y + 2 * SS), fill=col)
    if g > 0:
        off = 4 * g
        draw((255, 60, 70, int(200 * g)), -off, 2.5 * g)
        draw((70, 230, 255, int(200 * g)), off, 2.5 * g)
    draw((232, 236, 240, 255), 0, 1.5 * g)
    img.alpha_composite(lines)
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, W - 1, H - 1), outline=(46, 47, 50, 255), width=2 * SS)   # faint inner border
    return img.resize((FW, FH), Image.LANCZOS), lines.resize((FW, FH), Image.LANCZOS)

CUR = [0]
CACHE = {}
def _face(d, gd, W, H, face, im, gl):
    if face != 'front':
        d.rectangle((0, 0, W, H), fill=(14, 14, 15, 255)); return
    if CUR[0] not in CACHE: CACHE[CUR[0]] = face_image(CUR[0])
    a, ln = CACHE[CUR[0]]
    a = a.resize((W, H), Image.LANCZOS); ln = ln.resize((W, H), Image.LANCZOS)
    im.paste(a, (0, 0)); gl.paste(ln, (0, 0), ln)
def _frame(d, gd, W, H, face, im, gl):
    im.paste((30, 31, 33, 255), (0, 0, W, H))
    if face == 'front':
        ImageDraw.Draw(im).rectangle((0, 0, W - 1, max(0, H // 4)), fill=(52, 54, 58, 255))
I.IMG['ss_face'] = _face
I.IMG['ss_frame'] = _frame

def build():
    PW, PH, b = 21.0, 11.0, 0.5
    sd = [P('plate', (-PW / 2, -PH / 2, -0.5), (PW, PH, 1.0), 'img:ss_face')]
    for n, frm, size in (('ft', (-PW / 2 - b, -PH / 2 - b, -0.7), (PW + 2 * b, b, 1.4)), ('fb', (-PW / 2 - b, PH / 2, -0.7), (PW + 2 * b, b, 1.4)),
                         ('fl', (-PW / 2 - b, -PH / 2, -0.7), (b, PH, 1.4)), ('fr', (PW / 2, -PH / 2, -0.7), (b, PH, 1.4))):
        sd.append(P(n, frm, size, 'img:ss_frame'))
    sd += [box_c('back', 0, -PH / 2, 0.65, PW, PH, 0.3, 'img:ss_frame'),
           box_c('post_t', 0, -3.0, 1.35, 1.2, 1.2, 1.2, 'gunmetal'), box_c('post_b', 0, 1.8, 1.35, 1.2, 1.2, 1.2, 'gunmetal'),
           box_c('grip', 0, -3.0, 2.3, 1.6, 6.0, 0.9, 'gunmetal')]
    return sd
B.MODEL['parts'] = build()

def shot(f, Wd, Hd, yaw, pitch, scale, cy, held=True, ss=2):
    CUR[0] = f % NF
    q = []
    if held:
        pose = dict(left_arm=(-4, 0, -2), right_arm=(-4, 0, 2))
        for bname, b in gen.BODY.items():
            q += R.quads_for(b['parts'], b['tex'], b['texsize'], None, R.M(b['pivot'], pose.get(bname, (0, 0, 0))))
        q += B.shield_quads(0, R.M((0, 6.0, -4.4), (0, 0, 0)))
    else:
        q = B.shield_quads(0)
    Wb, Hb = Wd * ss, Hd * ss
    col, gl, mask = R.render(q, Wb, Hb, yaw, pitch, scale * ss, Wd / 2 * ss, cy * ss)
    img = np.where(mask[..., None] > 0, col, np.array((22, 22, 22), float))
    g = Image.fromarray(np.clip(gl, 0, 255).astype(np.uint8))
    img = img + np.asarray(g.filter(ImageFilter.GaussianBlur(3 * ss))).astype(float) * 0.35
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((Wd, Hd), Image.LANCZOS)

if __name__ == '__main__':
    R.AMB, R.DIF = 0.62, 0.5
    gen.export('/tmp/sign_body')
    if sys.argv[1] == 'still':
        o = Image.new('RGB', (560 * 4, 800), (22, 22, 22))
        for k, f in enumerate((3, 10, 30, 45)): o.paste(shot(f, 560, 800, 0, -2, 14, 270), (k * 560, 0))
        o.save('/home/claude/shift_shield_design.png')
    elif sys.argv[1] == 'export':
        out = '/home/claude/repo/src/main/resources/assets/therabbits'; name = 'shift_shield'
        os.makedirs(f'{out}/textures/cosmetics/{name}', exist_ok=True)
        gen.S = 16
        for f in range(NF):
            CUR[0] = f
            parts = copy.deepcopy(B.MODEL['parts'])
            tex, glow, (tw, th) = gen.build_atlas(parts, width=64)
            gw = glow.copy(); gw[gw[..., :3].max(-1) < 60] = 0
            Image.fromarray(tex, 'RGBA').save(f'{out}/textures/cosmetics/{name}/{f}.png', optimize=True)
            Image.fromarray(gw, 'RGBA').save(f'{out}/textures/cosmetics/{name}/{f}_glow.png', optimize=True)
        js = dict(attach='shield', texture=[tw, th], glow=True, frames=NF, frameMs=FRAME_MS,
                  parts=[dict(name=p['name'], parent=p['parent'], pivot=p['pivot'], rot=p['rot'], **{'from': p['frm']}, size=p['size'], uv=p['uv']) for p in parts])
        open(f'{out}/cosmetics/{name}.json', 'w').write(json.dumps(js, indent=1))
        icon, _ = face_image(3)
        ic = Image.new('RGBA', (128, 128), (0, 0, 0, 0)); fi = icon.resize((124, 65), Image.LANCZOS)
        ic.paste(fi, (2, 31)); ic.save(f'{out}/textures/gui/cosmetic_icons/{name}.png')
        print('exported', tw, th)
    else:
        os.makedirs('/tmp/ssv', exist_ok=True)
        for fr in range(int(sys.argv[1]), int(sys.argv[2])):
            shot(int(fr * (1000 / 30) / FRAME_MS), 400, 570, 0, -2, 10.5, 190).save(f'/tmp/ssv/{fr:04d}.jpg', quality=88)

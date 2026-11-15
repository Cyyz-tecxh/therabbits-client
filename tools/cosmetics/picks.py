# Refined picks: Koi Spirits (rebuilt), Oni Mask (fixed), Kitsune Mask (new), Spirit Bunny (refined), 5 mini dragon pets.
import math, copy
import numpy as np
from PIL import Image, ImageDraw
import gen, mythics as MY, ideas as I
from gen import P

# ---- koi pattern material: white/gold base with red (and black) patches, fine scales
_prev = gen.paint
def paint(mat, face, w, h):
    if mat.startswith('koipat:'):
        _, seed, base, spot, spot2 = mat.split(':')
        rng = np.random.default_rng(int(seed))
        B, Sp, S2 = (np.array(I.hexc(x), float) for x in (base, spot, spot2))
        yy, xx = np.mgrid[0:h, 0:w].astype(float)
        col = np.ones((h, w, 3)) * B
        f = gen.noise(h, w, 2.2, 2.2, 1)
        blob = f > 0.05
        col[blob] = Sp
        if spot2 != '000000' and True:
            blob2 = gen.noise(h, w, 2.6, 2.6, 1) > 0.32
            col[blob2] = S2
        sc = ((xx + (yy // 3 % 2) * 1.5) % 3 < 0.8) & (yy % 3 < 0.8)
        col[sc] = col[sc] * 0.85
        if face == 'bottom': col = col * 0.9 + 20
        img = np.zeros((h, w, 4)); img[..., :3] = np.clip(col, 0, 255); img[..., 3] = 255
        glow = img.copy(); glow[..., :3] *= 0.18
        return img, glow
    if mat.startswith('fin:'):
        c = np.array(I.hexc(mat.split(':')[1]), float)
        yy, xx = np.mgrid[0:h, 0:w].astype(float)
        rib = (np.abs(((xx / max(1, w)) * 6) % 1 - 0.5) < 0.12)
        img = np.zeros((h, w, 4)); img[..., :3] = c * (0.8 + 0.3 * (yy / max(1, h)))[..., None]; img[rib, :3] = c * 1.15; img[..., 3] = 255
        img[..., :3] = np.clip(img[..., :3], 0, 255)
        glow = img.copy(); glow[..., :3] *= 0.55
        return img, glow
    return _prev(mat, face, w, h)
gen.paint = paint

M = {}
def add(key, name, cat, desc, parts, **kw): M[key] = dict(name=name, cat=cat, desc=desc, parts=parts, **kw)

# ---------------- Koi Spirits (rebuilt): two continuous koi, tapered bodies, fins, flowing tails
KOI_N = 11
KOIS = [('a', 'koipat:3:FFFFFF:E8301C:000000', 'fin:FFD8C8'),      # kohaku: white with red
        ('b', 'koipat:7:FFC23A:FFFFFF:111111', 'fin:FFE7A8')]      # gold with white + black
def build_koi():
    ps = []
    for f, mat, fin in KOIS:
        for i in range(KOI_N):
            t = i / (KOI_N - 1)
            w = 3.7 * (1 - t) ** 0.8 * (0.75 + 0.6 * math.sin(min(1, t * 2.2 + 0.25) * math.pi * 0.5)) + 0.35
            n = 'k%s%d' % (f, i)
            ps.append(P(n, (-w / 2, -w * 0.42, -1.4), (w, w * 0.84, 2.8), mat))
            if 2 <= i <= 6:
                ps.append(P(n + 'd', (-0.06, -w * 0.42 - 0.9, -0.9), (0.12, 0.9, 1.8), fin, parent=n))
        h0 = 'k%s0' % f
        for sx in (-1, 1):
            ps.append(P('k%seye%d' % (f, sx), (-0.1, -0.22, -0.22), (0.2, 0.44, 0.44), 'neon:101010', pivot=(sx * 1.45, -0.35, -0.6), parent=h0))
            ps.append(P('k%sbar%d' % (f, sx), (-0.06, -0.06, -1.2), (0.12, 0.12, 1.2), fin, pivot=(sx * 0.6, 0.6, -0.9), rot=(20, sx * 20, 0), parent=h0))
            ps.append(P('k%spec%d' % (f, sx), (0 if sx > 0 else -2.6, -0.06, -0.8), (2.6, 0.12, 1.8), fin, pivot=(sx * 1.4, 0.9, 0.6), rot=(0, sx * -25, sx * -25), parent='k%s1' % f))
        tl = 'k%s%d' % (f, KOI_N - 1)
        for j, a in enumerate((-32, 32)):
            ps.append(P('k%stail%d' % (f, j), (-0.08, -1.3, 0), (0.16, 2.6, 3.6), fin, pivot=(0, 0, 1.0), rot=(a, 0, 0), parent=tl))
    for i in range(10):
        ps.append(P('kbub%d' % i, (-0.25, -0.25, -0.25), (0.5, 0.5, 0.5), 'neon:CFF4FF'))
    return ps
add('koi', 'Koi Spirits', 'AURAS', 'Two spirit koi swim figure-eights around you · bubbles rise', build_koi())

def pose_koi(t, ps0):
    ps = copy.deepcopy(ps0); by = {p['name']: p for p in ps}
    for k, (f, _, _) in enumerate(KOIS):
        ph = k * math.pi
        def path(s):
            return (math.sin(s) * 13.5, 7 + 3.0 * math.sin(s * 2 + 1) - 4 * k + 2, math.sin(2 * s) * 9.5)
        s0 = t * 0.55 + ph
        pts = [path(s0 - i * 0.1) for i in range(KOI_N + 1)]
        hp = path(s0 + 0.1)
        for i in range(KOI_N):
            p = by['k%s%d' % (f, i)]; p['pivot'] = list(pts[i])
            r = MY.look(pts[i], pts[i - 1] if i else hp)
            r[1] += 9 * math.sin(t * 5 - i * 0.7) * (i / KOI_N)      # swimming wiggle toward the tail
            p['rot'] = r
        for j, a in enumerate((-32, 32)):
            by['k%stail%d' % (f, j)]['rot'] = [a, 20 * math.sin(t * 5 - 7), 0]
    for i in range(10):
        life = (t * 0.3 + i / 10) % 1; a = i * 2.3
        p = by['kbub%d' % i]; p['pivot'] = [math.cos(a) * (10 + i % 3 * 2), 20 - life * 26, math.sin(a) * (10 + i % 3 * 2)]
        MY.scaled(p, 0.6 + 0.6 * (1 - life))
    return ps

# ---------------- masks (worn on the side of the head, like festival masks)
def mask(prefix, face_mat, side, extra):
    sx = 1 if side == 'left' else -1
    root = prefix
    ps = [P(root, (-3.4, -4.0, -0.45), (6.8, 8.0, 0.9), face_mat, pivot=(sx * 4.6, -4.4, 0.6), rot=(-6, sx * 80 + 180, sx * 10))]
    ps.append(P(prefix + 'cord', (-4.35, -0.5, -4.35), (8.7, 1.0, 8.7), 'metal:15151A', pivot=(0, -4.6, 0)))
    ps += extra(root)
    return ps

I.IMG['oni2'] = None
def _oni(d, g, W, H, face, im, gl):
    red, dark = (190, 22, 30, 255), (110, 10, 16, 255)
    d.rectangle((0, 0, W, H), fill=red)
    if face != 'front': d.rectangle((0, 0, W, H), fill=dark); return
    # brows, glowing gold eyes, wide fanged mouth, cheek lines
    for sx in (-1, 1):
        cx = W / 2 + sx * W * 0.22
        d.polygon([(W / 2 + sx * W * 0.05, H * 0.24), (W / 2 + sx * W * 0.46, H * 0.12), (W / 2 + sx * W * 0.46, H * 0.22), (W / 2 + sx * W * 0.08, H * 0.32)], fill=(20, 8, 10, 255))
        eye = [(cx - sx * W * 0.13, H * 0.36), (cx + sx * W * 0.14, H * 0.3), (cx + sx * W * 0.09, H * 0.44)]
        d.polygon(eye, fill=(255, 214, 80, 255)); g.polygon(eye, fill=(255, 190, 40))
        d.line([(cx + sx * W * 0.12, H * 0.5), (cx + sx * W * 0.2, H * 0.62)], fill=dark, width=int(W * 0.03))
    d.polygon([(W * 0.44, H * 0.4), (W * 0.56, H * 0.4), (W * 0.6, H * 0.56), (W * 0.4, H * 0.56)], fill=dark)
    d.rounded_rectangle((W * 0.16, H * 0.64, W * 0.84, H * 0.86), radius=int(W * 0.05), fill=(18, 6, 8, 255))
    for k in range(6):
        d.rectangle((W * (0.2 + k * 0.105), H * 0.64, W * (0.27 + k * 0.105), H * 0.71), fill=(245, 238, 225, 255))
    for x in (0.2, 0.74):
        d.polygon([(W * x, H * 0.86), (W * (x + 0.03), H * 0.98), (W * (x + 0.06), H * 0.86)], fill=(245, 238, 225, 255))
        d.polygon([(W * x, H * 0.64), (W * (x + 0.03), H * 0.52), (W * (x + 0.06), H * 0.64)], fill=(245, 238, 225, 255))
I.IMG['oni2'] = _oni
def oni_extra(root):
    out = [P(root + 'brow', (-3.2, -0.5, -0.5), (6.4, 1.0, 0.8), 'metal:7A0C12', pivot=(0, -1.6, -0.5), parent=root),
           P(root + 'nose', (-0.7, -0.8, -0.6), (1.4, 1.6, 0.8), 'metal:9A1218', pivot=(0, 0.2, -0.7), parent=root),
           P(root + 'jaw', (-2.8, -0.4, -0.5), (5.6, 0.8, 0.7), 'metal:7A0C12', pivot=(0, 3.4, -0.4), parent=root)]
    for s in (-1, 1):
        out += [P(root + 'h%d' % s, (-0.55, -2.6, -0.55), (1.1, 2.6, 1.1), 'gold', pivot=(s * 2.2, -3.6, 0), rot=(-15, 0, s * 22), parent=root),
                P(root + 'h2%d' % s, (-0.4, -2.0, -0.4), (0.8, 2.0, 0.8), 'gold', pivot=(0, -2.6, 0), rot=(-10, 0, s * 25), parent=root + 'h%d' % s),
                P(root + 'h3%d' % s, (-0.25, -1.3, -0.25), (0.5, 1.3, 0.5), 'fab_cream', pivot=(0, -2.0, 0), rot=(-5, 0, s * 20), parent=root + 'h2%d' % s)]
    return out
add('oni_mask', 'Oni Mask', 'HATS', 'Red demon mask on the side of the head · gold horns · glowing eyes', mask('oni', 'img:oni2', 'left', oni_extra))

def _kit(d, g, W, H, face, im, gl):
    white, red, gold = (246, 244, 240, 255), (214, 30, 46, 255), (240, 190, 70, 255)
    d.rectangle((0, 0, W, H), fill=white)
    if face != 'front': return
    for sx in (-1, 1):
        cx = W / 2 + sx * W * 0.22
        d.polygon([(cx - sx * W * 0.16, H * 0.42), (cx + sx * W * 0.16, H * 0.3), (cx + sx * W * 0.1, H * 0.4)], fill=(20, 16, 18, 255))
        for k in range(3):   # red flame markings above the eyes and on the cheeks
            d.polygon([(cx - sx * W * 0.12 + sx * k * W * 0.05, H * 0.28), (cx - sx * W * 0.06 + sx * k * W * 0.05, H * (0.12 + k * 0.02)), (cx + sx * k * W * 0.05, H * 0.27)], fill=red)
        d.line([(W / 2 + sx * W * 0.2, H * 0.58), (W / 2 + sx * W * 0.42, H * 0.54)], fill=red, width=int(W * 0.025))
        d.line([(W / 2 + sx * W * 0.2, H * 0.64), (W / 2 + sx * W * 0.42, H * 0.64)], fill=red, width=int(W * 0.025))
        g.line([(W / 2 + sx * W * 0.2, H * 0.58), (W / 2 + sx * W * 0.42, H * 0.54)], fill=(255, 60, 80), width=int(W * 0.02))
    d.ellipse((W * 0.45, H * 0.14, W * 0.55, H * 0.24), fill=gold); g.ellipse((W * 0.45, H * 0.14, W * 0.55, H * 0.24), fill=(255, 200, 80))
    d.line([(W * 0.4, H * 0.86), (W * 0.5, H * 0.8), (W * 0.6, H * 0.86)], fill=red, width=int(W * 0.025))
I.IMG['kitsune'] = _kit
def kit_extra(root):
    out = [P(root + 'snout', (-1.3, -1.0, -2.0), (2.6, 2.0, 2.0), 'fur', pivot=(0, 1.8, -0.4), parent=root),
           P(root + 'nose', (-0.5, -0.4, -0.3), (1.0, 0.7, 0.4), 'neon:1A1416', pivot=(0, 1.0, -2.55), parent=root)]
    for s in (-1, 1):
        out += [P(root + 'ear%d' % s, (-1.3, -1.6, -0.4), (2.6, 1.6, 0.8), 'fur', pivot=(s * 2.0, -3.6, 0), rot=(0, 0, s * 14), parent=root),
                P(root + 'ear2%d' % s, (-0.85, -1.2, -0.35), (1.7, 1.2, 0.7), 'fur', pivot=(s * 0.15, -1.6, 0), parent=root + 'ear%d' % s),
                P(root + 'ear3%d' % s, (-0.4, -0.9, -0.3), (0.8, 0.9, 0.6), 'fur', pivot=(s * 0.1, -1.2, 0), parent=root + 'ear2%d' % s),
                P(root + 'eari%d' % s, (-0.75, -1.4, -0.1), (1.5, 1.4, 0.2), 'metal:D61E2E', pivot=(0, -0.1, -0.42), parent=root + 'ear%d' % s),
                P(root + 'eari2%d' % s, (-0.4, -1.0, -0.1), (0.8, 1.0, 0.2), 'metal:D61E2E', pivot=(0, -0.1, -0.37), parent=root + 'ear2%d' % s)]
    out += [P(root + 'tassel', (-0.15, 0, -0.15), (0.3, 3.5, 0.3), 'neon:E0203C', pivot=(-2.6, 3.8, 0.5), parent=root),
            P(root + 'bell', (-0.5, -0.5, -0.5), (1.0, 1.0, 1.0), 'gold', pivot=(0, 3.7, 0), parent=root + 'tassel')]
    return out
add('kitsune_mask', 'Kitsune Mask', 'HATS', 'White fox mask · red markings · gold bell tassel', mask('kit', 'img:kitsune', 'right', kit_extra))

# ---------------- Spirit Bunny (refined)
def build_bunny():
    W_ = 'neon:EEF7FF'
    ps = [P('bb', (-2.3, -2.4, -3.2), (4.6, 4.6, 6.4), W_, pivot=(9.5, 20.2, 1.5), rot=(0, -20, 0)),
          P('bhp', (-2.6, -0.8, -2.0), (5.2, 2.6, 3.6), W_, pivot=(0, 0.6, 1.8), parent='bb'),
          P('bh', (-1.9, -3.4, -2.0), (3.8, 3.4, 3.6), 'neon:F6FBFF', pivot=(0, -1.6, -3.0), parent='bb'),
          P('bm', (-0.9, -0.7, -0.5), (1.8, 1.2, 0.8), 'neon:FFFFFF', pivot=(0, -0.9, -2.0), parent='bh'),
          P('bn', (-0.3, -0.25, -0.15), (0.6, 0.5, 0.3), 'neon:FF9AC8', pivot=(0, -0.9, -2.55), parent='bh'),
          P('bt', (-1.0, -1.0, 0), (2.0, 2.0, 1.6), 'neon:FFFFFF', pivot=(0, -1.2, 3.1), parent='bb')]
    for s in (-1, 1):
        ps += [P('be%d' % s, (-0.6, -4.6, -0.35), (1.2, 4.6, 0.7), 'neon:F6FBFF', pivot=(s * 0.95, -3.2, 0.2), rot=(-18, 0, s * 12), parent='bh'),
               P('bei%d' % s, (-0.3, -3.8, -0.06), (0.6, 3.8, 0.12), 'neon:6EC6FF', pivot=(0, -0.2, -0.36), parent='be%d' % s),
               P('bey%d' % s, (-0.3, -0.4, -0.12), (0.6, 0.8, 0.24), 'neon:1E78C8', pivot=(s * 1.0, -1.9, -2.0), parent='bh'),
               P('bf%d' % s, (-0.6, -0.3, -1.6), (1.2, 0.6, 2.4), 'neon:FFFFFF', pivot=(s * 1.6, 2.1, 2.0), parent='bb')]
    for i in range(8):
        ps.append(P('bsp%d' % i, (-0.18, -0.18, -0.18), (0.36, 0.36, 0.36), 'neon:6EC6FF' if i % 2 else 'neon:FFFFFF',
                    pivot=(9.5 + math.sin(i * 1.7) * 4, 14 + (i * 1.3) % 7, 2 + math.cos(i * 2.1) * 4)))
    return ps
add('spirit_bunny', 'Spirit Bunny', 'PETS', 'Glowing rabbit that hops after you · leaves light sparks', build_bunny())

# ---------------- mini dragon pets (all five dragons)
for k in ('eternal_dragon', 'shadow_dragon', 'frost_dragon', 'infernal_dragon', 'celestial_dragon'):
    add('pet_' + k, MY.PREMIUM[k]['name'] + ' Pet', 'PETS', 'Full-size companion · coils in the air beside you', [], dragon=k)

# auras kept from the idea sheet
for k in ('clockwork', 'petal_storm'):
    M[k] = I.M[k]

# ================= 3D masks (real geometry instead of painted plates) =================
_p2 = gen.paint
def paint3(mat, face, w, h):
    if mat.startswith('flat:'):
        c = np.array(I.hexc(mat.split(':')[1]), float)
        k = {'top': 1.08, 'bottom': 0.8}.get(face, 1.0)
        yy = np.linspace(0, 1, h)[:, None] * np.ones((1, w))
        img = np.zeros((h, w, 4)); img[..., :3] = np.clip(c * k * (1.04 - 0.08 * yy)[..., None], 0, 255); img[..., 3] = 255
        gen.bevel(img, face, 0.12, max(1, gen.S // 4))
        return img, None
    return _p2(mat, face, w, h)
gen.paint = paint3

def mask3d(prefix, side, build):
    sx = 1 if side == 'left' else -1
    root = prefix
    ps = [P(root, (-0.01, -0.01, -0.01), (0.02, 0.02, 0.02), 'flat:000000', pivot=(sx * 4.4, -4.4, 0.4), rot=(-6, sx * 80 + 180, sx * 10)),
          P(prefix + 'cord', (-4.35, -0.5, -4.35), (8.7, 1.0, 8.7), 'flat:15151A', pivot=(0, -4.6, 0))]
    def B(n, frm, size, mat, pivot=(0, 0, 0), rot=(0, 0, 0), parent=None):
        ps.append(P(prefix + n, frm, size, mat, pivot=pivot, rot=rot, parent=prefix + (parent or '')))
    build(B)
    return ps

def oni3d(B):
    R, D, K = 'flat:C0161E', 'flat:7E0A10', 'flat:120708'
    B('face', (-3.0, -3.6, -0.5), (6.0, 7.4, 1.0), R)                                  # centre plate
    for s in (-1, 1):                                                                   # cheeks curve back
        B('cheek%d' % s, (-1.0, -3.0, -0.45), (2.0, 6.0, 0.9), R, pivot=(s * 3.6, 0.2, 0.5), rot=(0, s * 35, 0))
        B('brow%d' % s, (-1.6, -0.5, -0.6), (3.2, 1.0, 1.2), D, pivot=(s * 1.6, -2.3, -0.7), rot=(0, s * 10, s * 16))
        B('sock%d' % s, (-0.9, -0.55, -0.1), (1.8, 1.1, 0.3), K, pivot=(s * 1.45, -1.1, -0.55))
        B('eye%d' % s, (-0.6, -0.3, -0.1), (1.2, 0.6, 0.3), 'neon:FFC83A', pivot=(s * 1.45, -1.1, -0.72), rot=(0, 0, s * 12))
        B('cb%d' % s, (-0.8, -0.6, -0.5), (1.6, 1.2, 0.9), R, pivot=(s * 1.9, 0.9, -0.6))
        B('fang%d' % s, (-0.25, 0, -0.25), (0.5, 1.4, 0.5), 'flat:F2EDE2', pivot=(s * 1.7, 2.5, -0.95))
        B('fangu%d' % s, (-0.22, -1.1, -0.22), (0.44, 1.1, 0.44), 'flat:F2EDE2', pivot=(s * 1.0, 2.2, -0.95))
        B('horn%d' % s, (-0.6, -2.6, -0.6), (1.2, 2.6, 1.2), 'gold', pivot=(s * 2.0, -3.4, 0.2), rot=(-10, 0, s * 20))
        B('horn2%d' % s, (-0.42, -2.0, -0.42), (0.84, 2.0, 0.84), 'gold', pivot=(0, -2.6, 0), rot=(-10, 0, s * 22), parent='horn%d' % s)
        B('horn3%d' % s, (-0.25, -1.4, -0.25), (0.5, 1.4, 0.5), 'flat:F2EDE2', pivot=(0, -2.0, 0), rot=(-6, 0, s * 18), parent='horn2%d' % s)
    B('nose', (-0.8, -0.7, -1.3), (1.6, 1.6, 1.3), D, pivot=(0, 0.4, -0.5))
    B('nost', (-0.9, 0.5, -1.35), (1.8, 0.4, 1.0), R, pivot=(0, 0.4, -0.5))
    B('mouth', (-2.2, -0.6, -0.2), (4.4, 1.5, 0.4), K, pivot=(0, 2.6, -0.6))
    for k in range(5):
        B('tooth%d' % k, (-0.3, -0.3, -0.15), (0.6, 0.6, 0.3), 'flat:F2EDE2', pivot=(-1.6 + k * 0.8, 2.2, -0.82))
    B('chin', (-2.0, -0.5, -0.5), (4.0, 1.0, 1.0), D, pivot=(0, 4.0, -0.2))

def kitsune3d(B):
    W_, Rd, K = 'flat:FFFFFF', 'flat:D61E2E', 'flat:141012'
    B('face', (-2.8, -3.4, -0.5), (5.6, 6.4, 1.0), W_)
    for s in (-1, 1):
        B('cheek%d' % s, (-0.9, -2.6, -0.45), (1.8, 5.0, 0.9), W_, pivot=(s * 3.3, 0.0, 0.4), rot=(0, s * 35, 0))
        B('slit%d' % s, (-0.9, -0.18, -0.1), (1.8, 0.36, 0.3), K, pivot=(s * 1.4, -1.0, -0.55), rot=(0, 0, s * -18))
        B('mk%d' % s, (-0.15, -0.9, -0.08), (0.3, 1.8, 0.16), Rd, pivot=(s * 1.4, -2.4, -0.55), rot=(0, 0, s * 20))
        B('mk2%d' % s, (-0.12, -0.7, -0.08), (0.24, 1.4, 0.16), Rd, pivot=(s * 2.0, -2.2, -0.55), rot=(0, 0, s * 35))
        B('wh%d' % s, (-1.1, -0.1, -0.08), (2.2, 0.2, 0.16), Rd, pivot=(s * 2.6, 0.9, -0.5), rot=(0, s * 35, s * -8))
        B('wh2%d' % s, (-1.1, -0.1, -0.08), (2.2, 0.2, 0.16), Rd, pivot=(s * 2.6, 1.6, -0.5), rot=(0, s * 35, 0))
        B('ear%d' % s, (-1.3, -1.8, -0.5), (2.6, 1.8, 1.0), W_, pivot=(s * 1.9, -3.3, 0), rot=(0, 0, s * 12))
        B('ear2%d' % s, (-0.85, -1.4, -0.45), (1.7, 1.4, 0.9), W_, pivot=(s * 0.15, -1.8, 0), parent='ear%d' % s)
        B('ear3%d' % s, (-0.4, -1.0, -0.4), (0.8, 1.0, 0.8), W_, pivot=(s * 0.1, -1.4, 0), parent='ear2%d' % s)
        B('eari%d' % s, (-0.7, -1.5, -0.1), (1.4, 1.5, 0.2), Rd, pivot=(0, -0.1, -0.52), parent='ear%d' % s)
        B('eari2%d' % s, (-0.4, -1.2, -0.1), (0.8, 1.2, 0.2), Rd, pivot=(0, 0, -0.47), parent='ear2%d' % s)
    B('muzzle', (-1.3, -1.0, -2.6), (2.6, 2.0, 2.6), W_, pivot=(0, 1.0, -0.4))           # fox snout sticks out
    B('muzz2', (-0.9, -0.7, -1.4), (1.8, 1.4, 1.4), W_, pivot=(0, 1.15, -3.0))
    B('nose', (-0.5, -0.4, -0.3), (1.0, 0.7, 0.4), K, pivot=(0, 0.75, -4.45))
    B('smile', (-0.9, -0.1, -0.1), (1.8, 0.2, 0.2), Rd, pivot=(0, 2.05, -3.4))
    B('gem', (-0.4, -0.4, -0.15), (0.8, 0.8, 0.3), 'neon:FFD24A', pivot=(0, -2.6, -0.62), rot=(0, 0, 45))
    B('tassel', (-0.15, 0, -0.15), (0.3, 3.5, 0.3), 'neon:E0203C', pivot=(-2.4, 3.0, 0.4))
    B('bell', (-0.5, -0.5, -0.5), (1.0, 1.0, 1.0), 'gold', pivot=(0, 3.7, 0), parent='tassel')

add('oni_mask', 'Oni Mask', 'HATS', '3D red demon mask · gold horns · glowing eyes · fangs', mask3d('oni', 'left', oni3d))
add('kitsune_mask', 'Kitsune Mask', 'HATS', '3D white fox mask · snout · red markings · bell tassel', mask3d('kit', 'right', kitsune3d))

# ================= masks v3: sculpted, lacquered, trimmed =================
_p3 = gen.paint
def paint4(mat, face, w, h):
    if mat.startswith('lacq:'):            # glossy lacquer: gradient, darker edges, soft highlight band
        c = np.array(I.hexc(mat.split(':')[1]), float)
        yy = np.linspace(0, 1, h)[:, None] * np.ones((1, w)); xx = np.linspace(-1, 1, w)[None, :] * np.ones((h, 1))
        k = 1.12 - 0.3 * yy - 0.18 * xx ** 2 + 0.28 * np.exp(-((yy - 0.18) ** 2) / 0.006) * (face in ('front', 'top'))
        if face == 'bottom': k = k * 0.7
        img = np.zeros((h, w, 4)); img[..., :3] = np.clip(c[None, None] * k[..., None] + 18 * np.exp(-((yy - 0.18) ** 2) / 0.006)[..., None] * (face == 'front'), 0, 255); img[..., 3] = 255
        gen.bevel(img, face, 0.25, max(1, gen.S // 3))
        return img, None
    return _p3(mat, face, w, h)
gen.paint = paint4

def arc(B, name, n, base_pivot, base_rot, parent, segs, mat, tip=None, bend=(0, 0, 0), depth=None):
    """chain of tapering segments: segs = [(w, len), ...], each bends by `bend` degrees; depth fixes thickness (flat ears)"""
    prev = parent
    for j, (w, L) in enumerate(segs):
        nm = '%s%d' % (name, j)
        dd = depth if depth is not None else w
        B(nm, (-w / 2, -L, -dd / 2), (w, L, dd), tip if (tip and j == len(segs) - 1) else mat,
          pivot=base_pivot if j == 0 else (0, -segs[j - 1][1], 0), rot=base_rot if j == 0 else bend, parent=prev)
        prev = nm

def oni3(B):
    R, R2, D, K, T, G = 'lacq:C8141C', 'lacq:A00E16', 'lacq:6A070C', 'flat:0E0506', 'flat:F4EEDF', 'gold'
    B('face', (-2.9, -3.4, -0.5), (5.8, 7.0, 1.0), R)
    B('fore', (-2.5, -1.2, -0.6), (5.0, 1.6, 1.1), R, pivot=(0, -3.2, -0.25))                       # bulging forehead
    for s in (-1, 1):
        B('cheek%d' % s, (-1.0, -3.0, -0.5), (2.0, 6.0, 1.0), R2, pivot=(s * 3.5, 0.2, 0.4), rot=(0, s * 38, 0))
        B('temple%d' % s, (-0.8, -1.4, -0.45), (1.6, 2.8, 0.9), R2, pivot=(s * 3.3, -2.6, 0.5), rot=(0, s * 45, s * 8))
        # brow: two angled wedges per side, furrowed toward the centre
        B('brow%d' % s, (-1.7, -0.45, -0.7), (3.4, 0.9, 1.4), D, pivot=(s * 1.65, -2.0, -0.75), rot=(0, s * 12, s * 22))
        B('browt%d' % s, (-0.9, -0.35, -0.6), (1.8, 0.7, 1.2), D, pivot=(s * 2.9, -2.6, -0.4), rot=(0, s * 30, s * 35))
        B('sock%d' % s, (-1.0, -0.6, -0.1), (2.0, 1.2, 0.35), K, pivot=(s * 1.45, -0.95, -0.6), rot=(0, 0, s * 10))
        B('eye%d' % s, (-0.75, -0.32, -0.1), (1.5, 0.64, 0.3), 'neon:FFC83A', pivot=(s * 1.45, -0.95, -0.78), rot=(0, 0, s * 14))
        B('pupil%d' % s, (-0.16, -0.16, -0.08), (0.32, 0.32, 0.16), 'neon:FF5A10', pivot=(s * 1.35, -0.95, -0.95))
        B('cb%d' % s, (-0.9, -0.65, -0.6), (1.8, 1.3, 1.1), R, pivot=(s * 1.95, 0.8, -0.6), rot=(0, s * 10, 0))
        B('wr%d' % s, (-0.6, -0.1, -0.1), (1.2, 0.2, 0.2), D, pivot=(s * 2.1, 1.7, -1.05), rot=(0, 0, s * -25))       # cheek wrinkle
        B('nwr%d' % s, (-0.1, -0.5, -0.1), (0.2, 1.0, 0.2), D, pivot=(s * 0.95, 0.9, -1.15), rot=(0, 0, s * 20))     # nose fold
        # tusks curving up from the lower jaw
        arc(B, 'tusk%d_' % s, 0, (s * 1.85, 3.0, -1.0), (-15, 0, s * -10), '', [(0.6, 0.9), (0.46, 0.8), (0.3, 0.7)], T, bend=(-25, 0, s * -10))
        B('fangu%d' % s, (-0.22, 0, -0.22), (0.44, 1.0, 0.44), T, pivot=(s * 1.05, 1.95, -1.0))
        # gold trim along the jaw and cheek edges
        B('trim%d' % s, (-0.15, -2.8, -0.15), (0.3, 5.6, 0.3), G, pivot=(s * 3.9, 0.3, 0.85), rot=(0, s * 38, 0))
        # horns: 5 tapering, outward-curving segments
        arc(B, 'horn%d_' % s, 0, (s * 2.0, -3.6, 0.2), (-12, 0, s * 18), '', [(1.3, 1.6), (1.1, 1.5), (0.9, 1.4), (0.66, 1.2), (0.4, 1.0)], G, tip=T, bend=(-8, 0, s * 16))
        B('hornb%d' % s, (-0.85, -0.3, -0.85), (1.7, 0.6, 1.7), D, pivot=(s * 2.0, -3.6, 0.2))
    B('nose', (-0.8, -0.8, -1.3), (1.6, 1.8, 1.3), R, pivot=(0, 0.3, -0.6))
    B('nosebr', (-0.4, -1.4, -0.6), (0.8, 1.4, 0.6), R, pivot=(0, -0.5, -0.9))
    for s in (-1, 1):
        B('nost%d' % s, (-0.3, -0.2, -0.1), (0.6, 0.4, 0.2), K, pivot=(s * 0.45, 1.05, -1.95))
    B('lip', (-2.4, -0.3, -0.6), (4.8, 0.6, 1.0), D, pivot=(0, 1.75, -0.7))
    B('mouth', (-2.2, -0.7, -0.2), (4.4, 1.6, 0.4), K, pivot=(0, 2.6, -0.65))
    B('gum', (-2.1, -0.15, -0.1), (4.2, 0.3, 0.2), 'flat:7A1A22', pivot=(0, 2.0, -0.88))
    for k in range(6):
        B('tooth%d' % k, (-0.28, 0, -0.14), (0.56, 0.55, 0.28), T, pivot=(-1.75 + k * 0.7, 2.05, -0.9))
        B('ltooth%d' % k, (-0.25, -0.45, -0.14), (0.5, 0.45, 0.28), T, pivot=(-1.75 + k * 0.7, 3.35, -0.9))
    B('chin', (-2.0, -0.5, -0.6), (4.0, 1.1, 1.1), D, pivot=(0, 3.95, -0.3))
    B('chintrim', (-2.1, -0.1, -0.1), (4.2, 0.25, 0.25), G, pivot=(0, 4.55, -0.85))
    for k in range(5):                                        # black mane tufts across the top
        B('mane%d' % k, (-0.45, -1.6, -0.45), (0.9, 1.6, 0.9), 'flat:121214', pivot=(-2.0 + k, -4.0, 0.3), rot=(15, 0, (k - 2) * 14))

def kitsune3(B):
    W_, W2, Rd, K, G = 'lacq:FFFFFF', 'lacq:ECEAE6', 'lacq:D81E30', 'flat:141012', 'gold'
    B('face', (-2.7, -3.2, -0.5), (5.4, 6.2, 1.0), W_)
    B('fore', (-2.2, -1.0, -0.5), (4.4, 1.6, 1.0), W_, pivot=(0, -3.0, -0.3))
    for s in (-1, 1):
        B('cheek%d' % s, (-1.0, -2.6, -0.5), (2.0, 5.0, 1.0), W2, pivot=(s * 3.3, 0.3, 0.4), rot=(0, s * 38, 0))
        B('jowl%d' % s, (-0.9, -0.7, -0.6), (1.8, 1.4, 1.2), W_, pivot=(s * 2.2, 2.4, -0.2), rot=(0, s * 25, 0))
        # almond eye slits with gold rims
        B('eyer%d' % s, (-1.15, -0.32, -0.12), (2.3, 0.64, 0.25), G, pivot=(s * 1.45, -0.9, -0.55), rot=(0, 0, s * -20))
        B('slit%d' % s, (-1.0, -0.18, -0.1), (2.0, 0.36, 0.3), K, pivot=(s * 1.45, -0.9, -0.66), rot=(0, 0, s * -20))
        B('glint%d' % s, (-0.15, -0.1, -0.05), (0.3, 0.2, 0.1), 'neon:FFE070', pivot=(s * 1.1, -0.85, -0.82))
        # red flame markings: three raised strokes each side + cheek swirls
        for j, (x, y, a, L) in enumerate(((1.0, -2.3, 18, 1.6), (1.7, -2.15, 32, 1.4), (2.3, -1.8, 48, 1.0))):
            B('mk%d_%d' % (s, j), (-0.14, -L / 2, -0.08), (0.28, L, 0.18), Rd, pivot=(s * x, y, -0.58), rot=(0, 0, s * a))
        B('sw%d' % s, (-0.8, -0.12, -0.08), (1.6, 0.24, 0.18), Rd, pivot=(s * 2.7, 0.8, -0.45), rot=(0, s * 38, s * -15))
        B('sw2%d' % s, (-0.7, -0.12, -0.08), (1.4, 0.24, 0.18), Rd, pivot=(s * 2.75, 1.4, -0.45), rot=(0, s * 38, s * 5))
        # tall pointed ears: 4 tapering tiers with red inner tiers
        arc(B, 'ear%d_' % s, 0, (s * 1.8, -3.4, 0.1), (-6, 0, s * 14), '', [(2.6, 1.3), (2.0, 1.2), (1.4, 1.1), (0.7, 1.0)], W_, bend=(0, 0, s * 4))
        arc(B, 'eari%d_' % s, 0, (0, -0.1, -0.52), (0, 0, 0), 'ear%d_0' % s, [(1.6, 1.3), (1.1, 1.2), (0.6, 1.0)], Rd)
        B('eartip%d' % s, (-0.2, -0.5, -0.2), (0.4, 0.5, 0.4), Rd, pivot=(0, -1.0, 0), parent='ear%d_3' % s)
        for j in range(2):   # whiskers (raised thin rods)
            B('wh%d_%d' % (s, j), (-0.06, -0.06, 0), (0.12, 0.12, 2.0), K, pivot=(s * 0.8, 1.05 + j * 0.45, -3.6), rot=(j * 8, s * 115, 0))
    # long elegant snout
    B('muzzle', (-1.3, -1.0, -2.4), (2.6, 2.0, 2.4), W_, pivot=(0, 0.9, -0.4))
    B('muzz2', (-1.0, -0.8, -1.6), (2.0, 1.6, 1.6), W_, pivot=(0, 1.0, -2.8))
    B('muzz3', (-0.7, -0.6, -1.0), (1.4, 1.2, 1.0), W_, pivot=(0, 1.05, -4.3))
    B('bridge', (-0.25, -0.15, -3.6), (0.5, 0.3, 3.6), Rd, pivot=(0, 0.0, -1.0))
    B('nose', (-0.55, -0.4, -0.35), (1.1, 0.8, 0.5), K, pivot=(0, 0.75, -5.35))
    B('jaw', (-0.8, -0.2, -2.6), (1.6, 0.4, 2.6), W2, pivot=(0, 1.85, -2.6))
    B('smile', (-0.7, -0.08, -0.08), (1.4, 0.16, 0.16), Rd, pivot=(0, 1.65, -5.2), rot=(0, 0, 0))
    B('gem', (-0.42, -0.42, -0.15), (0.84, 0.84, 0.3), 'neon:FFD24A', pivot=(0, -2.75, -0.82), rot=(0, 0, 45))
    B('gemr', (-0.6, -0.6, -0.1), (1.2, 1.2, 0.2), G, pivot=(0, -2.75, -0.7), rot=(0, 0, 45))
    B('trim', (-2.8, -0.12, -0.12), (5.6, 0.24, 0.24), G, pivot=(0, 3.1, -0.55))
    B('tassel', (-0.15, 0, -0.15), (0.3, 3.0, 0.3), 'lacq:D81E30', pivot=(-2.6, 3.0, 0.4))
    B('knot', (-0.35, -0.35, -0.35), (0.7, 0.7, 0.7), 'lacq:D81E30', pivot=(0, 0, 0), parent='tassel')
    B('bell', (-0.55, -0.55, -0.55), (1.1, 1.1, 1.1), G, pivot=(0, 3.2, 0), parent='tassel')
    B('bells', (-0.6, -0.08, -0.08), (1.2, 0.16, 0.16), 'flat:2A1E10', pivot=(0, 0.2, -0.56), parent='tasselbell')

def mask3(prefix, side, build):
    ps = mask3d(prefix, side, build)
    return [p for p in ps if p['parent'] is None or any(q['name'] == p['parent'] for q in ps)]

add('oni_mask', 'Oni Mask', 'HATS', 'Sculpted red lacquer demon · curved gold horns · tusks · glowing eyes', mask3('oni', 'left', oni3))
add('kitsune_mask', 'Kitsune Mask', 'HATS', 'White lacquer fox · long snout · gold-rimmed eyes · red flame marks', mask3('kit', 'right', kitsune3))

# ================= Oni v4: angular armoured style (silver horns, gold trim, slanted eyes, silver tusks) =================
def oni4(B):
    R, R2, D, K, G, SV, SV2, T = 'lacq:E3202E', 'lacq:BC1422', 'lacq:7E0A14', 'flat:1A0A0C', 'gold', 'lacq:B9BEC6', 'lacq:E6E9EE', 'flat:F2F0EA'
    B('face', (-2.8, -2.6, -0.5), (5.6, 6.0, 1.0), R)
    # forehead: two plates meeting in a raised centre ridge, peaked at the top
    for s in (-1, 1):
        B('fh%d' % s, (-1.6, -2.2, -0.55), (3.2, 2.6, 1.0), R, pivot=(s * 1.35, -2.4, -0.35), rot=(-8, s * -14, s * -14))
        B('fhl%d' % s, (-0.1, -1.3, -0.1), (0.2, 2.6, 0.2), G, pivot=(s * 0.35, -3.1, -0.95), rot=(0, 0, s * -22))
    B('ridge', (-0.4, -1.6, -0.5), (0.8, 3.2, 0.9), R2, pivot=(0, -2.6, -0.75))
    for s in (-1, 1):
        # cheek armour: two angled plates swept back, each with a gold edge line
        B('chk%d' % s, (-1.1, -2.4, -0.5), (2.2, 4.8, 1.0), R2, pivot=(s * 3.4, 0.3, 0.3), rot=(0, s * 40, s * -6))
        B('chkl%d' % s, (-0.1, -2.0, -0.1), (0.2, 4.0, 0.2), G, pivot=(s * 3.0, 0.4, -0.35), rot=(0, s * 40, s * -6))
        B('jawp%d' % s, (-1.0, -1.0, -0.5), (2.0, 2.0, 1.0), R2, pivot=(s * 2.8, 3.2, 0.0), rot=(0, s * 30, s * 28))
        B('temp%d' % s, (-0.8, -1.2, -0.4), (1.6, 2.4, 0.8), D, pivot=(s * 3.6, -2.4, 0.6), rot=(0, s * 50, s * 10))
        # angry brow: sharp wedge slanting down toward the nose, gold underline
        B('brow%d' % s, (-1.8, -0.4, -0.75), (3.6, 0.8, 1.5), R, pivot=(s * 1.7, -0.95, -0.8), rot=(0, s * 8, s * 24))
        B('browl%d' % s, (-1.6, -0.08, -0.08), (3.2, 0.16, 0.16), G, pivot=(s * 1.75, -0.45, -1.5), rot=(0, 0, s * 24))
        # slanted eye slit with a gold outline below
        B('eye%d' % s, (-1.1, -0.28, -0.1), (2.2, 0.56, 0.3), K, pivot=(s * 1.65, -0.15, -0.65), rot=(0, 0, s * 18))
        B('eyel%d' % s, (-1.15, -0.07, -0.07), (2.3, 0.14, 0.14), G, pivot=(s * 1.65, 0.22, -0.82), rot=(0, 0, s * 18))
        B('glow%d' % s, (-0.3, -0.12, -0.05), (0.6, 0.24, 0.1), 'neon:FFB020', pivot=(s * 1.9, -0.2, -0.82), rot=(0, 0, s * 18))
        # raised cheekbones and snarl folds
        B('cb%d' % s, (-0.9, -0.55, -0.55), (1.8, 1.1, 1.0), R, pivot=(s * 1.9, 1.3, -0.6), rot=(0, s * 12, s * -15))
        B('snarl%d' % s, (-0.1, -0.7, -0.1), (0.2, 1.4, 0.2), D, pivot=(s * 1.05, 1.5, -1.2), rot=(0, 0, s * 28))
        B('nost%d' % s, (-0.45, -0.3, -0.5), (0.9, 0.6, 0.9), R2, pivot=(s * 0.6, 1.25, -1.3), rot=(0, 0, s * 15))
        # silver tusks curving up and outward from the mouth corners
        arc(B, 'tusk%d_' % s, 0, (s * 2.15, 3.3, -1.0), (-10, 0, s * -30), '', [(0.75, 1.1), (0.58, 1.0), (0.42, 0.9), (0.26, 0.7)], SV, tip=SV2, bend=(-18, 0, s * -14))
        # silver horns: thick base, curving up and slightly inward
        B('hornb%d' % s, (-0.95, -0.4, -0.95), (1.9, 0.8, 1.9), G, pivot=(s * 2.4, -3.4, 0.2))
        arc(B, 'horn%d_' % s, 0, (s * 2.4, -3.6, 0.2), (-6, 0, s * 26), '', [(1.7, 1.6), (1.45, 1.5), (1.2, 1.4), (0.95, 1.3), (0.7, 1.2), (0.42, 1.1)], SV, tip=SV2, bend=(-4, 0, s * -12))
    # sharp nose
    B('nose', (-0.55, -1.8, -0.7), (1.1, 1.9, 0.8), R, pivot=(0, 1.0, -1.25), rot=(-12, 0, 0))
    B('nosetip', (-0.75, -0.4, -0.9), (1.5, 0.8, 1.0), R, pivot=(0, 1.0, -1.3))
    # snarling mouth: thick lips, dark interior, silver teeth top and bottom
    B('ulip', (-2.4, -0.35, -0.7), (4.8, 0.7, 1.1), R2, pivot=(0, 2.15, -0.75))
    B('mouth', (-2.2, -0.8, -0.2), (4.4, 1.9, 0.4), K, pivot=(0, 3.15, -0.7))
    for k in range(6):
        B('ut%d' % k, (-0.3, 0, -0.14), (0.6, 0.6, 0.28), T, pivot=(-1.75 + k * 0.7, 2.45, -0.92))
        B('lt%d' % k, (-0.28, -0.5, -0.14), (0.56, 0.5, 0.28), T, pivot=(-1.75 + k * 0.7, 3.95, -0.92))
    for s in (-1, 1):
        B('fang%d' % s, (-0.24, 0, -0.18), (0.48, 1.1, 0.36), SV2, pivot=(s * 1.25, 2.45, -0.95))
    B('llip', (-2.2, -0.35, -0.6), (4.4, 0.7, 1.0), R2, pivot=(0, 4.25, -0.55))
    # pointed chin
    for s in (-1, 1):
        B('chin%d' % s, (-1.2, -0.6, -0.5), (2.4, 1.2, 1.0), R, pivot=(s * 0.8, 4.9, -0.25), rot=(0, 0, s * -25))
    B('chinl', (-0.1, -0.6, -0.1), (0.2, 1.2, 0.2), G, pivot=(0, 5.0, -0.8))

add('oni_mask', 'Oni Mask', 'HATS', 'Angular red armoured demon · silver horns + tusks · gold trim · glowing slit eyes', mask3('oni', 'left', oni4))

# ---- Oni v4 tweaks: shorter horns sweeping out then up, tusks forward
_oni4 = oni4
def oni4b(B):
    rec = []
    def B2(n, frm, size, mat, pivot=(0, 0, 0), rot=(0, 0, 0), parent=None):
        if n.startswith(('horn', 'tusk')) and not n.startswith('hornb'): return
        B(n, frm, size, mat, pivot=pivot, rot=rot, parent=parent)
    _oni4(B2)
    SV, SV2 = 'lacq:C4C8D0', 'lacq:F0F2F5'
    for s in (-1, 1):
        arc(B, 'horn%d_' % s, 0, (s * 2.4, -3.5, 0.2), (0, 0, s * 62), '',
            [(1.6, 0.85), (1.42, 0.85), (1.24, 0.8), (1.06, 0.8), (0.88, 0.75), (0.68, 0.7), (0.48, 0.65), (0.3, 0.6)],
            SV, tip=SV2, bend=(0, 0, s * -16))
        arc(B, 'tusk%d_' % s, 0, (s * 2.1, 3.6, -1.3), (-35, s * -20, s * -40), '', [(0.75, 0.9), (0.6, 0.85), (0.46, 0.8), (0.32, 0.7), (0.2, 0.6)], SV, tip=SV2, bend=(-14, 0, s * -10))
add('oni_mask', 'Oni Mask', 'HATS', 'Angular red armoured demon · silver horns + tusks · gold trim · glowing slit eyes', mask3('oni', 'left', oni4b))

# ---- Kitsune v4: rounded white face, tall smooth pointed ears, flowing red stripes, black nose
def kitsune4(B):
    W_, W2, Rd, K, GR = 'flat:FFFFFF', 'flat:EEECE8', 'flat:D3121F', 'flat:121012', 'flat:A9ADB5'
    B('face', (-2.6, -2.6, -0.5), (5.2, 5.4, 1.0), W_)
    B('top', (-2.1, -0.8, -0.5), (4.2, 1.2, 1.0), W_, pivot=(0, -2.9, -0.15))
    B('top2', (-1.4, -0.6, -0.5), (2.8, 0.8, 1.0), W_, pivot=(0, -3.8, 0.05))
    for s in (-1, 1):
        B('cheek%d' % s, (-1.0, -2.2, -0.5), (2.0, 4.4, 1.0), W2, pivot=(s * 3.1, 0.3, 0.45), rot=(0, s * 40, 0))
        B('flare%d' % s, (-0.9, -0.6, -0.4), (1.8, 1.2, 0.8), W_, pivot=(s * 3.2, 2.6, 0.2), rot=(0, s * 35, s * -30))   # flared lower cheeks
        B('eyeh%d' % s, (-1.0, -0.45, -0.1), (2.0, 0.9, 0.35), K, pivot=(s * 1.35, -0.4, -0.58), rot=(0, 0, s * -14))
        B('eyerim%d' % s, (-1.1, -0.08, -0.08), (2.2, 0.16, 0.18), Rd, pivot=(s * 1.35, -0.95, -0.62), rot=(0, 0, s * -14))
        # stripes arching over the brow (3 per side), cheek swooshes (2 per side)
        for j, (x, y, a, L) in enumerate(((0.9, -1.65, -8, 1.6), (1.4, -2.25, -14, 1.9), (1.8, -2.85, -22, 1.6))):
            B('st%d_%d' % (s, j), (-L / 2, -0.12, -0.09), (L, 0.24, 0.18), Rd, pivot=(s * x, y, -0.58 + 0.12 * (j == 2)), rot=(0, 0, s * a))
        for j, (y, a) in enumerate(((0.55, 12), (1.15, 22))):
            B('sw%d_%d' % (s, j), (-0.9, -0.12, -0.09), (1.8, 0.24, 0.18), Rd, pivot=(s * 2.75, y, -0.25), rot=(0, s * 40, s * a))
        # tall smooth pointed ears: many thin tapering tiers, grey inner
        arc(B, 'ear%d_' % s, 0, (s * 1.7, -3.2, 0.25), (-4, 0, s * 12), '',
            [(2.9, 1.5), (2.1, 1.5), (1.3, 1.4), (0.55, 1.1)], W_, bend=(0, 0, s * 2), depth=0.8)
        arc(B, 'eari%d_' % s, 0, (0, -0.15, -0.45), (0, 0, 0), 'ear%d_0' % s,
            [(1.9, 1.5), (1.25, 1.5), (0.6, 1.3)], GR, depth=0.2)
        B('wh%d' % s, (-0.06, -0.06, 0), (0.12, 0.12, 1.4), Rd, pivot=(s * 0.7, 1.9, -2.4), rot=(0, s * 120, 0))
    B('drop', (-0.32, -0.55, -0.1), (0.64, 1.1, 0.2), Rd, pivot=(0, -2.75, -0.62))                       # forehead teardrop
    B('dropl', (-0.15, -0.3, -0.1), (0.3, 0.6, 0.2), Rd, pivot=(0, -3.65, -0.52))
    B('muzzle', (-1.25, -0.9, -1.8), (2.5, 1.9, 1.8), W_, pivot=(0, 1.35, -0.4))
    B('muzz2', (-0.95, -0.7, -1.0), (1.9, 1.5, 1.0), W_, pivot=(0, 1.45, -2.1))
    B('nose', (-0.75, -0.55, -0.55), (1.5, 1.1, 0.75), K, pivot=(0, 1.2, -3.0))
    B('mst', (-0.08, -0.4, -0.08), (0.16, 0.8, 0.16), Rd, pivot=(0, 2.2, -2.95))
    B('chin', (-1.2, -0.4, -0.5), (2.4, 0.8, 1.0), W2, pivot=(0, 2.95, -0.6))
    B('tassel', (-0.15, 0, -0.15), (0.3, 3.0, 0.3), 'lacq:D3121F', pivot=(-2.6, 2.6, 0.5))
    B('bell', (-0.55, -0.55, -0.55), (1.1, 1.1, 1.1), 'gold', pivot=(0, 3.2, 0), parent='tassel')
add('kitsune_mask', 'Kitsune Mask', 'HATS', 'White fox mask · tall pointed ears · flowing red stripes · black nose', mask3('kit', 'right', kitsune4))

# ---- Oni Menpo: half mask over the lower face (nose down), wide fanged grin, chin knob, side rings
def menpo(B):
    R, R2, D, K, T, SV = 'lacq:D21624', 'lacq:A80E1A', 'lacq:6E0810', 'flat:120708', 'flat:F2F0EA', 'lacq:C4C8D0'
    B('base', (-3.3, -1.6, -0.5), (6.6, 4.4, 1.0), R)
    for s in (-1, 1):
        B('cheek%d' % s, (-1.0, -1.8, -0.5), (2.0, 3.8, 1.0), R2, pivot=(s * 3.7, 0.4, 0.6), rot=(0, s * 42, 0))
        B('cb%d' % s, (-1.0, -0.6, -0.6), (2.0, 1.2, 1.1), R, pivot=(s * 2.0, -0.8, -0.65), rot=(0, s * 10, s * -12))
        B('fold%d' % s, (-0.12, -0.9, -0.12), (0.24, 1.8, 0.24), D, pivot=(s * 1.35, 0.0, -1.2), rot=(0, 0, s * 30))
        B('nost%d' % s, (-0.5, -0.35, -0.6), (1.0, 0.7, 1.0), R2, pivot=(s * 0.65, -0.8, -1.3), rot=(0, 0, s * 18))
        B('ring%d' % s, (-0.15, -0.6, -0.6), (0.3, 1.2, 1.2), SV, pivot=(s * 4.55, -1.0, 1.8))
        B('ringh%d' % s, (-0.2, -0.3, -0.3), (0.4, 0.6, 0.6), K, pivot=(s * 4.55, -1.0, 1.8))
        B('fangu%d' % s, (-0.26, 0, -0.18), (0.52, 1.3, 0.36), T, pivot=(s * 1.75, 0.65, -1.0))
        B('fangl%d' % s, (-0.24, -1.1, -0.18), (0.48, 1.1, 0.36), T, pivot=(s * 1.2, 2.55, -1.0))
        B('corner%d' % s, (-0.5, -0.5, -0.5), (1.0, 1.0, 1.0), D, pivot=(s * 2.55, 1.4, -0.75), rot=(0, 0, 45))
    B('nose', (-0.6, -1.3, -0.8), (1.2, 1.6, 0.9), R, pivot=(0, -0.4, -1.3), rot=(-10, 0, 0))
    B('ulip', (-2.6, -0.35, -0.7), (5.2, 0.7, 1.1), R2, pivot=(0, 0.35, -0.8))
    B('mouth', (-2.4, -0.9, -0.2), (4.8, 1.9, 0.4), K, pivot=(0, 1.55, -0.75))
    for k in range(7):
        B('ut%d' % k, (-0.28, 0, -0.14), (0.56, 0.55, 0.28), T, pivot=(-1.95 + k * 0.65, 0.65, -0.95))
        B('lt%d' % k, (-0.26, -0.5, -0.14), (0.52, 0.5, 0.28), T, pivot=(-1.95 + k * 0.65, 2.5, -0.95))
    B('llip', (-2.4, -0.4, -0.6), (4.8, 0.8, 1.0), R2, pivot=(0, 2.85, -0.6))
    B('chin', (-1.8, -0.5, -0.6), (3.6, 1.0, 1.0), R, pivot=(0, 3.6, -0.4))
    B('knob', (-0.55, -0.55, -0.4), (1.1, 1.1, 0.8), D, pivot=(0, 3.7, -1.1))
    B('knobh', (-0.25, -0.25, -0.1), (0.5, 0.5, 0.2), K, pivot=(0, 3.7, -1.55))
def menpo_mask():
    ps = [P('men', (-0.01, -0.01, -0.01), (0.02, 0.02, 0.02), 'flat:000000', pivot=(0, -2.3, -4.6)),
          P('mencord', (-4.4, -0.35, -4.4), (8.8, 0.7, 8.8), 'flat:15151A', pivot=(0, -3.4, 0))]
    def B(n, frm, size, mat, pivot=(0, 0, 0), rot=(0, 0, 0), parent=None):
        ps.append(P('men' + n, frm, size, mat, pivot=pivot, rot=rot, parent='men' + (parent or '')))
    menpo(B)
    return ps
add('oni_menpo', 'Oni Menpo', 'HATS', 'Samurai half mask over the lower face · fanged grin · silver rings', menpo_mask())

# ================= premium finish pass =================
_p5 = gen.paint
def paint5(mat, face, w, h):
    if mat.startswith('inlay:'):      # glowing metal inlay line: bright core, soft glow
        c = np.array(I.hexc(mat.split(':')[1]), float)
        img = np.zeros((h, w, 4)); img[..., :3] = np.clip(c * 1.05, 0, 255); img[..., 3] = 255
        glow = img.copy(); glow[..., :3] = c * 0.75
        return img, glow
    if mat.startswith('pearl'):       # pearl white: faint pink/blue sheen, soft rim shading
        yy = np.linspace(0, 1, h)[:, None] * np.ones((1, w)); xx = np.linspace(0, 1, w)[None, :] * np.ones((h, 1))
        col = np.stack([250 - 6 * xx + 4 * yy, 248 - 3 * yy, 252 - 8 * yy + 6 * xx], -1)
        if face == 'bottom': col = col * 0.82
        img = np.zeros((h, w, 4)); img[..., :3] = np.clip(col, 0, 255); img[..., 3] = 255
        gen.bevel(img, face, 0.10, max(1, gen.S // 4))
        glow = np.zeros_like(img); glow[..., :3] = 8; glow[..., 3] = 255   # keeps it bright white even at night
        return img, glow
    if mat.startswith('chrome:'):     # polished metal: strong bright/dark banding
        c = np.array(I.hexc(mat.split(':')[1]), float)
        yy = np.linspace(0, 1, h)[:, None] * np.ones((1, w))
        k = 0.75 + 0.55 * np.exp(-((yy - 0.25) ** 2) / 0.012) - 0.25 * yy + 0.2 * np.exp(-((yy - 0.8) ** 2) / 0.01)
        img = np.zeros((h, w, 4)); img[..., :3] = np.clip(c[None, None] * k[..., None], 0, 255); img[..., 3] = 255
        gen.bevel(img, face, 0.3, max(1, gen.S // 4))
        return img, None
    if mat.startswith('ember:'):      # dim inner glow (mouth interiors)
        c = np.array(I.hexc(mat.split(':')[1]), float)
        yy = np.linspace(0, 1, h)[:, None] * np.ones((1, w))
        img = np.zeros((h, w, 4)); img[..., :3] = np.clip(c[None, None] * (0.6 + 0.6 * yy)[..., None], 0, 255); img[..., 3] = 255
        glow = img.copy(); glow[..., :3] *= 0.7
        return img, glow
    return _p5(mat, face, w, h)
gen.paint = paint5

def remap(build, table, by_name=None):
    def wrapped(B):
        def B2(n, frm, size, mat, pivot=(0, 0, 0), rot=(0, 0, 0), parent=None):
            if by_name:
                for pre, m2 in by_name:
                    if n.startswith(pre): mat = m2; break
                else: mat = table.get(mat, mat)
            else: mat = table.get(mat, mat)
            B(n, frm, size, mat, pivot=pivot, rot=rot, parent=parent)
        build(B2)
    return wrapped

GOLDLINE = 'inlay:FFC24A'
def oni_premium(B):
    remap(oni4b, {'gold': GOLDLINE, 'flat:1A0A0C': 'ember:4A0A0E', 'lacq:C4C8D0': 'chrome:C8CCD4', 'lacq:F0F2F5': 'chrome:F4F6FA',
                  'lacq:B9BEC6': 'chrome:C8CCD4', 'lacq:E6E9EE': 'chrome:F4F6FA', 'flat:F2F0EA': 'pearl'},
          by_name=[('hornb', 'gold'), ('eyel', GOLDLINE), ('eye', 'flat:120507')])(B)
    # glowing emblem on the forehead ridge + inlay around the horn bases
    B('emb', (-0.45, -0.45, -0.12), (0.9, 0.9, 0.24), 'neon:FFD24A', pivot=(0, -2.9, -1.25), rot=(0, 0, 45))
    B('emb2', (-0.7, -0.7, -0.1), (1.4, 1.4, 0.2), 'gold', pivot=(0, -2.9, -1.15), rot=(0, 0, 45))
    for s in (-1, 1):
        B('hring%d' % s, (-1.05, -0.12, -1.05), (2.1, 0.24, 2.1), GOLDLINE, pivot=(s * 2.4, -3.9, 0.2))
def kit_premium(B):
    remap(kitsune4, {'flat:FFFFFF': 'pearl', 'flat:EEECE8': 'pearl2', 'flat:D3121F': 'inlay:E8182C', 'flat:A9ADB5': 'chrome:B8BCC6'})(B)
    for s in (-1, 1):   # gold rims around the eye holes and along the ear edges
        B('rimg%d' % s, (-1.1, -0.07, -0.07), (2.2, 0.14, 0.14), GOLDLINE, pivot=(s * 1.35, 0.05, -0.68), rot=(0, 0, s * -14))
    B('chinl', (-1.1, -0.07, -0.07), (2.2, 0.14, 0.14), GOLDLINE, pivot=(0, 3.35, -1.12))
    B('gem', (-0.35, -0.35, -0.12), (0.7, 0.7, 0.24), 'neon:FFD24A', pivot=(0, -3.6, -0.66), rot=(0, 0, 45))
def menpo_premium(B):
    remap(menpo, {'flat:120708': 'ember:4A0A0E', 'flat:F2F0EA': 'pearl', 'lacq:C4C8D0': 'chrome:C8CCD4'})(B)
    B('trimt', (-3.3, -0.08, -0.08), (6.6, 0.16, 0.16), GOLDLINE, pivot=(0, -1.55, -0.62))
    B('trimc', (-1.8, -0.08, -0.08), (3.6, 0.16, 0.16), GOLDLINE, pivot=(0, 4.12, -0.95))
    for s in (-1, 1):
        B('trims%d' % s, (-0.08, -1.7, -0.08), (0.16, 3.4, 0.16), GOLDLINE, pivot=(s * 3.3, 0.6, -0.15), rot=(0, s * 42, 0))
_p6 = gen.paint
def paint6(mat, face, w, h):
    if mat == 'pearl2':
        img, g = _p6('pearl', face, w, h); img[..., :3] *= 0.95; return img, g
    return _p6(mat, face, w, h)
gen.paint = paint6

add('oni_mask', 'Oni Mask', 'HATS', 'Lacquered armoured demon · chrome horns + tusks · glowing gold inlay · ember mouth', mask3('oni', 'left', oni_premium))
add('kitsune_mask', 'Kitsune Mask', 'HATS', 'Pearl fox mask · glowing red stripes · gold rims + gem · chrome ears', mask3('kit', 'right', kit_premium))
def menpo_mask2():
    ps = [P('men', (-0.01, -0.01, -0.01), (0.02, 0.02, 0.02), 'flat:000000', pivot=(0, -2.3, -4.6)),
          P('mencord', (-4.4, -0.35, -4.4), (8.8, 0.7, 8.8), 'flat:15151A', pivot=(0, -3.4, 0))]
    def B(n, frm, size, mat, pivot=(0, 0, 0), rot=(0, 0, 0), parent=None):
        ps.append(P('men' + n, frm, size, mat, pivot=pivot, rot=rot, parent='men' + (parent or '')))
    menpo_premium(B)
    return ps
add('oni_menpo', 'Oni Menpo', 'HATS', 'Lacquered half mask · gold inlay · pearl fangs · ember grin', menpo_mask2())

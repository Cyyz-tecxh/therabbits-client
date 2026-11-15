# CARD MASTER — floating, orbiting playing cards (6 decks). Each card: detailed front + back, glowing metal border.
import math, copy
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import gen
from gen import P

DECKS = {   # every deck: near-black card faces with neon edges and glowing symbols
    'blood':        dict(name='Blood Deck',   bg=(14, 6, 8),   bg2=(40, 6, 12),  acc=(255, 40, 70),   metal=(205, 208, 216), ink=(255, 220, 225), glow=(255, 45, 80)),
    'royal':        dict(name='Royal Deck',   bg=(12, 10, 6),  bg2=(40, 30, 8),  acc=(255, 196, 60),  metal=(240, 190, 70),  ink=(255, 250, 235), glow=(255, 200, 70)),
    'frost':        dict(name='Frost Deck',   bg=(6, 10, 18),  bg2=(10, 30, 60), acc=(80, 190, 255),  metal=(205, 222, 236), ink=(225, 245, 255), glow=(90, 200, 255)),
    'heartbreaker': dict(name='Heartbreaker', bg=(16, 6, 14),  bg2=(50, 8, 40),  acc=(255, 70, 190),  metal=(245, 175, 215), ink=(255, 230, 245), glow=(255, 80, 200)),
    'void':         dict(name='Void Deck',    bg=(8, 6, 14),   bg2=(30, 10, 56), acc=(170, 90, 255),  metal=(140, 136, 160), ink=(235, 220, 255), glow=(165, 90, 255)),
    'emerald':      dict(name='Emerald Deck', bg=(6, 12, 9),   bg2=(8, 40, 26),  acc=(40, 230, 130),  metal=(240, 190, 70),  ink=(235, 255, 240), glow=(50, 255, 150)),
}
CARDS = [('A', 'spade'), ('K', 'heart'), ('Q', 'diamond'), ('J', 'club'), ('10', 'heart'), ('A', 'diamond'),
         ('K', 'spade'), ('7', 'club'), ('*', 'star'), ('Q', 'heart'), ('J', 'spade'), ('A', 'heart')]
FONT = '/home/claude/fonts/ttf/exo2.ttf'

def suit(d, cx, cy, r, kind, fill):
    if kind == 'heart' or kind == 'spade':
        s = 1 if kind == 'heart' else -1
        d.ellipse((cx - r, cy - r * 0.55 * s - r * 0.5, cx, cy - r * 0.55 * s + r * 0.5), fill=fill) if s == 1 else None
        if s == 1:
            d.ellipse((cx, cy - r * 1.05, cx + r, cy - 0.05 * r), fill=fill)
            d.polygon([(cx - r * 0.97, cy - r * 0.35), (cx + r * 0.97, cy - r * 0.35), (cx, cy + r)], fill=fill)
        else:
            d.ellipse((cx - r, cy + 0.05 * r - r * 0.1, cx, cy + r * 1.0 - r * 0.1), fill=fill)
            d.ellipse((cx, cy + 0.05 * r - r * 0.1, cx + r, cy + r * 1.0 - r * 0.1), fill=fill)
            d.polygon([(cx - r * 0.97, cy + r * 0.35), (cx + r * 0.97, cy + r * 0.35), (cx, cy - r)], fill=fill)
            d.polygon([(cx, cy + r * 0.5), (cx - r * 0.45, cy + r * 1.25), (cx + r * 0.45, cy + r * 1.25)], fill=fill)
    elif kind == 'diamond':
        d.polygon([(cx, cy - r * 1.15), (cx + r * 0.8, cy), (cx, cy + r * 1.15), (cx - r * 0.8, cy)], fill=fill)
    elif kind == 'club':
        q = r * 0.52
        for ox, oy in ((0, -0.5), (-0.55, 0.25), (0.55, 0.25)):
            d.ellipse((cx + ox * r - q, cy + oy * r - q, cx + ox * r + q, cy + oy * r + q), fill=fill)
        d.polygon([(cx, cy), (cx - r * 0.4, cy + r * 1.15), (cx + r * 0.4, cy + r * 1.15)], fill=fill)
    elif kind == 'star':
        pts = []
        for k in range(10):
            a = -math.pi / 2 + k * math.pi / 5; rr = r * (1.15 if k % 2 == 0 else 0.48)
            pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
        d.polygon(pts, fill=fill)

def crown(d, cx, cy, r, fill):
    d.polygon([(cx - r, cy + r * 0.5), (cx - r, cy - r * 0.4), (cx - r * 0.5, cy + r * 0.05), (cx, cy - r * 0.7),
               (cx + r * 0.5, cy + r * 0.05), (cx + r, cy - r * 0.4), (cx + r, cy + r * 0.5)], fill=fill)
    for x in (-1, 0, 1):
        d.ellipse((cx + x * r - r * 0.16, cy - r * (0.4 if x else 0.7) - r * 0.32, cx + x * r + r * 0.16, cy - r * (0.4 if x else 0.7)), fill=fill)

def card_face(deck, idx, side, w, h):
    """Reference style: white-hot outer frame with coloured glow, near-black face, thin inner frame,
    one big glowing symbol in the middle, small corner marks."""
    D = DECKS[deck]; K = 8; W, H = w * K, h * K
    acc, gc = D['acc'], D['glow']
    hot = tuple(int(255 * 0.75 + c * 0.25) for c in acc)
    im = Image.new('RGBA', (W, H), (0, 0, 0, 255)); gl = Image.new('RGBA', (W, H), (0, 0, 0, 255))
    # near-black face with a faint coloured vignette
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    r = np.sqrt(((xx / W - 0.5) * 1.6) ** 2 + ((yy / H - 0.5) * 1.2) ** 2)
    face = np.array((8, 7, 10), float)[None, None] + np.array(acc, float)[None, None] * (0.10 * np.clip(1 - r, 0, 1))[..., None]
    im.paste(Image.fromarray(face.astype(np.uint8)).convert('RGBA'))
    d = ImageDraw.Draw(im); g = ImageDraw.Draw(gl)
    ob = int(W * 0.075)                       # outer frame: coloured band with a white-hot core line
    d.rectangle((0, 0, W - 1, H - 1), outline=acc, width=ob)
    d.rectangle((ob * 0.3, ob * 0.3, W - 1 - ob * 0.3, H - 1 - ob * 0.3), outline=hot, width=int(ob * 0.45))
    g.rectangle((0, 0, W - 1, H - 1), outline=gc, width=ob)
    g.rectangle((ob * 0.3, ob * 0.3, W - 1 - ob * 0.3, H - 1 - ob * 0.3), outline=(255, 255, 255), width=int(ob * 0.4))
    ib = int(ob * 1.9)                         # thin inner frame with notched corners
    for dd, col in ((d, acc), (g, gc)):
        dd.rectangle((ib, ib, W - 1 - ib, H - 1 - ib), outline=col, width=max(2, K // 2))
        n = int(W * 0.06)
        for (x, y, sx, sy) in ((ib, ib, 1, 1), (W - 1 - ib, ib, -1, 1), (ib, H - 1 - ib, 1, -1), (W - 1 - ib, H - 1 - ib, -1, -1)):
            dd.line([(x + sx * n, y), (x + sx * n, y + sy * n), (x, y + sy * n)], fill=col, width=max(2, K // 2))
    rank, s = CARDS[idx % len(CARDS)]
    cx, cy = W / 2, H / 2
    small = W * 0.05
    for (x, y) in ((ib + W * 0.1, ib + H * 0.08), (W - ib - W * 0.1, H - ib - H * 0.08)):
        suit(d, x, y, small, s if s != 'star' else 'spade', hot); suit(g, x, y, small, s if s != 'star' else 'spade', gc)
    for (x, y) in ((W - ib - W * 0.1, ib + H * 0.08), (ib + W * 0.1, H - ib - H * 0.08)):   # little x marks
        for dd, col in ((d, hot), (g, gc)):
            dd.line([(x - small * 0.6, y - small * 0.6), (x + small * 0.6, y + small * 0.6)], fill=col, width=K)
            dd.line([(x - small * 0.6, y + small * 0.6), (x + small * 0.6, y - small * 0.6)], fill=col, width=K)
    if side == 'front':
        big = W * 0.25
        if rank in ('K', 'Q'):
            crown(d, cx, cy, W * 0.24, acc); crown(g, cx, cy, W * 0.24, gc)
            crown(d, cx, cy + W * 0.02, W * 0.15, hot)
        elif s == 'star':
            suit(d, cx, cy, big, 'star', acc); suit(g, cx, cy, big, 'star', gc); suit(d, cx, cy, big * 0.45, 'star', hot)
        else:
            suit(d, cx, cy, big, s, acc); suit(g, cx, cy, big, s, gc)
            suit(d, cx - big * 0.12, cy - big * 0.15, big * 0.45, s, hot)      # white-hot inner highlight
            suit(g, cx - big * 0.12, cy - big * 0.15, big * 0.45, s, (255, 255, 255))
    else:
        crown(d, cx, cy - H * 0.04, W * 0.2, acc); crown(g, cx, cy - H * 0.04, W * 0.2, gc)
        suit(d, cx, cy + H * 0.12, W * 0.07, 'star', hot); suit(g, cx, cy + H * 0.12, W * 0.07, 'star', gc)
        d.ellipse((cx - W * 0.3, cy - W * 0.3, cx + W * 0.3, cy + W * 0.3), outline=acc, width=K // 2)
        g.ellipse((cx - W * 0.3, cy - W * 0.3, cx + W * 0.3, cy + W * 0.3), outline=gc, width=K // 2)
    im = im.resize((w, h), Image.LANCZOS); gl = gl.resize((w, h), Image.LANCZOS)
    img = np.asarray(im).astype(float); glw = np.asarray(gl).astype(float); glw[..., 3] = 255
    return img, glw

_prev = gen.paint
def paint(mat, face, w, h):
    if mat.startswith('card:'):
        _, deck, idx = mat.split(':')
        if face in ('front', 'back'):
            return card_face(deck, int(idx), face, w, h)
        D = DECKS[deck]
        img = np.zeros((h, w, 4)); img[..., :3] = D['metal']; img[..., 3] = 255
        glow = np.zeros((h, w, 4)); glow[..., :3] = np.array(D['glow'], float) * 0.6; glow[..., 3] = 255
        return img, glow
    return _prev(mat, face, w, h)
gen.paint = paint

NC = 12
NT = 40
CW, CH, CD = 9.0, 12.6, 0.6      # modelled at 3x for texture detail; drawn at ~0.5 scale
SIZES = [0.62, 0.52, 0.58, 0.5, 0.56, 0.66, 0.5, 0.58, 0.68, 0.54, 0.6, 0.5]
MODELS = {}
for deck in DECKS:
    ps = []
    for k in range(NC):
        ps.append(P('c%d' % k, (-CW / 2, -CH / 2, -CD / 2), (CW, CH, CD), 'card:%s:%d' % (deck, k)))
    G = 'neon:%02X%02X%02X' % DECKS[deck]['glow']
    for k in range(18):   # four-point sparkles: two crossed flat bars
        ps.append(P('s%d' % k, (-0.07, -1.3, -0.04), (0.14, 2.6, 0.08), 'neon:FFFFFF' if k % 3 == 0 else G))
        ps.append(P('s%dc' % k, (-0.25, -0.25, -0.05), (0.5, 0.5, 0.1), 'neon:FFFFFF', rot=(0, 0, 45), parent='s%d' % k))
        ps.append(P('s%dx' % k, (-1.0, -0.07, -0.04), (2.0, 0.14, 0.08), 'neon:FFFFFF' if k % 3 == 0 else G, parent='s%d' % k))
    for r in range(3):    # swirling light trails: segments along a tilted ring, thick bright head -> thin tail
        ps.append(P('t%d' % r, (-0.01, -0.01, -0.01), (0.02, 0.02, 0.02), G))
        for j in range(NT):
            ps.append(P('t%d_%d' % (r, j), (-0.75, -0.3, -0.12), (1.5, 0.6, 0.24), 'neon:FFFFFF' if j < 5 else G, parent='t%d' % r))
    # floating crown above the head
    M_ = 'neon:%02X%02X%02X' % DECKS[deck]['glow']
    MODELS['cards_' + deck] = dict(attach='aura', parts=ps, deck=deck)

def smooth(x): x = max(0.0, min(1.0, x)); return x * x * (3 - 2 * x)

def pose_cards(key, t, ncards=NC, cam_yaw=None):
    m = MODELS[key]; ps = copy.deepcopy(m['parts']); by = {p['name']: p for p in ps}
    # formation: every 12 s the deck snaps into a neat ring for ~3 s
    ph = t % 12.0
    form = smooth((ph - 7.5) / 0.8) * (1 - smooth((ph - 10.5) / 0.8))
    for k in range(NC):
        p = by['c%d' % k]
        if k >= ncards:
            p['frm'] = [0, 0, 0]; p['size'] = [1e-4] * 3; continue
        h = (k * 0.6180339) % 1
        a = t * (0.55 + 0.12 * h) + k / ncards * 2 * math.pi
        R = 15.5 + 1.0 * math.sin(t * 0.8 + k)
        y = 9 + 6 * math.sin(a + 0.6) + 14 * ((k * 0.37) % 1 - 0.5) + 1.0 * math.sin(t * 1.4 + k * 1.7)   # scattered around the body
        # fly out and come back
        fo = (t * 0.14 + h * 3.7) % 1
        R += 9 * math.sin(math.pi * smooth((fo - 0.85) / 0.15)) if fo > 0.85 else 0
        # spin: half the cards spin about their vertical axis, half about the horizontal
        outward = math.degrees(math.atan2(-math.cos(a), -math.sin(a)))
        spin_y = outward + 28 * math.sin(t * 1.3 + k)
        spin_x = 0 if k % 2 == 0 else 22 * math.sin(t * 1.1 + k)
        # occasional flip (180 deg) to show the back
        fl = (t * 0.2 + h * 5.1) % 1
        flip = 180 * smooth((fl - 0.80) / 0.06) - 180 * smooth((fl - 0.93) / 0.06)
        # blend toward ring formation
        af = t * 0.4 + k / ncards * 2 * math.pi
        Rf, yf = 16.0, 7.0
        ox = math.cos(a) * R; oz = math.sin(a) * R
        fx = math.cos(af) * Rf; fz = math.sin(af) * Rf
        p['pivot'] = [ox + (fx - ox) * form, y + (yf - y) * form, oz + (fz - oz) * form]
        fy = math.degrees(math.atan2(-math.cos(af), -math.sin(af)))
        if cam_yaw is not None:
            # face the viewer (always a full card), with a gentle wobble and the occasional flip
            face = -cam_yaw + 22 * math.sin(t * 1.3 + k)
            p['rot'] = [14 * math.sin(t * 1.1 + k * 0.7), face + flip, 24 * math.sin(t * 0.7 + k * 2.1)]
        else:
            p['rot'] = [spin_x * (1 - form), spin_y * (1 - form) + fy * form + flip * (1 - form), 6 * math.sin(t + k) * (1 - form)]
        sc = SIZES[k]
        p['uvsize'] = list(p['size'])
        p['frm'] = [v * sc for v in p['frm']]; p['size'] = [v * sc for v in p['size']]
    cards = [by['c%d' % k]['pivot'] for k in range(ncards)]
    def sc_(p, sc): p['frm'] = [v * sc for v in p['frm']]; p['size'] = [max(1e-4, v * sc) for v in p['size']]
    for k in range(18):   # sparkles twinkle near the cards and above the head
        life = (t * 0.5 + k / 18) % 1
        c = cards[(k * 5) % ncards] if k % 3 else (math.sin(k) * 12, 2 + math.cos(k * 2) * 4, math.cos(k) * 12)
        p = by['s%d' % k]
        p['pivot'] = [c[0] + math.sin(k * 2.3) * 3.5, c[1] - 2.5 + math.cos(k) * 2.5 - life, c[2] + math.cos(k * 1.7) * 3.5]
        p['rot'] = [0, -math.degrees(math.atan2(c[0], -c[2])) if c[2] else 0, 0]
        sc_(p, math.sin(math.pi * life) ** 2 * (0.6 + 0.4 * (k % 2)))
    # light trails: three tilted rings at different heights, rotating at different speeds
    for r, (y0, R, tilt, spd, ln) in enumerate(((10, 14.5, 16, 1.6, 1.0), (4, 13, -12, -1.2, 0.8), (16, 12, 8, 2.1, 0.7))):
        tr = by['t%d' % r]; tr['pivot'] = [0, y0, 0]; tr['rot'] = [tilt, 0, tilt * 0.4]
        head = t * spd + r * 2.1
        for j in range(NT):
            a = head - math.copysign(1, spd) * j * 0.085 * ln
            q = by['t%d_%d' % (r, j)]
            q['pivot'] = [math.cos(a) * R, 0, math.sin(a) * R]; q['rot'] = [0, -math.degrees(a) - 90, 0]
            f = 1 - j / NT
            q['frm'][0] *= 1.3; q['size'][0] *= 1.3
            q['frm'][1] *= f; q['size'][1] = max(1e-3, q['size'][1] * f); q['frm'][2] *= f; q['size'][2] = max(1e-3, q['size'][2] * f)
    return ps

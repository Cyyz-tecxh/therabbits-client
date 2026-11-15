# Mythic / Legendary aura drafts: 5 dragons + 3 dimension effects. Each has its own silhouette and its own motion.
import math, copy, json
import numpy as np
import gen
from gen import P, S, noise

def hexc(h): return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)

_orig_paint = gen.paint
def paint(mat, face, w, h):
    img = np.zeros((h, w, 4)); img[..., 3] = 255
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    if mat.startswith('dscale:'):          # scales: body colour fading to dark edges, glowing outline arcs
        _, b, d, l = mat.split(':'); B, D, L = hexc(b), hexc(d), hexc(l)
        sc = max(4, S // 2 + 1)
        row = (yy // sc).astype(int); off = (row % 2) * sc / 2
        cx = ((xx + off) % sc) - sc / 2; cy = (yy % sc)
        r = np.sqrt(cx ** 2 + (cy * 1.1) ** 2) / (sc * 0.62)
        body = np.clip(1 - r, 0, 1)
        col = D[None, None] + (B - D)[None, None] * (0.35 + 0.65 * body)[..., None]
        col *= (1 + noise(h, w, 0.4, 0.4, 2) * 0.08)[..., None]
        line = (np.abs(r - 0.92) < 0.13)
        col[line] = L
        img[..., :3] = np.clip(col, 0, 255)
        glow = np.zeros_like(img); glow[line, :3] = L; glow[line, 3] = 255
        g2 = body > 0.55; glow[g2 & ~line, :3] = B * 0.55; glow[g2 & ~line, 3] = 255
        return img, glow
    if mat.startswith('holo:'):             # wireframe cube face: only the edges are solid
        c = hexc(mat.split(':')[1]); e = max(1, min(w, h) // 5)
        edge = (xx < e) | (yy < e) | (xx >= w - e) | (yy >= h - e)
        img[..., :3] = c; img[~edge, 3] = 0
        diag = (np.abs(xx / max(1, w - 1) - yy / max(1, h - 1)) < 0.08)
        img[diag & ~edge, :3] = c * 0.8; img[diag & ~edge, 3] = 255
        return img, img.copy()
    if mat.startswith('rune:'):             # glowing dimensional glyph on a clear plate
        c = hexc(mat.split(':')[1]); u = xx / max(1, w - 1) * 2 - 1; v = yy / max(1, h - 1) * 2 - 1
        r = np.sqrt(u ** 2 + v ** 2); a = np.arctan2(v, u)
        ring = np.abs(r - 0.82) < 0.09
        tri = np.abs(np.maximum(np.abs(u) * 0.87 + v * 0.5, -v) - 0.42) < 0.07
        spokes = (np.abs(np.sin(a * 3)) < 0.12) & (r < 0.75) & (r > 0.25)
        dot = r < 0.13
        m = ring | tri | spokes | dot
        img[..., :3] = c; img[~m, 3] = 0
        img[dot, :3] = 255
        return img, img.copy()
    if mat.startswith('portal:'):           # swirl disc
        _, a1, a2 = mat.split(':'); C1, C2 = hexc(a1), hexc(a2)
        u = xx / max(1, w - 1) * 2 - 1; v = yy / max(1, h - 1) * 2 - 1
        r = np.sqrt(u ** 2 + v ** 2); a = np.arctan2(v, u)
        sw = 0.5 + 0.5 * np.sin(a * 3 + r * 9)
        col = C1[None, None] * (1 - sw[..., None]) + C2[None, None] * sw[..., None]
        col = col * np.clip(r * 1.3, 0.15, 1)[..., None] + 255 * np.clip(1 - r * 4, 0, 1)[..., None]
        rim = np.abs(r - 0.93) < 0.07
        col[rim] = 255
        img[..., :3] = np.clip(col, 0, 255); img[r > 1.0, 3] = 0
        return img, img.copy()
    if mat.startswith('crystal:'):          # faceted shard: bright edge, darker core, white highlight
        c = hexc(mat.split(':')[1]); u = xx / max(1, w - 1); v = yy / max(1, h - 1)
        k = 0.55 + 0.45 * np.abs(u - v) + 0.25 * np.exp(-((u + v - 0.4) ** 2) / 0.02)
        col = c[None, None] * k[..., None]
        e = (u < 0.12) | (v < 0.12) | (u > 0.88) | (v > 0.88)
        col[e] = np.clip(c * 0.5 + 140, 0, 255)
        img[..., :3] = np.clip(col, 0, 255)
        return img, img.copy()
    if mat.startswith('smoke:'):            # soft puff with a ragged edge (cutout)
        c = hexc(mat.split(':')[1]); u = xx / max(1, w - 1) * 2 - 1; v = yy / max(1, h - 1) * 2 - 1
        r = np.sqrt(u ** 2 + v ** 2) + noise(h, w, 0.6, 0.6, 2) * 0.35
        img[..., :3] = np.clip(c[None, None] * (1.15 - 0.5 * r)[..., None], 0, 255); img[r > 1.0, 3] = 0
        glow = img.copy(); glow[..., :3] = glow[..., :3] * 0.45
        return img, glow
    return _orig_paint(mat, face, w, h)
gen.paint = paint

MODELS = {}
NSEG, DELTA = 22, 0.24

# ------------------------------------------------------------------ dragons
DRAGONS = {
    'eternal_dragon':   dict(name='Eternal Dragon', rarity='MYTHIC', scale='dscale:22C57A:0B4A36:F2C14E', belly='gold', horn='gold',
                             eye='neon:FFFFFF', mane='neon:7CFFB5', spark=('neon:3CFF9A', 'neon:FFD24A'), smoke='smoke:1F6B50',
                             horns='antler', whisk=True, back='fin', tail='tuft', path='eternal',
                             anim='Circles you; head rises over your shoulder; opens its jaw and fires an energy pulse'),
    'shadow_dragon':    dict(name='Shadow Dragon', rarity='MYTHIC', scale='dscale:1A1A1F:050507:E0203C', belly='metal:3A0A12', horn='metal:2A2A30',
                             eye='neon:FF2A44', mane='neon:E0203C', spark=('neon:FF2A44', 'neon:5A0010'), smoke='smoke:2A0A10',
                             horns='spike', whisk=False, back='spikes', tail='blade', path='tilted',
                             anim='Orbits on a tilted ring, dives through its own smoke and vanishes, reappearing behind you'),
    'frost_dragon':     dict(name='Frost Dragon', rarity='LEGENDARY', scale='dscale:8FDFFF:2A6E9E:FFFFFF', belly='metal:DDF4FF', horn='crystal:BFEFFF',
                             eye='neon:FFFFFF', mane='crystal:E6FAFF', spark=('neon:BFEFFF', 'neon:FFFFFF'), smoke='smoke:CFEFFF',
                             horns='crystal', whisk=True, back='crystals', tail='crystal', path='helix',
                             anim='Spirals up from your feet to above your head, shedding ice shards, then sinks again'),
    'infernal_dragon':  dict(name='Infernal Dragon', rarity='LEGENDARY', scale='dscale:FF6A1A:6A0F05:FFD24A', belly='metal:3A1206', horn='metal:2A1A14',
                             eye='neon:FFF2B0', mane='neon:FF8A1A', spark=('neon:FF8A1A', 'neon:FF2A10'), smoke='smoke:3A1A12',
                             horns='spike', whisk=False, back='flames', tail='flame', path='fast',
                             anim='Fast low orbit with a burning mane; rears up and breathes a fire burst forward'),
    'celestial_dragon': dict(name='Celestial Dragon', rarity='MYTHIC', scale='dscale:FFF6DC:C9A23A:FFFFFF', belly='gold', horn='gold',
                             eye='neon:9FE8FF', mane='neon:FFF1B0', spark=('neon:FFFFFF', 'neon:FFD24A'), smoke='smoke:FFF1C8',
                             horns='antler', whisk=True, back='feathers', tail='feather', path='loop',
                             anim='Slow double loop with a golden halo above you and falling stardust'),
}

def build_dragon(key, d):
    ps = []
    # head (faces -z), jaw, eyes, horns, whiskers, mane
    ps += [P('d_head', (-1.6, -1.4, -1.8), (3.2, 2.6, 3.2), d['scale']),
           P('d_snout', (-1.1, -1.0, -4.5), (2.2, 1.5, 2.8), d['scale'], parent='d_head'),
           P('d_nose', (-0.95, -1.15, -4.7), (1.9, 0.4, 0.7), d['horn'], parent='d_head'),
           P('d_jaw', (-1.0, 0, -3.0), (2.0, 0.6, 3.0), d['belly'], pivot=(0, 0.6, -1.3), parent='d_head'),
           P('d_brow', (-1.75, -1.6, -1.9), (3.5, 0.45, 0.6), d['horn'], parent='d_head')]
    for sx, n in ((-1, 'l'), (1, 'r')):
        ps.append(P('d_eye' + n, (-0.15, -0.3, -0.45), (0.3, 0.55, 0.9), d['eye'], pivot=(sx * 1.62, -0.75, -1.2), parent='d_head'))
        ps.append(P('d_fang' + n, (-0.15, 0, -0.15), (0.3, 0.55, 0.3), 'fab_cream', pivot=(sx * 0.7, 0.45, -4.1), parent='d_head'))
        if d['horns'] == 'antler':
            ps.append(P('d_horn' + n, (-0.3, -2.6, -0.3), (0.6, 2.6, 0.6), d['horn'], pivot=(sx * 0.9, -1.3, -0.4), rot=(-40, 0, sx * 18), parent='d_head'))
            ps.append(P('d_horn2' + n, (-0.22, -1.8, -0.22), (0.44, 1.8, 0.44), d['horn'], pivot=(0, -2.6, 0), rot=(-30, 0, sx * 10), parent='d_horn' + n))
            ps.append(P('d_tine' + n, (-0.18, -1.1, -0.18), (0.36, 1.1, 0.36), d['horn'], pivot=(0, -1.4, 0), rot=(35, 0, sx * 30), parent='d_horn' + n))
        elif d['horns'] == 'spike':
            ps.append(P('d_horn' + n, (-0.4, -3.2, -0.4), (0.8, 3.2, 0.8), d['horn'], pivot=(sx * 1.0, -1.3, -0.2), rot=(-55, 0, sx * 12), parent='d_head'))
            ps.append(P('d_horn2' + n, (-0.22, -1.6, -0.22), (0.44, 1.6, 0.44), d['mane'], pivot=(0, -3.2, 0), rot=(-15, 0, 0), parent='d_horn' + n))
        else:  # crystal
            ps.append(P('d_horn' + n, (-0.45, -3.0, -0.45), (0.9, 3.0, 0.9), d['horn'], pivot=(sx * 1.0, -1.3, -0.3), rot=(-35, 45, sx * 20), parent='d_head'))
        if d['whisk']:
            ps.append(P('d_whisk' + n, (-0.1, -0.1, 0), (0.2, 0.2, 3.0), d['mane'], pivot=(sx * 1.1, -0.3, -3.9), rot=(10, sx * 140, 0), parent='d_head'))
            ps.append(P('d_whisk2' + n, (-0.08, -0.08, 0), (0.16, 0.16, 2.6), d['mane'], pivot=(0, 0, 3.0), rot=(15, sx * 25, 0), parent='d_whisk' + n))
    for j in range(3):
        ps.append(P('d_mane%d' % j, (-0.25, -1.6, -0.6), (0.5, 1.6, 1.2), d['mane'], pivot=((j - 1) * 0.9, -1.2, 1.2), rot=(-50, 0, (j - 1) * 25), parent='d_head'))
    HS = 1.45   # bigger head so it reads at a distance
    for p in ps:
        p['frm'] = [v * HS for v in p['frm']]; p['size'] = [v * HS for v in p['size']]
        if p['parent']: p['pivot'] = [v * HS for v in p['pivot']]
    # body
    for i in range(NSEG):
        f = i / (NSEG - 1)
        w = 2.5 - 1.5 * f ** 1.3; L = 2.9
        n = 'd_s%d' % i
        ps.append(P(n, (-w / 2, -w / 2, -L / 2), (w, w, L), d['scale']))
        ps.append(P(n + 'b', (-w / 2 + 0.2, w / 2 - 0.15, -L / 2 + 0.2), (w - 0.4, 0.3, L - 0.4), d['belly'], parent=n))
        bk = d['back']
        if bk == 'fin':
            ps.append(P(n + 'f', (-0.12, -w / 2 - 0.9 * (1 - f * 0.5), -0.9), (0.24, 0.9 * (1 - f * 0.5), 1.8), d['mane'], parent=n))
        elif bk == 'spikes' and i % 2 == 0:
            ps.append(P(n + 'f', (-0.3, -1.6, -0.3), (0.6, 1.6, 0.6), d['mane'], pivot=(0, -w / 2, 0), rot=(-30, 0, 0), parent=n))
        elif bk == 'crystals' and i % 2 == 0:
            ps.append(P(n + 'f', (-0.4, -1.9 * (1 - f * 0.4), -0.4), (0.8, 1.9 * (1 - f * 0.4), 0.8), d['mane'], pivot=(0, -w / 2, 0), rot=(-15, 45, 0), parent=n))
        elif bk == 'flames':
            hh = 1.4 + 0.8 * math.sin(i * 1.7)
            ps.append(P(n + 'f', (-0.2, -hh, -0.7), (0.4, hh, 1.4), d['mane'], pivot=(0, -w / 2, 0), rot=(-20, 0, 0), parent=n))
        elif bk == 'feathers' and i % 2 == 1:
            for sx in (-1, 1):
                ps.append(P(n + 'w%d' % sx, (-0.1, -0.15, -0.5), (0.2, 0.3, 2.6 * (1 - f * 0.6)), d['mane'], pivot=(sx * w / 2, -w / 2, 0.6), rot=(-25, sx * 140, 0), parent=n))
    # small legs on two segments
    for i in (4, 12):
        n = 'd_s%d' % i
        for sx in (-1, 1):
            ps.append(P(n + 'leg%d' % sx, (-0.35, 0, -0.35), (0.7, 1.6, 0.7), d['scale'], pivot=(sx * 0.9, 0.8, 0), rot=(30, 0, sx * 35), parent=n))
            ps.append(P(n + 'claw%d' % sx, (-0.4, 0, -0.6), (0.8, 0.35, 0.9), d['horn'], pivot=(0, 1.6, 0), parent=n + 'leg%d' % sx))
    # tail end
    n = 'd_s%d' % (NSEG - 1)
    t = d['tail']
    if t in ('tuft', 'flame', 'feather'):
        for j in range(3):
            ps.append(P('d_tail%d' % j, (-0.15, -0.15, 0), (0.3, 0.3, 2.6 - j * 0.4), d['mane'], pivot=(0, 0, 1.3), rot=((j - 1) * 25, (j - 1) * 20, 0), parent=n))
    elif t == 'blade':
        ps.append(P('d_tail0', (-0.15, -0.9, 0), (0.3, 1.8, 2.6), d['mane'], pivot=(0, 0, 1.3), parent=n))
    else:
        ps.append(P('d_tail0', (-0.4, -0.4, 0), (0.8, 0.8, 2.6), d['mane'], pivot=(0, 0, 1.3), rot=(0, 0, 45), parent=n))
    # energy orb (breath), particles, smoke
    ps.append(P('d_orb', (-0.6, -0.6, -0.6), (1.2, 1.2, 1.2), d['spark'][0]))
    for k in range(14):
        ps.append(P('d_p%d' % k, (-0.2, -0.2, -0.2), (0.4, 0.4, 0.4), d['spark'][k % 2]))
    for k in range(8):
        ps.append(P('d_k%d' % k, (-0.8, -0.8, -0.05), (1.6, 1.6, 0.1), d['smoke']))
    if key == 'celestial_dragon':
        for k in range(16):
            a = k / 16 * 2 * math.pi
            ps.append(P('d_halo%d' % k, (-0.9, -0.15, -0.15), (1.8, 0.3, 0.3), 'gold', pivot=(math.cos(a) * 4.6, -12.5, math.sin(a) * 4.6), rot=(0, -math.degrees(a) - 90, 0)))
    return ps

for key, d in DRAGONS.items():
    MODELS[key] = dict(attach='aura', parts=build_dragon(key, d), info=d)

def dragon_path(style, s, i, t):
    if style == 'eternal':
        R = 11 + math.sin(2 * s + 0.7 * t)
        y = 4 + 3.5 * math.sin(1.5 * s - 1.1 * t)
        rise = max(0.0, math.sin(2 * math.pi * t / 9)) ** 3 * 11 * math.exp(-i / 4)
        return R * math.cos(s), y - rise, R * math.sin(s)
    if style == 'tilted':
        R = 11.5
        return R * math.cos(s), 5 + 0.6 * R * math.sin(s), R * math.sin(s) * 0.8
    if style == 'helix':
        R = 10
        return R * math.cos(s), -6 + i * 1.25 + 2 * math.sin(t * 0.6), R * math.sin(s)
    if style == 'fast':
        R = 10.5
        return R * math.cos(s), 7 + 2 * math.sin(3 * s), R * math.sin(s)
    R = 11 + 3 * math.sin(2 * s)
    return R * math.cos(s), 1 + 5 * math.sin(s), R * math.sin(s)

def look(a, b):
    """rotation (deg) that points a part's -z axis from a toward b"""
    dx, dy, dz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
    L = math.sqrt(dx * dx + dy * dy + dz * dz) or 1
    yaw = math.atan2(-dx, -dz); pitch = math.asin(max(-1, min(1, dy / L)))
    return [math.degrees(pitch), math.degrees(yaw), 0]

def scaled(p, sc):
    p.setdefault('uvsize', list(p['size']))
    p['frm'] = [v * sc for v in p['frm']]; p['size'] = [max(1e-4, v * sc) for v in p['size']]

def pose_dragon(key, t, jaw=0.0, orb=-1.0):
    m = MODELS[key]; d = m['info']; ps = copy.deepcopy(m['parts']); by = {p['name']: p for p in ps}
    th0 = t * 0.9 + 1.2
    pts = [dragon_path(d['path'], th0 - i * DELTA, i, t) for i in range(NSEG + 1)]
    hp = dragon_path(d['path'], th0 + DELTA * 1.05, -1, t)
    for i in range(NSEG):
        p = by['d_s%d' % i]; p['pivot'] = list(pts[i]); p['rot'] = look(pts[i], pts[i - 1] if i else hp)
    h = by['d_head']; h['pivot'] = list(hp); h['rot'] = look(pts[0], hp)
    by['d_jaw']['rot'] = [jaw, 0, 0]
    # breath orb flies forward from the mouth
    o = by['d_orb']
    if orb >= 0:
        fx, fy, fz = hp[0] - pts[0][0], hp[1] - pts[0][1], hp[2] - pts[0][2]; L = math.sqrt(fx * fx + fy * fy + fz * fz)
        k = 4 + orb * 9
        o['pivot'] = [hp[0] + fx / L * k, hp[1] + fy / L * k + 0.6, hp[2] + fz / L * k]; scaled(o, 0.7 + 1.8 * orb)
    else:
        scaled(o, 0.0001)
    for k in range(14):
        life = (t * 0.35 + k / 14) % 1
        j = 1 + (k * 5) % (NSEG - 2)
        x, y, z = dragon_path(d['path'], th0 - j * DELTA - life * 0.5, j, t)
        r = math.sqrt(x * x + z * z) or 1
        p = by['d_p%d' % k]; p['pivot'] = [x + x / r * life * 2.5, y - life * 3 + math.sin(k) * 0.8, z + z / r * life * 2.5]
        scaled(p, 1.2 * (1 - life))
    for k in range(8):
        life = (t * 0.22 + k / 8) % 1
        j = 2 + (k * 3) % (NSEG - 3)
        x, y, z = dragon_path(d['path'], th0 - j * DELTA - life * 0.4, j, t)
        p = by['d_k%d' % k]; p['pivot'] = [x, y - life * 2, z]; p['rot'] = [0, -math.degrees(th0) + k * 40, k * 30]
        scaled(p, (0.7 + 1.1 * life) * (1 - life ** 3))
    return ps

# ------------------------------------------------------------------ dimensions
DIMS = {
    'dimension_breaker': dict(name='Dimension Breaker', rarity='MYTHIC',
                              crys=('crystal:2E8BFF', 'crystal:E0203C', 'crystal:FF9A1A'), holo='holo:6EC6FF', rune='rune:FFB040',
                              ring=('neon:2E8BFF', 'neon:E0203C'), arc='neon:BFE6FF', portal='portal:2E8BFF:E0203C', spark=('neon:FFFFFF', 'neon:FF9A1A'),
                              anim='Fragments appear, orbit, two rings form, a dimensional pulse blasts outward, everything fades and repeats'),
    'toxic_dimension':   dict(name='Toxic Dimension', rarity='LEGENDARY',
                              crys=('crystal:39FF14', 'crystal:0D3B0A', 'crystal:A8FF3C'), holo='holo:39FF14', rune='rune:39FF14',
                              ring=('neon:39FF14', 'neon:1A7A10'), arc='neon:C8FF9A', portal='portal:39FF14:0A0A0A', spark=('neon:39FF14', 'neon:C8FF9A'),
                              anim='Hex shards glitch and jitter in place, toxic droplets rise, a green shockwave ripples up your body'),
    'cosmic_dimension':  dict(name='Cosmic Dimension', rarity='LEGENDARY',
                              crys=('crystal:FF5FC8', 'crystal:6E7BFF', 'crystal:B04DFF'), holo='holo:FF9AE6', rune='rune:9FD8FF',
                              ring=('neon:B04DFF', 'neon:FF5FC8'), arc='neon:FFFFFF', portal='portal:B04DFF:2E8BFF', spark=('neon:FFFFFF', 'neon:FF9AE6'),
                              anim='Tiny planets orbit like a solar system, star-lines link them into constellations, a nebula portal opens'),
}

def build_dim(key, d):
    ps = []
    rng = np.random.default_rng(len(key))
    for i in range(10):     # crystal shards: two crossed diamonds
        n = 'x_c%d' % i; sz = 0.9 + rng.random() * 0.9
        if key == 'cosmic_dimension':      # tiny planets, some with rings
            sz *= 1.3
            ps.append(P(n, (-sz / 2, -sz / 2, -sz / 2), (sz, sz, sz), d['crys'][i % 3]))
            ps.append(P(n + 'b', (-sz * 0.6, -sz * 0.6, -sz * 0.6), (sz * 1.2, sz * 1.2, sz * 1.2), d['crys'][(i + 1) % 3], rot=(45, 45, 0), parent=n))
            if i % 3 == 0:
                ps.append(P(n + 'r', (-sz * 1.1, -0.05, -sz * 1.1), (sz * 2.2, 0.1, sz * 2.2), d['holo'], rot=(20, 0, 15), parent=n))
            continue
        if key == 'toxic_dimension':       # flat hex-like shards
            ps.append(P(n, (-sz * 0.8, -sz * 0.8, -sz * 0.15), (sz * 1.6, sz * 1.6, sz * 0.3), d['crys'][i % 3]))
            ps.append(P(n + 'b', (-sz * 0.8, -sz * 0.8, -sz * 0.12), (sz * 1.6, sz * 1.6, sz * 0.24), d['crys'][(i + 1) % 3], rot=(0, 0, 30), parent=n))
            continue
        ps.append(P(n, (-sz / 2, -sz * 1.2, -sz / 2), (sz, sz * 2.4, sz), d['crys'][i % 3]))
        ps.append(P(n + 'b', (-sz / 2, -sz * 0.6, -sz / 2), (sz, sz * 1.2, sz), d['crys'][(i + 1) % 3], rot=(0, 45, 0), parent=n))
    for i in range(6):
        ps.append(P('x_h%d' % i, (-0.9, -0.9, -0.9), (1.8, 1.8, 1.8), d['holo']))
        ps.append(P('x_hc%d' % i, (-0.3, -0.3, -0.3), (0.6, 0.6, 0.6), d['spark'][1], parent='x_h%d' % i))
    for i in range(4):
        ps.append(P('x_y%d' % i, (-1.4, -1.4, -0.02), (2.8, 2.8, 0.04), d['rune']))
    for r, (y, rad, n_, tilt) in enumerate(((3.0, 10.5, 28, 12), (17.0, 12.5, 32, -9))):
        ring = 'x_r%d' % r
        ps.append(P(ring, (-0.01, -0.01, -0.01), (0.02, 0.02, 0.02), d['ring'][r], pivot=(0, y, 0), rot=(tilt, 0, tilt / 2)))
        for i in range(n_):
            a = i / n_ * 2 * math.pi; seg = 2 * math.pi * rad / n_ * 0.78
            ps.append(P('%s_%d' % (ring, i), (-seg / 2, -0.2, -0.2), (seg, 0.4, 0.4), d['ring'][r] if i % 4 else d['spark'][0],
                        pivot=(math.cos(a) * rad, 0, math.sin(a) * rad), rot=(0, -math.degrees(a) - 90, 0), parent=ring))
    for i in range(6):
        ps.append(P('x_a%d' % i, (-0.08, -0.08, 0), (0.16, 0.16, 1.0), d['arc']))
    for i in range(2):
        ps.append(P('x_o%d' % i, (-2.4, -2.4, -0.03), (4.8, 4.8, 0.06), d['portal']))
    ps.append(P('x_wave', (-0.01, -0.01, -0.01), (0.02, 0.02, 0.02), d['spark'][0], pivot=(0, 11, 0)))
    for i in range(40):
        a = i / 40 * 2 * math.pi
        ps.append(P('x_w%d' % i, (-0.7, -0.12, -0.12), (1.4, 0.24, 0.24), d['ring'][i % 2], pivot=(math.cos(a), 0, math.sin(a)), rot=(0, -math.degrees(a) - 90, 0), parent='x_wave'))
    for i in range(16):
        ps.append(P('x_p%d' % i, (-0.2, -0.2, -0.2), (0.4, 0.4, 0.4), d['spark'][i % 2]))
    return ps

for key, d in DIMS.items():
    MODELS[key] = dict(attach='aura', parts=build_dim(key, d), info=d)

def pose_dim(key, t, wave=0.5):
    m = MODELS[key]; ps = copy.deepcopy(m['parts']); by = {p['name']: p for p in ps}
    rng = np.random.default_rng(7)
    cpos = []
    for i in range(10):
        h = rng.random(); a = i / 10 * 2 * math.pi + t * (0.5 + 0.3 * h) * (1 if i % 2 else -1) * 0.6
        r = 9 + 3 * h; y = -6 + 30 * ((i * 0.37 + h * 0.3) % 1)
        if key == 'toxic_dimension':
            y += math.sin(t * 23 + i * 5) * 0.6 * (math.sin(t * 3 + i) > 0.7)
        p = by['x_c%d' % i]; p['pivot'] = [math.cos(a) * r, y, math.sin(a) * r]
        p['rot'] = [math.degrees(t * (0.8 + h)), math.degrees(t * (1.1 - h) + i), math.degrees(h * 3)]
        cpos.append(p['pivot'])
    for i in range(6):
        h = rng.random(); a = i / 6 * 2 * math.pi + 0.4 - t * 0.35
        p = by['x_h%d' % i]; p['pivot'] = [math.cos(a) * 13, -2 + i * 4.5, math.sin(a) * 13]
        p['rot'] = [math.degrees(t * (1 + h)) * (1 if i % 2 else -1), math.degrees(t * 1.3 + i), math.degrees(t * 0.7 * h)]
    for i in range(4):
        a = i / 4 * 2 * math.pi + 0.8 + t * 0.2
        p = by['x_y%d' % i]; p['pivot'] = [math.cos(a) * 12.5, 2 + (i % 2) * 14, math.sin(a) * 12.5]; p['rot'] = [0, -math.degrees(a) + 90, math.degrees(t)]
    by['x_r0']['rot'][1] = math.degrees(t * 0.9); by['x_r1']['rot'][1] = -math.degrees(t * 0.7)
    pairs = [(0, 1), (2, 3), (4, 5), (5, 6), (7, 8), (8, 9)] if key != 'cosmic_dimension' else [(0, 1), (1, 2), (2, 3), (4, 5), (6, 7), (8, 9)]
    for i, (a, b) in enumerate(pairs):
        A, B = cpos[a], cpos[b]; L = math.dist(A, B)
        p = by['x_a%d' % i]
        if L < 16:
            p['pivot'] = list(A); p['rot'] = look(A, B); p['rot'][1] += 180; p['rot'][0] = -p['rot'][0]
            p['frm'] = [-0.08, -0.08, 0]; p['size'] = [0.16, 0.16, L]
        else:
            scaled(p, 0.0001)
    for i in range(2):
        a = i * math.pi + 2.2
        p = by['x_o%d' % i]; p['pivot'] = [math.cos(a) * 14, 6 + i * 8, math.sin(a) * 14]; p['rot'] = [0, -math.degrees(a) + 90, math.degrees(t * 2)]
        scaled(p, 0.9 if i == 0 else 0.6)
    wv = by['x_wave']; R = 4 + wave * 16
    for i in range(40):
        a = i / 40 * 2 * math.pi
        q = by['x_w%d' % i]; q['pivot'] = [math.cos(a) * R, 0, math.sin(a) * R]; scaled(q, 0.6 + wave)
    if key == 'toxic_dimension': wv['pivot'][1] = 22 - wave * 26
    for i in range(16):
        life = (t * 0.3 + i / 16) % 1; a = i * 2.4
        p = by['x_p%d' % i]; p['pivot'] = [math.cos(a) * (8 + life * 5), 20 - life * 26 if key != 'toxic_dimension' else 22 - life * 30, math.sin(a) * (8 + life * 5)]
        scaled(p, 1.2 * (1 - life))
    return ps

# ================================================================== premium dragon (v2)
_p1 = gen.paint
def paint2(mat, face, w, h):
    img = np.zeros((h, w, 4)); img[..., 3] = 255
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    if mat.startswith('pscale:'):     # overlapping fish scales, top-lit, thin metallic rim; no noise
        _, b, d, r = mat.split(':'); B, D, Rc = hexc(b), hexc(d), hexc(r)
        sc = 7.0
        row = np.floor(yy / (sc * 0.62)); off = (row % 2) * sc / 2
        cx = ((xx + off) % sc) - sc / 2; cy = (yy % (sc * 0.62)) / (sc * 0.62)
        rr = np.sqrt((cx / (sc / 2)) ** 2 + (1 - cy) ** 2 * 0.9)
        inside = rr < 1.0
        shade = np.clip(0.35 + 0.75 * cy * (1 - 0.4 * np.abs(cx) / (sc / 2)), 0, 1.2)
        col = D[None, None] + (B - D)[None, None] * shade[..., None]
        if face == 'bottom': col = col * 0.6
        rim = (rr > 0.87) & (rr < 1.0)
        col[rim] = Rc * 0.7 + col[rim] * 0.3
        hi = (rr < 0.35) & (cy > 0.55); col[hi] = np.clip(col[hi] + 50, 0, 255)
        img[..., :3] = np.clip(col, 0, 255)
        glow = np.zeros_like(img)
        glow[..., :3] = B * 0.10 * shade[..., None]; glow[..., 3] = 255
        glow[rim, :3] = Rc * 0.5
        return img, glow
    if mat.startswith('pbelly:'):     # segmented belly plates with grooves
        _, b, l = mat.split(':'); B, L = hexc(b), hexc(l)
        v = (yy % 5) / 5.0
        col = B[None, None] * (0.75 + 0.45 * (1 - v))[..., None]
        groove = (yy % 5) < 1
        col[groove] = B * 0.45
        img[..., :3] = np.clip(col, 0, 255)
        glow = np.zeros_like(img); glow[..., :3] = L * 0.08; glow[..., 3] = 255
        return img, glow
    if mat.startswith('hair:'):       # flowing strands, root colour A -> tip colour B
        _, a, b = mat.split(':'); A, Bc = hexc(a), hexc(b)
        v = yy / max(1, h - 1)
        if face in ('top', 'bottom'): v = np.full_like(v, 0.0 if face == 'top' else 1.0)
        strand = 0.8 + 0.2 * np.sin(xx * 2.1 + yy * 0.4)
        col = (A[None, None] * (1 - v[..., None]) + Bc[None, None] * v[..., None]) * strand[..., None]
        img[..., :3] = np.clip(col, 0, 255)
        return img, img.copy()
    if mat.startswith('pfin:'):       # translucent-looking fin: dark core, glowing gold edge
        _, a, b = mat.split(':'); A, Bc = hexc(a), hexc(b)
        u = xx / max(1, w - 1); v = yy / max(1, h - 1)
        col = A[None, None] * (0.5 + 0.6 * v)[..., None]
        ribs = (np.abs(((u * 4) % 1) - 0.5) < 0.12)
        col[ribs] = A * 1.3
        edge = v < 0.12
        col[edge] = Bc
        img[..., :3] = np.clip(col, 0, 255)
        glow = img.copy(); glow[~edge, :3] = glow[~edge, :3] * 0.25; glow[edge, :3] = Bc * 0.6
        return img, glow
    return _p1(mat, face, w, h)
gen.paint = paint2

NSEG2, DELTA2 = 30, 0.19

def build_premium(d):
    sc, bl, gd, hr, fn = d['pscale'], d['pbelly'], d['gold'], d['hair'], d['pfin']
    eye, mouth, tooth = d['eye'], d['mouth'], 'fab_cream'
    ps = [P('d_head', (-1.7, -1.6, -1.7), (3.4, 2.9, 3.4), sc),
          P('d_snout', (-1.2, -1.3, -5.3), (2.4, 1.6, 3.8), sc, parent='d_head'),
          P('d_ridge', (-0.45, -1.6, -5.1), (0.9, 0.35, 3.4), gd, parent='d_head'),
          P('d_nose', (-1.05, -1.4, -5.75), (2.1, 1.05, 0.6), gd, parent='d_head'),
          P('d_lip', (-1.28, 0.15, -5.4), (2.56, 0.3, 4.0), gd, parent='d_head'),
          P('d_jaw', (-1.05, 0, -4.2), (2.1, 0.75, 4.2), bl, pivot=(0, 0.45, -1.1), parent='d_head'),
          P('d_mouth', (-0.8, -0.12, -3.9), (1.6, 0.12, 3.5), mouth, parent='d_jaw')]
    for sx, n in ((-1, 'l'), (1, 'r')):
        ps += [P('d_eye' + n, (-0.14, -0.38, -0.6), (0.28, 0.76, 1.2), eye, pivot=(sx * 1.72, -0.65, -1.15), parent='d_head'),
               P('d_brow' + n, (-0.35, -0.3, -0.9), (0.7, 0.55, 1.9), gd, pivot=(sx * 1.45, -1.45, -1.2), rot=(12, 0, sx * -20), parent='d_head')]
        for j in range(3):
            ps.append(P('d_tooth%s%d' % (n, j), (-0.14, 0, -0.14), (0.28, 0.5 if j == 0 else 0.35, 0.28), tooth, pivot=(sx * 0.95, 0.4, -4.9 + j * 1.1), parent='d_head'))
        ps.append(P('d_ltooth' + n, (-0.13, -0.45, -0.13), (0.26, 0.45, 0.26), tooth, pivot=(sx * 0.8, 0, -3.6), parent='d_jaw'))
        for j in range(3):   # cheek frills fanning back
            ps.append(P('d_frill%s%d' % (n, j), (-0.1, -0.25, 0), (0.2, 0.5, 2.4 - j * 0.4), gd, pivot=(sx * 1.6, 0.3 - j * 0.6, -0.4), rot=(-10 + j * 18, sx * (35 + j * 8), 0), parent='d_head'))
        if d.get('horns') == 'spike':
            ps += [P('d_ant' + n, (-0.4, -3.6, -0.4), (0.8, 3.6, 0.8), gd, pivot=(sx * 1.0, -1.5, 0.3), rot=(-62, 0, sx * 14), parent='d_head'),
                   P('d_ant2' + n, (-0.25, -2.2, -0.25), (0.5, 2.2, 0.5), d.get('accent', gd), pivot=(0, -3.6, 0), rot=(-18, 0, 0), parent='d_ant' + n)]
        elif d.get('horns') == 'crystal':
            ps += [P('d_ant' + n, (-0.5, -3.4, -0.5), (1.0, 3.4, 1.0), gd, pivot=(sx * 1.0, -1.5, 0.2), rot=(-40, 45, sx * 18), parent='d_head'),
                   P('d_ant2' + n, (-0.3, -1.8, -0.3), (0.6, 1.8, 0.6), gd, pivot=(sx * 0.3, -1.4, 0), rot=(30, 0, sx * 40), parent='d_ant' + n)]
        else:
          # antlers: beam, second beam, two tines
          ps += [P('d_ant' + n, (-0.28, -3.0, -0.28), (0.56, 3.0, 0.56), gd, pivot=(sx * 1.0, -1.5, 0.2), rot=(-38, 0, sx * 16), parent='d_head'),
               P('d_ant2' + n, (-0.22, -2.4, -0.22), (0.44, 2.4, 0.44), gd, pivot=(0, -3.0, 0), rot=(-28, 0, sx * 12), parent='d_ant' + n),
               P('d_tine' + n, (-0.18, -1.4, -0.18), (0.36, 1.4, 0.36), gd, pivot=(0, -1.6, 0), rot=(48, 0, sx * 22), parent='d_ant' + n),
               P('d_tine2' + n, (-0.15, -1.1, -0.15), (0.3, 1.1, 0.3), gd, pivot=(0, -1.4, 0), rot=(45, 0, sx * -15), parent='d_ant2' + n)]
        # whiskers: three segments, animated
        if d.get('whisk', True): ps += [P('d_wh' + n, (-0.11, -0.11, 0), (0.22, 0.22, 3.0), hr, pivot=(sx * 1.05, -0.9, -5.4), rot=(-5, sx * 150, 0), parent='d_head'),
               P('d_wh2' + n, (-0.09, -0.09, 0), (0.18, 0.18, 2.7), hr, pivot=(0, 0, 3.0), rot=(10, sx * 15, 0), parent='d_wh' + n),
               P('d_wh3' + n, (-0.07, -0.07, 0), (0.14, 0.14, 2.3), hr, pivot=(0, 0, 2.7), rot=(15, sx * 15, 0), parent='d_wh2' + n)]
    for j in range(3):   # beard under the jaw
        ps.append(P('d_beard%d' % j, (-0.2, 0, -0.2), (0.4, 2.0 - j * 0.3, 0.4), hr, pivot=((j - 1) * 0.55, 0.7, -2.6 + j * 0.4), rot=(35, 0, (j - 1) * 10), parent='d_jaw'))
    for j in range(5):   # mane behind the head
        ps.append(P('d_mane%d' % j, (-0.3, -2.4, -0.3), (0.6, 2.4, 0.6), hr, pivot=((j - 2) * 0.65, -1.3 + abs(j - 2) * 0.4, 1.4), rot=(-62, 0, (j - 2) * 22), parent='d_head'))
    HS = 1.35
    for p in ps:
        p['frm'] = [v * HS for v in p['frm']]; p['size'] = [v * HS for v in p['size']]
        if p['parent']: p['pivot'] = [v * HS for v in p['pivot']]
    for i in range(NSEG2):
        f = i / (NSEG2 - 1); w = 2.9 - 1.9 * f ** 1.2; L = 3.0
        n = 'd_s%d' % i
        ps += [P(n, (-w / 2, -w / 2, -L / 2), (w, w, L), sc),
               P(n + 'b', (-w / 2 + 0.25, w / 2 - 0.12, -L / 2 + 0.1), (w - 0.5, 0.3, L - 0.2), bl, parent=n),
               ]
        bk = d.get('back', 'fin')
        if bk == 'fin':
            ps.append(P(n + 'f', (-0.08, -w / 2 - 0.9 * (1 - f * 0.6), -1.1), (0.16, 0.9 * (1 - f * 0.6), 2.2), fn, parent=n))
        elif bk == 'spikes' and i % 2 == 0:
            hh = 1.9 * (1 - f * 0.6)
            ps.append(P(n + 'f', (-0.3, -hh, -0.3), (0.6, hh, 0.6), d['accent'], pivot=(0, -w / 2 + 0.1, 0.3), rot=(-35, 0, 0), parent=n))
        elif bk == 'crystals' and i % 2 == 0:
            hh = 2.2 * (1 - f * 0.55)
            ps.append(P(n + 'f', (-0.45, -hh, -0.45), (0.9, hh, 0.9), d['accent'], pivot=(0, -w / 2 + 0.1, 0), rot=(-12, 45, 0), parent=n))
            ps.append(P(n + 'g', (-0.3, -hh * 0.6, -0.3), (0.6, hh * 0.6, 0.6), d['accent'], pivot=(0.4, -w / 2 + 0.1, 0.6), rot=(-25, 45, 25), parent=n))
        elif bk == 'flames':
            hh = (1.3 + 0.9 * abs(math.sin(i * 1.3))) * (1 - f * 0.5)
            ps.append(P(n + 'f', (-0.22, -hh, -0.8), (0.44, hh, 1.6), d['accent'], pivot=(0, -w / 2 + 0.1, 0), rot=(-25, 0, 0), parent=n))
        elif bk == 'feathers' and i % 2 == 1:
            for sx in (-1, 1):
                ps.append(P(n + 'w%d' % sx, (-0.1, -0.12, 0), (0.2, 0.24, 3.2 * (1 - f * 0.7)), d['accent'], pivot=(sx * w / 2, -w / 2 + 0.3, 0.2), rot=(-20, sx * 135, 0), parent=n))
        if i < 7 and d.get('neckmane', True):   # mane continues down the neck
            ps.append(P(n + 'm', (-0.25, -1.8 * (1 - i / 8), -0.25), (0.5, 1.8 * (1 - i / 8), 0.5), hr, pivot=(0, -w / 2, 0.6), rot=(-55, 0, 0), parent=n))
    for i in (5, 19):    # four legs with three gold talons each
        n = 'd_s%d' % i
        for sx in (-1, 1):
            leg = n + 'leg%d' % sx
            ps += [P(leg, (-0.45, 0, -0.45), (0.9, 1.8, 0.9), sc, pivot=(sx * 1.0, 0.6, 0), rot=(35, 0, sx * 40), parent=n),
                   P(leg + 'b', (-0.38, 0, -0.38), (0.76, 1.6, 0.76), sc, pivot=(0, 1.8, 0), rot=(-60, 0, 0), parent=leg)]
            for t in range(3):
                ps.append(P(leg + 't%d' % t, (-0.12, 0, -0.12), (0.24, 0.9, 0.24), gd, pivot=((t - 1) * 0.28, 1.6, -0.2), rot=(40, 0, (t - 1) * 15), parent=leg + 'b'))
    last = 'd_s%d' % (NSEG2 - 1)
    for j in range(5):
        ps.append(P('d_tail%d' % j, (-0.25, -0.25, 0), (0.5, 0.5, 3.6 - abs(j - 2) * 0.6), hr, pivot=(0, 0, 1.4), rot=((j - 2) * 14, (j - 2) * 12, 0), parent=last))
    if d.get('halo'):
        for k in range(20):
            a = k / 20 * 2 * math.pi
            ps.append(P('d_halo%d' % k, (-0.75, -0.14, -0.14), (1.5, 0.28, 0.28), 'gold', pivot=(math.cos(a) * 4.8, -12.8, math.sin(a) * 4.8), rot=(0, -math.degrees(a) - 90, 0)))
    ps.append(P('d_orb', (-0.7, -0.7, -0.7), (1.4, 1.4, 1.4), d['orb']))
    ps.append(P('d_orbr', (-1.2, -0.08, -1.2), (2.4, 0.16, 2.4), gd, rot=(90, 0, 0), parent='d_orb'))
    for k in range(20):
        ps.append(P('d_p%d' % k, (-0.15, -0.15, -0.15), (0.3, 0.3, 0.3), d['spark'][k % 2]))
    for k in range(10):
        ps.append(P('d_k%d' % k, (-0.8, -0.8, -0.04), (1.6, 1.6, 0.08), d['smoke']))
    return ps

ETERNAL2 = dict(name='Eternal Dragon', rarity='MYTHIC', pscale='pscale:22D07F:04261B:E9B949', pbelly='pbelly:E8B347:FFD86A',
                gold='gold', hair='hair:1FBF74:F5D06A', pfin='pfin:0F5A3E:F5C451', eye='neon:FFFFFF', mouth='neon:7CFFB5',
                orb='neon:9CFFC8', spark=('neon:5CFFAA', 'neon:FFD24A'), smoke='smoke:1A5A44', path='eternal')
MODELS['eternal_dragon'] = dict(attach='aura', parts=build_premium(ETERNAL2), info=dict(ETERNAL2, anim=''))
PREMIUM = {'eternal_dragon': ETERNAL2}
PREMIUM['shadow_dragon'] = dict(name='Shadow Dragon', rarity='MYTHIC', pscale='pscale:2A2A33:050507:D8203A', pbelly='pbelly:5A0E18:FF2A44',
    gold='metal:1C1C22', accent='neon:E0203C', hair='hair:3A0A12:FF2A44', pfin='pfin:120408:E0203C', eye='neon:FF2A44', mouth='neon:FF2A44',
    orb='neon:FF4060', spark=('neon:FF2A44', 'neon:7A0018'), smoke='smoke:22080E', path='tilted', horns='spike', whisk=False, back='spikes', neckmane=False)
PREMIUM['frost_dragon'] = dict(name='Frost Dragon', rarity='LEGENDARY', pscale='pscale:9FE4FF:1E5C8A:FFFFFF', pbelly='pbelly:DDF4FF:FFFFFF',
    gold='metal:D6EEFA', accent='crystal:BFE8FF', hair='hair:BFEFFF:FFFFFF', pfin='pfin:2E78A8:FFFFFF', eye='neon:FFFFFF', mouth='neon:CFF4FF',
    orb='neon:E6FAFF', spark=('neon:CFF4FF', 'neon:FFFFFF'), smoke='smoke:D8F2FF', path='helix', horns='crystal', back='crystals', neckmane=False)
PREMIUM['infernal_dragon'] = dict(name='Infernal Dragon', rarity='LEGENDARY', pscale='pscale:FF6A1A:3A0802:FFD24A', pbelly='pbelly:3A1206:FF8A1A',
    gold='metal:2A1A14', accent='neon:FF8A1A', hair='hair:FF3A10:FFE070', pfin='pfin:5A1004:FFB040', eye='neon:FFF2B0', mouth='neon:FFB040',
    orb='neon:FFB040', spark=('neon:FF8A1A', 'neon:FF2A10'), smoke='smoke:2A1410', path='fast', horns='spike', whisk=False, back='flames')
PREMIUM['celestial_dragon'] = dict(name='Celestial Dragon', rarity='MYTHIC', pscale='pscale:FFF4D6:B08A2E:FFFFFF', pbelly='pbelly:F2C14E:FFF1B0',
    gold='gold', accent='hair:FFFFFF:FFE59A', hair='hair:FFFFFF:F5D06A', pfin='pfin:C9A23A:FFFFFF', eye='neon:9FE8FF', mouth='neon:FFF1B0',
    orb='neon:FFFFFF', spark=('neon:FFFFFF', 'neon:FFD24A'), smoke='smoke:FFF1C8', path='loop', back='feathers', halo=True)
for _k, _d in PREMIUM.items():
    MODELS[_k] = dict(attach='aura', parts=build_premium(_d), info=dict(_d, anim=''))

def pose_premium(key, t, style_override=None):
    m = MODELS[key]; d = m['info']; ps = copy.deepcopy(m['parts']); by = {p['name']: p for p in ps}
    th0 = t * 0.85 + 1.0
    style = style_override or d.get('path', 'eternal')
    def path(s, i):
        if style == 'pet':     # companion: a loose coil hovering beside you, head up and looking ahead
            cx, cz = 17.0, 8.0
            ii = max(i, -1.6)
            R = 4.2 + 0.08 * ii
            return cx + R * math.cos(s * 1.7), -4 + ii * 0.75 + 1.5 * math.sin(t * 1.2), cz + R * math.sin(s * 1.7)
        if style == 'tilted':
            R = 12
            return R * math.cos(s), 5 + 0.55 * R * math.sin(s), R * math.sin(s) * 0.85
        if style == 'helix':
            R = 10.5
            return R * math.cos(s), -7 + max(i, -1.5) * 1.0 + 3 * math.sin(t * 0.7), R * math.sin(s)
        if style == 'fast':
            R = 11
            return R * math.cos(s), 8 + 1.8 * math.sin(3 * s + t), R * math.sin(s)
        if style == 'loop':
            R = 11.5 + 3 * math.sin(2 * s)
            return R * math.cos(s), 2 + 5 * math.sin(s), R * math.sin(s)
        R = 11.5 + 0.8 * math.sin(2 * s + 0.6 * t)
        y = 5 + 3.0 * math.sin(1.4 * s - 1.0 * t)
        rise = max(0.0, math.sin(2 * math.pi * (t - 0.5) / 9)) ** 2 * 12 * math.exp(-max(i, 0) / 5)
        return R * math.cos(s), y - rise, R * math.sin(s)
    if style == 'fast': th0 = t * 1.6 + 1.0
    pts = [path(th0 - i * DELTA2, i) for i in range(NSEG2 + 1)]
    hp = path(th0 + DELTA2 * 1.6, -1)
    for i in range(NSEG2):
        p = by['d_s%d' % i]; p['pivot'] = list(pts[i]); r = look(pts[i], pts[i - 1] if i else hp)
        r[2] = 12 * math.sin(t * 1.5 - i * 0.3); p['rot'] = r
    h = by['d_head']; h['pivot'] = list(hp); h['rot'] = look(pts[0], hp)
    # jaw opens every 6.5 s, an energy orb leaves the mouth
    tau = ((t + 3.0) % 6.5) / 6.5
    jaw = max(0.0, math.sin(tau / 0.5 * math.pi)) if tau < 0.5 else 0.0
    by['d_jaw']['rot'] = [35 * jaw, 0, 0]
    o = by['d_orb']; u = (tau - 0.18) / 0.32
    if 0 < u < 1:
        fx, fy, fz = hp[0] - pts[0][0], hp[1] - pts[0][1], hp[2] - pts[0][2]; L = math.sqrt(fx * fx + fy * fy + fz * fz)
        k = 7 + u * 12
        o['pivot'] = [hp[0] + fx / L * k, hp[1] + fy / L * k + 0.8, hp[2] + fz / L * k]
        o['rot'] = [0, math.degrees(t * 4), 0]; scaled(o, (0.6 + 1.4 * u) * (1 - u ** 4)); scaled(by['d_orbr'], 1.0)
    else:
        scaled(o, 1e-4); scaled(by['d_orbr'], 1e-4)
    for n in ('l', 'r'):   # whiskers and mane sway
        sx = -1 if n == 'l' else 1
        if 'd_wh' + n not in by: continue
        by['d_wh' + n]['rot'][0] = -5 + 12 * math.sin(t * 2.4 + sx)
        by['d_wh2' + n]['rot'][0] = 10 + 18 * math.sin(t * 2.4 + 0.7 + sx)
        by['d_wh3' + n]['rot'][0] = 15 + 22 * math.sin(t * 2.4 + 1.4 + sx)
    for j in range(5):
        by['d_mane%d' % j]['rot'][0] = -62 + 10 * math.sin(t * 3 + j)
    for k in range(20):
        life = (t * 0.4 + k / 20) % 1; j = 1 + (k * 7) % (NSEG2 - 2)
        x, y, z = path(th0 - j * DELTA2 - life * 0.35, j); r = math.sqrt(x * x + z * z) or 1
        p = by['d_p%d' % k]; p['pivot'] = [x + x / r * life * 2.0 + math.sin(k * 3) * 0.8, y - life * 3.5, z + z / r * life * 2.0]
        scaled(p, 1.3 * (1 - life) * (0.6 + 0.4 * math.sin(t * 12 + k) ** 2))
    for k in range(10):
        life = (t * 0.25 + k / 10) % 1; j = 3 + (k * 3) % (NSEG2 - 4)
        x, y, z = path(th0 - j * DELTA2 - life * 0.3, j)
        p = by['d_k%d' % k]; p['pivot'] = [x, y - life * 1.5, z]; p['rot'] = [math.degrees(t * 0.3), -math.degrees(th0) + k * 40, k * 30]
        scaled(p, (0.8 + 1.2 * life) * (1 - life ** 2))
    return ps

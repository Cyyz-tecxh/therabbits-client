# Textured z-buffer preview renderer for the generated cosmetics (same JSON/atlas data the mod loads).
import math, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import gen

def rotZYX(rx, ry, rz):
    rx, ry, rz = map(math.radians, (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx

def M(pivot, rot):
    m = np.eye(4); m[:3, :3] = rotZYX(*rot); m[:3, 3] = pivot; return m

def quads_for(parts, tex, texsize, glow, base):
    """yields (corners TL,TR,BL in world, uv rect in texels, texture, glow, cube centre)"""
    by = {p['name']: p for p in parts}
    cache = {}
    def world(p):
        if p['name'] in cache: return cache[p['name']]
        m = M(p['pivot'], p['rot'])
        m = (world(by[p['parent']]) if p['parent'] else base) @ m
        cache[p['name']] = m; return m
    out = []
    for p in parts:
        m = world(p)
        (x0, y0, z0), (w, h, d) = p['frm'], p['size']
        x1, y1, z1 = x0 + w, y0 + h, z0 + d
        corners = dict(
            front=((x0, y0, z0), (x1, y0, z0), (x0, y1, z0)), back=((x1, y0, z1), (x0, y0, z1), (x1, y1, z1)),
            right=((x0, y0, z1), (x0, y0, z0), (x0, y1, z1)), left=((x1, y0, z0), (x1, y0, z1), (x1, y1, z0)),
            top=((x0, y0, z1), (x1, y0, z1), (x0, y0, z0)), bottom=((x0, y1, z0), (x1, y1, z0), (x0, y1, z1)))
        cen = (m @ np.array([(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2, 1]))[:3]
        for face, (fu, fv, fw, fh) in gen.face_regions(*p['uv'], w, h, d).items():
            pts = [(m @ np.array([*c, 1]))[:3] for c in corners[face]]
            out.append((pts, (round(fu * gen.S), round(fv * gen.S), max(1, round(fw * gen.S)), max(1, round(fh * gen.S))), tex, glow, cen))
    return out

AMB, DIF = 0.42, 0.68
LAST_ZB = None
LIGHT = np.array([-0.45, -0.75, -0.5]); LIGHT /= np.linalg.norm(LIGHT)

def render(quads, W, H, yaw, pitch, scale, cx, cy):
    ry = rotZYX(0, yaw, 0); rp = rotZYX(pitch, 0, 0); R = rp @ ry
    col = np.zeros((H, W, 3)); zb = np.full((H, W), 1e9); gl = np.zeros((H, W, 3)); mask = np.zeros((H, W))
    yy, xx = np.mgrid[0:H, 0:W]
    for pts, (u, v, tw, th), tex, glow, cen in quads:
        wp = [R @ p for p in pts]; c = R @ cen
        e1, e2 = wp[1] - wp[0], wp[2] - wp[0]
        n = np.cross(e1, e2); nl = np.linalg.norm(n)
        if nl < 1e-9: continue
        n /= nl
        mid = wp[0] + (e1 + e2) / 2
        if np.dot(n, mid - c) < 0: n = -n            # make outward
        if n[2] > 0.02: continue                     # back-facing (viewer at -z)
        sp = [np.array([cx + q[0] * scale, cy + q[1] * scale]) for q in wp]
        a, b = sp[1] - sp[0], sp[2] - sp[0]
        det = a[0] * b[1] - a[1] * b[0]
        if abs(det) < 1e-6: continue
        allp = [sp[0], sp[1], sp[2], sp[1] + b]
        X0, X1 = int(max(0, min(p[0] for p in allp))), int(min(W - 1, max(p[0] for p in allp)) + 1)
        Y0, Y1 = int(max(0, min(p[1] for p in allp))), int(min(H - 1, max(p[1] for p in allp)) + 1)
        if X1 <= X0 or Y1 <= Y0: continue
        px = xx[Y0:Y1, X0:X1] + 0.5 - sp[0][0]; py = yy[Y0:Y1, X0:X1] + 0.5 - sp[0][1]
        s = (px * b[1] - py * b[0]) / det; t = (a[0] * py - a[1] * px) / det
        inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
        if not inside.any(): continue
        z = wp[0][2] + s * e1[2] + t * e2[2]
        tu = np.clip((u + s * tw).astype(int), u, u + tw - 1); tv = np.clip((v + t * th).astype(int), v, v + th - 1)
        texel = tex[tv, tu].astype(float)
        inside &= texel[..., 3] > 8
        sub = zb[Y0:Y1, X0:X1]
        win = inside & (z < sub)
        if not win.any(): continue
        diff = max(0.0, -np.dot(n, LIGHT))
        shade = AMB + DIF * diff
        rim = 0.12 * max(0.0, -n[0])
        rgb = texel[..., :3] * (shade + rim)
        if glow is not None:
            gt = glow[tv, tu].astype(float)
            g = gt[..., 3:4] / 255 > 0.1
            rgb = np.where(g, texel[..., :3] * 1.05, rgb)
            gl[Y0:Y1, X0:X1][win] = np.where(g, gt[..., :3], 0)[win]
        col[Y0:Y1, X0:X1][win] = rgb[win]
        sub[win] = z[win]; mask[Y0:Y1, X0:X1][win] = 1
        # clear glow where an opaque non-glow surface wins
        if glow is None:
            gl[Y0:Y1, X0:X1][win] = 0
    global LAST_ZB
    LAST_ZB = zb
    return col, gl, mask

def scene(key, yaw, pitch, pose):
    q = []
    m = gen.MODELS[key]
    for bname, b in gen.BODY.items():
        rot = pose.get(bname, (0, 0, 0))
        base = M(b['pivot'], rot)
        q += quads_for(b['parts'], b['tex'], b['texsize'], None, base)
        if m['attach'] == 'head' and bname == 'head':
            q += quads_for(m['parts'], m['tex'], m['texsize'], m['glow'], base)
        if m['attach'] == 'arms' and bname == 'right_arm':
            q += quads_for(m['parts'], m['tex'], m['texsize'], m['glow'], base)
        if m['attach'] == 'arms' and bname == 'left_arm':
            mp = gen.mirror(m['parts'])
            for a, b2 in zip(mp, m['parts']): a['uv'] = b2['uv']
            q += quads_for(mp, m['tex'], m['texsize'], m['glow'], base)
    return q

def tile(key, W, H, yaw, pitch, scale, cx, cy, pose, ss=2):
    Wb, Hb = W * ss, H * ss
    col, gl, mask = render(scene(key, yaw, pitch, pose), Wb, Hb, yaw, pitch, scale * ss, cx * ss, cy * ss)
    yy = np.linspace(0, 1, Hb)[:, None, None]
    xx = np.linspace(-1, 1, Wb)[None, :, None]
    bg = (34 - 16 * yy) * (1 - 0.25 * xx ** 2) * np.ones((1, 1, 3))
    bg = bg + np.array([0, 0, 2])
    # floor shadow
    fy = (cy + 33 * scale) * ss
    sh = np.exp(-(((np.arange(Wb)[None, :] - cx * ss) / (9 * scale * ss)) ** 2 + ((np.arange(Hb)[:, None] - fy) / (1.6 * scale * ss)) ** 2))
    bg = bg * (1 - 0.6 * sh[..., None])
    img = np.where(mask[..., None] > 0, col, bg)
    g = Image.fromarray(np.clip(gl, 0, 255).astype(np.uint8))
    bloom = np.asarray(g.filter(ImageFilter.GaussianBlur(6 * ss))).astype(float) * 1.6 + np.asarray(g.filter(ImageFilter.GaussianBlur(2 * ss))).astype(float) * 0.6
    img = np.clip(img + bloom, 0, 255).astype(np.uint8)
    return Image.fromarray(img).resize((W, H), Image.LANCZOS)

if __name__ == '__main__':
    gen.export('/home/claude/cos/out')
    F = gen  # noqa
    font = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 30)
    small = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 18)
    names = [('fedora', 'Black Fedora', 'Hat'), ('crown', 'Spiked Crown', 'Hat'), ('ears', 'Rabbit Ears', 'Hat'), ('claws', 'Claws', 'Arms')]
    W, H = 1720, 640
    out = Image.new('RGB', (W, H), (16, 16, 18)); d = ImageDraw.Draw(out)
    for i, (k, t, cat) in enumerate(names):
        if k == 'claws':
            im = tile(k, 410, 540, -28, -12, 11.5, 205, 175, dict(right_arm=(-38, 0, 8), left_arm=(-20, 0, -8)))
        else:
            im = tile(k, 410, 540, -30, -14, 14, 205, 300, dict(head=(0, -8, 0)))
        x = 15 + i * 427
        out.paste(im, (x, 15))
        d.text((x + 205 - d.textlength(t, font=font) / 2, 568), t, font=font, fill=(240, 240, 240))
        d.text((x + 205 - d.textlength(cat, font=small) / 2, 606), cat.upper(), font=small, fill=(140, 140, 146))
    out.save('/home/claude/cosmetics_preview.png')
    # close-ups
    print('done')

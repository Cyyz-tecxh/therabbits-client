# Voxel Drift aura (id voxel_aura): translucent cyan voxel cubes that float, tumble and orbit around the player at
# different speeds and distances. Motion maths is mirrored in CosmeticsLayer.animateCubes.
import os, sys, math, json, numpy as np
from PIL import Image
import gen, render as R

N = 40
S = 8
OUT = '/home/claude/repo/src/main/resources/assets/therabbits'

def h(a, b):
    v = math.sin(a * 12.9898 + b * 78.233) * 43758.5453
    return v - math.floor(v)

# ---------- texture: two cube variants, box UV 2x2x2 at u=0 and u=8 (texture 16x4 texels) ----------
VARIANTS = [
    dict(fill=(96, 178, 184), edge=(150, 214, 220)),   # pale cyan
    dict(fill=(72, 150, 158), edge=(126, 196, 204)),    # deeper turquoise
]
def paint_face(w, hgt, fill, edge, face):
    a = np.zeros((hgt, w, 4), np.uint8)
    lift = 38 if face == 'top' else -22 if face == 'bottom' else 0
    for y in range(hgt):
        for x in range(w):
            g = 1 - (x + y) / (w + hgt) * 0.35          # soft highlight towards the top-left
            c = [min(255, max(0, int(ch * g) + lift)) for ch in fill]
            al = 200
            if x < 2 or y < 2 or x >= w - 2 or y >= hgt - 2:   # bright translucent edges
                c = [min(255, ch + lift) for ch in edge]; al = 225
            a[y, x] = (*c, al)
    return a

def atlas():
    tex = np.zeros((4 * S, 16 * S, 4), np.uint8)
    for k, v in enumerate(VARIANTS):
        for face, (fu, fv, fw, fh) in gen.face_regions(k * 8, 0, 2, 2, 2).items():
            x0, y0, W, H = round(fu * S), round(fv * S), round(fw * S), round(fh * S)
            tex[y0:y0 + H, x0:x0 + W] = paint_face(W, H, v['fill'], v['edge'], face)
    return tex

# ---------- motion ----------
def cube(i, t, density=0.8, spread=1.3):
    """-> (visible, x, y, z, rx, ry, rz, scale)"""
    if h(i, 99) > density: return (False,) + (0,) * 7
    dirn = 1 if i % 2 else -1
    a = h(i, 1) * 6.2832 + t * (0.10 + h(i, 2) * 0.22) * dirn            # orbit at its own speed
    r = (3.4 + h(i, 3) * 4.2 + math.sin(t * (0.25 + h(i, 5) * 0.3) + i * 1.7) * 1.4) * spread   # drifts in and out
    y = -12 + h(i, 4) * 36 + math.sin(t * (0.5 + h(i, 6) * 0.6) + i * 2.1) * 1.6                 # bobs up and down
    rx = math.sin(t * (0.3 + h(i, 7) * 0.4) + h(i, 8) * 6.2832) * 0.45          # gentle tilt, mostly upright
    ry = h(i, 10) * 6.2832 + t * (0.15 + h(i, 9) * 0.3) * dirn
    rz = math.sin(t * 0.25 + i) * 0.35 * h(i, 11)
    s = 0.7 + h(i, 12) * 0.6
    return True, math.sin(a) * r, y, math.cos(a) * r, rx, ry, rz, s

def parts_at(t, density=0.8, spread=1.3):
    out = []
    for i in range(N):
        vis, x, y, z, rx, ry, rz, s = cube(i, t, density, spread)
        if not vis: continue
        out.append(dict(name=f'vc{i}', parent=None, pivot=[x, y, z], rot=[math.degrees(rx), math.degrees(ry), math.degrees(rz)],
                        frm=[-s, -s, -s], size=[2 * s] * 3, uvsize=(2, 2, 2), uv=[(i % 2) * 8, 0]))
    return out

# ---------- preview: opaque body, then translucent cubes blended back to front ----------
def blend(quads, img, zb, yaw, pitch, scale, cx, cy):
    H, W = zb.shape
    ry = R.rotZYX(0, yaw, 0); rp = R.rotZYX(pitch, 0, 0); RM = rp @ ry
    yy, xx = np.mgrid[0:H, 0:W]
    items = []
    for pts, uv, tex, glow, cen in quads:
        wp = [RM @ p for p in pts]; c = RM @ cen
        items.append(((wp[0][2] + wp[1][2] + wp[2][2]) / 3, wp, c, uv, tex))
    items.sort(key=lambda q: -q[0])                     # far first
    for _, wp, c, (u, v, tw, th), tex in items:
        e1, e2 = wp[1] - wp[0], wp[2] - wp[0]
        n = np.cross(e1, e2); nl = np.linalg.norm(n)
        if nl < 1e-9: continue
        n /= nl
        if np.dot(n, wp[0] + (e1 + e2) / 2 - c) < 0: n = -n
        if n[2] > 0.02: continue
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
        z = wp[0][2] + s * e1[2] + t * e2[2]
        inside &= z < zb[Y0:Y1, X0:X1]
        if not inside.any(): continue
        tu = np.clip((u + s * tw).astype(int), 0, tex.shape[1] - 1); tv = np.clip((v + t * th).astype(int), 0, tex.shape[0] - 1)
        texel = tex[tv, tu].astype(float)
        shade = 0.82 + 0.3 * max(0.0, -np.dot(n, R.LIGHT))
        al = (texel[..., 3:4] / 255) * inside[..., None]
        sub = img[Y0:Y1, X0:X1]
        img[Y0:Y1, X0:X1] = sub * (1 - al) + np.clip(texel[..., :3] * shade, 0, 255) * al
    return img

BG = (22, 23, 25)
def shot(t, yaw=0.0, W=720, H=1000, scale=20, cy=0.36, density=0.8, spread=1.3):
    SS = 2; gen.S = S
    if not hasattr(shot, 'tex'): shot.tex = atlas()
    q = []
    for name, b in gen.BODY.items():
        q += R.quads_for(b['parts'], b['tex'], b['texsize'], None, R.M(b['pivot'], (0, 0, 0)))
    col, gl, mask = R.render(q, W * SS, H * SS, yaw, 2, scale * SS, W / 2 * SS, H * cy * SS)
    zb = R.LAST_ZB.copy()
    img = np.where(mask[..., None] > 0, col, np.array(BG, float))
    cq = R.quads_for(parts_at(t, density, spread), shot.tex, (16, 4), None, np.eye(4))
    img = blend(cq, img, zb, yaw, 2, scale * SS, W / 2 * SS, H * cy * SS)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((W, H), Image.LANCZOS)

def icon():
    gen.S = S
    tex = atlas()
    W = 256
    img = np.zeros((W, W, 3)); zb = np.full((W, W), 1e9)
    ps = [dict(name='a', parent=None, pivot=[-1.2, 0.8, 0], rot=[30, 40, 6], frm=[-1.6] * 3, size=[3.2] * 3, uvsize=(2, 2, 2), uv=[0, 0]),
          dict(name='b', parent=None, pivot=[2.0, -1.8, 1], rot=[50, 18, 22], frm=[-1.0] * 3, size=[2.0] * 3, uvsize=(2, 2, 2), uv=[8, 0]),
          dict(name='c', parent=None, pivot=[1.6, 2.4, -1], rot=[12, 63, 34], frm=[-0.7] * 3, size=[1.4] * 3, uvsize=(2, 2, 2), uv=[0, 0]),
          dict(name='d', parent=None, pivot=[-2.6, -2.4, 0], rot=[70, 22, 12], frm=[-0.5] * 3, size=[1.0] * 3, uvsize=(2, 2, 2), uv=[8, 0])]
    q = R.quads_for(ps, tex, (16, 4), None, np.eye(4))
    acc = np.zeros((W, W, 3)); alpha = np.zeros((W, W, 1))
    # blend over black and over white to recover straight alpha
    k = blend(q, np.zeros((W, W, 3)), zb, 0, 0, 40, W / 2, W / 2)
    w = blend(q, np.full((W, W, 3), 255.0), zb, 0, 0, 40, W / 2, W / 2)
    al = np.clip(1 - (w - k).mean(axis=2, keepdims=True) / 255, 0, 1)
    rgb = np.where(al > 0.01, k / np.maximum(al, 1e-3), 0)
    out = np.concatenate([np.clip(rgb, 0, 255), al * 255], axis=2).astype(np.uint8)
    Image.fromarray(out, 'RGBA').resize((128, 128), Image.LANCZOS).save(f'{OUT}/textures/gui/cosmetic_icons/voxel_aura.png')

if __name__ == '__main__':
    R.AMB, R.DIF = 0.62, 0.5
    gen.export('/tmp/sign_body')
    if sys.argv[1] == 'still':
        o = Image.new('RGB', (720 * 3, 1000))
        for k, (t, yaw) in enumerate(((1.0, 0), (4.0, 0), (7.5, 0.45))): o.paste(shot(t, yaw), (k * 720, 0))
        o.save('/home/claude/voxel_aura_design.png')
    elif sys.argv[1] == 'export':
        Image.fromarray(atlas(), 'RGBA').save(f'{OUT}/textures/cosmetics/voxel_aura.png')
        js = dict(attach='aura', texture=[16, 4], glow=False,
                  parts=[dict(name=f'vc{i}', parent=None, pivot=[0, 0, 0], rot=[0, 0, 0], **{'from': [-1, -1, -1]},
                              size=[2, 2, 2], uv=[(i % 2) * 8, 0]) for i in range(N)])
        open(f'{OUT}/cosmetics/voxel_aura.json', 'w').write(json.dumps(js, indent=1))
        icon(); print('exported')
    else:
        os.makedirs('/tmp/vxv', exist_ok=True)
        for f in range(int(sys.argv[1]), int(sys.argv[2])):
            shot(f / 30).save(f'/tmp/vxv/{f:04d}.jpg', quality=93)

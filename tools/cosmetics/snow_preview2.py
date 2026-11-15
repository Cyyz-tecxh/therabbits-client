import os, sys, math, copy, subprocess, numpy as np
from PIL import Image, ImageFilter
import gen, render as R
gen.export('/tmp/snow_out')
R.AMB, R.DIF = 0.6, 0.55
m = gen.MODELS['snow_aura']
W, H, SS, N = 720, 900, 2, 180
base = m['parts']; K = 1.3; FACE = [0.0]   # default Aura Size 1.6 -> 1.3 spread for snow

def animate(t):
    parts = copy.deepcopy(base); by = {p['name']: p for p in parts}
    i = 0
    while 'f%d' % i in by:
        f = by['f%d' % i]; ip = base[i]
        h = (i * 0.6180339) % 1
        drift = t * (0.10 + h * 0.12) * (1 if i % 2 == 0 else -1)
        x0, y0, z0 = ip['pivot']; c, s = math.cos(drift), math.sin(drift)
        span, top = 36.0, -11.0
        fall = ((y0 - top) + t * (2.2 + h * 1.6)) % span
        xr, zr = (x0 * c - z0 * s) * K, (x0 * s + z0 * c) * K
        f['pivot'] = [xr + math.sin(t * (0.9 + h * 0.5) + i * 1.3) * 0.6, top + fall, zr]
        f['rot'] = [0, FACE[0] + math.degrees(math.sin(t * (0.5 + h * 0.4) + i * 1.7) * 0.45), math.degrees(t * (0.25 + h * 0.3) * (-1 if i % 3 == 0 else 1))]
        u = fall / span
        a = u / 0.1 if u < 0.1 else (1 - u) / 0.12 if u > 0.88 else 1
        sc = (0.9 + h * 0.3) * max(0, a)
        f['frm'] = [v * sc for v in f['frm']]; f['size'] = [max(1e-4, v * sc) for v in f['size']]
        i += 1
    return parts

def frame(t, yaw):
    FACE[0] = -yaw
    q = []
    for name, b in gen.BODY.items():
        q += R.quads_for(b['parts'], b['tex'], b['texsize'], None, R.M(b['pivot'], (0, 0, 0)))
    q += R.quads_for(animate(t), m['tex'], m['texsize'], m['glow'], np.eye(4))
    col, gl, mask = R.render(q, W * SS, H * SS, yaw, 4, 22 * SS, W / 2 * SS, H * 0.3 * SS)
    yy = np.linspace(0, 1, H * SS)[:, None, None]
    bg = (24 - 8 * yy) * np.array((0.9, 0.95, 1.1))[None, None] * np.ones((1, W * SS, 1))
    img = np.where(mask[..., None] > 0, col, bg)
    g = Image.fromarray(np.clip(gl, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(4 * SS))
    img = img + np.asarray(g, float) * 0.25
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((W, H), Image.LANCZOS)

if __name__ == '__main__':
    if sys.argv[1] == 'still':
        o = Image.new('RGB', (W * 3, H))
        for k, (t, yaw) in enumerate(((3, -20), (7, 25), (11, 160))): o.paste(frame(t, yaw), (k * W, 0))
        o.save('/home/claude/snow_aura_v2.png')
    elif sys.argv[1] == 'enc':
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', '30', '-i', '/tmp/snv/%04d.jpg', '-f', 'lavfi', '-i', 'anullsrc=r=48000:cl=stereo', '-shortest',
                        '-c:v', 'libx264', '-crf', '17', '-preset', 'veryfast', '-pix_fmt', 'yuv420p', '-profile:v', 'main', '-c:a', 'aac', '-movflags', '+faststart', '/home/claude/snow.tmp.mp4'], check=True)
        os.replace('/home/claude/snow.tmp.mp4', '/home/claude/Snow_Aura_preview.mp4')
    else:
        os.makedirs('/tmp/snv', exist_ok=True)
        for f in range(int(sys.argv[1]), int(sys.argv[2])):
            frame(f / 30, -35 + 70 * math.sin(f / N * 2 * math.pi)).save(f'/tmp/snv/{f:04d}.jpg', quality=93)

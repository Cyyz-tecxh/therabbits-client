import numpy as np, subprocess, os
from PIL import Image, ImageDraw
W, H, SC = 196, 446, 3
BODY = (184, 184, 184)
im = Image.new('RGB', (W, H), (22, 22, 22)); d = ImageDraw.Draw(im)
hx0, hx1 = 44, 152
d.rectangle((hx0, 14, hx1, 112), fill=(186, 186, 186)); d.rectangle((hx0 - 2, 22, hx1 + 2, 60), fill=(140, 140, 140))
for x in (hx0, hx1 - 10): d.rectangle((x, 6, x + 10, 16), fill=(200, 200, 200))
d.rectangle((6, 112, hx0 - 1, 296), fill=BODY); d.rectangle((hx1 + 1, 112, 190, 296), fill=BODY)
d.rectangle((6, 112, hx0 + 2, 116), fill=(250, 250, 250)); d.rectangle((hx1 - 2, 112, 190, 116), fill=(250, 250, 250))
d.rectangle((hx0, 296, hx1, 440), fill=BODY); d.line((98, 300, 98, 440), fill=(160, 160, 160))
base = np.asarray(im).copy()
px0, py0, pw, ph = hx0, 112, hx1 - hx0, 184     # the cape panel over the torso
os.makedirs('/tmp/lpv', exist_ok=True)
tmp = '/home/claude/lt.tmp.mp4'
ff = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W*SC}x{H*SC}', '-r', '30', '-i', '-',
    '-f', 'lavfi', '-i', 'anullsrc=r=48000:cl=stereo', '-shortest', '-c:v', 'libx264', '-crf', '22', '-preset', 'veryfast', '-pix_fmt', 'yuv420p',
    '-profile:v', 'main', '-c:a', 'aac', '-movflags', '+faststart', tmp], stdin=subprocess.PIPE)
for f in range(300):
    i = int(f * 1000 / 30 / 100) % 100
    face = Image.open(f'/home/claude/cos/lightning/face_{i:03d}.png').resize((pw, ph), Image.LANCZOS)
    fr = base.copy(); fr[py0:py0 + ph, px0:px0 + pw] = np.asarray(face)
    ff.stdin.write(Image.fromarray(fr).resize((W * SC, H * SC), Image.NEAREST).tobytes())
ff.stdin.close(); ff.wait(); os.replace(tmp, '/home/claude/Lightning_Cape_preview.mp4'); print('ok')

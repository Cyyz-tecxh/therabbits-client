# Claws v3: export the 16 pulse frames + render an animated preview of what the client does.
import sys, os, math, json, copy, subprocess
import numpy as np
import gen, render
from PIL import Image, ImageDraw, ImageFont
gen.export('/tmp/claws_export')
N = 16
OUT = '/home/claude/cos/out/claws_frames'; os.makedirs(OUT, exist_ok=True)
texs = []
for f in range(N):
    gen.PHASE = f / N
    tex, glow, ts = gen.build_atlas(gen.MODELS['claws']['parts'])
    Image.fromarray(tex, 'RGBA').save(f'{OUT}/{f}.png'); Image.fromarray(glow, 'RGBA').save(f'{OUT}/{f}_glow.png')
    texs.append((tex, glow, ts))
base = copy.deepcopy(gen.MODELS['claws']['parts'])

def pose_parts(t, atk):
    """Mirror of CosmeticsLayer.animateClaws (degrees here)."""
    ps = copy.deepcopy(base)
    for p in ps:
        n = p['name']
        if n.startswith('blade') and n[5].isdigit():
            i = int(n[5]); seg = n[6:]
            if seg == '':
                p['rot'][0] += 3.5 * math.sin(t * 2.2 + i * 0.9) - 22 * atk
                p['pivot'][1] += 0.9 * atk
            elif seg == 'b':
                p['rot'][0] += 4.0 * math.sin(t * 2.2 + i * 0.9 + 0.8) - 14 * atk
            elif seg == 'c':
                p['rot'][0] += 6.0 * math.sin(t * 2.2 + i * 0.9 + 1.6) + 10 * atk
    return ps

FPS = 30; DUR = 4.0
font = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 30); small = ImageFont.truetype('/home/claude/fonts/ttf/exo2.ttf', 18)
fd = '/tmp/claws_anim'; os.makedirs(fd, exist_ok=True)
for k in range(int(FPS * DUR)):
    t = k / FPS
    # attack swing at 1.2s and 2.9s
    atk = max(math.exp(-((t - 1.3) / 0.12) ** 2), math.exp(-((t - 3.0) / 0.12) ** 2))
    m = gen.MODELS['claws']
    tex, glow, ts = texs[int(t * 1000 / 110) % N]
    m['parts'] = pose_parts(t, atk); m['tex'], m['glow'], m['texsize'] = tex, glow, ts
    for a, b in zip(m['parts'], base): a['uv'] = b['uv']
    arm = (-38 - 55 * atk, 0, 8)
    left = render.tile('claws', 640, 720, -30 + 10 * math.sin(t * 0.8), -10, 13, 320, 200, dict(right_arm=arm, left_arm=(-20, 0, -8)))
    right = render.tile('claws', 640, 720, -70, -6, 30, 470, -150, dict(right_arm=arm, left_arm=(-20, 0, -8)))
    fr = Image.new('RGB', (1280, 720)); fr.paste(left, (0, 0)); fr.paste(right, (640, 0))
    d = ImageDraw.Draw(fr); d.text((28, 22), 'Claws v3', font=font, fill=(235, 235, 240))
    d.text((30, 60), 'pulse flows down the blades · idle flex · snap on hit', font=small, fill=(110, 198, 255))
    fr.save(f'{fd}/{k:04d}.png')
subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-framerate', str(FPS), '-i', f'{fd}/%04d.png', '-c:v', 'libx264', '-crf', '16',
                '-preset', 'veryfast', '-pix_fmt', 'yuv420p', '-profile:v', 'main', '-movflags', '+faststart', '/home/claude/Claws_v3_preview.mp4'], check=True)

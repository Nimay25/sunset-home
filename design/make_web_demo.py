"""Builds docs/index.html (the web preview) from web-demo.tpl.html and the sky layers.
Run after seg_marm.py and gen_clouds.py:  python3 make_web_demo.py"""
import base64, io
import cv2, numpy as np
from PIL import Image

def uri(img, fmt='WEBP', q=86):
    b = io.BytesIO(); img.save(b, fmt, quality=q, method=6)
    return f'data:image/{fmt.lower()};base64,' + base64.b64encode(b.getvalue()).decode()

m = cv2.imread('marm_mountains.png', cv2.IMREAD_UNCHANGED)
m = cv2.resize(m, (1600, 900), interpolation=cv2.INTER_AREA)
mtn = uri(Image.fromarray(cv2.cvtColor(m, cv2.COLOR_BGRA2RGBA), 'RGBA'))
cld = uri(Image.fromarray(cv2.imread('clouds.png', 0), 'L'))
g = cv2.imread('glow.png', 0)
glw = uri(Image.fromarray(np.dstack([np.full_like(g, 255)] * 3 + [g]), 'RGBA'))
s = open('web-demo.tpl.html').read().replace('{{MTN}}', mtn).replace('{{CLD}}', cld).replace('{{GLW}}', glw)
open('../docs/index.html', 'w').write(s)
print(len(s) // 1024, 'KB')

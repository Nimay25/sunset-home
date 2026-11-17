"""Renders the sky exactly the way the app now does (rows + additive clouds + baked mountains),
to check it against render2.py. Writes fast-preview.jpg."""
import cv2, numpy as np
W, H = 1920, 1080
c = cv2.resize(cv2.imread('clouds.png', 0), (W, H), interpolation=cv2.INTER_CUBIC).astype(np.float32)[..., None] / 255
g = cv2.resize(cv2.imread('glow.png', 0), (W, H), interpolation=cv2.INTER_CUBIC).astype(np.float32)[..., None] / 255
def rgba(n):
    x = cv2.imread(n, cv2.IMREAD_UNCHANGED).astype(np.float32) / 255
    return x[..., :3] * x[..., 3:4], x[..., 3:4]          # premultiplied BGR, alpha
base, a = rgba('front_base.webp'); mult, _ = rgba('front_mult.webp')
def hx(s): s = s.lstrip('#'); return np.array([int(s[4:6], 16), int(s[2:4], 16), int(s[0:2], 16)], np.float32) / 255
def column(stops, pos):
    y = np.linspace(0, 1, H)
    return np.stack([np.interp(y, pos, [s[i] for s in stops]) for i in range(3)], 1)[:, None, :]
def render(p):
    s1, s2, s3, s4, hl, rim = [hx(v) for v in p]
    b = column([s1, s2, s3, s4, s4], [0, .16, .32, .46, 1])
    P = np.where(b < .5, 0, 2 * b - 1); Q = np.where(b < .5, 2 * b, 2 * (1 - b))
    f = 1 - np.interp(np.linspace(0, 1, H), [0, .2, .6, 1], [.25, 0, 0, .55])[:, None, None]
    g1, g2 = (.12 * b + .88 * P) * f, .88 * Q * f
    img = np.clip(g1 + c * g2, 0, 1)                                     # sky + clouds (ADD)
    ga = g * 190 / 255; img = img + ga * hl - img * ga * hl              # glow (SCREEN)
    yy, xx = np.mgrid[0:H, 0:W]; d = np.sqrt(((xx / W - .62) / .42) ** 2 + ((yy / H - .44) / .2) ** 2)
    sa = np.interp(d, [0, .45, 1], [.73, .33, 0])[..., None]; img = img * (1 - sa) + s4 * sa   # sun
    tint = column([rim, rim, s2, s2], [0, .3, .8, 1])
    img = np.clip(base + img * (1 - a) + mult * tint, 0, 1)             # mountains, tinted (ADD)
    return (img * 255).astype(np.uint8)
P = {'Ember': ['#14030a', '#6e0b1a', '#e5231b', '#ffb347', '#ff4a2e', '#ff5a3a'],
     'Aurora': ['#010f12', '#06463f', '#1ce783', '#e6ff8a', '#39ffa0', '#3df2a0']}
out = [cv2.putText(cv2.resize(render(p), (800, 450)), k, (12, 30), cv2.FONT_HERSHEY_SIMPLEX, .8, (255, 255, 255), 2) for k, p in P.items()]
cv2.imwrite('fast-preview.jpg', np.hstack(out))

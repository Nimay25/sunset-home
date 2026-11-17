import cv2, numpy as np
rng = np.random.default_rng(7)
W,H = 960,540
def octave(sx, sy):
    n = rng.random((sy, sx)).astype(np.float32)
    return cv2.resize(n, (W,H), interpolation=cv2.INTER_CUBIC)
# horizontally stretched fractal noise = streaky sunset clouds
f = sum(octave(int(6*2**i), int(3*2**i*1.0)) / (1.7**i) for i in range(6))
f = cv2.GaussianBlur(f, (0,0), 1.2)
f = (f - f.min()) / (f.max() - f.min())
y = np.linspace(0,1,H)[:,None]
# a dense band low in the sky, wisps up top, none at the very top edge
band = np.exp(-((y-0.36)/0.12)**2)*0.9 + np.exp(-((y-0.16)/0.07)**2)*0.45
cover = np.clip((f - 0.52) * 3.2, 0, 1) * band
cover = cv2.GaussianBlur(cover, (0,0), 2.0)
gray = np.clip(0.5 + cover*0.5 - (1-cover)*0.04, 0, 1)   # 0.5 = no change under overlay
cv2.imwrite('clouds.png', (gray*255).astype(np.uint8))
glow = np.clip(cover**1.3 * 1.15, 0, 1)
cv2.imwrite('glow.png', (glow*255).astype(np.uint8))
print('ok', cover.max())

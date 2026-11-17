"""Bakes the mountains into two layers the TV can recolor cheaply.

The app tints the peaks with each sky's color using an overlay blend at 40%. Overlay with a
fixed backdrop is linear in the tint color, so  result = base + mult * tint  per pixel, where
base and mult only depend on the photo. The fixed top/bottom shade is folded in too. On the
TV: draw base normally, then draw mult multiplied by the tint gradient with an additive blend.
Writes front_base.webp and front_mult.webp (1920x1080, alpha = mountain cutout)."""
import cv2, numpy as np
from PIL import Image
m = cv2.imread('marm_mountains.png', cv2.IMREAD_UNCHANGED).astype(np.float32) / 255
img, a = m[..., 2::-1].copy(), m[..., 3:4]          # RGB, alpha
h = img.shape[0]
K = 0.4
A = np.where(img < .5, 0, 2 * img - 1)
B = np.where(img < .5, 2 * img, 2 * (1 - img))
shade = 1 - np.interp(np.linspace(0, 1, h), [0, .2, .6, 1], [.25, 0, 0, .55])[:, None, None]
base = (img * (1 - K) + K * A) * shade
mult = K * B * shade
def save(rgb, name):
    rgba = np.dstack([np.clip(rgb, 0, 1), a]) * 255
    Image.fromarray(rgba.round().astype(np.uint8), 'RGBA').save(name, 'WEBP', quality=92, method=6)
save(base, 'front_base.webp'); save(mult, 'front_mult.webp')
print('ok')

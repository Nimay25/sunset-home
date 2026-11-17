"""Generates the cloud texture (clouds.png) and its glow mask (glow.png), 960x540, seamless
left-to-right so the clouds can drift forever. Gray 128 = no cloud (overlay leaves the sky as is)."""
import cv2, numpy as np
rng = np.random.default_rng(7)
W, H = 960, 540

# Noise made in frequency space wraps around by construction, so the texture tiles left to right
# with no seam and no repeat. Frequencies along x are damped harder than along y, which stretches
# the clouds into long horizontal streaks like clouds lit at sunset.
kx = np.fft.fftfreq(W)[None, :] * W
ky = np.fft.fftfreq(H)[:, None] * H
amp = ((kx * 2.2) ** 2 + ky ** 2 + 9.0) ** -0.82
f = np.real(np.fft.ifft2(np.fft.fft2(rng.standard_normal((H, W))) * amp)).astype(np.float32)
f = (f - f.min()) / (f.max() - f.min())
y = np.linspace(0, 1, H)[:, None]
band = np.exp(-((y - 0.36) / 0.12) ** 2) * 0.9 + np.exp(-((y - 0.16) / 0.07) ** 2) * 0.45
lo, hi = np.percentile(f, 48), np.percentile(f, 97)
cover = np.clip((f - lo) / (hi - lo), 0, 1) ** 1.4 * band
k = cv2.getGaussianKernel(13, 2.0)
pad = np.concatenate([cover[:, -12:], cover, cover[:, :12]], axis=1)  # wrap-aware blur
cover = cv2.sepFilter2D(pad, -1, k, k)[:, 12:-12]
gray = np.clip(0.5 + cover * 0.5 - (1 - cover) * 0.04, 0, 1)
cv2.imwrite('clouds.png', (gray * 255).astype(np.uint8))
cv2.imwrite('glow.png', (np.clip(cover**1.3 * 1.15, 0, 1) * 255).astype(np.uint8))
print('seam error', float(np.abs(gray[:, 0] - gray[:, -1]).mean()))

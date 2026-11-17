# Removing Amazon's ads and tracking

The TV in the screenshots (a Toshiba 4K Fire TV, model AFTHA004, Fire OS 7.7) was cleaned up with [dumbtv](https://github.com/humblemuzzu/dumbtv) before Sunset Home was installed. Only its package cleanup was used: no DNS filter, which would also block Fire OS updates, and no dumbtv launcher, since Sunset Home replaces it.

## What was run

```bash
git clone https://github.com/humblemuzzu/dumbtv && cd dumbtv
adb connect <tv-ip>:5555
export ADB_SERIAL=<tv-ip>:5555
./scripts/preflight.sh --dry-run   # read-only: what the full pass would do
./scripts/debloat.sh --dry-run     # read-only: the exact package list
./scripts/preflight.sh             # saves a settings snapshot to report/ for undo
./scripts/debloat.sh -y            # the cleanup, then one reboot
```

## What it changed

- **36 Amazon packages disabled** (not uninstalled): the advertising ID, autoplaying promos, sponsored rows, Live Shopping, `com.amazon.tv.acr` (it fingerprints what's on screen, HDMI included), about a dozen telemetry and logging services, update nags, demos and empty stub apps.
- **13 settings:** ad tracking limited, usage data sharing off, featured trailers off, heads-up notifications off, and window and transition animations at 0.5× (the TV feels faster).
- **Untouched:** every installed app, Prime Video, Alexa, HDMI inputs, Settings, Bluetooth, AirPlay, and Amazon's launcher package itself, which also hosts Settings.

Amazon's launcher still shows a full-screen sponsored banner after the cleanup, because it comes from the protected launcher. A replacement home screen like Sunset Home is what gets rid of it.

## Undo

```bash
cd dumbtv && ADB_SERIAL=<tv-ip>:5555 ./scripts/restore.sh
```

A factory reset also undoes everything.

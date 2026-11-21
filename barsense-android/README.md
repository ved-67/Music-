# BarSense Android prototype

This project is isolated under `barsense-android/` so it does not overwrite the existing Music- application.

## Build
Push changes or open Actions and run **Build BarSense APK**. The workflow uploads `BarSense-debug-apk`.

## Current functionality
- Android launcher screen
- User-granted overlay permission flow
- Floating panel with a manual test-tap button
- User-consented MediaProjection screen-capture service
- Accessibility gesture service
- Diagnostic sampling of red-dominant pixels

## Not implemented yet
This is not a finished automatic game player. It does not yet robustly locate the egg when its shape and colour change, calculate the safe gap/trajectory, or automatically jump. The overlay's test tap is manual. Those features require game-frame calibration and on-device testing before automatic play can be enabled.

All capture and accessibility capabilities require explicit Android user permission.

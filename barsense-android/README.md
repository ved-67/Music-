# BarSense Advanced

Android floating game-assist prototype using MediaProjection, on-device pixel analysis, and AccessibilityService gestures.

## Features
- Floating AUTO ON/OFF control with live status, frame count, confidence estimate, and jump count.
- User-approved screen capture through MediaProjection.
- Basic moving-object tracking using frame-to-frame pixel changes.
- Red-dominant obstacle column and vertical gap heuristics.
- Accessibility gesture dispatch to the configured right-side tap point (default: 89% screen width, 50% height).
- Auto mode starts OFF and is stopped when the overlay is closed.

## Setup
1. Install the debug APK.
2. Grant **Display over other apps** permission.
3. Enable **BarSense** under Android Accessibility settings.
4. Tap **Start screen capture** and approve Android's system prompt.
5. Open the target game and show the floating panel.
6. Wait for the status to show player tracking and a detected gap. Tap **AUTO: OFF • START** to arm it. Tap STOP immediately if it behaves incorrectly.

## Detection limitations
This is a heuristic CV implementation, not a trained egg detector. It tracks moving non-red components and red-dominant columns. Games with scrolling backgrounds, particle effects, multiple moving objects, unusual obstacle colours, or different movement physics may confuse it. It has not been calibrated against a supplied game recording or verified on the actual game. The confidence percentage is a rule-based score, not a statistical probability. Auto mode must be tested cautiously and may fail to play correctly.

The default tap coordinate is 89% of screen width and 50% of screen height. If the game's jump control is elsewhere, this build needs a coordinate setting/calibration UI before reliable use.

## Build
GitHub Actions workflow: `.github/workflows/build-barsense.yml`
Run `gradle assembleDebug` from this directory with JDK 17 and Android SDK platform 35 installed.

## Inspiration and attribution
The architecture was informed by public Android CV automation examples, including [android-cv-bot-template](https://github.com/steve1316/android-cv-bot-template) and [android-smart-clicker](https://github.com/Overlord96/android-smart-clicker). Their code was not copied into this project; their repositories did not expose an explicit repository-level license in the metadata checked during development. BarSense's vision heuristic and integration are original code for this repository.

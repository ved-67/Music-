# BarSense Android game assistant

A separate Android module inside the Music- repository.

## Implemented
- Floating overlay and manual test-tap action.
- User-consented Android MediaProjection screen capture.
- Non-ML frame differencing motion-candidate tracker.
- Red-dominant vertical obstacle-column detection.
- Largest vertical non-red run estimate.
- GitHub Actions debug APK workflow.

## Important limitations
The visual engine is an experimental heuristic, not a validated egg detector. Frame differencing can be confused by scrolling backgrounds, animation, effects, or scene transitions. It does not robustly identify the egg when appearance changes, and gap estimation needs calibration against actual gameplay. Automatic gameplay taps are deliberately not connected to this unvalidated signal. The overlay's tap is a manual test control.

## Build
Open Actions > Build BarSense APK. Download the BarSense-debug-apk artifact after a successful run. Android screen capture and accessibility require explicit user permission.

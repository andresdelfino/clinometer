# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

The constraints, toolchain and commands shared with the other Kotlin projects are in `../CLAUDE.md`, which Claude Code loads automatically from the parent directory. This file covers only what is specific to this project.

## Constraints

- Android 17 is the only supported version: `minSdk` and `targetSdk` are both 37. Call the platform API directly, with no `Build.VERSION` checks, `*Compat` wrappers (`ContextCompat`, `WindowCompat`), backports such as `enableEdgeToEdge`, versioned resource folders (`-v34`), or fallbacks for other releases.
- The UI is Material 3 (`androidx.compose.material3`, version from the Compose BOM), coloured by the dynamic (wallpaper) scheme with no brand fallback, since Android 17 always has it. The one exception is the fixed `success` green in `ui/theme/Theme.kt`, which has to read as "on target" whatever the wallpaper. Use M3 components and the default type scale rather than hand-rolled controls or bundled fonts. Icons are Material Symbols vector drawables in `res/drawable` (`ic_add`, `ic_remove`), not the `material-icons` libraries.

## Commands

```sh
./gradlew assembleRelease        # not minified (no R8); unsigned (no signing config)
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew testDebugUnitTest --tests 'com.andres.clinometer.TiltMathTest.onTargetUsesHysteresis'   # single test
```

There are no instrumented (`androidTest`) tests.

## Architecture

This is a single-activity Jetpack Compose app for setting an incline, such as a weight bench's back pad. The phone lies screen-up on the pad and the app shows the angle against a target. The activity is portrait-locked, the screen stays on, and a tone plays each time the reading enters the target band.

The app is edge-to-edge because Android 17 enforces it, not because of any call. The window theme (`res/values/themes.xml`) sets the system-bar icons to suit the system's brightness (`?android:attr/isLightTheme`), which the Compose theme follows too, so no code changes them.

Data flows one way:

1. **`TiltSensor`** turns the raw `TYPE_ACCELEROMETER` sensor into a `callbackFlow`, smoothed by a time-constant exponential moving average so smoothing is the same at any sample rate. It uses the raw accelerometer on purpose, not `TYPE_GRAVITY`. Each `GravitySample` carries a timestamp so a perfectly still phone still emits distinct values instead of being deduplicated by `StateFlow`.
2. **`ClinometerViewModel`** (an `AndroidViewModel`) combines gravity, `Settings` and the calibrating flag into `UiState`. Details:
   - It uses `scan`, not `map`, because the on-target check has hysteresis and depends on the previous `onTarget`.
   - Both flows use `SharingStarted.WhileSubscribed(0, 0)`, so the sensor unregisters as soon as the UI stops collecting, and a stale reading is never replayed.
   - `Settings` are written to SharedPreferences on every `updateSettings` call: target, tolerance and sound to `"clinometer"`, which is backed up, and the calibration reference to `"calibration"`, which isn't, because it describes this phone (`res/xml/data_extraction_rules.xml`).
   - Calibration results go through a `Channel` and appear as snackbars.
3. **`ui/ClinometerScreen`**: `ClinometerScreen` is the stateful wrapper that owns the ViewModel, the beeper and keep-screen-on. `ClinometerContent` is stateless and is what the `@Preview`s render. `AngleGauge` is a hand-drawn Canvas protractor (0–90°, side view of the bench).

**Tilt model (`TiltMath.kt`):** the incline is the angle between the current gravity vector and a reference gravity vector stored in the phone's own frame. The reference is captured by calibrating on a level surface; the default is `Vec3.SCREEN_UP`, and "calibrated" means `reference != SCREEN_UP`. Because both vectors are in the phone's frame, the reading doesn't change when the phone is rotated on the pad, and a fixed offset such as a camera bump or case cancels out.

Calibration waits 1 s for the tap to settle, then averages samples for 1.5 s. It rejects the result if any sample deviates more than 0.5° from the mean (the phone moved) or if the mean is more than 20° from screen-up (the phone isn't flat).

`TiltMath`/`Vec3` are pure Kotlin with no Android dependencies, and they are the only unit-tested code. Keep new geometry or decision logic there so it stays testable on the JVM.

# Development

Use JDK 21 or later and an Android SDK with platform 37. Set `ANDROID_HOME` to the SDK
directory (or configure `sdk.dir` in your local `local.properties`).

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:lintDebug
```

APK assembly has a pre-existing resource merge failure:
the Netty native QUIC jars contain duplicate `META-INF/license/LICENSE.webbit.txt`
files. Run `./gradlew :app:testDebugUnitTest` independently to validate the JVM tests.
Lint dependency resolution succeeds, but the current source has two existing
`LocalContextGetResourceValueCall` errors in `MainActivity.kt` and `Messages.kt`.

## Dependency alignment

Declare external library and plugin coordinates and versions in
`gradle/libs.versions.toml`, and reference their `libs` aliases from build scripts.
Local project dependencies remain project references.

AGP aligns dependencies shared by the app and its Android tests to the app's runtime
versions. The app declares an `implementation` dependency constraint for
`androidx.concurrent:concurrent-futures:1.2.0`, matching the requirement of AndroidX
Test JUnit 1.3.0. This raises the older transitive requirement from ProfileInstaller
without forcing an exact version or removing ProfileInstaller. Keep the constraint's
version in `gradle/libs.versions.toml`; revisit it when upgrading AndroidX Test or when
the app's other dependencies already require a compatible version.

## HID keyboard

`HidKeyboardHost` owns target layout, calibration selection, and a sequential
send queue with observable task status, key progress, failure reasons, and cancellation.
The HID screen owns a saveable `TextFieldValue` draft and updates it synchronously
in `onValueChange`; submission passes an immutable text snapshot to the Host.
Background task updates never replace the draft, cursor, or IME composition. The activity creates and closes it, observes state with lifecycle-aware
Compose collection, and consumes its completion/error effects while started. The application
injects a serial background coroutine dispatcher; host tests inject a test dispatcher.

Text is fully mapped before enqueueing, so unsupported characters cannot produce a
partial send. Each queued message captures its selected layout. The connection handle is captured
synchronously at submission, before dispatching queue work. Handle identity and
validity are checked before and after every key; reconnecting to the same device
creates a new handle. Finished task history is bounded to ten entries. `KeyboardLayout`
maps characters directly to `HidKey(usage, modifier)` using physical US key positions.
There are no string interceptors. `sendText` on the Host is the convenience entry
point for both the HID screen and shared URLs.

All output, including calibration and modifier combinations, calls
a bound `HidKeyboardSession`. The controller publishes connection handles and
invalidates them on disconnect, service loss, unregistration, Bluetooth shutdown,
permission loss, and activity destruction. `RawHidKeyboard` serializes press/release pairs
and attempts a release even on failure or cancellation. Bluetooth calls run on the
IO dispatcher; each session captures one Bluetooth device and profile on the main
thread. Validity is checked again after acquiring the keyboard mutex; key release
always uses the original target. Keyboard report ID 2
has exactly two bytes: modifier mask and one key usage, as defined in
`app/src/main/assets/hid`. Do not replace this with an eight-byte boot keyboard report.
Shortcuts can combine modifiers with one key; multiple simultaneous non-modifier keys
would require a descriptor change.

Mapping references: [USB HID Usage Tables](https://www.usb.org/hid) and the
[XKB US layout definitions](https://github.com/xkbcommon/libxkbcommon/blob/master/test/data/symbols/us).

The JVM tests cover ASCII/layout translation, unsupported input, queued-message
ordering, failure recovery, layout-independent calibration, report bytes, and
cancellation cleanup, session replacement, queue cancellation, and task progress.
Robolectric Compose tests run locally with Android resources and verify immediate
draft edits across task updates/reconnection, plus visible progress and cancellation
controls after disconnecting. They run as part of `:app:testDebugUnitTest` and do not
require an emulator. Real-device HID end-to-end testing is currently deferred.
When resumed, it requires a Bluetooth HID-capable Android device meeting the minimum
version in [README.md](README.md) and a target computer. The validation checklist is:

- Verify mixed-case ASCII and punctuation with each matching target layout.
- Verify unsupported text produces no output and a subsequent valid send succeeds.
- In macOS Keyboard Setup Assistant, verify ANSI and ISO recognition with each text
  layout selected and with a Bluetooth name that does not contain `Mac`.
- Disconnect during a send and verify a new send works after reconnecting.

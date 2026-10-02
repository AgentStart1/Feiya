# Development

Use JDK 21 or later and an Android SDK with platform 37. Set `ANDROID_HOME` to the SDK
directory (or configure `sdk.dir` in your local `local.properties`).

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:assembleAlpha :app:assembleRelease
./gradlew :app:lintDebug
```

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

## APK resource packaging

Netty native QUIC platform jars contain identical third-party license files under
`META-INF/license/` and identical GraalVM configuration under
`META-INF/native-image/io.netty/netty-codec-native-quic/`. The app uses scoped
`packaging.resources.pickFirsts` rules to retain one copy of these resources in the
APK. Keep the license notices; do not exclude all `META-INF` resources to resolve
merge conflicts. Recheck the duplicate contents when upgrading Netty.

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

## HTTP downloads and port changes

`respondUri` reads optional provider metadata on the IO dispatcher. Unknown or
negative sizes remain unknown; they must not become `Content-Length: 0`. The response
opens a fresh input stream for each body/range and closes it on completion or
cancellation. HEAD requests return metadata without opening a stream. Providers
need not supply DocumentsContract MIME/last-modified columns or seekable descriptors.

`PortHandoff` serializes port changes through the service event collector. `AppServer`
uses an injected, application-owned serial background dispatcher; its coroutine scope
is owned by the service lifecycle. Blocking server start/stop and file IO run on IO
workers. A new
server and its self WebSocket must start before existing ports become redirects.
Each retained listener redirects directly to the latest active port with HTTP 307,
preserving host, path, query, and request method; redirects use `Cache-Control: no-store`.
Selecting an earlier port reactivates its existing server. Stop/restart/service
shutdown closes all retained listeners and clients. Listeners are retained only
for the current service lifetime; they continue to occupy their ports until stopped.
Each listener retains its server/client so it can be reactivated without rebinding.

Port-switch failures preserve the active `ServerState.Started` instance, keeping its
self WebSocket, message cache, and refresh events usable. A separate failure event
updates the foreground notification with the requested and still-active ports. With
no active listener, startup failure remains `ServerState.Error`.

Service commands use a service-owned, non-conflating channel closed on destruction, consumed by `collectServerEvents`
alongside port changes. Each Stop/Restart is processed once, even for consecutive
identical commands. A port change never replays a previous command. Regression tests
exercise Stop → change port → Stop and real WebSocket messaging/cache/refresh after
a failed port switch.

Index/chat pages subscribe to port-change SSE events and replace their location,
preserving the path, query, and fragment. The shared handler is `port-change.js`.

Local tests cover provider metadata, full/range/HEAD responses, cancellation cleanup,
port-switch failures, switching back, IPv6 redirects, and real loopback HTTP listener
shutdown. These are JVM/server integration tests, not Android device or Windows
browser end-to-end tests. Recheck downloads in a Windows browser with the provider
and file that originally reproduced issue #6 before claiming platform coverage.

## Boot startup

`BootSettingsHost` owns loading/saving/error state on the application-owned serial
settings dispatcher. The activity closes the Host and observes it with lifecycle-aware
collection. `DataStoreBootSettings` stores `start_on_boot` in the existing settings
DataStore; an absent key means disabled. Failed writes preserve the last saved UI value.

The non-exported `BootReceiver` handles only `BOOT_COMPLETED`, after credential storage
is available. It uses `goAsync` with an eight-second timeout and always finishes the
pending broadcast. Enabled startup uses the existing `remoteMessaging` foreground
service; Android 17 local-network permission must already be granted. It does not
launch a permission dialog or activity. Denied startup is logged without retrying.
The application initializes the shared-list path, and the service reloads shared
files before starting its listener. Foreground promotion happens immediately, including
when notification permission is denied.

Local tests cover preference persistence, Host errors/cancellation, switch rendering,
boot gating, timeout and denied-start cleanup, manifest receiver delivery, and foreground
promotion without notification permission. Actual reboot and manufacturer autostart
restrictions require device testing and remain unverified in this environment.

## Minified builds

PR CI assembles both Alpha and Release without signing secrets, in addition to tests.
Debug does not run R8 and cannot detect missing-class failures in these variants.
Local builds without the release signing environment variables produce unsigned APKs.

Netty 4.2.17 references desktop-only JFR classes and LDAP name parsers. The narrow
`-dontwarn` rules in `app/proguard-rules.pro` cover only the reported classes:
Netty detects unavailable JFR at runtime, and its LDAP references belong to TLS
certificate-error diagnostics unused by Feiya's plain HTTP Netty listeners. These
rules do not add desktop APIs to Android or disable certificate verification. Recheck
them when upgrading Netty or adding TLS listeners; do not suppress all R8 warnings.

## Shared URI integrity and device-test fixtures

Shared URI updates are serialized on IO workers, ignore repeat additions, and repair
legacy duplicate entries when loading. The combined file list also deduplicates by
URI, which is the Compose list key. Instrumented tests create unique temporary file
fixtures, wait for asynchronous additions, and remove only their own entries/files
in `@After`; they must not append permanent `file:///test.zip` entries. SSE assertions
have a timeout and propagate failures instead of swallowing them.

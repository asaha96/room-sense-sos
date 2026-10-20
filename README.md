# Room Sense SOS

Android app for Georgia Tech CS 8803 (Mobile Computing and IoT). A person in trouble holds an SOS button, reads a short incident code to 911, and the phone tells a nearby team which room they are in.

The phone learns rooms by Wi-Fi fingerprinting. During an incident it keeps estimating the room, opens a local-only hotspot, advertises the incident over BLE, and serves an offline web page with the floor map. There is no iOS target.

## Build and run locally

### Prerequisites

- **JDK 17 or newer.** The app compiles with Java 17 language level. JDK 21 works. Check with `java -version`.
- **Android SDK**, from [Android Studio](https://developer.android.com/studio) or the command-line tools. You need:
  - Android SDK Platform 34 (`platforms;android-34`)
  - Build-tools 34.0.0 (`build-tools;34.0.0`)
  - Platform-tools (`adb` is in there)
- `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) pointing at the SDK. Android Studio’s default is `~/Android/Sdk` on Linux, `~/Library/Android/sdk` on macOS.

The project is minSdk 26, targetSdk 34, compileSdk 34. Kotlin, Jetpack Compose, Room, and a small embedded HTTP server (no NanoHTTPD dependency). Unit tests cover kNN, the incident-code alphabet, the BLE payload, and the Wi-Fi QR string.

`local.properties` is gitignored. Do not commit it, the SDK, `.gradle/`, or `app/build/`.

### Build

```bash
export ANDROID_HOME="$HOME/Android/Sdk"   # or wherever the SDK lives
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew testDebugUnitTest assembleDebug
```

`assembleDebug` is enough for an installable APK. The test task is the same suite CI-style local check. Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Install on a physical Android phone

Turn on Developer options and USB debugging. Plug the phone in (or use wireless debugging) and accept the RSA prompt.

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` replaces an existing debug install and keeps its data. Open **Room Sense SOS**, grant location, nearby Wi-Fi, notifications, and Bluetooth, and turn system Location on. Real Wi-Fi scans, the local-only hotspot, and BLE advertise/scan all need this phone. Survey a building only on hardware.

### Emulator

An emulator can install the APK and walk the UI, including demo mode and the in-app page preview. These radios are not available there, so those features will not work:

- **Wi-Fi scans.** `WifiManager.startScan()` / `getScanResults()` do not see real access points. Room estimates from a live scan stay empty.
- **Hotspot.** `startLocalOnlyHotspot` fails. There is no system SSID or passphrase, and a second device cannot join the phone.
- **BLE.** Advertising the incident and scanning for nearby incidents do not run. The Listen screen stays empty.

Use demo mode for the rest of the flow (below). **Preview the page on this phone** still loads `http://127.0.0.1:<port>/` because the HTTP server does not need the hotspot.

### Demo mode

Settings → **Demo mode** replaces the radio with synthetic scans, so you never call `startScan`. **Load demo wing** replaces the map with four rooms (Atrium, Studio, Office, Corridor), each with four noisy fingerprints, and turns demo mode on.

**Standing in** chooses which centroid the next fake scan is drawn from. Change it during an SOS and the responder page follows within a few seconds. If the hotspot fails while demo mode is on (the usual emulator result), the SOS screen shows a simulated `ROOMSENSE-SOS` / `sense-sos` network and still serves the page. The class demo script below has a short emulator path.

## Architecture

`AppGraph` is the composition root. The SOS loop depends on `RoomClassifier`, not on kNN, so a later model can replace `KnnRoomClassifier` in one place.

| Layer | What lives there |
| --- | --- |
| `domain` | Rooms, fingerprints, `RoomClassifier`, `KnnRoomClassifier`, incident codes, Wi-Fi QR text, BLE codec, demo radio map. No Android imports. |
| `data` | Room database, settings, `WifiFingerprintSource` (real scans or demo), hotspot, BLE advertise/scan, HTTP server. |
| `service` | `SosForegroundService` keeps the incident alive and publishes `SosSession`. |
| `ui` | Compose: home, survey, floor editor, SOS, responder, in-app page preview. |

Fingerprints are rows in Room (`rooms` and `samples`). Each sample stores a JSON map of BSSID to RSSI. The floor plan image, demo flag, and fixed-SSID attempt are preferences.

The classifier builds one vector per sample. The axes are the union of BSSIDs in the live scan and the library. A missing access point is filled at **-100 dBm**. Distance is Euclidean. The k nearest samples vote with inverse-distance weights (an exact match gets a huge weight). Confidence is the winner's share of that weight, from 0 to 1. An empty scan or an empty library returns "unknown" instead of guessing.

## Permissions

Runtime, requested from the home screen before a real SOS:

- Fine and coarse location. Android only returns Wi-Fi scan results when location permission is granted **and** system Location is on. Room estimates are derived from those scans, so `NEARBY_WIFI_DEVICES` is declared **without** `neverForLocation`.
- Nearby Wi-Fi devices (API 33+).
- Notifications (API 33+), so the foreground SOS notification can show.
- Bluetooth scan, connect, and advertise (API 31+). Scan is marked `neverForLocation` because the beacon is not used as a location fix.

Install-time: Wi-Fi state, change Wi-Fi state, internet, network state, foreground service types (`location`, `connectedDevice`, `specialUse`), wake lock, and legacy Bluetooth for API 26–30.

Wi-Fi, BLE, and GPS are not required features, so an emulator without them can still install the app and run demo mode.

## Hotspot SSID limitation

`WifiManager.startLocalOnlyHotspot(callback, handler)` is the API a normal app is allowed to call. From Android 8 onward the **system picks the SSID and passphrase**. They look like `AndroidShare_1234` plus a random password. The SOS screen shows whatever the reservation actually returned, in large type, with a `WIFI:` QR code a responder can scan.

A fixed name such as `ROOMSENSE-SOS` needs `SoftApConfiguration`. On API 34 that builder is not even in the public SDK stub; the overload `startLocalOnlyHotspot(SoftApConfiguration, Executor, callback)` is a privileged API (`NETWORK_SETTINGS`, `NETWORK_SETUP_WIZARD`, or similar). A normal install cannot hold those.

What the app does:

1. By default it uses the public API and displays the system SSID and password.
2. Settings has **Try fixed SSID ROOMSENSE-SOS**. That path builds a configuration by reflection and invokes the hidden overload. If the method is missing or throws (it will, on a normal phone), the app drops to the public API and says so on the SOS screen.
3. Demo mode, or any hotspot failure while demo mode is on, shows `ROOMSENSE-SOS` / `sense-sos` as a simulated network so the rest of the flow still runs. The HTTP server is real either way.

The responder page does not repeat the password. Joining the hotspot is the access control.

## Wi-Fi scan throttling

Since Android 9, a foreground app may call `WifiManager.startScan()` about **4 times every 2 minutes**. When the quota is spent, `startScan()` returns false and `getScanResults()` still returns the last cached batch.

Room Sense treats that as a cached reading:

- The survey screen says the sample is cached and will not store the same cached vector twice for that room.
- **Record a set** on a real phone waits 32 seconds between attempts so a 4-sample pass stays inside the quota. **Save one** takes a single reading.
- During SOS the foreground service re-estimates every **32 seconds** (every 4 seconds in demo mode, which never calls `startScan`). If a live scan is throttled, the latest cache is classified and the screen says so.

Do the surveying before the SOS if you can. An incident and a survey share the same quota.

## Survey a building

1. Grant location (and the other runtime permissions). Turn on system Location and Wi-Fi.
2. Open **Map**. Add a room for each space, drag it into place, and drag the corner to resize. Optionally import a floor-plan image; rooms are rectangles in normalized coordinates over that image. The editor and the responder page both use a 4:3 frame, so the highlight lines up.
3. Open **Survey**. Stand in a room, select it, and save several fingerprints (four is a good start). Each sample is the set of BSSIDs and RSSI values heard there.
4. Repeat for 3 or 4 rooms. More separation in space, and more samples, makes kNN less twitchy.
5. On the home screen, tap **Locate** in a surveyed room and check the confidence.

Rooms created in Survey also get a default rectangle, so the map is never empty after a label exists.

## Demo mode

Settings → **Demo mode** replaces the radio with synthetic scans. **Load demo wing** replaces the current map with four rooms (Atrium, Studio, Office, Corridor), each with four noisy fingerprints, and turns demo mode on.

While demo mode is on, **Standing in** chooses which centroid the next fake scan is drawn from. Change it during an SOS and the responder page follows within a few seconds. Use this on an emulator, or in a classroom where you cannot walk a building.

Demo mode does not skip the hotspot attempt. If `startLocalOnlyHotspot` fails (typical on an emulator), the SOS screen falls back to the simulated SSID and still serves the page on the phone. **Preview the page on this phone** opens `http://127.0.0.1:<port>/`.

## Class demo script

Real phones, about ten minutes:

1. Install `app-debug.apk` on phone A. Grant the permissions. Leave demo mode off.
2. Walk three or four rooms. In each one, add it on the map (or accept the default rectangle and nudge it) and record a set of fingerprints. If Android throttles, wait out the countdown rather than hammering Scan.
3. Walk into one of those rooms. Tap **Locate**. Read the room and confidence out loud.
4. Hold the SOS button until the ring closes.
5. Read the six-character code as if you were on the phone with 911. The alphabet has no 0/O or 1/I/L.
6. On phone B or a laptop, join the Wi-Fi network printed under the code. The password is next to it, or scan the QR code.
7. Open one of the responder URLs on the SOS screen. You should see the same code, the floor with that room highlighted, the confidence, and a last-updated time. The page polls `/api/status` every two seconds. `GET /api/status` is the JSON form.
8. Optional: on phone B open **Listen**, start listening, and confirm the BLE card shows the code, room, and hotspot name. Then join that network for the map. Legacy advertisements put the SSID in the scan response because a 31-byte packet cannot hold a long AndroidShare name plus the room.

Emulator or a single phone:

1. Settings → Load demo wing.
2. Set **Standing in** to Studio. Tap **Locate**. It should come back Studio.
3. Hold SOS. Note the simulated hotspot if the emulator cannot start a real one.
4. Tap **Preview the page on this phone**. The map should highlight Studio.
5. Switch **Standing in** to Office and wait a few seconds. The preview should move.

## Responder page

Served by the phone on port 8088, or the next free port. Bound to all interfaces. HTML, CSS, and JS are one asset file. No CDN, no webfonts. The floor image, if you imported one, is `GET /floorplan`.

Common local-only hotspot gateways are `192.168.43.1` and `192.168.49.1`. The screen lists the addresses the phone can actually see, plus those two if they are missing, because the hotspot interface sometimes shows up late.

## BLE

Prototype identifiers, not registered with the Bluetooth SIG:

- Manufacturer id `0x5243`
- 16-bit service UUID `0x5243` (`00005243-0000-1000-8000-00805f9b34fb`)

Payload: magic `RS`, a version byte, then length-prefixed UTF-8 fields. Version 1 is code + SSID + room (used when it fits in the legacy budget). Otherwise version 2 (code + room) is manufacturer data and version 3 (SSID) is service data in the scan response. Room names are truncated to fit.

## What a real device still has to prove

This repo builds and the JVM unit tests run without hardware. These paths need a phone:

- `startScan` / `getScanResults`, including the 4-scans-per-2-minutes behavior on that OEM
- `startLocalOnlyHotspot` actually coming up, and the SSID/password matching the QR code
- The reflection attempt for `ROOMSENSE-SOS` failing closed on a non-privileged app
- A second device joining the hotspot and loading the page (the gateway IP varies)
- BLE advertise on the caller and scan on the responder, including scan responses
- Location services toggled off (scans come back empty) and the notification while the screen is locked

The foreground service is sticky and stores the incident code. Force-stop still ends it. Stopping SOS from the screen or the notification closes the hotspot, the advertiser, and the server.

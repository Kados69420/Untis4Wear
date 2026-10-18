# Untis4Wear

WebUntis timetable for **Wear OS 6 / Samsung One UI 8 Watch** (and Wear OS 3+ in general).
Native Kotlin port of Untis4Garmin (Garmin Connect IQ / Monkey C), which cannot run on
Wear OS. Same screen, same features, same settings. No third-party dependencies, only Android platform APIs.

## Build & install

Requirements: Android Studio (or JDK 17+) with the **Android 16 / API 36 SDK platform** installed.

1. Open this folder in Android Studio, let Gradle sync (or run `./gradlew assembleDebug`).
2. On the watch: *Settings → About watch → Software* → tap *Software version* 7 times → *Developer options* →
   enable *ADB debugging* and *Wireless debugging*.
3. Pair and connect from the computer (addresses/ports are shown on the watch):
   ```
   adb pair <ip>:<pairing-port>
   adb connect <ip>:<port>
   ./gradlew installDebug
   ```
   (or pick the watch as run target in Android Studio).

## Using it

* **Swipe up / down** or **rotate the crown/bezel**: next / previous lesson (past the last lesson it continues
  with the next day, before the first one with the previous day).
* **Tap**: menu (next/previous lesson, next/previous day, today, refresh, settings).
* On first start the settings screen opens. Enter username, password, the WebUntis host
  (e.g. `mese.webuntis.com`) and the school name exactly as in your WebUntis URL
  (`https://<host>/WebUntis/?school=<school>`). Colors, date format and delimiter are configurable as before.
* Yellow dot next to the time = showing the saved copy of today's timetable (loading / offline),
  red dot = refreshing failed.

## Differences to the Garmin version

* Settings are edited on the watch (Garmin used the phone's Connect IQ app).
* Dates use `java.time` (the hand-written month/leap-year code is gone).
* Text color on colored panels is chosen automatically for contrast (black or white).
* Logout is best effort: a failing logout no longer turns a successfully loaded timetable into an error.
* Outdated responses are ignored when you navigate quickly.
* English and German UI texts.

## Project layout

| File | Purpose |
| --- | --- |
| `TimetableParser.kt`, `UntisApi.kt` | WebUntis JSON-RPC client (authenticate → getTimetable → logout) |
| `TimetableController.kt` | navigation / loading / cache logic (port of `Untis4GarminActions` + view state) |
| `TimetableView.kt` | the screen drawing (port of `Untis4GarminView.onUpdate`) |
| `MainActivity.kt`, `SettingsActivity.kt` | crown/swipe/tap input, menu, settings form |
| `SettingsStore.kt`, `TimetableCodec.kt` | SharedPreferences settings and offline cache |

The credentials are stored in the app's private storage (backups are disabled in the manifest).

## License

GPL-3.0, like the original project (see `LICENSE`).

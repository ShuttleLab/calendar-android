<div align="center">
  <h1>Calendar Shuttle</h1>
  <p>
    <strong>Gregorian + lunar calendar for Android</strong>
  </p>
  <p>
    Chinese statutory holidays and make-up workdays at a glance — green for a day off,
    red for a day you work — with lunar dates, solar terms and festivals.
  </p>
  <p>
    🌐 <a href="https://calendar.shuttlelab.org">calendar.shuttlelab.org</a>
    &nbsp;·&nbsp;
    📦 <a href="../../releases/latest">Download the latest APK</a>
  </p>
</div>

<div align="center">

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin)
![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?style=flat-square&logo=jetpackcompose)

</div>

## About

Calendar Shuttle is the Android client for
[calendar.shuttlelab.org](https://calendar.shuttlelab.org). It shows the Gregorian and
lunar calendars side by side and marks China's statutory holidays and make-up workdays,
so a glance answers the question people actually open a calendar for: am I working that
day?

No account, no sign-up, nothing to configure.

## Features

- **Gregorian + lunar** — every cell carries its lunar date; the first of a lunar month
  shows the month name, and a solar term shows the term instead.
- **Holidays and make-up workdays** — green marks a statutory day off, red a make-up
  workday, yellow an ordinary weekend.
- **Festivals and solar terms** — lunar festivals such as Spring Festival and
  Mid-Autumn, Gregorian ones such as New Year's Day and National Day, and all
  twenty-four solar terms.
- **Day detail** — tap a day for its weekday, full lunar date, solar term, festivals,
  holiday status and observances.
- **Works offline** — the holiday schedule is cached, and a snapshot ships with the app
  so a fresh install marks days correctly with no network at all.
- **English / 中文** — switchable in the app, following your system language by default.
- **Dark mode and Material You** — follow the system or choose manually. With wallpaper
  colours on, a day off is still green and a make-up workday still red: those are
  meanings, not decoration.

## Privacy

No account, no analytics, no advertising identifiers, and nothing about you leaves the
device. The app makes exactly one kind of network request: fetching the public holiday
schedule. Your settings stay on the device and are excluded from cloud backup and
device-to-device transfer.

`INTERNET` is the only substantive permission. There are no notifications, no alarms and
no background work, and the app reads no calendars, no storage and no location.

## Install

Download **`CalendarShuttle.apk`** from the [latest release](../../releases/latest), open
it on your phone and allow installation from unknown sources. Android 8.0 or newer.

## Tech stack

- **Language:** Kotlin
- **Platform:** Android SDK (minSdk 26 / targetSdk 36), edge-to-edge
- **UI:** Jetpack Compose, Material 3
- **Dependencies:** close to none beyond Compose and AndroidX — no Retrofit, Room,
  DataStore, Hilt or Navigation
- **CI/CD:** GitHub Actions — every push builds a debug APK, every tag publishes a
  signed release

## Build

```sh
./gradlew testDebugUnitTest   # JVM only, no device needed
./gradlew assembleDebug       # needs JDK 17 + the Android SDK
```

To publish a release, push a tag of the form `v1.2.3`; CI derives the versionName and
versionCode from it and publishes a signed build. The repository secrets
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` must be set.

Development conventions live in [AGENTS.md](AGENTS.md).

## Licence

[AGPL-3.0-only](LICENSE) — © ShuttleLab. You may use, study, modify and redistribute this
app under its terms; derivative works stay under the same licence. For commercial
licensing without copyleft obligations, contact support@shuttlelab.org.

<div align="center">
  Built by <a href="https://github.com/ShuttleLab">ShuttleLab</a>
</div>

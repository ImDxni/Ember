<p align="center">
  <img src="docs/images/ember-flame.svg" width="96" alt="">
</p>

# Ember

**Ember is an open source screen mirroring receiver for TVs.** Install it on
your TV stick and mirror your phone's screen to the big screen.

The first version is **compatible with AirPlay screen mirroring** from iPhone
and iPad and **runs on Amazon Fire TV** (Fire OS 7 and 8). The AirPlay
protocol support comes from [UxPlay](https://github.com/FDH2/UxPlay).

> **Status: early development.** The project is being built in stages (see
> [Roadmap](#roadmap)). Mirroring does not work yet.

## Supported devices

| Receiver | Status |
|---|---|
| Amazon Fire TV with Fire OS 7 (Android 9) or Fire OS 8 (Android 11) | primary target |
| Other Android TV / Google TV devices, Android 7.1+ (API 25) | expected to work, not tested yet |

| Sender | Status |
|---|---|
| iPhone / iPad (screen mirroring) | in development |
| Android phones (Ember sender app) | planned |

Both 32-bit (`armeabi-v7a`) and 64-bit (`arm64-v8a`) devices are supported.
Many Fire TV sticks run a 32-bit system even on 64-bit chips: the
*universal* APK covers both.

## Install on Fire TV (ADB over Wi-Fi)

1. On the Fire TV: **Settings → My Fire TV → About**, click the device name 7
   times to enable developer options. Then **Settings → My Fire TV →
   Developer options → ADB debugging: ON**.
2. Find the TV's IP address: **Settings → My Fire TV → About → Network**.
3. Download `Ember-<version>-universal.apk` from the
   [Releases](https://github.com/ImDxni/Ember/releases) page.
4. On your computer, with [platform-tools](https://developer.android.com/tools/releases/platform-tools)
   installed:

   ```sh
   adb connect <TV-IP>:5555          # accept the prompt shown on the TV
   adb install -r Ember-<version>-universal.apk
   adb shell am start -n io.github.imdxni.ember/io.github.imdxni.ember.app.MainActivity
   ```

Ember then appears under **Your Apps & Channels**.

## Usage

Open Ember on the TV: it shows the receiver name and its IP address. On the
iPhone, open **Control Center → Screen Mirroring** and pick that name. The
phone and the TV must be on the same network.

## Build from source

Requirements:

- JDK 17 or 21
- Android SDK with platform 36, NDK `29.0.14206865`, CMake `3.31.6`
  (Gradle installs missing SDK packages automatically once licenses are accepted)
- For the native dependencies (OpenSSL, libplist): either the prebuilt bundle
  (downloaded automatically when published) or Linux / WSL with `make`, `perl`,
  `curl`, `bzip2` and a Linux NDK.

```sh
git clone https://github.com/ImDxni/Ember.git
cd Ember

# 1. Native dependencies (only once, or when third_party/ changes)
ANDROID_NDK_ROOT=/path/to/linux/ndk bash third_party/build-native-deps.sh
#    On Windows, run the same command inside WSL.

# 2. APKs
./gradlew :app-tv:assembleDebug        # Windows: gradlew.bat :app-tv:assembleDebug
```

APKs are written to `app-tv/build/outputs/apk/<debug|release>/`: one per ABI
plus `app-tv-universal-*.apk`.

Useful properties (`gradle.properties` or `-P` on the command line):

| Property | Meaning |
|---|---|
| `ember.applicationId` | Installed package name; change it to install a fork side by side |
| `ember.basePackage` | Kotlin base package; change it with `scripts/rename-package.sh` |
| `ember.versionName`, `ember.versionCode` | App version (CI sets them from the git tag) |

Release APKs are signed with the debug key unless `EMBER_KEYSTORE_PATH`,
`EMBER_KEYSTORE_PASSWORD`, `EMBER_KEY_ALIAS` and `EMBER_KEY_PASSWORD` are set.

### Logs

```sh
adb logcat -v time | grep "Ember/"
```

All Ember log tags (Kotlin and native) start with `Ember/`.

## Continuous integration

- Every push builds and tests the project and uploads the APKs as a workflow artifact.
- Pushing a tag `vX.Y.Z` also creates a GitHub Release with the APKs and their SHA-256 sums.
- Secrets `EMBER_KEYSTORE_BASE64`, `EMBER_KEYSTORE_PASSWORD`, `EMBER_KEY_ALIAS`,
  `EMBER_KEY_PASSWORD` enable release signing.

## Known limitations

- One sender at a time.
- Screen mirroring only. Video casting from apps by URL (HLS) and audio-only
  streaming with ALAC are not supported in the first version.
- No PIN or password protection yet.
- The receiver runs while the Ember app is open.

## Roadmap

1. ~~Analysis and plan~~
2. ~~Multi-module project, CI, native build for both ABIs~~
3. Discovery: the iPhone lists "Ember" under Screen Mirroring
4. Connection and pairing
5. Video
6. Audio and A/V sync
7. Stability (reconnects, rotation, resolution changes, memory)

Later: Android TV / Google TV validation, the **Ember protocol** (an Android
sender app using MediaProjection), audio-only streaming, DLNA.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the code is organised
and how to add a new protocol.

## License

Ember is licensed under the [GNU GPL v3.0 or later](LICENSE). It includes
code from UxPlay, OpenSSL, libplist and others: see [NOTICE.md](NOTICE.md).

Ember is not affiliated with Apple Inc. or Amazon.com, Inc. AirPlay is a
trademark of Apple Inc.; Fire TV is a trademark of Amazon.com, Inc.

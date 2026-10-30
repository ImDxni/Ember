# Ember architecture

This document is for contributors. It explains how the code is split, which
rules keep the modules independent, and how to add a new input protocol.

## Goals that shape the design

- **Several input protocols, one renderer.** Today: mirroring from iPhone/iPad
  (RAOP, the protocol behind AirPlay). Later: the Ember protocol (Android
  sender app), maybe DLNA. Adding one must not change the core.
- **Any Android TV, not only Fire TV.** Device differences are detected at
  runtime (`DeviceCapabilities`), never by checking the model or brand.

## Modules

```
                      ┌──────────────┐
                      │   :app-tv    │  UI, service, settings, protocol registry
                      └──────┬───────┘
         ┌──────────────┬────┴─────────┬────────────────┐
         ▼              ▼              ▼                ▼
  ┌─────────────┐ ┌───────────┐ ┌─────────────┐ ┌────────────────┐
  │ :media-core │ │:discovery │ │:protocol-raop│ │:protocol-<new> │
  │ MediaSink   │ │ Service-  │ │ UxPlay (C)  │ │                │
  │ impl, caps  │ │ Advertiser│ │ + JNI glue  │ │                │
  └──────┬──────┘ └─────┬─────┘ └──────┬──────┘ └───────┬────────┘
         └──────────────┴──────┬───────┴────────────────┘
                               ▼
                      ┌────────────────┐
                      │ :receiver-api  │  pure Kotlin interfaces
                      └────────────────┘
```

| Module | Type | Responsibility |
|---|---|---|
| `:receiver-api` | Kotlin/JVM, no Android | Interfaces and value types shared by everybody |
| `:media-core` | Android library | `MediaSink` implementation (MediaCodec, AudioTrack, A/V sync), `AndroidCapabilitiesProvider` |
| `:discovery` | Android library | `ServiceAdvertiser` implementations (NsdManager, JmDNS fallback) |
| `:protocol-raop` | Android library + NDK | iPhone/iPad mirroring: UxPlay `lib/` + JNI glue |
| `:app-tv` | Android app | Activity, foreground service, settings, `ProtocolRegistry`, `ReceiverController` |

**Dependency rule:** feature modules depend only on `:receiver-api`. A
protocol never depends on `:media-core` or `:discovery` and vice versa; only
`:app-tv` sees all modules and wires them together.

## The contracts (`:receiver-api`)

| Interface | Implemented by | Used by |
|---|---|---|
| `ProtocolFactory`, `ReceiverProtocol` | each protocol module | app (`ReceiverController`) |
| `SessionHost`, `MediaSession`, `SessionControl` | app | protocols |
| `MediaSink` | `:media-core` | protocols (via `MediaSession.sink`) |
| `ServiceAdvertiser` | `:discovery` | protocols |
| `CapabilitiesProvider` → `DeviceCapabilities` | `:media-core` | app, protocols |
| `EmberLog` | app (`AndroidLog`) | everybody |

### Lifecycle

1. The app builds a `ProtocolRegistry` and registers every factory
   (`EmberApp.onCreate`).
2. `ReceiverService` (foreground) calls `ReceiverController.start()`, which
   detects `DeviceCapabilities`, and for each supported factory creates the
   protocol and calls `start(ProtocolContext)`.
3. The protocol opens its sockets and announces itself through
   `context.advertiser`.
4. When a sender connects, the protocol calls
   `context.sessions.openSession(info, control)`. A `null` result means the
   host refused (e.g. busy). Otherwise it pushes media into
   `session.sink` and finally calls `session.close(reason)` or
   `session.fail(error)` exactly once.
5. The host can drop a sender at any time through `SessionControl.disconnect()`.

### Media data

Protocols deliver media in a protocol-neutral form:

- `VideoFormat`: codec (H.264/H.265), parameter sets (Annex-B), coded and
  visible size. Sent before the first frame and again whenever it changes
  (rotation, resolution change).
- `AudioFormat`: codec (AAC-LC, AAC-ELD, ALAC, PCM), sample rate, channels,
  codec config.
- `EncodedFrame`: payload + `ptsNanos` + flags. The buffer is only valid during
  the call.

**Timestamps** use one clock: the receiver's monotonic clock
(`System.nanoTime()` domain). Each protocol converts its sender clock to it
(RAOP uses UxPlay's NTP sync). The sink therefore never needs to know which
protocol produced the data.

## :protocol-raop internals

```
protocol-raop/src/main/cpp/
├── CMakeLists.txt          builds libember_raop.so for each ABI
├── uxplay/                 UxPlay lib/, vendored UNMODIFIED (see UPSTREAM.md)
└── android/
    ├── dnssd_android.c     replaces UxPlay's mDNS backends: builds TXT records,
    │                       forwards (un)registration to Kotlin via a hook
    ├── raop_jni.c          JNI entry points (RegisterNatives) and startup logic
    └── ember_log.c         logcat bridge for native and UxPlay logs
```

- UxPlay is updated with `scripts/update-uxplay.sh [commit]`. Never edit
  `uxplay/` by hand; if a change is unavoidable, add a patch under
  `protocol-raop/patches/` and list it in `UPSTREAM.md`.
- Native dependencies (OpenSSL libcrypto, libplist) are static libraries built
  by `third_party/build-native-deps.sh` into `third_party/prebuilt/<abi>/`.
  Gradle's `prepareNativeDeps` task downloads the pinned prebuilt bundle when
  they are missing.
- JNI natives are bound with `RegisterNatives` to the class name passed by
  Gradle (`EMBER_JNI_CLASS`), so renaming the Kotlin package needs no C changes.

## Adding a new input protocol

Example: the future Ember protocol (Android sender app).

1. Create the module `:protocol-ember` (Android library, or plain Kotlin if it
   needs no Android API) depending only on `:receiver-api`. Add it to
   `settings.gradle.kts`.
2. Implement `ProtocolFactory` (unique `id`, `isSupported(capabilities)`) and
   `ReceiverProtocol`:
   - in `start()`: open sockets on your own threads, then announce with
     `context.advertiser.register(ServiceDescriptor(...))`;
   - when a sender connects: `context.sessions.openSession(...)`; refuse the
     sender if it returns `null`;
   - convert your sender's timestamps to the receiver monotonic clock and push
     `VideoFormat`/`AudioFormat` before the frames;
   - in `stop()`: close sessions (`DisconnectReason.RECEIVER_STOPPED`), close
     registrations and sockets.
3. In `:app-tv`: add `implementation(project(":protocol-ember"))` and
   `register(EmberProtocolFactory())` in `EmberApp`.

Nothing in `:media-core`, `:discovery` or the other protocols changes. If
your protocol needs something the contracts do not offer (e.g. URL playback
for DLNA), extend `:receiver-api` with a new, separate interface (such as a
`UriPlaybackSink`) instead of overloading `MediaSink`.

## Device differences

`AndroidCapabilitiesProvider` reads `MediaCodecList` (decoders, max size and
frame rate, adaptive playback, low latency) and the display mode. Protocols
use it to decide what to advertise (e.g. H.265 only if a decoder exists) and
the sink uses it to configure decoders. Do not add `Build.MODEL` /
`Build.MANUFACTURER` checks; if a device needs a workaround, describe it as a
capability.

## Package name

The Kotlin base package (`ember.basePackage` in `gradle.properties`) is
`io.github.imdxni.ember`; module namespaces append `.api`, `.media`,
`.discovery`, `.protocol.raop`, `.app`. To change it, run
`scripts/rename-package.sh <new.package>`. The installed application id
(`ember.applicationId`) is independent and can be changed freely.

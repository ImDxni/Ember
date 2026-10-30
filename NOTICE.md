# Notices and credits

Ember is free software, distributed under the **GNU General Public License,
version 3 or later** (see [LICENSE](LICENSE)). It is built on the work of the
projects below. Their original copyright notices are kept unchanged in their
source files; this file explains where every third-party component comes from.

Ember is an independent project. It is not affiliated with, endorsed or
sponsored by Apple Inc. or Amazon.com, Inc. AirPlay, iPhone, iPad and Mac are
trademarks of Apple Inc.; Fire TV is a trademark of Amazon.com, Inc. They are
mentioned only to describe compatibility.

## Components compiled into the app

### UxPlay — protocol library (`protocol-raop/src/main/cpp/uxplay/`)

- Source: <https://github.com/FDH2/UxPlay>, `lib/` directory, vendored
  unmodified at the commit recorded in
  [`UPSTREAM.md`](protocol-raop/src/main/cpp/uxplay/UPSTREAM.md).
- UxPlay as a whole: GPL-3.0. Individual files keep their own notices:
  - Copyright (C) 2011–2018 Juho Vähä-Herttua (shairplay) — LGPL-2.1-or-later
  - Copyright (c) 2019 dsafa22 (RPiPlay); 2014 Joakim Plate; modified by
    Florian Draschbacher (2019) — LGPL-2.1-or-later
  - Copyright (C) 2020 Jaslo Ziska — LGPL-2.1-or-later
  - Copyright (c) 2021–2026 F. Duncanh (fduncanh), UxPlay maintainer — GPL-3.0 / LGPL-2.1-or-later as marked
  - `lib/srp.c`: Copyright (c) 2010–2014 Tom Cocagne — MIT
  - `lib/llhttp/`: Copyright Fedor Indutny, 2018 — MIT (`LICENSE-MIT`)
  - `lib/playfair/`: playfair (FairPlay handshake) — GPL-3.0 (`LICENSE.md`)
- Not imported: `lib/dns_sd/`, `lib/mdnsd/`, `renderers/` (GStreamer), `uxplay.cpp`.

### Ember Android glue derived from UxPlay

- `protocol-raop/src/main/cpp/android/dnssd_android.c` is derived from UxPlay
  `lib/mdnsd/dnssd_mdnsd.c` (Copyright (C) 2011-2012 Juho Vähä-Herttua,
  2026 kgbook, modified by fduncanh) — LGPL-2.1-or-later, notices retained.
- Startup logic in `protocol-raop/src/main/cpp/android/raop_jni.c` follows
  UxPlay's `uxplay.cpp` (GPL-3.0).

### OpenSSL — libcrypto (static, prebuilt)

- Source: <https://www.openssl.org/>, version pinned in
  [`third_party/native-deps.properties`](third_party/native-deps.properties).
- License: Apache License 2.0 (compatible with GPL-3.0).
- Built by [`third_party/build-native-deps.sh`](third_party/build-native-deps.sh);
  source tarball attached to the `native-deps-v*` GitHub release.

### libplist (static, prebuilt)

- Source: <https://github.com/libimobiledevice/libplist>, version pinned in
  [`third_party/native-deps.properties`](third_party/native-deps.properties).
- License: LGPL-2.1-or-later. Copyright (c) Jonathan Beck, Nikias Bassen,
  Martin Szulecki and contributors.
- Built by [`third_party/build-native-deps.sh`](third_party/build-native-deps.sh);
  source tarball attached to the `native-deps-v*` GitHub release.

### AndroidX Core

- <https://developer.android.com/jetpack/androidx/releases/core> — Apache License 2.0.

## Ember's own code

Copyright (C) 2026 Ember contributors — GPL-3.0-or-later.

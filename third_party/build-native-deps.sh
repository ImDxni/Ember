#!/usr/bin/env bash
# Builds the native dependencies of :protocol-raop (OpenSSL libcrypto and
# libplist) as static libraries for every Android ABI Ember ships.
#
# Output:
#   third_party/prebuilt/<abi>/include/...
#   third_party/prebuilt/<abi>/lib/libcrypto.a
#   third_party/prebuilt/<abi>/lib/libplist-2.0.a
#   third_party/dist/ember-native-deps-v<N>.tar.gz (+ .sha256)
#
# Requirements: Linux (or WSL), curl, tar, bzip2, make, perl, and an Android
# NDK pointed to by ANDROID_NDK_ROOT (or ANDROID_NDK_HOME).
#
# Usage: third_party/build-native-deps.sh [--no-bundle]
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source <(tr -d '\r' < "$HERE/native-deps.properties" | grep -E '^[A-Z0-9_]+=' | sed -E 's/^([A-Z0-9_]+)=(.*)$/\1="\2"/')

NDK="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-}}"
if [[ -z "$NDK" || ! -d "$NDK" ]]; then
    echo "error: set ANDROID_NDK_ROOT to an Android NDK (r27 or newer)" >&2
    exit 1
fi
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
[[ -d "$TOOLCHAIN" ]] || { echo "error: $TOOLCHAIN not found (Linux NDK required)" >&2; exit 1; }

MAKE_BUNDLE=1
[[ "${1:-}" == "--no-bundle" ]] && MAKE_BUNDLE=0

JOBS="$(nproc 2>/dev/null || echo 4)"
WORK="${EMBER_DEPS_WORKDIR:-$HOME/.cache/ember-native-deps}"
PREBUILT="$HERE/prebuilt"
mkdir -p "$WORK/src"

fetch() { # url sha256 dest
    local url="$1" sum="$2" dest="$3"
    if [[ ! -f "$dest" ]] || ! echo "$sum  $dest" | sha256sum -c --status; then
        echo ">> downloading $url"
        curl -sSfL -o "$dest.part" "$url"
        mv "$dest.part" "$dest"
    fi
    echo "$sum  $dest" | sha256sum -c --quiet
}

OPENSSL_TAR="$WORK/src/openssl-$OPENSSL_VERSION.tar.gz"
LIBPLIST_TAR="$WORK/src/libplist-$LIBPLIST_VERSION.tar.bz2"
fetch "$OPENSSL_URL" "$OPENSSL_SHA256" "$OPENSSL_TAR"
fetch "$LIBPLIST_URL" "$LIBPLIST_SHA256" "$LIBPLIST_TAR"

export PATH="$TOOLCHAIN/bin:$PATH"

for ABI in $ANDROID_ABIS; do
    case "$ABI" in
        armeabi-v7a) OSSL_TARGET=android-arm;   TRIPLE=armv7a-linux-androideabi; HOST=arm-linux-androideabi ;;
        arm64-v8a)   OSSL_TARGET=android-arm64; TRIPLE=aarch64-linux-android;    HOST=aarch64-linux-android ;;
        x86_64)      OSSL_TARGET=android-x86_64; TRIPLE=x86_64-linux-android;    HOST=x86_64-linux-android ;;
        *) echo "error: unsupported ABI $ABI" >&2; exit 1 ;;
    esac
    OUT="$PREBUILT/$ABI"
    BUILD="$WORK/build/$ABI"
    rm -rf "$OUT" "$BUILD"
    mkdir -p "$OUT" "$BUILD"

    echo ">> [$ABI] OpenSSL $OPENSSL_VERSION"
    tar -xzf "$OPENSSL_TAR" -C "$BUILD"
    (
        cd "$BUILD/openssl-$OPENSSL_VERSION"
        export ANDROID_NDK_ROOT="$NDK"
        ./Configure "$OSSL_TARGET" -D__ANDROID_API__="$ANDROID_API" -fPIC \
            no-shared no-module no-dso no-engine no-tests no-apps no-docs \
            no-ui-console no-comp no-legacy \
            --prefix="$OUT" --libdir=lib > "$BUILD/openssl-configure.log"
        make -j"$JOBS" build_libs > "$BUILD/openssl-build.log"
        make install_dev > "$BUILD/openssl-install.log"
    )
    # Only libcrypto is used; drop libssl and pkg-config files.
    rm -f "$OUT/lib/libssl.a"
    rm -rf "$OUT/lib/pkgconfig" "$OUT/lib/cmake"

    echo ">> [$ABI] libplist $LIBPLIST_VERSION"
    tar -xjf "$LIBPLIST_TAR" -C "$BUILD"
    (
        cd "$BUILD/libplist-$LIBPLIST_VERSION"
        export CC="$TRIPLE$ANDROID_API-clang" CXX="$TRIPLE$ANDROID_API-clang++"
        export AR=llvm-ar RANLIB=llvm-ranlib STRIP=llvm-strip
        export CFLAGS="-O2 -fPIC" CXXFLAGS="-O2 -fPIC"
        ./configure --host="$HOST" --prefix="$OUT" --disable-shared --enable-static \
            --with-pic --without-cython --without-tests > "$BUILD/libplist-configure.log"
        make -j"$JOBS" -C libcnary > "$BUILD/libplist-build.log"
        make -j"$JOBS" -C src >> "$BUILD/libplist-build.log"
        make -C src install-libLTLIBRARIES > "$BUILD/libplist-install.log"
        make -C include install > "$BUILD/libplist-install.log"
    )
    rm -f "$OUT"/lib/*.la "$OUT"/lib/libplist++*
    rm -rf "$OUT/lib/pkgconfig" "$OUT/include/plist/Array.h" "$OUT"/include/plist/*.h.in
    # Keep only C headers of libplist (the C++ wrapper is not used).
    find "$OUT/include/plist" -name '*.h' ! -name 'plist.h' -delete

    cat > "$OUT/VERSIONS" <<EOF
openssl=$OPENSSL_VERSION
libplist=$LIBPLIST_VERSION
android_api=$ANDROID_API
abi=$ABI
ndk=$(grep Pkg.Revision "$NDK/source.properties" | cut -d= -f2 | tr -d ' ')
EOF
    echo ">> [$ABI] done: $(ls "$OUT/lib" | tr '\n' ' ')"
done

# Licenses travel with the binaries.
mkdir -p "$PREBUILT/licenses"
tar -xzf "$OPENSSL_TAR" -C "$WORK" "openssl-$OPENSSL_VERSION/LICENSE.txt"
cp "$WORK/openssl-$OPENSSL_VERSION/LICENSE.txt" "$PREBUILT/licenses/OpenSSL-LICENSE.txt"
tar -xjf "$LIBPLIST_TAR" -C "$WORK" "libplist-$LIBPLIST_VERSION/COPYING.LESSER"
cp "$WORK/libplist-$LIBPLIST_VERSION/COPYING.LESSER" "$PREBUILT/licenses/libplist-COPYING.LESSER"
echo "$NATIVE_DEPS_VERSION" > "$PREBUILT/BUNDLE_VERSION"

if [[ "$MAKE_BUNDLE" == 1 ]]; then
    DIST="$HERE/dist"
    NAME="ember-native-deps-v$NATIVE_DEPS_VERSION.tar.gz"
    mkdir -p "$DIST"
    tar --sort=name --owner=0 --group=0 --numeric-owner --mtime='2020-01-01 00:00Z' \
        -C "$HERE" -cf - prebuilt | gzip -n > "$DIST/$NAME"
    (cd "$DIST" && sha256sum "$NAME" > "$NAME.sha256")
    echo ">> bundle: $DIST/$NAME"
    cat "$DIST/$NAME.sha256"
fi

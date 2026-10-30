#!/usr/bin/env bash
# Vendors UxPlay's protocol library (lib/) into :protocol-raop.
#
# Only lib/ is imported, unmodified, minus the mDNS backends (lib/dns_sd,
# lib/mdnsd): Ember replaces them with protocol-raop/src/main/cpp/android/
# dnssd_android.c. GStreamer renderers and uxplay.cpp are not imported.
#
# Usage: scripts/update-uxplay.sh [commit-or-tag]   (default: pinned commit)
set -euo pipefail

REPO_URL="https://github.com/FDH2/UxPlay.git"
PINNED="534e2cc0e558d6c94bcc66964f8fc13ad73a6cdc"
REF="${1:-$PINNED}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/protocol-raop/src/main/cpp/uxplay"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

git -c core.autocrlf=false clone --quiet "$REPO_URL" "$TMP/UxPlay"
git -C "$TMP/UxPlay" checkout --quiet "$REF"
COMMIT="$(git -C "$TMP/UxPlay" rev-parse HEAD)"
DATE="$(git -C "$TMP/UxPlay" log -1 --format=%cs)"

rm -rf "$DEST"
mkdir -p "$DEST"
cp -r "$TMP/UxPlay/lib" "$DEST/lib"
rm -rf "$DEST/lib/dns_sd" "$DEST/lib/mdnsd"
find "$DEST/lib" -name CMakeLists.txt -delete
cp "$TMP/UxPlay/LICENSE" "$DEST/LICENSE"

cat > "$DEST/UPSTREAM.md" <<EOF
# Vendored UxPlay

- Upstream: https://github.com/FDH2/UxPlay
- Commit: \`$COMMIT\` ($DATE)
- Imported: \`lib/\` (unmodified), \`LICENSE\`
- Not imported: \`lib/dns_sd/\`, \`lib/mdnsd/\` (replaced by \`../android/dnssd_android.c\`),
  \`lib/**/CMakeLists.txt\` (replaced by \`../CMakeLists.txt\`), \`renderers/\` (GStreamer),
  \`uxplay.cpp\` (its startup logic is ported to \`../android/raop_jni.c\`).

Do not edit files in this directory: re-run \`scripts/update-uxplay.sh [commit]\` instead.
Any unavoidable change must go in a patch under \`protocol-raop/patches/\` and be listed here.
EOF

echo "UxPlay $COMMIT vendored into $DEST"

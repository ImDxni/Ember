#!/usr/bin/env bash
# Renames Ember's Kotlin base package (e.g. io.github.imdxni.ember ->
# io.github.someone.ember): moves source directories, rewrites package and
# import statements and updates gradle.properties. JNI needs no change (the
# class name is passed to CMake by Gradle).
#
# Usage: scripts/rename-package.sh <new.base.package> [--app-id-too]
set -euo pipefail

NEW="${1:?usage: $0 <new.base.package> [--app-id-too]}"
[[ "$NEW" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]] || { echo "invalid package: $NEW" >&2; exit 1; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
OLD="$(grep '^ember.basePackage=' gradle.properties | cut -d= -f2 | tr -d '\r')"
[[ "$OLD" != "$NEW" ]] || { echo "already $NEW"; exit 0; }

OLD_PATH="${OLD//.//}"
NEW_PATH="${NEW//.//}"

for src in */src/*/kotlin */src/*/java; do
    [[ -d "$src/$OLD_PATH" ]] || continue
    mkdir -p "$src/$(dirname "$NEW_PATH")"
    mv "$src/$OLD_PATH" "$src/$NEW_PATH.tmp"
    # Remove now-empty parents of the old path, then move into place.
    (cd "$src" && rmdir -p "$(dirname "$OLD_PATH")" 2>/dev/null || true)
    mkdir -p "$src/$(dirname "$NEW_PATH")"
    mv "$src/$NEW_PATH.tmp" "$src/$NEW_PATH"
    echo "moved $src/$OLD_PATH -> $src/$NEW_PATH"
done

OLD_RE="${OLD//./\\.}"
grep -rlE "$OLD_RE" --include='*.kt' --include='*.kts' --include='*.xml' --include='*.md' --include='*.pro' . \
    | grep -v '/build/' | while read -r f; do
    sed -i.bak -E "s/\b$OLD_RE\b/$NEW/g" "$f" && rm -f "$f.bak"
done
sed -i.bak -E "s/^ember\.basePackage=.*/ember.basePackage=$NEW/" gradle.properties
if [[ "${2:-}" == "--app-id-too" ]]; then
    sed -i.bak -E "s/^ember\.applicationId=.*/ember.applicationId=$NEW/" gradle.properties
fi
rm -f gradle.properties.bak
echo "base package: $OLD -> $NEW"

#!/usr/bin/env sh
# Legt eine gebaute APK unter „Interner Speicher/mumblemood“ ab.
# Der Name kommt automatisch aus der versionName in app/build.gradle.kts:
# 0.8.0 -> Breadcrumbz_v0.8.apk, 0.9.0 -> Breadcrumbz_v0.9.apk.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
VERSION=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$ROOT/app/build.gradle.kts" | head -n 1)
MAJOR=$(printf '%s' "$VERSION" | cut -d. -f1)
MINOR=$(printf '%s' "$VERSION" | cut -d. -f2)
NAME="Breadcrumbz_v${MAJOR}.${MINOR}.apk"

SOURCE=${1:-"$ROOT/app/build/outputs/apk/debug/app-debug.apk"}
DEST_DIR=${2:-/sdcard/mumblemood}

if [ ! -f "$SOURCE" ]; then
    echo "APK nicht gefunden: $SOURCE" >&2
    exit 1
fi

mkdir -p "$DEST_DIR"
cp "$SOURCE" "$DEST_DIR/$NAME"
printf '%s\n' "$NAME"

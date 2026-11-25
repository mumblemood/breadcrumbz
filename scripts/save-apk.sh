#!/usr/bin/env sh
# Legt eine gebaute APK unter „Interner Speicher/mumblemood“ ab.
# Der Dateiname enthält nur die Version (versionName). Jeder neue Build zählt
# sie um eins hoch (0.19 -> 0.20 -> 0.21 ...); versionCode zählt still mit.
# 0.21 / code 38 -> Breadcrumbz_v0.21.apk
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
VERSION=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$ROOT/app/build.gradle.kts" | head -n 1)
CODE=$(sed -n 's/.*versionCode = \([0-9][0-9]*\).*/\1/p' "$ROOT/app/build.gradle.kts" | head -n 1)
MAJOR=$(printf '%s' "$VERSION" | cut -d. -f1)
MINOR=$(printf '%s' "$VERSION" | cut -d. -f2)
if [ -z "$CODE" ]; then
    echo "versionCode nicht gefunden: $ROOT/app/build.gradle.kts" >&2
    exit 1
fi
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

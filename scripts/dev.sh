#!/usr/bin/env bash
set -euo pipefail

# Fast Android iteration loop for PVPS.
#
#   ./scripts/dev.sh install   incremental build + install + launch on the connected device
#   ./scripts/dev.sh watch     rebuild + reinstall whenever a Kotlin/XML file changes
#   ./scripts/dev.sh apk       debug APK only (no device required)
#   ./scripts/dev.sh release   release APK (asks for signing config)
#   ./scripts/dev.sh logcat    follow app logs
#   ./scripts/dev.sh connect <ip[:port]>  pair/connect adb over WiFi
#
# Gradle keeps its daemon and build cache warm, so the second install is much
# faster than a clean APK build. On ARM hosts where the SDK's aapt2 is x86_64,
# /tmp/opencode/aapt2 is a qemu wrapper and is passed automatically.

cd "$(dirname "$0")/.."

AAPT2_ARGS=()
if [[ -x /tmp/opencode/aapt2 ]]; then
  AAPT2_ARGS=(-Pandroid.aapt2FromMavenOverride=/tmp/opencode/aapt2)
fi

ADB="${ANDROID_HOME:-/tmp/opencode/android-sdk}/platform-tools/adb"
PACKAGE="dev.pvps"
ACTIVITY="dev.pvps/.MainActivity"

case "${1:-install}" in
  install)
    ./gradlew installDebug "${AAPT2_ARGS[@]}" --console=plain
    "$ADB" shell am force-stop "$PACKAGE" || true
    "$ADB" shell am start -n "$ACTIVITY"
    ;;
  watch)
    if ! command -v inotifywait > /dev/null 2>&1; then
      echo "watch needs inotify-tools (sudo apt install inotify-tools)" >&2
      exit 1
    fi
    echo "Watching app/src — Ctrl+C to stop."
    ./gradlew installDebug "${AAPT2_ARGS[@]}" --console=plain
    "$ADB" shell am force-stop "$PACKAGE" || true
    "$ADB" shell am start -n "$ACTIVITY" || true
    while true; do
      inotifywait -q -r -e modify,create,delete,move \
        --include '\.(kt|xml)$' app/src/main > /dev/null
      sleep 0.35
      echo "Change detected — rebuilding."
      if ./gradlew installDebug "${AAPT2_ARGS[@]}" --console=plain; then
        "$ADB" shell am force-stop "$PACKAGE" || true
        "$ADB" shell am start -n "$ACTIVITY" || true
      fi
    done
    ;;
  connect)
    if [[ -z "${2:-}" ]]; then
      echo "usage: $0 connect <ip[:port]>" >&2
      exit 2
    fi
    "$ADB" connect "$2"
    ;;
  apk)
    ./gradlew assembleDebug "${AAPT2_ARGS[@]}" --console=plain
    cp app/build/outputs/apk/debug/app-debug.apk "pvps-$(date +%H%M).apk"
    ;;
  release)
    ./gradlew assembleRelease "${AAPT2_ARGS[@]}" --console=plain
    ;;
  logcat)
    "$ADB" logcat -v color --pid="$("$ADB" shell pidof -s "$PACKAGE")"
    ;;
  *)
    echo "usage: $0 {install|watch|apk|release|logcat|connect <ip[:port]>}" >&2
    exit 2
    ;;
esac

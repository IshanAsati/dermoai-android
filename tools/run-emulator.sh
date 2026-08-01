#!/usr/bin/env bash
# Fast DermoAI emulator launcher (host GPU + more cores/RAM).
# Also applies the sensors-HAL workaround: the AVD's
# android.hardware.sensors-service.multihal can spin at ~95% CPU after boot
# (known emulator bug), which ANR-storms the whole guest — including DermoAI —
# and makes the app "super laggy". Killing it is harmless (no sensors used).
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-/home/light/Android/Sdk}"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"

# Under Wayland, Qt/emulator host-GPU is more reliable on XWayland.
if [[ "${XDG_SESSION_TYPE:-}" == "wayland" && -z "${QT_QPA_PLATFORM:-}" ]]; then
  export QT_QPA_PLATFORM=xcb
fi

AVD_NAME="${1:-DermoAI_Pixel}"
shift || true

# Kill existing instance of this AVD if running (optional soft restart)
if adb devices 2>/dev/null | grep -q emulator; then
  echo "Stopping existing emulator..."
  adb emu kill 2>/dev/null || true
  sleep 2
fi

echo "Starting $AVD_NAME with host GPU acceleration..."
emulator -avd "$AVD_NAME" \
  -gpu host \
  -accel on \
  -no-audio \
  -no-boot-anim \
  -cores 8 \
  -memory 4096 \
  "$@" &
EMU_PID=$!

# Wait for boot, then kill the spinning sensors HAL (see header comment).
adb wait-for-device 2>/dev/null || true
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done' 2>/dev/null || true
adb root >/dev/null 2>&1 || true
adb wait-for-device 2>/dev/null || true
adb shell 'kill -9 $(pidof android.hardware.sensors-service.multihal) 2>/dev/null' || true
echo "Sensors HAL workaround applied."

wait "$EMU_PID"

#!/usr/bin/env bash
# DermoAI emulator launcher for Windows/Git-Bash (host GPU).
# Handles the known emulator bug where android.hardware.sensors-service.multihal
# spins at ~95% CPU after boot, ANR-storms the guest and watchdog-kills
# system_server.
#
# IMPORTANT (learned the hard way): the sensors HAL must NOT be stopped before
# boot completes — system_server blocks on android.hardware.sensors.ISensors
# during its init and boot hangs forever. So: boot normally, then stop it
# durably with ctl.stop (init won't respawn it, unlike kill -9).
set -u

export ANDROID_HOME="${ANDROID_HOME:-/c/Android/Sdk}"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"

AVD_NAME="${1:-dermoai_test}"
shift || true

# Kill any existing emulator instance first.
if adb devices 2>/dev/null | grep -q "emulator-"; then
  echo "Stopping existing emulator..."
  adb emu kill 2>/dev/null || true
  sleep 4
fi

echo "Starting $AVD_NAME (windowed, host GPU)..."
emulator -avd "$AVD_NAME" \
  -gpu host \
  -accel on \
  -no-audio \
  -no-boot-anim \
  -no-snapshot-load \
  -no-snapshot-save \
  -cores 6 \
  -memory 4096 \
  "$@" &
EMU_PID=$!

# Wait for full boot (do NOT touch the sensors HAL before this).
echo "Waiting for boot..."
adb wait-for-device 2>/dev/null || true
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done' 2>/dev/null || true
echo "Boot complete."

# Now contain the sensors HAL instead of stopping it: system_server on this
# image hard-depends on android.hardware.sensors.ISensors (it retries forever
# and never registers the activity service if the HAL is gone). But the HAL
# busy-spins at ~95% CPU (known emulator bug), ANR-storming the guest. Fix:
# pin it to CPU 0 and renice it to 19 so it can't starve anything (6 cores
# means it burns 1 core max, at lowest priority).
adb root >/dev/null 2>&1 || true
adb wait-for-device 2>/dev/null || true
adb shell 'PID=$(pidof android.hardware.sensors-service.multihal); if [ -n "$PID" ]; then taskset -p 1 $PID >/dev/null 2>&1; renice -n 19 -p $PID >/dev/null 2>&1; echo "sensors HAL contained: pid=$PID cpu0 nice19"; else echo "sensors HAL not running (nothing to contain)"; fi'
echo "Emulator PID $EMU_PID running."

wait "$EMU_PID"

#!/usr/bin/env bash
set -Eeuo pipefail

APP_ID="ai.bozer.app"
MODEL_NAME="qwen2.5-0.5b-instruct-q4_k_m.gguf"
MODEL_SHA="74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
MODEL_DIR="/data/user/0/ai.bozer.app/files/models"
DEVICE_MODEL="$MODEL_DIR/$MODEL_NAME"

collect_diagnostics() {
  adb logcat -d -v threadtime > bozer-emulator-test.log 2>&1 || true
  adb exec-out screencap -p > bozer-smoke.png 2>/dev/null || true
  adb shell dumpsys meminfo "$APP_ID" > bozer-memory.txt 2>&1 || true
}
trap collect_diagnostics EXIT

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell run-as "$APP_ID" mkdir -p "$MODEL_DIR"
adb push .ci-cache/"$MODEL_NAME" /data/local/tmp/bozer-qwen.gguf
adb shell chmod 644 /data/local/tmp/bozer-qwen.gguf
adb shell run-as "$APP_ID" cp /data/local/tmp/bozer-qwen.gguf "$DEVICE_MODEL"
DEVICE_SHA="$(adb shell run-as "$APP_ID" sha256sum "$DEVICE_MODEL" | awk '{print $1}' | tr -d '\\r')"
test "$DEVICE_SHA" = "$MODEL_SHA"
adb shell run-as "$APP_ID" test -s "$DEVICE_MODEL"
# The test runner independently verified the full GGUF checksum. Push the
# marker to shared temporary storage, then copy it as the app UID. This avoids
# adb shell parsing the redirection outside run-as (which caused EACCES).
printf '%s' "$MODEL_SHA" > .ci-cache/model.sha256
adb push .ci-cache/model.sha256 /data/local/tmp/bozer-model.sha256
adb shell run-as "$APP_ID" cp /data/local/tmp/bozer-model.sha256 "$MODEL_DIR/$MODEL_NAME.sha256"
adb shell run-as "$APP_ID" test -s "$MODEL_DIR/$MODEL_NAME.sha256"
echo "MODEL_SHA256=$DEVICE_SHA"

# Grant the emulator's test package the same special overlay permission that
# the user must explicitly enable in Android Settings on a physical device.
adb shell appops set "$APP_ID" SYSTEM_ALERT_WINDOW allow
adb shell appops get "$APP_ID" SYSTEM_ALERT_WINDOW

adb logcat -c
gradle :app:connectedDebugAndroidTest --no-daemon

adb shell am force-stop "$APP_ID"
adb shell am start -n "$APP_ID/.MainActivity"
sleep 8
adb shell pidof "$APP_ID" | grep -Eq '[0-9]'
collect_diagnostics

if grep -Eq 'FATAL EXCEPTION|E/AndroidRuntime|Process: ai[.]bozer[.]app' bozer-emulator-test.log; then
  cat bozer-emulator-test.log
  echo "AndroidRuntime reported a fatal application error."
  exit 1
fi

test -s bozer-smoke.png
grep -q 'PASS: seven local turns' bozer-emulator-test.log
echo "PASS: install, pinned GGUF checksum, instrumented tests, live local inference, network search and restart diagnostics"

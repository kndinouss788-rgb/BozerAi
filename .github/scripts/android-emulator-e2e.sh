#!/usr/bin/env bash
set -Eeuo pipefail

APP_ID="ai.bozer.app"
MODEL_NAME="qwen2.5-0.5b-instruct-q4_k_m.gguf"
MODEL_SHA="74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"

collect_diagnostics() {
  # Instrumentation and target-app logs may use different PIDs. Capture the
  # required BOZER tags globally so E2E assertions are not hidden by --pid.
  adb logcat -d -v threadtime -s BOZER_E2E:I BOZER_PERF:I BOZER_NATIVE:I BOZER_SEARCH:I BOZER_MODEL:I BOZER_VOICE:I AndroidRuntime:E > bozer-emulator-test.log 2>&1 || true
  adb exec-out screencap -p > bozer-smoke.png 2>/dev/null || true
  adb shell dumpsys meminfo "$APP_ID" > bozer-memory.txt 2>&1 || true
  adb shell dumpsys cpuinfo | grep -F "$APP_ID" > bozer-cpu.txt 2>&1 || true
  adb shell dumpsys batterystats "$APP_ID" > bozer-battery.txt 2>&1 || true
}
MODEL_SERVER_PID=""
cleanup() {
  if [[ -n "$MODEL_SERVER_PID" ]]; then
    kill "$MODEL_SERVER_PID" 2>/dev/null || true
    wait "$MODEL_SERVER_PID" 2>/dev/null || true
  fi
  collect_diagnostics
}
trap cleanup EXIT

adb install -r app/build/outputs/apk/debug/app-debug.apk
echo "MODEL_SHA256=$MODEL_SHA"

# Serve the already checksum-verified real GGUF over a local reverse tunnel.
# LocalModelManager performs the actual HTTP download into app-private storage,
# verifies the SHA-256 itself, and only then allows llama.cpp to load it.
MODEL_HTTP_PORT=8765
python3 -m http.server "$MODEL_HTTP_PORT" --directory .ci-cache --bind 127.0.0.1 > bozer-model-http.log 2>&1 &
MODEL_SERVER_PID=$!
for attempt in $(seq 1 30); do
  if curl -fsSI "http://127.0.0.1:$MODEL_HTTP_PORT/$MODEL_NAME" >/dev/null; then break; fi
  sleep 1
done
curl -fsSI "http://127.0.0.1:$MODEL_HTTP_PORT/$MODEL_NAME" >/dev/null
adb reverse "tcp:$MODEL_HTTP_PORT" "tcp:$MODEL_HTTP_PORT"

# Grant the emulator's test package the same special overlay permission that
# the user must explicitly enable in Android Settings on a physical device.
adb shell appops set "$APP_ID" SYSTEM_ALERT_WINDOW allow
adb shell appops get "$APP_ID" SYSTEM_ALERT_WINDOW

adb logcat -c
adb shell dumpsys batterystats --reset || true

# Run the real-model conversation first, followed by all independent Android
# integration suites in one Gradle invocation to avoid repeated emulator/test
# runner startup overhead. The JUnit suite declares the required order.
gradle :app:connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=ai.bozer.app.PreRestartInstrumentedSuite

# Kill the real app process only after the E2E dialogue has populated history
# and memory, then verify that a fresh process restores them and reloads GGUF.
adb shell am force-stop "$APP_ID"
sleep 2
gradle :app:connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=ai.bozer.app.ProcessRestartPersistenceTest

adb shell pidof "$APP_ID" | grep -Eq '[0-9]'
collect_diagnostics

if grep -Eq 'FATAL EXCEPTION|Process: ai[.]bozer[.]app|Fatal signal' bozer-emulator-test.log; then
  cat bozer-emulator-test.log
  echo "AndroidRuntime reported a fatal application error."
  exit 1
fi

test -s bozer-smoke.png
grep -q 'model_ready=true' bozer-emulator-test.log
grep -q 'generation_ms=' bozer-emulator-test.log
grep -q 'PASS: seven local turns' bozer-emulator-test.log
grep -q 'PASS: process restart restored history, memory and model' bozer-emulator-test.log
grep -q 'microphone_permission_granted=true' bozer-emulator-test.log
grep -q 'shutdown_verified=true' bozer-emulator-test.log
test -s bozer-memory.txt
test -s bozer-battery.txt
echo "PASS: install, pinned GGUF checksum, real multi-turn inference, real Android network search, process-restart memory/history, RAM/CPU/battery diagnostics"

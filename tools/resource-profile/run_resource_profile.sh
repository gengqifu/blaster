#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PACKAGE_NAME="com.orion.blaster.demo"
ACTIVITY_NAME="com.orion.blaster.demo/.MainActivity"
CPU_COLLECTOR="$SCRIPT_DIR/collectors/collect_cpu_sample.sh"
MEM_COLLECTOR="$SCRIPT_DIR/collectors/collect_mem_sample.sh"
IO_COLLECTOR="$SCRIPT_DIR/collectors/collect_io_sample.sh"

PHASE=""
DEVICE_SERIAL=""
WITH_SCAN="true"
DRY_RUN="false"
OUTPUT_DIR=""
STATUS="initialized"
ERROR_MESSAGE=""
STARTED_AT="$(date '+%Y-%m-%dT%H:%M:%S%z')"
ENDED_AT=""
TIMESTAMP="$(date '+%Y%m%d-%H%M%S')"
PID=""
COMPLETION_SIGNAL="not_started"
PHASE_TIMEOUT_SECONDS=0
SILENCE_WINDOW_SECONDS=8
PROFILE_WINDOW_AFTER_FIRST_EXTRACT_SECONDS=60
LOGCAT_PID=""
SAMPLE_COUNT=0

log_step() {
  printf '[resource-profile] %s\n' "$1"
}

usage() {
  cat <<'EOF'
Usage: run_resource_profile.sh --phase <audio_identity|local_feature> [options]

Options:
  --phase <name>        Required. One of: audio_identity, local_feature
  --device <serial>     Optional adb device serial
  --with-scan           Run scan preparation before the target phase (default)
  --without-scan        Skip scan preparation
  --output-dir <path>   Optional artifact directory
  --dry-run             Generate artifact scaffold without talking to adb
EOF
}

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

adb_cmd() {
  if [[ -n "$DEVICE_SERIAL" ]]; then
    adb -s "$DEVICE_SERIAL" "$@"
  else
    adb "$@"
  fi
}

resolve_output_dir() {
  if [[ -n "$OUTPUT_DIR" ]]; then
    printf '%s\n' "$OUTPUT_DIR"
  else
    printf '%s\n' "$SCRIPT_DIR/artifacts/${TIMESTAMP}-${PHASE:-unknown}"
  fi
}

write_meta() {
  local artifact_dir="$1"
  mkdir -p "$artifact_dir"
  ENDED_AT="$(date '+%Y-%m-%dT%H:%M:%S%z')"
  cat > "$artifact_dir/meta.json" <<EOF
{
  "phase": "$(json_escape "$PHASE")",
  "status": "$(json_escape "$STATUS")",
  "started_at": "$(json_escape "$STARTED_AT")",
  "ended_at": "$(json_escape "$ENDED_AT")",
  "package_name": "$PACKAGE_NAME",
  "activity_name": "$ACTIVITY_NAME",
  "device_serial": "$(json_escape "$DEVICE_SERIAL")",
  "pid": "$(json_escape "$PID")",
  "with_scan": $WITH_SCAN,
  "dry_run": $DRY_RUN,
  "completion_signal": "$(json_escape "$COMPLETION_SIGNAL")",
  "error_message": "$(json_escape "$ERROR_MESSAGE")"
}
EOF
}

write_placeholder_outputs() {
  local artifact_dir="$1"
  cat > "$artifact_dir/cpu_samples.csv" <<'EOF'
timestamp,process_cpu_percent,thread_cpu_percent,thread_name
EOF
  cat > "$artifact_dir/mem_samples.csv" <<'EOF'
timestamp,total_pss_kb,native_heap_kb,dalvik_heap_kb,threads
EOF
  cat > "$artifact_dir/io_samples.csv" <<'EOF'
timestamp,read_bytes,write_bytes,syscr,syscw
EOF
  : > "$artifact_dir/phase.logcat.txt"
  cat > "$artifact_dir/summary.json" <<EOF
{
  "phase": "$(json_escape "$PHASE")",
  "started_at": "$(json_escape "$STARTED_AT")",
  "ended_at": "",
  "elapsed_ms": 0,
  "cpu_peak_process": 0,
  "cpu_peak_thread": 0,
  "pss_peak_kb": 0,
  "native_heap_peak_kb": 0,
  "dalvik_heap_peak_kb": 0,
  "thread_peak": 0,
  "read_bytes_delta": 0,
  "write_bytes_delta": 0,
  "syscr_delta": 0,
  "syscw_delta": 0,
  "completion_signal": "not_started",
  "sample_count": 0
}
EOF
  cat > "$artifact_dir/report.md" <<EOF
# Resource Profile Report

- phase: \`$PHASE\`
- status: \`$STATUS\`
- started_at: \`$STARTED_AT\`

## Notes

- Placeholder artifact generated.
EOF
}

cleanup_logcat() {
  if [[ -n "$LOGCAT_PID" ]]; then
    kill "$LOGCAT_PID" >/dev/null 2>&1 || true
    wait "$LOGCAT_PID" >/dev/null 2>&1 || true
  fi
}

trap cleanup_logcat EXIT

while [[ $# -gt 0 ]]; do
  case "$1" in
    --phase)
      PHASE="${2:-}"
      shift 2
      ;;
    --device)
      DEVICE_SERIAL="${2:-}"
      shift 2
      ;;
    --with-scan)
      WITH_SCAN="true"
      shift
      ;;
    --without-scan)
      WITH_SCAN="false"
      shift
      ;;
    --output-dir)
      OUTPUT_DIR="${2:-}"
      shift 2
      ;;
    --dry-run)
      DRY_RUN="true"
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      ARTIFACT_DIR="$(resolve_output_dir)"
      STATUS="failed"
      ERROR_MESSAGE="unknown_argument:$1"
      write_meta "$ARTIFACT_DIR"
      write_placeholder_outputs "$ARTIFACT_DIR"
      usage >&2
      exit 1
      ;;
  esac
done

ARTIFACT_DIR="$(resolve_output_dir)"
mkdir -p "$ARTIFACT_DIR"

if [[ -n "$DEVICE_SERIAL" ]]; then
  export ANDROID_SERIAL="$DEVICE_SERIAL"
fi

if [[ "$PHASE" != "audio_identity" && "$PHASE" != "local_feature" ]]; then
  STATUS="failed"
  ERROR_MESSAGE="invalid_or_missing_phase"
  write_meta "$ARTIFACT_DIR"
  write_placeholder_outputs "$ARTIFACT_DIR"
  usage >&2
  exit 1
fi

write_placeholder_outputs "$ARTIFACT_DIR"

if [[ "$PHASE" == "audio_identity" ]]; then
  PHASE_TIMEOUT_SECONDS=600
else
  PHASE_TIMEOUT_SECONDS=240
fi

if [[ "$DRY_RUN" == "true" ]]; then
  STATUS="dry_run_ready"
  write_meta "$ARTIFACT_DIR"
  printf 'Dry run scaffold generated at %s\n' "$ARTIFACT_DIR"
  exit 0
fi

ensure_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    STATUS="failed"
    ERROR_MESSAGE="${command_name}_not_found"
    write_meta "$ARTIFACT_DIR"
    printf '%s not found\n' "$command_name" >&2
    exit 1
  fi
}

ensure_adb_device() {
  log_step "checking adb device"
  local devices_output
  devices_output="$(adb_cmd devices)"
  if ! printf '%s\n' "$devices_output" | awk 'NR > 1 && $2 == "device" {found = 1} END {exit found ? 0 : 1}'; then
    STATUS="failed"
    ERROR_MESSAGE="device_not_connected"
    write_meta "$ARTIFACT_DIR"
    printf 'No adb device connected\n' >&2
    exit 1
  fi
}

ensure_package_installed() {
  log_step "checking package install"
  if ! adb_cmd shell pm list packages "$PACKAGE_NAME" | grep -q "$PACKAGE_NAME"; then
    STATUS="failed"
    ERROR_MESSAGE="package_not_installed"
    write_meta "$ARTIFACT_DIR"
    printf 'Package %s is not installed\n' "$PACKAGE_NAME" >&2
    exit 1
  fi
}

resolve_pid() {
  log_step "resolving pid"
  local pid
  pid="$(adb_cmd shell pidof -s "$PACKAGE_NAME" | tr -d '\r')"
  if [[ -z "$pid" ]]; then
    STATUS="failed"
    ERROR_MESSAGE="pid_not_found"
    write_meta "$ARTIFACT_DIR"
    printf 'Failed to resolve pid for %s\n' "$PACKAGE_NAME" >&2
    exit 1
  fi
  PID="$pid"
}

start_app() {
  log_step "starting app"
  adb_cmd shell am start -n "$ACTIVITY_NAME" >/dev/null
  sleep 2
  resolve_pid
}

scroll_to_top() {
  log_step "scrolling to top"
  local attempt
  for attempt in 1 2 3 4 5; do
    adb_cmd shell input swipe 540 700 540 1900 250 >/dev/null
    sleep 1
  done
}

dump_ui() {
  log_step "dumping ui"
  adb_cmd shell uiautomator dump /sdcard/window_dump.xml >/dev/null
  adb_cmd shell cat /sdcard/window_dump.xml | tr -d '\r'
}

find_bounds_by_resource_id() {
  local resource_id="$1"
  local ui_xml="$2"
  printf '%s' "$ui_xml" |
    grep -o "resource-id=\"$resource_id\"[^>]*bounds=\"\\[[0-9,]*\\]\\[[0-9,]*\\]\"" |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1,\2,\3,\4/' |
    head -n 1
}

tap_center_of_bounds() {
  local bounds="$1"
  local x1 y1 x2 y2
  IFS=',' read -r x1 y1 x2 y2 <<<"$bounds"
  local tap_x=$(((x1 + x2) / 2))
  local tap_y=$(((y1 + y2) / 2))
  adb_cmd shell input tap "$tap_x" "$tap_y" >/dev/null
}

tap_button() {
  local resource_id="$1"
  log_step "tapping button ${resource_id##*:id/}"
  local attempt ui_xml bounds
  for attempt in 1 2 3 4 5; do
    ui_xml="$(dump_ui)"
    bounds="$(find_bounds_by_resource_id "$resource_id" "$ui_xml" || true)"
    if [[ -n "$bounds" ]]; then
      tap_center_of_bounds "$bounds"
      sleep 1
      return 0
    fi
    adb_cmd shell input swipe 540 1900 540 700 250 >/dev/null
    sleep 1
  done
  STATUS="failed"
  ERROR_MESSAGE="button_not_found:${resource_id##*:id/}"
  write_meta "$ARTIFACT_DIR"
  printf 'Failed to find button %s\n' "$resource_id" >&2
  exit 1
}

wait_for_scan_summary() {
  log_step "waiting fixed window for scan"
  sleep 10
}

prepare_scan_if_needed() {
  if [[ "$WITH_SCAN" != "true" ]]; then
    log_step "scan preparation skipped"
    return 0
  fi
  log_step "running scan preparation"
  scroll_to_top
  tap_button "com.orion.blaster.demo:id/runButton"
  wait_for_scan_summary
}

start_phase_logcat() {
  local log_tag="$1"
  log_step "starting logcat for $log_tag"
  adb_cmd logcat -c
  adb_cmd logcat -v time "${log_tag}:I" '*:S' >"$ARTIFACT_DIR/phase.logcat.txt" 2>&1 &
  LOGCAT_PID=$!
  sleep 1
}

append_cpu_sample() {
  local process_line thread_line
  process_line="$("$CPU_COLLECTOR" "$PID" "$PACKAGE_NAME")"
  thread_line="$(printf '%s\n' "$process_line" | tail -n 1)"
  printf '%s\n' "$thread_line" >>"$ARTIFACT_DIR/cpu_samples.csv"
}

append_mem_sample() {
  "$MEM_COLLECTOR" "$PACKAGE_NAME" "$PID" >>"$ARTIFACT_DIR/mem_samples.csv"
}

append_io_sample() {
  "$IO_COLLECTOR" "$PACKAGE_NAME" "$PID" >>"$ARTIFACT_DIR/io_samples.csv"
}

sample_once() {
  log_step "sampling metrics"
  append_cpu_sample
  append_mem_sample
  append_io_sample
  SAMPLE_COUNT=$((SAMPLE_COUNT + 1))
}

read_peak_from_csv_column() {
  local file="$1"
  local column="$2"
  awk -F',' -v col="$column" 'NR > 1 && $col != "" { if ($col + 0 > max) max = $col + 0 } END { print max + 0 }' "$file"
}

read_delta_from_csv_column() {
  local file="$1"
  local column="$2"
  awk -F',' -v col="$column" 'NR == 2 { first = $col + 0 } NR > 1 { last = $col + 0 } END { print (last - first) + 0 }' "$file"
}

update_summary() {
  local ended_at elapsed_ms
  ended_at="$(date '+%Y-%m-%dT%H:%M:%S%z')"
  elapsed_ms=$(( ($(date +%s) - PHASE_STARTED_EPOCH) * 1000 ))
  cat > "$ARTIFACT_DIR/summary.json" <<EOF
{
  "phase": "$(json_escape "$PHASE")",
  "started_at": "$(json_escape "$STARTED_AT")",
  "ended_at": "$(json_escape "$ended_at")",
  "elapsed_ms": $elapsed_ms,
  "cpu_peak_process": $(read_peak_from_csv_column "$ARTIFACT_DIR/cpu_samples.csv" 2),
  "cpu_peak_thread": $(read_peak_from_csv_column "$ARTIFACT_DIR/cpu_samples.csv" 3),
  "pss_peak_kb": $(read_peak_from_csv_column "$ARTIFACT_DIR/mem_samples.csv" 2),
  "native_heap_peak_kb": $(read_peak_from_csv_column "$ARTIFACT_DIR/mem_samples.csv" 3),
  "dalvik_heap_peak_kb": $(read_peak_from_csv_column "$ARTIFACT_DIR/mem_samples.csv" 4),
  "thread_peak": $(read_peak_from_csv_column "$ARTIFACT_DIR/mem_samples.csv" 5),
  "read_bytes_delta": $(read_delta_from_csv_column "$ARTIFACT_DIR/io_samples.csv" 2),
  "write_bytes_delta": $(read_delta_from_csv_column "$ARTIFACT_DIR/io_samples.csv" 3),
  "syscr_delta": $(read_delta_from_csv_column "$ARTIFACT_DIR/io_samples.csv" 4),
  "syscw_delta": $(read_delta_from_csv_column "$ARTIFACT_DIR/io_samples.csv" 5),
  "completion_signal": "$(json_escape "$COMPLETION_SIGNAL")",
  "sample_count": $SAMPLE_COUNT
}
EOF
}

update_report() {
  local completion_line round_count timeout_seen first_round last_round
  completion_line="$(grep -E 'drain_(completed|timeout)|compare_skipped|extracted localSongId=|extracted roundIndex=' "$ARTIFACT_DIR/phase.logcat.txt" | tail -n 10 || true)"
  round_count="$(grep -c 'drain_round_start' "$ARTIFACT_DIR/phase.logcat.txt" || true)"
  timeout_seen="false"
  if grep -q 'drain_timeout' "$ARTIFACT_DIR/phase.logcat.txt"; then
    timeout_seen="true"
  fi
  first_round="$(grep 'drain_round_result' "$ARTIFACT_DIR/phase.logcat.txt" | head -n 1 || true)"
  last_round="$(grep 'drain_round_result' "$ARTIFACT_DIR/phase.logcat.txt" | tail -n 1 || true)"
  cat > "$ARTIFACT_DIR/report.md" <<EOF
# Resource Profile Report

- phase: \`$PHASE\`
- status: \`$STATUS\`
- completion_signal: \`$COMPLETION_SIGNAL\`
- artifact_dir: \`$ARTIFACT_DIR\`

## Summary

- cpu_peak_process: \`$(read_peak_from_csv_column "$ARTIFACT_DIR/cpu_samples.csv" 2)\`
- cpu_peak_thread: \`$(read_peak_from_csv_column "$ARTIFACT_DIR/cpu_samples.csv" 3)\`
- pss_peak_kb: \`$(read_peak_from_csv_column "$ARTIFACT_DIR/mem_samples.csv" 2)\`
- native_heap_peak_kb: \`$(read_peak_from_csv_column "$ARTIFACT_DIR/mem_samples.csv" 3)\`
- dalvik_heap_peak_kb: \`$(read_peak_from_csv_column "$ARTIFACT_DIR/mem_samples.csv" 4)\`
- thread_peak: \`$(read_peak_from_csv_column "$ARTIFACT_DIR/mem_samples.csv" 5)\`
- read_bytes_delta: \`$(read_delta_from_csv_column "$ARTIFACT_DIR/io_samples.csv" 2)\`
- write_bytes_delta: \`$(read_delta_from_csv_column "$ARTIFACT_DIR/io_samples.csv" 3)\`
- syscr_delta: \`$(read_delta_from_csv_column "$ARTIFACT_DIR/io_samples.csv" 4)\`
- syscw_delta: \`$(read_delta_from_csv_column "$ARTIFACT_DIR/io_samples.csv" 5)\`
- sample_count: \`$SAMPLE_COUNT\`

## Local Feature Rounds

- round_count: \`$round_count\`
- drain_timeout_seen: \`$timeout_seen\`
- first_round_result: \`${first_round:-n/a}\`
- last_round_result: \`${last_round:-n/a}\`

## Key Log Snippets

\`\`\`text
${completion_line:-no phase log lines captured}
\`\`\`

## Manual Review

- Fill acceptance conclusion in the milestone document.
EOF
}

run_phase() {
  local resource_id="$1"
  local log_tag="$2"
  log_step "running phase $PHASE"
  local deadline last_extract_count=0 last_extract_epoch=0 extract_count=0

  start_phase_logcat "$log_tag"
  scroll_to_top
  tap_button "$resource_id"
  resolve_pid
  PHASE_STARTED_EPOCH="$(date +%s)"
  deadline=$(( PHASE_STARTED_EPOCH + PHASE_TIMEOUT_SECONDS ))

  while [[ $(date +%s) -lt "$deadline" ]]; do
    sample_once

    if [[ "$PHASE" == "audio_identity" ]]; then
      extract_count="$(grep -c 'BlasterAudioIdentity.*extracted localSongId=' "$ARTIFACT_DIR/phase.logcat.txt" || true)"
      if [[ "$extract_count" -gt "$last_extract_count" ]]; then
        last_extract_count="$extract_count"
        last_extract_epoch="$(date +%s)"
      fi
      if [[ "$extract_count" -ge 1 && "$last_extract_epoch" -gt 0 ]]; then
        if [[ $(( $(date +%s) - PHASE_STARTED_EPOCH )) -ge "$PROFILE_WINDOW_AFTER_FIRST_EXTRACT_SECONDS" ]]; then
          COMPLETION_SIGNAL="profile_window_elapsed_after_extract"
          STATUS="completed"
          return 0
        fi
        if [[ $(( $(date +%s) - last_extract_epoch )) -ge "$SILENCE_WINDOW_SECONDS" ]]; then
          COMPLETION_SIGNAL="audio_extract_quiet"
          STATUS="completed"
          return 0
        fi
      fi
    else
      if grep -q 'BlasterLocalFeature.*extracted roundIndex=' "$ARTIFACT_DIR/phase.logcat.txt"; then
        if grep -q 'BlasterLocalFeature.*drain_completed' "$ARTIFACT_DIR/phase.logcat.txt"; then
          COMPLETION_SIGNAL="drain_completed"
          STATUS="completed"
          return 0
        fi
        if grep -q 'BlasterLocalFeature.*drain_timeout' "$ARTIFACT_DIR/phase.logcat.txt"; then
          COMPLETION_SIGNAL="drain_timeout"
          STATUS="completed"
          return 0
        fi
      fi
    fi

    sleep 1
  done

  if [[ "$PHASE" == "audio_identity" ]]; then
    if [[ "$last_extract_count" -gt 0 ]]; then
      COMPLETION_SIGNAL="phase_timeout_after_extract"
      STATUS="completed"
      return 0
    fi
    COMPLETION_SIGNAL="timeout_no_extract"
  else
    COMPLETION_SIGNAL="timeout_no_embedding"
  fi
  STATUS="failed"
  ERROR_MESSAGE="$COMPLETION_SIGNAL"
  return 1
}

ensure_command adb
ensure_adb_device
ensure_package_installed
start_app
prepare_scan_if_needed

phase_exit_code=0
if [[ "$PHASE" == "audio_identity" ]]; then
  if ! run_phase "com.orion.blaster.demo:id/runAudioButton" "BlasterAudioIdentity"; then
    phase_exit_code=$?
  fi
else
  if ! run_phase "com.orion.blaster.demo:id/runLocalFeatureButton" "BlasterLocalFeature"; then
    phase_exit_code=$?
  fi
fi

update_summary
update_report
write_meta "$ARTIFACT_DIR"

if [[ "$phase_exit_code" -ne 0 || "$STATUS" != "completed" ]]; then
  exit 1
fi

printf 'Completed %s profiling at %s\n' "$PHASE" "$ARTIFACT_DIR"

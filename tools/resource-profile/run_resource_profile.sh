#!/usr/bin/env bash

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PACKAGE_NAME="com.orion.blaster.demo"
ACTIVITY_NAME="com.orion.blaster.demo/.MainActivity"

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
  "with_scan": $WITH_SCAN,
  "dry_run": $DRY_RUN,
  "error_message": "$(json_escape "$ERROR_MESSAGE")"
}
EOF
}

write_placeholder_outputs() {
  local artifact_dir="$1"
  cat > "$artifact_dir/cpu_samples.csv" <<'EOF'
timestamp,process_cpu_percent,thread_cpu_snapshot
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

- This is a milestone-2 scaffold artifact.
- Real sampling, completion detection, and summarized metrics are added in later milestones.
EOF
}

resolve_output_dir() {
  if [[ -n "$OUTPUT_DIR" ]]; then
    printf '%s\n' "$OUTPUT_DIR"
  else
    printf '%s\n' "$SCRIPT_DIR/artifacts/${TIMESTAMP}-${PHASE:-unknown}"
  fi
}

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
      ERROR_MESSAGE="unknown_argument:$1"
      STATUS="failed"
      ARTIFACT_DIR="$(resolve_output_dir)"
      write_meta "$ARTIFACT_DIR"
      write_placeholder_outputs "$ARTIFACT_DIR"
      usage >&2
      exit 1
      ;;
  esac
done

ARTIFACT_DIR="$(resolve_output_dir)"
mkdir -p "$ARTIFACT_DIR"

if [[ "$PHASE" != "audio_identity" && "$PHASE" != "local_feature" ]]; then
  STATUS="failed"
  ERROR_MESSAGE="invalid_or_missing_phase"
  write_meta "$ARTIFACT_DIR"
  write_placeholder_outputs "$ARTIFACT_DIR"
  usage >&2
  exit 1
fi

write_placeholder_outputs "$ARTIFACT_DIR"

if [[ "$DRY_RUN" == "true" ]]; then
  STATUS="dry_run_ready"
  write_meta "$ARTIFACT_DIR"
  printf 'Dry run scaffold generated at %s\n' "$ARTIFACT_DIR"
  exit 0
fi

if ! command -v adb >/dev/null 2>&1; then
  STATUS="failed"
  ERROR_MESSAGE="adb_not_found"
  write_meta "$ARTIFACT_DIR"
  printf 'adb not found\n' >&2
  exit 1
fi

STATUS="scaffold_ready"
write_meta "$ARTIFACT_DIR"
printf 'Scaffold ready at %s\n' "$ARTIFACT_DIR"

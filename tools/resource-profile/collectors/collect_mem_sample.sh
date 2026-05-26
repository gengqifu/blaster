#!/usr/bin/env bash

set -euo pipefail

PACKAGE_NAME="${1:?package name is required}"
PID="${2:?pid is required}"
TIMESTAMP="$(date '+%Y-%m-%dT%H:%M:%S%z')"

meminfo="$(adb shell dumpsys meminfo "$PACKAGE_NAME")"
status="$(adb shell run-as "$PACKAGE_NAME" cat "/proc/$PID/status")"

total_pss="$(printf '%s\n' "$meminfo" | awk '/TOTAL PSS:/ {print $3; exit}')"
native_heap="$(printf '%s\n' "$meminfo" | awk '$1 == "Native" && $2 == "Heap" {print $3; exit}')"
dalvik_heap="$(printf '%s\n' "$meminfo" | awk '$1 == "Dalvik" && $2 == "Heap" {print $3; exit}')"
threads="$(printf '%s\n' "$status" | awk -F ':' '/Threads/ {gsub(/ /, "", $2); print $2; exit}')"

printf '%s,%s,%s,%s,%s\n' "$TIMESTAMP" "${total_pss:-0}" "${native_heap:-0}" "${dalvik_heap:-0}" "${threads:-0}"

#!/usr/bin/env bash

set -euo pipefail

PACKAGE_NAME="${1:?package name is required}"
PID="${2:?pid is required}"
TIMESTAMP="$(date '+%Y-%m-%dT%H:%M:%S%z')"

io_stats="$(adb shell run-as "$PACKAGE_NAME" cat "/proc/$PID/io")"
read_bytes="$(printf '%s\n' "$io_stats" | awk -F ':' '/read_bytes/ {gsub(/ /, "", $2); print $2; exit}')"
write_bytes="$(printf '%s\n' "$io_stats" | awk -F ':' '/write_bytes/ {gsub(/ /, "", $2); print $2; exit}')"
syscr="$(printf '%s\n' "$io_stats" | awk -F ':' '/syscr/ {gsub(/ /, "", $2); print $2; exit}')"
syscw="$(printf '%s\n' "$io_stats" | awk -F ':' '/syscw/ {gsub(/ /, "", $2); print $2; exit}')"

printf '%s,%s,%s,%s,%s\n' "$TIMESTAMP" "${read_bytes:-0}" "${write_bytes:-0}" "${syscr:-0}" "${syscw:-0}"

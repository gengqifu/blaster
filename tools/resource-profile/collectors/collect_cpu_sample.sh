#!/usr/bin/env bash

set -euo pipefail

PID="${1:?pid is required}"
PACKAGE_NAME="${2:?package name is required}"
TIMESTAMP="$(date '+%Y-%m-%dT%H:%M:%S%z')"

process_cpu="$(
  adb shell top -b -p "$PID" -n 1 |
    awk -v pid="$PID" '$1 == pid {print $9; exit}'
)"

thread_snapshot="$(
  adb shell top -b -H -p "$PID" -n 1 |
    awk -v package="$PACKAGE_NAME" '
      $NF == package {
        cpu = $9 + 0
        if (cpu > max_cpu) {
          max_cpu = cpu
          thread_name = $(NF - 1)
        }
      }
      END { printf "%.1f,%s\n", max_cpu + 0, thread_name }
    '
)"

printf '%s,%s,%s\n' "$TIMESTAMP" "${process_cpu:-0}" "$thread_snapshot"

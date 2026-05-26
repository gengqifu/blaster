#!/usr/bin/env bash

set -eu

PID="${1:?pid is required}"
adb shell top -H -p "$PID" -n 1

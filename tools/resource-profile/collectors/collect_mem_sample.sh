#!/usr/bin/env bash

set -eu

PACKAGE_NAME="${1:?package name is required}"
PID="${2:?pid is required}"
adb shell dumpsys meminfo "$PACKAGE_NAME"
adb shell cat "/proc/$PID/status"

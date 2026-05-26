#!/usr/bin/env bash

set -eu

PID="${1:?pid is required}"
adb shell cat "/proc/$PID/io"

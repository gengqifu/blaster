#!/usr/bin/env bash

set -eu

TAG="${1:?log tag is required}"
adb logcat -v time "${TAG}:I" '*:S'

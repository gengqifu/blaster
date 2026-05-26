# Resource Profile Automation

This directory contains the automation entrypoint for Android-side resource profiling.

Supported phases:

- `audio_identity`
- `local_feature`

Main entry:

```bash
tools/resource-profile/run_resource_profile.sh --phase audio_identity --dry-run
tools/resource-profile/run_resource_profile.sh --phase audio_identity
tools/resource-profile/run_resource_profile.sh --phase local_feature
```

Supported arguments:

- `--phase audio_identity|local_feature`
- `--device <serial>`
- `--with-scan`
- `--without-scan`
- `--output-dir <path>`
- `--dry-run`

Default artifact layout:

```text
tools/resource-profile/artifacts/<timestamp>-<phase>/
  meta.json
  cpu_samples.csv
  mem_samples.csv
  io_samples.csv
  phase.logcat.txt
  summary.json
  report.md
```

Execution notes:

- `audio_identity` uses `RUN SCAN + MATCH` as the default preparation step unless `--without-scan` is passed.
- `local_feature` also uses `RUN SCAN + MATCH` as the default preparation step.
- The script clears phase logcat before each target phase and captures only the relevant tag:
  - `BlasterAudioIdentity`
  - `BlasterLocalFeature`

Completion signals:

- `audio_extract_quiet`
  - at least one `BlasterAudioIdentity extracted ...` was observed
  - no new `extracted` appeared during the configured silence window
- `profile_window_elapsed_after_extract`
  - at least one `BlasterAudioIdentity extracted ...` was observed
  - the bounded profiling window elapsed, so the run is considered a valid resource sample even if the full queue has not drained
- `drain_completed`
  - `BlasterLocalFeature` reported natural drain completion
- `drain_timeout`
  - `BlasterLocalFeature` reported timeout after bounded drain processing
- `timeout_no_extract`
  - `audio_identity` timed out without any successful extraction
- `timeout_no_embedding`
  - `local_feature` timed out without any embedding extraction

How to read artifacts:

- `meta.json`
  - final run status, package, pid, phase, completion signal
- `cpu_samples.csv`
  - timestamp, process CPU peak sample, highest thread CPU sample, hottest thread name
- `mem_samples.csv`
  - timestamp, `TOTAL PSS`, `Native Heap`, `Dalvik Heap`, thread count
- `io_samples.csv`
  - timestamp, `read_bytes`, `write_bytes`, `syscr`, `syscw`
- `phase.logcat.txt`
  - raw phase logs used for completion detection
- `summary.json`
  - machine-readable resource summary for document writeback
- `report.md`
  - human-readable phase report with key log snippets and round summary

Recommended writeback workflow:

1. Run the phase script and note the artifact directory.
2. Copy `summary.json` key metrics into the milestone acceptance record.
3. Reference `phase.logcat.txt` and `report.md` in `关键日志/截图` and `数据产物`.
4. Write manual conclusions only for:
   - whether the resource behavior is acceptable
   - follow-up optimization risks or hypotheses

Known failure modes to record instead of hiding:

- device not connected
- package not installed
- pid not found at startup
- target button not found in the current UI dump
- no completion logs observed
- adb sampling command failed during the phase

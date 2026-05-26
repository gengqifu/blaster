# Resource Profile Automation

This directory contains the automation scaffold for Android-side resource profiling.

Scope of the first implementation phase:

- support `audio_identity` and `local_feature` phases
- create a stable artifact directory
- write `meta.json` even when execution fails early
- generate placeholder output files for dry-run verification

Main entry:

```bash
tools/resource-profile/run_resource_profile.sh --phase audio_identity --dry-run
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

Current milestone status:

- milestone 2 provides scaffold and dry-run artifact generation
- real device sampling and phase completion detection are added in later milestones

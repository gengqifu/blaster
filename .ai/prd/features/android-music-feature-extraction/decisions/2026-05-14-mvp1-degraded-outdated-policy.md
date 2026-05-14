# Decision Log: MVP-1 degraded / outdated policy (2026-05-14)

## Background

This decision log records two MVP-1 policy clarifications for `.ai/prd/features/android-music-feature-extraction/mvp-plans/mvp-1-client-mock-loop.md`.

Before this decision, the document allowed ambiguous branches:

- `rejectReason = degraded` could map to `WAITING_TO_CONTINUE` or `SKIPPED`.
- `OUTDATED` appeared both as a manual repository state and a mock-returned scenario.

These ambiguities could lead to inconsistent implementation and test assertions.

## Decisions

1. For MVP-1, `rejectReason = degraded` MUST map to `WAITING_TO_CONTINUE`.
2. For MVP-1, `OUTDATED` MUST be triggered only by `FeatureRepository.markOutdated(localSongId)`.
3. `MockCloudMatchGateway` in MVP-1 MUST NOT directly return `OUTDATED`.

## Impact Scope

- Affects MVP-1 pipeline state mapping, mock scenario definition, demo scenario wording, and test/acceptance checklist wording.
- Does not change code interfaces or data structure definitions.
- Does not expand to a full cross-document cleanup in `tech-design-v0.1.md` for this change set.

## Alternatives Considered

- `degraded -> WAITING_TO_CONTINUE or SKIPPED`: rejected due to ambiguous implementation behavior in MVP-1.
- Mock returning `OUTDATED`: rejected because `OUTDATED` in MVP-1 is defined as a repository-level staleness marker, not a cloud match result.

## Effective Date

- Effective on 2026-05-14.

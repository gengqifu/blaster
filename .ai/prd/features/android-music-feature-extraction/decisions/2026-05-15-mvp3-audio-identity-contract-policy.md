# Decision Log: MVP-3 audio identity request contract policy (2026-05-15)

## Background

The existing code has an early placeholder `AudioIdentityMatchRequest`, while the MVP-3 plan requires a stable outer request shape for audio fingerprint matching. Without a documented contract, implementation could accidentally omit duration, clip policy, algorithm version, payload encoding, or basic info context.

MVP-3 also needs stable result semantics for timeout, degrade, and no-match paths before scheduler and pipeline work begins.

## Decisions

1. `AudioIdentityMatchRequest` outer fields for MVP-3 MUST include:
   - `localSongId`
   - `durationMs`
   - `clipPolicy`
   - `algorithm`
   - `algorithmVersion`
   - `payloadEncoding`
   - `payload`
   - `basicInfo`
   - `forceScenario`
2. `payload` MUST contain algorithm-specific fingerprint data only. It MUST NOT be used to hide missing outer fields.
3. `basicInfo` MUST be passed with the audio identity request as auxiliary matching context.
4. `forceScenario` is a Mock/demo control only and MUST NOT be treated as part of the future real service contract.
5. `MatchResult` MUST remain `RELIABLE`, `CANDIDATE`, `NONE`, and `ERROR`.
6. `timeout` and `degrade` MUST continue to be represented through `ERROR.rejectReason` or equivalent diagnostic reason, not new `MatchResult` values.
7. `NONE` means no audio identity match and MUST NOT consume technical failure retry count.

## Impact Scope

- Affects gateway request models, Mock audio identity matching, audio identity input generation, repository diagnostics, pipeline state transitions, tests, and demo request summaries.
- Does not change ResultProvider reliable/candidate consumption semantics.
- Does not require real service wire-format compatibility in MVP-3; the outer client contract is stabilized for future mapping.

## Alternatives Considered

- Keep the placeholder request shape: rejected because it omits fields required by MVP-3 acceptance and diagnostics.
- Put clip policy or algorithm version inside payload only: rejected because callers and diagnostics need stable outer fields.
- Add `TIMEOUT` or `DEGRADED` to `MatchResult`: rejected to keep cloud match semantics consistent with MVP-1/MVP-2.
- Retry `NONE`: rejected because no-match is a business result, not a technical failure.

## Effective Date

- Effective on 2026-05-15.

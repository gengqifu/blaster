# Decision Log: MVP-4 YAMNet Android TFLite policy (2026-05-15)

## Background

MVP-4 needs a concrete local embedding validation path before engineering work starts. The existing MVP-4 plan selects YAMNet TFLite as the preferred path, but earlier wording still leaves model source, runtime dependency, Android loading approach, package budget, and failure-stop rules as implementation-time decisions.

Leaving those choices open would make the repository contract, scheduler semantics, acceptance criteria, and later demo scope unstable.

## Decisions

1. MVP-4 MUST keep `YAMNet TFLite` as the only primary local embedding validation path.
2. `VGGish` MUST remain failover-only. It MUST NOT be implemented in parallel with YAMNet during MVP-4.
3. The first MVP-4 implementation MUST use an official TensorFlow-distributed YAMNet/TFLite starter artifact or a directly traceable derivative distributed for TensorFlow Lite audio classification workflows.
4. Before implementation starts, the exact model artifact used by MVP-4 MUST be recorded with:
   - official source URL
   - model file name
   - model file hash or equivalent immutable identifier
   - any associated label file or metadata file requirement
5. TensorFlow Lite runtime integration for MVP-4 MUST use the minimum dependency set needed to load the selected model on Android. MVP-4 MUST NOT introduce additional ML runtime stacks beyond TFLite for the primary path.
6. MVP-4 package planning MUST treat `20MB` as the default combined target for model artifact plus runtime increment. If the validated artifact exceeds this target, implementation MAY continue only if the deviation and reason are explicitly recorded back into the MVP-4 plan.
7. Android loading strategy for MVP-4 MUST assume development-time packaging only:
   - model may live in test resources or a development package
   - MVP-4 does NOT decide final production packaging
   - MVP-4 does NOT decide built-in versus dynamic delivery
8. License handling for MVP-4 MUST pass a redistribution gate before implementation continues:
   - the selected artifact must have an officially attributable source
   - the applicable upstream license/terms and any required notice handling must be identified
   - if those terms cannot be confirmed, MVP-4 implementation MUST stop and the plan MUST be updated before any further code integration
9. MVP-4 minimum feasibility gate MUST require one Android-side validation path that can:
   - load the selected model
   - execute at least one inference
   - return a non-empty embedding tensor or an explicit model-unsupported failure reason
10. If model source, licensing, Android loading, runtime integration, or the minimum feasibility gate fails, implementation MUST stop at MVP-4 milestone 1 and update the plan/decision log before continuing.

## Impact Scope

- Affects MVP-4 document Section 5, milestone 1 exit criteria, Android dependency selection, model asset handling, package budget discussion, and demo feasibility expectations.
- Does not commit MVP-4 to a production packaging strategy.
- Does not change MVP-1 through MVP-3 cloud match semantics.

## Alternatives Considered

- Implement YAMNet and VGGish in parallel: rejected because it would duplicate work before a single validated primary path exists.
- Pick VGGish as primary immediately: rejected because the current MVP-4 direction already prefers YAMNet and there is no local evidence yet that YAMNet is blocked.
- Accept an unofficial or unattributed model artifact: rejected because it would make license and reproducibility review unstable.
- Treat package budget as out of scope: rejected because MVP-4 explicitly needs package impact as a later decision input.

## Effective Date

- Effective on 2026-05-15.

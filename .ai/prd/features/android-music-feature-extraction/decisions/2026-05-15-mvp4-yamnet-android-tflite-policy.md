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
8. MVP-4 MUST treat model sourcing as a two-stage strategy:
   - demo/development validation MAY use a bundled model resource
   - productization MUST preserve a cloud-download-capable model delivery direction
   - first implementation design MUST NOT hardcode model loading so tightly that a later downloaded-file source cannot be introduced
9. License handling for MVP-4 MUST pass a redistribution gate before implementation continues:
   - the selected artifact must have an officially attributable source
   - the applicable upstream license/terms and any required notice handling must be identified
   - if those terms cannot be confirmed, MVP-4 implementation MUST stop and the plan MUST be updated before any further code integration
10. MVP-4 minimum feasibility gate MUST require one Android-side validation path that can:
   - load the selected model
   - execute at least one inference
   - return a non-empty embedding tensor or an explicit model-unsupported failure reason
11. MVP-4 milestone 1 MUST include a real engineering feasibility gate:
   - minimum TFLite runtime integration
   - a real model artifact available to the app
   - one real model load
   - one real inference attempt
12. Documentation and ADR updates alone do NOT satisfy MVP-4 milestone 1 completion.
13. If model source, licensing, Android loading, runtime integration, or the minimum feasibility gate fails, implementation MUST stop at MVP-4 milestone 1 and update the plan/decision log before continuing.

## Milestone 1 Validation Record

- Validation date: `2026-05-20`
- Selected artifact:
  - file name: `yamnet.tflite`
  - source URL: `https://tfhub.dev/google/lite-model/yamnet/tflite/1?lite-format=tflite`
  - SHA-256: `141fba1cdaae842c816f28edc4937e8b4f0af4c8df21862ccc6b52dc567993c3`
  - associated files: none for the first validation pass
- Runtime dependency:
  - `org.tensorflow:tensorflow-lite:2.14.0`
- Package size record:
  - model file: `16,096,668` bytes
  - TFLite AAR: `16,304,220` bytes
  - combined validation footprint: about `32.4MB`
  - conclusion: exceeds the default `20MB` target; acceptable for MVP-4 development validation only, and must be addressed by later productization packaging / cloud-download strategy
- License / redistribution conclusion:
  - first-pass validation uses the official TFHub-distributed YAMNet TFLite artifact
  - MVP-4 proceeds under upstream `Apache-2.0` notice handling
- Android-side feasibility result:
  - device command: `adb -s S4VCMV95CATGQGT4 shell am instrument -w -e class com.orion.blaster.demo.YamnetInstrumentationTest com.orion.blaster.demo.test/androidx.test.runner.AndroidJUnitRunner`
  - result: `OK (1 test)`
  - recorded model summary:
    - `inputShape=[1]`
    - `outputShapes=[[1, 521], [1, 1024], [1, 64]]`
    - `embeddingOutputIndex=1`
    - `embeddingVectorCount=1024`
- Milestone 1 decision:
  - the minimum Android-side YAMNet feasibility gate passed
  - MVP-4 may continue to milestone 2

## Impact Scope

- Affects MVP-4 document Section 5, milestone 1 exit criteria, Android dependency selection, model asset handling, package budget discussion, and demo feasibility expectations.
- Requires MVP-4 documentation to distinguish bundled-model validation from later cloud-download productization direction.
- Does not commit MVP-4 to a production packaging strategy.
- Does not change MVP-1 through MVP-3 cloud match semantics.

## Alternatives Considered

- Implement YAMNet and VGGish in parallel: rejected because it would duplicate work before a single validated primary path exists.
- Start with cloud download even for the first demo validation: rejected because it slows the fastest possible feasibility loop and adds non-essential moving parts to milestone 1.
- Keep milestone 1 at document-only level: rejected because MVP-4 cannot be considered started until a real model load and inference path has been exercised.
- Pick VGGish as primary immediately: rejected because the current MVP-4 direction already prefers YAMNet and there is no local evidence yet that YAMNet is blocked.
- Accept an unofficial or unattributed model artifact: rejected because it would make license and reproducibility review unstable.
- Treat package budget as out of scope: rejected because MVP-4 explicitly needs package impact as a later decision input.

## Effective Date

- Effective on 2026-05-15.

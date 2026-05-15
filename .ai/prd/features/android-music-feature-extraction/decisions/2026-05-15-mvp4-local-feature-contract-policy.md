# Decision Log: MVP-4 local feature contract policy (2026-05-15)

## Background

MVP-4 introduces a new local-feature result class that must remain clearly separated from reliable cloud association. The existing plan describes local embedding storage and result exposure, but the public field boundary, internal diagnostic boundary, feature schema version semantics, and `OUTDATED` trigger rule are not yet locked tightly enough for implementation.

Without a documented contract, later code could accidentally expose internal diagnostics as business data or blur `LOCAL_FEATURE_READY` with cloud reliability semantics.

## Decisions

1. MVP-4 `LocalFeature` public fields MUST be limited to:
   - `embedding`
   - `modelName`
   - `modelVersion`
   - `featureSchemaVersion`
   - `generatedAtMs`
2. `LocalFeature` MUST represent local feature availability only. It MUST NOT imply reliable cloud association.
3. `LOCAL_FEATURE_READY` MUST mean “local embedding fallback is available” and MUST NOT be treated as `RELIABLY_ASSOCIATED`.
4. `association` semantics MUST remain unchanged by local feature success:
   - reliable cloud association stays reliable
   - candidate cloud association stays candidate
   - no-match stays unassociated unless a later stage explicitly changes it
5. Internal diagnostics for MVP-4 MUST stay outside the public `LocalFeature` shape. At minimum this boundary includes:
   - `costMs`
   - top-K classifications
   - input strategy
   - output tensor shape
   - failure reason
6. MVP-4 embedding serialization MUST be:
   - deterministic
   - reversible
   - version-aware through `featureSchemaVersion`
7. `featureSchemaVersion` MUST identify the public embedding contract, not only the upstream model file version.
8. `OUTDATED` for local features MUST be triggered when either:
   - `modelVersion` changes in a way that invalidates existing embeddings, or
   - `featureSchemaVersion` changes in a way that invalidates existing serialized/public contract expectations
9. A local-feature version change MUST NOT silently overwrite an old ready result without a detectable transition path. MVP-4 documentation and implementation MUST preserve the ability to mark the previous result `OUTDATED`.
10. YAMNet top-K outputs MAY be stored or displayed as internal diagnostics, but MUST NOT be surfaced as business `mood`/`genre` labels and MUST NOT participate in reliable-association decisions.

## Impact Scope

- Affects MVP-4 document Section 7, Section 10, queue/scheduler acceptance language, repository contract design, result semantics, and demo wording.
- Does not require a final vector-search or recommendation schema.
- Does not change cloud gateway contracts from MVP-1 through MVP-3.

## Alternatives Considered

- Put `costMs` into public `LocalFeature`: rejected because it is diagnostic, not business result.
- Expose top-K as user-facing genre/mood: rejected because MVP-4 explicitly avoids promising business labels.
- Treat local-feature readiness as a reliable association equivalent: rejected because it would break existing consumer semantics and overstate result confidence.
- Trigger `OUTDATED` only on schema change: rejected because model-version changes can also invalidate previously stored embeddings.

## Effective Date

- Effective on 2026-05-15.

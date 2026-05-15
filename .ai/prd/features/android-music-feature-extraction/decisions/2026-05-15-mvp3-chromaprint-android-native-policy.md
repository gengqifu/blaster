# Decision Log: MVP-3 Chromaprint Android native policy (2026-05-15)

## Background

MVP-3 needs a concrete audio fingerprint implementation path before engineering work starts. The MVP-3 plan selected a Chromaprint-compatible algorithm, but earlier wording still left dependency access, ABI scope, package impact, and license handling as open implementation-time decisions.

Leaving those choices open would make the native implementation, request contract, tests, and acceptance criteria unstable.

## Decisions

1. MVP-3 MUST keep `algorithm = chromaprint-compatible` as the first audio fingerprint algorithm identifier.
2. Android MUST integrate Chromaprint through JNI/NDK as a dynamically linked shared library.
3. The first MVP-3 native package MUST build and ship `arm64-v8a` only.
4. Chromaprint license handling MUST assume LGPL 2.1 obligations and include required license / notice materials with the delivered package.
5. The first implementation SHOULD use the bundled/permissive KissFFT path and MUST avoid FFTW3/GPL risk.
6. FFmpeg MUST NOT be introduced as a default MVP-3 dependency.
7. If Chromaprint native build or packaging fails, implementation MUST fix the native integration or update this decision and the MVP-3 plan before continuing.
8. Implementation MUST NOT fall back to compressed-file hashes, MD5, or fake fingerprints as a substitute for Chromaprint-compatible payloads.

## Impact Scope

- Affects `core` native build configuration, ABI filters, package contents, license notices, fingerprint extraction implementation, and MVP-3 acceptance tests.
- Does not require real cloud service integration.
- Does not change MVP-1/MVP-2 basic info matching semantics.

## Alternatives Considered

- Static linking Chromaprint: rejected for first MVP because it increases LGPL compliance complexity.
- Shipping all Android ABIs: rejected for first MVP because it increases package size and native validation cost before the business flow is proven.
- Introducing FFmpeg: rejected because MVP-3 explicitly uses Android system decoding and keeps FFmpeg out of the default dependency set.
- Using file hash as fingerprint fallback: rejected because it violates the audio fingerprint requirement and would produce misleading acceptance results.

## Effective Date

- Effective on 2026-05-15.

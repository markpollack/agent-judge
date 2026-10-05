# Jev adapter

`JevJudge` is a configured `Judge` using a reusable `JevRuntime` for TypeSafe's System One API,
using `io.github.gudcks0305:jev-typesafe:0.2.0`. It has no Spring or generative-model runtime.
The caller supplies a model, credential, HTTP client, explicit question/projection,
deadline, byte bounds and a protected `ArtifactCapture`. The HTTP client must disable
redirects and remains caller-owned. The direct endpoint
`https://api.typesafe.ai/v1/systemone` requires a pinned `jev-MAJOR.MINOR.PATCH` model.
The exact Vercel endpoint `https://ai-gateway.vercel.sh/typesafe/v1/systemone` instead
requires `typesafe-ai/jev`; an alias response leaves the underlying version unknown.
HTTP loopback endpoints with the corresponding paths are allowed for tests.
No environment credentials are read.

Configure `JevJudge.builder().runtime(runtime).requirement(actual).evidence(bundle).build()` and call `judge()` with no arguments. The lower `runtime.rendering(renderer)` is a typed `EvalRuntime<RequirementRequest<S,JevEvidence>,Judgment>` shared by JevJudge and the actual RFC2119/EARS Judges. It renders the actual native specification once per execution, sends it as `state.requirement`, and sends selected evidence as `state.evidence`. `state.requirementIdentity` carries the outer id, revision and source snapshot separately from the evidence's native specification digest. The Judgment owns its actual pure Requirement association and native invocation facts. Stale evidence digests are refused before HTTP and are never rebound to new text.

Workspace, agent output, execution-context metadata and the evidence manifest are not sent. Evidence
must carry its exact UTF-8 bundle digest, retained manifest reference, exact requirement
SHA-256 and a caller-declared sufficiency flag. The declaration cannot transfer to a
different requirement. The caller owns manifest contents, review and artifact availability;
the adapter verifies bindings and performs no retrieval or evidence compilation.

`JevQuestion.Noul` requires an explicitly complete binary formulation and sufficient
selected evidence. `P(true) < 0.5` means a supported violation, `> 0.5` satisfaction, and
a tie abstention. The binary distribution is retained; no separate confidence is invented.
`Choice` preserves its full configured domain, predictive distribution and separate native
confidence. Labels map explicitly to satisfaction, violation or insufficient evidence;
top ties across different meanings abstain. `Score` retains the fractional zero-based
ordinal expectation, complete level distribution and native confidence. It requires a
versioned single-dimension rubric, strictly monotonic semantic ranks/direction, explicit
projection boundaries and an independent approved review bound to `configurationDigest()`.
Missing, stale, unresolved or non-independent review rejects before HTTP. A review is an
external semantic assertion, not a proof a syntax checker can manufacture. Intermediate
quality values abstain; missing native answers are protocol errors.

All native signal identities have `:v1` versions. The result's first-class Finding,
Confidence, ProbabilityDistribution and source-backed `CalibrationClaim` pass through ordinary Jury
and retained Verdict reporting. The TypeSafe claim is a provider declaration, not local
empirical calibration. Requested/reported versions remain distinct in provenance revision
(`jev-adapter:2;jev-java:0.2.0;requested=...;reported=...;route=...;underlyingModelVersion=...`) and the protected trace.
The exact native response and serialized configuration are retained through artifact refs. Invocation records retain available completion, HTTP attempts, native usage, response refs and original thrown causes even when decoding fails. Causes are memory-only; portable facts use typed codes/text or exact bounded native data.

There is exactly one SDK HTTP attempt (`maxRetries(0)`). The SDK owns its request deadline;
interruption restores the flag, cancels the actual HTTP future and propagates cancellation. The observer captures the SDK's bounded intended request bytes before sending and publishes
bounded response bytes before SDK convenience decoding and forwards cancellation, avoiding derived
future cancellation loss. Bounds cover selected UTF-8 requirement/evidence, encoded request,
response and captured artifacts. Oversized responses are canceled, with no truncated body
presented as an exact response. Timeouts or cancellation cannot promise the remote server
did not already perform or bill an evaluation. Attempt counts describe observable HTTP
client invocations, not private socket behavior inside an injected client.

`ArtifactCapture` must retain exact copies under caller-controlled access and retention
limits, return matching SHA-256 references, and complete promptly. It receives no credential
or request headers. Ordinarily, provider bodies and error echoes are protected artifacts; if artifact capture fails after receiving a bounded body, the retained invocation keeps that body as `nativeResponseJson` rather than discarding the only original.
Result reasoning uses fixed diagnostic messages. Intended request bytes do not certify
transmission; connection failure or cancellation may prevent it. Response headers and request
IDs may be absent when the response did not complete. Capture failure is instrument ERROR. The trace
records request/response refs, requested/reported model, optional request ID, attempt count,
HTTP status (zero means no response), elapsed nanoseconds and cancellation. If these facts
would exceed the artifact byte bound, `requestedModel`, `reportedModel`,
`underlyingModelVersion` and `requestId` (when present) instead hold `ArtifactRef` objects.
Each uses the artifact kind `diagnostic-FIELD`, refers to exact UTF-8 string bytes
with no selector, and also appears in
provenance evidence. Ordinary traces retain inline strings. No field is truncated and
each referenced artifact remains subject to the same byte bound. Native provider metadata,
when present, is referenced by the response artifact with selector `/provider_metadata`.
Configuration, requirement, request and trace refs accompany bundle/manifest refs in
provenance evidence;
the native response has its dedicated provenance response ref. Request-level token usage
appears once on the judgment under `metadata.usage` (`inputTokens`, `outputTokens`) and in
the protected trace; known valid usage is retained even when a finding is malformed.
Absent/untrusted usage is omitted, never fabricated as zero. No pricing is computed.

For the Vercel route, valid `/provider_metadata/gateway/cost` is also exposed in
`metadata.usage` as `cost` (a finite nonnegative `Double`), `currency: "USD"`, and
`costSource: "vercel-gateway-reported:v1:/provider_metadata/gateway/cost"`.
The protected trace's `usage` object carries those same three fields alongside its
existing `input_tokens` and `output_tokens` fields. This is the gateway's reported
request charge, not a price derived from tokens; no `priceRuleId` is invented.
See the [gateway response format](https://vercel.com/docs/ai-gateway/sdks-and-apis/typesafe).

Numeric values and decimal strings are accepted, including an explicit zero.
Missing, null, negative, malformed or out-of-range costs remain absent; they do not
invalidate an otherwise valid assessment. A nonzero value that would underflow to
zero as a `Double` is also omitted. The exact decimal remains available in the
protected response bytes; the metadata value is a floating-point projection.
`marketCost` and other pricing fields are not substitutes for `cost`. Direct-provider
responses do not acquire gateway billing semantics. Validated request usage and cost
survive a protocol ERROR caused by a malformed assessment. They are retained once
at the producer judgment root, not copied onto individual checks.

Recording integrations must map these optional fields explicitly; retaining native
response bytes alone does not populate a recorder's typed cost field. Older saved
results remain unchanged. Reported gateway cost excludes evidence acquisition and
preparation and must not be presented as end-to-end evaluation cost.

Local tests exercise fake HTTP and the released SDK, not live provider correctness. The
committed independent rubric review covers a synthetic finite-clause fixture only. It does
not certify arbitrary real requirements, their evidence sufficiency or model behavior.

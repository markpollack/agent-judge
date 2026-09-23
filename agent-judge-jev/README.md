# Jev adapter

`JevJudge` implements the ordinary `Judge` contract with TypeSafe's direct System One API,
using `io.github.gudcks0305:jev-typesafe:0.2.0`. It has no Spring or generative-model runtime.
The caller supplies a pinned model, credential, HTTP client, explicit question/projection,
deadline, byte bounds and a protected `ArtifactCapture`. The HTTP client must disable
redirects and remains caller-owned. The official endpoint is
`https://api.typesafe.ai/v1/systemone`; HTTP loopback endpoints are allowed for tests.
No environment credentials are read.

The adapter sends exactly `context.goal()` as `state.requirement` and the text from a
`JevEvidence` value in `context.metadata().get(JevEvidence.CONTEXT_KEY)` as `state.evidence`.
Workspace, agent output, other metadata and the evidence manifest are not sent. Evidence
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

All native signal identities have `:v1` versions. The result's first-class assessment,
certainty, distribution and source-backed `CalibrationClaim` pass through ordinary Jury
and authoritative Interpretation. The TypeSafe claim is a provider declaration, not local
empirical calibration. Requested/reported versions remain distinct in provenance revision
(`jev-adapter:1;jev-java:0.2.0;requested=...;reported=...`) and the protected trace.
The exact native response and serialized configuration are retained through artifact refs.

There is exactly one SDK HTTP attempt (`maxRetries(0)`). The SDK owns its request deadline;
interruption restores the flag and cancels the actual HTTP future. The observer captures the SDK's bounded intended request bytes before sending and publishes
bounded response bytes before SDK convenience decoding and forwards cancellation, avoiding derived
future cancellation loss. Bounds cover selected UTF-8 requirement/evidence, encoded request,
response and captured artifacts. Oversized responses are canceled, with no truncated body
presented as an exact response. Timeouts or cancellation cannot promise the remote server
did not already perform or bill an evaluation. Attempt counts describe observable HTTP
client invocations, not private socket behavior inside an injected client.

`ArtifactCapture` must retain exact copies under caller-controlled access and retention
limits, return matching SHA-256 references, and complete promptly. It receives no credential
or request headers. Provider bodies, including error echoes, are protected artifacts only;
result diagnostics use fixed safe messages. Intended request bytes do not certify
transmission; connection failure or cancellation may prevent it. Response headers and request
IDs may be absent when the response did not complete. Capture failure is instrument ERROR. The trace
records request/response refs, requested/reported model, optional request ID, attempt count,
HTTP status (zero means no response), elapsed nanoseconds and cancellation. Configuration,
requirement, request and trace refs accompany bundle/manifest refs in provenance evidence;
the native response has its dedicated provenance response ref. Request-level token usage
appears once on the judgment under `metadata.usage` (`inputTokens`, `outputTokens`) and in
the protected trace; known valid usage is retained even when an assessment is malformed.
Absent/untrusted usage is omitted, never fabricated as zero. No pricing is computed.

Local tests exercise fake HTTP and the released SDK, not live provider correctness. The
committed independent rubric review covers a synthetic finite-clause fixture only. It does
not certify arbitrary real requirements, their evidence sufficiency or model behavior.

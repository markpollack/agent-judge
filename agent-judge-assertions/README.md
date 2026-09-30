# Requirement evaluation and retained assertions

`RequirementAssertions` applies an application reliance rule to a completed Judge/Jury
result, retaining the original Verdict and its authoritative Interpretation.

```java
var assertions = RequirementAssertions.relyingOnJudgment();
var result = assertions.evaluate(READY_REQUIREMENT, READY_RESPONSE_JUDGE, "READY", null);
RequirementAssertions.requireSatisfied(result);
```

`READY_RESPONSE_JUDGE` can be an ordinary `Judge<String>`. A Judge requiring the native
specification uses `Judge<RequirementEvidence<S,E>>` and `evaluateRequirement(...)`.
A configured `Jury<E>` uses the same evaluate entry. Final acceptance does not reconfigure
Jury internals. `null` in the final argument selects the configured default; a policy lambda
there selects EXPLICIT acceptance. `new RequirementAssertions(null)` requires that override
and refuses missing policy before invoking the Judge.

AcceptancePolicy has one method, `AcceptanceDecision decide(Judgment judgment)`. It needs
no audit identity. `RELY` preserves positive or negative conclusions; `ABSTAIN` and
`ESCALATE` withhold reliance. An optional `Policies.recorded(reference, policy)` decorator
records application-supplied provenance.

`AssertionResult` retains Requirement, Verdict, Interpretation and `AcceptanceExecution`.
The execution records final policy source, optional identity, and application or bypass.
A policy failure is separate from producer error. A retained result constructor validates
its interpretation and required bypass without running a policy. Repeated calls to
`requireSatisfied` invoke no Judge, provider or policy.

A supported SATISFIED interpretation and final RELY pass. Supported VIOLATED with RELY
throws `RequirementAssertionError.Rejected`; unresolved/withheld conclusions are
Inconclusive; instrument failures are InstrumentFailure. Non-applicability and unsupported
reading have their own subclasses. Every failure retains the complete result.

The [AssertJ module](../agent-judge-assertj/README.md) provides the fluent grammar. The
[wire contract](../portable-results-v3.md) describes V3 retention. Tests use local fixtures;
the manually invoked conference runners are separate and require explicit credentials.
Historical reviewed fixture bytes remain unchanged; their old policy vocabulary is
translated only in the test fixture loader.

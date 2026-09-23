# Ordinary semantic assertions

Configure an immutable `SemanticAssertions` with an explicit requirement-to-`Judge` route and
`PolicyBinding`. A named `Requirement` may bind an override with `.under(policy)` before the eager
terminal operation. String requirements need a configured default policy; no threshold is inferred.

A fixture can expose a one-line helper:

```java
private SemanticAssertion assertThat(JudgmentContext evidence) {
    return assertions.assertThat(evidence);
}

@Test
void architectureIsPreserved() {
    assertThat(evidence).satisfies("Repository locks are acquired in the required order");
}
```

Resolve evidence, provider resources and policy during caller setup. The exact context goal must
match the requirement text. The facade does not rebind evidence to a new goal, load resources,
expand a repository, or manage credentials. Caller-supplied routes, judges, policies and metadata
payloads must be immutable/thread-safe for concurrent tests. String identities are content-addressed;
fixtures may explicitly map a reviewed text alias to a named source requirement.

Evaluation uses `PolicyJudges`, normal one-seat `SimpleJury`, `Verdict`, and `Verdicts.interpret`.
Only supported acceptance passes. `SemanticAssertionError` subclasses distinguish rejected,
inconclusive (including exhausted escalation), instrument failure, unexpected non-applicability,
and unsupported reading. They retain `AssertionResult`, including its authoritative interpretation.
No result is converted into a subject failure or JUnit skip. `evaluate` exposes the same evaluation
without asserting for audit consumers. Its runtime `Requirement` may contain a policy function;
serialize requirement identity/text and resolved policy reference/source separately from existing
portable Verdict/Interpretation contracts.

The conference tests use exact retained RULE-4 and AC8 source bundles. The short lock-order text
aliases only the scoped global-order clause. The independent source/configuration review and exact
binding live under `src/test/resources/assertions/v1`; manifests, reviews and expected outcomes are
excluded from provider inputs. Loopback responses are **fake** and establish assertion plumbing,
not live model detection, semantic accuracy, calibration, deadlocks or mutation kills. The Choice
primitive and explicit use-assessment policy are provisional demonstration configuration.

`ConferenceAssertionRun` is a test-source command-line harness, never a normal JUnit test. After
separate approval of evidence/configuration, account, two calls, timeout and spend, build the test
classpath and explicitly invoke its `main` with `--live-two-calls NEW_PROTECTED_OUTPUT_DIRECTORY`.
It requires an interactive credential prompt; programmatic callers may explicitly supply the key to
`run`. No environment variable or credential file enables it. Each of the two reviewed cases gets
at most one request to the official endpoint, with the frozen pinned model and 30-second SDK HTTP
deadline, no retries. Both actual outcomes and exact requests, native responses and result artifacts
are retained. Artifact storage must complete promptly; the SDK deadline is not an end-to-end storage
budget, and cancellation does not prove a server did not process or bill a request.

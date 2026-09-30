# Ordinary semantic assertions

Configure an immutable `SemanticAssertions` with an explicit requirement-to-`Judge<JudgmentContext>` route and
`PolicyBinding`. The judge makes a finding; the application policy decides whether to use it.
`USE_ASSESSMENT` accepts the finding, including a negative one. It does not mean the subject passes.

The optional [AssertJ module](../agent-judge-assertj/README.md) also provides the typed,
requirement-first progression. This evidence-first facade and its retained-result terminal remain
available and use the same separation between internal policy and final application policy.

## Configure once, then assert

This complete JUnit fixture uses a local deterministic judge and needs no provider or credentials.
The [executable caller examples](src/test/java/io/github/markpollack/judge/assertions/usage/SemanticAssertionUsageTest.java)
also verify access from outside the assertion package and count route, judge and policy calls.

```java
import java.nio.charset.StandardCharsets;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.assertions.AssertionResult;
import io.github.markpollack.judge.result.PolicyBinding;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.assertions.SemanticAssertionError;
import io.github.markpollack.judge.assertions.SemanticAssertions;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Acceptance;
import io.github.markpollack.judge.result.AcceptanceAction;
import io.github.markpollack.judge.result.AcceptancePolicy;
import io.github.markpollack.judge.result.ArtifactRef;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.PolicyRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ResponseAssertionsTest {
    private static final Requirement<String> READY = Requirement.text(
        "response-ready", "1", "The response is exactly READY");

    private final PolicyBinding defaultPolicy = policy(
        "local-response", "use exact-response finding",
        raw -> new Acceptance(AcceptanceAction.USE_ASSESSMENT,
            "Use the deterministic exact-response finding"));

    private final PolicyBinding criticalPolicy = policy(
        "critical-response", "require independent confirmation",
        raw -> new Acceptance(AcceptanceAction.ESCALATE,
            "Independent confirmation is required before acting; none is retained"));

    private final Judge<JudgmentContext> judge = context ->
        "READY".equals(context.agentOutput().orElse(""))
            ? Judgment.pass("Response is exactly READY")
            : Judgment.fail("Response differs from READY");

    private final SemanticAssertions assertions = new SemanticAssertions(requirement -> {
        if (!READY.text().equals(requirement.text())) {
            throw new IllegalArgumentException("No judge configured for this requirement");
        }
        return judge;
    }, defaultPolicy);

    private final JudgmentContext evidence = JudgmentContext.builder()
        .goal(READY.text()).agentOutput("READY").build();

    @Test
    void responseIsReady() {
        assertions.assertThat(evidence).satisfies(READY);
    }

    @Test
    void criticalRequirementNeedsIndependentConfirmation() {
        assertThrows(SemanticAssertionError.Inconclusive.class,
            () -> assertions.assertThat(evidence).satisfies(READY.under(criticalPolicy)));
    }

    @Test
    void retainAndAssertOneEvaluation() {
        AssertionResult result = assertions.evaluate(evidence, READY);
        // Inspect or retain result before asserting this same evaluation.
        SemanticAssertions.requireSatisfied(result);
    }

    private static PolicyBinding policy(String id, String configuration,
                                        AcceptancePolicy decision) {
        String digest = ArtifactRef.ofBytes(id,
            configuration.getBytes(StandardCharsets.UTF_8), null).sha256();
        return new PolicyBinding(new PolicyRef(id, "1", digest), decision);
    }
}
```

`satisfies` is an eager `void` terminal: it evaluates and asserts immediately. Bind an override on
`Requirement.under(policy)` before that call; it returns a new requirement and leaves the original
unchanged. String requirements, such as `.satisfies("The response is exactly READY")`, use the
configured default policy. No threshold is inferred. A fixture may expose its own one-line
`assertThat(evidence)` method delegating to the configured facade.

`evaluate` invokes the configured judge through a normal one-seat Jury and then applies the
selected final policy where applicable. NOT_ASSESSED, NOT_APPLICABLE and unsupported readings
bypass final policy; raw aggregate ERROR/N/A also bypasses it. **It does not assert**.
Calling it after `satisfies`, or calling `satisfies` after it, evaluates again and may incur another
provider call. To retain and assert one evaluation, use `evaluate` followed by the public static
`SemanticAssertions.requireSatisfied(result)`. This terminal reads the retained authoritative
Interpretation and separate final application decision; it invokes no route, judge, provider or policy function, and does not mutate the
result or replace its policy. Calling it again asserts the same facts.

Resolve evidence, provider resources and policy during caller setup. The exact context goal must
match the requirement text. The facade does not rebind evidence to a new goal, load resources,
expand a repository, or manage credentials. Caller-supplied routes, judges, policies and metadata
payloads must be immutable/thread-safe for concurrent tests. String identities are content-addressed;
fixtures may explicitly map a reviewed text alias to a named source requirement.

## Findings, policy and assertion outcomes

The path remains `Judge → Judgment → Jury → Verdict → Interpretation`. Evaluation invokes the
configured Judge unchanged in a normal one-seat `SimpleJury` and reads its Verdict through
`Verdicts.interpret`. Internal policies configured by the caller with `PolicyJudges.apply(...)`
remain part of that evaluation. The assertion's selected policy runs afterwards, with its result
recorded separately in `ApplicationDecision`; it never replaces an internal application.

Internal policy and final application policy use the same `AcceptancePolicy` abstraction in
different scopes. Both are application-owned. The final callback receives the raw aggregate
Judgment view, without an earlier policy application; it receives no seat roster. Its output
cannot repair the retained determination. Satisfaction requires SUPPORTED ACCEPTED plus a
successful final USE_ASSESSMENT application.

| Evaluation and final policy | Retained authoritative reading | Assertion outcome |
|---|---|---|
| PASS, USE_ASSESSMENT | ACCEPTED | Passes |
| FAIL, USE_ASSESSMENT | REJECTED | `SemanticAssertionError.Rejected` |
| PASS or FAIL, final ABSTAIN or ESCALATE | ACCEPTED or REJECTED, unchanged | `SemanticAssertionError.Inconclusive` |
| Producer ABSTAIN, any valid policy action | UNDECIDED | `SemanticAssertionError.Inconclusive` |
| Internal withholding or escalation, final USE | UNDECIDED | `SemanticAssertionError.Inconclusive` |
| Judge, internal policy or invocation failure | NOT_ASSESSED; final policy bypassed | `SemanticAssertionError.InstrumentFailure` |
| Final application policy failure | Original reading unchanged | `SemanticAssertionError.InstrumentFailure` |
| Declared NOT_APPLICABLE | NOT_APPLICABLE | `SemanticAssertionError.NotApplicable` |
| Unsupported/contradicted reading, or absent reading | No usable supported determination | `SemanticAssertionError.UnsupportedReading` |

The critical policy above deliberately requires independent confirmation that the single judge
does not supply; it sets no numerical threshold. Its ESCALATE action withholds the finding and
requests follow-up. This facade does not run another judge or perform human escalation. The original
PASS and the original operational status remain in the Verdict; ESCALATE is recorded separately
as the final application action. Undeclared non-applicability
is contained as an instrument failure. No uncertain/error outcome becomes a subject FAIL or JUnit skip.

For a deliberately negative fixture, assert the specific error:

```java
var negativeEvidence = JudgmentContext.builder()
    .goal(READY.text()).agentOutput("NOT READY").build();
var violation = assertThrows(SemanticAssertionError.Rejected.class,
    () -> assertions.assertThat(negativeEvidence).satisfies(READY));
AssertionResult retained = violation.result();
```

This succeeds only for a supported rejection. ABSTAIN, escalation and instrument failures do not
count as a demonstrated violation. Errors expose `category()`, `result()` and `interpretation()`;
failure messages briefly connect requirement, producer assessment, named support metric, internal
policy, final application policy, operational result and Interpretation. Long fields and artifact identifiers are abbreviated;
the complete result and interpretation summary remain available through those accessors.
SUPPORTED describes structural coherence of the retained reading, not model confidence or proof
that a model's finding is correct. Choice confidence and its distribution keep their own metric
identities; Noul's `p(true)` is a different probability signal, not Choice confidence. A protocol
ERROR has no valid assessment/support; a policy failure retains the original producer facts.

## Retained and stored results

`AssertionResult` is a runtime value, not another wire contract. Its native `Requirement` may contain
an associated policy function. Retain the requirement's identity/revision/specification/source and
the separate `ApplicationDecision` alongside the existing portable Verdict/Interpretation contracts;
do not serialize the function. The final decision holds the resolved reference/source plus an
application action/reason or failure, or an explicit bypass.

Where a supported version-2 storage path reconstructs a typed `Verdict` and the original requirement
and final application facts, `new AssertionResult(requirement, applicationDecision, verdict)` reconstructs
the authoritative reading without evaluation. The constructor checks retained policy/source
coherence; supply the facts actually recorded. Then `SemanticAssertions.requireSatisfied(result)`
asserts that reading without a new judge or policy call. This does not establish a new persistence
format or general historical deserialization support.

`PolicySource.ASSOCIATED` requires a requirement association with the exact resolved `PolicyRef`
(including its configuration digest); `DEFAULT` requires no association. `EXPLICIT` records a caller
override independently of any association. The final application must name its resolved policy;
an internal root or seat application can name a different one. Internal policy applications and
full Jury composition are retained unchanged. The constructor checks the final decision/bypass
against the original authoritative Interpretation; a reference alone does not prove policy ran.
Unsupported Verdicts remain eligible for `UnsupportedReading` diagnostics.
The caller remains responsible for associating the correct requirement/evidence with the Verdict;
these consistency checks cannot authenticate that pairing.

For unknown or legacy stored shapes, use the tolerant `Verdicts.interpret(Map<String, Object>)`
boundary from [portable results v2](../portable-results-v2.md). Do not coerce historical values into
live v2 types or invent absent requirement/policy facts. Ordinary JUnit can assert the returned
reading directly, checking support first:

```java
var reading = Verdicts.interpret(storedVerdictMap);
org.junit.jupiter.api.Assertions.assertEquals(
    ReadingSupport.SUPPORTED, reading.readingSupport(), reading.summary());
org.junit.jupiter.api.Assertions.assertEquals(
    VerdictReading.ACCEPTED, reading.reading(), reading.summary());
```

Here `Verdicts`, `ReadingSupport` and `VerdictReading` are from
`io.github.markpollack.judge.jury.interpretation`. Require REJECTED instead for an intentionally
negative stored case. Check the requirement's own verdict, not a separate agreement-scoring verdict.

Applying a different final policy is a separate explicit operation:
`AssertionResult.applyPolicy(requirement, binding, source, verdict)` retains the complete original
Verdict and creates a separate final decision, without a Judge call. Keep both decisions when comparing
policies; `requireSatisfied` never applies a policy implicitly. Core `Policies.apply` remains useful
for explicitly producing a new policy-bearing Judgment; do not insert that replacement into a retained
Jury and claim that its original decision/routing is unchanged.

Compatibility: eager `void satisfies(...)` timing and the public retained-result terminal remain.
Typed Judge/Requirement migration and the separate final-decision constructor require a coordinated
recompile; see the [migration guide](../MIGRATION_TYPED_EVIDENCE.md). Failure-message text has changed;
use structured categories and result accessors rather than parsing messages. Production dependencies
remain core, OpenTest4J and JSpecify; JUnit, AssertJ and Jev are test-scoped.

## Additional fixtures

The conference tests use exact retained RULE-4 and AC8 source bundles. The short lock-order text
aliases only the scoped global-order clause. The independent source/configuration review and exact
binding live under `src/test/resources/assertions/v1`; manifests, reviews and expected outcomes are
excluded from provider inputs. Loopback responses are **fake** and establish assertion plumbing,
not live model detection, semantic accuracy, calibration, deadlocks or mutation kills. The Choice
primitive and explicit use-assessment policy are provisional demonstration configuration.

`ConferenceAssertionRun` is a test-source command-line harness, never a normal JUnit test. After
separate approval of evidence/configuration, account, two calls, timeout and spend, build the test
classpath and explicitly invoke its `main` with `--live-two-calls NEW_PROTECTED_OUTPUT_DIRECTORY`.
That direct-route entry requires an interactive credential prompt; programmatic callers may
explicitly supply the key to `run`. The separate explicit
`--live-two-calls-vercel-env NEW_PROTECTED_OUTPUT_DIRECTORY` entry reads `AI_GATEWAY_API_KEY`
and uses the reviewed Vercel endpoint/model alias. Setting the variable alone does not run the
harness; no credential file is read. Each of the two reviewed cases gets at most one request
to the selected endpoint, with its frozen model configuration and 30-second SDK HTTP deadline,
no retries. Both actual outcomes and exact requests, native responses and result artifacts
are retained. Artifact storage must complete promptly; the SDK deadline is not an end-to-end storage
budget, and cancellation does not prove a server did not process or bill a request.

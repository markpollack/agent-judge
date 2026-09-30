# Requirement assertions with AssertJ

Agent Eval's optional `agent-judge-assertj` module adds typed requirement assertions to ordinary
JUnit tests. `agent-judge-core` does not depend on AssertJ. These APIs are available in the
`0.18.0-SNAPSHOT` source line; use the same version for every Agent Judge module.

> **Here is the requirement. Here is who judges it. Here is the evidence. Here is how my application is willing to act on the judgment. Now establish satisfaction.**

The five parts are Requirement, Judge/Jury, Evidence, AcceptancePolicy and Satisfaction.
The examples below run without a provider or credentials in
[`TutorialExamplesTest`](src/test/java/io/github/markpollack/judge/assertj/usage/TutorialExamplesTest.java).

## Start with a requirement

```java
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.requirement.Requirement;
import io.github.markpollack.judge.requirement.RequirementEvidence;
import io.github.markpollack.judge.result.Judgment;

import static io.github.markpollack.judge.assertj.Assertions.assertThat;

Requirement<String> requirement = Requirement.text(
    "response-ready", "1", "The response is exactly READY");

Judge<RequirementEvidence<Requirement<String>, String>> judge = input ->
    "READY".equals(input.evidence())
        ? Judgment.pass("Response is exactly READY")
        : Judgment.fail("Response differs from READY");

assertThat(requirement)
    .judgedBy(judge)
    .withEvidence("READY")
    .isSatisfied();
```

The static entry supplies the immutable, versioned and content-addressed
`agent-eval.assertion-default` policy, revision `1`: `USE_ASSESSMENT`, with no confidence
threshold. It asks the application to use the completed evaluation's determination. A negative
determination therefore still fails. The default is explicit in the static entry's contract;
there is no mutable process-wide setting.

`judgedBy(...)` fixes the evidence type: passing a number to this judge's `withEvidence(...)`
does not compile. The next stage exposes `isSatisfied()` only after evidence is supplied.
The chain keeps the original Requirement and Evidence objects and invokes evaluation at the
terminal. Calling the terminal twice performs two evaluations. Java permits an unused chain;
forgetting the terminal performs no evaluation and no assertion.

`Requirement<S>` retains the complete native specification, revision and source identity.
`S` can be an RFC2119 constraint or EARS criterion without flattening its structure. The caller
supplies a stable specification snapshot for evaluation and retention; a generic record cannot
deep-copy an arbitrary object. Provider rendering belongs to the evaluator and does not replace
the original requirement's identity. A general `Judge<String>` remains useful outside this
requirement-oriented API; not every Judge needs a Requirement.

## Configure the application's consequence

Use `Assertions.using(...)` to configure a reusable application entry:

```java
var applicationAssertions = Assertions.using(new RequirementAssertions(applicationDefault));

applicationAssertions.assertThat(requirement)
    .judgedBy(judge)
    .withEvidence(evidence)
    .isSatisfied();
```

Here `applicationDefault` is a `PolicyBinding`, pairing a `PolicyRef` with an
`AcceptancePolicy`. A reference includes an ID, revision and SHA-256 configuration digest.
Keep immutable policy configuration and use its exact bytes for that digest.
`Assertions` is from `io.github.markpollack.judge.assertj`; `RequirementAssertions` is from
`io.github.markpollack.judge.assertions`; policy values are from
`io.github.markpollack.judge.result`.

An explicit application override is the fourth part of the progression:

```java
assertThat(requirement)
    .judgedBy(judge)
    .withEvidence(evidence)
    .withAcceptancePolicy(criticalPolicy)
    .isSatisfied();
```

For example, configure an explicit escalation policy without a numerical threshold:

```java
String configuration = "always ESCALATE; require independent confirmation";
String digest = ArtifactRef.ofBytes("policy",
    configuration.getBytes(StandardCharsets.UTF_8), null).sha256();
PolicyBinding criticalPolicy = new PolicyBinding(
    new PolicyRef("critical-response", "1", digest),
    raw -> new Acceptance(AcceptanceAction.ESCALATE,
        "Independent confirmation is required before acting"));
```

`StandardCharsets` is from `java.nio.charset`; the other types are from the result package.
If the evaluation is ACCEPTED, its Verdict and Interpretation remain ACCEPTED. The separate final
application decision records ESCALATE, so the assertion is inconclusive. It creates no human task,
calls no provider and enters no additional Jury tier.

Resolution is **EXPLICIT override → ASSOCIATED requirement binding → DEFAULT configuration**.
`requirement.under(policy)` creates the explicit application association without changing its
native specification or source. A missing policy fails before evaluation. The result records the
resolved reference and source honestly; an override is never reported as requirement-associated.
Neither an RFC2119 `MUST` nor an EARS form implies a policy threshold.

## A Jury keeps its own policies

Internal policy and final application policy use the same `AcceptancePolicy` abstraction, with
different scopes. Both are application-owned:

```text
Judge/Jury internal policy → participates in producing the Verdict
Final application policy  → decides what the application does with that Verdict
```

Build internal seats explicitly with `PolicyJudges.apply(judge, reference, policy)` when their
policy must influence voting or cascade routing. A Jury can deliberately assign different
policies to different seats or tiers. Passing that Jury to an assertion preserves them all:

```java
var jury = SimpleJury.<RequirementEvidence<Requirement<String>, String>>builder()
    .judge(PolicyJudges.apply(judge, internalPolicy.reference(), internalPolicy.policy()))
    .votingStrategy(new AllMustPassStrategy())
    .parallel(false)
    .build();

assertThat(requirement)
    .judgedBy(jury)
    .withEvidence(evidence)
    .withAcceptancePolicy(criticalPolicy)
    .isSatisfied();
```

The supplied Jury executes unchanged. DEFAULT, ASSOCIATED and EXPLICIT final policies never
replace its seats, tier policies, quorum, trust, voting, reduction or routing configuration.
The same separation applies when supplying a single Judge: its existing policy application
survives the assertion's one-seat containment path.

For an assessment cascade, explicitly configure internal policies on each one-seat `SimpleJury`.
`STOP_ON_USABLE_ASSESSMENT` advances only on internal ESCALATE; its final tier has the same
one-seat guard. A final application policy cannot supply a missing internal policy or repair
an invalid tier. An internally decisive first tier still prevents later calls even when the
final application policy requests ESCALATE. The executable example verifies this with different
first-tier and fallback policies and a fallback invocation counter.

Retained results contain the original individual Judgments, producer facts and internal policy
applications, aggregate, seats and identities, attempts, disagreement, complete Verdict and
authoritative Interpretation. `ConsensusStrategy` is not a guarantee of independent confirmation:
PASS + FAIL is undecided, while PASS + ABSTAIN can be accepted under its configured semantics.
Explicit population/quorum requirements belong in Jury configuration.

## Read the determination and the application decision separately

Satisfaction requires **SUPPORTED + ACCEPTED retained Interpretation + a successful final
USE_ASSESSMENT application**. Supported means that the retained structure supports the reading;
it is not proof of evaluator accuracy or calibration.

| Retained determination | Final application | Assertion result |
|---|---|---|
| ACCEPTED | USE_ASSESSMENT | Satisfied |
| REJECTED | USE_ASSESSMENT | Rejected |
| UNDECIDED | USE_ASSESSMENT | Inconclusive |
| ACCEPTED, REJECTED or UNDECIDED | ABSTAIN / ESCALATE | Inconclusive; retained determination unchanged |
| Eligible determination | Policy failure | Application instrument failure; evaluation retained |
| NOT_ASSESSED | Bypassed | Evaluation instrument failure |
| NOT_APPLICABLE | Bypassed | Not applicable |
| Unsupported reading | Bypassed | Unsupported reading |

The callback receives the raw aggregate Judgment view, with no earlier policy application.
It can inspect assessment, named support and provenance; it does not receive a seat roster.
Final USE cannot revive a positive assessment that the Jury withheld internally. The original
Interpretation remains the eligibility guard. Final application cannot turn REJECTED, UNDECIDED,
NOT_ASSESSED, NOT_APPLICABLE or unsupported evaluation into satisfaction. Raw aggregate ERROR/N/A
also bypasses the callback even if a custom supported reduction has another reading.

NOT_APPLICABLE means a declared exclusion. It differs from insufficient evidence (UNDECIDED)
and a violation (REJECTED). Judges must declare exclusion capability; an undeclared exclusion is
contained as an instrument failure. Decide applicability explicitly before a bounded evaluator
when possible; when an evaluator judges applicability, give it an explicit protocol and retain
that result rather than mapping exclusion to insufficiency.

For evaluation once and repeated inspection, use the direct API:

```java
AssertionResult result = RequirementAssertions.usingAssessment()
    .evaluate(requirement, jury, evidence, null);

var determination = result.interpretation();
var application = result.applicationDecision();
SemanticAssertions.requireSatisfied(result);
```

`requireSatisfied` invokes no Judge, provider or policy. The complete original Verdict is at
`result.verdict()`. The separate `applicationDecision()` retains the policy reference, source,
action/reason or failure, or an explicit bypass. Reconstructing an `AssertionResult` from retained
values also executes no callback. To apply a different final policy to an existing Verdict,
use the explicit `AssertionResult.applyPolicy(...)` operation and retain its new decision.

AssertJ descriptions work through `.as("response contract")` before `.judgedBy(...)`.
On failure, the AssertJ error's cause is a `SemanticAssertionError` with a structured category,
the full result and the retained Interpretation. Final withholding changes the assertion's
category without manufacturing a new Interpretation.

See the [migration guide](../MIGRATION_TYPED_EVIDENCE.md) for the coordinated generic API break,
and the [ordinary assertion guide](../agent-judge-assertions/README.md) for the evidence-first
facade and stored-result boundary.

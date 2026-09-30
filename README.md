# Agent Eval

Evaluate typed evidence, retain complete judgments and composition history, and assert what the result establishes. Java 21; current source line **0.18.0-SNAPSHOT**.

Start with deterministic assertions when ordinary code can establish the property:

```java
import static org.assertj.core.api.Assertions.assertThat;

assertThat(2 + 2).isEqualTo(4);
```

An ordinary `Judge<E>` checks evidence and returns a `Judgment`. It needs no Requirement object, Finding, or policy:

```java
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.judgment.Judgment;
import static io.github.markpollack.judge.assertj.Assertions.assertThatEvidence;

Judge<Integer> positive = value -> value > 0
    ? Judgment.pass("positive") : Judgment.fail("not positive");
assertThatEvidence(42).judgedBy(positive).isPassed();
```

## Supply the requirement explicitly

Now consider a response such as “Two pairs make a group of four.” A requirement-aware evaluator receives the specification and evidence as separate typed arguments on each invocation:

```java
import io.github.markpollack.judge.RequirementJudge;
import io.github.markpollack.judge.requirement.Requirement;
import static io.github.markpollack.judge.assertj.Assertions.assertThat;

var requirement = Requirement.text("answer", "1", "4");
RequirementJudge<String, String> exactAnswer = (actual, response) ->
    actual.specification().equals(response)
        ? Judgment.pass("matches") : Judgment.fail("differs");

assertThat(requirement).judgedBy(exactAnswer).withEvidence("4").isSatisfied();
```

This exact-match judge is deterministic. A model-backed adapter can assess paraphrases. It must render the actual supplied requirement, choose a response protocol, and preserve uncertainty:

```java
import java.util.Map;
import io.github.markpollack.judge.ai.ModelBackedJudge;
import io.github.markpollack.judge.ai.JudgmentClassifiers;
import io.github.markpollack.judge.ai.prompt.JudgePromptTemplate;

// judgeModel is a JudgeModel configured by the application.
RequirementJudge<String, String> meaning = (actual, response) ->
    ModelBackedJudge.<String>builder()
        .name("meaning")
        .promptTemplate(JudgePromptTemplate.fromString("meaning", """
            Requirement: {{requirement}}
            Response: {{response}}
            Reply satisfied, violated, or unknown.
            """))
        .variables(text -> Map.of("requirement", actual.specification(), "response", text))
        .model(judgeModel)
        .judgmentClassifier(JudgmentClassifiers.passFail("satisfied", "violated"))
        .build().judge(response);

var arithmetic = Requirement.text("arithmetic", "1", "The response communicates that 2 + 2 equals 4.");
assertThat(arithmetic).judgedBy(meaning)
    .withEvidence("Two pairs make a group of four.").isSatisfied();
```

Unrecognized answers abstain; instrument errors remain errors. This example explains input flow, not model accuracy. The [executable examples](agent-judge-assertj/src/test/java/io/github/markpollack/judge/assertj/AssertJApiExperienceTest.java) use local model stubs.

Native specifications need not be prose: `Requirement<ApiLimit>` can carry a numeric limit and use `RequirementJudge<ApiLimit, Integer>`. Requirements contain no evaluators, voting rules, or policies. An ordinary Judge cannot enter the requirement-first assertion path and silently discard its requirement.

## Combine opinions, then constituents

A `Jury<E>` returns a complete `Verdict`; `RequirementJury<S,E>` additionally receives the actual requirement. Multiple opinions about one requirement use a voting Jury:

```java
import java.util.List;
import io.github.markpollack.judge.jury.*;

var security = Requirement.text("security", "1", "No critical vulnerabilities");
RequirementJury<String, String> securityJury = RequirementJuries.voting(
    new MajorityVotingStrategy(), List.of(
        (actual, evidence) -> Judgment.pass("scanner A"),
        (actual, evidence) -> Judgment.pass("scanner B"),
        (actual, evidence) -> Judgment.fail("dissent")));
assertThat(security).judgedBy(securityJury).withEvidence("scan").isSatisfied();
```

These opinions pass by majority while retaining the dissent. Different requirements belong to a parent's pure specification. An all-of parent fails if any constituent fails; an unresolved or required non-applicable child prevents PASS.

```java
import io.github.markpollack.judge.requirement.AllOf;

var compatibility = Requirement.text("compatibility", "1", "Existing API remains compatible");
var observability = Requirement.text("observability", "1", "Required metrics exist");
var readinessSource = Requirement.text("readiness-source", "1",
    "security AND compatibility AND observability").source();
var readiness = new Requirement<>("readiness", "1", "Ready to deploy",
    new AllOf(List.of(security, compatibility, observability)), readinessSource);
RequirementJudge<String, String> compatible = (actual, evidence) -> Judgment.pass("compatible");
RequirementJudge<String, String> observable = (actual, evidence) -> Judgment.fail("metrics missing");

var prepared = Assignments.<String>forRequirement(readiness)
    .jury(security, securityJury)
    .judge(compatibility, compatible)
    .judge(observability, observable)
    .validate();
Verdict verdict = prepared.vote("release evidence");
assertThat(verdict).hasConclusion(Verdict.Conclusion.FAIL);
```

The parent fails despite the passing security majority. Each child retains its own Requirement and complete Verdict. Nest another all-of evaluator through `.jury(...)`; do not flatten child opinions into one majority.

Assignments reject duplicate or unrelated references and null evaluators. Child IDs must satisfy composite stage-name constraints, and a direct roster must fit the 32-attempt bound. `validate()` checks the entire roster before any selector or evaluator runs and freezes the mapping. References match a unique child ID plus its complete native Requirement value; execution uses the parent's instance. Shared String types cannot prove semantic identity. Typed selectors such as `.judge(compatibility, ReleaseEvidence::apiDiff, compatibilityJudge)` select narrower evidence explicitly; selector failures remain on the child's attempt. See the [mixed native/selector and nested examples](agent-judge-assertj/src/test/java/io/github/markpollack/judge/assertj/AssertJApiExperienceTest.java) and [compiler checks](agent-judge-assertj/src/test/java/io/github/markpollack/judge/assertj/CompileGrammarTest.java).

## Decide whether to rely on the complete Verdict

`verdict.conclusion()` derives `PASS`, `FAIL`, `INCONCLUSIVE`, or `NOT_APPLICABLE` from retained facts. A cascade can establish a rejection while also retaining a collective ERROR and failed stage. A Policy sees that entire usable record:

```java
import io.github.markpollack.judge.policy.*;
import io.github.markpollack.judge.evaluation.*;

Policy rely = complete -> new PolicyDecision(PolicyAction.RELY, "Trust the retained conclusion");
EvaluationResult result = Evaluations.apply(verdict, rely);
assertThat(result).hasConclusion(Verdict.Conclusion.FAIL);
```

`RELY` trusts negative conclusions too. `ABSTAIN` and `ESCALATE` withhold reliance; neither changes facts nor restarts composition. No identity or calibration claim is required. Voting uses `ErrorHandling`, `ExclusionHandling`, and `TieBreakRule`; cascades use `RoutingRule`.

`EvaluationResult(Verdict, PolicyResult)` is in core and works without AssertJ. Its policy result is exactly `NotRequested`, `Decided(originalDecision)`, or `Failed(originalThrowable)`. Every requested policy runs on every usable conclusion, including all-attempts-failed. A null return is a failed policy contract. Unusable configuration or stored records throw before policy runs. Fatal errors and cancellation escape.

PASS without a requested policy can establish satisfaction. PASS with a requested policy that failed cannot. `isSatisfied()` also requires an associated Requirement; `isPassed()` checks the domain conclusion alone.

## Retain, report, and reopen

```java
import io.github.markpollack.judge.reporting.VerdictReport;
import io.github.markpollack.judge.serialization.VerdictCodec;

var report = VerdictReport.of(result.verdict());
System.out.println(report.summary());
String saved = new VerdictCodec().write(result);
EvaluationResult reopened = new VerdictCodec().readEvaluation(saved);
assertThat(reopened).hasConclusion(Verdict.Conclusion.FAIL);
```

Reports and retained assertions invoke nothing. Repeated terminals on the same fluent stage reuse its result. Judgments retain optional Boolean, Numeric, and Category Findings together; confidence, probabilities, and normalized scores retain their distinct meanings. Seats record participation separately from producer outcomes, and attempts retain complete child records and routing decisions.

[Schema version 4](portable-results-v4.md) stores policy results and typed requirement associations. Register a stable codec name for each additional native specification class. V2/V3 typed results are explicitly refused; the separate diagnostic reader retains historical unversioned support. [Domain and package diagrams](domain-model.md) show ownership and dependencies.

## Build and modules

Run `./mvnw clean verify` with Java 21. Build the candidate locally with `./mvnw install`, then use matching `0.18.0-SNAPSHOT` dependencies:

```xml
<dependency>
    <groupId>io.github.markpollack</groupId>
    <artifactId>agent-judge-assertj</artifactId>
    <version>0.18.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

Add `agent-judge-ai-core` for the model-backed example.

| Module | Responsibility |
|---|---|
| `agent-judge-core` | Requirements, Judgments, Verdicts, composition, policy, orchestration, reports and codecs |
| `agent-judge-assertions` / `agent-judge-assertj` | Retained satisfaction assertions / fluent integration |
| `agent-judge-ai-core` | Model backends, prompt rendering and classification |
| `agent-judge-file` / `agent-judge-exec` | Filesystem comparisons / command, build and coverage checks |
| `agent-judge-llm` / `agent-judge-rag` | Spring AI judging / typed RAG evidence |
| `agent-judge-jev` | Jev requirement-aware provider boundary and native measurement fidelity |
| `agent-judge-spring-ai` / `agent-judge-langchain4j` / `agent-judge-koog` | Runtime response bridges |
| `agent-judge-agent-client` | CLI execution evidence and judging backend |

Core uses Jackson, SLF4J API and JSpecify, with no assertion framework or model-provider dependency. Filesystem judges use `Path`; comparisons and coverage use their own typed evidence; runtime bridges use `CompletionEvidence`. No universal context or required metadata keys carry inputs.

See the [migration guide](MIGRATION_TYPED_EVIDENCE.md), [AssertJ guide](agent-judge-assertj/README.md), and [Jev guide](agent-judge-jev/README.md).

## License

The current source uses the Business Source License 1.1 with the project-specific terms in [LICENSE](LICENSE). Releases through 0.9.1 under the former `org.springaicommunity` coordinates retain their [Apache License 2.0](LICENSE-APACHE.txt) terms; that file records release history.
